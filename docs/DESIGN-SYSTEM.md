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
| Score number | Top strip of Paw Blocks and Paw Pop only | Plain number, top right, 32sp. See "Score, paws and the good-game screen". |
| Paws (three rescues / lives) | Same strip, right of the exit icon | Solid paw = left, hollow dotted paw = used. Same drawing in both games. |
| Good-game screen | End of Paw Blocks and Paw Pop | Kind ending: animal, score, best score with rosette, play-again and home buttons. |
| Rosette | Best-score cue on the good-game screen | Scalloped prize ribbon, deliberately not a coin or star. |
| Entry line (Pop) | Below Pop's top strip | Cloud bank the targets come out from; nothing pops until fully out. |

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
| Panel | **348 x 348dp** on a 360dp phone (width minus 12, so 6dp from each side, never wider than 348 on any phone), left 6dp, top **132dp** (was 96; moved down 36 on 2026-09-25 to make room for the score and paws strip and the fox's peek lane, see "Score, paws and the good-game screen"), white, radius 20, 3dp edge `#EADFCF`. Inner grid 340 x 340dp at (10, 136), which is 4dp padding. No scrolling ever. (Changed after QA: the first design had a 328dp panel and 35.6dp cells at 9x9; the board is never touched, so it may go close to the sides, and 9x9 cells are now 37.8dp at 360 wide and 33.3dp at 320 wide, about the most nine cells can have on 320dp.) |
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
- Three slots, **96 x 112dp**, left edges at x = 24, 132, 240 (12dp gaps, **24dp from each screen side**, which keeps the outer slots off the system back-gesture strip on gesture-navigation phones; a 320dp phone gets 82.7dp slots, still over 72), top at y = **520** (was 508; 40dp below the board panel). The whole slot is the grab area. Slot resting = white, 3dp sunshine border, radius 20 (same as Paw Kitchen's tray tile). Slot with its block lifted = transparent, dashed `#DCCFC0` border (8 on, 7 off), block shown at 18% until it lands or glides home. Empty slot (block placed) = the same dashed outline, nothing inside.
- **Tray cell = min(board cell, floor((slot width - 8) / widest side in cells of any block in the stage's set), and the same by height)**: 44 at stage 1 (widest 2), 29 at stages 2-3 (3), 22 at stages 4-5 (4), 17 at stage 6 (5). The block is centred in its slot. On pick-up it grows to the full board cell over 120ms.
- The home button (56dp, top-left at (20, 20)), the board and the tray never overlap; the bottom of the tray is at y = 632 (was 620; 60dp bottom margin on 692). On short windows the tray's bottom margin (60 down to 12), then the slot height (112 down to 72), then the gap above the tray (40 down to 12), then the board's top margin (132 down to 96, never into the score strip, which ends at y 76) give way before the board shrinks. The score and paws strip (y 20 to 76) is drawn above the board and never scrolls or shrinks.

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
- No screen contains text, a counter (other than the score), a timer, or the words "game over". **Amended 2026-09-25:** the score, three paws and the good-game screen exist (next section); the game has one lose state, always the kind screen.

### Decisions awaiting founder OK (mockup review)
Pink badge; the eight colours with star, heart, drop, fish, leaf, bone, paw and moon; the peek-up fox behind the board (rotating through the six critters); the quieter, animal-free clear-out look; tray blocks drawn smaller than board cells (46 down to 18dp); the block riding 64dp above the fingertip measured from its bottom edge.

## Paw Pop: ship, stars, targets, gifts and effects

Chrome is unchanged (cream background outside the game, white surfaces, ink, 56dp home button, 29dp tile corners). Inside the game the background is the sky below. Mockup: https://claude.ai/artifact/LCGGdCJVmwdWSJtAQu4cre (private; frame 3 is playable, and its raw HTML holds every path below as working code: `targetSvg`, `popSvg`, `shipSvg`, `giftIcon`, `ribbonSvg`, `waveSvg`, plus the whole game loop in `makeGame`). Everything is vector drawing, no bitmaps. Sizes are dp on the reference 360 x 800dp phone (the PRD's "sh" = 800dp); speeds below are the PRD's sh/s figures times 800. The mockup's play area is 360 x 692, so its pixel positions differ from the phone's but every dp size is the same.

### Home tile and badge
- Sixth tile, icon only, white 146dp tile as the others: teal **oval balloon** (size 50, ladder colour Teal, centre (104, 46)), a **star** (outer radius 14, tilted 12 degrees, centre (62, 48)) with two small sunshine sparkles (radius 4.6 at (76, 72), 3.2 at (54, 76)), and the **ship** at 72dp box, centre (50, 100). The star is on its way up to the balloon.
- Badge: **teal `#0F9D94`** with a white paw, same bottom-end corner and 22.6dp size as the others (Match = sunshine, Pour = sky, Kitchen = coral, Trace = grape, Blocks = pink, Pop = teal; leaf stays unused because it means "done"). White on it measures 3.35. It is a badge colour, not a theme token.
- Six games shelve 2 + 2 + 2 (three rows of 146dp with 20dp gaps = 478dp), so the last row is full and no longer centred.

### Sky (game background)
Vertical linear gradient over the play area, `#B7D6F4` at the top to `#DCEBF8` at the bottom (luminance 0.65 to 0.81). Decoration only, static: six clouds (three white ellipses at 45%: rx 40 / ry 14 at the centre, rx 26 / ry 11 at (-22, +4), rx 24 / ry 10 at (+26, +4), scaled 0.7 to 1.15) and nine 2.2dp white dots at 80%. Nothing in the sky twinkles, moves or resembles a target. The home button is drawn last, on top of everything.

### Target colours (content colours, not theme tokens)
Six, light to dark. Stage 1 uses Butter, Tangerine, Teal (the widest apart); stage 2 adds Blush and Berry; stage 3 onward all six.

| # | Name | Hex | Luminance | Ratio to the one above |
|---|---|---|---|---|
| 1 | Butter | `#FFE58A` | 0.791 | (lightest) |
| 2 | Blush | `#FF9FC4` | 0.500 | 1.53 |
| 3 | Tangerine | `#F2852F` | 0.359 | 1.35 |
| 4 | Lilac | `#9B6BE0` | 0.229 | 1.47 |
| 5 | Teal | `#0A7873` | 0.147 | 1.41 |
| 6 | Berry | `#A31C55` | 0.093 | 1.38 |

Every one of the 15 pairs is at least **1.3** (weakest: Blush / Tangerine 1.35; all other pairs are neighbours or further apart). These were worked by hand and the mockup page recomputes all of them from the hexes live; if a unit test (add `PawPopColorsTest`, like `PawPourRobustnessTest`: pair ratios, no two share a silhouette) disagrees, the test wins and only lightness moves, never hue. No colour means anything: any colour can wear any silhouette.

**Against the sky.** No colour can be far from a mid-toned sky, so every target has a **rim**: ink `#2B2320` at 45%, 3dp wide, `vector-effect`-style constant width, round joins (in Compose: a `Stroke(3.dp)` in ink at alpha 0.45 drawn over the shape edge). Fill against sky top / bottom: Butter 1.21 / 1.03, Blush 1.27 / 1.57, Tangerine 1.71 / 2.11, Lilac 2.50 / 3.10, Teal 3.53 / 4.38, Berry 4.88 / 6.05. The lightest colours are carried by the rim: the rim colour (fill mixed 55% with ink 45%) measures 2.14 (Butter) against the sky top, 2.65 against the bottom, higher for every other colour. So the rim must never be dropped.

### Silhouettes (drawing recipes)
All five are drawn in **local units of S/100**, origin at the body centre, y down, where **S is the nominal width** (the drawn width, 88 to 112dp; 72 for the stage-5 small ones). Compose: `translate(cx, cy)` then `scale(S / 100f)` on the canvas, and draw the paths below unchanged. Shared parts: rim as above; **shine** = a white ellipse at 55% (round: `cx -20 cy -25 rx 15 ry 8`, rotated -38 degrees about its centre, plus a white 50% dot `cx -34 cy -4 r 3.6`; others below); **string** = 1.6dp constant width ink at 50%, round caps, no fill; **knot** = small triangle in the body colour with the rim.

| Silhouette | Body | Extras | Hit ellipse (fractions of S: rx, ry, dx, dy) | Half height |
|---|---|---|---|---|
| Round bubble | circle r 50 | no string | 0.50, 0.50, 0, 0 | 0.50 |
| Oval balloon | ellipse rx 50 ry 62 | knot `M0,60 L-8,75 L8,75Z`; string `M0,74 C-7,82 7,90 0,98 C-5,104 4,108 1,112`; shine ellipse `-22,-30 rx 10 ry 22` rotated 20 | 0.50, 0.62, 0, 0 | 0.62 |
| Heart balloon | `scale(1.11) translate(-50 -50)` then `M50,92 C-6,56 -4,14 26,10 C40,8 50,18 50,28 C50,18 60,8 74,10 C104,14 106,56 50,92Z` | knot `M0,44 L-7,58 L7,58Z`; string `M0,56 C-7,64 7,72 0,80 C-5,86 4,90 1,94`; shine ellipse `-27,-30 rx 10 ry 5` rotated -35 | 0.46, 0.45, 0, -0.02 | 0.47 |
| Moon balloon | `translate(-50 -50)` then `M59.5,0.9 A50,50 0 1 0 96.7,67.9 A40,40 0 1 1 59.5,0.9Z` (a circle r 50 with a circle r 40 at (68, 40) taken out) | knot `M0,48 L-7,62 L7,62Z`; string `M0,62 C-7,70 7,78 0,86 C-5,92 4,96 1,100`; shine ellipse `-38,-12 rx 5.5 ry 17` | 0.38, 0.50, -0.12, 0 | 0.50 |
| Critter bubble | ears (below), then circle r 50 in the body colour, then a paper disc r 40 `#FFF8EC` at 95%, then the face | shine ellipse `-31,-33 rx 9 ry 4.5` rotated -35 (white 60%); no string | 0.50, 0.50, 0, 0 | 0.50 (ears reach 0.66 above; bunny 0.87) |

Draw order for oval, heart and moon: string, knot, body, shine (the body covers the knot's base). A target's box is never wider than S; the string (about 0.5 S below the body) is not part of the hit shape.

**Critter parts** (same units; rim = ink 40%, 2dp constant width, round joins). Ears/eyes poke out of the bubble and are drawn first, so the silhouette differs from the round bubble in greyscale:
- Fox: ears `-40,-24 -38,-66 -10,-46` and the mirror, `#D9722C`. Head circle at (0, 6) r 26 `#F0924A`, muzzle ellipse (0, 16) 13 x 9 `#FBEBD8`.
- Bear: ear circles r 15 at (+-34, -36) `#8A5A34`. Head `#A9754A`, muzzle `#E7CBA6`.
- Bunny: ear ellipses rx 10 ry 27 at (+-19, -60), rotated -+8 degrees, `#EDE3D8`, inner ellipse rx 4.5 ry 18 `#FFB8CC`. Head `#F1E8DD`, muzzle `#FFFFFF`, nose `#FF8FA8`.
- Frog: eye bumps r 15 at (+-24, -42) `#7FBF6B`, drawn first; after the paper disc, eye whites r 8 at (+-24, -44) and pupils r 4 at (+-24, -43). Head `#7FBF6B`, muzzle `#DFF2D6`, nostrils r 1.6 at (+-5, 9), no eyes on the head.
- Face (fox, bear, bunny): eyes r 3.4 ink at (+-9, 2); nose triangle `0,10 -4,15 4,15` ink (bunny `#FF8FA8`); mouth `M-6,19 Q0,24 6,19` 1.8dp ink. Happy face (used in the pop): eyes `M-14,3 Q-9,-4 -4,3` and `M4,3 Q9,-4 14,3` 2.6dp ink, open mouth `M-7,18 Q0,28 7,18Z` `#B8301F`, cheeks r 4 at (+-17, 14) `#FF8FA8` 70%. Frog happy eyes: `M-31,-44 Q-24,-52 -17,-44` and `M17,-44 Q24,-52 31,-44`.

**Sizes and placement.** Stage 1: 104-112 (round only); 2: 96-104; 3: 96-104; 4: 88-104; 5: 88-104 plus about one in five at 72. Sway is +-16dp, +-24dp at stage 5, one full sway per 3 s (`x = x0 + amp * sin(2 pi t / 3 + phase)`). A new target appears just above the top edge at least 12dp clear of every other (measured to the drawn edge; the mockup's stage-5 frame checks this live). Two swaying targets that briefly overlap simply pass over each other (older is behind); nothing reacts. The first target of a session appears already partly on screen.

**Hit.** A star counts as a hit when its centre is within 8dp of the drawn edge, tested against the hit ellipse in the table (centre = body centre + (dx, dy) * S; radii = fractions * S) grown by 8dp. Big-star hit: grown by 28dp instead. The ribbon hits any target whose ellipse overlaps its 56dp column above the ship.

### Star (the shot)
5-point polygon on a 100 grid centred on the origin: outer radius 50, inner radius 22, first point straight up, drawn at **28dp across (outer radius 14)**; fill sunshine `#FFD23F`, 2dp constant-width rim `#D99A00`, round joins, a white 55% copy scaled 0.4 and shifted (-7, -7) as a shine. Big star: same drawing at 56dp across. It rises at **720 dp/s** (0.9 sh/s), starting at the ship's nose, and is removed 30dp past the top edge (a tiny sparkle twinkle at the top is optional). Fired every 0.4 s. Triple: three stars, angles 0 and +-12 degrees, sideways speed 720 tan 12 = 153 dp/s, each drawn tilted by its angle. The star is also the game's sparkle-free glyph: it is never used for anything but the shot and the star gifts (no score, no count).

### The ship
Drawn on a 100 x 100 grid at an **88 x 88dp box** (fins span x 8 to 92 = **74dp across**, over the 72dp minimum). Its bottom edge is 24dp above the bottom edge of the screen and above any gesture bar; its centre x follows the finger (clamped so the fins stay 8dp clear of each side: centre x in [46, 314] on 360dp). Ship speed: eased, at most 540 dp/s (1.5 screen widths a second); the mockup uses `v = clamp(dx * 7, -540, 540)`.
Parts, in order (rim = ink 40%, 1.6dp constant width, round joins, on the fins, body and window):
1. **Flame** `M50,78 C41,86 44,94 50,100 C56,94 59,86 50,78Z` sunshine `#FFD23F` with an inner `M50,81 C46,86 47,91 50,95 C53,91 54,86 50,81Z` `#FFF6C7`; it scales vertically about y 78 between 0.92 and 1.08 once per second (sine).
2. **Fins** (coral `#FF6B4A`) `M28,52 C14,56 8,66 8,82 C20,82 28,78 33,70Z` and the mirror `M72,52 C86,56 92,66 92,82 C80,82 72,78 67,70Z`.
3. **Body** `M50,4 C72,16 80,44 76,78 L24,78 C20,44 28,16 50,4Z` white; the strip y 0..28 (nose) and the strip y 68..78 (band) are filled coral `#FF6B4A` clipped to the body path; then the body outline with the rim.
4. **Window:** circle (50, 48) r 16 `#DFF3FB`, 3dp white ring, rim; inside it (clipped to r 14) the Paw Match fox face, drawn 30 x 30 at (35, 34).

**Glow states.** None while nothing runs. A star effect (triple, big, ribbon) draws three flat circles under the ship, no blur: radii 62, 52, 44dp, sunshine `#FFD23F` at 16%, 24%, 34%. Slow drift draws the same three at radii 76, 66, 58 in lilac `#B79BF0` (drawn first, so both show when both run). The sparkle wave has no glow. Centre of the circles = ship centre.

### Sparkles, pop and ring
Sparkle = four-point star `M0,-1 Q0.18,-0.18 1,0 Q0.18,0.18 0,1 Q-0.18,0.18 -1,0 Q-0.18,-0.18 0,-1Z` scaled by its radius, fill sunshine `#FFD23F` or white, 0.13-unit rim `#E8A400` at 55%.
**Pop** (t = 0 when the star touches; total 0.35 s; the target is removed from play at t = 0, the picture just finishes):
- Body: scale 1 to 1.06 by 60 ms, then down to 0.94 by 170 ms; opacity `1 - (t/170)^2`, gone at 170 ms.
- Ring: from t = 20 ms to 350 ms, radius `S * (0.5 + 0.38 * easeOut(k))`, `k = (t - 20)/330`, opacity `0.85 * (1 - k)`; two circles: white 5.5dp underneath, the target's own colour 2.5dp on top.
- Sparkles: seven, angles `12 + 360 i / 7` degrees, distance `S * (0.34 + 0.42 * easeOut(k))`, radius `(5 + 4.5 * (i mod 2)) * sin(pi k)`, alternating sunshine and white. They grow and shrink smoothly (never blink); nothing goes further than 0.9 S from the centre.
- Critter: same, and from t = 60 ms the animal (ears at 0.8 scale, face group at 1.4 scale shifted up 6, happy face; no bubble) fades in over 100 ms, hops `16 * sin(pi * min(1, t/220))` dp up, and floats up `50 * easeOut(t/600)` dp in total, fading out from 360 to 600 ms. Total 0.6 s.
`easeOut(k) = 1 - (1 - k)^3`. If a pop would take the sparkle count over 60 it pops without sparkles.

### Misses
Target opacity = `clamp((H - y) / (0.1 * H), 0, 1)` with y = its centre and H the screen height: it is fully opaque until 0.1 H above the bottom and gone as its centre reaches the bottom edge. It is drawn **behind** the ship and stars; no bounce, wobble, sound, counter or colour change of anything else.

### Carriers and gifts
- **Carrier shell:** round bubble or oval balloon only (S at least 96 so the gift is at least 48dp; the moon and critter have no room). Inside it, over the body centre: sunshine halo r 44 units at 10 to 24% and r 39 units at 18 to 38% (swelling once per 1.6 s: `0.5 + 0.5 sin(2 pi t / 1.6 + 1)`), a white disc r 34 units with a 2dp sunshine rim, and the gift picture at **0.5 S** across (bobs `2.5 sin(2 pi t / 1.6)` units up and down).
- **Gift pictures** (100 grid, centred; ink rim 40% at 2dp constant width where noted):
  - Triple star: three sunshine stars (outer radius, centre): 25 at (50, 30), 19 at (22, 66) tilted -14, 19 at (78, 66) tilted +14.
  - Big star: one sunshine star radius 44 at (50, 52), sparkles radius 9 at (86, 16) and 6 at (14, 86).
  - Rainbow ribbon: four semicircle arcs about (50, 74), radii 36, 28, 20, 12, 8 wide, round caps, colours `#FF7A70`, `#FFD23F`, `#6FD08C`, `#4FC1E9`; an ink 30% under-stroke 10.5 wide beneath all four.
  - Slow drift (snail): body rounded rect x 12 y 62 w 78 h 15 rx 7.5 `#F6B57A`; head circle (20, 56) r 11; two stalks `M16,47 L13,33` and `M26,46 L29,32` (4 wide) ending in white eye circles r 4.4 at (13, 31) and (29, 30); shell circle (58, 52) r 24 `#9B6BE0` with a white 3-wide spiral `M58,52 c0,-4 6,-4 6,0 c0,8 -12,8 -12,0 c0,-12 18,-12 18,0 c0,15 -24,15 -24,0`; smile `M15,60 Q20,64 25,60`.
  - Sparkle wave: sine `M8,64 C18,44 30,44 40,64 C50,84 62,84 72,64 C77,54 84,50 92,52` in sky `#28A9E3`, 9 wide, round caps, ink 30% under-stroke 13 wide; three sunshine sparkles radius 10 at (26, 26), 8 at (62, 22), 6 at (86, 34).
  Each picture is a different shape in greyscale.
- **Release:** at the moment of the pop the gift is revealed at the carrier's centre and flies free: **48dp**, drawn as a white disc r 28 (ink 30% rim, 1.5dp) with a sunshine 2.5dp ring at r 25, sunshine halos r 34 (26%) and r 40 (14%), the picture 44dp across. It glides for **0.6 s** along a quadratic curve from P0 (carrier centre) to P1 (ship nose: ship centre x, box top minus 4), control point C = (x0 + 0.2 (x1 - x0), y0 - 26), position parameter `e = easeInOutQuad(t / 0.6)` (`2k^2` below half, `1 - 2(1-k)^2` above). P1 follows the ship if it moves. A trail of three ghost copies (60% size, 24 to 44% opacity) follows it. It fades to about 60% over the last 15%.
- **Arrival:** a ring (white 5dp and sunshine 2.5dp, radius 30 growing to 60dp, fading 80% to 0 over 0.6 s) and six sparkles (radius 8 and 6, distance 34 to 60dp from the ship, `sin(pi k)` envelope) around the ship's nose, and the effect starts. No sound cue exists yet (the whole game is silent).

### Effects (per gift)
| Gift | Duration | Look |
|---|---|---|
| Triple star | 8 s | Three stars per shot (angles 0, +-12); ship sunshine glow. |
| Big star | 8 s | 56dp stars, each with a trail of three copies behind it (radius 0.5, 0.4, 0.32 of the star, at 30%, 18%, 9%, spaced about 34dp), flying through targets; ship sunshine glow. |
| Rainbow ribbon | 6 s | A column 56dp wide (five equal stripes 11.2dp: `#FF8A80`, `#FFD23F`, `#8FDDA0`, `#5CC8F0`, `#B49AF0` at 92%) on a white 62dp band at 70%, from the ship's nose up to the top, swaying `5 * sin((y/140 + 0.35 t) 2 pi)` dp sideways; it grows from the nose at 1200 dp/s. While it runs it **replaces the stars** (they would be hidden in it). Ship sunshine glow. |
| Slow drift | 8 s | Every target gets two soft lilac ellipses behind it, `#B79BF0`: body ellipse grown by 0.18 S at 16% and by 0.09 S at 26%; targets drift at half speed; ship lilac glow. Runs alongside any of the others. |
| Sparkle wave | instant, about 1.5 s | A wavy band across the full width, centre line `y0 + 11 sin(2 pi x / 96 + 3 t)` dp, drawn as three round polylines (46dp white 16%, 28dp sunshine 30%, 10dp white 80%) with ten sparkles (radius 5 to 9) riding on it; it rises from just below the ship's nose to above the top edge in about 1.5 s (about 490 dp/s) and pops each ordinary target as its centre is reached (only if at least one ordinary, non-carrier target was on screen when the gift arrived; otherwise the band still glitters and pops nothing). Carriers do not count and are skipped. No glow. |
Replacement and stacking follow story 59.

### Breathing glow (last 2 seconds)
With `remaining` seconds left on a glow, glow opacity is multiplied by `b(remaining)`: `b = 1` while `remaining > 2`; then with `tau = 2 - remaining`: `0.675 + 0.325 cos(2 pi tau)` for `tau < 1.75` (a one-second breath between 100% and 35%: full, dimmest, full, dimmest), then `0.675 * (2 - tau) / 0.25` over the last quarter second so it fades away rather than switching off. Nothing else changes; no numbers. Applies separately to the star-effect glow and to the slow-drift glow.

### On-screen limits and flicker
At most **8 targets, 15 stars, 60 sparkles, 1 gift in flight** (a new one that would exceed a limit is not created; a target spawn is retried 0.3 s later). Nothing flashes or blinks faster than 3 times a second: the fastest periodic motions are the flame (1 s), the glow breath (1 s), the carrier's swell and bob (1.6 s) and sparkle envelopes (0.33 s, each once).

### Layout facts (360 x 692dp mock play area)
Home button 56dp at (20, 20). Ship box y 580 to 668 (nose at 580). Stage 1 (frame 2) has four bubbles 104-112dp; stage 5 (frame 4) has eight targets above y 522, the ship's column clear from y 456 down, and the smallest gap between any two targets about 22dp (the mockup measures it live).

### Implementation notes (2026-09-25, from building it)
No new tokens were needed. Where the build chose a number the design left open: the sky fills the play area inside the system bars (cream shows behind the bars, as in the mockup); the heart and moon paths are stored already centred (heart scaled 1.11) so the 3dp rim keeps its true width; the 3dp, 2dp and 1.6dp rims and strings are constant on screen for every whole-dp size 40 to 119 (stroke widths are made once per size); strokes inside the gift pictures are a fixed 4 units of the 100 grid (2dp at a carrier's 0.5 S), and the stars inside a gift use a fixed 7.14 rim (in star units) instead of a constant 2dp; a big star's rim is 2dp and its three ghosts sit 34, 68 and 102dp behind it; the ribbon pops a target whose ellipse centre is within `28 + 0.9 rx` of the ship's column and whose centre is above the nose; the wave's ten riding sparkles are spread across the width (not fixed at 36dp steps) and count against the 60-sparkle limit, as do the pop's seven and the arrival's six (a pop over the limit plays its ring without sparkles); the ship's fin reach is 38dp and its centre is kept in [46, width - 46]; a first target's body bottom starts 40dp down so it is already partly in view. Sound is not part of the build (pop and gift sounds are unmet pending the founder's app-wide decision).

**After review (2026-09-25):** the wave's arrival condition became "at least one ordinary target on screen" (from "3 or more", which left it popping nothing in about 9 of 10 arrivals). The sky is its own still layer with its gradient cached, and per-frame drawing reuses its strokes and layer bounds. The ribbon uses fewer than one point per 10dp on a very tall play area so it always reaches the top. Drawing phases (flame, carrier bob, ribbon sway, wave) are wrapped in double so a long session keeps its precision.

### Decisions awaiting founder OK (mockup review)
Teal badge; pale sky (not dark space); the six colours including deep Teal and Berry; the white-rocket-with-fox ship; critter ears/eyes poking out of the bubble to make its silhouette distinct; carriers limited to round bubbles and oval balloons (narrows the PRD's "any target"); the ribbon replacing the stars while it runs; slow drift shown as a lilac halo on every target; missed targets only fade (no bounce).

## Score, paws and the good-game screen (Paw Blocks and Paw Pop)

Added 2026-09-25 for PRD stories 63-73. Mockup: https://claude.ai/artifact/PMahD5NFUaXC9yr5m3qaif (private; its raw HTML has every path as working code: `paw`, `rosette`, `crown`, `fox`, `goodGame`, `bank`). Chrome is unchanged. No new theme tokens: everything below is content colour or existing chrome. Sizes are dp on the 360 x 692 play area of a 360 x 800 phone; type is the existing rounded face with **tabular (equal-width) figures**, weight 800.

### Top strip (both games)
| Element | Spec |
|---|---|
| Home button | Unchanged: 56dp, top-left at (20, 20) |
| Paws | 3 paws, **32dp** each, 8dp gaps, at x = 92, 132, 172, top y = 32 (vertical centre 48). Not tappable |
| Score | Right-aligned to x = 340 (20dp margin), baseline y = 61, **32sp**, weight 800, ink `#2B2320`, plain digits (no commas, no label, no icon). Six digits (999,999, the cap) still fit between x 212 and 340; if the font is wider, shrink to 26sp rather than move the paws |
| Order | Left to right: home, paws, score. The score is never under the home button, the fox's peek lane, or a dragged block |

Contrast (WCAG luminance ratio, measured): ink on cream 14.2, ink on the top of Pop's sky 10.4, ink on the cloud bank about 11. Score count-up runs under 0.4s with no flash, bounce, sound or colour change.

**Blocks vertical layout (changed):** the fox peeks up from behind the board's top-right edge and its ears used to reach y 34, which is exactly where the score sits. The board panel therefore starts at **y 132** (was 96) and the tray at **y 520** (was 508): the fox now reaches y 70, clear of the strip (which ends at y 76 for the home button, and at y 62 for the score digits). The fox's own coordinates relative to the board are unchanged.

### Paw (the rescue / life picture, same in both games)
- Drawn on Paw Match's `PawPrintIcon` 80-unit grid (ellipses: pad `(40,52,20,16)`, toes `(18,30,8,10)`, `(34,18,8,10)`, `(52,18,8,10)`, `(64,32,8,10)` as cx, cy, rx, ry), at 32dp.
- **Full paw:** fill coral `#FF6B4A`, ink `#2B2320` 4.5-unit outline (about 1.8dp) with round joins, plus a white 70% shine ellipse `(33,46)` rx 8 ry 3.6 rotated -25 degrees.
- **Used paw:** the same silhouette, **no shine, fill white at 40%, ink at 55% outline, 5.5 units wide, dashed 8 on / 7 off, round caps**. Hollow and dotted, so it differs from a full paw in shape and pattern, not only in colour.
- **Fade (0.6s):** the full layer's opacity goes 1 to 0 while the used layer goes 0 to 1 (linear-ish, ease). Paws are lost from the **right**: the rightmost full paw fades first. A used paw never returns. Nothing else on screen reacts.
- Not tappable, no content description needed beyond "paws left" for screen readers.
- Greyscale and red-green checks are in the mockup: coral solid vs white dotted differs by more than 3:1 in luminance and by fill vs hollow.

### Blocks: the quiet clear-out with a paw
Unchanged look (white wash 50%, rows scale to 0.72 and fade, pale sparkles, 900ms, story 45). Added: the **rightmost full paw fades to used over 0.6s**, starting at the same instant as the wash (t = 200ms of the 900ms). One paw per clear-out however many rows leave. No message, no animal, no sound beyond the existing whoosh. In the mockup the third paw fades while the two top rows leave (press Replay).

### Pop: the top strip, the entry line and a lost paw
- **Strip and entry line.** The strip is a **cloud bank**: fill white at 38%, 3dp white 90% edge, its bottom edge is 15 half-ellipse scallops 24dp wide and 14dp tall, hanging from y 86, so the lowest points are at **y 100 = the entry line**. Home, paws and score sit on it exactly as in Blocks. A flat variant (straight edge at y 100, white 38%, 3dp white line) is the alternative if the scallops are not wanted; behaviour is identical.
- **Targets.** A new target starts fully above the line, behind the bank, and enters heading straight down. It is **clipped by the bank's lower edge** (draw targets in a clip region below the scalloped edge, then draw the bank over them). Until its whole picture is below the line (its top edge past y 100) it is **veiled**: drawn at 50% opacity with a dashed rim (dash 9 on / 7 off in a 100-unit target grid, so scaled with the target). Stars pass through it and do not pop it. The moment its whole picture is below the line it becomes solid over 150ms and becomes poppable (the story-66 rule). It makes no turn in its first 0.5s after that.
- **Stars** twinkle out at the line: removed when their top edge reaches y 100. A small sunshine four-point sparkle (radius 9) may show there for 0.33s.
- **Lost paw.** When a plain target begins its bottom fade (story 56 rule: opacity `clamp((H - y) / (0.1 H), 0, 1)`) the rightmost full paw fades to used over 0.6s. The target keeps fading behind the ship. **Grace 3s:** for 3s after a paw is lost, the ship gets a soft **peach glow** (three flat circles under the ship, radii 62 / 52 / 44dp, `#FF9A76` at 16% / 24% / 34%, no blur; fades in over 300ms, out over the last 500ms; does not pulse), and further misses cost nothing. No sound, shake, red or flash. Peach was chosen so it never matches the sunshine glow of star gifts or the lilac glow of slow drift.

### Good-game screen (both games)
Shown by Blocks when stuck with no paws (story 65) and by Pop when the third paw is lost (story 69). One layout, only the background differs.

| Element | Spec |
|---|---|
| Background | **Blocks:** the frozen board and tray under a cream `#FFF3E4` wash at 84%. **Pop:** the sky with the ship resting at 50% opacity (no flame change), under a white wash at 20%. Paws and score strip are hidden (a row of hollow paws would read as sad). No red, no grey-out |
| Corner home | The normal 56dp home button at (20, 20), drawn on top, always works, never locked |
| Animal | Fox (the Blocks peek-up fox), **132dp** box at (114, 120), ears up. Any of the six Paw Match critters may be used, rotating. **Normal:** the calm face (dot eyes, small smile, faint cheeks). **New best:** the happy face (squeezed arched eyes, open smile with tongue, pink cheeks). Never a sad, worried or crying face |
| Card | 300 x 208dp white, radius 29, 3dp edge `#EADFCF` at (30, 232). On new best the edge is sunshine `#FFD23F` |
| Score | This game's score: **68sp**, weight 800, ink, centred, baseline y 310. No label and no icon (the number alone is this game) |
| Divider | 2dp `#EADFCF` line, x 64 to 296, y 336 |
| Best score | A pill 72dp tall (y 352 to 424), centred, holding the **rosette (56dp)**, a 14dp gap, then the best score at **44sp**, weight 800. Pill width = 56 + 14 + digits x 27 + 40 padding. Normal: fill `#FBF4EA`, 2dp `#EADFCF` edge. New best: fill `#FFF3C4`, 2.5dp sunshine edge plus the glow below |
| Buttons | Play again: 88dp circle, leaf `#35C46B`, white paw 40dp (same as Match, Pour), centre (120, 516). Home: 88dp circle, sky `#4FC1E9`, white house 40dp, centre (240, 516). **32dp between them** (the older win screens use 24; 32 here because a child mashes this screen more). Both far above the 72dp minimum |
| Adult gate | None needed: nothing here reaches money, a link or settings |

**Rosette (best-score cue, chosen over a coin, star or crown).** Drawn on a 64-unit box, ink 45% 2-unit outline, round joins: two ribbon tails (`M-9,10 L-19,32 L-10,27 L-4,33 L1,12Z` in sky `#4FC1E9`, and its mirror `M9,10 L19,32 L10,27 L4,33 L-1,12Z` in `#2FA0CC`); ten scallop circles r 8 at radius 17 around the centre in grape `#8E59E6`; a grape disc r 19.5; a white ring r 13; a grape disc r 10.5; a white paw (80-grid paw scaled to 13 units) in the middle. Shown 56dp on the screen. Its silhouette (scalloped disc with two tails) is unlike a coin or a star in greyscale. White on grape measures 4.4. **Alternative shown, not chosen:** a sunshine crown (`M-22,14 L-26,-16 L-11,-2 L0,-22 L11,-2 L26,-16 L22,14Z`, `#FFD23F`, rim `#D99A00`, band beneath, three jewel dots); it reads a little closer to gold. The founder picks between the two; until then build the rosette.

### Motion and timing
| Moment | Behaviour | Time |
|---|---|---|
| Score change | Count-up, no flash or bounce | under 0.4s |
| Paw fade | Solid to hollow dotted | 0.6s |
| Blocks clear-out | Existing look; paw fades from t = 200ms | 0.9s |
| Pop grace | Peach ship glow, misses free | 3s (fades in 0.3s, out last 0.5s) |
| Pop entry | Veiled until fully below the line, then solid | solid over 150ms; no turn for 0.5s |
| Good-game appears | Fades in over the dimmed background, about 1s after the last move (Blocks) or the moment the third paw is lost and the targets have faded (Pop) | 0.5s |
| Button lockout | Play again and home ignore touches. **No visual change** (no greying, no dim, no spinner): the buttons are visible as the screen fades in and simply wake at 0.6s | first 0.6s |
| New best | Best pill glow (three flat sunshine rounded rectangles around the pill, 6 / 12 / 18dp out, 35% / 22% / 12%, breathing 55% to 100% over 1.4s, twice, then resting at 55%); fox **three hops of 18dp, 0.67s each** (starting at 0.5s, so it ends at about 2.5s); six soft four-point sparkles (sunshine and white, radius 7 to 15) twinkling 1.3s each, twice | about 2s after fade-in |
| Rule | Nothing flashes or blinks faster than 3 times a second (hops 1.5/s, glow 0.7/s, sparkles 0.8/s). Reduced motion: no hop and no twinkle, the glow stays steady |
| Never | Red, grey-out, shake, sad face, sound, "game over" or "you lost" wording, comparison to anyone, a count of what went wrong |

### Type and number legibility
Score 32sp in play, 68sp and 44sp on the good-game screen, weight 800, tabular figures, ink on a light ground (at least 10 to 1). Digits must be unambiguous (no confusable 1 / l / I), as the type rule at the top of this file already requires. Numbers are plain digits: no thousands separators, no plus signs, no "+10" popups, no "pts" label.

### Decisions awaiting founder OK (mockup review)
Rosette (recommended) or crown; Pop's cloud-bank top edge (recommended) or a straight edge. Decided by the design, open to objection: paws fade from the right; score top right with paws beside home; Blocks board and tray 36 and 12dp lower; green paw play-again kept; corner home stays on the good-game screen; no paws on the good-game screen.

## Hard rules
- Every screen designed for a phone held one-handed by small hands, landscape or portrait per the game's needs.
- Touch targets ≥48dp; generous spacing between anything tappable.
- No screen puts a money-related action, an external link, or a destructive action (delete save) within one tap of normal play — those sit behind an adult gate.
- Loading, first-run (no save yet), and "try again" states are part of every screen's design, not afterthoughts.
