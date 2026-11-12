package com.codeguard.analytics;

import com.codeguard.security.RepositoryAccess;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every aggregate starts with the caller's current repository grants; no global metrics leak. */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private static final String SCOPE = """
        with visible_repos as (
          select r.id, r.full_name from repositories r
          join github_installations i on i.id = r.installation_id
          join repository_grants g on g.repository_id = r.id
          where g.user_id = :userId and g.expires_at > :now and r.active and i.active
            and (:repositoryId is null or r.id = cast(:repositoryId as uuid))
        ), scoped_jobs as (
          select j.*, p.repository_id, p.number, p.title, r.full_name
          from review_jobs j join pull_requests p on p.id = j.pull_request_id
          join visible_repos r on r.id = p.repository_id
          where j.created_at >= :since and j.created_at <= :now
        ), scoped_findings as (
          select f.*, j.repository_id from review_findings f
          join review_results r on r.id = f.result_id join scoped_jobs j on j.id = r.job_id
        )
        """;
    private final NamedParameterJdbcTemplate jdbc;
    private final RepositoryAccess access;
    private final Clock clock;
    public AnalyticsService(NamedParameterJdbcTemplate jdbc, RepositoryAccess access, Clock clock) { this.jdbc = jdbc; this.access = access; this.clock = clock; }
    public record Totals(long repositories, long reviews, long pullRequestsReviewed, long completed, long failed, long active, long findings, long criticalHighFindings, Double averageProcessingSeconds) {}
    public record Bucket(String name, long count) {}
    public record Week(String week, long reviews, long completed, long failed) {}
    public record RepositoryTrend(UUID id, String name, long reviews, long findings, long criticalHighFindings) {}
    public record Activity(UUID jobId, UUID pullRequestId, String repository, int number, String title, String status, Instant createdAt, long findings) {}
    public record Usage(long measuredInputTokens, long measuredOutputTokens, long estimatedInputTokens, long estimatedOutputTokens, BigDecimal estimatedCostUsd, BigDecimal reservedCostUsd) {}
    public record Dashboard(Instant since, Instant until, Totals totals, List<Bucket> severity, List<Bucket> category, List<Week> weekly, List<RepositoryTrend> repositories, List<Activity> recent, Usage usage) {}

    public Dashboard get(UUID userId, UUID repositoryId, int days) {
        if (days < 7 || days > 365) throw new IllegalArgumentException("Analytics period must be 7–365 days");
        if (repositoryId != null) access.read(userId, repositoryId);
        Instant now = clock.instant();
        Instant since = now.atZone(ZoneOffset.UTC).toLocalDate().minusDays(days - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
        var params = new MapSqlParameterSource().addValue("userId", userId).addValue("repositoryId", repositoryId == null ? null : repositoryId.toString(), Types.VARCHAR)
            .addValue("now", Timestamp.from(now)).addValue("since", Timestamp.from(since));
        var totals = jdbc.queryForObject(SCOPE + """
            select (select count(*) from visible_repos) repositories, count(*) reviews,
              count(distinct pull_request_id) filter (where status='COMPLETED') prs,
              count(*) filter (where status='COMPLETED') completed,
              count(*) filter (where status='FAILED') failed,
              count(*) filter (where status in ('PENDING','QUEUED','PROCESSING')) active,
              (select count(*) from scoped_findings) findings,
              (select count(*) from scoped_findings where severity in ('CRITICAL','HIGH')) critical_high,
              avg(greatest(0, extract(epoch from (completed_at-started_at)))) filter (where status='COMPLETED' and started_at is not null) average_seconds
            from scoped_jobs
            """, params, (rs, row) -> new Totals(rs.getLong("repositories"), rs.getLong("reviews"), rs.getLong("prs"), rs.getLong("completed"), rs.getLong("failed"), rs.getLong("active"), rs.getLong("findings"), rs.getLong("critical_high"), rs.getObject("average_seconds") == null ? null : rs.getDouble("average_seconds")));
        var severity = buckets(params, "severity"); var category = buckets(params, "category");
        var weeklyRows = jdbc.query(SCOPE + """
            select date_trunc('week', created_at at time zone 'UTC')::date week, count(*) reviews,
              count(*) filter (where status='COMPLETED') completed, count(*) filter (where status='FAILED') failed
            from scoped_jobs group by week order by week
            """, params, (rs, row) -> new Week(rs.getDate("week").toLocalDate().toString(), rs.getLong("reviews"), rs.getLong("completed"), rs.getLong("failed")));
        Map<String, Week> weeks = new HashMap<>(); weeklyRows.forEach(week -> weeks.put(week.week(), week));
        List<Week> weekly = new ArrayList<>();
        for (LocalDate date = since.atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); !date.isAfter(now.atZone(ZoneOffset.UTC).toLocalDate()); date = date.plusWeeks(1))
            weekly.add(weeks.getOrDefault(date.toString(), new Week(date.toString(), 0, 0, 0)));
        var repositories = jdbc.query(SCOPE + """
            select r.id, r.full_name, (select count(*) from scoped_jobs j where j.repository_id=r.id) reviews,
              (select count(*) from scoped_findings f where f.repository_id=r.id) findings,
              (select count(*) from scoped_findings f where f.repository_id=r.id and f.severity in ('CRITICAL','HIGH')) critical_high
            from visible_repos r order by reviews desc, r.full_name limit 10
            """, params, (rs, row) -> new RepositoryTrend(rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getLong("reviews"), rs.getLong("findings"), rs.getLong("critical_high")));
        var recent = jdbc.query(SCOPE + """
            select j.*, (select count(*) from review_results r join review_findings f on f.result_id=r.id where r.job_id=j.id) findings
            from scoped_jobs j order by j.created_at desc limit 10
            """, params, (rs, row) -> new Activity(rs.getObject("id", UUID.class), rs.getObject("pull_request_id", UUID.class), rs.getString("full_name"), rs.getInt("number"), rs.getString("title"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getLong("findings")));
        var usage = jdbc.queryForObject(SCOPE + """
            select coalesce(sum(u.input_tokens) filter (where u.measured_tokens),0) measured_input,
              coalesce(sum(u.output_tokens) filter (where u.measured_tokens),0) measured_output,
              coalesce(sum(u.input_tokens) filter (where not u.measured_tokens),0) estimated_input,
              coalesce(sum(u.output_tokens) filter (where not u.measured_tokens),0) estimated_output,
              sum(u.estimated_cost_usd) estimated_cost,
              (select coalesce(sum(b.reserved_usd),0) from ai_budget_reservations b join scoped_jobs j on j.id=b.job_id) reserved_cost
            from ai_usage_records u join scoped_jobs j on j.id=u.job_id
            """, params, (rs, row) -> new Usage(rs.getLong("measured_input"), rs.getLong("measured_output"), rs.getLong("estimated_input"), rs.getLong("estimated_output"), rs.getBigDecimal("estimated_cost"), rs.getBigDecimal("reserved_cost")));
        return new Dashboard(since, now, totals, severity, category, List.copyOf(weekly), repositories, recent, usage);
    }
    private List<Bucket> buckets(MapSqlParameterSource params, String column) {
        // column is chosen only by the two fixed calls above, never by request input.
        return jdbc.query(SCOPE + "select " + column + " name, count(*) count from scoped_findings group by " + column + " order by count desc, name", params,
            (rs, row) -> new Bucket(rs.getString("name"), rs.getLong("count")));
    }
}
