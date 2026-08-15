package br.com.saulocn.hermes.mailer.broker;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Postgres and Redis for the tests.
 *
 * <p>These are started explicitly instead of relying on Dev Services because the module's
 * application.properties (gitignored, created by each developer) hardcodes
 * quarkus.datasource.jdbc.url and quarkus.redis.hosts, which switches Dev Services off. The
 * values returned here come from a test resource, so they win over application.properties and
 * the suite behaves the same no matter what a developer has locally.
 */
public class InfraTestResource implements QuarkusTestResourceLifecycleManager {

    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;

    private GenericContainer<?> postgres;
    private GenericContainer<?> redis;

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

        redis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(REDIS_PORT)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        redis.start();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", String.format("jdbc:postgresql://%s:%d/hermes",
                postgres.getHost(), postgres.getMappedPort(POSTGRES_PORT)));
        config.put("quarkus.datasource.username", "hermes");
        config.put("quarkus.datasource.password", "pass_hermes");
        config.put("quarkus.redis.hosts", String.format("redis://%s:%d",
                redis.getHost(), redis.getMappedPort(REDIS_PORT)));
        return config;
    }

    @Override
    public void stop() {
        if (redis != null) {
            redis.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }
}
