package br.com.saulocn.hermes.api.admin.broker;

import br.com.saulocn.hermes.api.admin.BrokerAdminService;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests: no Quarkus, no container, no broker.
 *
 * <p>That is the point of the seam. The RabbitMQ arm had no test at all before — the only broker
 * coverage was an integration test that hit the Artemis arm, and only its failure path.
 */
class BrokerAdminTest {

    private static final BrokerEndpoint ENDPOINT = new BrokerEndpoint("broker.test", 15672, "u", "p", "");

    /** Answers canned JSON per URL substring, and records what it was asked for. */
    private static final class StubHttp extends BrokerHttp {
        private final Map<String, String> responses = new LinkedHashMap<>();
        private final List<URI> calls = new ArrayList<>();
        private RuntimeException toThrow;

        StubHttp on(String urlContains, String json) {
            responses.put(urlContains, json);
            return this;
        }

        @Override
        public JsonObject get(URI uri, String username, String password, Map<String, String> headers) {
            calls.add(uri);
            if (toThrow != null) {
                throw toThrow;
            }
            return responses.entrySet().stream()
                    .filter(e -> uri.toString().contains(e.getKey()))
                    .findFirst()
                    .map(e -> Json.createReader(new StringReader(e.getValue())).readObject())
                    .orElseThrow(() -> new BrokerHttp.QueueNotFound(uri.getPath()));
        }
    }

    // ---------------------------------------------------------------- rabbit

    @Test
    void rabbitReadsBothDepths() throws Exception {
        StubHttp http = new StubHttp()
                .on("/api/queues/%2F/MailQueueDLQ", "{\"messages\": 7, \"message_stats\": {\"publish\": 10, \"ack\": 3}}")
                .on("/api/queues/%2F/MailQueue", "{\"messages\": 42, \"message_stats\": {\"publish\": 100, \"ack\": 58}}");

        QueueReading reading = new RabbitBrokerAdmin(http, ENDPOINT).read();

        assertEquals(42L, reading.main());
        assertEquals(7L, reading.dlq());
        assertEquals(100L, reading.enqueued());
        assertEquals(58L, reading.acknowledged());
    }

    @Test
    void rabbitUsesConfiguredQueueName() throws Exception {
        BrokerEndpoint customEndpoint = new BrokerEndpoint("broker.test", 15672, "u", "p", "CustomMail");
        StubHttp http = new StubHttp()
                .on("/api/queues/%2F/CustomMailDLQ", "{\"messages\": 3, \"message_stats\": {\"publish\": 5, \"ack\": 2}}")
                .on("/api/queues/%2F/CustomMail", "{\"messages\": 99, \"message_stats\": {\"publish\": 200, \"ack\": 101}}");

        QueueReading reading = new RabbitBrokerAdmin(http, customEndpoint).read();

        assertEquals(99L, reading.main());
        assertEquals(3L, reading.dlq());
        assertEquals(200L, reading.enqueued());
        assertEquals(101L, reading.acknowledged());
        // Verify correct queue names were requested
        assertTrue(http.calls.stream().anyMatch(uri -> uri.toString().contains("CustomMail")),
                "should request the custom main queue");
        assertTrue(http.calls.stream().anyMatch(uri -> uri.toString().contains("CustomMailDLQ")),
                "should request the custom DLQ");
    }

    @Test
    void rabbitFallsBackToDefaultWhenQueueNameIsBlank() throws Exception {
        BrokerEndpoint blankEndpoint = new BrokerEndpoint("broker.test", 15672, "u", "p", "");
        StubHttp http = new StubHttp()
                .on("/api/queues/%2F/MailQueueDLQ", "{\"messages\": 7, \"message_stats\": {\"publish\": 10, \"ack\": 3}}")
                .on("/api/queues/%2F/MailQueue", "{\"messages\": 42, \"message_stats\": {\"publish\": 100, \"ack\": 58}}");

        QueueReading reading = new RabbitBrokerAdmin(http, blankEndpoint).read();

        assertEquals(42L, reading.main());
        assertEquals(7L, reading.dlq());
        // Verify default queue names were used
        assertTrue(http.calls.stream().anyMatch(uri -> uri.toString().contains("MailQueue")),
                "should use default MailQueue when blank");
    }

    @Test
    void rabbitReportsNullWhenTheBrokerHasNotCollectedStatsYet() throws Exception {
        // Right after boot the management API answers without a `messages` field. Null, not zero:
        // the broker declined to say, it did not say the queue is empty.
        StubHttp http = new StubHttp().on("/api/queues/", "{\"name\": \"MailQueue\"}");

        QueueReading reading = new RabbitBrokerAdmin(http, ENDPOINT).read();

        assertNull(reading.main());
        assertNull(reading.dlq());
        assertNull(reading.enqueued());
        assertNull(reading.acknowledged());
    }

    @Test
    void rabbitReturnsNullForCountersWhenMessageStatsNotCollectedYet() throws Exception {
        // message_stats.publish and message_stats.ack may be absent before the management
        // database has collected stats. Null, not zero.
        StubHttp http = new StubHttp()
                .on("/api/queues/%2F/MailQueueDLQ", "{\"messages\": 0}")
                .on("/api/queues/%2F/MailQueue", "{\"messages\": 10}");

        QueueReading reading = new RabbitBrokerAdmin(http, ENDPOINT).read();

        assertEquals(10L, reading.main());
        assertEquals(0L, reading.dlq());
        assertNull(reading.enqueued());
        assertNull(reading.acknowledged());
    }

