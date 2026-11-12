package com.codeguard.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        // Publication preflight/POST, broker outbox dispatch, and lease recovery must not
        // block one another. Each fixed-delay task still has at most one invocation in flight.
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("codeguard-scheduler-");
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
