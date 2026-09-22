# Paw Play

A calm, ad-free hub of small games for a 2-5 year old, opened with the phone handed over and no supervision needed. First game on the shelf: Paw Match, a matching game.

> Stack & commands below are still unfilled — run `/kickoff` (or just start scaffolding, per `docs/PRD.md`) to get the project actually building.

## Who you're working with

The founder is a **parent building a game for their kid, not a professional developer**. They decide *what* the game is and how it should feel; you and the agent team decide *how*. Practically:

- Explain things in player terms, not implementation terms. "You can now pick a level" beats "added level-select screen state".
- Never ask them technical questions the team can answer (engine choice, save format, which library). Decide, record it in `docs/DECISIONS.md`, and mention it.
- Do ask them product questions (what should happen when the kid loses a level? should there be a timer?) — and ask early, never mid-build.
- Show, don't describe: screen decisions go through mockup Artifacts they can look at.

## The standing rule

This game will never have ads, in-app purchases, virtual currency, a login, or anything that asks a child to make a decision involving money or an external link. This isn't a v1 scope cut — it's the whole point of the project. Every agent treats a request that would violate this as something to flag and confirm, not build.

## The team

Specialist agents live in `.claude/agents/`. Delegate to them by role — don't do a specialist's job ad-hoc when a workflow calls for that specialist:

| Agent | Owns |
|---|---|
| product-manager | PRD, user stories, scope cuts |
| architect | Engine/stack choice, `docs/ARCHITECTURE.md`, ADRs |
| ui-designer | `docs/DESIGN-SYSTEM.md`, mockups |
| android-developer | Implementation — gameplay, screens, save state |
| qa-engineer | Testing against acceptance criteria, on-device |
| devops-engineer | CI/CD, signing, release builds |
| security-reviewer | Ads/IAP/tracking audits before every release |
| tech-writer | README, CHANGELOG, user-facing copy |

## The workflows

| Command | When |
|---|---|
| `/kickoff` | Once, at project start |
| `/feature <description>` | Day-to-day building |
| `/ship` | Merge an approved PR and produce a release build |
| `/deploy` | Build main / first-time signing setup |
| `/status` | Catch up after time away |

Requests that arrive without a slash command still follow the same discipline: a build request follows the `/feature` shape (spec → branch → build → QA → PR), even informally.

## Ground rules

- **Branches**: never commit directly to `main`; all work via feature branches and PRs.
- **Docs are the memory**: `docs/PRD.md` (what & why), `docs/ARCHITECTURE.md` (how), `docs/DECISIONS.md` (ADR log), `docs/DESIGN-SYSTEM.md` (look & feel), `docs/STATUS.md` (current state). Keep them current — they're how future sessions get context.
- **Checkpoints, not check-ins**: the founder approves specs and reviews PRs. Between those two points, work autonomously.
- **Secrets**: the release signing keystore lives only as a GitHub Actions secret, never in code or committed anywhere.
- **Definition of done**: acceptance criteria verified by QA on-device, CI green, security-reviewer's ads/IAP/tracking check clean, docs updated. Not before.

## Stack & commands

<!-- KICKOFF: filled in during /kickoff -->
- Engine:
- Install: `<!-- e.g. open in Android Studio / godot --headless --import -->`
- Build (debug): `<!-- e.g. ./gradlew assembleDebug -->`
- Test: `<!-- e.g. ./gradlew testDebugUnitTest -->`
- Lint: `<!-- e.g. ./gradlew lintDebug -->`
- Release build: `scripts/deploy.sh` (CI runs it via `.github/workflows/deploy.yml`)
