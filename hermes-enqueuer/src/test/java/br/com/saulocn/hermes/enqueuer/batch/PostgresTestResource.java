package br.com.saulocn.hermes.enqueuer.batch;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Postgres for the batch tests.
 *
 * <p>Started explicitly rather than via Dev Services: the module's versioned
 * application.properties pins quarkus.datasource.jdbc.url, which switches Dev Services off.
 * A test resource outranks it, so the suite behaves the same whatever a developer has
 * locally.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final int POSTGRES_PORT = 5432;

    private GenericContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        postgres = new GenericContainer<>("postgres:16-alpine")
                .withEnv("POSTGRES_DB", "hermes")
                .withEnv("POSTGRES_USER", "hermes")
                .withEnv("POSTGRES_PASSWORD", "pass_hermes")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(
                                Paths.get("src", "test", "resources", "db-init.sql").toAbsolutePath(), 0644),
                        "/docker-entrypoint-initdb.d/db-init.sql")
                .withExposedPorts(POSTGRES_PORT)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        postgres.start();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", String.format("jdbc:postgresql://%s:%d/hermes",
                postgres.getHost(), postgres.getMappedPort(POSTGRES_PORT)));
        config.put("quarkus.datasource.username", "hermes");
        config.put("quarkus.datasource.password", "pass_hermes");
        return config;
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}
