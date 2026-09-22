---
name: ship
description: Merge and release — final checks on the open PR, merge to main, build a signed release, verify it installs and runs. Use when a reviewed feature is ready to go on the phone.
---

# Ship

Take the current branch/PR from "approved" to "installed and verified". Stop and report at the first failure — never push past a red step.

## 1. Pre-flight
- Confirm which PR is shipping (`gh pr status`). If more than one is open, ask the founder which.
- CI must be green on the PR. If red, diagnose and fix (or hand back to `/feature` step 4) — do not merge red.
- Working tree clean; branch up to date with main.

## 2. Final review gate
- If anything changed since the last `/code-review`, re-run it on the final diff.
- Every ship requires a security-reviewer pass with no unresolved blockers — the ads/IAP/tracking check runs on every release, not just "sensitive" ones.

## 3. Merge
- `gh pr merge --squash --delete-branch`. Squash message = plain-language summary of the user-visible change.

## 4. Build
- If `deploy.yml` builds on push to main: watch the run (`gh run watch`) until it succeeds.
- Otherwise trigger it: `gh workflow run deploy.yml` — or run `scripts/deploy.sh` locally if the founder prefers.
- Output is a signed release AAB/APK, attached as a CI artifact or GitHub release.

## 5. Verify
- Install the build on a device or emulator and exercise the shipped feature the way a player would (qa-engineer agent for anything non-trivial).
- If the build is broken: don't hand it to the founder — diagnose and fix, or roll back to the previous known-good build. Tell the founder immediately either way.

## 6. Wrap up
- tech-writer agent updates `CHANGELOG.md`.
- Final message: what's new, where the installable build is (link) and how to get it onto a phone, verification evidence, and rollback status if anything went wrong. Update `docs/STATUS.md`.
