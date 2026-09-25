# Status

*Updated at the end of /kickoff, /feature, /ship, /deploy, and /status runs. This is the first file to read when resuming work.*

- **Last updated**: 2026-09-25
- **Phase**: all six games are merged into `main`: Paw Match, Paw Pour, Paw Kitchen, Paw Trace, Paw Blocks and Paw Pop (PRs #7 to #11, merged in that order with merge commits). Nothing is in progress. None of the five newest games has been played on a real phone yet.
- **Latest build**: `main` at merge commit 9264066 passed CI (real Gradle lint, unit tests and debug build) and the `Release build` workflow (run 36098377713). Its `release-build` download is an unsigned test build (no signing keystore yet) that includes all six games. Every earlier merge into `main` passed the same checks, so each step of the stack was verified on its own.
- **Currently in flight**: nothing. The merged feature branches (`feat/paw-pour`, `feat/paw-kitchen`, `feat/paw-trace`, `feat/paw-blocks`, `feat/paw-pop`) and `docs/status-*` branches still exist on GitHub and can be deleted.
- **Blocked on founder**:
  1. Sound. The specs mention pour, chime, whoosh, pop and gift sounds, but every game ships silent, like Paw Match. One app-wide decision is needed. Default if no answer: stay silent.
  2. Play the games on a real phone. Only a phone can settle: tube size and pour feel (Pour); tray tile size and thin-topping taps (Kitchen); how the 64dp paint corridor feels under a small finger (Trace); how dragging with the block riding 64dp above the finger feels, and whether 9x9 is too busy (Blocks); how the ship's glide toward the finger feels, whether the busiest stage is calm enough, and whether the ramp pace is right (Pop, the first real-time game).
  3. Blocks on phones wider than 360dp: the board keeps its design size and centres, leaving a gap above the tray. A bigger board there needs a small design pass. Do you want it?
  4. On newer phones, the edge-to-edge screen may put the system bars over the home button. This is already true of Paw Match and Paw Pour. It needs a separate fix. Paw Kitchen, Paw Trace, Paw Blocks and Paw Pop keep clear of the bars.
- **Next up**: build the release from `main` (`/deploy`; needs the signing keystore set up first for a signed build), install it on the founder's phone and playtest all six games. Follow-ups: move the home button, sparkle and animal drawings that six games now copy into a shared `ui/` folder; trim `docs/PRD.md`, which is far past its two-page target; per-game notes in the merged PR descriptions (Trace digit 9 credit and short-stroke ends, Kitchen's instant customer swap, Pop's star-exit twinkle).
