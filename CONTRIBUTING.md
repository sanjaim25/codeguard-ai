# Contributing to CodeGuard AI

## Development setup
See [README.md](README.md) for local development prerequisites and commands.

## Branch naming
- Features: `feature/<short-description>`
- Bug fixes: `fix/<short-description>`
- Documentation: `docs/<short-description>`

## Pull request checklist
- [ ] `mvn verify` passes (backend unit + integration tests)
- [ ] `npm test` passes (frontend component tests)
- [ ] `npm run lint` passes
- [ ] `npm run build` passes
- [ ] No secrets or `.env` file committed
- [ ] No new `npm audit` high/critical advisories introduced
- [ ] PHASE_STATUS.md updated if a new feature is added

## Coding standards
- Backend: standard Java/Spring idioms; no Lombok; constructor injection only
- Frontend: TypeScript strict mode; no untyped `any`; Tailwind for all styling
- Never log request bodies, diffs, tokens, provider responses, or authorization headers
- Always scope database queries by authenticated user's repository grants
- Review diffs and findings are sensitive — handle with care

## Security
- Read SECURITY.md before making any change that touches auth, webhooks, or AI output
- Never commit credentials, keys, or tokens
- Run `npm audit` and `mvn dependency:check` before opening a PR
