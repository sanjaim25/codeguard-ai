# Continuous integration

`.github/workflows/ci.yml` runs on pull requests, pushes to main/master and manual dispatch. It uses read-only repository permissions, no application credentials, and official actions pinned to commit hashes. Dependabot checks Java, npm, Actions and Dockerfile dependencies weekly.

The backend job runs Java 21 `mvn verify`, including real PostgreSQL and RabbitMQ Testcontainers checks. It does not set the local broker-skip environment variable. Test reports are retained for seven days.

The frontend job installs the lockfile, runs ESLint, component/API tests, the production TypeScript/Vite build and `npm audit --audit-level=high`. An audit failure must be investigated rather than bypassed with a blanket ignore. Dependency advisories change over time; a passing audit is not proof of security.

After both jobs pass, the container job builds both application images and starts PostgreSQL, RabbitMQ, the backend and Nginx frontend through Compose. It waits for health checks and runs the real browser demo journey against port 3000. The job uses only explicit sample fixtures and mock AI. Logs and failure traces are retained; disposable volumes are removed afterward. No image is pushed and nothing is deployed externally.

The workflow has been authored but has **not run on GitHub** because this workspace has not been pushed. Local source/build/test evidence, including Linux-container image builds and Compose startup, is recorded in `PHASE_STATUS.md`; it does not establish that the GitHub-hosted workflow itself will pass. Inspect the first GitHub run before treating CI execution as verified.

Enable Dependabot alerts and dependency graph in repository settings where available. Review Java transitive dependency advisories as well as npm advisories; the npm audit gate does not cover Maven dependencies. Keep branch protection configured to require all three jobs before merging.
