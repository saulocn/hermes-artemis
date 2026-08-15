package br.com.saulocn.hermes.api.admin.broker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Queue depth from the Artemis console's Jolokia endpoint. */
@ApplicationScoped
public class ArtemisBrokerAdmin implements BrokerAdmin {

    private static final String MAIN_QUEUE = "jms.queue.MailQueue";
    private static final String DLQ = MAIN_QUEUE + "DLQ";

    /** Jolokia refuses requests it considers cross-origin. RabbitMQ neither needs nor wants this. */
    private static final String ORIGIN_HEADER = "Origin";

    private final BrokerHttp http;
    private final BrokerEndpoint endpoint;

    @Inject
    public ArtemisBrokerAdmin(BrokerHttp http, BrokerEndpoint endpoint) {
        this.http = http;
        this.endpoint = endpoint;
    }

    @Override
    public String kind() {
        return "artemis";
    }

    @Override
    public QueueDepth read() throws Exception {
        Long main = depthOf(MAIN_QUEUE);

        // Artemis creates the dead letter queue only once something lands in it, so a missing
        // MBean means nothing was ever dead-lettered — zero, not unknown. Only that case is
        // treated as zero; a transport failure still propagates, which it did not before.
        Long dlq;
        try {
            dlq = depthOf(DLQ);
        } catch (NoSuchMBean e) {
            dlq = 0L;
        }
        return new QueueDepth(main, dlq);
    }

    private Long depthOf(String queue) throws Exception {
        // The broker name is part of the MBean name and is not "hermes" — the base image names it
        // after the bind address ("0.0.0.0"). Asking Jolokia to find it with broker=* survives a
        // different image or a renamed broker.
        String pattern = "org.apache.activemq.artemis:broker=*,component=addresses,address=\"" + queue
                + "\",subcomponent=queues,routing-type=\"anycast\",queue=\"" + queue + "\"";

        JsonObject search = jolokia("/console/jolokia/search/" + encode(pattern));
        var matches = search.getJsonArray("value");
        if (matches == null || matches.isEmpty()) {
            throw new NoSuchMBean(queue);
        }

        return (long) jolokia("/console/jolokia/read/" + encode(matches.getString(0)) + "/MessageCount")
                .getInt("value");
    }

    private JsonObject jolokia(String path) throws Exception {
        JsonObject body = http.get(URI.create(endpoint.baseUrl() + path),
                endpoint.username(), endpoint.password(),
                Map.of(ORIGIN_HEADER, "http://" + endpoint.host()));
        // Jolokia answers HTTP 200 with an error body, so the status code alone is not enough.
        if (body.containsKey("error")) {
            throw new IllegalStateException(body.getString("error"));
        }
        return body;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static class NoSuchMBean extends RuntimeException {
        NoSuchMBean(String queue) {
            super("no MBean for queue " + queue);
        }
    }
}
