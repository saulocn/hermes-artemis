package br.com.saulocn.hermes.enqueuer.batch.vo;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Ensures that {@link RecipientVO} serializes to and from JSON with an exact set of field names.
 *
 * <p>Two independent modules (hermes-enqueuer and hermes-mailer) are responsible for the two
 * halves of the serialization contract: enqueuer publishes {@code RecipientVO} to the broker as
 * JSON, and mailer deserializes it. They are built and tested separately, so there is no compile
 * check that the field names match. A renamed field in one module compiles and passes all tests,
 * but the pipeline breaks at runtime: the JSON arriving at the mailer has different keys, and
 * unmarshalling leaves fields null.
 *
 * <p>This test pairs with an identical one in hermes-mailer. Both read the same
 * {@code contracts/recipient-vo.json} file to close the contract: two modules, two separate
 * builds, one shared JSON schema in version control. Renaming or removing a field requires
 * updating the contract file and both modules' tests before the build passes.
 */
class RecipientVOContractTest {

    @Test
    void deserializesFromJsonWithExactFieldSet() throws Exception {
        String contractJson = Files.readString(
                Path.of("..", "contracts", "recipient-vo.json"));

        RecipientVO recipient = RecipientVO.fromJSON(contractJson);

        assertNotNull(recipient);
        assertEquals(42L, recipient.getId());
        assertEquals("ana@example.com", recipient.getEmail());
        assertEquals(7L, recipient.getMessageId());
    }

    @Test
    void serializesToJsonWithExactFieldSet() throws Exception {
        String contractJson = Files.readString(
                Path.of("..", "contracts", "recipient-vo.json"));

        // Extract the expected set of keys from the contract.
        JsonReader contractReader = Json.createReader(new StringReader(contractJson));
        JsonObject contractObject = contractReader.readObject();
        Set<String> contractKeys = contractObject.keySet();

        // Create an instance, serialize it, and extract its keys.
        RecipientVO recipient = new RecipientVO(42L, "ana@example.com", 7L);
        String serialized = recipient.toJSON();

        JsonReader serializedReader = Json.createReader(new StringReader(serialized));
        JsonObject serializedObject = serializedReader.readObject();
        Set<String> serializedKeys = serializedObject.keySet();

        // The two must match exactly: no extra fields, no missing ones.
        assertEquals(contractKeys, serializedKeys,
                "Serialized JSON keys must match the contract file exactly. "
                        + "Expected: " + contractKeys + ", got: " + serializedKeys);
    }
}
