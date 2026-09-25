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
- No two ingredients in the same dish share a silhouette; brightness also differs, so a greyscale check passes. **This is measured, not eyeballed:** every icon paints an outline shape defined in `KitchenShapes.kt`, and `KitchenMenuTest` requires every pair of outlines in a dish to overlap by at most 80% (intersection over union). Today's worst pairs are lettuce/bun 0.75 and mushroom/pepperoni 0.70. The strawberry and chocolate scoops (0.90) are the one exempt pair: they differ by their skirt and by seeds vs chips. Pieces are pictures only, never letters or numbers.
- Tray icon = the same drawing used in the bubble and on the tray tile, at different sizes. On the dish each ingredient has a **dish piece** (a wider, flatter or repeated version), described below.

| Dish | Ingredient (tray icon silhouette and colours) |
|---|---|
| Burger (side view) | **Bun** domed crown over a low heel with a gap, `#F0A94A` / `#E39A3B`, 6 sesame ellipses `#FFF3D6`. **Patty** fat pill 84x48, `#7B4A2B`, 3 grill strokes `#4A2914`. **Cheese** wide square slice with one drip tongue, tilted -8 degrees, `#FFC83A`, holes `#E8A400`. **Tomato** circle r35 `#F2543A` with green 5-point calyx star `#2E9E4E`. **Lettuce** tall ruffled fan with scalloped bottom (stretched 15% taller so it cannot be mistaken for the patty pill), `#6FCB5A`, veins `#4FA83E`. **Pickle** diagonal capsule 88x36, `#4E9A3A`, light bumps `#A6D986`. |
| Pizza (top view) | **Dough** big disc r43 with crust ring, `#E5B676` / `#F6DDB0`. **Sauce** WIDE splat: an ellipse 47 across by 27 tall with 7 wavy lobes (r = 47 + 4 sin 7 theta, height squashed to 27/47), `#E63C2C`, swirl `#FF9078`. **Cheese** wedge with holes, `#FFD23F`, light top strip `#FFE98A`. **Mushroom** cap-and-stem silhouette `#F3E3CC`, tan cap `#C9976A`, 3 spots. **Olive** TALL oval ring, 21 wide by 41 tall radius, `#4B5A2E`, white oval hole 8 x 14, white highlight arc down the left. **Pepperoni** plain round disc r34 `#B8301F` with 6 cream flecks `#F4C3A0`. |
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

## Paw Trace: guide, paint, markers and glyphs

Chrome is unchanged (cream background, white surfaces, ink, 56dp home button, 88dp play-on button, 29dp tile corners). Mockup: https://claude.ai/artifact/EfRW97F8Y27HtWMzp33EPP (private; its raw HTML holds exact geometry). Every glyph is drawn from the data in `docs/glyphs/paw-trace-glyphs.md`. Working name and tile art are content: the game id stays `paw-trace`.

