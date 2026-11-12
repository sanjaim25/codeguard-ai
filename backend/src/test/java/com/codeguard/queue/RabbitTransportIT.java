package com.codeguard.queue;

import com.codeguard.review.ReviewWorker;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Requires Docker. Local infrastructure failures must be explicitly recorded, never hidden as a pass. */
@DisabledIfEnvironmentVariable(named = "CODEGUARD_SKIP_BROKER_TESTS", matches = "true")
class RabbitTransportIT {
    @Test void confirmedDurableDeliveryAndMalformedMessageDeadLettering() {
        try (var broker = new RabbitMQContainer("rabbitmq:4-management-alpine")) {
            broker.start();
            var connection = new CachingConnectionFactory(broker.getHost(), broker.getAmqpPort());
            connection.setUsername(broker.getAdminUsername()); connection.setPassword(broker.getAdminPassword());
            connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED); connection.setPublisherReturns(true);
            var listener = new SimpleMessageListenerContainer(connection);
            try {
                var admin = new RabbitAdmin(connection);
                var topology = new ReviewQueueConfig().reviewTopology();
                for (var item : topology.getDeclarables()) {
                    if (item instanceof Exchange exchange) admin.declareExchange(exchange);
                    if (item instanceof org.springframework.amqp.core.Queue queue) admin.declareQueue(queue);
                    if (item instanceof Binding binding) admin.declareBinding(binding);
                }
                var template = new RabbitTemplate(connection); template.setMandatory(true);
                var worker = mock(ReviewWorker.class);
                var receiver = new ReviewQueueListener(worker);
                listener.setQueueNames(ReviewQueueConfig.QUEUE); listener.setPrefetchCount(1);
                listener.setDefaultRequeueRejected(false); listener.setMessageListener((MessageListener) receiver::receive); listener.start();
                var id = UUID.randomUUID();
                assertThat(new RabbitReviewPublisher(template).publish(id, ReviewOutbox.Kind.REVIEW)).isTrue();
                verify(worker, timeout(10000)).process(id);
                template.convertAndSend(ReviewQueueConfig.EXCHANGE, "review", "malformed");
                assertThat(template.receive(ReviewQueueConfig.DEAD_QUEUE, 10000)).isNotNull();
                verifyNoMoreInteractions(worker);
            } finally { listener.stop(); connection.destroy(); }
        }
    }
}
