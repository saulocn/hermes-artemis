package br.com.saulocn.hermes.api.admin.broker;

/**
 * Queue depth and cumulative counters from the broker.
 *
 * <p>Zero and null mean different things and both adapters must use them the same way: zero is
 * "the broker answered and there is nothing there", null is "the broker answered but would not
 * say". An unreachable broker is neither — that throws.
 *
 * <p>{@code main} and {@code dlq} are current queue depths. {@code enqueued} and {@code acknowledged}
 * are cumulative counters since broker start. Both follow the null-vs-zero contract identically.
 */
public record QueueReading(Long main, Long dlq, Long enqueued, Long acknowledged) {
}
