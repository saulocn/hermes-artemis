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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contracts between hermes-enqueuer and hermes-mailer are expressed in {@code contracts/recipient-vo.json}.
 *
 * <p>This test reads the contract file, deserializes it, and verifies that all three fields can be
 * deserialized correctly. It then serializes back and verifies that the keys in the JSON output
 * match exactly those in the contract file.
 *
 * <p>A sibling test in hermes-enqueuer reads the same contract file, serializes, and verifies the
 * shape: together, they catch field renames in either direction without either test needing to
 * know the field names.
 */
class RecipientVOContractTest {

    @Test
    void deserializesAllFieldsFromContract() throws Exception {
        String contractJson = Files.readString(Path.of("..", "contracts", "recipient-vo.json"));
        RecipientVO vo = RecipientVO.fromJSON(contractJson);

        // Read expected values from the contract file to avoid hardcoding field names.
        JsonReader reader = Json.createReader(new StringReader(contractJson));
        JsonObject contract = reader.readObject();

        assertEquals(contract.getJsonNumber("id").longValue(), vo.getId(),
                "id field must match the contract");
        assertEquals(contract.getString("email"), vo.getEmail(),
                "email field must match the contract");
        assertEquals(contract.getJsonNumber("messageId").longValue(), vo.getMessageId(),
                "messageId field must match the contract");
    }

    @Test
    void serializesWithExactlyTheContractKeys() throws Exception {
        String contractJson = Files.readString(Path.of("..", "contracts", "recipient-vo.json"));
        RecipientVO vo = RecipientVO.fromJSON(contractJson);

        // Parse the contract to extract expected keys
        JsonReader reader = Json.createReader(new StringReader(contractJson));
        JsonObject contract = reader.readObject();
        Set<String> contractKeys = contract.keySet();

        // Serialize back and extract keys
        String serialized = vo.toJSON();
        JsonReader serializedReader = Json.createReader(new StringReader(serialized));
        JsonObject serializedObject = serializedReader.readObject();
        Set<String> serializedKeys = serializedObject.keySet();

        assertEquals(contractKeys, serializedKeys,
                "serialized JSON keys must match contract keys exactly");
    }
}
