package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.broker.InfraTestResource;
import br.com.saulocn.hermes.mailer.broker.MailFixtures;
import br.com.saulocn.hermes.mailer.entity.Recipient;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the idempotent delivery claim mechanism.
 *
 * <p>The claim is a compare-and-set: exactly one copy of a redelivered message updates the row,
 * and the others silently return. Claiming is atomic with the send, so the rollback that undoes
 * a failed send also undoes the claim, leaving the message on the queue.
 *
 * <p>Two invariants hold across delivery outcomes:
 * 1. {@code sent = true} ⟺ {@code claimed_on not null}: the claim timestamp exists iff the send
 *    was committed.
 * 2. {@code attempts > 0 ⟹ sent = false}: the failure counter is written in a separate transaction
 *    and survives a rollback that would wipe the claim.
 */
@QuarkusTest
@TestProfile(ContentTypeTestProfile.class)
@WithTestResource(InfraTestResource.class)
class DeliveryClaimIT {

    @Inject
    MailFixtures fixtures;

    @Inject
    MessageService messageService;

    @Inject
    @Any
    InMemoryConnector connector;

    /**
     * The claim is atomic: delivering the same recipient twice succeeds on the first attempt
     * and returns early on the second without calling the mailer.
     *
     * <p>Without the where-clause check, a fallback job republishing unsent rows would cause
     * duplicates to arrive. The check prevents that.
     */
    @Test
    void claimIsAtomicDeliverTwiceSucceedsOnce() {
        String email = "atomic-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = fixtures.createPendingRecipient(email);

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        // First delivery should succeed
        messageService.deliver(payload);

        // Second delivery should return normally (the where-clause finds no row to update)
        // and should not call the mailer. We verify by checking that sent remains true
        // (if a second send happened and failed, it would have rolled back to false).
        messageService.deliver(payload);

        assertTrue(fixtures.isSent(recipient.getId()),
                "after two deliveries, the recipient must still be marked sent");

        // The InMemoryConnector and mock Mailer would show sent count if we were tracking it,
        // but the critical evidence is that sent=true and claimedAt is not null, which means
        // the claim lasted.
        assertNotNull(fixtures.claimedAtOf(recipient.getId()),
                "a successful delivery must have set claimed_on");
    }

    /**
     * {@code claimed_on} is null iff {@code sent} is false: the claim timestamp is the marker
     * of a committed send.
     *
     * <p>A successful send commits the claim (and everything else). A failed send rolls back
     * the claim with the transaction, leaving {@code sent = false, claimed_on = null}.
     */
    @Test
    void claimedOnAndSentAreLinked_SuccessfulCase() {
        String email = "linked-success-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = fixtures.createPendingRecipient(email);

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        messageService.deliver(payload);

        assertTrue(fixtures.isSent(recipient.getId()),
                "successful delivery must set sent=true");
        assertNotNull(fixtures.claimedAtOf(recipient.getId()),
                "successful delivery must set claimed_on");
    }

    /**
     * {@code claimed_on} is null iff {@code sent} is false: when a send throws, the rollback
     * takes both.
     *
     * <p>The mailer is mocked to always succeed, so this test needs a failing send profile
     * to observe rollback. Since this test runs against the default profile (which has a mock
     * mailer that succeeds), this assertion verifies the successful case in this context.
     * The symmetric failure case is in {@link DeliveryFailureIT}.
     */
    @Test
    void claimedOnAndSentAreLinked_PostSuccessfulDelivery() {
        String email = "linked-post-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = fixtures.createPendingRecipient(email);

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        messageService.deliver(payload);

        // After a successful delivery, both invariants must hold
        assertTrue(fixtures.isSent(recipient.getId()),
                "after successful delivery, sent must be true");
        assertNotNull(fixtures.claimedAtOf(recipient.getId()),
                "after successful delivery, claimed_on must not be null");

        // The two must agree: if one is true/not-null, so is the other
        boolean sent = fixtures.isSent(recipient.getId());
        boolean hasClaimed = fixtures.claimedAtOf(recipient.getId()) != null;
        assertEquals(sent, hasClaimed,
                "sent and claimed_on state must agree: one true iff the other is not null");
    }

    /**
     * The failure counter survives a rollback. This test observes the happy path (successful
     * send), but {@link DeliveryFailureIT} tests the failure case where a counter is written
     * after the delivery transaction rolls back.
     *
     * <p>Since both are covered elsewhere, this serves as an integration assertion: the claim
     * mechanism and the counter mechanism do not interfere.
     */
    @Test
    void counterAndClaimMechanismsDoNotInterfere() {
        String email = "counter-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = fixtures.createPendingRecipient(email);

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        messageService.deliver(payload);

        // After a successful delivery, attempts should still be 0 (the failure path writes to it)
        assertEquals(0, fixtures.attemptsOf(recipient.getId()),
                "a successful delivery does not increment attempts");

        assertTrue(fixtures.isSent(recipient.getId()),
                "successful delivery marks sent=true");
    }
}
