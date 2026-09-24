# PRD: Paw Play

*Owned by the product-manager agent. Written during `/kickoff`, updated by `/feature`. Keep under 2 pages.*

## Problem
Parents of toddlers who want screen time to be calm, safe, and self-contained have few options that aren't riddled with ads, surprise purchase prompts, or timers/fail states that frustrate a 2-5 year old. The founder wants a single app their son can open on his own — including with the phone handed over and the parent not watching — that offers a small, growing shelf of simple games, with zero risk of him tapping into a purchase, an ad, or an external link from any of them.

## Users
- **Primary**: the founder's son, age 2-5. Limited or no reading. Taps and drags, not menus. Gets frustrated by failure states, timers, or anything that punishes exploration.
- **Secondary**: the founder, as the one who hands over the phone and needs zero-oversight confidence that nothing bad can happen, in any game, ever.

## The one thing
A toddler can open the app to a picture-only home screen, tap a game tile, play that game to a satisfying completion, and get back to the home screen to pick another — with no reading required anywhere and no way to reach anything money-related, an ad, or an external link, from the home screen or from inside any individual game.

## Product shape
This is a **hub app**: one home screen showing a shelf of game tiles (icons only, no labels needed), each tile launching a self-contained mini-game. We are not building every game up front — Milestone 1 ships the hub with exactly **one** working game (the matching game originally scoped, now the first tile). Each future `/feature` adds one more game to the shelf; the hub itself doesn't change shape to do that. See `docs/ARCHITECTURE.md` for how a new game gets added without touching the others.

**Long-term target: roughly a dozen small games on the shelf**, built up one at a time over many `/feature` runs, not a fixed roadmap set now. The home screen's tile grid is built to reflow into more columns as tiles are added (see `docs/ARCHITECTURE.md`'s `AdaptiveSquareGrid`), so this was accounted for from the shelf's first version rather than something to rework later at tile #5 or #10.

## Milestones

### Milestone 1 — Walking skeleton
*The thinnest end-to-end slice a real user could touch: hub + one game.*

| # | Story | Acceptance criteria | Status |
|---|---|---|---|
| 1 | As a toddler, I want to see a picture-only home screen when I open the app, so that I can pick something to play without reading | Given the app opens, when the home screen renders, then it shows one or more large tappable game tiles as icons, with no body text required to understand what to do | ☐ |
| 2 | As a toddler, I want tapping a game tile to take me straight into that game, so that I can start playing immediately | Given the home screen, when I tap the Paw Match tile, then the Paw Match game opens ready to play, no loading screen with text, no confirmation dialog | ☐ |
| 3 | As a toddler, I want to tap a card to reveal what's under it, so that I can find its match | Given the Paw Match game screen, when I tap a face-down card, then it flips to show a friendly animal icon; tapping an already-revealed card does nothing | ☐ |
| 4 | As a toddler, I want matched pairs to celebrate, so that I know I did something right | Given two revealed cards showing the same animal, when they're the second tap of the pair, then both play a short celebration (animation + happy sound) and stay face-up/locked | ☐ |
| 5 | As a toddler, I want a mismatch to be gentle, not punishing, so that I'm not afraid to try | Given two revealed cards showing different animals, when a short pause elapses, then both flip back face-down with no negative sound, no penalty, no counter of "wrong" attempts | ☐ |
| 6 | As a toddler, I want to know when I've won, so that the game feels complete | Given all pairs are matched, when the last pair locks in, then a full-screen "you did it!" celebration appears with one large icon button to play again and one large icon button back to the home screen | ☐ |
| 7 | As a toddler, I want the next round to feel like a fresh little challenge, so that the game stays interesting as I get better at it | Given I tap "play again" after winning, when the new round starts, then it has one more pair than the round I just won (3 → 4 → 5 → 6), capped at 6 pairs so the grid never gets too big for the screen or my patience | ☐ |
| 8 | As a parent, I want it structurally impossible to reach a purchase, ad, or external link anywhere in the app — hub or any game, so that I never have to supervise | Given any screen in the app, there is no button, menu, or gesture that opens a purchase flow, an ad, or a browser/external app | ☐ |

