package br.com.saulocn.hermes.api.admin;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for rate calculations. Plain JUnit, no Quarkus, no container.
 * These cover the arithmetic and gap-fill logic exhaustively.
 */
class RatesTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2024, 1, 15, 10, 0);

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

    @Test
    void fillGapsProducesOnePointPerMinute() {
        LocalDateTime start = BASE;
        LocalDateTime end = BASE.plusMinutes(3);
        Rates.TimeWindow window = new Rates.TimeWindow(start, end);

        Map<LocalDateTime, Long> buckets = new HashMap<>();
        buckets.put(BASE, 10L);
        buckets.put(BASE.plusMinutes(1), 20L);
        buckets.put(BASE.plusMinutes(2), 30L);

        List<Long> points = Rates.fillGaps(window, BASE, buckets);

        // 3 minutes (start, +1, +2), excluding the in-progress minute (+3)
        assertEquals(3, points.size());
        assertEquals(10L, points.get(0));
        assertEquals(20L, points.get(1));
        assertEquals(30L, points.get(2));
    }

    @Test
    void fillGapsIncludesZeroesForGaps() {
        LocalDateTime start = BASE;
        LocalDateTime end = BASE.plusMinutes(4);
        Rates.TimeWindow window = new Rates.TimeWindow(start, end);

        Map<LocalDateTime, Long> buckets = new HashMap<>();
        buckets.put(BASE, 10L);
        // No entry for BASE+1min
        buckets.put(BASE.plusMinutes(2), 30L);
        // No entry for BASE+3min

        List<Long> points = Rates.fillGaps(window, BASE, buckets);

        assertEquals(4, points.size());
        assertEquals(10L, points.get(0));
        assertEquals(0L, points.get(1)); // Gap
        assertEquals(30L, points.get(2));
        assertEquals(0L, points.get(3)); // Gap
    }

    @Test
    void fillGapsIncludesNullsBeforeFirstSample() {
        LocalDateTime start = BASE;
        LocalDateTime end = BASE.plusMinutes(4);
        Rates.TimeWindow window = new Rates.TimeWindow(start, end);

        LocalDateTime firstSample = BASE.plusMinutes(2);
        Map<LocalDateTime, Long> buckets = new HashMap<>();
        buckets.put(firstSample, 30L);
        buckets.put(firstSample.plusMinutes(1), 40L);

        List<Long> points = Rates.fillGaps(window, firstSample, buckets);

        assertEquals(4, points.size());
        assertNull(points.get(0)); // Before firstSample
        assertNull(points.get(1)); // Before firstSample
        assertEquals(30L, points.get(2)); // At firstSample
        assertEquals(40L, points.get(3)); // After firstSample
    }

    @Test
    void fillGapsExcludesInProgressMinute() {
        LocalDateTime start = BASE;
        LocalDateTime end = BASE.plusMinutes(2).plusSeconds(30); // In the middle of +2min
        Rates.TimeWindow window = new Rates.TimeWindow(start, end);

        Map<LocalDateTime, Long> buckets = new HashMap<>();
        buckets.put(BASE, 10L);
        buckets.put(BASE.plusMinutes(1), 20L);
        buckets.put(BASE.plusMinutes(2), 30L); // In-progress, should be excluded

        List<Long> points = Rates.fillGaps(window, BASE, buckets);

        // Only 2 points: the 0-minute and 1-minute buckets
        assertEquals(2, points.size());
        assertEquals(10L, points.get(0));
        assertEquals(20L, points.get(1));
    }

    @Test
    void fillGapsWithNullFirstSample() {
        LocalDateTime start = BASE;
        LocalDateTime end = BASE.plusMinutes(2);
        Rates.TimeWindow window = new Rates.TimeWindow(start, end);

        Map<LocalDateTime, Long> buckets = new HashMap<>();
        buckets.put(BASE, 10L);
        buckets.put(BASE.plusMinutes(1), 20L);

        // No samples exist yet
        List<Long> points = Rates.fillGaps(window, null, buckets);

        assertEquals(2, points.size());
        assertEquals(10L, points.get(0)); // Not null, because no firstSample means use what we have
        assertEquals(20L, points.get(1));
    }

    @Test
    void fillGapsHandlesEmptyBuckets() {
        LocalDateTime start = BASE;
        LocalDateTime end = BASE.plusMinutes(3);
        Rates.TimeWindow window = new Rates.TimeWindow(start, end);

        Map<LocalDateTime, Long> buckets = new HashMap<>();

        List<Long> points = Rates.fillGaps(window, BASE, buckets);

        assertEquals(3, points.size());
        assertEquals(0L, points.get(0));
        assertEquals(0L, points.get(1));
        assertEquals(0L, points.get(2));
    }

    @Test
    void fillGapsHandlesSingleMinuteWindow() {
        LocalDateTime start = BASE;
        LocalDateTime end = BASE.plusMinutes(1);
        Rates.TimeWindow window = new Rates.TimeWindow(start, end);

        Map<LocalDateTime, Long> buckets = new HashMap<>();
        buckets.put(BASE, 42L);

        List<Long> points = Rates.fillGaps(window, BASE, buckets);

        assertEquals(1, points.size());
        assertEquals(42L, points.get(0));
    }

    @Test
    void fillGapsHandlesMultipleNullRanges() {
        LocalDateTime start = BASE;
        LocalDateTime end = BASE.plusMinutes(6);
        Rates.TimeWindow window = new Rates.TimeWindow(start, end);

        LocalDateTime firstSample = BASE.plusMinutes(3);
        Map<LocalDateTime, Long> buckets = new HashMap<>();
        buckets.put(firstSample, 30L);

        List<Long> points = Rates.fillGaps(window, firstSample, buckets);

        assertEquals(6, points.size());
        assertNull(points.get(0)); // Before
        assertNull(points.get(1)); // Before
        assertNull(points.get(2)); // Before
        assertEquals(30L, points.get(3)); // At
        assertEquals(0L, points.get(4)); // Gap
        assertEquals(0L, points.get(5)); // Gap
    }

    @Test
    void timeWindowValidation() {
        LocalDateTime start = BASE;
        LocalDateTime end = BASE.plusMinutes(1);

        // Valid window
        assertDoesNotThrow(() -> new Rates.TimeWindow(start, end));

        // Null start
        assertThrows(IllegalArgumentException.class,
                () -> new Rates.TimeWindow(null, end));

        // Null end
        assertThrows(IllegalArgumentException.class,
                () -> new Rates.TimeWindow(start, null));

        // start == end
        assertThrows(IllegalArgumentException.class,
                () -> new Rates.TimeWindow(start, start));

        // start > end
        assertThrows(IllegalArgumentException.class,
                () -> new Rates.TimeWindow(end, start));
    }
}
