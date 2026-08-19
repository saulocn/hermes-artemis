package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.broker.InfraTestResource;
import br.com.saulocn.hermes.mailer.broker.MailFixtures;
import br.com.saulocn.hermes.mailer.entity.Message;
import br.com.saulocn.hermes.mailer.entity.Recipient;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify that contentType from the message is honored: text/plain uses Mail.withText and
 * produces email in the text body, while text/html (or null/blank) uses Mail.withHtml and
 * produces email in the html body.
 */
@QuarkusTest
@TestProfile(ContentTypeTestProfile.class)
@WithTestResource(InfraTestResource.class)
class MailSenderContentTypeIT {

    @Inject
    EntityManager entityManager;

    @Inject
    MockMailbox mailbox;

    @Inject
    @Any
    InMemoryConnector connector;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    @Test
    void textPlainContentTypeIsDeliveredAsTextBody() {
        String email = "plain-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = createPendingRecipientWithContentType(email, "text/plain",
                "This is plain text");

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        connector.source("mail").send(payload.toJSON());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Mail> mails = mailbox.getMailsSentTo(email);
            assertEquals(1, mails.size(), "expected exactly one mail");
            Mail mail = mails.get(0);
            // For text/plain, the content should be in the text body
            assertEquals("This is plain text", mail.getText(),
                    "plain text content should be in text body");
            // HTML body should be empty or null for text/plain
            assertTrue(mail.getHtml() == null || mail.getHtml().isEmpty(),
                    "html body should be empty for text/plain content");
        });
    }

    @Test
    void textHtmlContentTypeIsDeliveredAsHtmlBody() {
        String email = "html-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = createPendingRecipientWithContentType(email, "text/html",
                "<b>This is HTML</b>");

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        connector.source("mail").send(payload.toJSON());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Mail> mails = mailbox.getMailsSentTo(email);
            assertEquals(1, mails.size(), "expected exactly one mail");
            Mail mail = mails.get(0);
            // For text/html, the content should be in the html body
            assertEquals("<b>This is HTML</b>", mail.getHtml(),
                    "html content should be in html body");
        });
    }

    @Test
    void nullContentTypeDefaultsToHtmlBody() {
        String email = "null-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = createPendingRecipientWithContentType(email, null,
                "<p>Default to HTML</p>");

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        connector.source("mail").send(payload.toJSON());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Mail> mails = mailbox.getMailsSentTo(email);
            assertEquals(1, mails.size(), "expected exactly one mail");
            Mail mail = mails.get(0);
            // Null content type should default to HTML
            assertEquals("<p>Default to HTML</p>", mail.getHtml(),
                    "null contentType should default to html body");
        });
    }

    @Test
    void blankContentTypeDefaultsToHtmlBody() {
        String email = "blank-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = createPendingRecipientWithContentType(email, "   ",
                "<p>Blank defaults to HTML</p>");

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        connector.source("mail").send(payload.toJSON());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Mail> mails = mailbox.getMailsSentTo(email);
            assertEquals(1, mails.size(), "expected exactly one mail");
            Mail mail = mails.get(0);
            // Blank content type should default to HTML
            assertEquals("<p>Blank defaults to HTML</p>", mail.getHtml(),
                    "blank contentType should default to html body");
        });
    }

    @Test
    void caseInsensitiveContentTypeMatching() {
        String email = "case-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = createPendingRecipientWithContentType(email, "TEXT/PLAIN",
                "Case-insensitive plain");

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        connector.source("mail").send(payload.toJSON());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Mail> mails = mailbox.getMailsSentTo(email);
            assertEquals(1, mails.size(), "expected exactly one mail");
            Mail mail = mails.get(0);
            // Case-insensitive match should work
            assertEquals("Case-insensitive plain", mail.getText(),
                    "TEXT/PLAIN (uppercase) should match text/plain");
            assertTrue(mail.getHtml() == null || mail.getHtml().isEmpty(),
                    "html body should be empty for case-insensitive text/plain");
        });
    }

    @Transactional
    public Recipient createPendingRecipientWithContentType(String email, String contentType, String text) {
        Message message = new Message();
        message.setTitle("Content Type Test");
        message.setText(text);
        message.setContentType(contentType);
        entityManager.persist(message);

        Recipient recipient = new Recipient(email, message.getId());
        recipient.setProcessed(true);
        recipient.setSent(false);
        entityManager.persist(recipient);

        return recipient;
    }
}
