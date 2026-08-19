package br.com.saulocn.hermes.mailer.broker;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** Same contract as {@link AbstractMailConsumerIT}, against RabbitMQ 4.x over native AMQP 1.0. */
@QuarkusTest
@TestProfile(MailConsumerTestProfile.class)
@WithTestResource(InfraTestResource.class)
@WithTestResource(RabbitMqTestResource.class)
class MailConsumerRabbitIT extends AbstractMailConsumerIT {
}
