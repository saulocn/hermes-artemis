package br.com.saulocn.hermes.api.admin;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JobTriggerService} is hermes-api's only line into the enqueuer's batch runtime: it POSTs
 * to {@code /jobs/{job}} and has to turn whatever comes back — success, a declined 409, or no
 * connection at all — into something {@code AdminResource} can act on. That translation is the
 * whole value of the class, and before this file it had no test at any layer.
 *
 * <p>Each case is driven against a real (stubbed) HTTP server rather than asserted by reading the
 * code, because the interesting behaviour lives entirely in how {@code java.net.http} status
 * codes and connection failures get mapped — exactly the part a mock would paper over.
 *
 * <p>No Quarkus, no container: {@code enqueuerUrl} is package-private and this test lives in the
 * same package, so it is set directly on a plain {@code new JobTriggerService()}. That keeps this
 * a fast unit test (surefire), leaving the container-backed HTTP-status-mapping question (what an
 * operator's browser actually receives) to {@link AdminJobTriggerIT}.
 */
class JobTriggerServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void a409FromTheEnqueuerBecomesJobAlreadyRunningException() throws IOException {
        server = stubResponding(409, "{\"error\":\"job already running\",\"job\":\"enqueue\"}");
        JobTriggerService service = serviceFor(server);

        JobTriggerService.JobAlreadyRunningException thrown = assertThrows(
                JobTriggerService.JobAlreadyRunningException.class, () -> service.trigger("enqueue"));

        assertTrue(thrown.getMessage().contains("enqueue"));
    }

    @Test
    void a200FromTheEnqueuerReturnsItsExecutionId() throws IOException {
        server = stubResponding(200, "{\"executionId\":42}");
        JobTriggerService service = serviceFor(server);

        var started = service.trigger("enqueue");

        assertEquals(42L, started.executionId());
    }

    /**
     * "Cannot reach the enqueuer" has its own type, so the resource can answer 502 instead of
     * letting a plain {@code IllegalStateException} reach the default exception mapper as a 500.
     * The distinction matters to the operator: a 500 blames this api, and the api is fine.
     *
     * <p>See {@link AdminJobTriggerIT} for the same case through the full HTTP stack.
     */
    @Test
    void anUnreachableEnqueuerThrowsItsOwnTypeSoTheResourceCanTellItApart() {
        JobTriggerService service = new JobTriggerService();
        service.enqueuerUrl = "http://127.0.0.1:1"; // nothing listens here: connection refused

        var thrown = assertThrows(JobTriggerService.EnqueuerUnreachableException.class,
                () -> service.trigger("enqueue"));

        assertTrue(thrown.getMessage().contains("could not reach the enqueuer"));
    }

    private static JobTriggerService serviceFor(HttpServer server) {
        JobTriggerService service = new JobTriggerService();
        service.enqueuerUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return service;
    }

    private static HttpServer stubResponding(int status, String jsonBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jobs/enqueue", exchange -> {
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
