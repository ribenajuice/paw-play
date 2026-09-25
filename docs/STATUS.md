# Status

*Updated at the end of /kickoff, /feature, /ship, /deploy, and /status runs. This is the first file to read when resuming work.*

- **Last updated**: 2026-09-25
- **Phase**: building milestone 2 — Paw Pour built on `feat/paw-pour` and unit-tested in code, but not yet confirmed by a real Gradle build or on a phone. Milestone 1 (hub + Paw Match) is on `main` and compiles in CI; it has still not been verified on a real device.
- **Latest build**: none verified yet for this branch. The last CI build on `main` (2026-09-24, run 35945563222) succeeded and produced a `release-build` download (unsigned test build, no signing keystore yet), but it does not include Paw Pour. A real Gradle build of `feat/paw-pour` and an on-device playtest are both still pending.
- **Currently in flight**:
  - PR #7: Paw Pour (draft).
  - Paw Kitchen: built on `feat/paw-kitchen`, awaiting QA.
  - Paw Trace and Paw Blocks: specs approved, awaiting build.
  - The branches are stacked: `feat/paw-pour` -> `feat/paw-kitchen` -> `feat/paw-trace` -> `feat/paw-blocks`. Each one builds on the one before, so PR #7 has to land first.
- **Blocked on founder**:
  1. Sound. The specs mention pour, chime and happy sounds, but every game ships silent, like Paw Match. One app-wide decision is needed. Default if no answer: stay silent.
  2. Confirm Paw Pour behaves as you want on a real phone.
  3. On newer phones, the edge-to-edge screen may put the system bars over the home button. This is already true of Paw Match. It needs a separate fix.
- **Next up**: founder reviews PR #7 and plays Paw Pour on a device.
