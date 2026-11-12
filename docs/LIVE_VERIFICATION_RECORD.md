# Private live verification record template

Copy this template to an encrypted, access-controlled location **outside this repository** before filling it in. Do not commit the completed record. Never record tokens, secrets, private keys, webhook bodies, source diffs, or raw AI responses.

This template supports the remaining credential-gated acceptance checks. It does not make a live integration verified by itself.

## Preconditions

- Date and operator:
- Application version / immutable commit:
- Non-demo environment and HTTPS origin:
- Disposable GitHub organization/repository:
- GitHub App slug and numeric installation ID (no credentials):
- Cloud provider/model and configured maximum review cost:
- Database and RabbitMQ health verified:
- GitHub Actions workflow run URL, if a remote run is authorized:

## GitHub authentication and repository access

1. Sign in through GitHub and record the resulting dashboard account identifier (not a token):
2. Confirm the callback returns to `/app` and `/api/me` is authenticated:
3. Confirm the expected installation and repository appear after synchronization:
4. Sign in as a user without access and confirm the repository is hidden:
5. Sign out and confirm protected API calls return `401`:

Result: PASS / FAIL / NOT RUN

## Verified webhook and review lifecycle

1. Open a small disposable pull request with a changed source line.
2. Record the GitHub delivery ID, event action, PR number, commit SHA, returned `202` status, and internal review-job ID.
3. Confirm status progression from `PENDING` or `QUEUED` to `PROCESSING` and then `COMPLETED`.
4. Confirm results and findings appear only for an authorized dashboard user.
5. Redeliver the same event and confirm no second job exists for the same commit.
6. Push a new commit and confirm a new review is created; confirm stale unfinished work is cancelled.
7. Remove repository access or uninstall the App; confirm access is denied and pending work cannot continue.

Result: PASS / FAIL / NOT RUN

## Cloud AI verification

1. Use one non-sensitive, explicitly opted-in repository and a small diff.
2. Record the configured provider/model, request time, job ID, measured versus estimated usage, and estimated cost. Do not retain repository content or raw model output here.
3. Confirm stored findings use valid paths and changed-line locations, while invalid locations become general observations.
4. Confirm private-code opt-in is required and disabling consent blocks external analysis.
5. Confirm the configured budget and concurrency limits reject an over-budget or concurrent request safely.

Result: PASS / FAIL / NOT RUN

## GitHub publishing verification

1. Leave automatic publishing disabled and request one manual publication for the completed disposable review.
2. Record the review job ID, current commit SHA, GitHub review URL/ID, and result status.
3. Confirm a second manual request does not create duplicate comments.
4. Enable automatic publishing only for a new disposable review and confirm one publication record is created.
5. Change the PR head before dispatch and confirm publication is refused rather than posted at a stale location.

Result: PASS / FAIL / NOT RUN

## Remote CI and closeout

1. If an authorized remote exists, record the GitHub Actions run URL and status for backend, frontend, and containers/browser jobs.
2. Confirm no secrets or private data appear in logs or artifacts.
3. Record failures, remediation, and re-run evidence. Do not mark a failed or skipped check as passed.

Overall result: PASS / FAIL / PARTIAL

Follow [GitHub App setup](GITHUB_SETUP.md), [AI configuration](AI.md), [publishing](PUBLISHING.md), and [CI](CI.md) for the detailed setup and safety constraints.
