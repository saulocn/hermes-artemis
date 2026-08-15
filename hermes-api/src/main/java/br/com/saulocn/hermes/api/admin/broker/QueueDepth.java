package br.com.saulocn.hermes.api.admin.broker;

/**
 * Depth of the main queue and of the dead letter queue.
 *
 * <p>Zero and null mean different things and both adapters must use them the same way: zero is
 * "the broker answered and there is nothing there", null is "the broker answered but would not
 * say". An unreachable broker is neither — that throws.
 */
public record QueueDepth(Long main, Long dlq) {
}