### Home tile and badge
- Fourth tile, icon only: a wavy line (path `M10,60 C16,28 30,28 37,60 C44,92 57,92 64,60 C71,28 84,28 90,60` on a 100-unit grid, drawn about 112dp wide inside the 146dp tile, nudged up 4dp). Guide band 14 units with a 2-unit edge, the first 54% of its length painted grape with the glow, and a **frog** (marker face, 22 units, white ring 12 units radius, sunshine halo 15 units at 28%) sitting at 68% of the length, just ahead of the paint. Two small sunshine sparkles beside the frog.
- Badge: **grape `#8E59E6`** with a white paw, same bottom-end corner and 22.6dp size as the others. Match = sunshine, Pour = sky, Kitchen = coral, Trace = grape. Leaf is not used for a badge because leaf means "done" inside games. (This is a badge and content colour, not a theme token; it is brighter than Paw Pour's grape band `#5F36B0` and the two never appear together.) With four games the shelf reflows to 2 x 2; tiles stay 146dp.

### Play screen layout (360 x 692dp play area, portrait)
| Element | Size / position |
|---|---|
| Home button | 56dp, top-left, 20dp from edges, white circle, ink home glyph (same as every game). Always visible, on the celebration too |
| Glyph box | **320 x 320dp** reference box, left 20dp, top 186dp (centre at 50% of the play area). One glyph unit = 3.2dp. Nothing but the glyph and the frog is drawn here |
| Play-on button | 88dp leaf `#35C46B` circle with a white 46dp paw, horizontally centred, top 560dp (bottom 648dp). Exists only after a glyph completes |
| Corridor of touch | the whole glyph box takes touches; only points within 32dp of a centreline paint |

The glyph never sits under the home button (its top edge is 110dp below it) or the play-on button (54dp gap). On a phone whose play area is not 360 x 692dp: keep the glyph box centred at 50% of the play area, scale it uniformly to fit with 20dp side margins and at least 16dp clear of the home button and the play-on button, **never below 288dp**, and keep every dp measure below (band, corridor, finger, marker) fixed, because they are sized for a fingertip and not for the picture. If it cannot fit 288dp, tell the ui-designer.

### Guide, paint and glow
| Layer | Size | Colour |
|---|---|---|
| Guide edge | band + 3dp each side = 62dp, round caps and joins | `#A98BE6` |
| Guide fill | **56dp** wide band (17.5 glyph units), round caps and joins | `#E6DBF9` |
| Centre dots | 3dp dotted line down the middle of the band, dots about 10dp apart | `#B9A2EC` |
| Glow (3 rings under the paint) | 77dp, 69dp, 62dp wide | `#B18CFA` at 18%, 26%, 38% |
| Paint core | 56dp wide, same shape as the band, round | grape `#8E59E6` |
| Paint shine | 16dp wide line down the centre of painted runs | `#B995FA` at 55% |
| Stroke-complete line | 5dp white line down the centre of a finished stroke | white at 50% |

Contrast (WCAG luminance ratio, measured): paint vs guide fill 3.3, paint vs cream 4.1, guide edge vs cream 2.6 (the fill alone is 1.2 against cream, so the edge is what makes it readable in sun; do not drop it), white paw on grape 4.5. The glow is drawn as three widening, fading strokes, no blur (same approach as Paw Pour's selected glow), and reaches 10dp past the band each side, so progress shows beyond a 40dp fingertip.

There is one paint colour for every glyph. Nothing ever paints outside the guide shape; the glow may spill a few dp onto the cream and that is intended.

### Marker (the frog) and direction cue
- **Marker:** Paw Match's frog face (`#7FBF6B` head, `#DFF2D6` muzzle, ink eyes, small smile; the `CritterFace` recipe, cropped tight so the head fills the box) at **48dp**, on a 58dp white ring, on two sunshine halos (74dp at 30%, 90dp at 14%). It bobs 5dp up and down over 1.3s. It is never hidden by the finger: its centre sits **48dp (15 glyph units) further along the path than the first unpainted point** of its stroke, so the ring's edge is clear of a 40dp fingertip.
- **Which stroke it is on:** the stroke the child is currently painting, while it is unfinished; otherwise the first unfinished stroke in the glyph's list order. If a stroke's start is unpainted the marker sits exactly on the start point.
- **Hop:** when a stroke completes the frog hops to its next stroke in about 0.4s (a 30dp arc up, sliding position). Reduced motion: it just moves.
- **Direction cue:** three **paw prints** on the path ahead of the frog, 20dp each, centred 48, 83 and 118dp further along the stroke (not drawn past the stroke's end), rotated to the path direction (toes forward), colour `#A98BE6` at 95%, 65%, 40%. They show which way to go without any arrow. They are hidden while the frog walks the hint and once the glyph is done.
- **Idle hint:** after about 5s with no touch, the frog slides along its stroke about 134dp ahead over 1.7s (ease in and out), then drifts back; repeats after each further pause. Any touch cancels it.
- **Happy frog:** on completion the face swaps to Paw Kitchen's happy face (squeezed-shut arched eyes, open smile, pink cheeks).

### Finger and paint rules (drawing behaviour)
- Paint goes at the point of each stroke nearest the finger, only if the finger is within 32dp of that stroke's centreline, plus 3dp behind and ahead of that point. It fills the whole 56dp band across (not just the finger width). Where two strokes join, a finger near the join paints both.
- Off the path: nothing at all (no colour, no fade, no shake, no sound, no count). Lift: paint stays, frog stays. Touch down anywhere on a path: painting resumes there.
- Several fingers or a palm: each touch is independent; touches off the path do nothing.
- **Stroke complete at 85%** of its length painted: the remaining gaps fill in over about 250ms, three sunshine four-point sparkles (about 14, 17 and 12dp radius, at 22%, 50% and 78% of the stroke, offset 15dp off the line, twinkling 1.3s) show for about 1.8s, the chime plays, the frog hops on. The finger never has to reach an exact end or corner.
- **No failure state anywhere.** There is no score, star, timer, or "how well" readout. The first glyph is on screen the moment the game opens, with the frog already at its start (no loading state; first run and every run look the same).

### Celebration (~2s, then wait for the button)
Same style as Paw Match and the other games. The glyph is fully grape and stays put (it pops 1.6s loop between 100% and 103.5% scale); a white shimmer (12% of stroke length, 6-unit wide) runs along every stroke over 1.8s; sunshine sparkles and a few coral hearts float around the box; the frog, now happy, hops 22dp on the glyph's top (centred at glyph unit (50, -8)); the happy sound plays. **One** 88dp leaf play-on button (white paw, same as Match and Pour) appears at the bottom, hopping 7dp every 1.1s with a 6dp leaf halo at 22%. Tapping the corner home button still works. The play-on button does nothing until it exists, and after tapping it the next glyph appears with the frog at its start; the celebration never advances by itself.

### Glyph data conventions (for the developer)
The full data lives in `docs/glyphs/paw-trace-glyphs.md` (id, stage, strokes as SVG path strings). Rules:
- **Box:** every glyph is in a 100 x 100 box, y down. Centrelines stay inside x 12..88, y 11..89, so the 56dp band (8.75 units each side at 3.2dp per unit) stays inside the box. One box unit = 3.2dp at the 320dp reference box.
- **Strokes:** a glyph is an ordered list of strokes. Each stroke is a single SVG path string using only absolute `M`, `L` and `C` commands, one `M` per stroke, no `Z`, written with commas between coordinates and spaces between commands. A closed shape (circle, square, triangle, heart, 6, 8, 0, O) is one stroke that ends exactly on its own start point.
- **Start and direction:** the first point of the path is the stroke's start (where the frog stands) and the path direction is the direction of the paw prints and the hint walk. The child may paint either way. Conventions: top to bottom, left to right; closed shapes start at the top (or top-left corner) and go counter-clockwise; letters are made of the fewest natural strokes (a stem, then its bowl or arms).
- **Order of the list:** the order in which the frog visits unpainted strokes (stem before arms, left to right, top to bottom). It is **never enforced**; painting in any order and direction works, and completion is only "every stroke at least 85% covered".
- **Joins:** strokes that meet share an endpoint or a point on each other, and their bands simply overlap there. Strokes that do not join keep centrelines at least 20 units (64dp) apart, so a band never touches an unrelated stroke. Q's tail starts inside the ring (about 21 units from it), which is the tightest case.
- **Special glyphs:** digit 1 is one stroke (flag then stem, no base). Digit 4 is closed: stroke 1 is the diagonal plus crossbar, stroke 2 is the vertical, both starting at the top point. Digit 3 doubles back on itself at the middle (a deliberate cusp; treat a 180 degree turn as legal). Digit 8 is two touching loops in one stroke that passes the centre twice. Capital I has top and bottom bars so it never looks like the plain vertical line.
- **Measuring coverage:** sample each stroke's path every 1.5 units (`PathMeasure`); a sample is covered once the finger's nearest point on that stroke lies within 3 units of it (see the paint rules). A stroke is done at 85% of samples covered. Because a closed shape's first and last sample are the same spot, this needs no special case.
- **Adding a glyph later:** add an id, a stage and its strokes. Nothing in the layout, colours or markers changes.

### Added by the developer while building
- **Layout on other phones.** The glyph box is at most 320dp (the validated reference) and at least 288dp. Side margins are 20dp and squeeze to 16dp on a 320dp-wide phone before the box shrinks. The box is centred vertically unless that would leave less than 16dp to the home button or the play-on button; the play-on button's bottom margin (44dp at the reference) can shrink to 12dp before the glyph moves. Under about 496dp of usable height the play-on button may overlap the glyph's foot (it only exists on the celebration); tell the ui-designer if a real phone that small matters.
- **Touch failsafes.** Only the first finger paints. A finger that landed first but has never been on a path yields to a later finger that lands on one, so a resting palm cannot block painting. A finger still down from the last glyph does not paint the next one until it lifts and lands again.
- **Paint corners.** Round caps and joins are asked for everywhere; on-device Android draws them as designed.
- **Frog.** Drawn inside the Paw Trace package (same colours and recipe as Paw Kitchen's customer frog: the smile and the happy grin), cropped tight to a 54 of 80 window.
- **Touch area.** Wider than the design table says: the whole screen below the buttons takes the finger, so a swipe that starts in the blank space and slides onto the path paints when it reaches it. The home and play-on buttons still take their own touches. On a short screen the happy frog's hop is trimmed so its halo stays on screen.
- **Silent.** No sound yet, like every game; the chime and happy sound wait for the founder's app-wide decision.

### Decisions awaiting founder OK (mockup review)
Grape for the tile badge and every glyph's paint; a frog as the marker (alternative: plain glowing dot); paw prints as the direction cue instead of arrows; pale lavender guide with an outline and dotted centre; one big green paw button on the celebration with home staying in its corner; the letter, digit and shape drawings (capital I with bars, closed 4, flagged 1).

## Paw Blocks: board, blocks, marks and clears

Chrome is unchanged (cream background, white surfaces, ink, 56dp home button, 29dp tile corners). Mockup: https://claude.ai/artifact/3FwMKpnXaG8oKN6zrWmtQV (private; frame 3 is playable, and its raw HTML holds exact geometry and every mark's path). Everything below is on a 360 x 692dp play area; sizes are dp.

### Home tile and badge
- Fifth tile, icon only, drawn on a 3 x 3 patch of the board: cells 32dp, patch 96dp, top-left at (25, 25) inside the 146dp tile. Empty cells as on the board; the **Tangerine square** fills columns 0-1 of rows 0-1, the **Bubblegum bar of 2** fills columns 0-1 of row 2, column 2 shows a sky ghost (3 cells, `#4FC1E9` at 22%, 2.5dp `#1A8FCB` outline) and a **Sky bar of 3 (vertical)** hovers over it, lifted 13dp, moved 7dp right, tilted 6 degrees, with the block shadow.
- Badge: **bubblegum pink `#F2599B`** with a white paw, same bottom-end corner and 22.6dp size as the others (Match = sunshine, Pour = sky, Kitchen = coral, Trace = grape, Blocks = pink; leaf stays unused because it means "done"). White on it measures 3.1. It is a badge colour, not a theme token, and is deeper than the Bubblegum block `#FF9ACB`.
- Five games shelve as 2 + 2 + 1 with the odd tile centred; tiles stay 146dp (three rows = 478dp with 20dp gaps).

### Board
| Element | Spec |
|---|---|
| Panel | **348 x 348dp** on a 360dp phone (width minus 12, so 6dp from each side, never wider than 348 on any phone), left 6dp, top 96dp, white, radius 20, 3dp edge `#EADFCF`. Inner grid 340 x 340dp at (10, 100), which is 4dp padding. No scrolling ever. (Changed after QA: the first design had a 328dp panel and 35.6dp cells at 9x9; the board is never touched, so it may go close to the sides, and 9x9 cells are now 37.8dp at 360 wide and 33.3dp at 320 wide, about the most nine cells can have on 320dp.) |
| Cell size | **grid / N dp** (340 / N on the reference screen) |
| Empty cell | fill `#F4ECDF`, outline `#D9CCB8` (max(1.25dp, 2.5% of cell)), no mark. Inset 3% of the cell on every side, corner radius 22% of the cell |
| Filled cell | same geometry, fill = family colour, rim ink `#2B2320` at 28% (max(1dp, 2.5%)), one mark on top (below) |

| Stage | Board | Cell | Ghost | Tray cell (see below) |
|---|---|---|---|---|
| 1 | 5x5 | 68.0 | 3dp | 44 |
| 2 | 5x5 | 68.0 | 3dp | 29 |
| 3 | 6x6 | 56.7 | 3dp | 29 |
| 4 | 7x7 | 48.6 | 4.5dp + glow | 22 |
| 5 | 8x8 | 42.5 | 4.5dp + glow | 22 |
| 6 | 9x9 | 37.8 | 4.5dp + glow | 17 |

Board cells are never touch targets. **Empty-cell vs Sunshine block is the weakest fill contrast at 1.23** (luminance ratio), which is why filled cells carry the ink rim and the mark and empty cells carry an outline and no mark; do not drop either.

### Tray
- Three slots, **96 x 112dp**, left edges at x = 24, 132, 240 (12dp gaps, **24dp from each screen side**, which keeps the outer slots off the system back-gesture strip on gesture-navigation phones; a 320dp phone gets 82.7dp slots, still over 72), top at y = 508. The whole slot is the grab area. Slot resting = white, 3dp sunshine border, radius 20 (same as Paw Kitchen's tray tile). Slot with its block lifted = transparent, dashed `#DCCFC0` border (8 on, 7 off), block shown at 18% until it lands or glides home. Empty slot (block placed) = the same dashed outline, nothing inside.
- **Tray cell = min(board cell, floor((slot width - 8) / widest side in cells of any block in the stage's set), and the same by height)**: 44 at stage 1 (widest 2), 29 at stages 2-3 (3), 22 at stages 4-5 (4), 17 at stage 6 (5). The block is centred in its slot. On pick-up it grows to the full board cell over 120ms.
- The home button (56dp, top-left at (20, 20)), the board and the tray never overlap; on stage 6 the bottom of the tray is at y = 620. On short windows the tray's bottom margin (72 down to 12), then the slot height (112 down to 72), then the gap above the tray (24 down to 12), then the top margin (96 down to 84) give way before the board shrinks.

### Block encoding (for the developer)
Cells are `[col, row]` offsets from the block's top-left, x to the right, y down. A block has an id, a family, and its cells. Width = max col + 1, height = max row + 1. Blocks never rotate; every orientation is its own entry.

| Family | Colour | Hex | Mark | Mark colour | Ids and cells |
|---|---|---|---|---|---|
| dot (stage 1) | Sunshine | `#FFD23F` | star | ink `#2B2320` | `dot` [0,0] |
| bar2 (stage 1) | Bubblegum | `#FF9ACB` | heart | ink | `bar2h` [0,0] [1,0]; `bar2v` [0,0] [0,1] |
| bar3 (stage 2) | Sky | `#28A9E3` | drop | ink | `bar3h` [0..2,0]; `bar3v` [0,0..2] |
| square (stage 3) | Tangerine | `#E85A10` | fish | ink | `sq` [0,0] [1,0] [0,1] [1,1] |
| corner (stage 3) | Leaf | `#1A8344` | leaf | white | `cTL` [0,0] [1,0] [0,1]; `cTR` [0,0] [1,0] [1,1]; `cBR` [1,0] [0,1] [1,1]; `cBL` [0,0] [0,1] [1,1] (the letters name where the elbow cell is) |
| bar4 (stage 4) | Cocoa | `#83532B` | bone | white | `bar4h` [0..3,0]; `bar4v` [0,0..3] |
| rect (stage 5) | Grape | `#5A31A6` | paw | white | `rectV` 2 wide 3 tall [0..1,0..2]; `rectH` 3 wide 2 tall [0..2,0..1] |
| bar5 (stage 6) | Midnight | `#223475` | moon | white | `bar5h` [0..4,0]; `bar5v` [0,0..4] |

Sixteen shapes, eight families: the colour and mark belong to the family, so a bar lying down and standing up share both, and the mark is always drawn upright. The board stores the family per filled cell, which is all the drawing needs. Colours run light to dark in the order the families arrive, so stage 1 is the two brightest.

**Mark drawing.** Solid single-colour shape in a unit box from -0.5 to +0.5 on both axes (y down), centred on the cell, scaled to **62% of the cell side**, filled in the mark colour with a 0.07-unit round-join stroke in the same colour. Paths (unit box):
- star: 10-point polygon, outer radius 0.52, inner 0.24, shifted down 0.03, first point straight up.
- heart: `M0,0.42 C-0.58,0.02 -0.5,-0.44 -0.2,-0.44 C-0.08,-0.44 0,-0.36 0,-0.27 C0,-0.36 0.08,-0.44 0.2,-0.44 C0.5,-0.44 0.58,0.02 0,0.42Z`
- drop: `M0,-0.5 C0.08,-0.32 0.36,-0.12 0.36,0.14 A0.36,0.36 0 0 1 -0.36,0.14 C-0.36,-0.12 -0.08,-0.32 0,-0.5Z`
- fish: `M-0.46,0 C-0.34,-0.24 0.06,-0.3 0.24,-0.04 L0.48,-0.26 L0.48,0.26 L0.24,0.04 C0.06,0.3 -0.34,0.24 -0.46,0Z` plus an eye, a circle r 0.055 at (-0.24, -0.04) in the cell colour.
- leaf: `M-0.42,0.42 C-0.54,-0.08 -0.12,-0.5 0.44,-0.44 C0.5,0.12 0.08,0.52 -0.42,0.42Z` plus a vein, line (-0.34,0.34) to (0.2,-0.2), 0.07 wide, round caps, in the cell colour.
- bone: rotate -40 degrees; rect x -0.3..0.3, y -0.1..0.1; four circles r 0.13 at (+-0.32, +-0.13).
- paw: ellipses (cx, cy, rx, ry): pad (0, 0.2, 0.28, 0.24); toes (-0.36, -0.02, 0.11, 0.14), (-0.13, -0.27, 0.12, 0.15), (0.13, -0.27, 0.12, 0.15), (0.36, -0.02, 0.11, 0.14).
- moon: `M0.25,-0.5 A0.5,0.5 0 1 0 0.25,0.5 A0.7,0.7 0 0 1 0.25,-0.5Z`
At the smallest cell (37.8dp) a mark is about 23dp; in the stage-6 tray (17dp cell) about 11dp, where the picture is a reminder and the colour and silhouette do the work. Pictures only, never letters or numbers.

**Luminance contrast (WCAG ratio, measured; the mockup page recomputes all 28 pairs live from the hexes).** Ladder, lightest to darkest, ratio to the family above:

| Family | Luminance | Ratio to the one above | Mark vs block |
|---|---|---|---|
| Sunshine | 0.677 | (lightest) | 10.7 (ink) |
| Bubblegum | 0.487 | 1.35 | 7.9 (ink) |
| Sky | 0.343 | 1.36 | 5.8 (ink) |
| Tangerine | 0.245 | 1.33 | 4.3 (ink) |
| Leaf | 0.169 | 1.35 | 4.8 (white) |
| Cocoa | 0.112 | 1.35 | 6.5 (white) |
| Grape | 0.071 | 1.34 | 8.7 (white) |
| Midnight | 0.041 | 1.33 | 11.6 (white) |

Every one of the 28 pairs is at least 1.33 (the closest are neighbours, so all others are larger); all eight can share a stage-6 board, so the rule is every pair at least **1.3**, and every mark at least **3** against its block. The developer should add the same unit test as `PawPourRobustnessTest` (pair ratios, mark ratios, no two families sharing a mark). The numbers above were worked by hand and the mockup page recomputes them in the browser; if the unit test disagrees with either, the test wins and only lightness moves, never hue or mark.

### Drag, ghost and snap
- **Grab.** Finger down anywhere in a slot with a block: the block grows to full board cell size over 120ms (ease-out), a soft shadow (ink 18%, 8dp down) appears, the slot goes dashed.
- **Ride offset.** The block is centred horizontally on the fingertip and its **bounding-box bottom edge is 64dp above the fingertip**, at every stage and block size (so a bar of 5 standing up at 36dp cells reaches 178dp above the bottom edge; the finger is never under it).
- **Ghost.** Shown only when a legal spot is within reach; cells filled `#4FC1E9` at 30%, outline `#1A8FCB` 3dp (measures 3.1 against an empty cell). On 7x7 and larger: fill 42%, outline 4.5dp, plus a 10dp `#4FC1E9` at 50% halo stroke under it. It jumps between spots with no animation, so what you see is what you get. No mark on the ghost.
- **Snap reach.** Legal spot nearest to the drawn block's top-left, if within max(one board cell, 40dp); ties go to the spot nearer the fingertip.
- **Drop, legal.** The block moves into its cells over 90ms, then a "plop": those cells scale 1 to 1.08 to 1 over 160ms (sine). Soft plop sound.
- **Drop, illegal or interrupted.** Glides to its slot over 250ms (ease-out) shrinking to tray size. No sound, shake, red or count. The block can be grabbed again at once, mid-glide.

### Clear, clear-out and growth motion
| Moment | Look | Timing |
|---|---|---|
| **Line clear** | The line's cells are removed from the grid at once (the board is playable straight away). A white shimmer band (1.6 cells wide, leaning 0.18 of a cell, white at 85% in the middle fading to 0 at both edges) sweeps along the line from start to end, from -0.2 to 1.2 of the line's length. Each cell, as the band's centre passes it: brightens (white 50%, scale up to 1.06 in 150ms), then shrinks to 0 and fades over 250ms. 4 plus the number of cleared cells, at most 12, four-point sunshine sparkles (8 to 15dp radius) pop near the cells, each 520ms, timed to the sweep. One soft rising chime at t = 0. Nothing above or beside the line moves. Several lines play together (one shared sweep, one sparkle set). | Sweep 600ms; cells gone by 850ms; the whole thing 1s |
| **Animal reaction** | Fox (the Paw Match fox with Paw Kitchen's happy face: squeezed-shut arched eyes, open smile, pink cheeks), 72dp head, peeks up from behind the board's top-right edge (head at x 266 to 338, ears reaching y 34), two paws (20 x 14dp, `#F0924A`) rest on the panel edge at (284, 98) and (320, 98). Rises 58dp over 250ms, holds 500ms, sinks over 250ms. The panel is drawn on top of it, so it never covers a cell. One animal per clear; the full production set is the six Paw Match critters in a fixed rotation. It is not drawn at all when idle | 1s |
| **Clear-out** | After about 1s of nothing fitting, the 2 or 3 fullest rows (ties: top first) leave together: a white wash (50%) fades in over 200ms on those rows and out by 900ms while the cells scale to 0.72 and fade over 700ms; 4 plus a few small sparkles (5 to 8dp; pale sunshine `#FFF6C7`), each 520ms, scattered over the rows in the first 250ms. Soft whoosh. No animal, no chime, no message; tray untouched | 900ms |
| **Growth** | After the last line-clear animation ends, the grid re-fits: cell size eases from 320/N to 320/(N+1) over 520ms (ease in-out) while every placed cell keeps its column and row; one empty row (bottom) and one empty column (right) fade in as ordinary empty cells. Nothing is announced, no sound | 520ms |
| **Refill** | 500ms after the last block is placed (and any clear-out or line clear has ended), all three slots slide in from 28dp lower with a fade over 320ms, together | 320ms |

### States and safety
- The home button (56dp white circle, ink home glyph, top-left 20dp from the edges) is drawn last and stays on top; it works mid-drag, and a drag that ends over it is an illegal drop.
- Only the first finger that grabbed a block is followed; other touches are ignored until it lifts (the mockup implements this).
- No screen contains text, a score, a counter, a timer, a "game over" or any lose state.

### Decisions awaiting founder OK (mockup review)
Pink badge; the eight colours with star, heart, drop, fish, leaf, bone, paw and moon; the peek-up fox behind the board (rotating through the six critters); the quieter, animal-free clear-out look; tray blocks drawn smaller than board cells (46 down to 18dp); the block riding 64dp above the fingertip measured from its bottom edge.

## Hard rules
- Every screen designed for a phone held one-handed by small hands, landscape or portrait per the game's needs.
- Touch targets ≥48dp; generous spacing between anything tappable.
- No screen puts a money-related action, an external link, or a destructive action (delete save) within one tap of normal play — those sit behind an adult gate.
- Loading, first-run (no save yet), and "try again" states are part of every screen's design, not afterthoughts.
