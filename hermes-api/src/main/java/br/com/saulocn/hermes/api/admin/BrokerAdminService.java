package br.com.saulocn.hermes.api.admin;

import br.com.saulocn.hermes.api.admin.vo.AdminVOs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Reads queue depth from whichever broker is running.
 *
 * <p>Uses the JDK HTTP client rather than adding a REST client extension: these are two plain
 * authenticated GETs, and the module's JAX-RS stack is the classic one, so pulling in a client
 * would mean picking a matching flavour for no benefit.
 *
 * <p>Depth is best-effort by design. Both management APIs live outside this repo's control (the
 * Artemis console comes from a base image whose Jolokia policy is not in the tree), so a failure
 * returns null depths plus the reason instead of failing the request — the console degrades to
 * showing "unavailable" rather than breaking.
 */
@ApplicationScoped
public class BrokerAdminService {

    private static final String MAIN_QUEUE_ARTEMIS = "jms.queue.MailQueue";
    private static final String MAIN_QUEUE_RABBIT = "MailQueue";
    private static final String DLQ_RABBIT = "MailQueueDLQ";

    @Inject
    Logger log;

    @ConfigProperty(name = "hermes.broker.kind", defaultValue = "artemis")
    String brokerKind;

    @ConfigProperty(name = "hermes.broker.host", defaultValue = "localhost")
    String brokerHost;

    @ConfigProperty(name = "hermes.broker.management-port", defaultValue = "8161")
    int managementPort;

    @ConfigProperty(name = "hermes.broker.username", defaultValue = "hermes")
    String username;

    @ConfigProperty(name = "hermes.broker.password", defaultValue = "pass_hermes")
    String password;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AdminVOs.BrokerStatus status() {
        try {
            return "rabbit".equalsIgnoreCase(brokerKind) ? rabbitStatus() : artemisStatus();
        } catch (Exception e) {
            log.warnf("Could not read broker depth from %s: %s", brokerKind, e.toString());
            return new AdminVOs.BrokerStatus(brokerKind, null, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private AdminVOs.BrokerStatus rabbitStatus() throws Exception {
        Long main = rabbitQueueDepth(MAIN_QUEUE_RABBIT);
        Long dlq = rabbitQueueDepth(DLQ_RABBIT);
        return new AdminVOs.BrokerStatus("rabbit", main, dlq, null);
    }

    /** %2F is the default vhost "/" — it has to stay percent-encoded in the path. */
    private Long rabbitQueueDepth(String queue) throws Exception {
        JsonObject body = getJson(URI.create(
                "http://" + brokerHost + ":" + managementPort + "/api/queues/%2F/" + queue));
        return body.containsKey("messages") ? (long) body.getInt("messages") : null;
    }

    private AdminVOs.BrokerStatus artemisStatus() throws Exception {
        Long main = artemisQueueDepth(MAIN_QUEUE_ARTEMIS);
        // Artemis auto-creates the dead letter queue only once something lands in it, so a
        // missing DLQ is normal and reads as zero rather than as an error.
        Long dlq;
        try {
            dlq = artemisQueueDepth(MAIN_QUEUE_ARTEMIS + "DLQ");
        } catch (Exception e) {
            dlq = 0L;
        }
        return new AdminVOs.BrokerStatus("artemis", main, dlq, null);
    }

    private Long artemisQueueDepth(String queue) throws Exception {
        // The broker name is part of the MBean name and is not "hermes" — the base image names it
        // after the bind address ("0.0.0.0"). Rather than hardcode that, ask Jolokia to find the
        // queue MBean with broker=* and then read whatever name it returns, so this survives a
        // different image or a renamed broker.
        String pattern = "org.apache.activemq.artemis:broker=*,component=addresses,address=\"" + queue
                + "\",subcomponent=queues,routing-type=\"anycast\",queue=\"" + queue + "\"";

        JsonObject search = getJson(URI.create("http://" + brokerHost + ":" + managementPort
                + "/console/jolokia/search/" + java.net.URLEncoder.encode(pattern, StandardCharsets.UTF_8)));
        failIfJolokiaError(search);

        var matches = search.getJsonArray("value");
        if (matches == null || matches.isEmpty()) {
            throw new IllegalStateException("no MBean for queue " + queue);
        }

        JsonObject read = getJson(URI.create("http://" + brokerHost + ":" + managementPort
                + "/console/jolokia/read/"
                + java.net.URLEncoder.encode(matches.getString(0), StandardCharsets.UTF_8)
                + "/MessageCount"));
        failIfJolokiaError(read);

        return (long) read.getInt("value");
    }

    /** Jolokia answers HTTP 200 with an error body, so the status code alone is not enough. */
    private static void failIfJolokiaError(JsonObject body) {
        if (body.containsKey("error")) {
            throw new IllegalStateException(body.getString("error"));
        }
    }

    private JsonObject getJson(URI uri) throws Exception {
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Basic " + credentials)
                .header("Accept", "application/json")
                // Jolokia rejects requests it considers cross-origin unless this is set.
                .header("Origin", "http://" + brokerHost)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + uri.getHost());
        }
        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            return reader.readObject();
        }
    }
}
