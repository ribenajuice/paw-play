---
name: kickoff
description: Start a brand-new project from the template — interviews the founder, produces PRD + architecture + design direction, scaffolds the code, and sets up GitHub and the release pipeline. Run once per project.
---

# Project Kickoff

You are running the founder's project kickoff for a kid's Android game. They are a product manager: gather product intent from them, then let the team handle everything technical. Use AskUserQuestion for the interview steps; make sensible autonomous decisions everywhere else.

## Step 1 — Interview (the only heavy input step)
Ask, in at most two rounds of questions:
1. What's the game, in a sentence — genre/mechanic (puzzle, platformer, matching, quiz, endless runner, something else)?
2. Who's it for — the child's age, and roughly how independently they'll play (fully alone vs. sometimes with you)?
3. What's the ONE thing a player must be able to do in v1 — the core loop, played once end to end?
4. Any hard constraints? (must work fully offline, session length, must be simple enough to quit anytime, existing characters/theme the founder has in mind)
5. Visual vibe (bright & cartoonish / soft & calm / bold primary colors / founder's own words)

Do not ask about monetization — there is none, by standing rule. Do not ask about analytics/ads — also none.

## Step 2 — Product & technical foundation
- Launch the **product-manager** agent with the interview answers → it writes `docs/PRD.md` with Milestone 1 as a walking skeleton (the core loop, played once, with no polish).
- Launch the **architect** agent → it writes `docs/ARCHITECTURE.md`, records ADRs in `docs/DECISIONS.md`, and picks the engine per its defaults (Godot for a genuine game, Kotlin+Compose for a UI-driven app).
- Present both summaries to the founder in one message. Get a go/no-go before writing code.

## Step 3 — Design direction
- Launch the **ui-designer** agent → 2–3 visual directions as an Artifact + `docs/DESIGN-SYSTEM.md`, sized and colored the way the actual Android screens will be.
- Founder picks a direction; designer commits it to the design system doc.

## Step 4 — Scaffold
- Scaffold the chosen engine/stack (official project template — `godot --headless` project init, or `androidx` Compose starter).
- Fill in the `<!-- KICKOFF -->` placeholders in `CLAUDE.md`: project name, one-line description, stack, dev commands (install/build/test/lint).
- Make sure `scripts/deploy.sh` matches the chosen build path (architect's design) — it should produce an installable debug APK at minimum.
- Verify the project builds and any starter tests pass before declaring done.

## Step 5 — GitHub & release wiring
- Confirm the repo exists on GitHub with CI green (`.github/workflows/ci.yml` runs on push). If the repo isn't created yet: `gh repo create <name> --private --source=. --push`.
- Ask whether to set up signed release builds now or later. If now: the devops-engineer agent walks the founder through generating a release keystore, storing it as a GitHub Actions secret (never in the repo), and wiring `.github/workflows/deploy.yml` to produce a signed AAB on release.
- Publishing to the Play Store (needs a one-time $25 Google Play Developer account) is a separate, later decision — don't push for it during kickoff; a sideloadable APK is enough to get the game onto the founder's own phone.

## Step 6 — Handover
Final message: what was built, links (repo, CI run, design artifact), how to get the current build onto a phone, and the suggested first `/feature` to build next. Update `docs/STATUS.md` with the current state.
