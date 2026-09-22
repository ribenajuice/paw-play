# 🎮 Kid Game Team Template

A project template that gives you a full software team inside Claude Code, adapted for building a kid's Android game. You act as the product manager; a roster of specialist agents (PM, architect, UI designer, Android developer, QA, DevOps, security, tech writer) handles the rest — wired into GitHub for CI and a signed release build pipeline. No ads, no in-app purchases, no accounts, by standing rule.

> **Already scaffolded a project from this?** This README gets rewritten by the tech-writer agent to describe *your game*. The template manual below is for starting fresh.

## Starting the project

```bash
claude
> /kickoff
```

`/kickoff` interviews you (5 product questions, ~2 minutes — genre, who it's for, the core loop, constraints, visual vibe), then the team writes the PRD, picks the engine (Godot for a real game, Kotlin+Compose for a UI-driven one), shows you visual directions as clickable mockups, scaffolds the code, and wires up CI. That's the whole setup.

## Day-to-day: the five commands

| Command | What it does |
|---|---|
| `/kickoff` | One-time project start: interview → PRD → architecture → design → scaffold |
| `/feature add a new level` | Builds a feature end-to-end: spec → branch → build → QA → PR. You approve the spec at the start and review the PR at the end — that's it. |
| `/ship` | Merges the approved PR, produces a signed release build, verifies it installs and runs |
| `/deploy` | Builds main directly; also does first-time signing setup |
| `/status` | 60-second catch-up: what shipped, what's in flight, what needs you |

A typical week: `/status` to catch up → `/feature <idea>` a few times → review the PRs → `/ship`.

## The team

Agents live in `.claude/agents/` — each has a role, standards, and rules:

- **product-manager** — PRDs, user stories, cutting scope
- **architect** — engine choice (Godot / Kotlin+Compose), offline-first, decision log
- **ui-designer** — design system + HTML mockups you can view before code exists, sized for a child's fingers
- **android-developer** — implementation: gameplay, screens, local save state
- **qa-engineer** — verifies acceptance criteria on a real device/emulator, tries to break things the way an impatient kid would
- **devops-engineer** — CI/CD, release signing, build pipeline
- **security-reviewer** — audits every release for ads, IAP, and tracking before it ships
- **tech-writer** — README, changelog, user-facing copy

The project's shared memory lives in `docs/` (PRD, architecture, decision log, design system, status). Every session reads these, so you never re-explain your project.

## One-time machine setup (prerequisites)

Only needed once per computer — you likely have these already:

```bash
gh auth login        # GitHub CLI
```

Android builds need a JDK + the Android SDK (Android Studio's SDK manager is the easiest way to get these) or, for a Godot project, the Godot editor with the Android export templates installed. `/kickoff` checks for what it needs and tells you what's missing.

## Release builds

Run `/deploy` in Claude Code, or manually:

```bash
./scripts/deploy.sh
```

First time, this walks you through generating a signing keystore and storing it as a GitHub Actions secret — never in the repo. After that, every merge to `main` produces a signed build via `.github/workflows/deploy.yml`, downloadable as a CI artifact to sideload onto a phone. Publishing to the Google Play Store (a one-time $25 developer account) is a separate, optional later step — nothing here requires it.

## What's in the box

```
.claude/agents/      # the team (8 specialist agents)
.claude/skills/      # the workflows (/kickoff /feature /ship /deploy /status)
.claude/settings.json# pre-approved safe commands (fewer permission prompts)
CLAUDE.md            # how the team operates (filled in by /kickoff), including the no-ads/no-IAP standing rule
docs/                # PRD, architecture, decisions, design system, status
.github/workflows/   # CI on every PR; signed release build on merge to main
.github/             # PR template, issue forms, dependabot
scripts/deploy.sh        # single build/release entrypoint (CI runs exactly this)
```

## Models: one dial, with per-agent overrides

By default every agent **inherits your session's model** — run Claude Code on your strongest model (Fable 5 / Opus) and the whole team uses it. One setting controls everything.

If you hit rate limits or want to stretch usage on a big build, tier the *execution* roles down without touching the *judgment* roles: each agent file in `.claude/agents/` has a commented `# model:` line in its frontmatter — uncomment it to pin that agent.

| Tier | Agents | Suggested pin |
|---|---|---|
| Judgment — decisions and reviews; mistakes are expensive | product-manager, architect, security-reviewer | keep inherited (or `opus`) |
| Execution — builds against clear specs; the QA gate catches slips | android-developer, qa-engineer, ui-designer, tech-writer | `sonnet` |
| Infra — touches the signing key; tier down last | devops-engineer | keep inherited |

Rule of thumb: don't tier down preemptively — run fully inherited until cost actually bites.

## Improving the template

When this project teaches you something (a better agent rule, a new workflow) that would help a *future* kid's-game project too, consider copying it back to `~/template` so the next one starts smarter. This copy is yours to diverge freely, though — it doesn't need to stay in sync.
