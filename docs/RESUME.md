# Resume and interview notes

Use claims tied to implemented and tested behavior. No deployment, live GitHub/AI integration, production traffic, latency improvement or security guarantee has been verified.

## Suggested bullets

- Built CodeGuard AI, a Java 21/Spring Boot and React platform with PostgreSQL-backed pull request history, diff inspection, repository policies and usage analytics.
- Implemented signed GitHub webhook ingestion, transactional outbox delivery, fenced worker leases, bounded retries and dead-letter routing; verified database lifecycle and duplicate-delivery behavior with automated tests.
- Designed a configurable cloud AI adapter with strict JSON validation, private-code consent, changed-line mapping and durable per-review cost reservations, alongside a credential-free mock mode.
- Added permission-scoped GitHub review publishing with fresh commit/line checks, durable single-submission claims and read-only reconciliation for uncertain responses.
- Tested authentication boundaries, CSRF, repository isolation, provider failures and publishing safety; built responsive persistent dark/light dashboard journeys tested in Chrome and authored container/CI configuration.

## Demonstration narrative

Use the [demo walkthrough](DEMO.md). Explain that every dashboard metric comes from persisted, explicitly labeled fixtures. Show the diff-to-finding mapping and repository consent controls. Then explain the database outbox and why a timed-out publication is reconciled rather than blindly retried.

For architecture questions, discuss the modular monolith, short database transactions around network calls, at-least-once broker delivery, stale-worker fencing and the limits of external exactly-once effects. Explain that static heuristics and model confidence are advisory rather than proofs of correctness.

For testing questions, separate the Docker-backed PostgreSQL/RabbitMQ and Compose/browser evidence from deterministic mocked GitHub/AI calls and the still-unverified live-credential gates. Use current counts from PHASE_STATUS.md instead of copying a number that may become stale.

Do not claim the application has reviewed real organization repositories or saved engineering time until an authorized deployment produces that evidence.
