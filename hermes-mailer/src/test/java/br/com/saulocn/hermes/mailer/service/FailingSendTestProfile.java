package br.com.saulocn.hermes.mailer.service;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Every send throws, and the queue is in memory.
 *
 * <p>No broker: what is under test is the ordering between the delivery transaction and the
 * failure counter, and AMQP has no part in that. The database is real, because the row lock is
 * the whole subject.
 *
 * <p>The 30s transaction timeout is deliberate and load-bearing. It is what makes the test a
 * proof rather than a stopwatch: counting the failure from inside the delivery transaction waits
 * on a lock only the timeout can break, so that arrangement takes at least 30 seconds to record
 * one attempt, and this test's 10-second budget cannot be met by accident.
 */
public class FailingSendTestProfile implements QuarkusTestProfile {

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

        config.putAll(InMemoryConnector.switchIncomingChannelsToInMemory("mail"));
        config.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("mail-requests"));
        return config;
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(FailingMailSender.class);
    }
}
