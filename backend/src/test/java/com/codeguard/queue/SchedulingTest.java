package com.codeguard.queue;

import com.codeguard.config.SchedulingConfig;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import static org.assertj.core.api.Assertions.*;

class SchedulingTest {
    @Test void blockedNetworkTasksDoNotStarveLeaseRecovery() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(SchedulingConfig.class)) {
            var scheduler = context.getBean(TaskScheduler.class);
            var networkStarted = new CountDownLatch(2);
            var releaseNetwork = new CountDownLatch(1);
            var recovered = new CountDownLatch(1);
            Runnable blockedNetwork = () -> {
                networkStarted.countDown();
                try { releaseNetwork.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            };
            try {
                scheduler.schedule(blockedNetwork, Instant.now());
                scheduler.schedule(blockedNetwork, Instant.now());
                assertThat(networkStarted.await(5, TimeUnit.SECONDS)).isTrue();
                scheduler.schedule(recovered::countDown, Instant.now());
                assertThat(recovered.await(5, TimeUnit.SECONDS)).isTrue();
            } finally { releaseNetwork.countDown(); }
        }
    }
}
