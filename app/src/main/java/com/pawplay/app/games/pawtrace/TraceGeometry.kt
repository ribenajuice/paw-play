package com.pawplay.app.games.pawtrace

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure-Kotlin geometry for Paw Trace: a parser for the tiny SVG path subset the glyph data
 * uses (absolute M, L and C only; docs/glyphs/paw-trace-glyphs.md) and a sampler that turns a
 * stroke into points about every 1.5 glyph units. No Android or Compose types on purpose, so
 * it is unit-tested on a plain JVM. Coordinates are glyph units: a 100 x 100 box, y down.
 */

/** One point in glyph units. */
data class Pt(val x: Double, val y: Double)

sealed interface PathSegment {
    val end: Pt
}

data class LineTo(override val end: Pt) : PathSegment

data class CubicTo(val control1: Pt, val control2: Pt, override val end: Pt) : PathSegment

/** One stroke: where it starts, then what it does next. */
data class StrokePath(val start: Pt, val segments: List<PathSegment>)

/**
 * Parses one stroke, e.g. `M14,50 L86,50` or `M14,78 C14,3 86,3 86,78`. Exactly one leading
 * `M`, then at least one `L` or `C` (the usual SVG rule that extra coordinate groups repeat the
 * command is honoured). Anything else (relative commands, Z, arcs, stray text) throws
 * [IllegalArgumentException], because the glyph data is ours and a bad string is a bug that the
 * unit tests must catch, not something to limp past on a child's phone.
 */
fun parseStrokePath(d: String): StrokePath {
    val tokens = tokenize(d)
    require(tokens.isNotEmpty()) { "empty path" }
    var i = 0
    require(tokens[i] == 'M') { "path must start with M: $d" }
    i++
    val start = readPoints(tokens, i, 1, d).let { i += 2; it[0] }
    val segments = ArrayList<PathSegment>()
    while (i < tokens.size) {
        val command = tokens[i] as? Char ?: throw IllegalArgumentException("number where a command was expected in: $d")
        i++
        val perGroup = when (command) {
            'L' -> 1
            'C' -> 3
            'M' -> throw IllegalArgumentException("only one M per stroke: $d")
            else -> throw IllegalArgumentException("unsupported command '$command' in: $d")
        }
        var groups = 0
        while (i < tokens.size && tokens[i] is Double) {
            val pts = readPoints(tokens, i, perGroup, d)
            i += perGroup * 2
            segments += if (command == 'L') LineTo(pts[0]) else CubicTo(pts[0], pts[1], pts[2])
            groups++
        }
        require(groups > 0) { "'$command' needs coordinates in: $d" }
    }
    require(segments.isNotEmpty()) { "a stroke needs at least one L or C: $d" }
    return StrokePath(start, segments)
}

private fun readPoints(tokens: List<Any>, from: Int, count: Int, d: String): List<Pt> {
    require(from + count * 2 <= tokens.size) { "not enough coordinates in: $d" }
    return List(count) { k ->
        val x = tokens[from + 2 * k] as? Double ?: throw IllegalArgumentException("expected a number in: $d")
        val y = tokens[from + 2 * k + 1] as? Double ?: throw IllegalArgumentException("expected a number in: $d")
        Pt(x, y)
    }
}

/** Letters become [Char] tokens and numbers become [Double] tokens; commas and spaces are separators. */
private fun tokenize(d: String): List<Any> {
    val out = ArrayList<Any>()
    var i = 0
    while (i < d.length) {
        val c = d[i]
        when {
            c == ' ' || c == ',' || c == '\t' || c == '\n' || c == '\r' -> i++
            c.isLetter() -> { out += c; i++ }
            c == '-' || c == '.' || c.isDigit() -> {
                val start = i
                if (d[i] == '-') i++
                while (i < d.length && d[i].isDigit()) i++
                if (i < d.length && d[i] == '.') {
                    i++
                    while (i < d.length && d[i].isDigit()) i++
                }
                out += d.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("bad number '${d.substring(start, i)}' in: $d")
            }
            else -> throw IllegalArgumentException("unexpected '$c' in: $d")
        }
    }
    return out
}

