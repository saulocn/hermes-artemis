package br.com.saulocn.hermes.api.admin.broker;

/**
 * Reads how much is sitting on the broker.
 *
 * <p>Two adapters satisfy this — Artemis over Jolokia, RabbitMQ over its management API — which
 * is what makes the seam real rather than hypothetical. Before it existed, the choice was a
 * ternary inside one class, and anything that was not literally "rabbit" was treated as Artemis.
 *
 * <p>{@code read} throws when the broker cannot be reached. Callers are expected to degrade, not
 * to fail: queue depth is diagnostic, and the console showing "unavailable" beats the console
 * showing an error page.
 */
public interface BrokerAdmin {

    /** Matched against hermes.broker.kind to pick the adapter at runtime. */
    String kind();

    QueueDepth read() throws Exception;
}
