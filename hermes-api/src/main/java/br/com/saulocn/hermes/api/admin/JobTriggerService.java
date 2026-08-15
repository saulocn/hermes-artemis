package br.com.saulocn.hermes.api.admin;

import br.com.saulocn.hermes.api.admin.vo.AdminVOs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fires the batch jobs on demand, so an operator does not have to wait out the 30s (enqueue) or
 * 10 minute (fallback) schedules.
 *
 * <p>The jobs live in hermes-enqueuer — that is where the JobOperator and the reader/processor/
 * writer beans are. This forwards to its HTTP endpoint rather than duplicating the batch wiring.
 */
@ApplicationScoped
public class JobTriggerService {

    @ConfigProperty(name = "hermes.enqueuer.url", defaultValue = "http://localhost:8082")
    String enqueuerUrl;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AdminVOs.JobStarted trigger(String job) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(enqueuerUrl + "/jobs/" + job))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("enqueuer returned HTTP " + response.statusCode());
            }
            try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
                JsonObject body = reader.readObject();
                return new AdminVOs.JobStarted((long) body.getInt("executionId"));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("could not reach the enqueuer at " + enqueuerUrl, e);
        }
    }
}
