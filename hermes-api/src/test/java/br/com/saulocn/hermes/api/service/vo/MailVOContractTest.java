package br.com.saulocn.hermes.api.service.vo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for MailVO serialization.
 *
 * <p>Verifies that MailVO can serialize and deserialize all fields defined in
 * contracts/mail-vo.json. This is a pure unit test (no Quarkus, no container).
 * The contract is shared with hermes-mailer, which reads the same JSON.
 */
class MailVOContractTest {

    private static final Path CONTRACT_FILE = Path.of("..", "contracts", "mail-vo.json");

    /**
     * Reads the contract JSON file relative to the module root.
     */
    private String readContractJson() throws IOException {
        Path contractPath = Path.of("hermes-api").resolve(CONTRACT_FILE);
        if (!Files.exists(contractPath)) {
            // Fallback: try from test directory
            contractPath = Path.of(".").resolve(CONTRACT_FILE).toAbsolutePath();
        }
        assertTrue(Files.exists(contractPath),
                "Contract file not found at " + contractPath.toAbsolutePath());
        return Files.readString(contractPath);
    }

    /**
     * Extracts expected field names from the contract JSON by simple regex matching.
     * No full JSON parser is used here to avoid adding a dependency for one test.
     */
    private Set<String> extractFieldNames(String json) {
        Set<String> fields = new java.util.HashSet<>();
        // Simple pattern: "fieldName": ...
        String[] lines = json.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains(":") && line.startsWith("\"")) {
                int colon = line.indexOf(":");
                if (colon > 0) {
                    String fieldName = line.substring(1, colon - 1);
                    if (!fieldName.isEmpty() && fieldName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        fields.add(fieldName);
                    }
                }
            }
        }
        return fields;
    }

    /**
     * MailVO can deserialize all fields from the contract JSON.
     */
    @Test
    void mailVODeserializesWithContractFields() throws IOException {
        String contractJson = readContractJson();

        // Deserialize from contract
        MailVO deserialized = MailVO.fromJSON(contractJson);
        assertNotNull(deserialized, "MailVO should deserialize from contract JSON");

        // Verify all expected fields are present
        assertNotNull(deserialized.getMessageId(), "messageId should not be null");
        assertNotNull(deserialized.getSubject(), "subject should not be null");
        assertNotNull(deserialized.getText(), "text should not be null");
        assertNotNull(deserialized.getContentType(), "contentType should not be null");
    }

    /**
     * MailVO can serialize back to JSON and maintains all required fields.
     */
    @Test
    void mailVOSerializesWithAllFields() throws IOException {
        String contractJson = readContractJson();

        // Deserialize and serialize
        MailVO deserialized = MailVO.fromJSON(contractJson);
        String serialized = deserialized.toJSON();

        // Verify serialized JSON is valid and can be deserialized again
        MailVO roundTrip = MailVO.fromJSON(serialized);
        assertEquals(deserialized.getMessageId(), roundTrip.getMessageId(),
                "messageId should survive round-trip");
        assertEquals(deserialized.getSubject(), roundTrip.getSubject(),
                "subject should survive round-trip");
        assertEquals(deserialized.getText(), roundTrip.getText(),
                "text should survive round-trip");
        assertEquals(deserialized.getContentType(), roundTrip.getContentType(),
                "contentType should survive round-trip");
    }

    /**
     * The contract includes messageId, subject, text, and contentType.
     * No additional fields should be added or removed.
     */
    @Test
    void contractContainsExactlyRequiredFields() throws IOException {
        String contractJson = readContractJson();
        Set<String> contractFields = extractFieldNames(contractJson);

        String[] requiredFields = {"messageId", "subject", "text", "contentType"};
        for (String field : requiredFields) {
            assertTrue(contractFields.contains(field),
                    "Contract should contain field: " + field);
        }

        assertEquals(requiredFields.length, contractFields.size(),
                "Contract should have exactly " + requiredFields.length + " fields, " +
                "but has " + contractFields + " with " + contractFields.size());
    }
}
