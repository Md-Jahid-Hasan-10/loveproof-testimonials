package com.loveproof.testimonials.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.domain.SpaceTestimonial;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.repository.SpaceTestimonialRepository;

/**
 * The dashboard's read model: the account's own numbers, and the daily
 * series behind the chart.
 *
 * <p>Three rules shape it (PRD §4.6):
 *
 * <ul>
 *   <li>**Only the account's own data.** Every count is scoped by the Space
 *   ids the account owns. There is no way to ask for another account's
 *   numbers, because the Space list is the ownership check.</li>
 *   <li>**The buckets are UTC days.** A submission is filed under the UTC
 *   day it arrived on, not under the viewer's local day — otherwise the
 *   same data would produce a different chart for two people looking at it
 *   at the same moment.</li>
 *   <li>**A day with no submissions shows zero.** The chart needs a point
 *   for every day in the range, or the line would lie about what happened
 *   in the gaps.</li>
 * </ul>
 *
 * <p>There is no deletion in the SaaS product: a testimonial is removed, not
 * soft-deleted, so there is no deleted row to exclude from the counts. The
 * PRD's "deleted testimonials are excluded" is satisfied by the delete being
 * a real delete.
 */
@Service
public class DashboardAnalyticsService {

    /** How many days the chart covers by default. */
    public static final int DEFAULT_RANGE_DAYS = 30;

    /** The longest range the chart will draw. */
    public static final int MAX_RANGE_DAYS = 365;

    /** How many recent submissions the dashboard lists. */
    public static final int RECENT_LIMIT = 10;

    private final SpaceRepository spaceRepository;
    private final SpaceTestimonialRepository testimonialRepository;
    private final AppUserRepository userRepository;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param spaceRepository       the Space store
     * @param testimonialRepository the testimonial store
     * @param userRepository        the account store
     * @param clock                 the clock, for the range boundaries
     */
    public DashboardAnalyticsService(
            SpaceRepository spaceRepository,
            SpaceTestimonialRepository testimonialRepository,
            AppUserRepository userRepository,
            Clock clock) {
        this.spaceRepository = spaceRepository;
        this.testimonialRepository = testimonialRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * One day's bucket on the chart.
     *
     * @param date  the UTC day
     * @param count how many submissions arrived on it
     */
    public record DailyCount(String date, long count) {
    }

    /**
     * One recent submission on the dashboard.
     *
     * @param id          the testimonial's identifier
     * @param spaceId     the Space it belongs to
     * @param spaceTitle  the Space's title
     * @param name        the submitter's name
     * @param content     the text
     * @param rating      the rating, or {@code null} when the Space has none
     * @param submittedAt when it arrived
     */
    public record RecentSubmission(
            UUID id,
            UUID spaceId,
            String spaceTitle,
            String name,
            String content,
            Integer rating,
            Instant submittedAt) {
    }

    /**
     * The whole dashboard.
     *
     * @param totalSpaces         how many Spaces the account owns
     * @param totalTestimonials   how many testimonials they have collected
     * @param uniqueSubmitters    how many distinct people submitted them
     * @param daily               the per-day counts, oldest first
     * @param rangeDays           how many days the chart covers
     * @param recent              the most recent submissions
     * @param plan                the account's plan
     * @param spacesLimit         how many Spaces the plan allows
     */
    public record Dashboard(
            long totalSpaces,
            long totalTestimonials,
            long uniqueSubmitters,
            List<DailyCount> daily,
            int rangeDays,
            List<RecentSubmission> recent,
            Plan plan,
            int spacesLimit) {
    }

    /**
     * Reads the dashboard for one account.
     *
     * @param ownerId  the signed-in account
     * @param rangeDays how many days the chart covers; clamped to a sane range
     * @return the dashboard
     */
    @Transactional(readOnly = true)
    public Dashboard read(UUID ownerId, Integer rangeDays) {
        int days = clampRange(rangeDays);
        Plan plan = planOf(ownerId);

        List<Space> spaces = spaceRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId);
        List<UUID> spaceIds = spaces.stream()
                .map(space -> space.getId())
                .toList();

        // An account with no Spaces has no testimonials either, and the
        // queries below would be "IN ()" — which is not valid SQL. An empty
        // list is answered from here instead.
        if (spaceIds.isEmpty()) {
            return new Dashboard(
                    0,
                    0,
                    0,
                    emptySeries(days),
                    days,
                    List.of(),
                    plan,
                    PlanLimits.spacesFor(plan));
        }

        long totalTestimonials = testimonialRepository.countAcrossSpaces(spaceIds);
        long uniqueSubmitters = testimonialRepository.countDistinctSubmittersAcrossSpaces(spaceIds);

        List<SpaceTestimonial> inRange = testimonialRepository.findSubmittedBetween(
                spaceIds,
                rangeStart(days),
                clock.instant());

        List<RecentSubmission> recent = testimonialRepository
                .findRecentAcrossSpaces(spaceIds, RECENT_LIMIT)
                .stream()
                .map(testimonial -> toRecent(testimonial, titlesOf(spaces)))
                .toList();

        return new Dashboard(
                spaces.size(),
                totalTestimonials,
                uniqueSubmitters,
                series(inRange, days),
                days,
                recent,
                plan,
                PlanLimits.spacesFor(plan));
    }

