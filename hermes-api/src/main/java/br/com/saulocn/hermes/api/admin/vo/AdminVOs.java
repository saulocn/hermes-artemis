package br.com.saulocn.hermes.api.admin.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shapes for the operator console. They are records rather than the JPA entities on
 * purpose: {@code MessageResource} already leaks entities to callers, and repeating that here
 * would tie the console to the persistence model.
 */
public final class AdminVOs {

    private AdminVOs() {
    }

    /**
     * What the recipient table looks like right now.
     *
     * <p>The four states partition every row: a recipient is in exactly one of
     * {@code pending}, {@code inFlight}, {@code failing}, or {@code delivered}.
     * {@code inFlight} are published, undelivered, with no attempt failures. {@code failing} are
     * published, undelivered, and known to have thrown at least once. The four counts always sum to
     * the table size.
     */
    public record Stats(long pending, long inFlight, long failing, long delivered,
                        long totalMessages, Long oldestPendingSeconds) {
    }

    /**
     * A single point in a throughput series.
     *
     * <p>{@code count} is nullable because minutes before a column's first sample are
     * filled with null to distinguish "the column did not exist yet" from "no activity
     * that minute". The in-progress minute is excluded from the series.
     */
    public record ThroughputPoint(String minute, Long count) {
    }

    /**
     * Per-minute throughput series, one series per stage (created_on, published_on, claimed_on).
     *
     * <p>Each series includes one point per minute in the window, with nulls for minutes
     * before that stage's first sample.
     */
    public record ThroughputSeries(String stage, List<ThroughputPoint> points) {
    }

    public record Throughput(List<ThroughputSeries> series, LocalDateTime asOf) {
    }

    /**
     * Rate statistics per stage.
     *
     * <p>{@code ratePerSecond} = count / window, the rate during the window.
     * {@code sustainedPerSecond} = count / max(span, 1), the true throughput accounting
     * for idle gaps. It is null when fewer than 2 rows exist or the span is null.
     *
     * <p>The span is the time from the first to the last row in that stage's column,
     * in seconds. It is a fraction because the rows are milliseconds apart; the window is
     * {@code int} because it is a request parameter counted in whole seconds, and expressing it
     * as a float only invites a 3599.9999 nobody wants to read or assert on.
     */
    public record StageRate(long count, int window, Double span,
                            double ratePerSecond, Double sustainedPerSecond) {
    }

    /**
     * Overall rates across all stages, with last published timestamp and query time.
     *
     * <p>{@code lastPublishAt} is the max {@code published_on} across all rows in the
     * window, null if no rows published in the window. {@code asOf} is the database's
     * {@code now()} at query time.
     */
    public record Rates(StageRate created, StageRate published, StageRate claimed,
                        LocalDateTime lastPublishAt, LocalDateTime asOf) {
    }

    /** queueDepth/dlqDepth are null when the broker could not be reached; error says why. */
    /**
     * {@code ackRate} is messages acknowledged per second, differenced from the broker's own
     * cumulative counter between two polls. Null while there is no predecessor sample, after a
     * broker restart, and whenever the broker declines to report the counter — never zero, which
     * would claim the queue is idle.
     *
     * <p>It is not the same number as the delivery rate from the database, and the difference is
     * useful: the consumer acks duplicates without recording a delivery, so {@code ackRate} minus
     * the delivered rate is the duplicate rate.
     */
    public record BrokerStatus(String kind, Long queueDepth, Long dlqDepth, Double ackRate,
                               String error) {
    }

    public record MessageSummary(Long id, String title, String contentType, LocalDateTime createdAt,
                                 long recipientCount, long sentCount) {
    }

    /**
     * {@code attempts} is here so the console can tell a failing row from one merely in flight.
     * The two booleans read the same for both, so without it the list could not show the state
     * the dashboard was already counting.
     */
    public record RecipientSummary(Long id, String email, Long messageId, boolean processed,
                                   boolean sent, int attempts, LocalDateTime createdAt) {
    }

    public record Page<T>(int page, int size, long total, List<T> items) {
    }

    public record Retried(Long id, boolean retried) {
    }

    public record JobStarted(Long executionId) {
    }
}