    @Test
    void rabbitTreatsAMissingQueueAsZero() throws Exception {
        QueueReading reading = new RabbitBrokerAdmin(new StubHttp(), ENDPOINT).read();

        assertEquals(0L, reading.main());
        assertEquals(0L, reading.dlq());
        // Counters are null because broker gave no stats (missing queue, not just no messages)
        assertNull(reading.enqueued());
        assertNull(reading.acknowledged());
    }

    // ---------------------------------------------------------------- artemis

    @Test
    void artemisSearchesForTheMBeanThenReadsIt() throws Exception {
        StubHttp http = new StubHttp()
                .on("/console/jolokia/search/", "{\"value\": [\"org.apache.activemq.artemis:broker=\\\"0.0.0.0\\\"\"]}")
                .on("MessageCount,MessagesAdded,MessagesAcknowledged",
                    "{\"value\": {\"MessageCount\": 13, \"MessagesAdded\": 100, \"MessagesAcknowledged\": 87}}");

        QueueReading reading = new ArtemisBrokerAdmin(http, ENDPOINT).read();

        assertEquals(13L, reading.main());
        assertEquals(100L, reading.enqueued());
        assertEquals(87L, reading.acknowledged());
        // Two searches (one per queue) + two comma-joined reads (one per queue)
        assertEquals(4, http.calls.size());
        assertTrue(http.calls.get(0).toString().contains("search"));
    }

    @Test
    void artemisSurfacesAJolokiaErrorBodyDespiteHttp200() {
        StubHttp http = new StubHttp()
                .on("/console/jolokia/", "{\"error\": \"forbidden\", \"status\": 403}");

        Exception e = assertThrows(Exception.class, () -> new ArtemisBrokerAdmin(http, ENDPOINT).read());
        assertTrue(e.getMessage().contains("forbidden"));
    }

    @Test
    void artemisReportsZeroDlqWhenTheMBeanDoesNotExist() throws Exception {
        // Artemis creates the DLQ only once something is dead-lettered, so an empty search result
        // means nothing ever was. Distinct from the broker being unreachable, which still throws.
        // Matched on the queue name inside the URL-encoded MBean pattern. DLQ first: MailQueue is
        // a prefix of MailQueueDLQ, so the order of these decides which one wins.
        StubHttp http = new StubHttp()
                .on("MailQueueDLQ", "{\"value\": []}")
                .on("/console/jolokia/search/", "{\"value\": [\"mbean\"]}")
                .on("MessageCount,MessagesAdded,MessagesAcknowledged",
                    "{\"value\": {\"MessageCount\": 5, \"MessagesAdded\": 50, \"MessagesAcknowledged\": 45}}");

        QueueReading reading = new ArtemisBrokerAdmin(http, ENDPOINT).read();

        assertEquals(5L, reading.main());
        assertEquals(0L, reading.dlq());
        assertEquals(50L, reading.enqueued());
        assertEquals(45L, reading.acknowledged());
    }

    @Test
    void artemisUsesConfiguredQueueName() throws Exception {
        BrokerEndpoint customEndpoint = new BrokerEndpoint("broker.test", 15672, "u", "p", "jms.queue.CustomMail");
        StubHttp http = new StubHttp()
                .on("jms.queue.CustomMailDLQ", "{\"value\": []}")
                .on("jms.queue.CustomMail", "{\"value\": [\"org.apache.activemq.artemis:broker=\\\"0.0.0.0\\\"\"]}")
                .on("/console/jolokia/search/", "{\"value\": [\"mbean\"]}")
                .on("MessageCount,MessagesAdded,MessagesAcknowledged",
                    "{\"value\": {\"MessageCount\": 42, \"MessagesAdded\": 200, \"MessagesAcknowledged\": 158}}");

        QueueReading reading = new ArtemisBrokerAdmin(http, customEndpoint).read();

        assertEquals(42L, reading.main());
        assertEquals(0L, reading.dlq());
        assertEquals(200L, reading.enqueued());
        assertEquals(158L, reading.acknowledged());
        // Verify custom queue names were used in the search patterns
        assertTrue(http.calls.stream().anyMatch(uri -> uri.toString().contains("jms.queue.CustomMail")),
                "should request the custom main queue");
        assertTrue(http.calls.stream().anyMatch(uri -> uri.toString().contains("jms.queue.CustomMailDLQ")),
                "should request the custom DLQ");
    }

    @Test
    void artemisFallsBackToDefaultWhenQueueNameIsBlank() throws Exception {
        BrokerEndpoint blankEndpoint = new BrokerEndpoint("broker.test", 15672, "u", "p", "");
        StubHttp http = new StubHttp()
                .on("jms.queue.MailQueueDLQ", "{\"value\": []}")
                .on("/console/jolokia/search/", "{\"value\": [\"mbean\"]}")
                .on("MessageCount,MessagesAdded,MessagesAcknowledged",
                    "{\"value\": {\"MessageCount\": 13, \"MessagesAdded\": 100, \"MessagesAcknowledged\": 87}}");

        QueueReading reading = new ArtemisBrokerAdmin(http, blankEndpoint).read();

        assertEquals(13L, reading.main());
        assertEquals(0L, reading.dlq());
        // Verify default queue names were used
        assertTrue(http.calls.stream().anyMatch(uri -> uri.toString().contains("jms.queue.MailQueue")),
                "should use default jms.queue.MailQueue when blank");
    }
}
