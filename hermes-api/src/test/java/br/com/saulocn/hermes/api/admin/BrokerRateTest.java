package br.com.saulocn.hermes.api.admin;

import br.com.saulocn.hermes.api.admin.broker.QueueReading;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The drain rate: cumulative counters differenced over time.
 *
 * <p>Plain JUnit, no Quarkus, no broker — the whole point of the seam is that this arithmetic can
 * be proven without either. Time is stepped through an injected {@link Clock}; nothing sleeps.
 *
 * <p>These replace four tests that called the service with no reading at all and asserted null
 * for every case, including one whose own comment said the answer should be 100/s. They passed,
 * and they proved nothing — the method under test returned null unconditionally.
 */
class BrokerRateTest {

    /** A Clock the test moves by hand. */
    private static final class SteppedClock extends Clock {
        private Instant now = Instant.parse("2026-08-17T12:00:00Z");

        void advance(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static QueueReading acked(long acknowledged) {
        return new QueueReading(0L, 0L, null, acknowledged);
    }

    @Test
    void theFirstSampleHasNothingToDifferenceAgainst() {
        BrokerAdminService service = new BrokerAdminService(Clock.systemUTC());
        SteppedClock clock = new SteppedClock();

        assertNull(service.sampleAndRate(acked(100), clock.instant()),
                "no predecessor means the rate cannot be derived — null, not zero");
    }

    @Test
    void differencesTwoCountersOverTheElapsedTime() {
        BrokerAdminService service = new BrokerAdminService(Clock.systemUTC());
        SteppedClock clock = new SteppedClock();

        service.sampleAndRate(acked(1_000), clock.instant());
        clock.advance(2_000);

        // 500 messages in 2 seconds.
        assertEquals(250.0, service.sampleAndRate(acked(1_500), clock.instant()), 0.0001);
    }

    @Test
    void aCounterGoingBackwardsMeansTheBrokerRestarted() {
        BrokerAdminService service = new BrokerAdminService(Clock.systemUTC());
        SteppedClock clock = new SteppedClock();

        service.sampleAndRate(acked(9_000), clock.instant());
        clock.advance(1_000);

        // Cumulative totals only ever grow, so a smaller number is a new broker, not negative
        // throughput. Answering with the difference would print a large negative rate.
        assertNull(service.sampleAndRate(acked(12), clock.instant()));

        // And the restarted counter becomes the new baseline, so the next interval reads normally.
        clock.advance(1_000);
        assertEquals(88.0, service.sampleAndRate(acked(100), clock.instant()), 0.0001);
    }

    @Test
    void pollsTooCloseTogetherRepeatTheLastAnswerInsteadOfAmplifyingNoise() {
        BrokerAdminService service = new BrokerAdminService(Clock.systemUTC());
        SteppedClock clock = new SteppedClock();

        service.sampleAndRate(acked(0), clock.instant());
        clock.advance(1_000);
        Double established = service.sampleAndRate(acked(300), clock.instant());
        assertEquals(300.0, established, 0.0001);

        // 100 ms later: one more message would otherwise read as 10/s and the number would jump
        // around with the operator's refresh setting rather than with the pipeline.
        clock.advance(100);
        assertEquals(300.0, service.sampleAndRate(acked(301), clock.instant()), 0.0001);
    }

    @Test
    void aBrokerThatWillNotReportTheCounterYieldsNullNotZero() {
        BrokerAdminService service = new BrokerAdminService(Clock.systemUTC());
        SteppedClock clock = new SteppedClock();

        service.sampleAndRate(acked(500), clock.instant());
        clock.advance(1_000);

        // Zero here would claim the queue is idle. It is not a claim we can make.
        assertNull(service.sampleAndRate(new QueueReading(0L, 0L, null, null), clock.instant()));
    }
}
