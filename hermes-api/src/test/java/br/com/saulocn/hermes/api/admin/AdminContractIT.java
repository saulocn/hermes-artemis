package br.com.saulocn.hermes.api.admin;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Contract tests for the admin console endpoints. These tests verify:
 * - Stats correctness with different recipient states (pending, inFlight, delivered)
 * - Graceful broker degradation when broker is unreachable
 * - Pagination and filtering of messages and recipients
 * - Error handling for invalid IDs
 * - Page size clamping
 */
@QuarkusTest
@TestProfile(ApiTestProfile.class)
@WithTestResource(InfraTestResource.class)
public class AdminContractIT {

    @Inject
    EntityManager em;

    @Test
    void statsExposesTheThreeRecipientStates() {
        // Seed: POST a message with 2 recipients
        String title = "Stats test " + UUID.randomUUID();
        Map<String, Object> messagePayload = Map.of(
                "title", title,
                "text", "Test message for stats",
                "contentType", "text/html",
                "recipients", List.of(
                        "recipient1-" + UUID.randomUUID() + "@example.com",
                        "recipient2-" + UUID.randomUUID() + "@example.com"
                )
        );

        given()
                .contentType(ContentType.JSON)
                .body(messagePayload)
                .when()
                .post("/message")
                .then()
                .statusCode(200);

        // GET /admin/stats and assert HTTP 200 and required keys
        given()
                .when()
                .get("/admin/stats")
                .then()
                .statusCode(200)
                .body("pending", greaterThanOrEqualTo(2))
                .body("inFlight", notNullValue())
                .body("failing", notNullValue())
                .body("delivered", notNullValue())
                .body("totalMessages", notNullValue());
        // oldestPendingSeconds may be present or null
    }

    @Test
    void brokerDegradesGracefullyWhenUnreachable() {
        // The test profile points the broker at an unreachable port (port 1),
        // so we expect HTTP 200 (NOT 5xx) with error info
        given()
                .when()
                .get("/admin/broker")
                .then()
                .statusCode(200)
                .body("kind", equalTo("artemis"))
                .body("error", notNullValue());
    }

    @Test
    void messagesArePaginated() {
        // Seed: POST 3 messages
        String uniquePrefix = "Paginated test " + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> messagePayload = Map.of(
                    "title", uniquePrefix + " msg" + i,
                    "text", "Test message " + i,
                    "contentType", "text/html",
                    "recipients", List.of("recipient-" + UUID.randomUUID() + "@example.com")
            );

            given()
                    .contentType(ContentType.JSON)
                    .body(messagePayload)
                    .when()
                    .post("/message")
                    .then()
                    .statusCode(200);
        }

