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
 * Aggregate queries for the operator console.
 *
 * <p>Kept apart from {@code MessageService} because that one sits on the ingest path: the console
 * polls these, and mixing them would make it easy to slow ingestion down by accident. They share
 * a datasource, which is why its pool was raised from 2.
 */
@ApplicationScoped
public class DashboardService {

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

    @Transactional
    public AdminVOs.Stats stats() {
        // One pass over recipient instead of one count per state. The predicates come from
        // RecipientState so that this and the filter in recipients() cannot disagree.
        Object[] row = (Object[]) em.createNativeQuery("""
                select
                  %s,
                  min(created_on) filter (where not recipient_sent)
                from hermes.recipient
                """.formatted(RecipientState.countColumns())).getSingleResult();

        // Positional array from countColumns() emits in enum order; EnumMap re-keys by state. Safety
        // comes from both sides using the same order (enum.values()).
        Map<RecipientState, Long> counts = new EnumMap<>(RecipientState.class);
        RecipientState[] states = RecipientState.values();
        for (int i = 0; i < states.length; i++) {
            counts.put(states[i], ((Number) row[i]).longValue());
        }
        Object oldestCreatedOn = row[states.length];

        long totalMessages = ((Number) em.createNativeQuery("select count(*) from hermes.message")
                .getSingleResult()).longValue();

        Long oldestPendingSeconds = null;
        if (oldestCreatedOn != null) {
            LocalDateTime oldest = toLocalDateTime(oldestCreatedOn);
            oldestPendingSeconds = java.time.Duration.between(oldest, LocalDateTime.now()).getSeconds();
        }

        return new AdminVOs.Stats(
                counts.get(RecipientState.PENDING),
                counts.get(RecipientState.IN_FLIGHT),
                counts.get(RecipientState.FAILING),
                counts.get(RecipientState.DELIVERED),
                totalMessages,
                oldestPendingSeconds);
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
        Rates.TimeWindow window = new Rates.TimeWindow(windowStart, dbNow);

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
            Rates.TimeWindow window,
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

        List<Long> counts = Rates.fillGaps(window, firstSample, buckets);
        List<AdminVOs.ThroughputPoint> points = new ArrayList<>(counts.size());
        LocalDateTime minute = window.start().withSecond(0).withNano(0);
        for (Long count : counts) {
            points.add(new AdminVOs.ThroughputPoint(minute.toString(), count));
            minute = minute.plusMinutes(1);
        }

        return new AdminVOs.ThroughputSeries(column, points);
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

    @Transactional
    public AdminVOs.Page<AdminVOs.MessageSummary> messages(int page, int size, String query) {
        String filter = (query == null || query.isBlank()) ? null : "%" + query.toLowerCase() + "%";

        var countQuery = em.createNativeQuery("""
                select count(*) from hermes.message m
                where (cast(:filter as text) is null or lower(m.message_title) like cast(:filter as text))
                """).setParameter("filter", filter);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select m.message_id, m.message_title, m.content_type, m.created_on,
                       count(r.recipient_id), count(r.recipient_id) filter (where r.recipient_sent)
                from hermes.message m
                left join hermes.recipient r on r.message_id = m.message_id
                where (cast(:filter as text) is null or lower(m.message_title) like cast(:filter as text))
                group by m.message_id, m.message_title, m.content_type, m.created_on
                order by m.message_id desc
                """)
                .setParameter("filter", filter)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        List<AdminVOs.MessageSummary> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(new AdminVOs.MessageSummary(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    (String) row[2],
                    toLocalDateTime(row[3]),
                    ((Number) row[4]).longValue(),
                    ((Number) row[5]).longValue()));
        }
        return new AdminVOs.Page<>(page, size, total, items);
    }

    @Transactional
    public AdminVOs.Page<AdminVOs.RecipientSummary> recipients(String email, String state, int page, int size) {
        String filter = (email == null || email.isBlank()) ? null : "%" + email.toLowerCase() + "%";
        // An unknown state is rejected rather than ignored: silently answering with every row is
        // how `state=failing` looked like it worked while matching no arm at all.
        String stateFilter = (state == null || state.isBlank()) ? null
                : RecipientState.fromWireName(state)
                        .orElseThrow(() -> new IllegalArgumentException("unknown recipient state: " + state))
                        .wireName();

        String where = """
                where (cast(:filter as text) is null or lower(recipient_mail) like cast(:filter as text))
                  and (cast(:state as text) is null
                       %s)
                """.formatted(RecipientState.filterArms());

        long total = ((Number) em.createNativeQuery("select count(*) from hermes.recipient " + where)
                .setParameter("filter", filter)
                .setParameter("state", stateFilter)
                .getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select recipient_id, recipient_mail, message_id, recipient_processed,
                       recipient_sent, recipient_attempts, created_on
                from hermes.recipient
                """ + where + " order by recipient_id desc")
                .setParameter("filter", filter)
                .setParameter("state", stateFilter)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        List<AdminVOs.RecipientSummary> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(new AdminVOs.RecipientSummary(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    row[2] == null ? null : ((Number) row[2]).longValue(),
                    (Boolean) row[3],
                    (Boolean) row[4],
                    ((Number) row[5]).intValue(),
                    toLocalDateTime(row[6])));
        }
        return new AdminVOs.Page<>(page, size, total, items);
    }

    /**
     * Clears `processed` and `published_on` so the enqueuer picks the recipient up again.
     * A targeted update, for the same reason the enqueuer and mailer use one: writing
     * the whole row here would race with them and clobber `sent`.
     *
     * <p>The attempt counter is cleared with it. An operator retrying a row is saying the last
     * failures no longer describe it — leaving the count would put the row straight back in
     * "failing" the moment the enqueuer republishes it, before anything has been tried again.
     *
     * <p>`claimed_on` is NOT cleared: the mailer may be mid-delivery for this recipient,
     * and clearing it would break the `claimed_on IS NOT NULL ⟺ recipient_sent` invariant.
     */
    @Transactional
    public boolean retry(Long recipientId) {
        // Use native query to clear published_on alongside the JPA-friendly fields.
        // This is driven through QuarkusTransaction.requiringNew() in tests because
        // the annotation does not propagate when called from another test method.
        int updated = em.createNativeQuery("""
                update hermes.recipient
                   set recipient_processed = false, recipient_attempts = 0, published_on = null
                 where recipient_id = :id
                """)
                .setParameter("id", recipientId)
                .executeUpdate();
        return updated > 0;
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
