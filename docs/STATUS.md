# Status

*Updated at the end of /kickoff, /feature, /ship, /deploy, and /status runs. This is the first file to read when resuming work.*

- **Last updated**: 2026-09-26
- **Phase**: all six games are in `main` (Paw Match, Paw Pour, Paw Kitchen, Paw Trace, Paw Blocks, Paw Pop). On top of that, the **score, paws and good-game screen** feature for Paw Blocks and Paw Pop (PRD stories 63-73) is built and QA'd on branch `feat/score-and-game-over`. It is waiting for your review of the PR and a playtest on a real phone.
- **What the branch does**: Paw Blocks and Paw Pop now show a score and three paws, and end with a kind good-game screen (score, best score, play again, home). Paw Pop's bubbles now zig-zag, come in from a cloud bank and vary in size and speed. The best score for each game is saved on the phone only: not backed up, not transferred, no network. To reset it, clear app storage in Android settings. The app also no longer restarts a game when dark mode or font size changes.
- **Checks on the branch**: 682 automated tests pass. The security check (no ads, purchases, tracking, network or backup of the saved number) is clean. Not yet checked: any real device.
- **Latest build**: `main` at 17b829b passed CI and a manual `Release build` (run 36103617834, 2026-09-25, unsigned test build with all six games, but without the score feature). No build of this branch has been installed on the founder's phone.
- **Blocked on founder**:
  1. Review the PR for `feat/score-and-game-over`, then play it on a real phone. Only a phone can settle:
     - Does the Paw Pop cloud bank and the paw pictures (Blocks and Pop) look right?
     - Does the good-game screen fade in nicely, and do the new-best glow and the animal's hop feel right?
     - Are Pop's 3 paws too harsh for a 2-year-old (a child who never touches the screen ends at about 40 seconds), or too easy at the last stage (a player who leads every shot never ran out in 25 minutes of testing)? If either, it is one number to change.
     - Is the Blocks 9x9 square size big enough on very short phones (about 35dp on a 360 x 520 screen)?
     - Does a quick tap on a Blocks tray block feel right when it does not drop?
     - Also still unchecked on a phone from the earlier games: tube size and pour feel (Pour); tray tile size and thin-topping taps (Kitchen); the 64dp paint corridor under a small finger (Trace); how dragging feels with the block riding 64dp above the finger (Blocks); how the rocket's glide feels (Pop).
  2. Sound. The specs mention pour, chime, whoosh, pop and gift sounds, but every game ships silent. One app-wide decision is needed. Default if no answer: stay silent.
  3. Blocks on phones wider than 360dp: the board keeps its design size and centres, leaving a gap above the tray. A bigger board there needs a small design pass. Do you want it?
  4. On newer phones, the edge-to-edge screen may put the system bars over the home button on Paw Match and Paw Pour. It needs a separate fix. The other four games keep clear of the bars.
- **Housekeeping**: merged branches (`feat/paw-pour`, `feat/paw-kitchen`, `feat/paw-trace`, `feat/paw-blocks`, `feat/paw-pop`, `docs/status-all-games-merged`) still exist on GitHub and can be deleted. Leftover local folders sit in `.claude/worktrees/` (untracked, safe to clear).
- **Next up**: merge the score PR once you approve it, build the release from `main` (`/deploy`; a signed build needs the signing keystore set up first), install it and playtest all six games. Follow-ups: move the home button, sparkle and animal drawings that games copy into a shared `ui/` folder; trim `docs/PRD.md`, which is far past its two-page target.