    /**
     * The account's plan.
     *
     * <p>The dashboard needs it for two things: whether to show the upgrade
     * notice, and how many Spaces the plan allows. A missing account is
     * treated as Free, which is the safe default — it shows the notice
     * rather than hiding it.
     *
     * @param ownerId the account
     * @return the plan
     */
    private Plan planOf(UUID ownerId) {
        return userRepository.findById(ownerId)
                .map(account -> account.getPlan())
                .orElse(Plan.FREE);
    }

    /**
     * The first instant of the chart's range.
     *
     * @param days how many days back the range reaches
     * @return the start, inclusive
     */
    private Instant rangeStart(int days) {
        return today().minusDays(days - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Today's UTC date.
     *
     * @return the date
     */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * The per-day series, with a point for every day in the range.
     *
     * <p>The counts are grouped by the UTC day the submission arrived on. A
     * day with no submissions gets a zero rather than being left out — the
     * chart needs the point to draw an honest line.
     *
     * @param submissions the submissions in the range
     * @param days        how many days the range covers
     * @return the series, oldest first
     */
    private List<DailyCount> series(List<SpaceTestimonial> submissions, int days) {
        Map<LocalDate, Long> counts = submissions.stream()
                .collect(Collectors.groupingBy(
                        testimonial -> LocalDate.ofInstant(
                                testimonial.getSubmittedAt(), ZoneOffset.UTC),
                        Collectors.counting()));

        List<DailyCount> series = new ArrayList<>(days);
        LocalDate first = today().minusDays(days - 1L);
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = first.plusDays(offset);
            series.add(new DailyCount(date.toString(), counts.getOrDefault(date, 0L)));
        }
        return series;
    }

    /**
     * An empty series: one zero for every day.
     *
     * @param days how many days
     * @return the series
     */
    private List<DailyCount> emptySeries(int days) {
        List<DailyCount> series = new ArrayList<>(days);
        LocalDate first = today().minusDays(days - 1L);
        for (int offset = 0; offset < days; offset++) {
            series.add(new DailyCount(first.plusDays(offset).toString(), 0L));
        }
        return series;
    }

    /**
     * The Space titles, by id, for the recent-submissions list.
     *
     * @param spaces the account's Spaces
     * @return the titles
     */
    private Map<UUID, String> titlesOf(List<Space> spaces) {
        return spaces.stream()
                .collect(Collectors.toMap(Space::getId, Space::getTitle));
    }

    /**
     * Renders one submission for the recent list.
     *
     * @param testimonial the testimonial
     * @param titles      the Space titles, by id
     * @return the entry
     */
    private RecentSubmission toRecent(SpaceTestimonial testimonial, Map<UUID, String> titles) {
        return new RecentSubmission(
                testimonial.getId(),
                testimonial.getSpaceId(),
                titles.getOrDefault(testimonial.getSpaceId(), ""),
                testimonial.getName(),
                testimonial.getContent(),
                testimonial.getRating(),
                testimonial.getSubmittedAt());
    }

    /**
     * Clamps the requested range to something the chart can draw.
     *
     * @param rangeDays the requested number of days, or {@code null}
     * @return the number of days to use
     */
    private int clampRange(Integer rangeDays) {
        if (rangeDays == null) {
            return DEFAULT_RANGE_DAYS;
        }
        if (rangeDays < 1) {
            return 1;
        }
        return Math.min(rangeDays, MAX_RANGE_DAYS);
    }
}
