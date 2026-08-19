package br.com.saulocn.hermes.mailer.service.vo;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract between hermes-api and hermes-mailer is expressed in {@code contracts/mail-vo.json}.
 *
 * <p>The cache (Redis) stores only the four fields in the contract: messageId, subject, text,
 * and contentType. {@code MailVO} has two extra fields — {@code to} and {@code recipientId} —
 * which are <em>not</em> in the contract because they are populated <em>after</em> deserialization
 * from the cache. {@code MessageService.deliver} reads from the cache and then sets these fields
 * by looking them up from the current message being processed.
 *
 * <p>This test verifies that:
 * 1. All four contract fields deserialize correctly.
 * 2. The contract fields are a <em>subset</em> of the keys produced by serialization (not equality,
 *    because {@code to} and {@code recipientId} are serialized as well, even though they are not
 *    part of the cache contract).
 *
 * <p>A sibling test in hermes-api reads the contract, serializes, and verifies shape: together,
 * they ensure neither side renames the four shared fields.
 */
class MailVOContractTest {

    @Test
    void deserializesAllContractFieldsFromCache() throws Exception {
        String contractJson = Files.readString(Path.of("..", "contracts", "mail-vo.json"));
        MailVO vo = MailVO.fromJSON(contractJson);

        // Read expected values from the contract file to avoid hardcoding field names.
        JsonReader reader = Json.createReader(new StringReader(contractJson));
        JsonObject contract = reader.readObject();

        assertEquals(contract.getJsonNumber("messageId").longValue(), vo.getMessageId(),
                "messageId field must match the contract");
        assertEquals(contract.getString("subject"), vo.getSubject(),
                "subject field must match the contract");
        assertEquals(contract.getString("text"), vo.getText(),
                "text field must match the contract");
        assertEquals(contract.getString("contentType"), vo.getContentType(),
                "contentType field must match the contract");
    }

    @Test
    void contractFieldsAreSubsetOfSerializedKeys() throws Exception {
        String contractJson = Files.readString(Path.of("..", "contracts", "mail-vo.json"));
        MailVO vo = MailVO.fromJSON(contractJson);

        // Parse the contract to extract expected keys
        JsonReader reader = Json.createReader(new StringReader(contractJson));
        JsonObject contract = reader.readObject();
        Set<String> contractKeys = contract.keySet();

        // Serialize and extract keys
        String serialized = vo.toJSON();
        JsonReader serializedReader = Json.createReader(new StringReader(serialized));
        JsonObject serializedObject = serializedReader.readObject();
        Set<String> serializedKeys = serializedObject.keySet();

        // Contract keys must all be present in serialized keys
        for (String contractKey : contractKeys) {
            assertTrue(serializedKeys.contains(contractKey),
                    "contract key '" + contractKey + "' must be present in serialized output");
        }

        // The contract fields are a subset: serialized has extra fields (to, recipientId) that
        // are populated locally after deserialization, not sent through the cache.
        assertTrue(serializedKeys.size() >= contractKeys.size(),
                "serialized JSON should have at least as many keys as the contract");
    }
}
