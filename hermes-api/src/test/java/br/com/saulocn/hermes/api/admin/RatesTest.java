package br.com.saulocn.hermes.api.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Rates arithmetic.
 *
 * <p>Tests the pure arithmetic logic of rate calculations. Gap-filling logic
 * is now handled by RecipientRollup and tested via RecipientRollupIT with
 * real database data.
 */
class RatesTest {

    @Test
    void ratePerSecondIsCountDividedByWindow() {
        Rates.RateStats stats = Rates.rate(100, 10.0, null);
        assertEquals(10.0, stats.ratePerSecond(), 0.0001);
        assertNull(stats.sustainedPerSecond());
    }

    @Test
    void sustainedPerSecondNeedsAtLeastTwoRows() {
        // Count < 2: no sustained rate
        Rates.RateStats stats = Rates.rate(1, 10.0, 5.0);
        assertNull(stats.sustainedPerSecond());

        // Count >= 2: sustained rate calculated
        stats = Rates.rate(2, 10.0, 5.0);
        assertEquals(0.4, stats.sustainedPerSecond(), 0.0001);
    }

    @Test
    void sustainedPerSecondIsCountDividedBySpan() {
        Rates.RateStats stats = Rates.rate(60, 10.0, 5.0);
        assertEquals(12.0, stats.sustainedPerSecond(), 0.0001);
    }

    @Test
    void spanIsFlooredAt1() {
        // span=0: floor to 1
        Rates.RateStats stats = Rates.rate(10, 60.0, 0.0);
        assertEquals(10.0, stats.sustainedPerSecond(), 0.0001);

        // span < 0 would be impossible in practice, but floor still applies
        stats = Rates.rate(10, 60.0, -0.5);
        assertEquals(10.0, stats.sustainedPerSecond(), 0.0001);
    }

    @Test
    void nullSpanYieldsNullSustainedRate() {
        Rates.RateStats stats = Rates.rate(10, 60.0, null);
        assertNull(stats.sustainedPerSecond());
    }
}
