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
