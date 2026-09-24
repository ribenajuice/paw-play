package com.pawplay.app.games.pawtrace

/**
 * A glyph is an ordered list of strokes on a 100 x 100 box (y down), each an SVG path string that
 * uses only absolute M, L and C. The first point of a stroke is where the frog stands, and the path
 * direction is the direction the paw prints point. The list order is only where the frog goes next
 * when nothing is painted; it is never enforced (docs/DESIGN-SYSTEM.md, "Glyph data conventions").
 */
data class Glyph(val id: String, val stage: Int, val pathData: List<String>) {
    /** The strokes as parsed paths (for drawing the guide exactly). Computed once, on first use. */
    val paths: List<StrokePath> by lazy { pathData.map { parseStrokePath(it) } }

    /** The strokes sampled about every 1.5 units (for painting and coverage). Computed once, on first use. */
    val strokes: List<SampledStroke> by lazy { paths.map { sampleStroke(it) } }
}

/**
 * The 46 Paw Trace glyphs, in ramp order (docs/PRD.md, "Content ramp"). The strings are the ones in
 * docs/glyphs/paw-trace-glyphs.md, which the founder-approved mockup renders; `TraceGlyphsTest`
 * checks this list still matches that file. Adding a glyph later means adding one line here.
 */
object TraceGlyphs {
    const val STAGE_COUNT = 4

    private fun glyph(id: String, stage: Int, vararg strokes: String) = Glyph(id, stage, strokes.toList())

