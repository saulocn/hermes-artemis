package br.com.saulocn.hermes.api.admin;

import br.com.saulocn.hermes.api.admin.vo.AdminVOs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Aggregated rollup queries for recipient state changes. Produces per-minute throughput series
 * and per-stage rates, with caching.
 *
 * <p>The critical invariant: per-minute throughput series have labels and counts that are
 * guaranteed to align. This is enforced by construction — a single loop walks the minute
 * boundaries once, and for each minute iteration the label and count are computed together.
 * Desynchronization is impossible; changing the minute calculation affects both the label
 * and the count in the same place.
 *
 * <p>Kept apart from {@code DashboardService} because it is stateful (caches) and the service
 * is read-mostly with multiple small queries. This separation also isolates the caching logic.
 */
@ApplicationScoped
public class RecipientRollup {

    @Inject
    EntityManager em;

    @Inject
    Clock clock;

    @ConfigProperty(name = "hermes.dashboard.rates-cache-ttl")
    long ratesCacheTtlSeconds;

    @ConfigProperty(name = "hermes.dashboard.throughput-cache-ttl")
    long throughputCacheTtlSeconds;

    // Per-stage earliest sample time, cached for the process lifetime.
    private volatile LocalDateTime cachedFirstSamplePublished;
    private volatile LocalDateTime cachedFirstSampleClaimed;

    // Rate and throughput caches: (value, timestamp)
    private final AtomicReference<CachedValue<AdminVOs.Rates>> ratesCache = new AtomicReference<>();
    private final AtomicReference<CachedValue<AdminVOs.Throughput>> throughputCache = new AtomicReference<>();

    private record CachedValue<T>(T value, long timestampMs) {
        boolean isExpired(long ttlMs, long nowMs) {
            // TTL of 0 means caching is disabled (always expired).
            // TTL > 0 means cache expires after ttlMs milliseconds.
            if (ttlMs == 0) {
                return true;
            }
            return nowMs - timestampMs > ttlMs;
        }
    }

