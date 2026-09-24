package com.pawplay.app.games.pawkitchen

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The outline of each ingredient picture, as pure geometry on the same
 * 100 x 100 grid the pictures are drawn on (docs/DESIGN-SYSTEM.md, Ingredient
 * drawing rules: "no two ingredients in the same dish share a silhouette").
 *
 * The icons in KitchenIcons.kt paint these very shapes, so what is drawn and
 * what is measured cannot drift apart, and [silhouetteOverlap] can be unit
 * tested on the plain JVM with no drawing at all. Inner marks (seeds, spots,
 * grill lines) are not part of a silhouette.
 */
sealed interface Part {
    fun contains(x: Float, y: Float): Boolean
}

class Circle(val cx: Float, val cy: Float, val r: Float) : Part {
    override fun contains(x: Float, y: Float) = (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r
}

/** An ellipse; [rotation] (degrees) turns it about its own centre. */
class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val rotation: Float = 0f) : Part {
    override fun contains(x: Float, y: Float): Boolean {
        val (lx, ly) = rotateAround(x, y, cx, cy, -rotation)
        val dx = (lx - cx) / rx
        val dy = (ly - cy) / ry
        return dx * dx + dy * dy <= 1f
    }
}

/** A rounded rectangle; [rotation] (degrees) turns it about its own centre. */
class Box(
    val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float, val rotation: Float = 0f,
) : Part {
    override fun contains(x: Float, y: Float): Boolean {
        val (lx, ly) = rotateAround(x, y, this.x + w / 2, this.y + h / 2, -rotation)
        if (lx < this.x || lx > this.x + w || ly < this.y || ly > this.y + h) return false
        val r = radius.coerceAtMost(minOf(w, h) / 2)
        val cx = lx.coerceIn(this.x + r, this.x + w - r)
        val cy = ly.coerceIn(this.y + r, this.y + h - r)
        return (lx - cx) * (lx - cx) + (ly - cy) * (ly - cy) <= r * r
    }
}

/** A closed polygon (straight edges; curves are flattened by [flattenPath] first). */
class Poly(val points: List<Offset>) : Part {
    override fun contains(x: Float, y: Float): Boolean {
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}

/** The union of [parts], the whole thing optionally turned by [rotation] degrees about the picture's centre (50, 50). */
class Silhouette(val parts: List<Part>, val rotation: Float = 0f) {
    constructor(vararg parts: Part, rotation: Float = 0f) : this(parts.toList(), rotation)

    fun contains(x: Float, y: Float): Boolean {
        val (lx, ly) = rotateAround(x, y, 50f, 50f, -rotation)
        return parts.any { it.contains(lx, ly) }
    }
}

/**
 * How much two outlines overlap: area of the intersection over area of the
 * union (1.0 = identical outline, 0.0 = nothing shared), sampled every [step] grid units.
 */
fun silhouetteOverlap(a: Silhouette, b: Silhouette, step: Float = 0.5f): Float {
    var both = 0
    var either = 0
    var y = step / 2
    while (y < 100f) {
        var x = step / 2
        while (x < 100f) {
            val inA = a.contains(x, y)
            val inB = b.contains(x, y)
            if (inA && inB) both++
            if (inA || inB) either++
            x += step
        }
        y += step
    }
    return if (either == 0) 0f else both.toFloat() / either
}

private fun rotateAround(x: Float, y: Float, cx: Float, cy: Float, degrees: Float): Pair<Float, Float> {
    if (degrees == 0f) return x to y
    val a = degrees * PI.toFloat() / 180f
    val dx = x - cx
    val dy = y - cy
    return (cx + dx * cos(a) - dy * sin(a)) to (cy + dx * sin(a) + dy * cos(a))
}

// --------------------------------------------------------------- outlines from data

/** A polygon from SVG-style path data (absolute M L H V C Q T Z), curves flattened. */
fun polyFromPath(d: String): Poly = Poly(flattenPath(d))

/** Points on the path described by [d]; supports the absolute commands M L H V C Q T Z used by the drawings. */
fun flattenPath(d: String, curveSteps: Int = 16): List<Offset> {
    val tokens = Regex("[MLHVCQTZ]|-?\\d*\\.?\\d+(?:[eE]-?\\d+)?").findAll(d).map { it.value }.toList()
    val out = ArrayList<Offset>()
    var i = 0
    var cur = Offset.Zero
    var lastQuadControl: Offset? = null
    fun num() = tokens[i++].toFloat()
    var cmd = ' '
    while (i < tokens.size) {
        if (tokens[i].first().isLetter()) cmd = tokens[i++].first()
        when (cmd) {
            'M', 'L' -> { cur = Offset(num(), num()); out += cur; lastQuadControl = null }
            'H' -> { cur = Offset(num(), cur.y); out += cur; lastQuadControl = null }
            'V' -> { cur = Offset(cur.x, num()); out += cur; lastQuadControl = null }
            'C' -> {
                val p1 = Offset(num(), num()); val p2 = Offset(num(), num()); val p3 = Offset(num(), num())
                for (s in 1..curveSteps) {
                    val t = s.toFloat() / curveSteps; val u = 1 - t
                    out += Offset(
                        u * u * u * cur.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
                        u * u * u * cur.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y,
                    )
                }
                cur = p3; lastQuadControl = null
            }
            'Q', 'T' -> {
                val control = if (cmd == 'Q') Offset(num(), num()) else
                    lastQuadControl?.let { Offset(2 * cur.x - it.x, 2 * cur.y - it.y) } ?: cur
                val end = Offset(num(), num())
                for (s in 1..curveSteps) {
                    val t = s.toFloat() / curveSteps; val u = 1 - t
                    out += Offset(
                        u * u * cur.x + 2 * u * t * control.x + t * t * end.x,
                        u * u * cur.y + 2 * u * t * control.y + t * t * end.y,
                    )
                }
                cur = end; lastQuadControl = control
            }
            'Z' -> lastQuadControl = null
            else -> error("unsupported path command '$cmd' in $d")
        }
    }
    return out
}

/** A disc with wavy edges, r = radius + amplitude * sin(lobes * theta), stretched to [ratio] tall over wide. */
fun blobPoints(cx: Float, cy: Float, radius: Float, amplitude: Float, lobes: Int, ratio: Float = 1f): List<Offset> {
    val steps = lobes * 8
    return List(steps) { i ->
        val a = i.toFloat() / steps * 2f * PI.toFloat()
        val rr = radius + amplitude * sin(a * lobes)
        Offset(cx + rr * cos(a), cy + rr * sin(a) * ratio)
    }
}

fun starPoints(cx: Float, cy: Float, outer: Float, inner: Float, points: Int, startAngle: Float): List<Offset> =
    List(points * 2) { i ->
        val r = if (i % 2 == 1) inner else outer
        val a = startAngle + i * PI.toFloat() / points
        Offset(cx + r * cos(a), cy + r * sin(a))
    }

internal fun Float.approxEquals(other: Float, tolerance: Float = 1e-3f) = abs(this - other) <= tolerance
