package br.com.saulocn.hermes.api.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for message cache configuration.
 *
 * <p>Verifies that MessageService's cache key format and TTL match
 * contracts/message-cache.properties, which is the single source of truth
 * shared between hermes-api and hermes-mailer.
 *
 * <p>This test guards against hand-maintained duplication: if someone changes
 * one of the values in the code, this test will catch it.
 */
class MessageCacheContractTest {

    private static final Path CONTRACT_FILE = Path.of("..", "contracts", "message-cache.properties");

    /**
     * Reads the contract properties file relative to the module root.
     */
    private String readContractProperties() throws IOException {
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
     * Parses the properties file and extracts key=value pairs.
     * Ignores comments and empty lines.
     */
    private Map<String, String> parseProperties(String content) {
        Map<String, String> props = new HashMap<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                props.put(key, value);
            }
        }
        return props;
    }

    /**
     * MessageService.getMessageKeyFormat() returns the exact format from
     * contracts/message-cache.properties (key.format).
     */
    @Test
    void messageKeyFormatMatchesContract() throws IOException {
        String contractContent = readContractProperties();
        Map<String, String> contractProps = parseProperties(contractContent);

        String expectedKeyFormat = contractProps.get("key.format");
        assertNotNull(expectedKeyFormat,
                "Contract should define key.format property");

        String actualKeyFormat = MessageService.getMessageKeyFormat();
        assertEquals(expectedKeyFormat, actualKeyFormat,
                "MessageService.getMessageKeyFormat() should match contract key.format. " +
                "Expected: " + expectedKeyFormat + ", got: " + actualKeyFormat);
    }

    /**
     * MessageService.getMessageCacheTtl() returns the exact TTL from
     * contracts/message-cache.properties (ttl.seconds).
     */
    @Test
    void messageCacheTtlMatchesContract() throws IOException {
        String contractContent = readContractProperties();
        Map<String, String> contractProps = parseProperties(contractContent);

        String expectedTtl = contractProps.get("ttl.seconds");
        assertNotNull(expectedTtl,
                "Contract should define ttl.seconds property");

        String actualTtl = MessageService.getMessageCacheTtl();
        assertEquals(expectedTtl, actualTtl,
                "MessageService.getMessageCacheTtl() should match contract ttl.seconds. " +
                "Expected: " + expectedTtl + ", got: " + actualTtl);
    }

    /**
     * The key format "%d" substitution works correctly with a sample message ID.
     */
    @Test
    void messageKeyFormatSubstitutesMessageId() throws IOException {
        String keyFormat = MessageService.getMessageKeyFormat();
        String expectedKey = String.format(keyFormat, 12345);

        // If the format is "message_%d", this should produce "message_12345"
        assertTrue(expectedKey.contains("12345"),
                "Formatted key should contain the message ID");
        assertTrue(expectedKey.matches("message_\\d+"),
                "Formatted key should match pattern 'message_<number>'");
    }

    /**
     * The contract file exists and is not empty.
     */
    @Test
    void contractFileExists() throws IOException {
        String content = readContractProperties();
        assertFalse(content.isBlank(),
                "Contract file should not be empty");

        Map<String, String> props = parseProperties(content);
        assertTrue(props.size() >= 2,
                "Contract file should have at least key.format and ttl.seconds");
    }
}
