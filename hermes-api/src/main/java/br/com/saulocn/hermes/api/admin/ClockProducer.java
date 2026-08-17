package br.com.saulocn.hermes.api.admin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

/**
 * Produces the system Clock as an injectable dependency.
 *
 * <p>Separated so tests can override it with a {@code @TestProfile} to inject
 * a fixed or steppable clock instead of the system clock.
 */
@ApplicationScoped
public class ClockProducer {

    @Produces
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
