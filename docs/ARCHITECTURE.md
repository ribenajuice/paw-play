# Architecture

*Owned by the architect agent. If code and this doc disagree, fix one of them.*

## Stack
- **Language/UI**: Kotlin + Jetpack Compose (native Android). See ADR 2026-09-22 for why over Godot.
- **Local storage**: Jetpack DataStore (Preferences), added when the first game actually needs to remember something between sessions. Nothing in Milestone 1 needs persistence — every round starts fresh.
- **Third-party dependencies**: none beyond standard AndroidX/Compose libraries. No ad SDK, no analytics SDK, no billing library, no network library.

## Shape: a hub with pluggable game modules
The app is one shell (the home screen) plus a growing set of independent games. Each game is a **module that knows nothing about the others or about the shell beyond one small contract**:

```kotlin
interface MiniGame {
    val id: String              // stable, e.g. "paw-match" — never reused once shipped
    val icon: @Composable () -> Unit   // the home-screen tile's icon, no text
    val content: @Composable (onExit: () -> Unit) -> Unit  // the game itself; calls onExit() to return home
}
```

- Each game lives in its own package (`games/pawmatch/`, `games/<nextgame>/`), owns its own state, and is added to a single static list (`GameCatalog.kt`) that the home screen renders as a grid of tiles — one tile per registered game, in list order.
- **Adding a game is additive only**: a new package + one line in `GameCatalog.kt`. No existing game, and nothing in the home screen shell, should need to change to add the next one. If a change request would require touching another game to add a new one, that's an architecture problem — stop and flag it rather than special-case it.
- The home screen never shows a tile for a game that isn't actually built and registered — no "coming soon" placeholders in the shipped app (see PRD, out of scope). The grid simply has as many tiles as there are entries in the catalog.
- The home screen's tile grid and Paw Match's card grid both use a shared `AdaptiveSquareGrid` composable (`app/src/main/java/com/pawplay/app/ui/AdaptiveSquareGrid.kt`): given N same-size square things, it picks whichever column count (2-4) makes them as big as possible while still fitting on screen with no scrolling, never below a 48dp touch-target floor. Any future screen with the same "N things, must fit, no scroll" shape — including the home shelf as it grows toward its ~dozen-game target — reuses this rather than re-deriving the layout math. See `docs/DECISIONS.md`, 2026-09-23/24.
- A `MiniGame`'s `icon` composable receives the tile size it's being drawn at (not a fixed dp value), since the same tile might render at 220dp with one game on the shelf or under 100dp with a dozen. Every icon in `AnimalIcons.kt` is written as a fraction of its container size for exactly this reason — a new game's tile icon should follow the same pattern.
- Every `MiniGame.content` gets an `onExit` callback (a large, obvious "home" icon inside the game) rather than the system back button being the only way out — consistent, discoverable, works the same in every game.

## System diagram
```mermaid
flowchart TB
    P[Toddler] --> HOME[Home screen — tile grid]
    HOME -->|tap tile| CATALOG[GameCatalog]
    CATALOG --> G1[Paw Match]
    CATALOG --> G2[Paw Pour]
    CATALOG --> G3[Paw Kitchen]
    CATALOG --> G4[Paw Trace]
    CATALOG -.->|future| G5[Game 5]
    G1 -->|onExit| HOME
    G2 -->|onExit| HOME
    G3 -->|onExit| HOME
    G4 -->|onExit| HOME
```
No server, no API, no database, no network calls. Fully offline. Dotted lines are games that don't exist yet — not stubs in the code, just future catalog entries.

Paw Pour's package (`games/pawpour/`): `PawPourLogic.kt` (rules, ramp, generation, round state), `PawPourSolver.kt` (solvability search), `PawPourLayout.kt` (tube sizes/positions as plain numbers), and the Compose files `PawPourGame.kt`, `TubeView.kt`, `BandStyle.kt`. Like Paw Match, only the logic/solver/layout files are unit-tested; the Compose files just call into them.

