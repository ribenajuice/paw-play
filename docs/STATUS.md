# Status

*Updated at the end of /kickoff, /feature, /ship, /deploy, and /status runs. This is the first file to read when resuming work.*

- **Last updated**: 2026-09-25
- **Phase**: building milestones 2, 3 and 4 — Paw Pour (`feat/paw-pour`), Paw Kitchen (`feat/paw-kitchen`) and Paw Trace (`feat/paw-trace`) are built and unit-tested in code, but not yet confirmed by a real Gradle build or on a phone. Milestone 1 (hub + Paw Match) is on `main` and compiles in CI; it has still not been verified on a real device.
- **Latest build**: none verified yet for these branches. The last CI build on `main` (2026-09-24, run 35945563222) succeeded and produced a `release-build` download (unsigned test build, no signing keystore yet), but it does not include Paw Pour, Paw Kitchen or Paw Trace. A real Gradle build of these branches and an on-device playtest are both still pending. The Paw Trace pieces were checked with a stand-in toolchain (Kotlin compiler against the Android Compose libraries, all 208 unit tests across the four games, and headless screenshots), not with Gradle.
- **Currently in flight**:
  - PR #7: Paw Pour (draft).
  - Paw Kitchen: built on `feat/paw-kitchen` (rebased onto `feat/paw-pour` on 2026-09-25) and unit-tested in code, awaiting review. It has not had a real Gradle build or an on-device playtest either.
  - Paw Trace: built on `feat/paw-trace` (on top of Paw Kitchen) and unit-tested in code, awaiting review. Stories 29-38 are implemented; no Gradle build or on-device playtest yet.
  - Paw Blocks: spec approved, awaiting build.
  - The branches are stacked: `feat/paw-pour` -> `feat/paw-kitchen` -> `feat/paw-trace` -> `feat/paw-blocks`. Each one builds on the one before, so PR #7 has to land first.
- **Blocked on founder**:
  1. Sound. The specs mention pour, chime and happy sounds, but every game ships silent, like Paw Match (Paw Trace's sparkle chime and celebration sound too). One app-wide decision is needed. Default if no answer: stay silent.
  2. Confirm Paw Pour, Paw Kitchen and Paw Trace behave as you want on a real phone. For Paw Trace, the things only a phone can settle are how the paint feels under a real finger (the 64dp forgiving corridor) and whether the glyph fits the smallest screens.
  3. On newer phones, the edge-to-edge screen may put the system bars over the home button. This is already true of Paw Match and Paw Pour. It needs a separate fix. Paw Kitchen and Paw Trace keep clear of the bars.
- **Next up**: founder reviews PR #7 and plays Paw Pour on a device; then Paw Trace and Paw Blocks in stack order. A follow-up should move the home button and win overlay that four games now copy into shared `ui/`.
