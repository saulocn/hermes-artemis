package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.batch.vo.RecipientVO;
import br.com.saulocn.hermes.enqueuer.entity.Message;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the DB -> queue pump: unprocessed recipients get published and flagged as processed. */
@QuarkusTest
@TestProfile(BatchTestProfile.class)
@WithTestResource(PostgresTestResource.class)
class MailEnqueuerJobIT {

    @Inject
    BatchFixtures fixtures;

    @Inject
    @Any
    InMemoryConnector connector;

    private InMemorySink<String> sink;

    @BeforeEach
    void resetSink() {
        sink = connector.sink("mail-requests");
        sink.clear();
    }

    @Test
    void publishesUnprocessedRecipientsAndFlagsThem() {
        Message message = fixtures.createMessage();
        List<Long> ids = fixtures.createRecipients(message.getId(), 3, false, false, LocalDateTime.now());

        BatchJobs.runToCompletion("mail-enqueuer-chunk");

        Set<Long> published = sink.received().stream()
                .map(m -> RecipientVO.fromJSON(m.getPayload()).getId())
                .collect(Collectors.toSet());

        assertTrue(published.containsAll(ids),
                "expected all created recipients on the queue, published=" + published + " created=" + ids);
        ids.forEach(id -> assertTrue(fixtures.isProcessed(id), "recipient " + id + " should be processed"));
    }
}
