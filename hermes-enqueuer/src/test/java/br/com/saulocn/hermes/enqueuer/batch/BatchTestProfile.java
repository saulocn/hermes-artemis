package br.com.saulocn.hermes.enqueuer.batch;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.HashMap;
import java.util.Map;

/**
 * Batch logic is broker-independent, so the outgoing channels are swapped for the in-memory
 * connector: no Artemis or RabbitMQ needed here. Broker wiring is covered by the mailer's
 * MailConsumerArtemisIT / MailConsumerRabbitIT.
 *
 * <p>Config lives in this profile rather than in src/test/resources/application.properties
 * because the module's versioned application.properties pins quarkus.datasource.jdbc.url
 * and quarkus.redis.hosts, which switches Dev Services off. A test-resource/profile value
 * outranks it, so the suite behaves the same whatever a developer has locally.
 */
public class BatchTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();

        config.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("mail-requests"));

        config.put("quarkus.datasource.db-kind", "postgresql");
        config.put("quarkus.hibernate-orm.database.default-schema", "hermes");
        config.put("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");
        config.put("quarkus.transaction-manager.default-transaction-timeout", "30s");
        config.put("quarkus.http.test-port", "0");

        // The tests start the jobs explicitly; the @Scheduled triggers would race with them.
        config.put("quarkus.scheduler.enabled", "false");

        return config;
    }
}
