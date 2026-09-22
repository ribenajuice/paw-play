# Decision log (ADRs)

*Append-only. Newest first. Every significant technical or product decision gets an entry — this is how future sessions avoid relitigating settled questions.*

Format:

## YYYY-MM-DD — Short decision title
- **Context**: what forced a choice
- **Decision**: what we chose
- **Alternatives**: what we didn't, and why not
- **Consequences**: what this makes easier/harder; revisit-if condition

---

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
