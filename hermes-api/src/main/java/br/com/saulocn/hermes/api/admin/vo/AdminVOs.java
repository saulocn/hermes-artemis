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
     * <p>{@code pending}, {@code inFlight} and {@code delivered} come from the two booleans.
     * {@code failing} is a subset of {@code inFlight}: published, undelivered, and known to have
     * thrown at least once. Without it a message stuck in a retry loop and a message merely
     * waiting in a queue are the same number.
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

    public record RecipientSummary(Long id, String email, Long messageId, boolean processed,
                                   boolean sent, LocalDateTime createdAt) {
    }

    public record Page<T>(int page, int size, long total, List<T> items) {
    }

    public record Retried(Long id, boolean retried) {
    }

    public record JobStarted(Long executionId) {
    }
}