Paw Trace's package (`games/pawtrace/`) splits the same way. Pure Kotlin, unit-tested on a plain JVM: `TraceGeometry.kt` (SVG-subset parser and the 1.5-unit sampler), `TraceGlyphs.kt` (the 46 glyphs as data), `PawTraceLogic.kt` (paint model, marker, finger rules, `TraceSession`), `PawTraceRamp.kt` (the shapes, numbers, letters ramp) and `PawTraceLayout.kt` (where things go, as plain numbers). Compose: `PawTraceGame.kt` (game object, screen, frog, celebration, buttons, tile icon), `TraceDrawing.kt` (guide, paint, glow, paw prints, and a per-glyph path cache), `TraceFrog.kt` (the frog's face, drawn inside the package) and `TraceColors.kt`. Paw Trace imports nothing from Paw Kitchen or Paw Pour, and from Paw Match only the public `HomeGlyphIcon` and `PawPrintIcon`, the same pattern Paw Pour and Paw Kitchen use; a unit test (`TraceQaTest`) fails if another cross-game import appears.

## Data model
- **MiniGame catalog entry**: id, icon composable, content composable (see contract above). Static list, no persistence.
- **Paw Match round state** (in-memory only): list of Cards (id, matched pair id, icon reference, revealed, matched), count of matched pairs, derived "is round complete" flag.
- **Paw Pour round state** (in-memory only): a `Board` (tubes as bottom-first lists of `BandColor`, plus capacity) wrapped in a `RoundState` (round number, the board, `history` of every pour as `Step(boardBefore, move)`, and `safeDepth` = how many pours lead to the latest position known to be finishable). The ramp (`specForRound`) is a pure function of the round number, capped at round 6 (9 tubes, 7 colours). Selection, the pour animation and wobble live in the composable, not in the round state.
- **Paw Kitchen state** (in-memory only, `games/pawkitchen/`): a `KitchenState` holds the menu (list of `Dish`), customers served this session, the current customer (a Paw Match `Critter`), the `Order` (dish + ingredient ids in shelf order), the tray (ingredient ids in a fixed random position order), the set of ingredients on the dish, and a phase (`BUILDING` or `SERVING`). A `Dish` is pure data: id, base ingredient, shelf of 3-6 ingredient ids. All rules are pure functions in `PawKitchenLogic.kt` (ramp, order generation, add/remove/toggle, exact-match, serve, next customer), unit tested with no Android.
- **Paw Kitchen content** (`KitchenMenu.kt`, `KitchenIcons.kt`): a `DishSpec` pairs a `Dish` with one vector picture per shelf ingredient and a `DishView` that says which pieces are drawn (and where they can be tapped) for the ingredients currently on the dish. **Adding a dish = adding one `DishSpec` to `KitchenMenu.specs`**; ordering, tray, serve, ramp and the screen do not change. `KitchenMenuTest` checks every dish in the menu is complete.
- **Paw Trace state** (in-memory only, `games/pawtrace/`): a `Glyph` is an id, a stage (1-4) and its strokes as SVG path strings on a 100 x 100 box; strokes are parsed (absolute M, L, C only) and sampled every 1.5 units. `TracePaint` is one glyph's painting: per stroke, which samples a finger painted (`touched`), which are covered (touched plus the gap that fills itself at 85%), and whether it is done; plus which stroke is being painted (`current`). `RampState` is the child's place on the ramp (stage, glyphs completed once, new glyphs since the last mixed-in one, the glyph on screen). `TraceSession` ties them together with the phase (`TRACING` or `CELEBRATING`) and `PrimaryPointer` (which finger paints). Everything is in glyph units; only the sizes made for a fingertip (32dp corridor, 56dp band, 48dp frog lead, 134dp hint walk) are dp constants in `TraceMetrics`, converted to glyph units by `TraceTuning` for whatever box size the screen gets.
- (Future) **Preferences**: per-game settings worth remembering (e.g. a theme choice) — persisted via DataStore only once a specific game needs it; not a shared/global concept until two games actually want the same thing.

