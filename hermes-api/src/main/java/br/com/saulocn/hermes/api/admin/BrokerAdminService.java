package br.com.saulocn.hermes.api.admin;

import br.com.saulocn.hermes.api.admin.broker.BrokerAdmin;
import br.com.saulocn.hermes.api.admin.broker.QueueReading;
import br.com.saulocn.hermes.api.admin.vo.AdminVOs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Picks the broker adapter and turns its answer — or its failure — into what the console reads.
 *
 * <p>Selection is at runtime, not build time, on purpose: the same hermes-api image runs under
 * both compose profiles, so a build-time choice would need two images.
 *
 * <p>Depth is best-effort by design. Both management APIs live outside this repo's control (the
 * Artemis console comes from a base image whose Jolokia policy is not in the tree), so a failure
 * returns null depths plus the reason instead of failing the request.
 */
@ApplicationScoped
public class BrokerAdminService {

    @Inject
    Logger log;

    @Inject
    Instance<BrokerAdmin> adapters;

    @ConfigProperty(name = "hermes.broker.kind", defaultValue = "artemis")
    String brokerKind;

    private final Clock clock;
    private final AtomicReference<TimedReading> previousSample = new AtomicReference<>();

    @Inject
    public BrokerAdminService(Clock clock) {
        this.clock = clock;
    }

    /**
     * A reading, when it was taken, and the rate derived from it.
     *
     * <p>The rate is carried along because of the minimum-interval rule: when two polls arrive
     * too close together, dividing by that interval produces noise, so the previous answer is
     * repeated. Repeating it requires having kept it.
     */
    private record TimedReading(QueueReading reading, Instant instant, Double rate) {
    }

    /**
     * Below this, two polls are too close for the difference to mean anything: the counters move
     * in whole messages, so a 50 ms gap turns one message into 20/s.
     */
    private static final long MIN_INTERVAL_MILLIS = 500;

    public AdminVOs.BrokerStatus status() {
        BrokerAdmin adapter = adapterFor(brokerKind);
        if (adapter == null) {
            // Used to fall through to Artemis, so a typo in BROKER_KIND produced Artemis readings
            // labelled with the typo — a kind the console's own type does not admit.
            String known = adapters.stream().map(BrokerAdmin::kind).sorted().toList().toString();
            return new AdminVOs.BrokerStatus(brokerKind, null, null, null,
                    "unknown broker kind: " + brokerKind + ", known: " + known);
        }

        try {
            QueueReading reading = adapter.read();
            Double ackRate = sampleAndRate(reading, clock.instant());
            return new AdminVOs.BrokerStatus(adapter.kind(), reading.main(), reading.dlq(),
                    ackRate, null);
        } catch (Exception e) {
            log.warnf("Could not read broker depth from %s: %s", adapter.kind(), e.toString());
            return new AdminVOs.BrokerStatus(adapter.kind(), null, null, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Takes one sample and returns the acknowledged-per-second rate against the previous one.
     *
     * <p>Cumulative counters differenced over time, not depth differenced over time: a queue that
     * is filled and drained at the same speed has a flat depth, so a depth-derived rate would read
     * zero exactly when the pipeline is busiest. Depth tells you the backlog; only the broker's own
     * counters tell you the flow.
     *
     * <p>Null rather than zero in three cases, because each means "cannot say", not "nothing is
     * moving": the broker did not report the counter, there is no predecessor to difference
     * against, or the counter went backwards — which means the broker restarted and its cumulative
     * total began again.
     *
     * @return messages acknowledged per second, or null when it cannot be derived
     */
    // Package-private so the arithmetic can be tested without CDI: the three rules below are
    // the whole value of this class, and reaching them through status() would mean faking an
    // Instance<BrokerAdmin> to prove a division.
    Double sampleAndRate(QueueReading reading, Instant now) {
        TimedReading previous = previousSample.get();
        Long acknowledged = reading.acknowledged();

        if (acknowledged == null || previous == null || previous.reading().acknowledged() == null) {
            previousSample.set(new TimedReading(reading, now, null));
            return null;
        }

        long earlier = previous.reading().acknowledged();
        if (acknowledged < earlier) {
            previousSample.set(new TimedReading(reading, now, null));
            return null;
        }

        long elapsedMillis = now.toEpochMilli() - previous.instant().toEpochMilli();
        if (elapsedMillis < MIN_INTERVAL_MILLIS) {
            // Keep the older sample: replacing it with one taken milliseconds later would make the
            // next interval short too, and the rate would never recover a usable baseline.
            return previous.rate();
        }

        double rate = (acknowledged - earlier) * 1000.0 / elapsedMillis;
        previousSample.set(new TimedReading(reading, now, rate));
        return rate;
    }

    private BrokerAdmin adapterFor(String kind) {
        return adapters.stream()
                .filter(a -> a.kind().equalsIgnoreCase(kind))
                .findFirst()
                .orElse(null);
    }

}
