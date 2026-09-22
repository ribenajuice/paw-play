---
name: status
description: Product status report — what shipped, what's in flight, what's blocked, what's next. Use when returning to a project after time away, or before deciding what to build next.
---

# Status Report

Produce a report the founder can absorb in 60 seconds. Gather facts first; never report from memory.

## Gather
- `git log --oneline -20` and `gh pr list` — recent work and open PRs.
- `gh run list --limit 5` — CI/build health.
- `docs/PRD.md` — milestone progress: which acceptance criteria are done.
- `docs/STATUS.md` — last recorded state (and update it after reporting).
- Latest release build (if any): where it is and whether it's installed on the founder's device.

## Report (in this order)
1. **Health line** — one sentence: is the project green, and does the latest build install and run?
2. **Shipped since last status** — plain language, player-visible framing.
3. **In flight** — open PRs/branches and what state they're in.
4. **Blocked / needs you** — decisions waiting on the founder. If none, say so.
5. **Recommended next** — the single highest-leverage `/feature` to run next, based on the PRD milestone, with a one-line why.

Keep it under ~20 lines. No jargon; write for a PM, not a developer. Finish by updating `docs/STATUS.md` with today's date and the current state.
