# Status

*Updated at the end of /kickoff, /feature, /ship, /deploy, and /status runs. This is the first file to read when resuming work.*

- **Last updated**: 2026-09-25
- **Phase**: six games are built and unit-tested in code: Paw Match (on `main`), then Paw Pour, Paw Kitchen, Paw Trace, Paw Blocks and Paw Pop on stacked feature branches. Nothing new is in progress. Milestone 1 (hub + Paw Match) is on `main` and compiles in CI; it has still not been verified on a real device.
- **Latest build**: PRs #7 to #10 (Paw Pour, Paw Kitchen, Paw Trace, Paw Blocks) are open and green in CI (real Gradle build, unit tests and lint). Paw Pop (`feat/paw-pop`) has no PR yet, so it has not been through CI: it was checked with a stand-in toolchain (the Kotlin compiler against the Android Compose libraries, all 449 unit tests across the six games, real pointer events on the desktop Compose renderer, and headless screenshots), not with Gradle. No on-device playtest of any of the five new games has happened. The last CI build on `main` (2026-09-24, run 35945563222) produced a `release-build` download (unsigned test build, no signing keystore yet) that does not include any of the five new games.
- **Currently in flight**:
  - PR #7: Paw Pour. PR #8: Paw Kitchen. PR #9: Paw Trace. PR #10: Paw Blocks. All open and green, awaiting founder review.
  - Paw Pop: built on `feat/paw-pop` (on top of Paw Blocks) and unit-tested in code, awaiting review. Stories 51-62 are implemented; no PR, Gradle build or on-device playtest yet. Nothing is left in progress.
  - The branches are stacked: `feat/paw-pour` -> `feat/paw-kitchen` -> `feat/paw-trace` -> `feat/paw-blocks` -> `feat/paw-pop`. Each one builds on the one before, so PR #7 has to land first.
- **Blocked on founder**:
  1. Sound. The specs mention pour, chime, whoosh, pop and gift sounds, but every game ships silent, like Paw Match (Paw Trace's sparkle chime and celebration sound, Paw Blocks' plop, chime and whoosh, and Paw Pop's pop and gift sounds, too). One app-wide decision is needed. Default if no answer: stay silent.
  2. Confirm Paw Pour, Paw Kitchen, Paw Trace, Paw Blocks and Paw Pop behave as you want on a real phone. For Paw Trace, the things only a phone can settle are how the paint feels under a real finger (the 64dp forgiving corridor) and whether the glyph fits the smallest screens. For Paw Blocks: whether dragging with the block riding 64dp above the finger feels right, whether 9 by 9 is too busy, and whether the tray blocks are big enough. For Paw Pop: whether the ship's glide toward the finger feels right, whether the busiest stage (eight targets) is calm enough, and whether the gifts read clearly.
  3. On newer phones, the edge-to-edge screen may put the system bars over the home button. This is already true of Paw Match and Paw Pour. It needs a separate fix. Paw Kitchen and Paw Trace keep clear of the bars.
- **Next up**: founder reviews PR #7 and plays Paw Pour on a device; then Paw Kitchen, Paw Trace and Paw Blocks in stack order, then Paw Pop once it has a PR. A follow-up should move the home button, sparkle and animal drawings that six games now copy into shared `ui/`.
