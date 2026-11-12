# Verification guide

## Ordinary setup

Install Java 21, Maven 3.9+ and Node 22.12+. Start Docker Desktop's Linux engine, then run:

```powershell
docker compose config --quiet
docker compose up -d postgres rabbitmq
cd backend
mvn test
mvn verify
cd ../frontend
npm.cmd ci
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

Use `npm` on Linux/macOS. `mvn test` runs unit and API slice tests. `mvn verify` also runs `*IT` integration tests; a working Docker daemon is required by default. Never count skipped or unexecuted integration tests as successful.

## PostgreSQL fallback when Docker is unavailable

Database integration tests can use a **dedicated disposable test database**, never a production database. They apply all migrations. Most checks roll back; the worker transaction test commits its own fixtures and removes those exact fixtures afterward.

PowerShell:

```powershell
$env:CODEGUARD_TEST_DB_URL='jdbc:postgresql://localhost:55432/codeguard_test'
$env:CODEGUARD_TEST_DB_USER='codeguard'
$env:CODEGUARD_TEST_DB_PASSWORD='<your local test database password>'
$env:CODEGUARD_SKIP_BROKER_TESTS='true' # Explicitly records RabbitTransportIT as skipped, not passed.
cd backend
mvn verify
```

Linux/macOS:

```sh
export CODEGUARD_TEST_DB_URL='jdbc:postgresql://localhost:55432/codeguard_test'
export CODEGUARD_TEST_DB_USER='codeguard'
export CODEGUARD_TEST_DB_PASSWORD='<your local test database password>'
export CODEGUARD_SKIP_BROKER_TESTS='true' # Explicit infrastructure exclusion, not a pass.
cd backend
mvn verify
```

This fallback does **not** verify RabbitMQ, Testcontainers container startup or Docker Compose. Those checks must still run with Docker before claiming complete infrastructure verification.

For application startup without a broker while developing the foundation:

```powershell
$env:DATABASE_URL='jdbc:postgresql://localhost:55432/codeguard'
$env:DATABASE_USER='codeguard'
$env:DATABASE_PASSWORD='<your local development database password>'
$env:QUEUE_ENABLED='false'
$env:COOKIE_SECURE='false'
cd backend
mvn spring-boot:run
```

On Linux/macOS set those variables with `export`. Disabling the queue means asynchronous review processing is unavailable; use the normal Compose setup for the complete pipeline.

If Java on Windows fails with `Unable to establish loopback connection`, create a short, writable directory and pass `-Djdk.net.unixdomain.tmpdir=<directory>` and `-Djava.io.tmpdir=<directory>` to the JVM. This project verified startup using `.tools/tmp` under the workspace. Stop a running `java -jar` process before rebuilding the same JAR on Windows, where it holds a file lock.

## Credential checks

Live GitHub OAuth, GitHub App installation and signed PR webhook verification require a configured GitHub App and a disposable test repository; follow [GitHub setup](GITHUB_SETUP.md). Cloud AI verification requires a provider key and explicit private-code opt-in when applicable; see [AI configuration](AI.md). Publishing is implemented and locally tested; follow its [live checklist](PUBLISHING.md). Live status is UNVERIFIED, not passed.

With Docker available, remove `CODEGUARD_SKIP_BROKER_TESTS` (`Remove-Item Env:CODEGUARD_SKIP_BROKER_TESTS` in PowerShell; `unset CODEGUARD_SKIP_BROKER_TESTS` in sh), then run `mvn verify`. `RabbitTransportIT` starts a real RabbitMQ container and checks confirmed delivery to the listener plus malformed-message dead lettering.

## Current machine

2026-11-10: Docker Engine 29.5.2 and Compose v5.1.4 were verified. A full backend `mvn verify` completed with 54 unit/API and 27 Testcontainers integration tests, zero skipped; this includes real PostgreSQL 17 and RabbitMQ `RabbitTransportIT`. Both application images built, and the full Compose stack became healthy. Four Chrome journeys then passed through the Nginx proxy, including persistence of the accessible light/dark appearance.

If a default loopback port is occupied locally, keep the container ports unchanged and override only the host side:

```powershell
$env:POSTGRES_PORT='15432'
$env:RABBITMQ_PORT='15672'
$env:RABBITMQ_MANAGEMENT_PORT='15673'
$env:FRONTEND_PORT='3001'
docker compose up --build --wait --wait-timeout 180
```

The standard defaults remain 5432, 5672, 15672 and 3000. A separate PostgreSQL 18.4 cluster may exist under ignored `.tools/postgres/data`, listening only on `127.0.0.1:55432`; it is developer verification infrastructure, not part of production deployment.
