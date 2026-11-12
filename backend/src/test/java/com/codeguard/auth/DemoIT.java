package com.codeguard.auth;

import com.codeguard.repository.*;
import com.codeguard.review.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"codeguard.demo=true", "codeguard.queue.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false", "spring.datasource.hikari.maximum-pool-size=3"})
@AutoConfigureMockMvc
@Transactional
class DemoIT {
    static PostgreSQLContainer postgres;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("CODEGUARD_TEST_DB_URL");
        if (url == null) {
            postgres = new PostgreSQLContainer("postgres:17-alpine"); postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv("CODEGUARD_TEST_DB_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("CODEGUARD_TEST_DB_PASSWORD"));
        }
    }
    @Autowired MockMvc mvc;
    @Autowired CodeRepositoryStore repositories;
    @Autowired ReviewJobStore jobs;
    @Autowired ReviewResultStore results;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.codeguard.github.GithubClient github;
    @Test void demoRequiresCsrfCreatesScopedFixturesAndRotatesSession() throws Exception {
        mvc.perform(post("/api/auth/demo")).andExpect(status().isForbidden());
        var session = new MockHttpSession(); var originalId = session.getId();
        mvc.perform(post("/api/auth/demo").session(session).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.login").value("demo-developer"));
        assertThat(session.getId()).isNotEqualTo(originalId);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.login").value("demo-developer"));
        mvc.perform(get("/api/analytics").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totals.repositories").value(3)).andExpect(jsonPath("$.totals.reviews").value(6))
            .andExpect(jsonPath("$.totals.findings").value(5)).andExpect(jsonPath("$.totals.criticalHighFindings").value(3));
        jdbc.update("update review_jobs set created_at = created_at - interval '60 days'");
        mvc.perform(get("/api/analytics").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totals.reviews").value(0));
        mvc.perform(post("/api/auth/demo").session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/analytics").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totals.reviews").value(6)).andExpect(jsonPath("$.totals.findings").value(5));
        assertThat(jobs.count()).isEqualTo(6); assertThat(results.count()).isEqualTo(6);
        var repo = repositories.findByGithubRepositoryId(-1001).orElseThrow();
        mvc.perform(patch("/api/repositories/" + repo.getId() + "/policy").session(session).with(csrf()).contentType("application/json")
            .content("{\"reviewEnabled\":true,\"cloudAiOptIn\":true,\"publishingEnabled\":false,\"autoPublish\":false}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(github);
    }
}
