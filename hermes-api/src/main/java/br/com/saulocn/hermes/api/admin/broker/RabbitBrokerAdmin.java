package br.com.saulocn.hermes.api.admin.broker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import java.net.URI;
import java.util.Map;

/** Queue depth from the RabbitMQ management API. */
@ApplicationScoped
public class RabbitBrokerAdmin implements BrokerAdmin {

    private static final String DEFAULT_MAIN_QUEUE = "MailQueue";
    private static final String DEFAULT_DLQ = "MailQueueDLQ";

    private final BrokerHttp http;
    private final BrokerEndpoint endpoint;
    private final String mainQueue;
    private final String dlqQueue;

    @Inject
    public RabbitBrokerAdmin(BrokerHttp http, BrokerEndpoint endpoint) {
        this.http = http;
        this.endpoint = endpoint;
        // If queue name is blank or null, use the RabbitMQ default; otherwise use configured value
        this.mainQueue = endpoint.queue() == null || endpoint.queue().isBlank()
            ? DEFAULT_MAIN_QUEUE
            : endpoint.queue();
        // For RabbitMQ, DLQ is always mainQueue + "DLQ" suffix
        this.dlqQueue = mainQueue + "DLQ";
    }

    @Override
    public String kind() {
        return "rabbit";
    }

    @Override
    public QueueDepth read() throws Exception {
        return new QueueDepth(depthOf(mainQueue), depthOf(dlqQueue));
    }

    /** %2F is the default vhost "/" — it has to stay percent-encoded in the path. */
    private Long depthOf(String queue) throws Exception {
        try {
            JsonObject body = http.get(URI.create(endpoint.baseUrl() + "/api/queues/%2F/" + queue),
                    endpoint.username(), endpoint.password(), Map.of());
            // Absent right after boot, before the management database has collected stats. Null
            // rather than zero: the broker declined to say, it did not say nothing is there.
            return body.containsKey("messages") ? (long) body.getInt("messages") : null;
        } catch (BrokerHttp.QueueNotFound e) {
            return 0L;
        }
    }
}
