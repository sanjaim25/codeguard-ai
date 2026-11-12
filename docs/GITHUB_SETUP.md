# Configure and verify a GitHub App

**Live verification is pending.** You need an account allowed to register/install a GitHub App, a disposable repository, and a public HTTPS origin that you control. Do not paste credentials into chat or commit them.

## Registration

Register a GitHub App (not an OAuth App) under your account or organization settings. GitHub documents the [registration fields](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/registering-a-github-app) and [authorization callback](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/about-the-user-authorization-callback-url). Use the following application-specific values, replacing `https://codeguard.example.com` with your actual origin:

| Field | Value |
|---|---|
| Homepage | `https://codeguard.example.com` |
| Callback URL | `https://codeguard.example.com/login/oauth2/code/github` |
| Webhook URL | `https://codeguard.example.com/api/webhooks/github` |
| Webhook active / SSL verification | Enabled |
| Webhook secret | A generated high-entropy secret, also set on the backend |
| Repository permissions | Metadata: read; Contents: read; Pull requests: write for publishing |
| Subscription | Pull request events; installation lifecycle events are handled by the application |

Keep user-token expiration enabled. The application prompts users to sign in again when authorization expires; it does not implement transparent user-token refresh. App installation tokens are separately minted and refreshed by the server. User tokens determine which repositories the dashboard user may access; installation tokens perform analysis and publish as the App bot.

Generate an App private key and client secret. Record the App ID, client ID and App slug. The signer requires an unencrypted PKCS8 RSA PEM (`BEGIN PRIVATE KEY`). If GitHub supplies a PKCS1 RSA key, convert it locally with OpenSSL:

```sh
openssl pkcs8 -topk8 -nocrypt -in github-downloaded-key.pem -out secrets/github-app.pem
```

The command is the same in PowerShell when OpenSSL is installed. Create the directory first (`New-Item -ItemType Directory -Force secrets` or `mkdir -p secrets`). Restrict the key file to the deployment service account; never commit either file. OpenSSL is not bundled by this project.

## Backend configuration

Start with a fresh database for live use; never enable live integration against the seeded demo database. Set these through your local ignored `.env` or deployment secret manager:

```dotenv
DEMO_MODE=false
COOKIE_SECURE=true
GITHUB_AUTH_ENABLED=true
GITHUB_APP_ID=<numeric App ID>
GITHUB_APP_SLUG=<registered slug>
GITHUB_CLIENT_ID=<App client ID>
GITHUB_CLIENT_SECRET=<secret>
GITHUB_WEBHOOK_SECRET=<same webhook secret>
GITHUB_PRIVATE_KEY_PATH=/run/secrets/github-app.pem
AI_PROVIDER=mock
QUEUE_ENABLED=true
```

Compose mounts `./secrets` read-only at `/run/secrets`; a direct JVM uses an absolute local key path instead. Keep the application behind your trusted HTTPS ingress and preserve the external origin in forwarding headers. Do not expose the demo through a tunnel. Local HTTP OAuth testing requires a matching localhost callback and `COOKIE_SECURE=false`; webhook delivery still needs an explicitly configured public receiver. No tunnel or external deployment is created by the project.

## Verification sequence

1. Install the App on the disposable repository. Start PostgreSQL, RabbitMQ, backend and frontend. Verify `/actuator/health` internally.
2. Choose GitHub sign-in. Confirm the callback returns to `/app`, the profile is correct, and repository synchronization lists only repositories you can access. Test a second user who lacks access; the repository must not be visible.
3. Open a PR with a small source change. In the App's recent webhook deliveries, confirm a `202` response. The dashboard should show a job progressing to `COMPLETED`, stored findings, and explicitly labeled mock/static analysis.
4. Redeliver the same webhook. Confirm no second job for the same PR commit. Push a new commit and confirm a new job; stale unfinished work is cancelled.
5. Remove repository access or uninstall the App. Confirm subsequent repository access is denied and pending work cannot process. Existing grants also expire unless refreshed.
6. Follow the [publishing checklist](PUBLISHING.md) for manual and automatic comments. Follow [AI configuration](AI.md) for a separately authorized, cost-bounded live AI call.

Capture actual delivery IDs, commit SHAs, review job IDs and observed outcomes in a private verification record. Start from the [sanitized record template](LIVE_VERIFICATION_RECORD.md), copy it outside the repository, and exclude tokens, key material and private diffs. Until these checks run, OAuth/App delivery/publishing stay UNVERIFIED.