    val all: List<Glyph> = listOf(
        // Stage 1: lines and curves
        glyph("line-h", 1, "M14,50 L86,50"),
        glyph("line-v", 1, "M50,14 L50,86"),
        glyph("line-slant", 1, "M24,14 L76,86"),
        glyph("arch", 1, "M14,78 C14,3 86,3 86,78"),
        glyph("wave", 1, "M12,50 C18,22 31,22 37,50 C43,78 57,78 63,50 C69,22 82,22 88,50"),

        // Stage 2: shapes
        glyph("circle", 2, "M50,14 C30.12,14 14,30.12 14,50 C14,69.88 30.12,86 50,86 C69.88,86 86,69.88 86,50 C86,30.12 69.88,14 50,14"),
        glyph("square", 2, "M18,18 L18,82 L82,82 L82,18 L18,18"),
        glyph("triangle", 2, "M50,16 L16,84 L84,84 L50,16"),
        glyph("cross", 2, "M50,16 L50,84", "M16,50 L84,50"),
        glyph("heart", 2, "M50,32 C42,14 14,14 16,38 C18,58 42,76 50,88 C58,76 82,58 84,38 C86,14 58,14 50,32"),

        // Stage 3: numbers, counting order, 0 last
        glyph("digit-1", 3, "M32,32 L54,12 L54,88"),
        glyph("digit-2", 3, "M26,34 C26,8 74,8 74,34 C74,52 44,66 24,88 L78,88"),
        glyph("digit-3", 3, "M28,22 C34,8 70,8 70,30 C70,44 60,46 42,47 C58,49 74,52 74,68 C74,92 34,94 26,76"),
        glyph("digit-4", 3, "M62,12 L16,62 L84,62", "M62,12 L62,88"),
        glyph("digit-5", 3, "M32,12 L28,46 C36,40 46,38 54,38 C72,38 78,52 78,64 C78,80 66,88 50,88 C40,88 30,84 24,76", "M32,12 L74,12"),
        glyph("digit-6", 3, "M64,12 C40,22 26,44 26,62 C26,80 38,88 52,88 C68,88 76,76 76,64 C76,50 66,42 52,42 C40,42 30,50 26,62"),
        glyph("digit-7", 3, "M22,12 L78,12 L40,88"),
        glyph("digit-8", 3, "M50,12 C37.85,12 28,20.51 28,31 C28,41.49 37.85,50 50,50 C64.36,50 76,58.51 76,69 C76,79.49 64.36,88 50,88 C35.64,88 24,79.49 24,69 C24,58.51 35.64,50 50,50 C62.15,50 72,41.49 72,31 C72,20.51 62.15,12 50,12"),
        glyph("digit-9", 3, "M50,12 C35.64,12 24,22.75 24,36 C24,49.25 35.64,60 50,60 C64.36,60 76,49.25 76,36 C76,22.75 64.36,12 50,12", "M76,36 L76,88"),
        glyph("digit-0", 3, "M50,12 C35.6,12 24,29 24,50 C24,71 35.6,88 50,88 C64.4,88 76,71 76,50 C76,29 64.4,12 50,12"),

        // Stage 4: capital letters, straight strokes first, curvy last
        glyph("letter-I", 4, "M50,12 L50,88", "M32,12 L68,12", "M32,88 L68,88"),
        glyph("letter-L", 4, "M28,12 L28,88 L74,88"),
        glyph("letter-T", 4, "M22,12 L78,12", "M50,12 L50,88"),
        glyph("letter-H", 4, "M26,12 L26,88", "M74,12 L74,88", "M26,50 L74,50"),
        glyph("letter-E", 4, "M28,12 L28,88", "M28,12 L76,12", "M28,50 L70,50", "M28,88 L76,88"),
        glyph("letter-F", 4, "M28,12 L28,88", "M28,12 L76,12", "M28,50 L68,50"),
        glyph("letter-A", 4, "M50,12 L20,88", "M50,12 L80,88", "M30,62 L70,62"),
        glyph("letter-V", 4, "M20,12 L50,88 L80,12"),
        glyph("letter-W", 4, "M14,12 L32,88 L50,28 L68,88 L86,12"),
        glyph("letter-M", 4, "M22,12 L22,88", "M22,12 L50,62 L78,12", "M78,12 L78,88"),
        glyph("letter-N", 4, "M26,12 L26,88", "M26,12 L74,88", "M74,12 L74,88"),
        glyph("letter-Z", 4, "M24,12 L76,12 L24,88 L76,88"),
        glyph("letter-K", 4, "M28,12 L28,88", "M74,12 L28,52 L76,88"),
        glyph("letter-X", 4, "M24,12 L76,88", "M76,12 L24,88"),
        glyph("letter-Y", 4, "M22,12 L50,48 L78,12", "M50,48 L50,88"),
        glyph("letter-O", 4, "M50,12 C32,12 18,29 18,50 C18,71 32,88 50,88 C68,88 82,71 82,50 C82,29 68,12 50,12"),
        glyph("letter-C", 4, "M76,26 C68,16 60,12 50,12 C32,12 18,29 18,50 C18,71 32,88 50,88 C60,88 68,84 76,74"),
        glyph("letter-U", 4, "M22,12 L22,58 C22,79 34,88 50,88 C66,88 78,79 78,58 L78,12"),
        glyph("letter-J", 4, "M64,12 L64,60 C64,80 54,88 42,88 C32,88 24,82 22,70"),
        glyph("letter-D", 4, "M28,12 L28,88", "M28,12 C52,12 76,24 76,50 C76,76 52,88 28,88"),
        glyph("letter-P", 4, "M28,12 L28,88", "M28,12 C56,12 74,20 74,34 C74,50 56,56 28,56"),
        glyph("letter-B", 4, "M28,12 L28,88", "M28,12 C58,12 72,20 72,32 C72,44 58,50 28,50", "M28,50 C64,50 78,58 78,69 C78,80 62,88 28,88"),
        glyph("letter-R", 4, "M28,12 L28,88", "M28,12 C56,12 74,20 74,32 C74,44 56,52 28,52", "M44,51 L76,88"),
        glyph("letter-G", 4, "M76,26 C68,16 60,12 50,12 C32,12 18,29 18,50 C18,71 32,88 50,88 C66,88 78,80 78,64 L78,56 L54,56"),
        glyph("letter-S", 4, "M74,26 C68,16 60,12 50,12 C34,12 26,20 26,30 C26,42 36,46 50,50 C64,54 74,58 74,70 C74,82 66,88 50,88 C40,88 30,84 24,74"),
        glyph("letter-Q", 4, "M50,12 C32,12 18,29 18,50 C18,71 32,88 50,88 C68,88 82,71 82,50 C82,29 68,12 50,12", "M56,64 L80,88"),
    )

    private val byId: Map<String, Glyph> = all.associateBy { it.id }

    fun get(id: String): Glyph = byId.getValue(id)

    /** The glyphs of one stage (1..[STAGE_COUNT]) in the order they are played. */
    fun stage(stage: Int): List<Glyph> = all.filter { it.stage == stage }
}
