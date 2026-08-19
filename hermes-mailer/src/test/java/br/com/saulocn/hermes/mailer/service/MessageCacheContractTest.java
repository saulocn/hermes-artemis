package br.com.saulocn.hermes.mailer.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the Redis cache protocol matches the contract in {@code contracts/message-cache.properties}.
 *
 * <p>The api and mailer both use the same Redis key format and TTL. These are frozen in the
 * properties file and read by this test to ensure both code sides remain synchronized.
 */
class MessageCacheContractTest {

    @Test
    void messageCacheKeyFormatMatchesContract() throws IOException {
        Properties props = loadContractProperties();
        String expectedKeyFormat = props.getProperty("key.format");

        // Test by formatting a sample ID and comparing the pattern
        Long testId = 42L;
        String expectedKey = String.format(expectedKeyFormat, testId);
        String actualKey = MessageService.getMessageKey(testId);

        assertEquals(expectedKey, actualKey,
                "message cache key format must match contracts/message-cache.properties");
    }

    @Test
    void messageCacheTtlMatchesContract() throws IOException {
        Properties props = loadContractProperties();
        String expectedTtl = props.getProperty("ttl.seconds");

        assertEquals(expectedTtl, MessageService.TTL_IN_SECONDS,
                "message cache TTL must match contracts/message-cache.properties");
    }

    private Properties loadContractProperties() throws IOException {
        Properties props = new Properties();
        String content = Files.readString(Path.of("..", "contracts", "message-cache.properties"));
        props.load(new java.io.StringReader(content));
        return props;
    }
}
