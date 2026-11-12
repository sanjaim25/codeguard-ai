package com.codeguard.common;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@ConditionalOnProperty(name = "codeguard.admin.enabled", havingValue = "true", matchIfMissing = false)
public class AdminController {

    private final AmqpAdmin amqpAdmin;
    private final JdbcTemplate jdbc;
    // Exact queue names from ReviewQueueConfig
    private static final String REVIEW_QUEUE = "codeguard.reviews.work";
    private static final String DLQ_NAME     = "codeguard.reviews.dead.work";

    public AdminController(AmqpAdmin amqpAdmin, JdbcTemplate jdbc) {
        this.amqpAdmin = amqpAdmin;
        this.jdbc = jdbc;
    }

    @GetMapping("/queues")
    public ResponseEntity<Map<String, Object>> queueStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        addQueueInfo(result, "review_queue", REVIEW_QUEUE);
        addQueueInfo(result, "dlq", DLQ_NAME);
        return ResponseEntity.ok(result);
    }

    private void addQueueInfo(Map<String, Object> result, String prefix, String queueName) {
        QueueInformation info = amqpAdmin.getQueueInfo(queueName);
        if (info != null) {
            result.put(prefix + "_messages", info.getMessageCount());
            result.put(prefix + "_consumers", info.getConsumerCount());
        } else {
            result.put(prefix + "_messages", -1);
            result.put(prefix + "_consumers", -1);
        }
    }

    @GetMapping("/jobs/failed")
    public ResponseEntity<Map<String, Object>> failedJobs() {
        // Columns from V4__pull_requests_and_review_jobs.sql: id, pull_request_id, commit_sha, status, attempts, updated_at
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, pull_request_id, commit_sha, status, attempts, updated_at " +
            "FROM review_jobs WHERE status IN ('FAILED','CANCELLED') ORDER BY updated_at DESC LIMIT 50"
        );
        return ResponseEntity.ok(Map.of(
            "timestamp", Instant.now().toString(),
            "count", rows.size(),
            "jobs", rows
        ));
    }

    @GetMapping("/cleanup-log")
    public ResponseEntity<Map<String, Object>> cleanupLog() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                "SELECT id, ran_at, table_name, rows_deleted FROM cleanup_log ORDER BY ran_at DESC LIMIT 100"
            );
        } catch (Exception e) {
            rows = List.of();
        }
        return ResponseEntity.ok(Map.of(
            "timestamp", Instant.now().toString(),
            "entries", rows
        ));
    }
}