Difficulty is **session-scoped, not saved**: leaving Paw Match for the home screen and tapping back in starts over at 3 pairs (consistent with the no-persistence decision in `docs/DECISIONS.md`). No numeric level indicator — the grid getting visibly bigger is the only signal a toddler needs; reading a number isn't required anywhere.

### Milestone 2 — Second game on the shelf: Paw Pour
*A liquid-pouring sort puzzle (the founder's son already likes the genre). Working name **Paw Pour**, chosen by the team at the founder's request; renaming is a content-only change. Also proves the "adding a game" path in `docs/ARCHITECTURE.md` is a pure addition: a new game package plus one `GameCatalog` line, with no changes to Paw Match or the hub shell.*

Vocabulary: a **tube** holds a stack of coloured **bands**; a **pour** moves the top colour from one tube to another.

| # | Story | Acceptance criteria | Status |
|---|---|---|---|
| 9 | As a toddler, I want a new picture tile on the home screen for the pouring game, so that I can find it and start | Given the home screen, when it renders, then a second icon-only tile (tubes with coloured liquid) appears next to Paw Match; tapping it opens Paw Pour straight into round 1, with no text, dialog or loading screen. Adding it required no change to Paw Match or the home screen beyond the tile | ☐ |
| 10 | As a toddler, I want to tap a tube and see it react, so that I know I picked it up | Given a tube with liquid, when I tap it, then it lifts and glows; tapping it again puts it back down; tapping an empty tube first does nothing. Only one tube is selected at a time. Tapping a different tube while one is selected is a pour attempt (story 11/12): if legal it pours, otherwise the target wobbles and the selected tube sets back down; it never switches the selection directly | ☐ |
| 11 | As a toddler, I want to tap a second tube to pour into it, so that I can sort the colours without dragging | Given a tube is selected, when I tap a target tube where the pour is legal, then the top colour flows across with a soft pour animation and sound, and the source tube goes back down. **Legal pour**: the target is empty or its top band is the same colour, and it has room. The whole run of matching top bands moves as far as the room allows. A pour that changes nothing useful (emptying a tube that is already a single colour into an empty tube) counts as not legal | ☐ |
| 12 | As a toddler, I want a wrong tap to be gentle, so that I'm never scolded for trying | Given a tube is selected, when I tap a target where the pour is not legal (different colour, or full), then the target gives a tiny wobble and the selected tube sets back down. There is no error sound, no red, no penalty, and no count of wrong tries | ☐ |
| 13 | As a colour-blind child, I want each colour to also have its own simple mark, so that I can tell colours apart without seeing hue | Given any round, when bands are drawn, then every colour carries a distinct large pattern or shape mark (e.g. dot, stripe, star, heart), and no two colours in the game share a mark. Also, colours differ enough in brightness that they stay distinguishable in greyscale. Marks are pictures, never letters or numbers. (Colours are on-screen only; the ui-designer picks the set.) | ☐ |
| 14 | As a toddler, I want every round to be finishable, so that I can never get truly stuck by the game's own fault | Given any round the game starts, then it can be solved from its starting position, never starts already solved, and never starts with a tube that is already complete. Bands are mixed up so each round is a real puzzle | ☐ |
| 15 | As a toddler, I want to know when I've finished, so that it feels complete | Given every colour is gathered into one full tube (every non-empty tube is full and a single colour; a colour split across two part-full tubes is not yet a win), when the last pour lands, then a full-screen "you did it!" celebration (same look and pattern as Paw Match) appears with one large play-again icon button and one large home icon button. Finishing one tube may also get a small sparkle | ☐ |
| 16 | As a toddler, I want the next round to be a bit bigger, so that it stays interesting | Given I tap play again, when the next round starts, then it follows the ramp table below and never exceeds the cap. Round 1 has 6 tubes. The round resets to round 1 when I leave to the home screen and come back (session-only, nothing saved); no number or level is shown | ☐ |
| 17 | As a toddler, I want the game to quietly fix things if I paint myself into a corner, so that I never lose or see a failure | Given a position where the round can no longer be finished (see "Stuck" below), when it happens, then after a short pause the last pour(s) gently un-pour with a soft animation back to the latest position that can still be finished, and play continues. No message, no failure sound, no icon, no counter. Nothing the child does can end a round except finishing it | ☐ |
| 18 | As a parent, I want the standing rules to hold in this game too, so that I never have to watch | Given any Paw Pour screen, then there is no text needed to play, no timer, no score, no moves counter, and no ad, purchase, login or external link (standing rule, see `CLAUDE.md`). Mashing the screen or multi-touch during a pour never breaks the game; extra taps mid-animation are simply ignored | ☐ |

**Difficulty ramp** (tube capacity = bands per tube; every colour has exactly that many bands, so a finished tube is always full; always 2 spare empty tubes except round 1):

| Round | Tubes | Colours | Capacity | Empty at start |
|---|---|---|---|---|
| 1 | 6 | 3 | 3 | 3 |
| 2 | 6 | 4 | 3 | 2 |
| 3 | 7 | 5 | 3 | 2 |
| 4 | 8 | 6 | 3 | 2 |
| 5 | 8 | 6 | 4 | 2 |
| 6 and up (cap) | 9 | 7 | 4 | 2 |

Rationale: round 1 has three spare tubes so a 2-year-old can succeed almost by accident; each step adds one thing at a time. The cap is 9 tubes so all tubes fit on a phone screen with no scrolling and touch targets of at least 48dp. Seven colours is the most that each get a clearly distinct colour plus mark.

**Stuck, in player terms:** the child cannot finish the round from where they are. That covers "no legal pour remains" and also "pours exist but they only shuffle things around and the round can no longer be won". The game notices after the child's pour lands, waits about a second so they see what they did, then rewinds.

**Out of scope for Milestone 2 (v2):** a manual undo or restart button (auto-undo covers it), hints, per-tube themes, saved progress, more than 9 tubes, and any sound/music setting.

### Milestone 3 — Third game on the shelf: Paw Kitchen
*The founder's son loves cooking, so: a tiny restaurant. A friendly animal customer shows up and orders a dish as a **picture bubble**; the child builds it from ingredients and serves it. Working name **Paw Kitchen**, chosen by the team; renaming is a content-only change. Like Paw Pour, it must be a pure addition per `docs/ARCHITECTURE.md`: a new game package plus one `GameCatalog` line, no change to Paw Match, Paw Pour or the hub shell.*

Vocabulary: an **order** is a dish plus the set of ingredients in its bubble; the **dish** is what the child is building on the counter; the **tray** is the row/grid of ingredients to tap.

**Menu (three dishes, each with a 6-ingredient shelf; the base is always in the order).** Burger: bun, patty, cheese, tomato, lettuce, pickle. Pizza: dough, sauce, cheese, mushroom, olive, pepperoni. Ice cream: cone, strawberry scoop, chocolate scoop, cherry, sprinkles, wafer. An ingredient appears at most once per order; there are no quantities to count. **The founder confirmed this starting menu (2026-09-24) and asked that more dishes can be added later:** a dish is defined as content (a base, a shelf of up to 6 ingredients, and their pictures), so adding pancakes, sandwiches, soup or cupcakes later needs no change to how orders, the tray, serving or the ramp work, and never touches the other games.

| # | Story | Acceptance criteria | Status |
|---|---|---|---|
| 19 | As a toddler, I want a third picture tile on the home screen for the cooking game, so that I can find it and start | Given the home screen, when it renders, then a third icon-only tile (a friendly animal in a chef hat with a dish) appears after Paw Pour; tapping it opens Paw Kitchen straight to the first customer, with no text, dialog or loading screen. Adding it required no change to Paw Match, Paw Pour or the home screen beyond the tile. A large home icon button is on screen at all times, in the same corner as the other games, and returns to the home screen | ☐ |
| 20 | As a toddler, I want a customer to walk up and show me what they want as pictures, so that I know what to make without reading | Given the game starts or the previous customer has left, when a customer animal arrives, then a thought bubble shows the order as a row of ingredient pictures (no words or numbers), in the same bottom-to-top order the dish will build. The dish and ingredients are chosen at random within the current ramp step, and the order is never identical (same dish and same ingredient set) to the previous customer's. Each customer is a different animal from the one before | ☐ |
| 21 | As a toddler, I want to tap an ingredient to put it on my dish and tap it again to take it off, so that building is easy and I can change my mind | Given the tray, when I tap an ingredient, then it hops onto the dish, snapped into its correct place (base at the bottom, the rest stacked above it in the bubble's order), with a soft pop sound; its tray slot stays in place as a faint outline. When I tap an ingredient on the dish, then it hops back to the tray. Any ingredient can be added or removed at any time, including ones not in the order; there is no drag, no long-press, no text. A picture in the bubble gets a soft glow while that ingredient is on the dish, so a non-reader can see what is still missing. Extras never trigger any warning, sound or colour | ☐ |
| 22 | As a toddler, I want the tray to be easy to see and hit, so that I can build without frustration | Given any customer, then the tray shows exactly the "tray choices" for the current ramp step (3 to 6), all on screen with no scrolling, each ≥48dp in both directions (target ≥72dp, the ui-designer sizes to fit) with ≥8dp gaps. The tray holds only the current dish's ingredients: every ingredient in the order plus extras from the same dish's shelf, so every order is always makeable from its tray | ☐ |
| 23 | As a colour-blind child, I want ingredients to look different by shape, so that I never have to rely on colour | Given any tray, then no two ingredients share a silhouette, and each stays identifiable in greyscale (the two ice-cream scoops differ by a mark, e.g. seed dots vs. chocolate chips, not just pink vs. brown). Ingredients are pictures only, never labelled with letters or numbers | ☐ |
| 24 | As a toddler, I want a serve button that wakes up when my dish is right, so that I know when to hand it over | Given the dish, when it does not exactly match the order (something missing or an extra on it), then the serve button is visibly asleep (dimmed, still, no press effect) and tapping it does nothing, silently. When the dish exactly matches, then the button wakes with a gentle bounce and a soft chime; removing an ingredient puts it back to sleep. Nothing ever says "wrong" — there is no way to serve the wrong dish | ☐ |
| 25 | As a toddler, I want the customer to be happy when I serve them and then a new one to come, so that it feels good and keeps going | Given a matching dish, when I tap serve, then the dish slides to the customer, who reacts happily (bounce, hearts or big smile, happy sound) with a small sparkle celebration for about 2 seconds; the customer then leaves happily and the next customer arrives and orders. There is no game-over, no score, coins, tips or stars, no timer, no waiting customer and no unhappy or leaving-angry customer, ever. Play continues until the child taps the home icon; leaving and coming back starts again at the first customer (session-only, nothing saved) | ☐ |
| 26 | As a toddler, I want the orders to grow a little as I keep serving, so that it stays interesting | Given I keep serving customers, then orders follow the ramp table below and never exceed its cap. Customer 1 is always simple. No number or level is shown | ☐ |
| 27 | As a parent, I want the standing rules to hold in this game too, so that I never have to watch | Given any Paw Kitchen screen, then no text or reading is needed, and there is no timer, score, currency, lose state, ad, purchase, login or external link (standing rule, see `CLAUDE.md`) | ☐ |
| 28 | As a small child who mashes the screen, I want the game to stay calm, so that it never breaks or gets stuck | Given rapid or multi-finger tapping, then the game stays consistent: an ingredient is on the dish at most once; extra taps during the serve reaction are ignored; double-tapping serve serves once and brings exactly one next customer; the home icon works at every moment | ☐ |

**Order-of-stacking decision: free.** The child chooses *which* ingredients, not *in what order*; the game snaps each one into its correct spot. A 2-year-old cannot judge or fix a sequence, and a hidden order rule would create a silent "wrong" that nothing on screen can explain, which breaks the no-scolding decision. The bubble still shows the finished order as a row so the dish looks like the picture.

**Difficulty ramp** (by customers served this session; the dish is picked at random from all three from customer 1; "in order" counts the base, e.g. a 2-ingredient order is base plus one topping; "tray" is how many ingredients are on screen):

| Customers | Ingredients in order | Tray choices | Extras in tray |
|---|---|---|---|
| 1–2 | 2 | 3 | 1 |
| 3–4 | 3 | 4 | 1 |
| 5–6 | 4 | 5 | 1 |
| 7 and up (cap) | 5 | 6 (whole shelf) | 1 |

Rationale: starts at two picture-matches, adds one thing every two customers, and caps at five (the founder's "about 5"). A tray of one extra keeps it makeable and gently tests looking, without ever needing a wrong answer. Six tap targets fit on a phone in two rows of three at ≥72dp with no scrolling.

**Out of scope for Milestone 3 (v2):** unlockable or purchasable dishes and kitchens (never: standing rule), mixed-dish trays, cooking steps (chopping, timing, stirring), drag-and-drop, ingredient quantities ("two patties"), free-play with no customer, saved progress, and any sound/music setting.

### Milestone 4 — Fourth game on the shelf: Paw Trace (shape-tracing game)
*Stories numbered 29-38 provisionally; renumber when branches are integrated. "Paw Trace" is a working name and the tile's icon is a picture — renaming is a content-only change (game `id` stays `paw-trace`).*

**Problem it solves:** a 2-5 year old needs low-pressure practice steering a finger with intent (hand-eye coordination, the same skill behind holding a crayon), and there is no "wrong" way to practise it. **Founder decisions (settled):** (1) paint colours only while the finger is on the path; off the path nothing happens, and he can lift and continue; (2) content ramps shapes, then numbers, then letters, purely by playing on — no locks, no visible levels, session-only (leaving resets to the start).

| # | Story | Acceptance criteria | Status |
|---|---|---|---|
| 29 | As a toddler, I want a Paw Trace tile on the home screen that opens straight into tracing, so that I can start with one tap | Given the home screen, when it renders, then a picture-only Paw Trace tile (a paw leading a painted squiggle) is on the shelf; when I tap it, then the first glyph is on screen ready to trace, with no loading text or dialog. Given I am in the game, then the shared home icon is always visible in its usual corner and returns to the shelf from any moment, mid-stroke included | ☐ |
| 30 | As a toddler, I want to see what to trace and where to begin without being told, so that I can just start | Given a glyph is showing, then it appears as a soft, wide, pale guide path filling most of the screen width, with a friendly animal (or glowing dot) bobbing gently at the start of the next unpainted stroke. Given I have not touched for ~5 seconds, then the marker slowly walks along the stroke once to show which way to go, and repeats after each further pause. No text, no numbers, no arrows that need interpreting | ☐ |
| 31 | As a toddler, I want colour to appear only where my finger is on the path, so that nothing I do is ever wrong | Given my finger is on the guide path, when I drag, then bright paint fills the path under and behind it. Given my finger is off the path, then nothing happens: no colour, no red, no error sound, no restart, no counter. Given I lift my finger, when I touch down anywhere on the path again (not only where I stopped), then painting resumes and earlier paint stays | ☐ |
| 32 | As a toddler, I want letters and numbers with more than one line to work just as easily, so that E, 4 or A aren't harder to play | Given a glyph with several strokes (e.g. E, F, H, 4, A), then all strokes show as guide at once; I can paint them in any order and either direction, and the marker always points to the start of the next unfinished stroke and hops there when a stroke completes. Given I lift my finger between strokes, then nothing is lost and no "stroke order" error exists | ☐ |
| 33 | As a toddler, I want to see my painting fill the path and know when a line or shape is finished, so that I feel progress | Given I paint, then the guide visibly turns to colour as covered. Given a stroke is at least **85% covered** (measured along its length), then the remaining gap fills itself with a short sparkle and a gentle chime, and the marker moves on. Given every stroke is done, then the glyph is complete and its colour is full and bright. The child never has to reach exact ends or corners | ☐ |
| 34 | As a toddler, I want my finger not to hide what I'm tracing, so that I can see where to go next | Given the path is 56dp wide or more (a fingertip is about 40dp), when I drag, then guide pixels remain visible around my finger; the paint has a bright soft glow so progress shows past the edge of the finger; and the marker sits at the next unpainted spot ahead of my paint, so it is not under the fingertip. The glyph never sits under the home icon or play-on button | ☐ |
| 35 | As a toddler, I want a happy celebration when I finish, and to move on when I'm ready, so that it feels good and I'm in control | Given a glyph completes, then it plays the same style of short celebration and happy sound as Paw Match's and the other games' (glyph bounces and shimmers, animal does a happy hop, ~2s), and one large play-on icon button appears (not auto-advance). Given I tap it, then the next glyph appears. Why a button: a toddler is often still touching or admiring the finished shape, and an automatic switch would yank it away and start painting under a resting finger. Given I mash the button, then it advances exactly one glyph, because it only exists after a real completion | ☐ |
| 36 | As a toddler, I want the shapes to get a little harder as I keep playing, so that it stays interesting without me choosing anything | Given I complete glyphs, then content follows the ramp below with no level number, lock, banner, or text; stage changes are invisible. Given I leave to the home screen and re-enter, then I start again at the first glyph (nothing saved) | ☐ |
| 37 | As a toddler with imprecise fingers, I want it to work however I hold the phone, so that clumsy touches don't break it | Given a typical 360dp-wide portrait phone, then paint counts anywhere within 32dp of the path centreline (a 64dp-wide forgiving corridor, above the 48dp floor), and neighbouring strokes never overlap except where the glyph joins them. Given several fingers or a resting palm, then every touch paints only where it lies on the path and any other touch does nothing (no crash, no error, no stuck state). Given one-handed play in portrait, then the glyph, play-on button and painting area are reachable by a thumb and nothing needs landscape or a second hand | ☐ |
| 38 | As a parent, I want Paw Trace to obey every house rule, so that I never supervise | Given any Paw Trace screen, then there is no ad, purchase, currency, login, network call, or external link; no score, stars, timer, or accuracy readout; no lose or failure state; and no text a child must read — tracing a letter or digit is an activity, and he is never asked to name or pick one. Given the code, then the game is a new package plus one `GameCatalog` line and changes no other game or hub code | ☐ |

**Content ramp** (all glyphs are a single-colour outline guide; one glyph at a time; uppercase letters only):

| Stage | Glyphs (in this order) | Count | Advances when |
|---|---|---|---|
| 1 Lines and curves | horizontal line, vertical line, slanted line, arch (rainbow curve), wave | 5 | each completed once |
| 2 Shapes | circle, square, triangle, cross (+), heart | 5 | each completed once |
| 3 Numbers | 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 (counting order; 0 last) | 10 | each completed once |
| 4 Letters | I L T H E F, then A V W M N Z K X Y, then O C U J D P B R G S Q (straight strokes first, curvy last) | 26 | after all 26, play on forever in a random mix of all 46, never the same glyph twice in a row |

- **Mixing:** from stage 2 on, every third glyph is a random earlier-stage glyph (never the one just played) as an easy win; only the "new" glyphs count toward advancing.
- **Why uppercase only:** capitals are made of simpler strokes and are what children meet first; lowercase and stroke-order rules are later.
- **Roughly:** stage 1-2 take ~10 completions, so a child who plays about 20 glyphs reaches letters; a short session may stay in shapes or numbers.

**Cut from Paw Trace v1 (v2 or later):** lowercase; spoken letter/number names; tracing his own name; free drawing; saved progress or picking a glyph; enforced stroke order; accuracy or "how well you traced" feedback; per-glyph colour/brush choices; optional idle sounds.

### Milestone 5 — More to explore
<!-- Sketch only. -->
- A fourth game and beyond.
- Possibly a per-game card-set/theme picker (animals, shapes, vehicles) using pictures, not text, inside Paw Match specifically.
- Optional gentle background music with a single obvious on/off icon toggle, app-wide.

## Explicitly out of scope (v1)
- Ads, in-app purchases, virtual currency, and any account/login system. Standing rule for this project, not a v1-only cut.
- Timers, scores, leaderboards, or anything that ranks/pressures performance, in the hub or in any game.
- Any lose state, in the hub or in any game. Nothing in this app can be "lost" — only completed at whatever pace the child sets.
- Text-heavy UI, anywhere. No screen should require reading to progress.
- Network access of any kind.
- "Coming soon" / locked tiles on the home screen. A game either exists and shows a tile, or doesn't exist yet and shows nothing — never a tile that leads nowhere or looks broken when tapped.

## Open questions
- None blocking Milestone 1. App name defaulted to **Paw Play** (the hub) with **Paw Match** as the first game inside it — founder can redirect either name at any time; it's a content change, not an architecture one.
- Card theme for Paw Match itself defaulted to friendly animals — same, a pure content swap.
