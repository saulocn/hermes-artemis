package br.com.saulocn.hermes.api.admin;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Pure arithmetic for dashboard rate calculations. No database or CDI dependencies,
 * so these functions can be tested without a running container.
 *
 * <p>Kept apart from {@code DashboardService} for testability: the service calls the database,
 * and these functions operate on the results.
 */
public class Rates {

    private Rates() {
    }

    /**
     * Per-stage statistics: how loaded it is right now, and how much work it has sustained.
     *
     * @param count          number of rows in the time window
     * @param windowSeconds  seconds in the window (> 0)
     * @param spanSeconds    elapsed time from the column's first to last row, in seconds;
     *                       null if fewer than 2 rows or the span is null
     * @return stats with both rates, or nulls for rates that cannot be calculated
     */
    public static RateStats rate(long count, double windowSeconds, Double spanSeconds) {
        double ratePerSecond = count / windowSeconds;

        // Sustained rate = count / max(span, 1), matching bench.sh's convention at line 84:
        // https://github.com/saulocn/hermes-artemis/blob/main/bench/bench.sh
        // This is "the answer to the question: if the pipeline ran 300/s in the window
        // but bench.sh reported 1200/s for the same run, which is right?"
        // The division count/span gives the true sustained throughput, ignoring idle gaps.
        Double sustainedPerSecond = null;
        if (spanSeconds != null && count >= 2) {
            double span = Math.max(spanSeconds, 1.0);
            sustainedPerSecond = count / span;
        }

        return new RateStats(ratePerSecond, sustainedPerSecond);
    }

    /**
     * Fill gaps in a per-minute bucketed series.
     *
     * <p>Buckets are (minute -> count) from the database. For each minute in the window,
     * produce a point: the count (which may be 0), or null for minutes earlier than the
     * stage's first sample. The in-progress minute is excluded (today's chart always ends
     * in a dip because the last bucket is partial).
     *
     * <p>Without the null fill, the first hour after a new column is deployed reads as
     * "the pipeline is dead" (zero counts) rather than "the column is new" (nulls),
     * which is why the null fill is mandatory.
     *
     * @param window           start (inclusive) and end (exclusive) of the time window
     * @param firstSampleTime  the earliest timestamp ever recorded for this stage, or null
     *                         if no samples exist yet
     * @param buckets          (minute -> count) from the database, in order
     * @return one point per minute in the window (oldest first), with nulls for unsampled
     *         minutes before firstSampleTime, zeros for gaps, and excluding the in-progress minute
     */
    public static List<Long> fillGaps(
            TimeWindow window,
            LocalDateTime firstSampleTime,
            Map<LocalDateTime, Long> buckets) {

        List<Long> points = new ArrayList<>();

        // The in-progress minute is excluded: truncate end to the previous minute.
        LocalDateTime windowEnd = window.end().withSecond(0).withNano(0);
        if (windowEnd.isAfter(window.end())) {
            windowEnd = windowEnd.minusMinutes(1);
        }

        LocalDateTime minute = window.start().withSecond(0).withNano(0);
        while (minute.isBefore(windowEnd)) {
            if (firstSampleTime != null && minute.isBefore(firstSampleTime)) {
                // Before the column existed: null
                points.add(null);
            } else {
                // After the column existed: count (0 if gap)
                points.add(buckets.getOrDefault(minute, 0L));
            }
            minute = minute.plusMinutes(1);
        }

        return points;
    }

    public record RateStats(double ratePerSecond, Double sustainedPerSecond) {
    }

    public record TimeWindow(LocalDateTime start, LocalDateTime end) {
        public TimeWindow {
            if (start == null || end == null) {
                throw new IllegalArgumentException("start and end cannot be null");
            }
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("start must be before end");
            }
        }
    }
}
