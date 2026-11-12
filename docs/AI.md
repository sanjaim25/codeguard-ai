# AI analysis configuration

`AI_PROVIDER=mock` runs entirely locally without a key. It returns an explicitly labeled mock summary, zero AI usage and no fabricated AI findings. Local static heuristics still inspect eligible changed lines.

`AI_PROVIDER=cloud` uses an OpenAI Responses-compatible endpoint configured with `AI_BASE_URL`, `AI_API_KEY` and `AI_MODEL`. The client sends a strict JSON schema, disables response storage with `store=false`, supplies no tools, rejects redirects, and applies connection/read timeouts. Provider data retention policies still apply; `store=false` is not a guarantee of zero retention. See the official [structured outputs guide](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses).

Private repository code requires an explicit repository policy opt-in. The worker reads current policy immediately before preparing the provider call; a revocation cannot recall a request already in flight. Without consent it completes with local static checks and an explanatory summary. No repository code is executed. Repository text is isolated as user data and the system instructions reject embedded instructions; prompt controls reduce risk but do not make model output trustworthy. Every finding is validated server-side.

The normalizer excludes common generated directories, secret-file names, lockfiles and unsupported file types. It does not detect every embedded secret: permitted source patches may contain sensitive data. Findings can refer inline only to actual added lines in complete, parsed patches. Unknown file paths are removed; invalid locations become general observations. Truncation and excluded files are recorded so the UI can disclose limited coverage.

## Limits and accounting

- `AI_MAX_DIFF_CHARS`: total normalized patch characters, default 40,000.
- `AI_MAX_INPUT_TOKENS`: conservative input bound, default 64,000. The client counts UTF-8 request bytes plus a framing margin, not a model-specific tokenizer estimate.
- `AI_MAX_OUTPUT_TOKENS`: provider output cap, default 4,000.
- `AI_CONCURRENCY`: simultaneous cloud calls per application process, default 2. Multiple replicas multiply this limit.
- `AI_MAX_COST_USD`: estimated reservation ceiling per review across attempts, default 0.05.
- `AI_INPUT_PER_MILLION` and `AI_OUTPUT_PER_MILLION`: operator-supplied model prices. Template values are illustrative; verify current provider pricing before enabling cloud mode.

Each attempt reserves its worst-case estimated cost in PostgreSQL before sending code. Reservations remain after failure because a timeout may still have incurred a charge. A retry is refused if cumulative reservations would exceed the review ceiling. This is a conservative application safeguard based on configured prices, not a provider-enforced billing limit; set a provider project spending limit as well.

Returned token counts are labeled measured only when the provider supplies valid counts. Without them, usage uses conservative bounds and is labeled estimated. Costs remain estimates even when token counts are measured. Refusals, malformed/truncated structured responses and authorization failures are not blindly retried. Rate limits and transient failures are retried by the durable job lifecycle, never by an unbounded HTTP retry loop.

Live cloud calls are **UNVERIFIED**. The automated provider suite uses mocked HTTP and never incurs charges. After configuring credentials, opt in a non-sensitive test repository, open a small PR, and verify the persisted provider/model, usage, mapped findings and estimated cost. Keep automatic GitHub publishing disabled during this check.
