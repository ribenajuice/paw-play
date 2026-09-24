# Paw Trace glyph set (centreline data)

Source of truth for the 46 Paw Trace glyphs. Drawn by the ui-designer; the founder-approved mockup renders exactly these strings. Conventions live in `docs/DESIGN-SYSTEM.md`, section "Paw Trace: guide, paint, markers and glyphs". The short version:

- Every glyph lives in a **100 x 100 box**, y grows downward. Centrelines stay inside x 12..88, y 11..89 (the guide band adds 8.75 units each side).
- One line per glyph: `id | stroke ; stroke ; ...`. Each stroke is one SVG path string. Only absolute `M`, `L`, `C` commands are used, one `M` per stroke, no `Z` (a closed shape ends on its own start point).
- **The first point of a stroke is its start point** (where the marker sits) and the path direction is the direction to trace. The order of strokes is the order the marker visits them when nothing is painted yet; it is never enforced.
- Stage numbers match the PRD content ramp. The order of ids inside each stage is the ramp order.

To use in Kotlin: parse each string with `androidx.compose.ui.graphics.vector.PathParser().parsePathString(s).toPath()` (or split on spaces/commas yourself: the subset is tiny), then measure with `PathMeasure` and sample every 1.5 units.

## Stage 1: lines and curves (5)

```
line-h     | M14,50 L86,50
line-v     | M50,14 L50,86
line-slant | M24,14 L76,86
arch       | M14,78 C14,3 86,3 86,78
wave       | M12,50 C18,22 31,22 37,50 C43,78 57,78 63,50 C69,22 82,22 88,50
```

## Stage 2: shapes (5)

Closed shapes start at the top (or top-left corner) and go counter-clockwise, and end exactly on their start point.

```
circle     | M50,14 C30.12,14 14,30.12 14,50 C14,69.88 30.12,86 50,86 C69.88,86 86,69.88 86,50 C86,30.12 69.88,14 50,14
square     | M18,18 L18,82 L82,82 L82,18 L18,18
triangle   | M50,16 L16,84 L84,84 L50,16
cross      | M50,16 L50,84 ; M16,50 L84,50
heart      | M50,32 C42,14 14,14 16,38 C18,58 42,76 50,88 C58,76 82,58 84,38 C86,14 58,14 50,32
```

## Stage 3: numbers (10, counting order, 0 last)

Digit 1 is a single stroke (flag then stem). Digit 4 is the closed kind: a diagonal-and-crossbar stroke plus a vertical that crosses it. Digit 3 has a deliberate cusp in the middle (the stroke doubles back).

```
digit-1    | M32,32 L54,12 L54,88
digit-2    | M26,34 C26,8 74,8 74,34 C74,52 44,66 24,88 L78,88
digit-3    | M28,22 C34,8 70,8 70,30 C70,44 60,46 42,47 C58,49 74,52 74,68 C74,92 34,94 26,76
digit-4    | M62,12 L16,62 L84,62 ; M62,12 L62,88
digit-5    | M32,12 L28,46 C36,40 46,38 54,38 C72,38 78,52 78,64 C78,80 66,88 50,88 C40,88 30,84 24,76 ; M32,12 L74,12
digit-6    | M64,12 C40,22 26,44 26,62 C26,80 38,88 52,88 C68,88 76,76 76,64 C76,50 66,42 52,42 C40,42 30,50 26,62
digit-7    | M22,12 L78,12 L40,88
digit-8    | M50,12 C37.85,12 28,20.51 28,31 C28,41.49 37.85,50 50,50 C64.36,50 76,58.51 76,69 C76,79.49 64.36,88 50,88 C35.64,88 24,79.49 24,69 C24,58.51 35.64,50 50,50 C62.15,50 72,41.49 72,31 C72,20.51 62.15,12 50,12
digit-9    | M50,12 C35.64,12 24,22.75 24,36 C24,49.25 35.64,60 50,60 C64.36,60 76,49.25 76,36 C76,22.75 64.36,12 50,12 ; M76,36 L76,88
digit-0    | M50,12 C35.6,12 24,29 24,50 C24,71 35.6,88 50,88 C64.4,88 76,71 76,50 C76,29 64.4,12 50,12
```

