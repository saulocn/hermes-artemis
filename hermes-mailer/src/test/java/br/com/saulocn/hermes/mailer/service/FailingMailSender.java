package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.service.vo.MailVO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * An SMTP failure, on demand.
 *
 * <p>{@code quarkus.mailer.mock} makes the mailer succeed silently, which is what the parity
 * tests want and exactly what the failure path cannot be tested against.
 *
 * <p>No {@code @Priority}: without it this only wins where a profile names it in
 * {@code getEnabledAlternatives()}, so it cannot leak into the tests that expect mail to be sent.
 * An earlier alternative in the enqueuer had one and did leak.
 */
@Alternative
@ApplicationScoped
public class FailingMailSender extends MailSenderService {

    static final String MESSAGE = "SMTP refused this one";

    @Override
    public void sendHtmlMail(MailVO mailVO) {
        throw new IllegalStateException(MESSAGE);
    }
}
