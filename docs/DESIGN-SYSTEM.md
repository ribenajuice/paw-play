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
| Game tile | Home screen | Large icon-only tile, one per `GameCatalog` entry. Icon must read as "what game is this" with zero text — the toddler picks by picture. |
| Exit icon | Inside every game | The only way out of a game besides system back. Same icon, same corner, every game — consistency matters more than per-game flourish here. |

## Home screen
- One shelf, one grid of Game tiles — as many as `GameCatalog` currently has (see `docs/ARCHITECTURE.md`). No "coming soon" tiles ever ship; the grid just grows as games are added.
- Tiles are the single largest interactive element on screen — this is the one screen a toddler must navigate with zero adult help, every single time they open the app.

## Voice & tone
<!-- How the game talks: e.g. "Playful and encouraging. Short phrases, simple words. Never says 'wrong' — says 'try again!'" -->

## Paw Pour: tubes, bands and marks

Chrome (cream background, white surfaces, coral/sunshine/sky/leaf, ink, radii, 56dp exit button, 88dp win buttons, star badge) is unchanged and lives in `ui/theme/Color.kt`; Paw Pour reuses it as-is. Mockup: see the founder-approved Paw Pour artifact.

**Band colours (content colours, not theme tokens).** Seven, each with its own mark, ordered brightest to darkest so they stay apart in greyscale:

| # | Name | Hex | Mark | Mark colour |
|---|---|---|---|---|
| 1 | Sunshine | `#FFEB70` | star | ink `#2B2320` |
| 2 | Bubblegum | `#FFA0CA` | heart | ink |
| 3 | Sky | `#55BDEB` | ring | ink |
| 4 | Coral | `#F25F3F` | triangle | white |
| 5 | Leaf | `#1E9E4F` | diamond | white |
| 6 | Grape | `#5B33A8` | plus | white |
| 7 | Midnight | `#1F2F6B` | moon (crescent) | white |

Rounds use the first N colours in this order (round 1 uses 3 fixed for maximum contrast: Sunshine, Sky, Coral; the code may pick any N as long as no two share a mark). Marks are solid shapes with softly rounded corners, drawn at 60% of the smaller band side, centred, one per band. Never letters or numbers.

**Tube.** White glass body, open top with a slightly wider lip, bottom corners rounded to 40% of the body width, 3dp border, a faint white highlight down the left. Bands fill from the bottom with a 2dp light divider between bands. Border colour is the state: sunshine = resting, sky = selected, leaf = full and finished (same three states as Paw Match cards).
- Selected: lifts 16dp and gets a sky glow. No other motion is required to read selection.
- Finished tube: leaf border plus a small sunshine sparkle.
- Wrong pour: tiny wobble only. No red, no sound.

**Sizing.** The whole tube box (lip to base) is the tap area. Sides ≥16dp from the screen edge, gaps ≥12dp between tubes, 32dp between rows, 16dp headroom for the lift.
| Mocked round | Layout | Tube w x h | Band h |
|---|---|---|---|
| 1 (6 tubes, capacity 3) | 3 columns x 2 rows | 88 x 212dp | 64dp |
| 6+ cap (9 tubes, capacity 4) | rows of 5 and 4, centred | 56 x 204dp | 46dp |

Sizes above are for a 360dp-wide phone. Rounds 2-5 are not mocked: the developer picks columns and the largest tube that fits the whole board with no scrolling, the same way `AdaptiveSquareGrid` does, never below 48dp wide, band height shrinking with tube size.

**Home tile.** Paw Pour tile = three small tubes (middle one raised) with a sky badge holding a white paw, in the same bottom-end position as Paw Match's sunshine badge with a coral paw. Icon only.

## Hard rules
- Every screen designed for a phone held one-handed by small hands, landscape or portrait per the game's needs.
- Touch targets ≥48dp; generous spacing between anything tappable.
- No screen puts a money-related action, an external link, or a destructive action (delete save) within one tap of normal play — those sit behind an adult gate.
- Loading, first-run (no save yet), and "try again" states are part of every screen's design, not afterthoughts.
