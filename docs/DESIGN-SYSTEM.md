# Design system

*Owned by the ui-designer agent; law for the android-developer. Created during `/kickoff` step 3.*

## Direction
<!-- The chosen visual direction in a sentence, e.g. "Bright & cartoonish: saturated primary colors, chunky rounded shapes, big friendly character." -->

## Tokens

### Color
| Token | Value | Use |
|---|---|---|
| `--primary` | | Main character/action color |
| `--secondary` | | Secondary accent |
| `--bg` / `--surface` | | Screen / card backgrounds |
| `--text` / `--text-muted` | | Copy (kept minimal — icons do most of the talking) |
| `--success` / `--try-again` | | Win state / gentle retry state (never a harsh "fail" red) |

All color pairs must stay high-contrast — a child squinting at a phone in bright sunlight is the real test, not a contrast checker alone.

### Type
| Token | Size / weight | Use |
|---|---|---|
| `--text-xl` | | Big celebratory text ("You did it!") |
| `--text-lg` | | Level/section titles |
| `--text-base` | 18sp min | Any body copy — bigger than a typical adult app, most players can't read fluently yet |

Font: a single rounded, friendly, highly legible typeface. Numerals must be unambiguous (no easily-confused 1/l/I).

### Spacing & shape
- Spacing scale: 8 / 16 / 24 / 32 / 48 / 64 (dp)
- Radius: <!-- one value, used everywhere — soft/rounded reads as friendly -->
- Touch targets: **≥48dp**, with ≥8dp gaps between adjacent tappable elements

## Component inventory
<!-- Grows as components are built. Name, where it lives, when to use it. -->

| Component | Location | Notes |
|---|---|---|
| | | |

## Voice & tone
<!-- How the game talks: e.g. "Playful and encouraging. Short phrases, simple words. Never says 'wrong' — says 'try again!'" -->

## Hard rules
- Every screen designed for a phone held one-handed by small hands, landscape or portrait per the game's needs.
- Touch targets ≥48dp; generous spacing between anything tappable.
- No screen puts a money-related action, an external link, or a destructive action (delete save) within one tap of normal play — those sit behind an adult gate.
- Loading, first-run (no save yet), and "try again" states are part of every screen's design, not afterthoughts.
