package com.pawplay.app.games.pawpop

import kotlin.random.Random

/** A step of a 60 frames-a-second screen. */
internal const val FRAME = 1f / 60f

internal fun session(seed: Int = 1, pops: Int = 0, width: Float = 360f, height: Float = 692f) =
    PopSession(Random(seed), pops, width, height)

/** Runs [seconds] of play in [dt] steps, calling [each] after every step. */
internal inline fun PopSession.run(seconds: Float, dt: Float = FRAME, each: (PopSession) -> Unit = {}) {
    val steps = Math.round(seconds / dt)
    for (i in 0 until steps) { step(dt); each(this) }
}

/** Every target instance that has ever been on screen, in the order first seen (a target is only ever added, never changed, at spawn). */
internal class Spawns {
    val seen = ArrayList<PopTarget>()
    private val known = java.util.IdentityHashMap<PopTarget, Boolean>()
    fun watch(s: PopSession) {
        for (i in 0 until s.targetCount) { val t = s.target(i); if (known.put(t, true) == null) seen += t }
    }
}

internal fun PopSession.targets(): List<PopTarget> = List(targetCount) { target(it) }

/** A session with nothing firing and one pinned target, so a test can watch a single thing. */
internal fun PopSession.firstTarget(): PopTarget = target(0)

// ------------------------------------------------------------------ scripted players (the pacing is measured with these)

/** The five kinds of player the ramp is measured with (docs/PRD.md, Paw Pop ramp rationale). */
internal enum class Player { PERFECT, BUSY, WANDERING, TODDLER, NEVER, SHARP }

/** What one scripted game looked like: when each stage began, when each paw fell, and when (if) the game ended. */
internal class Played(
    /** Seconds at which stage 2, 3, 4 and 5 began (NaN if never reached). Index 0 is stage 2. */
    val stageAt: DoubleArray,
    val pawLostAt: List<Double>,
    val endedAt: Double,
    val pops: Int,
    val score: Int,
    val seconds: Double,
) {
    val ended: Boolean get() = !endedAt.isNaN()
    fun pawsLostBefore(t: Double): Int = pawLostAt.count { it <= t }
}

/**
 * Plays one whole game with [player] until it ends or [maxSeconds] pass, at 60 frames a second. Nothing here reads the
 * session's insides beyond what a child can see: where the targets are, how fast they go and which way.
 */
internal fun playGame(player: Player, seed: Int, maxSeconds: Double = 1500.0, width: Float = 360f, height: Float = 692f): Played {
    val s = PopSession(Random(seed * 31 + player.ordinal), 0, width, height)
    val r = Random(seed * 7 + player.ordinal)
    val stageAt = DoubleArray(4) { Double.NaN }
    val lost = ArrayList<Double>()
    var paws = s.paws
    var nextTap = 0.0
    var endedAt = Double.NaN
    var down = false
    while (s.time < maxSeconds) {
        when (player) {
            Player.NEVER -> {}
            Player.TODDLER -> if (s.time >= nextTap) { nextTap = s.time + 4 + r.nextDouble() * 3; s.touchDown(1, r.nextFloat() * width); s.touchUp(1) }
            Player.WANDERING -> if (s.time >= nextTap) { nextTap = s.time + 0.5 + r.nextDouble() * 1.5; s.touchDown(1, r.nextFloat() * width) }
            Player.BUSY -> if (s.time >= nextTap) { nextTap = s.time + 0.2 + r.nextDouble() * 0.6; s.touchDown(1, r.nextFloat() * width); s.touchUp(1) }
            Player.PERFECT, Player.SHARP -> {
                var best: PopTarget? = null
                for (i in 0 until s.targetCount) {
                    val g = s.target(i)
                    if (s.isPoppable(g) && g.y < s.nose - 20f && (best == null || g.y > best.y)) best = g
                }
                if (best != null) {
                    // lead the target: where it will be when a star fired now would get to its height
                    val flight = (s.nose - best.y) / (PopMetrics.STAR_SPEED * height + best.vy)
                    val x = if (player == Player.SHARP) best.x else best.x + best.vx * flight // SHARP aims at where it is now, PERFECT leads it
                    if (!down) { s.touchDown(1, x); down = true } else s.touchMove(1, x)
                }
            }
        }
        s.step(FRAME)
        for (k in 0 until 4) if (stageAt[k].isNaN() && s.stage.number >= k + 2) stageAt[k] = s.time
        while (paws > s.paws) { lost += s.time; paws-- }
        if (s.phase == PopPhase.OVER) { endedAt = s.time; break }
    }
    return Played(stageAt, lost, endedAt, s.pops, s.score, s.time)
}

internal fun median(values: List<Double>): Double {
    val v = values.filter { !it.isNaN() }.sorted()
    return if (v.isEmpty()) Double.NaN else v[v.size / 2]
}

internal fun percentile(values: List<Double>, p: Double): Double {
    val v = values.filter { !it.isNaN() }.sorted()
    return if (v.isEmpty()) Double.NaN else v[((v.size - 1) * p).toInt()]
}
