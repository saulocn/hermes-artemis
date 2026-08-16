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

    public record ThroughputPoint(String minute, long delivered) {
    }

    public record Throughput(List<ThroughputPoint> points) {
    }

    /** queueDepth/dlqDepth are null when the broker could not be reached; error says why. */
    public record BrokerStatus(String kind, Long queueDepth, Long dlqDepth, String error) {
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
