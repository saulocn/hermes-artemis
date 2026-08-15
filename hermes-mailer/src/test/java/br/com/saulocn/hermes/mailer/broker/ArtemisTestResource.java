package br.com.saulocn.hermes.mailer.broker;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts the same Artemis image and broker.xml the compose `artemis` profile uses.
 * Deliberately does not set MQ_MAIL_ADDRESS, so the test also covers the default address.
 */
public class ArtemisTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String IMAGE = "saulocn/artemismq:2.22.0";
    private static final int AMQP_PORT = 5672;

    private GenericContainer<?> artemis;

    @Override
    public Map<String, String> start() {
        Path brokerXml = Paths.get("..", "artemis", "broker.xml").toAbsolutePath().normalize();

        artemis = new GenericContainer<>(IMAGE)
                .withCopyFileToContainer(MountableFile.forHostPath(brokerXml, 0644), "/artemis/etc/broker.xml")
                .withEnv("ARTEMIS_BROKER", "hermes")
                .withEnv("ARTEMIS_USER", "hermes")
                .withEnv("ARTEMIS_PASSWORD", "pass_hermes")
                .withCommand("/bin/sh", "/artemis/artemis.sh")
                .withExposedPorts(AMQP_PORT)
                .waitingFor(Wait.forLogMessage(".*AMQ221007.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(5)));
        artemis.start();

        Map<String, String> config = new HashMap<>();
        config.put("amqp-host", artemis.getHost());
        config.put("amqp-port", String.valueOf(artemis.getMappedPort(AMQP_PORT)));
        config.put("amqp-username", "hermes");
        config.put("amqp-password", "pass_hermes");
        return config;
    }

    @Override
    public void stop() {
        if (artemis != null) {
            artemis.stop();
        }
    }
}
