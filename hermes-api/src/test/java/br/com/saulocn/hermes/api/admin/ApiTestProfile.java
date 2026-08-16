package br.com.saulocn.hermes.api.admin;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile for contract tests. Configures the API test environment with database,
 * Redis, and broker settings (the latter pointing to unreachable ports to test graceful
 * degradation).
 */
public class ApiTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.db-kind", "postgresql");
        config.put("quarkus.hibernate-orm.database.default-schema", "hermes");
        config.put("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");
        config.put("quarkus.transaction-manager.default-transaction-timeout", "30s");
        config.put("quarkus.http.test-port", "0");
        config.put("hermes.broker.host", "127.0.0.1");
        config.put("hermes.broker.management-port", "1");
        config.put("hermes.broker.queue", "");
        config.put("hermes.enqueuer.url", "http://127.0.0.1:1");
        return config;
    }
}
