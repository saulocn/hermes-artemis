package br.com.saulocn.hermes.enqueuer.batch;

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
 * A real RabbitMQ broker, with the outgoing address pointed at a queue that does not exist.
 *
 * <p>RabbitMQ does not auto-create queues for AMQP 1.0 {@code /queues/<name>} addresses, so every
 * publish is refused. That is the point: it lets a test assert what the enqueuer does with the
 * database when the broker did NOT accept the message. The in-memory connector cannot express
 * this — its sink acknowledges everything it receives.
 */
public class RejectingBrokerTestResource implements QuarkusTestResourceLifecycleManager {

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
        config.put("MQ_MAIL_ADDRESS", "/queues/NoSuchQueue");
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
