package com.pawplay.app.games.pawpop

import kotlin.math.pow

/**
 * The five silhouettes as numbers (docs/DESIGN-SYSTEM.md, "Silhouettes"), the gifts, and the six colours.
 * Pure Kotlin, so the rules (hit test, placement) and the tests (colour ladder, distinct outlines) can read them
 * with no Android. The drawing (`PopDrawing.kt`) repeats the path strings; `PopShapesTest` keeps them honest.
 *
 * Local units: S / 100 around the body centre, y down, where S is the target's nominal (drawn) width.
 */
enum class TargetKind(
    /** The hit ellipse as fractions of S: radii, and the centre's offset from the body centre. */
    val rx: Float, val ry: Float, val dx: Float, val dy: Float,
    /** Half the body's height (fractions of S), for placement. */
    val halfHeight: Float,
    /**
     * How far above the centre the whole picture reaches (fractions of S): a target has entered, and can be popped, only
     * when this line is below the entry line. The critter's is its bunny ears, the tallest of its four animals.
     */
    val top: Float,
    /** How far below the centre the whole picture reaches, string included: a new target starts with this above the entry line. */
    val bottom: Float,
) {
    ROUND(0.50f, 0.50f, 0f, 0f, 0.50f, 0.50f, 0.50f),
    OVAL(0.50f, 0.62f, 0f, 0f, 0.62f, 0.62f, 1.12f),
    HEART(0.46f, 0.45f, 0f, -0.02f, 0.47f, 0.50f, 0.94f),
    MOON(0.38f, 0.50f, -0.12f, 0f, 0.50f, 0.50f, 1.00f),
    CRITTER(0.50f, 0.50f, 0f, 0f, 0.50f, 0.87f, 0.50f);

    /** A circle that surely holds the whole hit shape, in fractions of S: used to keep new targets clear of each other. */
    val reach: Float get() = if (rx > ry) rx else ry

    companion object {
        val ALL: Array<TargetKind> = values()
    }
}

/** The five gifts a carrier can hold. */
enum class GiftKind(val seconds: Float) {
    TRIPLE(PopMetrics.TRIPLE_SECONDS),
    BIG(PopMetrics.BIG_SECONDS),
    RIBBON(PopMetrics.RIBBON_SECONDS),
    SLOW(PopMetrics.SLOW_SECONDS),
    WAVE(0f);

    /** Triple, big and ribbon change the stars and never stack; slow drift runs alongside; the wave is instant. */
    val changesStars: Boolean get() = this == TRIPLE || this == BIG || this == RIBBON

    companion object {
        val ALL: Array<GiftKind> = values()
    }
}

/**
 * The six target colours, light to dark, as ARGB. Content colours, not theme tokens: every one of the 15 pairs is at
 * least 1.3 apart in WCAG luminance (`PopShapesTest`), so all stay apart in greyscale, and no colour means anything.
 */
object PopPalette {
    val names: List<String> = listOf("Butter", "Blush", "Tangerine", "Lilac", "Teal", "Berry")
    val argb: IntArray = intArrayOf(
        0xFFFFE58A.toInt(), 0xFFFF9FC4.toInt(), 0xFFF2852F.toInt(), 0xFF9B6BE0.toInt(), 0xFF0A7873.toInt(), 0xFFA31C55.toInt(),
    )
    val count: Int get() = argb.size

    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance of an opaque colour. */
    fun luminance(color: Int): Double =
        0.2126 * channel((color shr 16) and 255) + 0.7152 * channel((color shr 8) and 255) + 0.0722 * channel(color and 255)

    /** WCAG contrast ratio of two colours (1 = identical, 21 = black on white). */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}

/**
 * Path data for the shapes the tests also need, in local units (S / 100 around the body centre), so the drawing and the
 * tests read one source. The heart and moon are the design's 100-grid paths already moved to the centre (the heart is
 * also scaled 1.11), so a stroke drawn on them keeps its true width.
 */
object PopPaths {
    const val HEART =
        "M0,46.62 C-62.16,6.66 -59.94,-39.96 -26.64,-44.4 C-11.1,-46.62 0,-35.52 0,-24.42 C0,-35.52 11.1,-46.62 26.64,-44.4 " +
            "C59.94,-39.96 62.16,6.66 0,46.62Z"
    /** A circle of radius 50 with a circle of radius 40 centred at (18, -10) taken out. */
    const val MOON = "M9.5,-49.1 A50,50 0 1 0 46.7,17.9 A40,40 0 1 1 9.5,-49.1Z"
    const val MOON_CUT_X = 18f
    const val MOON_CUT_Y = -10f
    const val MOON_CUT_R = 40f
    const val OVAL_KNOT = "M0,60 L-8,75 L8,75Z"
    const val OVAL_STRING = "M0,74 C-7,82 7,90 0,98 C-5,104 4,108 1,112"
    const val HEART_KNOT = "M0,44 L-7,58 L7,58Z"
    const val HEART_STRING = "M0,56 C-7,64 7,72 0,80 C-5,86 4,90 1,94"
    const val MOON_KNOT = "M0,48 L-7,62 L7,62Z"
    const val MOON_STRING = "M0,62 C-7,70 7,78 0,86 C-5,92 4,96 1,100"

    /** The critter's ears and eye bumps that poke out of the bubble: circles (x, y, r), fox triangles and bunny ovals are in the drawing. */
    val FOX_EARS: List<List<Pair<Float, Float>>> = listOf(
        listOf(-40f to -24f, -38f to -66f, -10f to -46f),
        listOf(40f to -24f, 38f to -66f, 10f to -46f),
    )
    /** Ear or eye-bump circles per critter (bear, frog): x, y, radius. */
    val BEAR_EARS: List<Triple<Float, Float, Float>> = listOf(Triple(-34f, -36f, 15f), Triple(34f, -36f, 15f))
    val FROG_BUMPS: List<Triple<Float, Float, Float>> = listOf(Triple(-24f, -42f, 15f), Triple(24f, -42f, 15f))
    /** Bunny ears: centre x, centre y, rx, ry, tilt degrees. */
    val BUNNY_EARS: List<FloatArray> = listOf(floatArrayOf(-19f, -60f, 10f, 27f, -8f), floatArrayOf(19f, -60f, 10f, 27f, 8f))
}
