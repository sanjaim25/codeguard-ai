package com.codeguard.common;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetentionCleanupServiceTest {

    @Test
    void runCleanupExecutesAllPurgeQueries() {
        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        when(mockJdbc.update(anyString(), (Object[]) any())).thenReturn(0);

        RetentionCleanupService service = new RetentionCleanupService(mockJdbc, 90, 30, 180);
        service.runCleanup();

        // Should have called update for deliveries, audit_events, findings, results, jobs
        // Plus logCleanup inserts (5 purge + 5 log inserts = 10 total calls)
        verify(mockJdbc, atLeast(5)).update(anyString(), (Object[]) any());
    }
}
