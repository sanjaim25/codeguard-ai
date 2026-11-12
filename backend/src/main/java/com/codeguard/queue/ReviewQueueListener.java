package com.codeguard.queue;

import com.codeguard.review.ReviewWorker;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codeguard.queue.enabled", havingValue = "true", matchIfMissing = true)
public class ReviewQueueListener {
    private final ReviewWorker worker;
    public ReviewQueueListener(ReviewWorker worker) { this.worker = worker; }
    @RabbitListener(queues = ReviewQueueConfig.QUEUE)
    public void receive(Message message) {
        if (message.getBody().length != 36) throw new AmqpRejectAndDontRequeueException("Invalid job message");
        String value = new String(message.getBody(), StandardCharsets.US_ASCII);
        if (!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new AmqpRejectAndDontRequeueException("Invalid job message");
        worker.process(UUID.fromString(value));
    }
}
