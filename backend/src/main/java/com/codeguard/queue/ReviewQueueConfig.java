package com.codeguard.queue;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name = "codeguard.queue.enabled", havingValue = "true", matchIfMissing = true)
public class ReviewQueueConfig {
    public static final String EXCHANGE = "codeguard.reviews";
    public static final String QUEUE = "codeguard.reviews.work";
    public static final String DEAD_EXCHANGE = "codeguard.reviews.dead";
    public static final String DEAD_QUEUE = "codeguard.reviews.dead.work";
    public static final String ROUTING = "review";
    @Bean Declarables reviewTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var dead = new DirectExchange(DEAD_EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(ROUTING).build();
        var deadQueue = QueueBuilder.durable(DEAD_QUEUE).build();
        return new Declarables(exchange, dead, queue, deadQueue,
            BindingBuilder.bind(queue).to(exchange).with(ROUTING), BindingBuilder.bind(deadQueue).to(dead).with(ROUTING));
    }
}