## Key flows
- **App open → home**: app launches straight into the home screen tile grid. No splash screen with text.
- **Tile tap → game**: tap a tile → that game's `content` composable takes over the full screen.
- **Paw Match round**: shuffle N pairs of animal icons into a grid → all cards face-down → tap flow (flip, compare after a short delay, match-and-lock or mismatch-and-flip-back) → win screen (play again / home).
- **Paw Pour round**: `generateBoard` deals a shuffled board and only keeps it if it is well mixed and the solver proves it winnable → tap a tube to lift it, tap another to pour (`Board.tap` decides: select / put down / pour / gentle wobble) → the pour animates, then `RoundState.poured` records it → `checkedFinishable()` runs the solver off the main thread → if the round can no longer be finished (no pour left, or pours that can't win), wait ~1s, then `undoLast()` step by step with a reverse animation back to `safeDepth`, and play continues → when every colour is gathered into one full tube (`Board.isSolved`; the same test as the finished-tube border and sparkle, so they always agree), win overlay (play again = next round on the ramp / home). Boards are dealt off the main thread, and "play again" keeps the win overlay up until the next board is ready. `RoundState.history` keeps at most `MAX_HISTORY` pours (never dropping any auto-undo could still need). Input is ignored while anything animates, is being checked or is being dealt; there is no sound, timer, score or counter.
- **Paw Kitchen loop**: new game deals customer 1 (2 ingredients, 3 tray tiles) → tap a tray tile to put that ingredient on the dish (tap its faint slot or the piece on the dish to take it off; each ingredient can be on the dish once, pieces snap to their own place so tap order never matters) → the serve button is asleep until the dish is exactly the order (`onDish == order`, extras block it) → tap serve: phase becomes `SERVING`, every ingredient/serve tap is ignored, the customer is happy for 2 s → `nextCustomer` deals a different animal and a different order (never the previous one), one step up the ramp every two customers, capped at 5 in the order and 6 in the tray. Nothing is saved; leaving and coming back starts at customer 1.
- **Paw Trace loop**: opening the game builds a `TraceSession` on the first glyph (the horizontal line) with the frog on its start; nothing loads. One full-screen touch layer sits under the glyph and the buttons and turns positions into glyph units relative to the glyph box, so a finger that lands anywhere (blank space, the side margins) and slides onto the path starts painting when it reaches it; the home and play-on buttons sit above the layer and take their own touches, so a finger that lands on them never paints. Touches go to `TraceSession.pointerDown/Move/Up`. A single finger movement makes at most 200 stamps and non-finite positions are ignored. Only the first finger paints (a finger that lands first but has never been on a path yields to a later one that lands on it, so a resting palm cannot block play). `TracePaint.stamp` paints the sample nearest the finger, plus two either side, on every unfinished stroke whose centreline is within 32dp; off the path nothing changes (no state, no penalty), and a swipe is stamped every 2 units so nothing is skipped. A stroke finishes at 85% covered: its gaps fill (250ms) with a sparkle, and the frog hops to the next unfinished stroke. When every stroke is done the phase becomes `CELEBRATING`: paint input is ignored, the frog does a happy hop, shimmer, sparkles and hearts play, and one big play-on button appears. Tapping it calls `TraceSession.playOn()`, which only works while celebrating and leaves that phase at once, so a mashed button advances exactly one glyph. `RampState.completed()` then picks the next glyph: each stage in order until every glyph in it is done once (lines and curves, shapes, digits 1-9 then 0, capital letters); from stage 2 on every third glyph is a random one from an earlier stage, never the one just played; after the 26 letters an endless random mix of all 46, never the same twice in a row. After 5 seconds with no finger down the frog walks its stroke once. Nothing is saved; leaving and coming back starts at the first glyph.
- **Exit → home**: any game's exit icon calls `onExit`, returning to the tile grid. Round/game state resets; nothing carries over between plays.

## Third-party dependencies

| Library | Purpose | Network/data access |
|---|---|---|
| *(none yet)* | | |

## Release
- **Signing**: release keystore stored as a GitHub Actions secret, never in the repo. Not yet generated — run `/deploy` when ready for a signed build; a debug build is sufficient for on-device testing until then.
- **Distribution**: built and signed from `main` via `.github/workflows/deploy.yml`; sideloaded APK onto the founder's phone. No Play Store listing planned unless the founder asks for one later.
