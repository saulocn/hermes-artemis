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
 * Starts the same RabbitMQ image and configuration the compose `rabbit` profile uses,
 * so the test exercises the real broker definitions instead of a test-only copy.
 */
public class RabbitMqTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String IMAGE = "rabbitmq:4-management";
    private static final int AMQP_PORT = 5672;

    private GenericContainer<?> rabbit;

    @Override
    public Map<String, String> start() {
        rabbit = new GenericContainer<>(IMAGE)
                .withCopyFileToContainer(configFile("rabbitmq.conf"), "/etc/rabbitmq/rabbitmq.conf")
                .withCopyFileToContainer(configFile("definitions.json"), "/etc/rabbitmq/definitions.json")
                .withExposedPorts(AMQP_PORT)
                .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        rabbit.start();

        Map<String, String> config = new HashMap<>();
        config.put("amqp-host", rabbit.getHost());
        config.put("amqp-port", String.valueOf(rabbit.getMappedPort(AMQP_PORT)));
        config.put("amqp-username", "hermes");
        config.put("amqp-password", "pass_hermes");
        // RabbitMQ 4.x AMQP 1.0 address format v2. Proves the address is env-driven.
        config.put("MQ_MAIL_ADDRESS", "/queues/MailQueue");
        return config;
    }

    @Override
    public void stop() {
        if (rabbit != null) {
            rabbit.stop();
        }
    }

    private static MountableFile configFile(String name) {
        Path path = Paths.get("..", "rabbit", name).toAbsolutePath().normalize();
        return MountableFile.forHostPath(path, 0644);
    }
}
