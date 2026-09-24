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

| # | Name | Hex | Mark | Mark colour | Contrast vs the one above |
|---|---|---|---|---|---|
| 1 | Sunshine | `#FFEB70` | star | ink `#2B2320` | (lightest) |
| 2 | Bubblegum | `#FFA8CE` | heart | ink | 1.48 |
| 3 | Sky | `#28ACE6` | ring | ink | 1.44 |
| 4 | Coral | `#F04520` | triangle | white | 1.46 |
| 5 | Leaf | `#177A3D` | diamond | white | 1.43 |
| 6 | Grape | `#5F36B0` | plus | white | 1.47 |
| 7 | Midnight | `#223475` | moon (crescent) | white | 1.45 |

Contrast is the WCAG luminance ratio between neighbouring rows; every pair of colours, not just neighbours, must be at least 1.3 (unit-tested in `PawPourRobustnessTest`), and every mark at least 3 against its band. Re-tuned 2026-09-25: QA had measured Coral vs Leaf 1.07, Bubblegum vs Sky 1.13 and Grape vs Midnight 1.48 with the first values (Bubblegum `#FFA0CA`, Sky `#55BDEB`, Coral `#F25F3F`, Leaf `#1E9E4F`, Grape `#5B33A8`, Midnight `#1F2F6B`), which fails greyscale. Hues and marks are unchanged; only lightness (and a little saturation) moved. The app-wide sunshine, sky and leaf state-border colours are separate and untouched.

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

**Motion (added by the developer while building).** Soft and unhurried, nothing that reads as an alarm. Lift 16dp over 180ms. Wobble: side-to-side up to 6dp, dying away over 320ms. Pour: the source lifts, tips 16 degrees toward the target and a ribbon of the colour arcs into the target while its top bands drain and the target's fill, over 520ms. Auto-undo: about 900ms after the pour lands, each pour is reversed the same way over 700ms with 200ms between. Sparkle: three small sunshine four-point stars pop in when a tube becomes full and one colour. Selected glow: four widening, fading sky outlines, no blur. Tube bounds: at most 88dp wide, bands at most 64dp tall and at most 0.85 of the tube width; the layout picks the row count that gives the tallest bands. No sound in Paw Pour, same as Paw Match.

**Home tile.** Paw Pour tile = three small tubes (middle one raised) with a sky badge holding a white paw, in the same bottom-end position as Paw Match's sunshine badge with a coral paw. Icon only.

## Paw Kitchen: dishes, ingredients and customers

Chrome is unchanged (cream background, white surfaces, coral/sunshine/sky/leaf, ink, 56dp home button, 20% corner radius on tiles). Mockup: https://claude.ai/artifact/PusWamKe8ex7eQZwEr2cm1 (private; its raw HTML holds exact path data for every drawing below, on the same 100 x 100 grid).

