package com.codeguard.queue;

import java.util.UUID;

public interface ReviewMessagePublisher {
    /** True only after a broker ack with no unroutable return. Ambiguous delivery must be retried. */
    boolean publish(UUID jobId, ReviewOutbox.Kind kind);
}
