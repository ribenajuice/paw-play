# Decision log (ADRs)

*Append-only. Newest first. Every significant technical or product decision gets an entry — this is how future sessions avoid relitigating settled questions.*

Format:

## YYYY-MM-DD — Short decision title
- **Context**: what forced a choice
- **Decision**: what we chose
- **Alternatives**: what we didn't, and why not
- **Consequences**: what this makes easier/harder; revisit-if condition

---

## 2026-09-23 — Adaptive column count instead of a fixed 2-column scrolling grid
- **Context**: first on-device playtest (founder, real phone) found that past 8 cards (4 pairs) the fixed-2-column grid overflowed the screen and introduced a scrollbar — not intuitive for a 4-year-old, and contrary to the "grid getting bigger is the only difficulty signal" intent in `docs/PRD.md`. The original 2026-09-22 progressive-difficulty ADR below assumed a fixed 2-column layout that was never actually correct.
- **Decision**: `PawMatchScreen` now measures its available space (`BoxWithConstraints`) and picks whichever column count from 2–4 yields the largest square card that still fits every card on screen with no scrolling, down to a 48dp floor. More pairs can mean more columns, not just more rows.
- **Alternatives**: keep 2 columns and just shrink card size — rejected once tried, cards got uncomfortably small before hitting 6 pairs on a typical phone; letting column count flex too produces meaningfully bigger touch targets at the same card count.
- **Consequences**: `bestColumnCount()` in `PawMatchGame.kt` is the one place this logic lives — any future game with a similar "N items, must fit, no scroll" screen should reuse the same approach rather than re-deriving it.

## 2026-09-22 — Progressive difficulty, capped at 6 pairs, session-scoped
- **Context**: founder wants Paw Match to get gradually harder as rounds are cleared, rather than staying at a fixed difficulty forever.
- **Decision**: each "play again" starts one pair harder than the round just won (3 → 4 → 5 → 6 pairs), capped at 6. The cap exists because 6 pairs (12 cards) is roughly where card size hits the 48dp touch-target floor on a typical phone screen even with the adaptive column layout — see the 2026-09-23 entry above and `docs/DESIGN-SYSTEM.md`. (This entry originally assumed a fixed 2-column grid; that assumption was wrong and is corrected above — the cap and pair progression themselves are unchanged.) Difficulty resets to 3 pairs each time the game is entered fresh from the home screen; it is not persisted (consistent with the standing no-persistence-in-v1 decision below).
- **Alternatives**: persisting the player's highest level reached (so re-entering resumes difficulty) — rejected for v1 as an unnecessary DataStore dependency for a game this short; revisit if it turns out to matter in practice. An uncapped difficulty curve — rejected, an ever-growing grid stops being fun and starts being a scrolling UI problem.
- **Consequences**: `PawMatchLogic.pairsForLevel()` is a pure function of level number, trivially unit-tested without touching Compose or Android at all.

## 2026-09-22 — Hub app with pluggable game modules, not a single-game app
- **Context**: founder wants an app that holds a small, growing shelf of mini-games (Kidlo-style home menu), not just the one matching game originally scoped. We won't build every game up front.
- **Decision**: one app shell (home screen tile grid) + independent game modules behind a small `MiniGame` contract (id, icon, content composable with an `onExit` callback), registered in a single static `GameCatalog`. Home screen renders one tile per catalog entry — no placeholders for unbuilt games.
- **Alternatives**: separate app per game — rejected, defeats the point of one trusted, ad-free home the founder hands over once. A single monolithic screen with a mode switch instead of a real module boundary — rejected, makes every future `/feature` (new game) a change to shared code instead of a pure addition, which is exactly the risk to design out given how often this app will grow.
- **Consequences**: adding game N+1 should touch only its own package plus one line in `GameCatalog.kt`. If a future game genuinely needs something from another game or the shell beyond the `MiniGame` contract, that's a signal to revisit this ADR, not to quietly widen the contract.

## 2026-09-22 — Kotlin + Jetpack Compose over Godot
- **Context**: Milestone 1 is a card-matching game for a 2-5 year old — grid layout, tap-to-flip, simple flip/celebration animations. No physics, no sprite-based movement, no level-by-level content pipeline.
- **Decision**: build native in Kotlin + Jetpack Compose rather than Godot.
- **Alternatives**: Godot 4 — better fit for a physics/sprite-heavy platformer, but adds an engine, a separate export pipeline, and GDScript for a game that's fundamentally a UI grid with animated state transitions, which Compose already does well. Rejected as unnecessary weight for this genre. Now doubly true with the hub decision above — Compose's navigation between composables is a natural fit for "tile grid → game → back", where Godot would mean scene-switching between an engine-level menu and each game.
- **Consequences**: smaller APK, tighter integration with Android's accessibility/input handling, easier for the architect's default no-dependency stance to hold. If a future game genuinely needs real physics/sprite gameplay, that game can still be built as a Godot project embedded via an Android View if it ever comes to that — a per-game decision, not a whole-app one.

## 2026-09-22 — No persistence in Milestone 1
- **Context**: PRD Milestone 1 has no settings, no theme choice, no progress to save — every round is self-contained.
- **Decision**: keep all round state in-memory; add DataStore only when a specific game introduces something worth remembering between sessions.
- **Alternatives**: wire up DataStore now "for later" — rejected; nothing to persist yet, and an empty persistence layer is a dependency with no payoff.
- **Consequences**: the first game (or the home screen) that needs to remember a choice is the trigger to revisit this.

<!-- Entries go below this line -->
