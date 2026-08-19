package br.com.saulocn.hermes.api.admin;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * What an operator gets when the enqueuer is down.
 *
 * <p>{@code ApiTestProfile} points {@code hermes.enqueuer.url} at {@code http://127.0.0.1:1} —
 * deliberate, and unchanged here: it is what lets this exercise the real endpoint rather than a
 * unit calling {@code JobTriggerService} directly.
 *
 * <p>This used to answer 500 with a stack trace, because {@code AdminResource.trigger} caught only
 * {@code JobAlreadyRunningException} and the unreachable case arrived as a plain
 * {@code IllegalStateException}. A 500 points the operator at the wrong service: this api is
 * healthy and the request was valid — the thing behind it did not answer. It is now 502, and the
 * body says {@code started: false}, so a retry is known to be safe.
 */
@QuarkusTest
@TestProfile(ApiTestProfile.class)
@WithTestResource(InfraTestResource.class)
class AdminJobTriggerIT {

    @Test
    void triggeringEnqueueAgainstAnUnreachableEnqueuerAnswers502() {
        given()
                .when()
                .post("/admin/jobs/enqueue")
                .then()
                .statusCode(502)
                .body("error", containsString("could not reach the enqueuer"))
                .body("started", equalTo(false));
    }

    @Test
    void triggeringFallbackAgainstAnUnreachableEnqueuerAnswers502() {
        // Same code path (AdminResource.trigger), reached from the other job name.
        given()
                .when()
                .post("/admin/jobs/fallback")
                .then()
                .statusCode(502)
                .body("started", equalTo(false));
    }
}
