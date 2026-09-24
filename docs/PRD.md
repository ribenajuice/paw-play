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
| 10 | As a toddler, I want to tap a tube and see it react, so that I know I picked it up | Given a tube with liquid, when I tap it, then it lifts and glows; tapping it again puts it back down; tapping an empty tube first does nothing. Only one tube is selected at a time; tapping a different liquid tube while one is selected switches the selection to it | ☐ |
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

### Milestone 3 — More to explore
<!-- Sketch only. -->
- A third and fourth game.
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
- **Milestone 2, needs founder's answer before build:** the founder's decision was to rewind when "no useful pour is left". We read that broadly (story 17): the game also rewinds when pours are still possible but the round can no longer be finished, because otherwise a child could shuffle forever in a position that can't be won. Is that the intent? Default if no answer: yes, broad reading.
