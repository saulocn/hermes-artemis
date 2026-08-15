package br.com.saulocn.hermes.enqueuer.batch;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Unlike {@link BatchTestProfile}, this keeps the real smallrye-amqp connector: the whole point
 * is to observe what happens when an actual broker refuses the publish.
 */
public class BrokerAckTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();

        config.put("quarkus.datasource.db-kind", "postgresql");
        config.put("quarkus.hibernate-orm.database.default-schema", "hermes");
        config.put("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");
        config.put("quarkus.transaction-manager.default-transaction-timeout", "30s");
        config.put("quarkus.http.test-port", "0");
        config.put("quarkus.scheduler.enabled", "false");

        // Mirrors application.sample.properties, so the address stays env-driven and the test
        // resource can point it at a queue the broker will refuse.
        config.put("mp.messaging.outgoing.mail-requests.connector", "smallrye-amqp");
        config.put("mp.messaging.outgoing.mail-requests.durable", "true");
        config.put("mp.messaging.outgoing.mail-requests.address", "${MQ_MAIL_ADDRESS:jms.queue.MailQueue}");

        return config;
    }
}
