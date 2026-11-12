# CodeGuard AI

AI-assisted GitHub pull request reviews, built with Java 21, Spring Boot, PostgreSQL, RabbitMQ and React. **Under active construction.** See [PHASE_STATUS.md](PHASE_STATUS.md) for verified features and remaining work. AI findings are advisory and never guarantee security or correctness.

The backend includes GitHub App OAuth and webhooks, a durable review worker, diff analysis, mock/cloud AI providers, scoped analytics and manual/opt-in GitHub publishing. The responsive dashboard and local demo are implemented and browser-tested. Local hardening, Docker/Compose and documentation are verified; CI is authored but has not run remotely. Live integrations still require credentials. See the [acceptance audit](docs/ACCEPTANCE.md) for the exact evidence and blockers.

See [publishing behavior and verification](docs/PUBLISHING.md) and [CI checks](docs/CI.md).

Additional guides: [GitHub App setup](docs/GITHUB_SETUP.md), [deployment and recovery](docs/DEPLOYMENT.md), the private [live-verification record template](docs/LIVE_VERIFICATION_RECORD.md), and [evidence-based resume bullets](docs/RESUME.md).

## Local development

Requirements: Docker Desktop with the Linux engine running (Windows: WSL2 backend), or Java 21 + Maven 3.9+, Node.js 22.12+ and PostgreSQL / RabbitMQ.

PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose logs -f backend
```

Linux/macOS:

```sh
cp .env.example .env
docker compose up --build -d
docker compose logs -f backend
```

The frontend is served at http://localhost:3000. Compose is intentionally bound to localhost; defaults are for local development only. Choose **Explore the local demo** on the sign-in page. No GitHub or AI credentials are required. Local Docker image build, Compose health checks and browser journeys were verified on 2026-11-10; see [verification instructions](docs/VERIFICATION.md) for commands and the fallback option.

See the [demo walkthrough](docs/DEMO.md) and [dashboard screenshot](docs/screenshots/dashboard.png).

Run source builds (PowerShell uses `npm.cmd` if script execution is restricted):

```powershell
cd backend
mvn verify
cd ../frontend
npm.cmd ci
npm.cmd run build
npm.cmd run lint
npm.cmd test
```

On Linux/macOS use `npm` instead of `npm.cmd`. Integration tests require a working Docker daemon; unit tests use `mvn test`. Never mark a skipped integration suite as verified.

## Structure

`backend/` contains the Java modular monolith and Flyway migrations. `frontend/` contains the React TypeScript client. `compose.yaml` defines local infrastructure and application containers. [ARCHITECTURE.md](ARCHITECTURE.md) describes the module boundaries and durability model.

See [API documentation](docs/API.md), [verification instructions](docs/VERIFICATION.md), [security model](SECURITY.md), and the [phase-by-phase acceptance plan](IMPLEMENTATION_PLAN.md). Spring Boot 3.5.16 is the selected stable line compatible with the required JUnit 5 stack; Java 21 is required.

## Configuration and security

Copy `.env.example` to `.env`, which is ignored by Git. Keep App private keys under `secrets/`; PEM and KEY files are ignored. Use secret mounts and HTTPS in production. Do not enable demo mode on a public host. GitHub OAuth, cloud AI, and live review publishing remain unverified until configured and tested with real credentials.