    /**
     * A time window for binning operations. Package-private: used internally by RecipientRollup
     * to define minute boundaries. Moved here from Rates because only RecipientRollup needs it now.
     */
    record TimeWindow(LocalDateTime start, LocalDateTime end) {
        TimeWindow {
            if (start == null || end == null) {
                throw new IllegalArgumentException("start and end cannot be null");
            }
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("start must be before end");
            }
        }
    }

    /**
     * Per-minute throughput series for the past {@code minutes}, with caching.
     *
     * <p>Produces three series (created_on, published_on, claimed_on), each with
     * one point per minute. Points before that stage's first sample are null.
     * The in-progress minute is excluded.
     */
    @Transactional
    public AdminVOs.Throughput throughput(int minutes) {
        long nowMs = clock.instant().toEpochMilli();
        long cacheTtlMs = throughputCacheTtlSeconds * 1000;
        CachedValue<AdminVOs.Throughput> cached = throughputCache.get();
        if (cached != null && !cached.isExpired(cacheTtlMs, nowMs)) {
            return cached.value;
        }

        AdminVOs.Throughput result = computeThroughput(minutes);
        throughputCache.set(new CachedValue<>(result, nowMs));
        return result;
    }

    // No @Transactional here: interceptors do not apply to private methods, and Quarkus fails the
    // build rather than let the annotation sit there doing nothing. The transaction comes from the
    // public caller, which is the only way in.
    private AdminVOs.Throughput computeThroughput(int minutes) {
        LocalDateTime dbNow = getDbNow();
        LocalDateTime windowStart = dbNow.minusMinutes(minutes);
        TimeWindow window = new TimeWindow(windowStart, dbNow);

        // Ensure first-sample cache is populated
        if (cachedFirstSamplePublished == null) {
            Object result = em.createNativeQuery(
                    "select min(published_on) from hermes.recipient where published_on is not null")
                    .getSingleResult();
            cachedFirstSamplePublished = result != null ? toLocalDateTime(result) : null;
        }
        if (cachedFirstSampleClaimed == null) {
            Object result = em.createNativeQuery(
                    "select min(claimed_on) from hermes.recipient where claimed_on is not null")
                    .getSingleResult();
            cachedFirstSampleClaimed = result != null ? toLocalDateTime(result) : null;
        }

        // Fetch three series
        List<AdminVOs.ThroughputSeries> series = new ArrayList<>();
        series.add(fetchThroughputSeries("created_on", window, null));
        series.add(fetchThroughputSeries("published_on", window, cachedFirstSamplePublished));
        series.add(fetchThroughputSeries("claimed_on", window, cachedFirstSampleClaimed));

        return new AdminVOs.Throughput(series, dbNow);
    }

    private AdminVOs.ThroughputSeries fetchThroughputSeries(
            String column,
            TimeWindow window,
            LocalDateTime firstSample) {
        // created_on is never null; published_on and claimed_on are nullable
        String nullCheck = "created_on".equals(column) ? "" : column + " is not null and ";

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select date_trunc('minute', %s) as bucket, count(*)
                from hermes.recipient
                where %s%s >= :start
                  and date_trunc('minute', %s) < :end
                group by bucket
                order by bucket
                """.formatted(column, nullCheck, column, column))
                .setParameter("start", window.start())
                .setParameter("end", window.end())
                .getResultList();

        Map<LocalDateTime, Long> buckets = new HashMap<>();
        for (Object[] row : rows) {
            LocalDateTime bucket = toLocalDateTime(row[0]);
            long count = ((Number) row[1]).longValue();
            buckets.put(bucket, count);
        }

        // Build labeled points: single loop where minute calculation and count lookup happen together.
        // This guarantees label and count always align, by construction (not by discipline).
        List<AdminVOs.ThroughputPoint> points = buildThroughputPoints(window, firstSample, buckets);

        return new AdminVOs.ThroughputSeries(column, points);
    }

    /**
     * Builds a per-minute throughput series with labels and counts in a single loop.
     *
     * <p>Walks the minute boundaries from window.start() to window.end(), excluding the
     * in-progress minute. For each minute:
     * - If before firstSample: emits a point with null count (column is new)
     * - Otherwise: emits a point with the count from buckets, or 0 if the minute is a gap
     *
     * <p>The invariant is guaranteed by construction: the minute label and its count are
     * computed in the same iteration, making desynchronization impossible.
     *
     * @param window start (inclusive) and end (exclusive) of the time window
     * @param firstSample the earliest timestamp for this stage, or null if no samples exist
     * @param buckets (minute -> count) from the database
     * @return one ThroughputPoint per minute in the window (oldest first), with label and count
     */
    private List<AdminVOs.ThroughputPoint> buildThroughputPoints(
            TimeWindow window,
            LocalDateTime firstSample,
            Map<LocalDateTime, Long> buckets) {

        List<AdminVOs.ThroughputPoint> points = new ArrayList<>();

        // Compute the window end, excluding the in-progress minute
        LocalDateTime windowEnd = window.end().withSecond(0).withNano(0);
        if (windowEnd.isAfter(window.end())) {
            windowEnd = windowEnd.minusMinutes(1);
        }

        // Walk minutes from window.start() to window.end(), one iteration per minute
        LocalDateTime minute = window.start().withSecond(0).withNano(0);
        while (minute.isBefore(windowEnd)) {
            // Determine the count for this minute
            Long count;
            if (firstSample != null && minute.isBefore(firstSample)) {
                // Before the column existed: null (column is new, not that there's no data)
                count = null;
            } else {
                // After the column existed: count from bucket, or 0 if gap
                count = buckets.getOrDefault(minute, 0L);
            }

            // Add point with label and count from the same minute iteration
            points.add(new AdminVOs.ThroughputPoint(minute.toString(), count));
            minute = minute.plusMinutes(1);
        }

        return points;
    }

    /**
     * Per-stage rates over a time window, with caching.
     *
     * <p>Three subqueries, one per stage (created_on, published_on, claimed_on),
     * each returning count, span (max - min), and for publish also max(published_on).
     */
    @Transactional
    public AdminVOs.Rates rates(int windowSeconds) {
        long nowMs = clock.instant().toEpochMilli();
        long cacheTtlMs = ratesCacheTtlSeconds * 1000;
        CachedValue<AdminVOs.Rates> cached = ratesCache.get();
        if (cached != null && !cached.isExpired(cacheTtlMs, nowMs)) {
            return cached.value;
        }

        AdminVOs.Rates result = computeRates(windowSeconds);
        ratesCache.set(new CachedValue<>(result, nowMs));
        return result;
    }

    // See computeThroughput: the transaction is the public caller's.
    private AdminVOs.Rates computeRates(int windowSeconds) {
        LocalDateTime dbNow = getDbNow();
        LocalDateTime windowStart = dbNow.minusSeconds(windowSeconds);

        // created_on: always has data
        var createdData = fetchRateData("created_on", windowStart, dbNow);
        var createdRate = Rates.rate(createdData.count(), windowSeconds, createdData.span());
        AdminVOs.StageRate createdStageRate = new AdminVOs.StageRate(
                createdData.count(), windowSeconds, createdData.span(),
                createdRate.ratePerSecond(), createdRate.sustainedPerSecond());

        // published_on: nullable column
        var publishedData = fetchRateData("published_on", windowStart, dbNow);
        var publishedRate = Rates.rate(publishedData.count(), windowSeconds, publishedData.span());
        AdminVOs.StageRate publishedStageRate = new AdminVOs.StageRate(
                publishedData.count(), windowSeconds, publishedData.span(),
                publishedRate.ratePerSecond(), publishedRate.sustainedPerSecond());

        // claimed_on: nullable column
        var claimedData = fetchRateData("claimed_on", windowStart, dbNow);
        var claimedRate = Rates.rate(claimedData.count(), windowSeconds, claimedData.span());
        AdminVOs.StageRate claimedStageRate = new AdminVOs.StageRate(
                claimedData.count(), windowSeconds, claimedData.span(),
                claimedRate.ratePerSecond(), claimedRate.sustainedPerSecond());

        LocalDateTime lastPublishAt = (LocalDateTime) em.createNativeQuery(
                """
                select max(published_on) from hermes.recipient
                where published_on is not null
                  and published_on >= :start
                """)
                .setParameter("start", windowStart)
                .getSingleResult();

        return new AdminVOs.Rates(createdStageRate, publishedStageRate, claimedStageRate,
                lastPublishAt, dbNow);
    }

    private record RateData(long count, Double span) {
    }

    // Private: the transaction belongs to the public caller (see computeThroughput).
    private RateData fetchRateData(String column, LocalDateTime start, LocalDateTime end) {
        // created_on has no null check; published_on and claimed_on do
        String whereClause = "created_on".equals(column) ? "" : column + " is not null and ";

        Object[] row = (Object[]) em.createNativeQuery("""
                select count(*), extract(epoch from (max(%s) - min(%s)))
                from hermes.recipient
                where %s%s >= :start and %s < :end
                """.formatted(column, column, whereClause, column, column))
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();

        long count = ((Number) row[0]).longValue();
        Double span = row[1] != null ? ((Number) row[1]).doubleValue() : null;

        return new RateData(count, span);
    }

    /**
     * The database's clock, in the same time domain as the columns it will be compared against.
     *
     * <p>{@code localtimestamp}, not {@code now()}. Every timestamp column in this schema is
     * {@code TIMESTAMP} without a zone, while {@code now()} is {@code timestamptz} — Hibernate
     * hands it back as an {@code Instant}, and turning that into a {@code LocalDateTime} means
     * picking a zone. Pick the wrong one and every window boundary silently shifts by the offset,
     * which for a rate query means counting the wrong minutes rather than failing.
     *
     * <p>{@code localtimestamp} sidesteps the question: same type as the columns, same domain, no
     * conversion. The window boundary comes from the database rather than the JVM on purpose —
     * the rows were stamped by two other containers, and the comparison has to be made somewhere.
     */
    private LocalDateTime getDbNow() {
        return toLocalDateTime(em.createNativeQuery("select localtimestamp").getSingleResult());
    }

    /**
     * Hibernate 6 hands back LocalDateTime for timestamp columns, but the exact type depends on
     * the query shape (date_trunc, min(), a plain column), so accept either rather than betting
     * on one and failing at runtime.
     */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalStateException("unexpected timestamp type: " + value.getClass());
    }
}
