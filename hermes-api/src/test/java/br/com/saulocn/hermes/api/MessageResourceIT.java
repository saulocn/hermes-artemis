package br.com.saulocn.hermes.api;

import br.com.saulocn.hermes.api.admin.ApiTestProfile;
import br.com.saulocn.hermes.api.admin.InfraTestResource;
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
 * Contract tests for the message endpoints. These tests verify:
 * - Message creation returns a numeric ID
 * - Message retrieval by ID returns the correct message
 */
@QuarkusTest
@TestProfile(ApiTestProfile.class)
@WithTestResource(InfraTestResource.class)
public class MessageResourceIT {

    @Inject
    EntityManager em;

    @Test
    void postCreatesMessageAndReturnsId() {
        // POST /message with 2 recipients
        Map<String, Object> messagePayload = Map.of(
                "title", "Test message " + UUID.randomUUID(),
                "text", "This is a test message body",
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
                .statusCode(200)
                .body("id", instanceOf(Number.class))
                .body("id", notNullValue());
    }

    @Test
    void getByIdReturnsTheMessage() {
        // POST a message and capture its ID
        String testTitle = "Message retrieval test " + UUID.randomUUID();
        Map<String, Object> messagePayload = Map.of(
                "title", testTitle,
                "text", "This is a test message for retrieval",
                "contentType", "application/json",
                "recipients", List.of("recipient-" + UUID.randomUUID() + "@example.com")
        );

        Integer messageId = given()
                .contentType(ContentType.JSON)
                .body(messagePayload)
                .when()
                .post("/message")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        // GET /message/{id} and assert the response
        given()
                .when()
                .get("/message/" + messageId)
                .then()
                .statusCode(200)
                .body("messageId", equalTo(messageId));
    }
}