        // GET /admin/messages?page=0&size=2
        given()
                .when()
                .get("/admin/messages?page=0&size=2")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("total", greaterThanOrEqualTo(3))
                .body("items", hasSize(2))
                .body("items[0].id", notNullValue())
                .body("items[0].title", notNullValue())
                .body("items[0].contentType", notNullValue())
                .body("items[0].createdAt", notNullValue())
                .body("items[0].recipientCount", notNullValue())
                .body("items[0].sentCount", notNullValue());
    }

    @Test
    void messagesCanBeFilteredByTitle() {
        // Seed: POST a message with a distinctive title
        String distinctiveTitle = "Unique Message " + UUID.randomUUID();
        Map<String, Object> messagePayload = Map.of(
                "title", distinctiveTitle,
                "text", "Distinctive test message",
                "contentType", "text/html",
                "recipients", List.of("recipient-" + UUID.randomUUID() + "@example.com")
        );

        given()
                .contentType(ContentType.JSON)
                .body(messagePayload)
                .when()
                .post("/message")
                .then()
                .statusCode(200);

        // GET /admin/messages?q=<part of that title>
        String searchQuery = distinctiveTitle.substring(0, 10); // Search for partial title
        given()
                .when()
                .get("/admin/messages?q=" + searchQuery)
                .then()
                .statusCode(200)
                .body("items", not(empty()))
                .body("items.title", everyItem(containsString(searchQuery)));
    }

    @Test
    void recipientsCanBeFilteredByEmailAndState() {
        // Seed: POST a message with a distinctive recipient e-mail
        String distinctiveEmail = "filter-test-" + UUID.randomUUID() + "@example.com";
        Map<String, Object> messagePayload = Map.of(
                "title", "Recipients filter test " + UUID.randomUUID(),
                "text", "Test message for recipient filtering",
                "contentType", "text/html",
                "recipients", List.of(distinctiveEmail)
        );

        given()
                .contentType(ContentType.JSON)
                .body(messagePayload)
                .when()
                .post("/message")
                .then()
                .statusCode(200);

        // GET /admin/recipients?email=<that email>
        given()
                .when()
                .get("/admin/recipients?email=" + distinctiveEmail)
                .then()
                .statusCode(200)
                .body("total", greaterThanOrEqualTo(1))
                .body("items", not(empty()))
                .body("items[0].email", equalTo(distinctiveEmail));

        // GET /admin/recipients?state=pending
        given()
                .when()
                .get("/admin/recipients?state=pending")
                .then()
                .statusCode(200)
                .body("total", greaterThanOrEqualTo(1));
    }

    /**
     * The bucket the dashboard counts has to be reachable from the list, or the operator sees a
     * number with no way to find the rows behind it. `state=failing` used to match no arm of the
     * where clause at all, so it answered 200 with zero rows — indistinguishable from "none".
     * Failing is now a partition: not returned by pending, inFlight, delivered, or anything else.
     */
    @Test
    void failingRecipientsCanBeListedAndCarryTheirAttemptCount() {
        String email = "failing-" + UUID.randomUUID() + "@example.com";
        seedRecipient(email);
        markFailing(email, 3);

        given()
                .when()
                .get("/admin/recipients?state=failing&email=" + email)
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].email", equalTo(email))
                .body("items[0].attempts", equalTo(3));

        // And it is not confused with a row that is merely queued (pending).
        given()
                .when()
                .get("/admin/recipients?state=pending&email=" + email)
                .then()
                .statusCode(200)
                .body("total", equalTo(0));

        // And it is not confused with a row that is merely in flight (processed, but no failures).
        given()
                .when()
                .get("/admin/recipients?state=inFlight&email=" + email)
                .then()
                .statusCode(200)
                .body("total", equalTo(0));
    }

    /** A state the server does not know is a bad request, not an empty result. */
    @Test
    void anUnknownStateIsRejected() {
        given()
                .when()
                .get("/admin/recipients?state=bogus")
                .then()
                .statusCode(400)
                .body("states", hasItems("pending", "inFlight", "failing", "delivered"));
    }

    /** Retrying says the past failures no longer describe the row, so the count goes with them. */
    @Test
    void retryClearsTheAttemptCount() {
        String email = "retry-" + UUID.randomUUID() + "@example.com";
        seedRecipient(email);
        markFailing(email, 2);

        Integer id = given()
                .when()
                .get("/admin/recipients?email=" + email)
                .then()
                .statusCode(200)
                .extract().path("items[0].id");

        given().when().post("/admin/recipients/" + id + "/retry").then().statusCode(200);

        given()
                .when()
                .get("/admin/recipients?email=" + email)
                .then()
                .statusCode(200)
                .body("items[0].attempts", equalTo(0))
                .body("items[0].processed", equalTo(false));
    }

    private void seedRecipient(String email) {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "title", "State test " + UUID.randomUUID(),
                        "text", "Test message",
                        "contentType", "text/html",
                        "recipients", List.of(email)))
                .when()
                .post("/message")
                .then()
                .statusCode(200);
    }

    /**
     * What the mailer leaves behind when a send throws: claim rolled back, counter kept.
     *
     * <p>Driven through {@code QuarkusTransaction} rather than {@code @Transactional}, because
     * calling an annotated method on {@code this} from another test method skips the interceptor.
     */
    private void markFailing(String email, int attempts) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                update hermes.recipient
                   set recipient_processed = true, recipient_sent = false, recipient_attempts = :attempts
                 where recipient_mail = :email
                """)
                .setParameter("attempts", attempts)
                .setParameter("email", email)
                .executeUpdate());
    }

    @Test
    void retryOnUnknownRecipientReturns404() {
        // POST /admin/recipients/999999999/retry with a non-existent ID
        given()
                .when()
                .post("/admin/recipients/999999999/retry")
                .then()
                .statusCode(404);
    }

    @Test
    void pageSizeIsClamped() {
        // GET /admin/messages?size=99999 should clamp to MAX_PAGE_SIZE (200)
        given()
                .when()
                .get("/admin/messages?size=99999")
                .then()
                .statusCode(200)
                .body("size", equalTo(200));
    }
}
