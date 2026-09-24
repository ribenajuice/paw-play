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

### Milestone 2 — Second game on the shelf
<!-- Sketch only; detail when Milestone 1 ships. -->
- One more mini-game added to the home screen shelf (candidate: a shape/color sorting game — genre and theme TBD when we get there).
- Confirms the "adding a game" path from `docs/ARCHITECTURE.md` actually stays a pure addition — no changes needed to Paw Match or the home screen shell to add it.

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
