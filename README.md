# Paw Play

A calm little shelf of games for a 2 to 5 year old. Hand over the phone and walk away.

There are no ads, no purchases, no logins and no links out of the app. There are no timers, scores or "you lost" screens either. Your child can play with no reading at all.

## The games

The home screen shows one picture tile per game. Tap a tile to play.

- **Paw Match** — a card-matching game. Flip two cards to find the same animal. Wrong guesses flip back quietly. Each new round adds one more pair, up to six pairs.
- **Paw Pour** — a colour-sorting puzzle. Tap a tube of coloured liquid to lift it, then tap another tube to pour into it. Your child keeps going until every colour sits in its own full tube. Each colour also has its own shape, so it works for colour-blind children. Rounds get a little bigger as you go, up to 9 tubes.
  - Every round can be finished.
  - A wrong tap gives a tiny wobble. Nothing scolds.
  - If your child pours themselves into a dead end, the game quietly undoes the last pour or pours after a moment.
  - It has no sound yet.
- **Paw Kitchen** — a tiny restaurant. A friendly animal shows up and asks for a dish in pictures: a burger, a pizza or an ice cream. Your child taps ingredients to build it. When the dish is exactly what was asked for, the serve button wakes up. Tap it and the customer cheers, then the next one arrives.
  - Nothing is ever "wrong": extra ingredients are fine, the serve button just stays asleep until the dish matches.
  - Orders start at 2 ingredients and grow to 5.
  - New dishes can be added later without changing how the game works.
  - It has no sound yet.
- **Paw Trace** — a tracing game for small fingers. A wide, soft path shows what to trace, and a friendly frog waits at the start of the next line. Your child drags a finger along the path and it fills with colour. Colour only appears where the finger is on the path, so nothing is ever wrong: off the path, nothing happens, and lifting and starting again anywhere on the path just carries on. When a line is nearly covered it finishes itself with a sparkle, and when the whole shape is done there is a little celebration and one big button to play on.
  - It starts with lines and curves, then simple shapes, then the numbers 1 to 9 and 0, then the capital letters. Every third one is an easier one from before. Nobody picks a level; it just goes.
  - Letters with several lines, like E, 4 or A, can be traced in any order and either direction.
  - After all 46 it keeps going with a random mix.
  - It has no sound yet.

- **Paw Blocks** — a block-fitting puzzle with nothing falling and no clock. Three blocks wait in a tray. Drag one onto the board and a soft blue outline shows where it will land; let go and it snaps in. Fill a whole row or column and it sparkles away while a little animal peeks over the corner of the board. A wrong drop just glides back to the tray.
  - The board starts empty at 5 by 5 and grows a little as your child clears lines, up to 9 by 9, while new block shapes join in one at a time. Nobody picks a level.
  - Every block has its own colour and its own small picture, so children who can't tell colours apart can still play.
  - Three new blocks arrive together, and at least one always has a place to go. If the board ever fills so that none of them fits, it quietly sparkles a few rows clear after a moment. There is no score, no counter and no "game over".
  - It has no sound yet.

All five games start over from the beginning when you leave to the home screen. Nothing is saved.

More games are planned. See `docs/PRD.md`. Paw Pour, Paw Kitchen, Paw Trace and Paw Blocks are built and waiting for review and a test on a real phone.

## Build it on your computer

You need Android Studio, which brings the Android SDK and a JDK with it.

1. Open Android Studio. Choose Open, then pick this folder.
2. When it offers to create the Gradle wrapper (`gradlew`), accept.
3. Press Run to start it on an emulator or a connected phone.

Or from a terminal in this folder:

```bash
./gradlew assembleDebug        # build the app (use `gradle` if there is no wrapper yet)
./gradlew testDebugUnitTest    # run the game-rule tests (no phone needed)
./gradlew lintDebug            # check for common mistakes
```

The debug app lands at `app/build/outputs/apk/debug/app-debug.apk`.

These commands come from the project's CI setup. They have not been run from this README yet, so if one fails, tell us.

## Get it onto a phone

Pick one.

**From a build on GitHub.** Every merge to `main` runs a build. Open the repository's Actions tab, open the latest "Release build" run, and download `release-build`. Copy the `.apk` file to your phone and open it. Android will ask you to allow installs from that source. There is no signing key set up yet, so this is a test build, not a Play Store release.

**From your computer over USB.** Turn on USB debugging on the phone, plug it in, and run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app needs Android 8.0 or newer.

## How this project is run

You are the product manager. Specialist agents in `.claude/agents/` do the rest, in Claude Code. `CLAUDE.md` explains the team and the rules. The everyday commands:

| Command | What it does |
|---|---|
| `/feature <idea>` | Builds something end to end. You approve the plan, then review the pull request. |
| `/ship` | Merges an approved pull request and makes a build. |
| `/deploy` | Builds `main`, and sets up release signing the first time. |
| `/status` | A one-minute catch-up on what is done, in progress and waiting on you. |

The project's memory lives in `docs/`: the plan (`PRD.md`), how it is built (`ARCHITECTURE.md`), why choices were made (`DECISIONS.md`), the look (`DESIGN-SYSTEM.md`) and where things stand (`STATUS.md`).

To make a signed release build, run `/deploy`. Publishing to Google Play is optional and separate. It needs a one-time developer account fee.