/** Spacing between samples along a stroke, in glyph units (docs/DESIGN-SYSTEM.md, "Measuring coverage"). */
const val SAMPLE_STEP = 1.5

/**
 * A stroke sampled evenly by arc length: [count] points, the first exactly on the start and the
 * last exactly on the end, about [SAMPLE_STEP] apart. A closed shape's first and last samples are
 * the same spot, so it needs no special case anywhere.
 */
class SampledStroke(val xs: DoubleArray, val ys: DoubleArray, val length: Double) {
    val count: Int get() = xs.size
    val last: Int get() = xs.size - 1

    fun point(index: Int): Pt = Pt(xs[index], ys[index])

    /** The point at a fractional sample index (0 .. last), by straight interpolation between samples. */
    fun pointAt(index: Double): Pt {
        val clamped = index.coerceIn(0.0, last.toDouble())
        val lo = clamped.toInt().coerceAtMost(max(0, last - 1))
        val t = clamped - lo
        val hi = (lo + 1).coerceAtMost(last)
        return Pt(xs[lo] + (xs[hi] - xs[lo]) * t, ys[lo] + (ys[hi] - ys[lo]) * t)
    }

    /** Direction of travel around [index] in degrees (0 = towards +x, 90 = towards +y/down), from samples 2 either side. */
    fun angleDegrees(index: Int): Double {
        val a = point((index - 2).coerceAtLeast(0))
        val b = point((index + 2).coerceAtMost(last))
        return Math.toDegrees(kotlin.math.atan2(b.y - a.y, b.x - a.x))
    }
}

private const val FLATTEN_STEPS = 64

/** Turns a parsed stroke into evenly spaced samples. A stroke always gets at least 3 samples. */
fun sampleStroke(path: StrokePath, step: Double = SAMPLE_STEP): SampledStroke {
    // Flatten to a fine polyline first (curves into 64 straight bits), then walk it at even spacing.
    val fx = ArrayList<Double>()
    val fy = ArrayList<Double>()
    fx += path.start.x; fy += path.start.y
    var cur = path.start
    for (seg in path.segments) {
        when (seg) {
            is LineTo -> { fx += seg.end.x; fy += seg.end.y }
            is CubicTo -> for (k in 1..FLATTEN_STEPS) {
                val t = k.toDouble() / FLATTEN_STEPS
                val u = 1 - t
                fx += u * u * u * cur.x + 3 * u * u * t * seg.control1.x + 3 * u * t * t * seg.control2.x + t * t * t * seg.end.x
                fy += u * u * u * cur.y + 3 * u * u * t * seg.control1.y + 3 * u * t * t * seg.control2.y + t * t * t * seg.end.y
            }
        }
        cur = seg.end
    }
    val cumulative = DoubleArray(fx.size)
    for (k in 1 until fx.size) cumulative[k] = cumulative[k - 1] + hypot(fx[k] - fx[k - 1], fy[k] - fy[k - 1])
    val total = cumulative.last()

    val n = max(2, (total / step).roundToInt())
    val xs = DoubleArray(n + 1)
    val ys = DoubleArray(n + 1)
    var seg = 1
    for (i in 0..n) {
        val target = total * i / n
        while (seg < cumulative.size - 1 && cumulative[seg] < target) seg++
        val span = cumulative[seg] - cumulative[seg - 1]
        val t = if (span > 0.0) ((target - cumulative[seg - 1]) / span).coerceIn(0.0, 1.0) else 0.0
        xs[i] = fx[seg - 1] + (fx[seg] - fx[seg - 1]) * t
        ys[i] = fy[seg - 1] + (fy[seg] - fy[seg - 1]) * t
    }
    // Pin the ends so they are exactly the path's own start and end (arithmetic drift must not move them).
    xs[0] = path.start.x; ys[0] = path.start.y
    xs[n] = path.segments.last().end.x; ys[n] = path.segments.last().end.y
    return SampledStroke(xs, ys, total)
}
