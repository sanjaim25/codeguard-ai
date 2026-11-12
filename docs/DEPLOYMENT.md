# Deployment and operations

No deployment has been performed. Local Docker image build, Compose startup and browser smoke tests passed on 2026-11-10; the authored CI workflow has not run remotely. This guide describes the supported design and release gates, not evidence of a production deployment.

## Topology

Start with one Java application instance, a same-origin frontend/reverse proxy, PostgreSQL and RabbitMQ on private networking. Terminate HTTPS at a controlled ingress. Redis is an optional Compose infrastructure profile and is **not integrated into application sessions, caches or rate limits**.

Possible hosting arrangements are a VM running containers, or a container platform with managed PostgreSQL and a compatible RabbitMQ service. Choose services that support durable storage and backup restoration; no provider-specific infrastructure is provisioned here. Do not deploy the developer Compose defaults directly to a public host.

The backend currently keeps sessions, OAuth user credentials and installation token caches in process memory. A restart signs users out. Use one application instance initially. Horizontal scaling requires sticky sessions or an explicitly implemented shared session store, shared ingress rate limits, and coordination of cloud AI concurrency (the current semaphore is per instance). Database job leases and publication claims already coordinate worker ownership, but do not make sessions or all resource budgets cluster-wide.

## Release gate

1. Run `mvn verify` with Docker available and no broker-skip variable. Run frontend lint, tests, build and audit. Run the Compose browser journey from [CI](CI.md).
2. Use a new non-demo database. Set `DEMO_MODE=false`, `COOKIE_SECURE=true`, strong independent database/broker passwords, GitHub credentials and a read-only App key mount. Use `AI_PROVIDER=mock` until live consent and cost checks are complete.
3. Keep backend, database, broker and management ports private. The public ingress should expose only the frontend, required API paths and OAuth callbacks; protect operational endpoints. Enforce a two-MiB request body limit and ingress timeouts/rate limits.
4. Terminate TLS at the proxy and supply the correct external `Host` and `X-Forwarded-Proto`. If placing another TLS proxy in front of the bundled nginx, adapt its forwarding configuration: the shipped local nginx uses its own `$scheme`, which is HTTP. Only trust incoming forwarding headers from the controlled TLS proxy. Never expose a backend that trusts arbitrary client forwarding headers.
5. Back up PostgreSQL and broker storage before upgrades. Flyway runs migrations at application startup; Hibernate only validates. Use one controlled migration/startup step. Rollback requires an application/schema-compatible version or a verified backup restore, not automatic down-migrations.
6. Complete the [live GitHub checks](GITHUB_SETUP.md), [publishing checks](PUBLISHING.md), and a bounded [cloud AI verification](AI.md). Record versions and results before calling the release verified.

## Backup and recovery

Use encrypted scheduled database backups and test restoration into an isolated environment. Example local Compose backup commands below work in PowerShell and POSIX shells and avoid corrupting binary dumps through shell redirection:

```sh
docker compose exec -T postgres pg_dump -U codeguard -d codeguard -Fc -f /tmp/codeguard.dump
docker compose cp postgres:/tmp/codeguard.dump ./codeguard.dump
```

The dump contains private repository data; keep it outside version control in encrypted storage and remove the temporary container copy after securely transferring it. Restore to a **separate disposable database** with `pg_restore`, verify Flyway/schema compatibility, counts and authorized access, and only then plan a recovery cutover. Do not overwrite the running database during a restore drill. Managed services should use their native backup/point-in-time recovery facilities.

Database outbox records recover unsent analysis work after restart. Expired processing claims are fenced and requeued within retry limits. RabbitMQ dead letters remain in `codeguard.reviews.dead.work` for investigation. Do not blindly republish dead letters: first fix the cause and inspect the job status. A duplicate analysis delivery cannot bypass its claim, but an exhausted failed job is not automatically reset.

Publication `UNKNOWN` states need read-only reconciliation. Never delete publication records or reset them to pending to force another POST. The unique row is part of the duplicate-notification protection.

## Monitoring and incidents

`/actuator/health` reports database/broker health without details; disable broker health only in explicit queue-disabled local mode. The dashboard reports scoped processing failures and usage. Structured application log events include safe lifecycle event names, IDs, outcomes, retry metadata and counts—not provider payloads, exception messages or repository content. Forward logs to your chosen collector with access controls and retention; no hosted monitoring is configured.

Alert on unhealthy instances, growing unsent outbox/queued-job age, dead-letter count, expired leases, failed AI requests, publication unknown states and usage/cost growth. Request limits return `429` with `Retry-After`; investigate sustained throttling instead of disabling limits. Provider prices are operator-entered estimates and must be reconciled with actual billing.

For suspected credential exposure, disable the affected integration, rotate credentials at the provider, replace mounted/environment secrets and restart the application. Revoke OAuth authorization as needed. For a repository incident, disable its review/publishing policies and remove App access. An already-started external call may finish; consent changes cannot recall data already transmitted.

Review results include private diffs and findings. The application has no automated retention/purge scheduler or self-service account deletion yet. Define retention before a public launch and implement an audited, dependency-aware purge procedure; database backups must follow the same retention policy. Do not represent this local project as compliance-certified or production-operated.


## Shared session storage (horizontal scaling)

By default (`SESSION_STORE_TYPE=none`) sessions are stored in server memory and are lost on restart. To enable Redis-backed sessions for multi-instance deployments:

1. Start Redis: `docker compose --profile session up -d redis`
2. Set environment variables:
   ```
   SESSION_STORE_TYPE=redis
   REDIS_HOST=<your-redis-host>
   REDIS_PORT=6379
   REDIS_PASSWORD=<your-redis-password>
   ```
3. Restart the backend. Sessions now survive restarts and are shared across replicas.

> **Security**: Always use Redis AUTH (`REDIS_PASSWORD`) and TLS in production. Never expose Redis without network-level access controls and authentication.
