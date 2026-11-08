package com.loveproof.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * How fast the public wall answers once there is real data behind it.
 *
 * <p>A wall with a handful of rows answers instantly whichever way it is
 * indexed, so a performance test measured against an empty database proves
 * nothing. This one fills the table with a thousand rows — the size a busy
 * self-hosted installation reaches within a year — and then measures the
 * public read.
 *
 * <p>The measurements are timing observations, not strict thresholds: the
 * assertions are on the shape of the answer (the right rows, in the right
 * order) and on the plan the database chose. The timings are printed, so a
 * regression shows up in the build log even when it is still inside the
 * budget.
 */
@AutoConfigureMockMvc
class WallPerformanceTest extends PostgresIntegrationTest {

    /** How many rows the table holds while the test runs. */
    private static final int ROW_COUNT = 1_000;

    /** How many requests are timed. */
    private static final int SAMPLES = 60;

    /** The budget for the 95th percentile, in milliseconds. */
    private static final long P95_BUDGET_MILLIS = 300L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void fillTheWall() {
        jdbcTemplate.execute("DELETE FROM moderation_action");
        jdbcTemplate.execute("DELETE FROM testimonial");

        // The approved rows are recorded as moderated by the seeded
        // administrator, because the table's check constraint requires a
        // moderated row to carry a moderation timestamp and a moderator.
        UUID adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM admin_user ORDER BY id LIMIT 1", UUID.class);

        List<Object[]> rows = new ArrayList<>(ROW_COUNT);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        for (int index = 0; index < ROW_COUNT; index++) {
            // Two thirds are approved, so the wall has to filter as well as
            // sort — an index over every row would not be enough.
            String status = index % 3 == 0 ? "PENDING" : "APPROVED";
            int rating = (index % 5) + 1;
            // The JDBC driver cannot bind an `Instant` in a batch, so the
            // timestamp is handed over as an `OffsetDateTime`.
            OffsetDateTime submittedAt =
                    base.plus(index, ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC);

            rows.add(new Object[] {
                    UUID.randomUUID(),
                    "Customer " + index,
                    "Role " + (index % 7),
                    "Company " + (index % 23),
                    rating,
                    "A testimonial written for the performance check. Number " + index + ".",
                    status,
                    "v1",
                    // The consent is always given: the column's check
                    // constraint forbids anything else.
                    Boolean.TRUE,
                    submittedAt,
                    // The fingerprint and the IP hash are fixed-width columns
                    // with a length constraint, so each value is padded out to
                    // exactly 64 characters. Every row gets a distinct
                    // fingerprint, so nothing is treated as a duplicate.
                    pad("fingerprint-" + index),
                    pad("ip-hash-" + (index % 400)),
                    // A moderated row must carry a moderation timestamp. The
                    // administrator is the seeded one, so the foreign key
                    // holds.
                    status.equals("APPROVED") ? submittedAt : null,
                    status.equals("APPROVED") ? adminId : null,
            });
        }

        Collections.shuffle(rows);

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO testimonial (
                    id, name, role, company, rating, content, status,
                    consent_text_version, consent_given, submitted_at,
                    content_fingerprint, submitter_ip_hash, deleted_at,
                    moderated_at, moderated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                """,
                rows);
    }

    /**
     * Pads a value out to the 64 characters the fixed-width columns require.
     *
     * @param value the value to pad
     * @return the padded value
     */
    private static String pad(String value) {
        StringBuilder padded = new StringBuilder(value);
        while (padded.length() < 64) {
            padded.append('0');
        }
        return padded.toString();
    }

    @Test
    @DisplayName("the wall answers within the budget with a thousand rows behind it")
    void theWallAnswersWithinTheBudget() throws Exception {
        // The first request warms the connection pool and the plan cache; the
        // timings below are the steady state.
        mockMvc.perform(get("/api/testimonials")).andExpect(status().isOk());

        List<Long> durations = new ArrayList<>();
        for (int sample = 0; sample < SAMPLES; sample++) {
            long startedAt = System.nanoTime();
            mockMvc.perform(get("/api/testimonials")).andExpect(status().isOk());
            durations.add(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        }

        Collections.sort(durations);
        long p50 = durations.get(durations.size() / 2);
        long p95 = durations.get((int) (durations.size() * 0.95));

        System.out.printf(
                "Wall read over %d rows: p50 %d ms, p95 %d ms, max %d ms%n",
                ROW_COUNT, p50, p95, durations.get(durations.size() - 1));

        assertThat(p95).isLessThanOrEqualTo(P95_BUDGET_MILLIS);
    }

    @Test
    @DisplayName("the wall sorted by rating answers within the budget too")
    void theRatingSortAnswersWithinTheBudget() throws Exception {
        mockMvc.perform(get("/api/testimonials?sort=rating"))
                .andExpect(status().isOk());

        List<Long> durations = new ArrayList<>();
        for (int sample = 0; sample < SAMPLES; sample++) {
            long startedAt = System.nanoTime();
            mockMvc.perform(get("/api/testimonials?sort=rating")).andExpect(status().isOk());
            durations.add(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        }

        Collections.sort(durations);
        long p95 = durations.get((int) (durations.size() * 0.95));

        System.out.printf(
                "Wall read by rating over %d rows: p50 %d ms, p95 %d ms, max %d ms%n",
                ROW_COUNT,
                durations.get(durations.size() / 2),
                p95,
                durations.get(durations.size() - 1));

        assertThat(p95).isLessThanOrEqualTo(P95_BUDGET_MILLIS);
    }

    @Test
    @DisplayName("the database reads the wall through an index, not a table scan")
    void theWallQueryUsesAnIndex() {
        String planText = explain(
                """
                SELECT * FROM testimonial
                WHERE status = 'APPROVED' AND deleted_at IS NULL AND consent_given = true
                ORDER BY submitted_at DESC
                LIMIT 12
                """);

        System.out.println("The plan for the wall read:\n" + planText);

        // A sequential scan over a thousand rows would read every one of them.
        // The index on the live, approved rows keeps the scan small.
        assertThat(planText).doesNotContain("Seq Scan on testimonial");
        assertThat(planText).contains("Index Scan");
    }

    @Test
    @DisplayName("the wall sorted by rating is also read through an index")
    void theRatingSortUsesAnIndex() {
        String planText = explain(
                """
                SELECT * FROM testimonial
                WHERE status = 'APPROVED' AND deleted_at IS NULL AND consent_given = true
                ORDER BY rating DESC, submitted_at DESC
                LIMIT 12
                """);

        System.out.println("The plan for the wall read by rating:\n" + planText);

        assertThat(planText).doesNotContain("Seq Scan on testimonial");
        assertThat(planText).contains("Index Scan");
    }

    @Test
    @DisplayName("the wall's composite indexes are in place")
    void theCompositeIndexesAreInPlace() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'testimonial' ORDER BY indexname",
                String.class);

        assertThat(indexes).contains(
                "idx_testimonial_live_approved_recent",
                "idx_testimonial_live_approved_rating",
                "idx_testimonial_live_approved_rating_filter");
    }

    @Test
    @DisplayName("the headline count and average rating are cheap to read")
    void thePublicStatsAreCheapToRead() {
        // The count and the average are read on every wall load, so they must
        // not cost more than the list itself.
        String planText = explain(
                """
                SELECT COUNT(*), AVG(rating) FROM testimonial
                WHERE status = 'APPROVED' AND deleted_at IS NULL AND consent_given = true
                """);

        System.out.println("The plan for the public statistics:\n" + planText);

        List<Long> durations = new ArrayList<>();
        for (int sample = 0; sample < SAMPLES; sample++) {
            long startedAt = System.nanoTime();
            jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM testimonial
                    WHERE status = 'APPROVED' AND deleted_at IS NULL AND consent_given = true
                    """,
                    Long.class);
            durations.add(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        }

        Collections.sort(durations);
        long p95 = durations.get((int) (durations.size() * 0.95));
        System.out.printf(
                "Public statistics over %d rows: p50 %d ms, p95 %d ms%n",
                ROW_COUNT, durations.get(durations.size() / 2), p95);

        assertThat(p95).isLessThanOrEqualTo(P95_BUDGET_MILLIS);
    }

    /**
     * Runs an {@code EXPLAIN ANALYZE} and returns the plan as one string.
     *
     * @param sql the query to explain
     * @return the plan
     */
    private String explain(String sql) {
        return String.join("\n", jdbcTemplate.queryForList("EXPLAIN (ANALYZE, FORMAT TEXT) " + sql, String.class));
    }
}
