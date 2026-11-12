# Security model

This application is under construction; consult PHASE_STATUS.md before using it. Code review findings are advisory, not a guarantee of security or correctness.

## Trust boundaries

- Authenticate dashboard users with GitHub OAuth; authenticate server GitHub API calls separately with short-lived GitHub App installation tokens.
- Keep browser sessions HttpOnly, SameSite=Lax, Secure in production. Preserve CSRF protection for every browser mutation. Only the signed webhook route may bypass CSRF.
- Verify webhook HMAC-SHA256 over original request bytes before parsing or persisting events. Enforce a body limit and accepted events. Deduplicate delivery IDs and commits.
- Scope every repository, pull request, finding and analytics query by the current user's repository grant; writing settings and publishing require elevated repository permission.
- Fence repository-discovery snapshots against access changes committed while GitHub requests are in flight. A database revision and shared transaction lock prevent stale synchronization from undoing a known uninstall, repository removal or user-authorization revocation. A conflicting sync fails safely and can be retried with a fresh snapshot; grants still have a bounded cache lifetime when no revocation event is available.
- Treat GitHub filenames, code, PR descriptions and AI responses as untrusted. Never execute submitted code, shell commands or model tool calls. React text escaping is required; never render raw HTML.
- External AI processing of private repositories requires explicit repository policy consent. Apply file exclusions, diff limits, output limits, concurrency bounds and a configured maximum estimated call cost.
- AI output must use allowed enum values, bounded text and confidence. Only map inline comments to real changed lines in submitted context.
- Restrict GitHub App permissions to metadata read, contents read, pull requests read/write (write required for publishing). Subscribe only to necessary installation/repository and pull request events.

## Operations

Never commit `.env`, private keys, access tokens or user code. Keep secrets in environment variables or mounted secret files. Do not log request bodies, provider responses, authorization headers or repository diffs. Webhook and review lifecycle logs use controlled `event=...` keys with IDs, outcomes, retry metadata and counts only; they never interpolate external payload fields or exception messages. Use private infrastructure networking, individual service passwords, TLS, encrypted storage/backups and least-privilege database access.

Local Compose defaults bind the dashboard and infrastructure ports to loopback. Demo mode must never be exposed publicly. Set `DEMO_MODE=false` and `COOKIE_SECURE=true` before production. Proxy forwarding headers are trusted only when the API is reachable exclusively through your controlled proxy. Use ingress request limits and a per-IP limiter for Internet-facing traffic.

RabbitMQ is at-least-once delivery. Database constraints and conditional job claims make repeated deliveries safe. Ambiguous GitHub publish responses can only be reconciled; there is no automatic re-POST.

The backend enforces per-process, per-minute request limits: 1,200 general requests per authenticated user, 300 per anonymous peer, 12 repository synchronizations and 12 publication mutations per user, and 30 demo logins per peer. Limits return safe `429` responses with `Retry-After`. The in-memory map holds at most 10,000 active identity/scope windows and rejects new entries when full. Fixed windows allow boundary bursts; shared ingress controls are required across replicas. Health probes are exempt. OAuth redirect handling occurs earlier in the security chain and requires ingress protection.

The local nginx overwrites forwarding headers rather than accepting client-supplied addresses. Configure a trusted real-client address at a production ingress; shared proxy/NAT addresses may otherwise share anonymous limits. Session credentials are kept in server memory, not persisted in the database. Review diffs and findings are persisted and need encrypted storage, backup protection and an operator-defined retention policy. See [deployment limitations](docs/DEPLOYMENT.md).

## Reporting

Do not place secrets or private repository content in a public issue. Contact the deployment owner privately with reproduction steps and affected versions. Rotate exposed credentials immediately. This repository does not yet have an external security contact or hosted deployment.
