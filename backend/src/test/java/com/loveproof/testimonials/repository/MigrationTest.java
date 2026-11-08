package com.loveproof.testimonials.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * Verifies the Flyway migrations produce the schema the data model specifies.
 *
 * <p>These tests assert against the live PostgreSQL instance, so the CHECK constraints, partial
 * indexes and foreign keys are exercised for real rather than simulated.
 */
class MigrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void allMigrationsAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2', '3', '4', '5', '6') AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(6);

        String description = jdbcTemplate.queryForObject(
                "SELECT description FROM flyway_schema_history WHERE version = '1'", String.class);
        assertThat(description).isEqualTo("baseline");
    }

    @Test
    void reRunningMigrationsIsANoOp() {
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class);
        assertThat(pending).isZero();

        Integer versions = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT version) FROM flyway_schema_history", Integer.class);
        assertThat(versions).isEqualTo(6);
    }

    /**
     * The seeded administrator's hash must be a real BCrypt hash.
     *
     * <p>An earlier placeholder chain resolved the hash to the literal text
     * {@code ${dollar}2a${dollar}...}, which is not a hash at all — no password could ever match
     * it. This check guards against that shape coming back.
     */
    @Test
    void theSeededAdministratorHasARealPasswordHash() {
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM admin_user WHERE lower(username) = lower(?)",
                String.class,
                "testadmin");

        assertThat(hash).isNotBlank();
        assertThat(hash).startsWith("$2");
        assertThat(hash).doesNotContain("dollar");
        assertThat(hash).doesNotContain("${");
    }

    @Test
    void ratingOutsideOneToFiveIsRejected() {
        insertTestimonial("rating", "6");
        insertTestimonial("rating", "0");
    }

    @Test
    void consentCannotBeFalse() {
        insertTestimonial("consent_given", "FALSE");
    }

    @Test
    void invalidStatusIsRejected() {
        insertTestimonial("status", "PUBLISHED");
    }

    @Test
    void shortNameIsRejected() {
        insertTestimonial("name", "A");
    }

    @Test
    void longNameIsRejected() {
        // Postgres rejects the value on the column's own type limit before the CHECK
        // constraint is evaluated, so this asserts the insert fails rather than naming the
        // constraint.
        assertThatThrownBy(() -> jdbcTemplate.execute(insertWith("name", "n".repeat(101))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shortContentIsRejected() {
        insertTestimonial("content", "too short");
    }

    @Test
    void rejectionWithoutReasonIsRejected() {
        insertTestimonial("rejected_without_reason", "REJECTED");
    }

    @Test
    void pendingWithModerationTimestampIsRejected() {
        insertTestimonial("moderated_at", "now()");
    }

    @Test
    void partialLiveApprovedIndexExists() {
        String definition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_testimonial_live_approved'",
                String.class);

        assertThat(definition).isNotNull();
        assertThat(definition).contains("WHERE");
        assertThat(definition).contains("'APPROVED'");
        assertThat(definition).contains("deleted_at IS NULL");
        assertThat(definition).contains("submitted_at DESC");
    }

    @Test
    void partialRatingApprovedIndexExists() {
        String definition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_testimonial_rating_approved'",
                String.class);

        assertThat(definition).isNotNull();
        assertThat(definition).contains("WHERE");
        assertThat(definition).contains("'APPROVED'");
    }

    @Test
    void allExpectedIndexesExist() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                    'uq_admin_user_username',
                    'idx_testimonial_status_submitted',
                    'idx_testimonial_live_approved',
                    'idx_testimonial_rating_approved',
                    'idx_testimonial_fingerprint',
                    'idx_testimonial_submitter',
                    'idx_testimonial_deleted_at',
                    'idx_moderation_action_testimonial',
                    'idx_moderation_action_created_at'
                  )
                """,
                Integer.class);

        assertThat(count).isEqualTo(9);
    }

    @Test
    void foreignKeysToAdminUserAreSetNullOnDelete() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.referential_constraints rc
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_name = rc.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND rc.delete_rule = 'SET NULL'
                  AND tc.constraint_name IN ('fk_testimonial_moderated_by', 'fk_testimonial_deleted_by')
                """,
                Integer.class);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void moderationActionForeignKeyCascadesOnDelete() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.referential_constraints rc
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_name = rc.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND rc.delete_rule = 'CASCADE'
                  AND tc.constraint_name = 'fk_moderation_action_testimonial'
                """,
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void adminUserIsSeededExactlyOnce() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_user WHERE username = 'testadmin'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void seedingIsIdempotent() {
        // The seed uses ON CONFLICT DO NOTHING, so re-inserting the same username is a no-op.
        jdbcTemplate.update(
                """
                INSERT INTO admin_user (username, password_hash, display_name, role, enabled)
                VALUES ('testadmin', 'not-a-real-hash', 'Duplicate', 'ADMIN', TRUE)
                ON CONFLICT (lower(username)) DO NOTHING
                """);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_user WHERE username = 'testadmin'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void contentFingerprintColumnIsFixedWidth() {
        String type = jdbcTemplate.queryForObject(
                """
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'testimonial' AND column_name = 'content_fingerprint'
                """,
                String.class);

        assertThat(type).isEqualTo("character");
    }

    @Test
    void timestampsAreStoredWithTimeZone() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name IN ('testimonial', 'admin_user', 'moderation_action')
                  AND data_type = 'timestamp with time zone'
                """,
                Integer.class);

        // testimonial: submitted_at, moderated_at, deleted_at, updated_at (4)
        // admin_user: created_at, last_login_at (2)
        // moderation_action: created_at (1)
        assertThat(count).isEqualTo(7);
    }

    /**
     * Attempts an insert that violates one column constraint.
     *
     * <p>The column name comes from the test itself and is matched against a fixed set of
     * overrides, so no untrusted value is ever interpolated into the statement.
     *
     * @param column the column to override
     * @param value  the raw value assigned to that column
     */
    private void insertTestimonial(String column, String value) {
        assertThatThrownBy(() -> jdbcTemplate.execute(insertWith(column, value)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraintFor(column));
    }

    /**
     * Maps a column override to the CHECK constraint it is expected to trip.
     *
     * @param column the column
     * @return the constraint name
     */
    private String constraintFor(String column) {
        return switch (column) {
            case "rating" -> "ck_testimonial_rating_range";
            case "consent_given" -> "ck_testimonial_consent_required";
            case "status" -> "ck_testimonial_status";
            case "name" -> "ck_testimonial_name_length";
            case "content" -> "ck_testimonial_content_length";
            case "moderated_at" -> "ck_testimonial_moderation_fields";
            case "rejected_without_reason" -> "ck_testimonial_rejection_reason";
            default -> throw new IllegalArgumentException("Unsupported column override: " + column);
        };
    }

    /**
     * Builds a valid INSERT that overrides one column with a value that breaks its constraint.
     *
     * <p>Text values are single-quoted here; every other type is inserted as a bare literal.
     *
     * @param column the column to override
     * @param value  the raw value
     * @return the statement
     */
    private String insertWith(String column, String value) {
        String id = UUID.randomUUID().toString();
        String fingerprint = "f".repeat(64);

        // A rejected testimonial must carry a reason, and a moderated one must carry a
        // timestamp. The overrides below stay internally consistent so that only the
        // constraint under test can fail.
        return switch (column) {
            case "rating" -> """
                    INSERT INTO testimonial (
                        id, name, rating, content, status, consent_given, consent_text_version,
                        content_fingerprint, updated_at
                    ) VALUES (
                        '%s', 'Valid Name', %s,
                        'A testimonial body that is comfortably longer than twenty characters.',
                        'PENDING', TRUE, 'v1', '%s', now()
                    )
                    """.formatted(id, value, fingerprint);
            case "consent_given" -> """
                    INSERT INTO testimonial (
                        id, name, rating, content, status, consent_given, consent_text_version,
                        content_fingerprint, updated_at
                    ) VALUES (
                        '%s', 'Valid Name', 4,
                        'A testimonial body that is comfortably longer than twenty characters.',
                        'PENDING', %s, 'v1', '%s', now()
                    )
                    """.formatted(id, value, fingerprint);
            // 'PUBLISHED' is not a valid status, and it is neither PENDING nor accompanied by a
            // moderation timestamp, so both constraints would reject it. The status vocabulary
            // check is the one asserted here.
            case "status" -> """
                    INSERT INTO testimonial (
                        id, name, rating, content, status, consent_given, consent_text_version,
                        content_fingerprint, updated_at, moderated_at, rejection_reason
                    ) VALUES (
                        '%s', 'Valid Name', 4,
                        'A testimonial body that is comfortably longer than twenty characters.',
                        %s, TRUE, 'v1', '%s', now(), now(), 'Not a valid status.'
                    )
                    """.formatted(id, quote(value), fingerprint);
            case "name" -> """
                    INSERT INTO testimonial (
                        id, name, rating, content, status, consent_given, consent_text_version,
                        content_fingerprint, updated_at
                    ) VALUES (
                        '%s', %s, 4,
                        'A testimonial body that is comfortably longer than twenty characters.',
                        'PENDING', TRUE, 'v1', '%s', now()
                    )
                    """.formatted(id, quote(value), fingerprint);
            case "content" -> """
                    INSERT INTO testimonial (
                        id, name, rating, content, status, consent_given, consent_text_version,
                        content_fingerprint, updated_at
                    ) VALUES (
                        '%s', 'Valid Name', 4, %s, 'PENDING', TRUE, 'v1', '%s', now()
                    )
                    """.formatted(id, quote(value), fingerprint);
            case "moderated_at" -> """
                    INSERT INTO testimonial (
                        id, name, rating, content, status, consent_given, consent_text_version,
                        content_fingerprint, updated_at, moderated_at
                    ) VALUES (
                        '%s', 'Valid Name', 4,
                        'A testimonial body that is comfortably longer than twenty characters.',
                        'PENDING', TRUE, 'v1', '%s', now(), %s
                    )
                    """.formatted(id, fingerprint, value);
            // A rejected testimonial must carry a reason. The timestamp is supplied so that only
            // the missing reason can trip a constraint.
            case "rejected_without_reason" -> """
                    INSERT INTO testimonial (
                        id, name, rating, content, status, consent_given, consent_text_version,
                        content_fingerprint, updated_at, moderated_at, rejection_reason
                    ) VALUES (
                        '%s', 'Valid Name', 4,
                        'A testimonial body that is comfortably longer than twenty characters.',
                        %s, TRUE, 'v1', '%s', now(), now(), NULL
                    )
                    """.formatted(id, quote(value), fingerprint);
            default -> throw new IllegalArgumentException("Unsupported column override: " + column);
        };
    }

    /**
     * Quotes a text value for the SQL statement.
     *
     * @param value the raw value
     * @return the quoted literal
     */
    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
