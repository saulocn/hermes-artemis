package br.com.saulocn.hermes.api.admin.broker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Queue depth from the Artemis console's Jolokia endpoint. */
@ApplicationScoped
public class ArtemisBrokerAdmin implements BrokerAdmin {

    private static final String DEFAULT_MAIN_QUEUE = "jms.queue.MailQueue";

    /** Jolokia refuses requests it considers cross-origin. RabbitMQ neither needs nor wants this. */
    private static final String ORIGIN_HEADER = "Origin";

    private final BrokerHttp http;
    private final BrokerEndpoint endpoint;
    private final String mainQueue;
    private final String dlqQueue;

    @Inject
    public ArtemisBrokerAdmin(BrokerHttp http, BrokerEndpoint endpoint) {
        this.http = http;
        this.endpoint = endpoint;
        // If queue name is blank or null, use the Artemis default; otherwise use configured value
        this.mainQueue = endpoint.queue() == null || endpoint.queue().isBlank()
            ? DEFAULT_MAIN_QUEUE
            : endpoint.queue();
        this.dlqQueue = mainQueue + "DLQ";
    }

    @Override
    public String kind() {
        return "artemis";
    }

    @Override
    public QueueReading read() throws Exception {
        QueueCounters main = readCounters(mainQueue);

        // Artemis creates the dead letter queue only once something lands in it, so a missing
        // MBean means nothing was ever dead-lettered — zero, not unknown. Only that case is
        // treated as zero; a transport failure still propagates, which it did not before.
        QueueCounters dlq;
        try {
            dlq = readCounters(dlqQueue);
        } catch (NoSuchMBean e) {
            dlq = new QueueCounters(0L, null, null);
        }
        return new QueueReading(main.depth, dlq.depth, main.enqueued, main.acknowledged);
    }

    /** Cache of counters per queue to avoid repeated reads. */
    private record QueueCounters(Long depth, Long enqueued, Long acknowledged) {
    }

    /**
     * Reads depth and cumulative counters from the queue MBean.
     *
     * <p>First tries to read all three attributes in a single Jolokia call, which is more efficient.
     * If that fails (e.g. Jolokia policy does not permit comma-joined attributes), falls back to
     * separate reads.
     */
    private QueueCounters readCounters(String queue) throws Exception {
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

        String mbean = matches.getString(0);
        return readCountersForMbean(mbean);
    }

    /**
     * Reads MessageCount, MessagesAdded, MessagesAcknowledged from an MBean.
     *
     * <p>Tries comma-separated read first; on failure or unexpected response format, falls back
     * to separate reads.
     */
    private QueueCounters readCountersForMbean(String mbean) throws Exception {
        try {
            return readCountersCommaJoined(mbean);
        } catch (Exception e) {
            // Fallback to separate reads if comma-joined fails
            return readCountersSeparate(mbean);
        }
    }

    /**
     * Attempts to read all three attributes in a single Jolokia call.
     */
    private QueueCounters readCountersCommaJoined(String mbean) throws Exception {
        JsonObject response = jolokia(
                "/console/jolokia/read/" + encode(mbean) + "/MessageCount,MessagesAdded,MessagesAcknowledged");

        // Check if response has nested object format (multiple attributes)
        var value = response.get("value");
        if (value instanceof JsonObject valueObj) {
            // Multiple attributes: {"value": {"MessageCount": 42, "MessagesAdded": 100, ...}}
            long depth = valueObj.getInt("MessageCount");
            Long enqueued = valueObj.containsKey("MessagesAdded") ? (long) valueObj.getInt("MessagesAdded") : null;
            Long acknowledged = valueObj.containsKey("MessagesAcknowledged") ? (long) valueObj.getInt("MessagesAcknowledged") : null;
            return new QueueCounters(depth, enqueued, acknowledged);
        } else {
            // Single attribute format, fall back to separate reads
            throw new IllegalStateException("Unexpected response format from comma-joined read");
        }
    }

    /**
     * Falls back to separate Jolokia reads for each attribute.
     */
    private QueueCounters readCountersSeparate(String mbean) throws Exception {
        long depth = (long) jolokia("/console/jolokia/read/" + encode(mbean) + "/MessageCount")
                .getInt("value");

        Long enqueued = null;
        Long acknowledged = null;

        try {
            var response = jolokia("/console/jolokia/read/" + encode(mbean) + "/MessagesAdded");
            if (response.containsKey("value")) {
                enqueued = (long) response.getInt("value");
            }
        } catch (Exception e) {
            // MessagesAdded not available, leave as null
        }

        try {
            var response = jolokia("/console/jolokia/read/" + encode(mbean) + "/MessagesAcknowledged");
            if (response.containsKey("value")) {
                acknowledged = (long) response.getInt("value");
            }
        } catch (Exception e) {
            // MessagesAcknowledged not available, leave as null
        }

        return new QueueCounters(depth, enqueued, acknowledged);
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
