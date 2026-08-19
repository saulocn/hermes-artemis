package br.com.saulocn.hermes.enqueuer.resource;

import br.com.saulocn.hermes.enqueuer.batch.BatchFixtures;
import br.com.saulocn.hermes.enqueuer.batch.PostgresTestResource;
import br.com.saulocn.hermes.enqueuer.entity.Message;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.batch.runtime.BatchStatus;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The manual job-trigger path had no Java test at all before this file, even though the 409 guard
 * it exercises is credited with eliminating the ~72,000 duplicate publishes described in
 * {@link br.com.saulocn.hermes.enqueuer.batch.JobLauncher}'s javadoc. A regression here — the 409
 * branch quietly turning into a 200, say — would silently reopen that overlap.
 *
 * <p>The 409 test drives a real, concurrently-running execution rather than asserting against
 * {@code JobLauncher.startIfIdle} directly, and rather than sleeping and hoping the timing works
 * out: it swaps in {@link BlockingPublisher} (see {@link SlowJobTestProfile}), which parks the
 * writer mid-chunk and signals a latch the moment it has, so the second trigger is sent only once
 * the first run is provably still executing.
 */
@QuarkusTest
@TestProfile(SlowJobTestProfile.class)
@WithTestResource(PostgresTestResource.class)
class JobResourceIT {

    @Inject
    BatchFixtures fixtures;

    @Inject
    BlockingPublisher blockingPublisher;

    @AfterEach
    void releaseAnyBlockedRun() {
        // Belt and braces: if an assertion above failed mid-test, don't leave the (singleton)
        // publisher parked forever and starve every later test's job of a chance to finish.
        blockingPublisher.release();
    }

    @Test
    void enqueueStartsARunAndAnswers200WithAnExecutionId() {
        given()
                .when()
                .post("/jobs/enqueue")
                .then()
                .statusCode(200)
                .body("executionId", notNullValue());
    }

    @Test
    void unknownJobPathAnswers404AndListsTheKnownPaths() {
        given()
                .when()
                .post("/jobs/nonexistent")
                .then()
                .statusCode(404)
                .body("known", hasItems("enqueue", "fallback"));
    }

    @Test
    void statusOfAnUnknownExecutionAnswers404() {
        given()
                .when()
                .get("/jobs/999999999")
                .then()
                .statusCode(404);
    }

    /**
     * The valuable branch: a second trigger arriving while the first run is still publishing.
     *
     * <p>{@link BlockingPublisher#armAndBlockNextPublish()} hands back a latch that only opens
     * once {@code MailWriter} is inside the (now blocked) call to {@code publisher.publishAll} —
     * i.e. once JBeret has genuinely started the chunk. Waiting on that latch, instead of on a
     * clock, is what keeps this test deterministic: the second POST is issued only after the
     * first run is provably still in flight, never "probably by now".
     */
    @Test
    void secondTriggerWhileARunIsInFlightAnswers409() throws InterruptedException {
        Message message = fixtures.createMessage();
        fixtures.createRecipients(message.getId(), 1, false, false, LocalDateTime.now());

        CountDownLatch enteredPublish = blockingPublisher.armAndBlockNextPublish();

        long executionId = given()
                .when()
                .post("/jobs/enqueue")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("executionId");

        assertTrue(enteredPublish.await(30, TimeUnit.SECONDS),
                "writer never reached the blocked publish; the first run may not have started " +
                        "in time for this test to trust the 409 that follows");

        given()
                .when()
                .post("/jobs/enqueue")
                .then()
                .statusCode(409)
                .body("job", equalTo("enqueue"));

        blockingPublisher.release();

        JobOperator jobOperator = BatchRuntime.getJobOperator();
        await().atMost(Duration.ofSeconds(30)).until(
                () -> jobOperator.getJobExecution(executionId).getBatchStatus(),
                status -> status == BatchStatus.COMPLETED || status == BatchStatus.FAILED
                        || status == BatchStatus.ABANDONED || status == BatchStatus.STOPPED);
    }
}
