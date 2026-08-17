package br.com.saulocn.hermes.mailer.service.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code MailVO} carries the two things this system must not write to disk: who the mail is going
 * to, and what it says. Its {@code toString()} used to render both, and one INFO line per delivery
 * put every recipient address and every message body into the container log.
 *
 * <p>The fix is the object, not the log statement: a redacted {@code toString()} cannot leak no
 * matter who logs it later, whereas a log level is a convention the next commit undoes. These
 * tests are what stop the fields coming back.
 */
class MailVOLoggingTest {

    private static MailVO of(String to, String text) {
        MailVO vo = new MailVO();
        vo.setMessageId(42L);
        vo.setSubject("Promoção de verão");
        vo.setText(text);
        vo.setTo(to);
        return vo;
    }

    @Test
    void neverRendersTheMessageBody() {
        String body = "<p>Prezado cliente, seu código é 998877</p>";

        String rendered = of("ana@example.com", body).toString();

        assertFalse(rendered.contains("998877"), "the body must not reach the log");
        assertFalse(rendered.contains("Prezado"), "the body must not reach the log");
        assertTrue(rendered.contains("<" + body.length() + " chars>"),
                "a length is still useful for spotting an empty or enormous message");
    }

    @Test
    void masksTheRecipientButKeepsTheDomain() {
        String rendered = of("ana.paula@example.com", "x").toString();

        assertTrue(rendered.contains("a***@example.com"));
        assertFalse(rendered.contains("ana.paula"), "the local part is the identifying half");
    }

    @Test
    void redactsEntirelyWhatDoesNotLookLikeAnAddress() {
        // Nothing validates recipients on the way in, so this is arbitrary caller text. Returning
        // it verbatim — the obvious fallback — would leak precisely the unexpected input.
        assertTrue(of("not-an-address", "x").toString().contains("<redacted>"));
        assertFalse(of("not-an-address", "x").toString().contains("not-an-address"));

        // A leading '@' has no local part to keep, so there is nothing to partially mask.
        assertTrue(of("@example.com", "x").toString().contains("<redacted>"));
    }

    @Test
    void survivesNulls() {
        MailVO empty = new MailVO();

        String rendered = empty.toString();

        assertTrue(rendered.contains("<null>"), "must not throw while building a log line");
    }

    @Test
    void keepsWhatCorrelatesALogLineToARow() {
        MailVO vo = of("ana@example.com", "x");
        vo.setRecipientId(1001L);

        String rendered = vo.toString();

        assertTrue(rendered.contains("messageId=42"));
        assertTrue(rendered.contains("recipientId=1001"));
    }

    @Test
    void aSubjectIsKeptWhole() {
        // Deliberate: subjects here are campaign titles chosen by the operator, and support needs
        // to recognise them. Worth revisiting if this system ever sends per-person subject lines,
        // because then the subject becomes personal data too.
        assertEquals(true, of("ana@example.com", "x").toString().contains("Promoção de verão"));
    }
}