**Home tile.** Chef bear (Paw Match's bear face with a tall white chef hat, coral band, faint warm-grey outline so the hat shows on the white tile), about 86dp inside the 146dp tile. Badge: **coral `#FF6B4A`** with a white paw, same bottom-end corner and 22.6dp size as the others (Match = sunshine, Pour = sky, Kitchen = coral; leaf is not used for a badge because leaf means "done" inside games). With three games the shelf reflows to 2 + 1 through `AdaptiveSquareGrid`; tiles stay 146dp.

**Kitchen content colours** (not theme tokens): counter lip `#E9B274`, counter `#F6D2A2`, counter edge `#E2A868`, plate `#FFFFFF` with `#DDD3C7` rim and `#F3EDE5` well, asleep serve button `#DAD2C8`.

### Ingredient drawing rules
- 18 ingredients, each drawn on a 100 x 100 grid inside a 10..90 safe box, flat solid shapes, plus one **faint rim**: ink at 30% opacity, 3 units wide, round joins (so pale things like cheese, dough and the bunny read on white and cream). Small marks (seeds, spots, grill lines) have no rim.
- No two ingredients in the same dish share a silhouette; brightness also differs, so a greyscale check passes. Pieces are pictures only, never letters or numbers.
- Tray icon = the same drawing used in the bubble and on the tray tile, at different sizes. On the dish each ingredient has a **dish piece** (a wider, flatter or repeated version), described below.

| Dish | Ingredient (tray icon silhouette and colours) |
|---|---|
| Burger (side view) | **Bun** domed crown over a low heel with a gap, `#F0A94A` / `#E39A3B`, 6 sesame ellipses `#FFF3D6`. **Patty** fat pill 84x48, `#7B4A2B`, 3 grill strokes `#4A2914`. **Cheese** wide square slice with one drip tongue, tilted -8 degrees, `#FFC83A`, holes `#E8A400`. **Tomato** circle r35 `#F2543A` with green 5-point calyx star `#2E9E4E`. **Lettuce** ruffled fan with scalloped bottom, `#6FCB5A`, veins `#4FA83E`. **Pickle** diagonal capsule 88x36, `#4E9A3A`, light bumps `#A6D986`. |
| Pizza (top view) | **Dough** big disc r43 with crust ring, `#E5B676` / `#F6DDB0`. **Sauce** splat: circle with 7 wavy lobes (r = 36 + 4.5 sin 7 theta), `#E63C2C`, swirl `#FF9078`. **Cheese** wedge with holes, `#FFD23F`, light top strip `#FFE98A`. **Mushroom** cap-and-stem silhouette `#F3E3CC`, tan cap `#C9976A`, 3 spots. **Olive** donut r38 with r14 hole, `#4B5A2E`, white arc highlight. **Pepperoni** disc r38 `#B8301F` with 6 cream flecks `#F4C3A0`. |
| Ice cream (side view) | **Cone** downward triangle (20,22) (80,22) (50,96) with waffle lines `#B8762F`, rim band `#F0C07C`, body `#E6A85C`. **Strawberry scoop** dome with a 3-scallop skirt, `#FF9EC4`, 7 tiny teardrop seeds `#D63A78`. **Chocolate scoop** same dome with a 5-point zigzag skirt, `#7A4B2B`, 6 light triangle chips `#EBC790` (differs from strawberry by skirt, seeds vs chips, and dark vs light). **Cherry** pair of r20 circles `#D62839` joined by stems and a leaf. **Sprinkles** scatter of 10 capsules 18x7 in coral, sky, sunshine, leaf, pink. **Wafer** tilted (28 degrees) 40x92 rectangle with grid lines, `#F4D8A0`. |

### Dish pieces (200 x 160dp canvas, plate at the bottom)
A dish in content is: a **view** (`stack` seen from the side, or `top`), the base ingredient, and per ingredient a piece type. Pieces draw in shelf order, so the dish always looks like the bubble.
- **Slab** (burger, cone, scoops): height in dp, stacks on the running top, overlapping the one below by 2dp (scoops by 12dp so they nest). Burger heights: heel 18, patty 20, cheese 7 with two drips, tomato 11, lettuce 10 with a wavy top, pickle 7 (three coins). The bun is a **pair**: heel first at the bottom, crown (40 tall dome) always drawn last on top, so `bun` alone reads as a whole bun and a bun with toppings is a closed burger. Widths 116..132 of 200.
- **Overlay** (sprinkles, wafer, cherry): no height. Cherry sits on the top scoop; sprinkles scatter over the top scoop; wafer leans out of the top scoop's right side, drawn behind the scoops.
- **Slots** (pizza toppings): fixed positions on the pie, centre (100,80): dough r62, sauce r47, cheese r41 stacked concentric; pepperoni x3 at (76,66) (124,66) (100,108), mushroom x3 at (100,52) (72,96) (128,96), olive x3 at (100,80) (64,80) (136,80), drawn at 28 / 28 / 20dp. A missing ingredient leaves no gap that looks broken.
- Ice cream sits in a small white stand so the cone tip has somewhere to rest. Everything, all six at once, fits the canvas (checked for all three dishes in the mockup sheet).
- Adding a dish later means: a view, up to 6 tray icons, and one piece per ingredient. Nothing else in the layout changes.

### Play screen layout (360 x 692dp play area, portrait)
| Element | Size / position |
|---|---|
| Home button | 56dp, top-left, 20dp from edges (same as Paw Pour) |
| Order bubble | 252 x 68dp, right of the home button, white, radius 26, two small tail dots pointing to the customer. Pictures 56dp for 2 to 4 items, 44dp for 5. Left to right = bottom to top of the dish |
| Customer | 120dp critter box (head about 76dp across), centred, paws resting on the counter edge |
| Counter | full width band from y 198 to 392; dish on the left (20..220), serve button on the right |
| Serve button | 88dp circle at x 240..328, y 256..344 |
| Tray tiles | **96 x 96dp** (target was 72), white, radius 19, 3dp border, 12dp gaps, icon 68dp. Layouts: 3 = one row of 3; 4 = 2 + 2; 5 = 3 + 2; 6 = 3 + 3. The block is centred vertically in y 404..688, so it never scrolls and never moves the counter |

If a phone is shorter or narrower than 360 x 692dp, scale the tiles down uniformly (never below 72dp; below that the developer should tell the ui-designer). All tray tiles, serve and home stay at least 48dp.

### States
- **Tray tile:** resting = white, sunshine border. Finger down = sky border, sky glow, scale 0.96. On the dish = transparent, dashed `#DCCFC0` border, icon at 20% (still a live 96dp target: tap it to take the ingredient off, because slabs like cheese are only 7dp tall on the dish). Tapping the piece on the dish also removes it (topmost piece wins).
- **Bubble picture:** on the dish = 3dp leaf border, leaf glow, scale 1.05; not yet = plain. Extras never change anything.
- **Serve button:** asleep = `#DAD2C8`, white cloche at 75%, closed-eye arcs, no motion, no press effect. Awake = leaf green, full white cloche, open eyes and smile, 7dp hop every 1.1s, leaf halo, 3 sunshine sparkles.
- **Customer:** waiting = dot eyes and a small smile. Happy = squeezed-shut arched eyes, open smile with tongue, pink cheeks, bounce (10dp up, 4 degrees tilt, 1s). Heads carry the same faint rim as ingredients so the pale bunny and cat hold against cream.
- **Serve reaction (about 2s):** dish slides to the customer's centre, sunshine 4-point sparkles and coral hearts float around the head, tray dims to 50% and ignores taps, serve button hides, bubble stays with every picture glowing. No coins, no stars, no score, no words.

### Customers
The six Paw Match critters (fox, bear, bunny, owl, cat, frog) built with the same `CritterFace` recipe on the 80 x 80 grid, plus a mouth: waiting `M34,58 Q40,63 46,58` stroke 2; happy = closed eyes `M27,44 Q32,37 37,44` and `M43,44 Q48,37 53,44` stroke 3, open mouth `M32,56 Q40,68 48,56 Z` fill `#B8301F`, tongue `#FF8FA8`, blush circles r4.6 at (24,52) and (56,52). Shown 1.5x on screen (120dp box). Paws: two ellipses 28 x 20dp in the head colour at x = centre plus or minus 52. Ear families: pointed (fox, cat), round (bear, owl), floppy (bunny), none (frog).

### Decisions awaiting founder OK (mockup review)
Coral badge; pizza drawn from above while burger and ice cream are side-view stacks; tapping the faint tray slot removes an ingredient (in addition to tapping it on the dish); 96dp tray tiles.

## Hard rules
- Every screen designed for a phone held one-handed by small hands, landscape or portrait per the game's needs.
- Touch targets ≥48dp; generous spacing between anything tappable.
- No screen puts a money-related action, an external link, or a destructive action (delete save) within one tap of normal play — those sit behind an adult gate.
- Loading, first-run (no save yet), and "try again" states are part of every screen's design, not afterthoughts.
