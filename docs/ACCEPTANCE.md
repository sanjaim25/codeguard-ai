# Acceptance audit — 2026-11-10

This audit maps the master prompt to implementation and verification evidence. **The full definition of done is not met:** live GitHub/cloud checks and remote CI execution remain unverified. No external deployment or remote push has been performed.

## Requirements and evidence

| Master-prompt requirement | Implementation and evidence | Remaining qualification |
|---|---|---|
| Java 21/Spring Boot modular monolith; no Python backend or local model dependency | Maven project and feature packages under `backend/src/main/java/com/codeguard`; Java compilation/package passes | Boot 3.5 line selected for JUnit 5 compatibility |
| PostgreSQL/Flyway/JPA schema, keys, indexes, validation | Migrations V1–V9; Hibernate validate; `FoundationIT` and `RepositoryAccessIT` pass on isolated PostgreSQL and PostgreSQL 17 Testcontainers/Compose | No production database migration is claimed |
| A: GitHub user authentication, session, profile, logout, protected APIs | OAuth user flow and server-session credential repository; `OAuthSecurityTest`, controller/security and demo tests | Real GitHub OAuth requires credentials and callback setup |
| B: App installation discovery, JWT/tokens, retrieval, HMAC/idempotent webhooks | `github` module; token/client/signature tests and database discovery/webhook tests | Real App installation and webhook delivery require configuration |
| C: review lifecycle, outbox, RabbitMQ, retries/DLQ, duplicate protection | `review` and `queue` modules; database worker transactions, lease fencing, retries and scheduler starvation regression pass; `RabbitTransportIT` passes against real RabbitMQ | No production broker deployment is claimed |
| D: cloud/mock provider, schema, consent, budgets, failure handling | `ai`/`analysis` modules; provider and analysis tests; mock worker result persisted and browser-visible | No paid/live provider request; prices remain operator-entered estimates |
| E: landing, sign-in, dashboard, repositories, PRs, review details, analytics, settings, error/empty states | React pages and shared components, responsive dark/light appearance, component tests and Chrome demo journeys | Demo uses labeled persisted fixtures; no production traffic claimed |
| F: changed files/lines, syntax highlighting, inline markers, severity and explanations | Safe React/Prism diff viewer; parser/XSS/location tests and browser interaction | Findings are advisory; invalid/truncated locations remain general |
| G: manual/automatic publishing, policy, commit/line validation, duplicate prevention | `publishing` module, V8, preview UI; mocked GitHub and separate database transaction tests | Live posting unverified; ambiguous submissions are never automatically re-POSTed |
| H: real scoped analytics, weekly/severity/category/repository/duration/usage/cost | SQL aggregates through valid repository grants; API integration tests and charts | Costs estimated; token measurements explicitly distinguished |
| Security: resource isolation, CSRF, validation, safe errors, headers, rate limits, no raw HTML/code execution | Security chain and resource grants; authorization/CSRF/rate-limit tests; safe React rendering; credential-free source scan; metadata-only structured webhook/review lifecycle logs exercised in integration tests | Not a penetration test. Shared ingress limits/TLS/encrypted storage are operator responsibilities |
| Credential storage | OAuth credentials/session and installation cache are server-memory only; key from secret mount; ignored env/key files | Shared/persistent session storage is not implemented; production encryption/backups require infrastructure configuration |
| Required automated testing | 54 unit/API tests and 27 Testcontainers integration tests pass in the current full backend run with zero skips; 11 frontend component/API tests and four Chrome journeys pass through Compose | See PHASE_STATUS for exact command and scope |
| Docker development environment | Dockerfiles, health checks, loopback Compose ports, volumes, dependencies and optional Redis profile; images build and PostgreSQL, RabbitMQ, backend and frontend become healthy; browser suite passes through Nginx | Redis is optional infrastructure, not an application cache; this is local verification, not deployment |
| GitHub Actions | Pinned actions for Java/Testcontainers, frontend lint/test/build/audit, Docker builds and Compose browser journey | YAML parsed locally; workflow has not run on GitHub |
| Documentation/demonstration/resume | README, architecture, API/OpenAPI, security, phase status, env template, demo/screenshots, App setup, deployment/recovery and resume guides | Local Markdown links checked; operator steps require live verification |
| Engineering process | Incremental tested changes, local mock default, cost guards, no remote push/deployment | All project source remains uncommitted in the initialized local repository |

## Current command evidence

- Backend: `mvn -B -ntp -f backend/pom.xml verify` passed on 2026-11-10 with Docker-backed Testcontainers and no skip variables. Surefire: 54 passed. Failsafe: 27 passed, zero skipped. The executable JAR was produced; `RabbitTransportIT` used real RabbitMQ.
- Dependency audit: `npm audit --json` returned zero vulnerabilities on 2026-10-30. This covers the npm tree, not Maven or base images. No npm version upgrade was required.
- Infrastructure: Docker Engine 29.5.2 / Compose v5.1.4 built both images. `docker compose up --build --wait --wait-timeout 180` passed using alternate loopback host ports because 5432 was occupied locally. PostgreSQL 17, RabbitMQ 4, backend and Nginx frontend were healthy; `/api/system` returned the demo configuration and all four Chrome journeys passed through the proxy. Containers and network were then removed while named volumes were preserved.
- Secrets: named GitHub/AI credential environment variables, `.env`, and `secrets/github-app.pem` are absent. Presence was checked without printing values. Live integration cannot be inferred from mocked tests.
- A subsequent browser run exposed demo fixtures aging out of the default 30-day window. The demo-only date refresh passed a targeted PostgreSQL regression after simulating 60 days of age. This fix was packaged after the full suite above; it does not alter live review history.
- Final Chrome rerun on that package: all three journeys pass, including Swagger UI rendering, authenticated publication schema presence and anonymous schema denial.
- Authorization review then found a stale discovery/revocation race. V9 and the access-revision fence now prevent an in-flight snapshot from restoring known revoked access. Separate-transaction tests for uninstall and user revocation pass. The subsequent full backend run covers this code: 54 unit/API tests and 27 Testcontainers integration tests passed with zero skips. Frontend build/lint/11 tests and all four browser journeys also pass.

## Outstanding acceptance gates

1. Configure a disposable GitHub App/repository using [GITHUB_SETUP.md](GITHUB_SETUP.md). Verify real OAuth, signed deliveries, commit deduplication, installation removal and [publishing](PUBLISHING.md).
2. Supply a provider key through a secret store and perform one explicitly cost-bounded live review using [AI.md](AI.md), including private-repository consent behavior.
3. If a remote repository is authorized later, push and execute the authored CI workflow; inspect job artifacts. No remote action is assumed authorized by this audit.

## Local review conclusion

The local source/document review covered the requirement areas above and fixed the concrete issues it found: scheduler starvation, demo fixtures aging out of the walkthrough window, and stale repository discovery overriding known revocations. Regression evidence is recorded in PHASE_STATUS.md. No additional known implementation defect remains from that review. This is not a claim of exhaustive bug-freedom or live production correctness; the four external gates above remain open.

The prior Docker engine issue recorded on 2026-10-30 is resolved in the current environment. The 2026-11-10 verification required no Docker reset or settings change. The first standard-port startup exposed only a local port-5432 collision; configurable loopback host ports permit coexistence with local services while preserving the normal defaults.

Deployment itself is not claimed. The project can be demonstrated locally without external credentials using the labeled demo; that does not satisfy the live integration gates above.
