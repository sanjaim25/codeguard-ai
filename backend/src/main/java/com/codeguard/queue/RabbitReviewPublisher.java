package com.codeguard.queue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codeguard.queue.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitReviewPublisher implements ReviewMessagePublisher {
    private final RabbitTemplate rabbit;
    public RabbitReviewPublisher(RabbitTemplate rabbit) { this.rabbit = rabbit; }
    public boolean publish(UUID jobId, ReviewOutbox.Kind kind) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(jobId.toString());
        var correlation = new CorrelationData(UUID.randomUUID().toString());
        String exchange = kind == ReviewOutbox.Kind.REVIEW ? ReviewQueueConfig.EXCHANGE : ReviewQueueConfig.DEAD_EXCHANGE;
        try {
            rabbit.send(exchange, ReviewQueueConfig.ROUTING, new Message(jobId.toString().getBytes(StandardCharsets.UTF_8), properties), correlation);
            return correlation.getFuture().get(5, TimeUnit.SECONDS).isAck() && correlation.getReturned() == null;
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        catch (Exception unavailable) { return false; }
    }
}
