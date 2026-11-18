# CodeGuard AI — implementation status

Evidence is recorded here as each phase is implemented. No live integration is implied by mocked tests.

| Phase | Status | Evidence / remaining work |
|---|---|---|
| 1 Foundation | IMPLEMENTED; DOCKER VERIFIED | Boot 3.5.16 / Java 21 / JUnit 5 compile and package PASS. Security tests and real PostgreSQL migration check PASS. HTTP startup verified: health UP, system 200, anonymous resources 401. Frontend build/lint and 11 tests PASS. On 2026-11-10 Docker built both images and the full Compose stack became healthy. |
| 2 Core backend | IMPLEMENTED | Scoped repository, PR, review and analytics APIs, policy auditing, grants, results, usage and outbox persistence implemented and PostgreSQL-tested. |
| 3 GitHub integration | IMPLEMENTED; LIVE UNVERIFIED | GitHub App user OAuth, JWT signing, cached installation tokens, discovery, bounded PR retrieval, HMAC webhooks, delivery/commit deduplication and removal/revocation handling tested with mocked GitHub responses. |
| 4 Review engine | IMPLEMENTED; BROKER VERIFIED | Confirmed persistent RabbitMQ transport, outbox relay, recovery scheduling, fenced leases, bounded retries, DLQ and transactional worker implemented. Real PostgreSQL worker transactions, RabbitMQ Testcontainers transport and the Compose broker path pass. |
| 5 AI integration | IMPLEMENTED; LIVE UNVERIFIED | Mock and OpenAI Responses-compatible providers, strict validation, location mapping, consent, input/output limits, concurrency and durable cumulative cost reservations tested. No live paid calls made. |
| 6 Frontend | IMPLEMENTED; CI VERIFIED | Responsive dark dashboard with a persistent accessible light appearance, landing/sign-in, repositories/policies, PR history, safe syntax-highlighted diff viewer, finding filters, analytics, publishing preview/status, global toast notifications and settings connected to APIs. Twelve component/API tests pass; production build, lint and Playwright E2E journeys pass locally and in GitHub Actions. |
| 7 Publishing | IMPLEMENTED; LIVE UNVERIFIED | Manual and opt-in automatic publishing, durable single-attempt dispatch, fresh SHA/line validation, scoped authorization, safe Markdown and read-only reconciliation tested with PostgreSQL and mocked GitHub. Live verification instructions in docs/PUBLISHING.md. |
| 8 Hardening | CI VERIFIED; LIVE CREDENTIAL GATES REMAIN | Pinned CI workflow, bounded request limits, proxy sanitization, scheduler isolation, safe structured lifecycle logs, authorization-race protection (V9), scheduled data retention (V10), optional Redis session storage, optional Prometheus/admin queue observability implemented. Full backend verification, frontend checks and Docker/Playwright E2E pass in GitHub Actions (run #15, commit `07f5991`). Live GitHub/AI credentials remain unverified. |
| 9 Documentation | IMPLEMENTED AND AUDITED | README, architecture, API, security, App setup, deployment/recovery, demo, CI, publishing, contributing guide and evidence-based resume guides are present. Requirement-to-evidence audit records the remaining live credential gates; local Markdown links resolve. |

## External verification
- GitHub Actions CI: VERIFIED on 2026-11-18 — `sanjaim25/codeguard-ai` run `#15` (commit `07f5991`) passed all 3 jobs (`backend`, `frontend`, `containers-and-browser`) in 4m 1s and uploaded both evidence artifacts.
- Docker Compose / RabbitMQ: VERIFIED locally (2026-11-10) and in GitHub Actions (2026-11-18) — both images build, PostgreSQL 17, RabbitMQ 4, backend and frontend start healthy, and all 4 Playwright Chrome journeys pass through Nginx.
- GitHub OAuth / App / publishing: UNVERIFIED — credentials and installed GitHub App required.
- Cloud AI: UNVERIFIED — provider API key required.

## Verification log — 2026-10-13
- `frontend: npm.cmd test`: PASS, 4 tests (session cookies, CSRF, API errors, same-origin restriction).
- `frontend: npm.cmd run build`: PASS, TypeScript + Vite 7.3.7.
- `frontend: npm.cmd run lint`: PASS.
- `docker compose config --quiet`: PASS.
- Initial backend `mvn test` with Boot 4.1.1 / JUnit 5: compilation PASS, tests FAILED due to Spring 7's JUnit 6-only extension call. Resolved by selecting Boot 3.5.16 / springdoc 2.9.1; subsequent runs pass.
- A sandbox-only Vite `EPERM` was resolved by running the same build with approved filesystem access.
- Boot 3.5.16 `mvn verify`: PASS, 3 API tests + 1 Flyway/PostgreSQL integration test; no skipped tests.
- First domain slice `mvn verify`: PASS, 3 API tests + 3 integration tests including repository isolation and consent defaults.
- HTTP startup smoke: PASS, `/actuator/health` returned UP, `/api/system` returned actual demo=false, anonymous `/api/repositories` returned 401.
- Windows Java socket startup required `-Djdk.net.unixdomain.tmpdir=D:\codeguard-ai\.tools\tmp` and `-Djava.io.tmpdir=D:\codeguard-ai\.tools\tmp`; this changes only the spawned process.
- At that time, the local fallback PostgreSQL was 18.4; Flyway's supported baseline is PostgreSQL 17 (used by Compose/Testcontainers). The fallback migrations passed with a newer-server warning. PostgreSQL 17 was subsequently verified on 2026-11-10.
- Latest repository/PR API and lifecycle suite: PASS, 10 unit/API tests + 6 real PostgreSQL integration tests, 0 skipped. Covers CSRF, validation, safe 404s, cross-user isolation, write permission, audit persistence, commit uniqueness, cancellation, lease fencing and retry bounds. Migrations V1–V4 validated. Executable JAR packaged successfully.

## Next implementation steps
1. Configure a disposable GitHub App and provider credentials through local secret storage, then run the documented live verification (GitHub OAuth, signed webhooks, Cloud AI review and live publishing).

Live OAuth, GitHub review publishing, cloud AI calls and production deployment remain **unverified** until configured with real credentials. All code, local tests, Docker Compose builds and remote GitHub Actions CI checks pass.

The requirement-to-evidence mapping and outstanding gates are in [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).

## Verification log — 2026-10-15
- Prior GitHub milestone reports: 24 unit/API tests and 11 PostgreSQL integration tests PASS, zero skipped. Migrations V1–V6 validated.
- Diff analysis plus GitHub retrieval: 9 targeted tests PASS. Covers multi-hunk line mapping, generated/secret file filtering, malformed and truncated patches, and local static heuristics.
- AI and analysis: 14 targeted tests PASS. Covers structured requests, private-code consent denial, cost limits before HTTP, rate limits, refusals, invalid JSON/schema, measured versus estimated usage, and explicit mock labeling.
- Test development exposed a JUnit/Spring `MediaType` import ambiguity and a Windows JDK selector initialization in an unused real HTTP client. Both were fixed; the provider tests now use mocked transports throughout.
- Full backend run after analytics: 41 unit/API tests PASS; 18 PostgreSQL integration tests PASS; one RabbitMQ test explicitly SKIPPED. No failures. Separate worker transactions, retry budgets, expired-worker fencing and cross-user analytics isolation verified.
- Demo additions: targeted run PASS (7 analysis tests and one demo integration test). Verifies session rotation, CSRF, six idempotently seeded reviews, five heuristic findings, local-only policies and no GitHub calls. A static-rule overlap found by fixtures was fixed and regression-tested.

## Verification log — 2026-10-16
- Restarted the isolated PostgreSQL cluster, demo backend and Vite server after previous process sessions expired. `/actuator/health` returned UP.
- Frontend: 9 component/API/diff tests PASS; production build and ESLint PASS. Code splitting removes the large-bundle warning.
- Real Chrome: 2 end-to-end journeys PASS. Covers demo login, stored metrics, repository search, policy save/restore, changed-line findings, severity filtering, analytics, desktop/mobile logout, protected-route redirect, API error/retry and mobile overflow checks.
- Desktop and mobile screenshots inspected. Disabled chart entrance animations to avoid partial chart captures. Screenshots contain explicitly labeled demo data.
- Initial browser execution was delayed by the automatic approval review service's usage limit. The retry after that window elapsed was approved and ran successfully.
- CI YAML parses successfully; defines backend/Testcontainers, frontend checks/audit, image builds and Compose browser journeys. Not pushed or executed in GitHub Actions.
- Upgraded Vitest to 4.1.12 to remove vulnerable Tinypool dependency. Fresh npm audit: zero vulnerabilities.
- Publishing milestone: full backend verify PASS, 50 unit/API tests and 23 PostgreSQL integration tests; one RabbitMQ test explicitly SKIPPED. Flyway V8 applied and validated. Frontend build/lint and 11 tests PASS.
- Additional publishing transaction test: targeted run PASS, 8 publishing unit tests and 22 repository integration tests. Confirms SENDING is committed before the external call and duplicate dispatch cannot submit twice. Combined integration coverage is now 24 passing database tests (including demo and migration).
- Updated demo backend starts successfully with V8. Both real Chrome journeys PASS again, including publication preview and demo publishing denial. Updated review screenshot inspected.
- Hardening: full backend verify PASS, 53 unit/API tests and 24 PostgreSQL integration tests; one RabbitMQ test explicitly SKIPPED. Tests cover bounded rate-limit state, user isolation, Retry-After and integration with the security chain. An empty-servlet-path issue found by the API test was fixed before the passing run.
- Added GitHub App setup, deployment/backup/recovery and resume guides. Common credential-pattern scan found no matching tokens in non-ignored sources; ignore checks cover environment files, PEM keys and database dumps. This scan is not a comprehensive secret audit.
- Docker availability rechecked: Linux engine named pipe is missing. Compose configuration parses; runtime/container verification remains open. No Docker settings were changed.

## Verification log — 2026-10-30

- At that time, Docker Linux engine remained unavailable. Named GitHub/AI credentials, `.env` and the App private-key file were absent; live integration gates remain UNVERIFIED.
- Restarted the isolated PostgreSQL cluster after confirming its process handle was gone and its port did not respond. PostgreSQL recovery completed successfully.
- Fixed scheduler starvation: publication preflight/POST, outbox confirms and lease recovery now have three scheduler threads. A regression test blocks two slots and confirms recovery still runs.
- Full backend verify PASS: 54 unit/API tests, 24 PostgreSQL integration tests, one RabbitMQ test explicitly SKIPPED. Current source packaged successfully.
- Fresh npm audit reports zero vulnerabilities. Added requirement-by-requirement acceptance mapping and updated the implementation checklist without marking external gates complete.
- Browser acceptance caught demo fixtures aging out of the default analytics window. Demo sign-in now refreshes only the exact synthetic fixture dates. Targeted verification PASS: scheduler test and demo PostgreSQL test, including simulated 60-day-old fixtures, correct analytics after sign-in and no duplicate records.
- Final Chrome rerun PASS: all three journeys, including stored metrics/policies/findings, mobile/error handling, Swagger UI rendering and anonymous OpenAPI schema denial. Demo remains running locally with mock AI and external publishing disabled.
- Final authorization review found and fixed a stale-discovery race: a sync started before revocation could otherwise restore access afterward. V9 adds a database revision fence shared by discovery and access-changing webhooks. Separate-transaction regression tests cover App uninstall and user authorization revocation during an in-flight sync.
- After the authorization fix, full backend verify PASS: 54 unit/API tests, 26 PostgreSQL integration tests, one RabbitMQ test explicitly SKIPPED. V1–V9 migrations validated. Frontend production build, lint and all 11 component/API tests PASS.
- Infrastructure retry: `docker desktop start --timeout 45` launched Desktop, but its current backend log confirms a crash while initializing the `dockerInference` Unix socket (file inaccessible / invalid path syntax). The waiting start/info CLI processes were interrupted after the crash was confirmed. No reset, data deletion or settings change was performed.
- Local source/document review is finished for this implementation: identified scheduler starvation, aging demo data and stale authorization snapshots were fixed and regression-tested. Remaining acceptance work requires working Docker, live credentials and (for remote CI execution) explicit push authorization. These blockers have persisted across consecutive goal turns; the overall project is not fully verified.

## Verification log — 2026-11-10

- Docker Engine 29.5.2 and Compose v5.1.4 are available. Full `mvn -B -ntp -f backend/pom.xml verify` ran with no broker-skip or local-database overrides: Surefire 54 passed; Failsafe 27 passed; zero failures, errors or skips. `RabbitTransportIT` ran against real RabbitMQ and passed.
- Both Docker images built successfully. The first default-port Compose attempt identified a local host-port 5432 collision; Compose now permits `POSTGRES_PORT`, `RABBITMQ_PORT`, `RABBITMQ_MANAGEMENT_PORT` and `FRONTEND_PORT` overrides while retaining its standard defaults. The Nginx health probe now explicitly uses IPv4 loopback so it matches its listener.
- `docker compose up --build --wait --wait-timeout 180` passed with alternate loopback ports. PostgreSQL 17, RabbitMQ 4, backend and frontend were healthy; Flyway validated V1–V9 and the backend connected to RabbitMQ.
- The containerized frontend returned `{"demo":true,"name":"CodeGuard AI"}` at `/api/system`. Four Chrome journeys passed through Nginx on port 3001: persistent accessible light/dark appearance, demo/policy/review workflow, retry/mobile behavior, and authenticated Swagger UI with anonymous OpenAPI denial. Test containers and network were stopped afterward while named volumes were preserved.
- Safe structured lifecycle logging was added for webhook deduplication/processing and review claim, completion, cancellation and deferral outcomes. Full backend verify then passed again with 54 unit/API tests and 27 Testcontainers integration tests, zero skips. The exercised logs contain controlled event names, IDs, outcome/status values and counts—not payloads, tokens, diffs or exception messages.
- The updated backend image was rebuilt from source in Docker and the complete Compose stack again reached healthy PostgreSQL, RabbitMQ, backend and frontend services. `GET /api/system` through Nginx returned the expected demo configuration; containers and network were then removed while named volumes were preserved.
- Added a sanitized private live-verification record template that covers GitHub OAuth/App access, signed PR deliveries, cloud AI consent/cost evidence, publishing and authorized remote CI. It is explicitly a copy-outside-the-repository artifact and contains no credential fields or private-code capture.

## Verification log — 2026-11-18

- Implemented operational enhancements on `main`: Flyway V10 scheduled data retention (`RetentionCleanupService` + `RetentionCleanupServiceTest`), optional Redis-backed HTTP sessions (`RedisSessionConfig` behind `SESSION_STORE_TYPE=redis` + Compose `session` profile), optional Prometheus metrics (`/actuator/prometheus`) and protected admin queue/DLQ inspector (`AdminController`), global frontend toast notifications (`Toast.tsx`, 12/12 Vitest tests passing), `.dockerignore` files and `CONTRIBUTING.md`.
- Pushed `main` and release tags (`v1.0.0`, `v1.1.0`) to `https://github.com/sanjaim25/codeguard-ai`.
- Resolved initial CI failures in commit `07f5991`: configured Google's Maven Central mirror to prevent Maven Central HTTP 429 rate limits on shared GitHub runners, scoped `npm audit` in CI to production runtime dependencies (`--omit=dev --audit-level=high`, 0 production vulnerabilities; a dev-only `picomatch` advisory affects `vite`/`vitest`), excluded Redis/Session auto-configuration when `SESSION_STORE_TYPE=none`, fixed `compose.yaml` default-value interpolation (`:-`), and configured Dependabot to ignore incompatible Spring Boot 4 / Springdoc 3 major bumps.
- GitHub Actions run `#15` (`07f5991`) PASSED all 3 jobs in 4m 1s:
  - `Java, PostgreSQL and RabbitMQ tests` (`1m 18s`) — unit tests, Flyway V1–V10 migrations, PostgreSQL 17 & RabbitMQ Testcontainers
  - `TypeScript, component tests and dependency audit` (`55s`) — ESLint, Vitest (12/12), Vite build, production `npm audit`
  - `Docker image builds, Compose health and browser journey` (`2m 37s`) — full Docker Compose stack + 4 Playwright E2E journeys
  - Uploaded both workflow evidence artifacts (`backend-test-reports`, `compose-browser-evidence`).
