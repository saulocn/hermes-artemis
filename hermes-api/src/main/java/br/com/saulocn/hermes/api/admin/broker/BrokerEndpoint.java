package br.com.saulocn.hermes.api.admin.broker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Where the broker's management interface is, how to authenticate to it, and the main queue name
 * it reports on.
 *
 * <p>The queue name is configurable to match the {@code MQ_MAIL_ADDRESS} environment variable used
 * by the enqueuer and mailer. Each broker adapter falls back to its own default when the config
 * property is blank: Artemis defaults to {@code jms.queue.MailQueue}, RabbitMQ to {@code MailQueue}.
 */
public record BrokerEndpoint(String host, int port, String username, String password, String queue) {

    public String baseUrl() {
        return "http://" + host + ":" + port;
    }

    /**
     * Produced from config so the adapters can take it as a constructor argument. That is what
     * lets them be built directly in a unit test, with no CDI and no broker.
     */
    @ApplicationScoped
    public static class FromConfig {

        // @Singleton, not @ApplicationScoped: a normal-scoped producer needs a client proxy and
        // records are final, so CDI refuses. A pseudo-scope needs no proxy.
        @Produces
        @Singleton
        BrokerEndpoint endpoint(
                @ConfigProperty(name = "hermes.broker.host", defaultValue = "localhost") String host,
                @ConfigProperty(name = "hermes.broker.management-port", defaultValue = "8161") int port,
                @ConfigProperty(name = "hermes.broker.username", defaultValue = "hermes") String username,
                @ConfigProperty(name = "hermes.broker.password", defaultValue = "pass_hermes") String password,
                @ConfigProperty(name = "hermes.broker.queue") Optional<String> queue) {
            return new BrokerEndpoint(host, port, username, password, queue.orElse(""));
        }
    }
}
