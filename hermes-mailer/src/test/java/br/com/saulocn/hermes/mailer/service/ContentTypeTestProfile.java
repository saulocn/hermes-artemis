package br.com.saulocn.hermes.mailer.service;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile for contentType tests: uses in-memory broker channels instead of a real broker,
 * so no broker container is required.
 */
public class ContentTypeTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();

        config.put("quarkus.datasource.db-kind", "postgresql");
        config.put("quarkus.hibernate-orm.database.default-schema", "hermes");
        config.put("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");
        config.put("quarkus.transaction-manager.default-transaction-timeout", "30s");
        config.put("quarkus.http.test-port", "0");

        config.put("quarkus.mailer.mock", "true");
        config.put("quarkus.mailer.from", "hermes-test@localhost");

        // Use in-memory channels instead of AMQP, so no broker container is needed
        config.putAll(InMemoryConnector.switchIncomingChannelsToInMemory("mail"));
        config.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("mail-requests"));

        return config;
    }
}
