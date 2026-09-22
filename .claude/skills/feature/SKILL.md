---
name: feature
description: Build a feature end-to-end from a one-line description — spec, design, implementation, tests, PR. The founder's main day-to-day command.
---

# Feature Pipeline

Input: `$ARGUMENTS` — the founder's description of what they want. Run the pipeline below. Keep the founder's involvement to two checkpoints: approving the spec, and reviewing the finished PR.

## 1. Spec (product-manager agent)
Turn the description into user stories + acceptance criteria, appended to `docs/PRD.md` under the current milestone. If the request is ambiguous in ways that change what gets built, ask the founder now — never later, mid-build.
**Checkpoint:** show the founder the acceptance criteria; get approval before building. Skip this checkpoint only for trivial changes (copy, styling tweaks).

## 2. Branch
Create a feature branch: `feat/<slug>`. Never build on main.

## 3. Design (ui-designer agent — only if the feature adds/changes screens)
Mockup for new screens as an Artifact if the feature is visually significant; otherwise the designer just confirms which design-system components to use.

## 4. Build (android-developer agent)
Implements gameplay/screen logic following `docs/ARCHITECTURE.md` and `docs/DESIGN-SYSTEM.md`.

## 5. Verify (qa-engineer agent)
QA tests each acceptance criterion by exercising the build on an emulator/device, adds automated tests, and reports pass/fail. A failed criterion goes back to step 4 — max two round-trips before escalating to the founder.

## 6. Review & PR
- Run the built-in `/code-review` skill on the diff; fix confirmed findings.
- Every feature gets a **security-reviewer** pass before merge — this is a kid's game, so the ads/IAP/tracking check runs every time, not just on "sensitive" changes. Fix blockers.
- Launch the **tech-writer** agent if README/CHANGELOG need updating.
- Open a PR with `gh pr create`: summary, acceptance criteria checklist (checked per QA results), test evidence, screenshots/artifact links for UI work.

## 7. Report
Final message: PR link, what shipped vs. what was cut, QA verdict, and anything needing the founder's judgment. Update `docs/STATUS.md`.
