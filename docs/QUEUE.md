# Durable review processing

Verified webhooks persist a job and outbox entry in one database transaction. A scheduled relay sends only the job UUID to the durable `codeguard.reviews.work` queue. Messages are persistent. The relay records delivery only after a publisher confirmation with no unroutable return; an ambiguous outcome is retried. The database job lock serializes relay updates with consumers, so duplicate delivery cannot create a second attempt while one is active.

A worker claims an eligible job in a short transaction and receives a unique claim token with a ten-minute lease. GitHub retrieval and AI HTTP calls happen outside database transactions. Before a provider call, a new transaction checks current repository policy and durably reserves the attempt's estimated cost. Completion requires the current claim token, an unexpired lease, an active repository and the same open PR commit.

Retryable failures rearm the outbox with bounded exponential backoff (5 seconds to 5 minutes). Provider rate-limit delays can extend this to one hour. Review execution defaults to at most three attempts. Failed terminal jobs produce a durable dead-letter outbox record; `codeguard.reviews.dead.work` has no automatic consumer. Malformed messages also route there. Inspect the job's safe `failureCode` and broker queue before taking operator action; do not blindly replay the DLQ.

The recovery task fences expired processing claims and retries them subject to the attempt limit. Queued jobs without progress for ten minutes are republished, covering a consumer failure before it acquired a database claim. Duplicate messages for completed, cancelled, processing or not-yet-due jobs are harmless. Unknown job IDs are ignored.

`QUEUE_ENABLED=false` disables the relay, consumer and recovery tasks. Jobs can still be recorded by webhooks but remain pending. `REVIEW_CONCURRENCY` controls consumer concurrency (default 2), and prefetch is 1. `AI_CONCURRENCY` separately caps simultaneous cloud calls per process.

PostgreSQL worker and retry tests, mocked broker-confirmation tests, and the real `RabbitTransportIT` Testcontainers check pass. Local Compose also started RabbitMQ with the complete stack on 2026-11-10. See [verification instructions](VERIFICATION.md). No external deployment has been performed.

## Scheduler isolation

Three scheduler threads allow publication network calls, outbox broker confirms and expired-lease recovery to run independently. A stalled publication cannot consume the only scheduler thread. Each fixed-delay task remains serial; this is not an unbounded worker pool. A regression test blocks two network-task slots and verifies recovery can still execute in the third.
