package br.com.saulocn.hermes.mailer.broker;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** Same contract as {@link AbstractMailConsumerIT}, against Artemis on the default address. */
@QuarkusTest
@TestProfile(MailConsumerTestProfile.class)
@WithTestResource(InfraTestResource.class)
@WithTestResource(ArtemisTestResource.class)
class MailConsumerArtemisIT extends AbstractMailConsumerIT {
}
