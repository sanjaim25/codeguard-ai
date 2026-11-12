# Implementation and acceptance plan

Each phase closes only after its relevant code builds and checks run. Live checks requiring external credentials remain visibly UNVERIFIED. A passing unit test does not substitute for a live integration test.

| Phase | Implementation | Acceptance evidence |
|---|---|---|
| 1 Foundation | Java 21 / Spring Boot, React / TypeScript, session-security baseline, PostgreSQL / Flyway, Compose, build tooling | Backend unit tests, frontend build/lint/tests, database migration and server startup, Compose startup if Docker available |
| 2 Core backend | Users, installations, repository grants/policies, PRs, jobs, results/findings, deliveries, usage, audit; DTOs and scoped paginated APIs | PostgreSQL constraints and indexes; service/controller validation and cross-user isolation tests |
| 3 GitHub | Browser OAuth separate from App JWTs; installation discovery; HMAC over raw body; supported PR events; idempotency; API timeouts/rate limits | Deterministic HTTP fixtures for token refresh, authorization, pagination, invalid signatures and duplicate deliveries; live OAuth/App checks with credentials |
| 4 Review engine | Transactional outbox; publisher confirms; durable queue and DLQ; conditional claims; leases; delayed bounded retry; cancellation; diffs and static checks | PostgreSQL + RabbitMQ Testcontainers tests, restart/retry/duplicate delivery scenarios, lifecycle tests |
| 5 AI | Mock and cloud providers; strict schema and content validation; changed-line mapping; private-code consent; file/diff limits; token/cost/concurrency budgets | Provider success/error/timeout fixtures, invalid findings and prompt-injection boundaries; real cloud request only with configured credentials |
| 6 Frontend | Landing/sign-in, responsive shell, dashboard, repositories, PR list/detail, findings, analytics, settings, empty/error states; syntax-highlighted diff | Component and client tests, build/lint, browser journeys against seeded database; screenshots if available |
| 7 Publishing | Manual approval by default; explicit per-repository auto mode; write authorization; fresh head SHA; valid positions; durable duplicate suppression/reconciliation | Mock GitHub request assertions and ambiguous failure cases; live review on a test repository |
| 8 Hardening | CSRF, secure headers, safe logs/errors, authorization, request limits, operational health, CI tests/dependency/image checks | Full automated suite, security regression tests, Compose health and end-to-end smoke test |
| 9 Documentation/demo | README with PowerShell/POSIX commands, architecture, API, security, demo script, credential instructions, production options, honest resume bullets | New-user setup walkthrough, explicit verified/unverified matrix, no tracked secrets |

## Mandatory invariants

1. Every dashboard resource, list and metric is scoped to the signed-in user's repository grants. Publishing/settings require write permission.
2. Browser authentication is never proof of App installation identity. Neither browser OAuth tokens nor installation tokens are exposed to JavaScript.
3. Webhooks are authenticated before parsing and never cause duplicate reviews for the same repository/PR/head SHA.
4. Job persistence and outbox creation share a transaction. Queue messages are identifiers, not secrets or code.
5. Database claims prevent parallel work for the same job. Retry policy is bounded; terminal errors are visible.
6. Private repository code cannot leave the server through the cloud provider without explicit policy consent.
7. Repository content cannot become model instructions or executable commands. No PR code is executed.
8. Findings cannot invent inline locations; unmappable observations remain general.
9. Publishing rechecks current SHA and valid changed lines, and reconciles ambiguous responses before retrying.
10. Production metrics come only from persisted records. Seeded demo data is explicitly labeled.

## Deliverables checklist

- [x] Source structure, architecture, environment template, Compose and Dockerfiles (local runtime verification complete)
- [x] Java domain and APIs; OAuth/App integration; async review engine; AI clients; publishing (live gates remain)
- [x] React dashboard and diff viewer
- [x] PostgreSQL schema with migrations, FK/unique/index coverage
- [x] Backend/frontend/integration tests and CI workflow authored (broker/container/remote CI execution remains)
- [x] API guide and generated OpenAPI tested
- [x] Local demo, screenshots, deployment guide, completed-feature resume bullets
- [x] Requirement-by-requirement evidence audit: docs/ACCEPTANCE.md
- [ ] All external acceptance gates verified; full goal complete
