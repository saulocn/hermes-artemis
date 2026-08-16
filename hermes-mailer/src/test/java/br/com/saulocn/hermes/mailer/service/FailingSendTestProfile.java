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
 * the whole subject — but the lock is never reached. Instead, the second transaction fails to
 * acquire a JDBC connection from the pool.
 *
 * <p>The 30s transaction timeout is deliberate but NOT the proof mechanism. The real constraint
 * is Agroal's default 5-second connection-acquisition timeout on a pool of size 2: when the first
 * transaction holds one connection and the second waits for another, Agroal times out in ~5s with
 * "Unable to acquire JDBC Connection [Sorry, acquisition timeout!]", never reaching the row lock.
 * That makes the test a proof rather than a stopwatch — the failure is deterministic, not a race.
 * The 30s timeout is only headroom, so the pool timeout is what fires.
 *
 * <p><strong>The pool size is pinned here on purpose.</strong> It used to be inherited from
 * application.properties, where it has since moved to {@code MAIL_WORKERS} and a default of ten —
 * which would have quietly changed what this test exercises without changing a line of it. A test
 * whose mechanism lives in someone else's config is a test that can be disarmed by accident.
 */
public class FailingSendTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();

        config.put("quarkus.datasource.db-kind", "postgresql");
        config.put("quarkus.datasource.jdbc.max-size", "2");
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
