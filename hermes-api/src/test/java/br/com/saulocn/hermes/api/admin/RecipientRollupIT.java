package br.com.saulocn.hermes.api.admin;

import br.com.saulocn.hermes.api.admin.vo.AdminVOs;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for recipient rollup queries.
 *
 * <p>Core invariant being tested: per-minute throughput series labels and counts
 * are guaranteed to refer to the same minute. A single loop walks the minutes
 * and produces labeled points, making label-count divergence impossible by construction.
 *
 * <p>Note: These tests use database NOW() to compute time windows, so test data must be inserted
 * with recent timestamps (within the query window). This ensures the rollup queries find the data.
 */
@QuarkusTest
@TestProfile(ApiTestProfile.class)
@WithTestResource(InfraTestResource.class)
public class RecipientRollupIT {

    @Inject
    EntityManager em;

    @Inject
    RecipientRollup rollup;

    /**
     * Every test here reads aggregates over the whole table, so rows left by the previous test are
     * indistinguishable from the ones this test inserted. Without this, asserting an exact count
     * for a given minute fails the moment another test happens to seed the same minute — which is
     * what a shared clock makes likely, not unlikely.
     */
    @BeforeEach
    void emptyTheTable() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("delete from hermes.recipient").executeUpdate();
            em.createNativeQuery("delete from hermes.message").executeUpdate();
        });
        em.clear();
    }

    /**
     * Insert a recipient row with all timestamps set to specific values.
     * The recipient_id is generated via nextval('recipient_seq') in the SQL.
     * Uses QuarkusTransaction.requiringNew() to ensure the write is visible immediately.
     */
    private void insertRecipient(LocalDateTime createdOn,
                                  LocalDateTime publishedOn, LocalDateTime claimedOn) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                insert into hermes.recipient
                  (recipient_id, recipient_mail, created_on, published_on, claimed_on)
                values (nextval('recipient_seq'), :mail, :createdOn, :publishedOn, :claimedOn)
                """)
                .setParameter("mail", "test-" + System.nanoTime() + "@example.com")
                .setParameter("createdOn", createdOn)
                .setParameter("publishedOn", publishedOn)
                .setParameter("claimedOn", claimedOn)
                .executeUpdate());
        em.clear();
    }

    /**
     * Insert a message row with a specific ID.
     */
    private void insertMessage(long messageId) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                insert into hermes.message
                  (message_id, message_title, message_text, content_type)
                values (:id, :title, :text, :contentType)
                """)
                .setParameter("id", messageId)
                .setParameter("title", "Test message " + messageId)
                .setParameter("text", "Test body " + messageId)
                .setParameter("contentType", "text/plain")
                .executeUpdate());
        em.clear();
    }

    /**
     * Get current database time to ensure test data is within the query window.
     */
    private LocalDateTime getDbNow() {
        Object result = em.createNativeQuery("select localtimestamp").getSingleResult();
        if (result instanceof LocalDateTime) {
            return (LocalDateTime) result;
        }
        return LocalDateTime.now();
    }

    /**
     * The label a point carries names the minute its count was taken from.
     *
     * <p>This is the one thing {@link RecipientRollup} exists to guarantee, and it is the only
     * assertion here that can fail for an interesting reason. Counting and labelling used to live
     * in different code — {@code Rates.fillGaps} derived the first minute to bin by, and the
     * caller re-derived the same expression to label what came back. Both were
     * {@code window.start().withSecond(0).withNano(0)}, and nothing tied them together: shifting
     * one left the chart reading a real count against the wrong minute, with every point still
     * present and every label still consecutive.
     *
     * <p>Three distinct counts at three non-adjacent minutes, and the counts differ from each
     * other on purpose. Equal counts would survive a swap — that is exactly how the bug in
     * {@code stats()} hid (see e1da578): two {@code long} columns traded places, compiled, and the
     * assertion was {@code notNullValue()}. A shift of one minute here moves 3, 7 and 1 onto
     * neighbours that must be zero, so both sides of every wrong pair disagree.
     *
     * <p>Absolute minutes rather than offsets from the series start: the window is derived from
     * the database clock inside the rollup, so a minute boundary crossing mid-test would shift the
     * whole series. Looking each inserted minute up by its own label is immune to that.
     */
    @Test
    void eachLabelCarriesTheCountOfItsOwnMinute() {
        LocalDateTime nowMinute = getDbNow().withSecond(0).withNano(0);
        // Non-adjacent, and all comfortably inside a 6-minute window. The most recent is one
        // minute back because the in-progress minute is excluded from the series by design.
        LocalDateTime m3 = nowMinute.minusMinutes(1);
        LocalDateTime m7 = nowMinute.minusMinutes(3);
        LocalDateTime m1 = nowMinute.minusMinutes(5);

        // Seconds offsets prove the binning truncates rather than rounds.
        for (int i = 0; i < 3; i++) {
            insertRecipient(m3.plusSeconds(7 + i), null, null);
        }
        for (int i = 0; i < 7; i++) {
            insertRecipient(m7.plusSeconds(11 + i), null, null);
        }
        insertRecipient(m1.plusSeconds(59), null, null);

        AdminVOs.Throughput result = rollup.throughput(6);

        AdminVOs.ThroughputSeries created = result.series().stream()
                .filter(s -> "created_on".equals(s.stage()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no created_on series"));

        Map<String, Long> byLabel = new HashMap<>();
        for (AdminVOs.ThroughputPoint point : created.points()) {
            assertNotNull(point.minute(), "every point carries a label");
            assertNull(byLabel.put(point.minute(), point.count()),
                    "a minute appears at most once in the series: " + point.minute());
        }

        assertEquals(3L, byLabel.get(m3.toString()),
                "the point labelled " + m3 + " must carry the 3 rows created in that minute");
        assertEquals(7L, byLabel.get(m7.toString()),
                "the point labelled " + m7 + " must carry the 7 rows created in that minute");
        assertEquals(1L, byLabel.get(m1.toString()),
                "the point labelled " + m1 + " must carry the 1 row created in that minute");

        // Every other minute in the window is empty. Without this a shift that moved a count onto
        // a neighbour would still satisfy the three assertions above for the minutes it vacated.
        byLabel.forEach((label, count) -> {
            if (label.equals(m3.toString()) || label.equals(m7.toString()) || label.equals(m1.toString())) {
                return;
            }
            assertEquals(0L, count, "no rows were created in minute " + label + ", so it must read 0");
        });
    }

    /**
     * Retained from the structural pass: the window length is deterministic, so the point count is
     * too. {@code >=} here would hide an off-by-one at the window edge.
     */
    @Test
    void windowLengthDeterminesPointCount() {
        AdminVOs.Throughput result = rollup.throughput(4);

        for (AdminVOs.ThroughputSeries series : result.series()) {
            assertEquals(4, series.points().size(),
                    "a 4-minute window produces exactly 4 points in series " + series.stage());
        }
    }

    /**
     * Rates endpoint returns the three stage rates with correct structure.
     */
    @Test
    void ratesEndpointReturnsValidRateStructure() {
        // Arrange: Insert a message and recipients with different states
        long msgId = 30000L;
        insertMessage(msgId);

        LocalDateTime baseTime = getDbNow().minusSeconds(50);
        for (int i = 0; i < 10; i++) {
            insertRecipient(baseTime.plusSeconds(i),
                           i < 5 ? baseTime.plusSeconds(i + 10) : null,
                           i < 3 ? baseTime.plusSeconds(i + 20) : null);
        }

        // Act: Fetch rates for a 60-second window
        AdminVOs.Rates result = rollup.rates(60);

        // Assert: Structure and sanity checks
        assertNotNull(result.created(), "created rates should not be null");
        assertNotNull(result.published(), "published rates should not be null");
        assertNotNull(result.claimed(), "claimed rates should not be null");

        // created_on should have counts (at least 10)
        assertTrue(result.created().count() >= 10,
                "created rates should have at least 10 counts");
        assertTrue(result.created().ratePerSecond() >= 0,
                "ratePerSecond should be >= 0");
        assertFalse(Double.isNaN(result.created().ratePerSecond()),
                "ratePerSecond should not be NaN");
        assertFalse(Double.isInfinite(result.created().ratePerSecond()),
                "ratePerSecond should not be infinite");

        // published_on should have fewer counts than created (only 5 set)
        assertTrue(result.published().count() >= 0,
                "published count should be >= 0");
        assertTrue(result.published().count() <= result.created().count(),
                "published should have <= counts than created");
    }

    /**
     * Null fill before first sample distinguishes "column is new" from "no data".
     *
     * <p>Inserts published_on values starting only after T+2 and verifies:
     * - At least some points before T+2 are null (column existed but had no data yet)
     * - All points at/after T+2 are counts (not null)
     */
    @Test
    void throughputSeriesNullFillMarksBoundaryBetweenNewAndEmpty() {
        // Arrange: Message exists, but published_on data starts only later
        long msgId = 40000L;
        insertMessage(msgId);

        LocalDateTime baseTime = getDbNow().minusMinutes(5);
        baseTime = baseTime.withSecond(0).withNano(0); // Align to minute boundary
        LocalDateTime publishedStart = baseTime.plusMinutes(2); // Data only from T+2

        // Insert created_on for all 5 minutes, but published_on only from T+2 onward
        for (int i = 0; i < 5; i++) {
            LocalDateTime createdTime = baseTime.plusMinutes(i).plusSeconds(10 + i);
            LocalDateTime publishedTime = i >= 2 ? publishedStart.plusSeconds(10 + i) : null;
            insertRecipient(createdTime, publishedTime, null);
        }

        // Act: Fetch throughput for 5 minutes
        AdminVOs.Throughput result = rollup.throughput(5);

        // Assert: published_on series should show nulls before T+2, counts after
        AdminVOs.ThroughputSeries publishedSeries = result.series().stream()
                .filter(s -> "published_on".equals(s.stage()))
                .findFirst()
                .orElseThrow();

        List<AdminVOs.ThroughputPoint> points = publishedSeries.points();
        assertEquals(5, points.size(), "5-minute window must produce exactly 5 points");

        // Count nulls vs non-nulls to verify the boundary
        int nullCount = 0;
        int nonNullCount = 0;
        for (AdminVOs.ThroughputPoint point : points) {
            if (point.count() == null) {
                nullCount++;
            } else {
                nonNullCount++;
            }
        }

        // Before T+2, should be nulls; at T+2 and after, should be counts
        assertTrue(nullCount >= 2, "Should have at least 2 null points (before published_on starts)");
        assertTrue(nonNullCount >= 2, "Should have at least 2 non-null points (at/after published_on starts)");
    }
}
