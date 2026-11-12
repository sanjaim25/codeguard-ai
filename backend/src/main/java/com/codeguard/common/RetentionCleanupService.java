package com.codeguard.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "codeguard.retention.enabled", havingValue = "true", matchIfMissing = false)
public class RetentionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupService.class);
    private final JdbcTemplate jdbc;
    private final int defaultDays;
    private final int deliveryDays;
    private final int auditDays;

    public RetentionCleanupService(
            JdbcTemplate jdbc,
            @org.springframework.beans.factory.annotation.Value("${codeguard.retention.default-days:90}") int defaultDays,
            @org.springframework.beans.factory.annotation.Value("${codeguard.retention.delivery-days:30}") int deliveryDays,
            @org.springframework.beans.factory.annotation.Value("${codeguard.retention.audit-days:180}") int auditDays) {
        this.jdbc = jdbc;
        this.defaultDays = defaultDays;
        this.deliveryDays = deliveryDays;
        this.auditDays = auditDays;
    }

    @Scheduled(cron = "${codeguard.retention.cron:0 0 2 * * *}")
    @Transactional
    public void runCleanup() {
        log.info("event=retention_cleanup_start default_days={} delivery_days={} audit_days={}", defaultDays, deliveryDays, auditDays);
        purgeDeliveries();
        purgeAuditEvents();
        purgeOldJobs();
        log.info("event=retention_cleanup_complete");
    }

    private void purgeDeliveries() {
        // webhook_deliveries uses created_at (V5 schema)
        int deleted = jdbc.update(
            "DELETE FROM webhook_deliveries WHERE created_at < NOW() - (? || ' days')::INTERVAL",
            deliveryDays);
        log.info("event=retention_cleanup table=webhook_deliveries deleted={}", deleted);
        logCleanup("webhook_deliveries", deleted);
    }

    private void purgeAuditEvents() {
        // audit_events uses created_at (V3 schema)
        int deleted = jdbc.update(
            "DELETE FROM audit_events WHERE created_at < NOW() - (? || ' days')::INTERVAL",
            auditDays);
        log.info("event=retention_cleanup table=audit_events deleted={}", deleted);
        logCleanup("audit_events", deleted);
    }

    private void purgeOldJobs() {
        // Schema: review_jobs -> pull_request_id -> pull_requests -> repository_id -> repositories
        // review_findings -> result_id -> review_results -> job_id -> review_jobs
        // Delete findings first (FK: review_findings.result_id -> review_results.id)
        int findings = jdbc.update(
            "DELETE FROM review_findings rf " +
            "USING review_results rr, review_jobs rj, pull_requests pr, repositories r " +
            "WHERE rf.result_id = rr.id " +
            "AND rr.job_id = rj.id " +
            "AND rj.pull_request_id = pr.id " +
            "AND pr.repository_id = r.id " +
            "AND rj.status IN ('COMPLETED','FAILED','CANCELLED') " +
            "AND rj.updated_at < NOW() - ((COALESCE(r.retention_days, ?) || ' days')::INTERVAL)",
            defaultDays);
        log.info("event=retention_cleanup table=review_findings deleted={}", findings);
        logCleanup("review_findings", findings);

        // Delete results (FK: review_results.job_id -> review_jobs.id, UNIQUE constraint)
        int results = jdbc.update(
            "DELETE FROM review_results rr " +
            "USING review_jobs rj, pull_requests pr, repositories r " +
            "WHERE rr.job_id = rj.id " +
            "AND rj.pull_request_id = pr.id " +
            "AND pr.repository_id = r.id " +
            "AND rj.status IN ('COMPLETED','FAILED','CANCELLED') " +
            "AND rj.updated_at < NOW() - ((COALESCE(r.retention_days, ?) || ' days')::INTERVAL)",
            defaultDays);
        log.info("event=retention_cleanup table=review_results deleted={}", results);
        logCleanup("review_results", results);

        // Delete jobs last
        int jobs = jdbc.update(
            "DELETE FROM review_jobs rj " +
            "USING pull_requests pr, repositories r " +
            "WHERE rj.pull_request_id = pr.id " +
            "AND pr.repository_id = r.id " +
            "AND rj.status IN ('COMPLETED','FAILED','CANCELLED') " +
            "AND rj.updated_at < NOW() - ((COALESCE(r.retention_days, ?) || ' days')::INTERVAL)",
            defaultDays);
        log.info("event=retention_cleanup table=review_jobs deleted={}", jobs);
        logCleanup("review_jobs", jobs);
    }

    private void logCleanup(String tableName, long rowsDeleted) {
        jdbc.update("INSERT INTO cleanup_log (table_name, rows_deleted) VALUES (?, ?)", tableName, rowsDeleted);
    }
}
