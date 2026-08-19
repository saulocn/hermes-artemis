package br.com.saulocn.hermes.api.admin;

/**
 * Pure arithmetic for dashboard rate calculations. No database or CDI dependencies,
 * so these functions can be tested without a running container.
 *
 * <p>Kept apart from {@code RecipientRollup} for testability: the rollup orchestrates
 * database queries and series construction, and these functions compute statistics
 * from the results.
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

    public record RateStats(double ratePerSecond, Double sustainedPerSecond) {
    }
}