Digit 8 is two stacked loops that touch at (50,50); one stroke, and it passes (50,50) twice, heading east both times.

## Stage 4: letters (26, capitals only, ramp order)

Group 1, straight strokes first: I L T H E F.

```
letter-I   | M50,12 L50,88 ; M32,12 L68,12 ; M32,88 L68,88
letter-L   | M28,12 L28,88 L74,88
letter-T   | M22,12 L78,12 ; M50,12 L50,88
letter-H   | M26,12 L26,88 ; M74,12 L74,88 ; M26,50 L74,50
letter-E   | M28,12 L28,88 ; M28,12 L76,12 ; M28,50 L70,50 ; M28,88 L76,88
letter-F   | M28,12 L28,88 ; M28,12 L76,12 ; M28,50 L68,50
```

Group 2, slants: A V W M N Z K X Y.

```
letter-A   | M50,12 L20,88 ; M50,12 L80,88 ; M30,62 L70,62
letter-V   | M20,12 L50,88 L80,12
letter-W   | M14,12 L32,88 L50,28 L68,88 L86,12
letter-M   | M22,12 L22,88 ; M22,12 L50,62 L78,12 ; M78,12 L78,88
letter-N   | M26,12 L26,88 ; M26,12 L74,88 ; M74,12 L74,88
letter-Z   | M24,12 L76,12 L24,88 L76,88
letter-K   | M28,12 L28,88 ; M74,12 L28,52 L76,88
letter-X   | M24,12 L76,88 ; M76,12 L24,88
letter-Y   | M22,12 L50,48 L78,12 ; M50,48 L50,88
```

Group 3, curvy: O C U J D P B R G S Q.

```
letter-O   | M50,12 C32,12 18,29 18,50 C18,71 32,88 50,88 C68,88 82,71 82,50 C82,29 68,12 50,12
letter-C   | M76,26 C68,16 60,12 50,12 C32,12 18,29 18,50 C18,71 32,88 50,88 C60,88 68,84 76,74
letter-U   | M22,12 L22,58 C22,79 34,88 50,88 C66,88 78,79 78,58 L78,12
letter-J   | M64,12 L64,60 C64,80 54,88 42,88 C32,88 24,82 22,70
letter-D   | M28,12 L28,88 ; M28,12 C52,12 76,24 76,50 C76,76 52,88 28,88
letter-P   | M28,12 L28,88 ; M28,12 C56,12 74,20 74,34 C74,50 56,56 28,56
letter-B   | M28,12 L28,88 ; M28,12 C58,12 72,20 72,32 C72,44 58,50 28,50 ; M28,50 C64,50 78,58 78,69 C78,80 62,88 28,88
letter-R   | M28,12 L28,88 ; M28,12 C56,12 74,20 74,32 C74,44 56,52 28,52 ; M44,51 L76,88
letter-G   | M76,26 C68,16 60,12 50,12 C32,12 18,29 18,50 C18,71 32,88 50,88 C66,88 78,80 78,64 L78,56 L54,56
letter-S   | M74,26 C68,16 60,12 50,12 C34,12 26,20 26,30 C26,42 36,46 50,50 C64,54 74,58 74,70 C74,82 66,88 50,88 C40,88 30,84 24,74
letter-Q   | M50,12 C32,12 18,29 18,50 C18,71 32,88 50,88 C68,88 82,71 82,50 C82,29 68,12 50,12 ; M56,64 L80,88
```

## Counts and checks

- 5 + 5 + 10 + 26 = 46 glyphs. Multi-stroke: cross 2, 4 (2), 5 (2), 9 (2), I 3, T 2, H 3, E 4, F 3, A 3, M 3, N 3, K 2, Y 2, D 2, P 2, B 3, R 3, Q 2, X 2. Everything else is one stroke.
- Spacing rule the data was drawn to: centrelines of strokes that do not join stay at least 20 units apart (band 17.5 units at the 320dp reference box, so a gap of at least 2.5 units remains between bands), and joined strokes simply overlap where they meet. Tightest cases: Q tail start to the ring (about 20), digit 3 tip to its middle cusp (about 33), G bar to the C arm tip (30).
- The mockup's "Show 64 dp paint corridor" switch overlays the band and corridor on every card of the glyph sheet so any overlap can be reviewed by eye.
