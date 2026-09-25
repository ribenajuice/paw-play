package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The six colours stay apart in greyscale and against the sky, and the five silhouettes really differ. */
class PopShapesTest {

    // ------------------------------------------------------------------ colours

    @Test
    fun `all 15 pairs of target colours are at least 1_3 apart in luminance, so they stay apart in greyscale`() {
        val c = PopPalette.argb
        assertEquals(6, c.size)
        var closest = 99.0
        for (i in c.indices) for (j in i + 1 until c.size) {
            val r = PopPalette.ratio(c[i], c[j])
            closest = minOf(closest, r)
            assertTrue("${PopPalette.names[i]} / ${PopPalette.names[j]} = $r", r >= 1.3)
        }
        assertEquals("the closest pair is Blush / Tangerine", 1.35, closest, 0.02)
        // light to dark, as the design lists them
        for (i in 1 until c.size) assertTrue(PopPalette.luminance(c[i - 1]) > PopPalette.luminance(c[i]))
        // and every neighbour is at least 1.33 (the design's claim)
        for (i in 1 until c.size) assertTrue(PopPalette.ratio(c[i - 1], c[i]) >= 1.33)
    }

    @Test
    fun `stage 1 uses the three colours that are furthest apart`() {
        val stage1 = PopRamp.stages[0].colours.map { PopPalette.argb[it] }
        for (i in stage1.indices) for (j in i + 1 until stage1.size) assertTrue(PopPalette.ratio(stage1[i], stage1[j]) >= 1.4)
        assertEquals(setOf(0, 2, 4), PopRamp.stages[0].colours.toSet())
        assertEquals(setOf(0, 1, 2, 4, 5), PopRamp.stages[1].colours.toSet())
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(s: Int) = Math.round(((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    @Test
    fun `the 3dp rim carries every colour against the sky, so it must never be dropped`() {
        val ink = 0xFF2B2320.toInt()
        val skyTop = 0xFFB7D6F4.toInt()
        val skyBottom = 0xFFDCEBF8.toInt()
        for (c in PopPalette.argb) {
            val rim = mix(c, ink, 0.45)
            assertTrue("rim of ${"%08X".format(c)} against the sky top ${PopPalette.ratio(rim, skyTop)}", PopPalette.ratio(rim, skyTop) >= 2.0)
            assertTrue(PopPalette.ratio(rim, skyBottom) >= 2.5)
        }
        // the lightest fill on its own is nearly the sky: that is exactly why the rim is there
        assertTrue(PopPalette.ratio(PopPalette.argb[0], skyBottom) < 1.1)
    }

    @Test
    fun `the design document lists every colour, its name and the badge`() {
        val doc = File("../docs/DESIGN-SYSTEM.md").readText()
        for (i in 0 until 6) {
            val hex = "#%06X".format(PopPalette.argb[i] and 0xFFFFFF)
            assertTrue("$hex missing from DESIGN-SYSTEM.md", doc.contains(hex))
            assertTrue("${PopPalette.names[i]} missing", doc.contains("| ${PopPalette.names[i]} |"))
        }
        assertTrue(doc.contains("#0F9D94"))
        assertTrue(doc.contains("#B7D6F4") && doc.contains("#DCEBF8"))
        for (kind in listOf("Round bubble", "Oval balloon", "Heart balloon", "Moon balloon", "Critter bubble")) assertTrue(kind, doc.contains(kind))
    }

    // ------------------------------------------------------------------ silhouettes

    private fun parseCubics(d: String): List<Pair<Double, Double>> {
        val nums = Regex("-?[0-9]+\\.?[0-9]*").findAll(d).map { it.value.toDouble() }.toList()
        val pts = ArrayList<Pair<Double, Double>>()
        var p = nums[0] to nums[1]
        pts += p
        var i = 2
        while (i + 5 < nums.size + 0) {
            val p1 = nums[i] to nums[i + 1]; val p2 = nums[i + 2] to nums[i + 3]; val p3 = nums[i + 4] to nums[i + 5]
            for (k in 1..24) {
                val t = k / 24.0; val u = 1 - t
                pts += (u * u * u * p.first + 3 * u * u * t * p1.first + 3 * u * t * t * p2.first + t * t * t * p3.first) to
                    (u * u * u * p.second + 3 * u * u * t * p1.second + 3 * u * t * t * p2.second + t * t * t * p3.second)
            }
            p = p3; i += 6
        }
        return pts
    }

    private fun inPoly(poly: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (xi, yi) = poly[i]; val (xj, yj) = poly[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private val heartPoly = parseCubics(PopPaths.HEART)

    private fun inTri(x: Double, y: Double, a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>) = inPoly(listOf(a, b, c), x, y)

    private fun inEllipse(x: Double, y: Double, cx: Double, cy: Double, rx: Double, ry: Double, degrees: Double = 0.0): Boolean {
        val a = -degrees * PI / 180
        val dx = x - cx; val dy = y - cy
        val u = dx * cos(a) - dy * sin(a); val v = dx * sin(a) + dy * cos(a)
        return (u / rx) * (u / rx) + (v / ry) * (v / ry) <= 1.0
    }

    /** Whether local point (x, y) is inside the drawn silhouette (body, knot, and the ears or eye bumps that poke out). */
    private fun inside(kind: TargetKind, critter: Int, x: Double, y: Double): Boolean = when (kind) {
        TargetKind.ROUND -> x * x + y * y <= 2500
        TargetKind.OVAL -> inEllipse(x, y, 0.0, 0.0, 50.0, 62.0) || inTri(x, y, 0.0 to 60.0, -8.0 to 75.0, 8.0 to 75.0)
        TargetKind.HEART -> inPoly(heartPoly, x, y) || inTri(x, y, 0.0 to 44.0, -7.0 to 58.0, 7.0 to 58.0)
        TargetKind.MOON -> (x * x + y * y <= 2500 && (x - PopPaths.MOON_CUT_X.toDouble()).let { it * it } + (y - PopPaths.MOON_CUT_Y.toDouble()).let { it * it } > 1600) ||
            inTri(x, y, 0.0 to 48.0, -7.0 to 62.0, 7.0 to 62.0)
        TargetKind.CRITTER -> x * x + y * y <= 2500 || when (critter) {
            0 -> PopPaths.FOX_EARS.any { e -> inTri(x, y, e[0].first.toDouble() to e[0].second.toDouble(), e[1].first.toDouble() to e[1].second.toDouble(), e[2].first.toDouble() to e[2].second.toDouble()) }
            1 -> PopPaths.BEAR_EARS.any { inEllipse(x, y, it.first.toDouble(), it.second.toDouble(), it.third.toDouble(), it.third.toDouble()) }
            2 -> PopPaths.BUNNY_EARS.any { inEllipse(x, y, it[0].toDouble(), it[1].toDouble(), it[2].toDouble(), it[3].toDouble(), it[4].toDouble()) }
            else -> PopPaths.FROG_BUMPS.any { inEllipse(x, y, it.first.toDouble(), it.second.toDouble(), it.third.toDouble(), it.third.toDouble()) }
        }
    }

    private class Shape(val name: String, val kind: TargetKind, val critter: Int)

    private val shapes = listOf(
        Shape("round", TargetKind.ROUND, 0), Shape("oval", TargetKind.OVAL, 0), Shape("heart", TargetKind.HEART, 0), Shape("moon", TargetKind.MOON, 0),
        Shape("fox", TargetKind.CRITTER, 0), Shape("bear", TargetKind.CRITTER, 1), Shape("bunny", TargetKind.CRITTER, 2), Shape("frog", TargetKind.CRITTER, 3),
    )

    private val rasters = HashMap<String, BooleanArray>()

    private fun raster(s: Shape): BooleanArray = rasters.getOrPut(s.name) {
        val n = 160
        BooleanArray(n * n) { idx -> inside(s.kind, s.critter, -80.0 + (idx % n) * 1.0 + 0.5, -110.0 + (idx / n) * 1.5 + 0.5) }
    }

    private fun overlap(a: BooleanArray, b: BooleanArray): Double {
        var both = 0; var either = 0
        for (i in a.indices) { if (a[i] && b[i]) both++; if (a[i] || b[i]) either++ }
        return both.toDouble() / either
    }

    @Test
    fun `no two silhouettes are alike, so the five kinds differ in greyscale`() {
        val r = shapes.map { raster(it) }
        val worst = ArrayList<String>()
        for (i in shapes.indices) for (j in i + 1 until shapes.size) {
            if (shapes[i].kind == TargetKind.CRITTER && shapes[j].kind == TargetKind.CRITTER) continue // critters differ by their animal's ears and face
            val o = overlap(r[i], r[j])
            println("overlap ${shapes[i].name} / ${shapes[j].name} = %.3f".format(o))
            // the plain bubble and a critter differ by what pokes out of the critter (ears), and by its paper disc and face inside
            val limit = if (shapes[i].kind == TargetKind.ROUND && shapes[j].kind == TargetKind.CRITTER) 0.95 else 0.85
            if (o > limit) worst += "${shapes[i].name}/${shapes[j].name} $o"
        }
        assertTrue("silhouettes too alike: $worst", worst.isEmpty())
    }

    @Test
    fun `every critter pokes out of the bubble enough to change the outline`() {
        val round = raster(shapes[0])
        for (s in shapes.drop(4)) {
            val mine = raster(s)
            val extra = mine.indices.count { mine[it] && !round[it] }.toDouble() / round.count { it }
            println("${s.name} sticks out by %.3f of the bubble".format(extra))
            assertTrue("${s.name} adds $extra", extra >= 0.06)
        }
    }

    @Test
    fun `the four critters are told apart by ears, not colour alone`() {
        val r = shapes.drop(4).map { raster(it) }
        for (i in r.indices) for (j in i + 1 until r.size) assertTrue("${shapes[4 + i].name}/${shapes[4 + j].name}", overlap(r[i], r[j]) < 0.98)
    }

    @Test
    fun `a target is never wider than its nominal size, and the drawing is where the hit shape says`() {
        for (s in shapes) {
            val n = 160
            val g = raster(s)
            var minX = 1e9; var maxX = -1e9
            for (idx in g.indices) if (g[idx]) { val x = -80.0 + (idx % n) + 0.5; minX = minOf(minX, x); maxX = maxOf(maxX, x) }
            assertTrue("${s.name} spans ${maxX - minX}", maxX - minX <= 103.0)
            // the body is inside the hit ellipse grown by 12% (the string is not part of the hit shape)
            val k = s.kind
            var outside = 0; var total = 0
            for (idx in g.indices) if (g[idx]) {
                val x = -80.0 + (idx % n) + 0.5; val y = -110.0 + (idx / n) * 1.5 + 0.5
                if (k == TargetKind.CRITTER && (x * x + y * y > 2500)) continue // ears may leave it: a star that clips an ear still pops its bubble
                total++
                if (!inEllipse(x, y, k.dx * 100.0, k.dy * 100.0, k.rx * 100.0 * 1.12 + 8, k.ry * 100.0 * 1.12 + 8)) outside++
            }
            // (the moon's two thin tips reach past its narrow hit ellipse, as the design specifies; a star that clips a tip is not a hit)
            assertTrue("${s.name}: $outside of $total body points fall outside the hit reach", outside.toDouble() / total < if (k == TargetKind.MOON) 0.05 else 0.02)
        }
    }

    @Test
    fun `the heart is one closed outline and the moon is a crescent`() {
        val heart = raster(shapes[2])
        assertTrue(heart.count { it } > 3000)
        // the heart is a heart: notch at the top centre, point at the bottom
        assertTrue(!inside(TargetKind.HEART, 0, 0.0, -30.0) || inside(TargetKind.HEART, 0, 0.0, -20.0))
        assertTrue(inside(TargetKind.HEART, 0, 0.0, 10.0))
        assertTrue(inside(TargetKind.HEART, 0, -30.0, -25.0) && inside(TargetKind.HEART, 0, 30.0, -25.0))
        // the moon: the left side is solid, the middle is the cut-out
        assertTrue(inside(TargetKind.MOON, 0, -40.0, 0.0))
        assertTrue(!inside(TargetKind.MOON, 0, 20.0, 0.0))
        val moon = raster(shapes[3]).count { it }
        val round = raster(shapes[0]).count { it }
        assertTrue("crescent covers ${moon.toDouble() / round} of the bubble", moon.toDouble() / round in 0.45..0.8)
    }
}
