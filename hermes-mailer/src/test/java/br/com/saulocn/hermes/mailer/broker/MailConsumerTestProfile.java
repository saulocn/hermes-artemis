package br.com.saulocn.hermes.mailer.broker;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Config lives here instead of src/test/resources/application.properties because the repo
 * .gitignore excludes **&#47;application.properties, which would make the suite unrunnable
 * on a clean clone. Datasource and Redis are left unset on purpose so Dev Services provide
 * them; the broker coordinates come from the test resource.
 */
public class MailConsumerTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();

        config.put("quarkus.datasource.db-kind", "postgresql");
        config.put("quarkus.hibernate-orm.database.default-schema", "hermes");
        config.put("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");
        config.put("quarkus.transaction-manager.default-transaction-timeout", "30s");
        config.put("quarkus.http.test-port", "0");

        // Mirrors application.sample.properties, including the env-driven address.
        config.put("mp.messaging.outgoing.mail-requests.connector", "smallrye-amqp");
        config.put("mp.messaging.outgoing.mail-requests.durable", "true");
        config.put("mp.messaging.outgoing.mail-requests.address", "${MQ_MAIL_ADDRESS:jms.queue.MailQueue}");
        config.put("mp.messaging.incoming.mail.connector", "smallrye-amqp");
        config.put("mp.messaging.incoming.mail.failure-strategy", "modified-failed");
        config.put("mp.messaging.incoming.mail.durable", "true");
        config.put("mp.messaging.incoming.mail.address", "${MQ_MAIL_ADDRESS:jms.queue.MailQueue}");

        config.put("quarkus.mailer.mock", "true");
        config.put("quarkus.mailer.from", "hermes-test@localhost");

        return config;
    }
}
