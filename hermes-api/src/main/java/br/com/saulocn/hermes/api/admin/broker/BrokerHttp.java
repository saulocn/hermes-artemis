package br.com.saulocn.hermes.api.admin.broker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Authenticated JSON GET, and nothing broker-specific.
 *
 * <p>It lives apart from the adapters because it used to be mixed in with them, which is how the
 * {@code Origin} header — required only by Jolokia — ended up being sent to RabbitMQ as well.
 * Headers that only one broker needs are now that adapter's business.
 *
 * <p>The JDK client rather than a REST client extension: these are plain authenticated GETs, and
 * the module's JAX-RS stack is the classic one, so pulling in a client would mean matching
 * flavours for no benefit.
 */
@ApplicationScoped
public class BrokerHttp {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public JsonObject get(URI uri, String username, String password, Map<String, String> extraHeaders)
            throws Exception {
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Basic " + credentials)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET();
        extraHeaders.forEach(request::header);

        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new QueueNotFound(uri.getPath());
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + uri.getHost());
        }
        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            return reader.readObject();
        }
    }

    /** The broker answered, and said the queue is not there. Distinct from being unreachable. */
    public static class QueueNotFound extends RuntimeException {
        public QueueNotFound(String what) {
            super("queue not found: " + what);
        }
    }
}
