package com.codeguard;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

/** Uses a disposable container by default, or an explicitly supplied dedicated test database. */
class FoundationIT {
    @Test void migratesPostgresAndRejectsDuplicateGithubUsers() throws Exception {
        String external = System.getenv("CODEGUARD_TEST_DB_URL");
        try (var container = external == null ? new PostgreSQLContainer("postgres:17-alpine") : null) {
            if (container != null) container.start();
            String url = container == null ? external : container.getJdbcUrl();
            String user = container == null ? System.getenv("CODEGUARD_TEST_DB_USER") : container.getUsername();
            String password = container == null ? System.getenv("CODEGUARD_TEST_DB_PASSWORD") : container.getPassword();
            var flyway = Flyway.configure().dataSource(url, user, password).load();
            flyway.migrate();
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO user_accounts (id, github_user_id, login) VALUES (gen_random_uuid(), 987654321, 'test-user')");
                    org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                        () -> statement.execute("INSERT INTO user_accounts (id, github_user_id, login) VALUES (gen_random_uuid(), 987654321, 'duplicate')"));
                } finally { connection.rollback(); }
            }
        }
    }
}
