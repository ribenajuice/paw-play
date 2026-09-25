package com.pawplay.app.games.pawtrace

import kotlin.random.Random

/**
 * The content ramp (docs/PRD.md, Paw Trace): lines and curves, then shapes, then numbers, then capital
 * letters, purely by playing on. No locks, no visible levels, session-only. Pure Kotlin, unit tested.
 *
 *  - Each stage's glyphs are played in order until each has been completed once, then the stage advances.
 *  - From stage 2 on, every third glyph is a random glyph from an earlier stage (never the one just
 *    played), an easy win. Only the "new" glyphs count towards advancing.
 *  - After stage 4 it plays on forever as a random mix of all 46, never the same glyph twice in a row.
 */

/** The stage number used once every glyph has been completed once: the endless random mix. */
const val ENDLESS_STAGE = TraceGlyphs.STAGE_COUNT + 1

/** Every third glyph is an earlier-stage one: after this many new glyphs comes a mix. */
private const val NEW_BEFORE_MIX = 2

/**
 * Where the child is on the ramp.
 *
 * @param stage 1..4, or [ENDLESS_STAGE]
 * @param completedNew ids of new glyphs completed once (mixed-in repeats do not count)
 * @param newSinceMix new glyphs completed since the last mixed-in one (only counted from stage 2 on)
 * @param current the glyph on screen now
 * @param currentIsNew whether [current] counts towards advancing (false for mixed-in and endless glyphs)
 */
data class RampState(
    val stage: Int,
    val completedNew: Set<String>,
    val newSinceMix: Int,
    val current: Glyph,
    val currentIsNew: Boolean,
) {
    /**
     * The child finished [current]: records it and picks the next glyph. Call it exactly once per
     * completion. Advances the stage when every glyph in it has been completed once.
     */
    fun completed(random: Random = Random.Default): RampState {
        val done = if (currentIsNew) completedNew + current.id else completedNew
        // A new glyph completed while the mixing rule was on (stage 2 or later) brings the next mix closer.
        val counted = if (currentIsNew && stage >= 2) newSinceMix + 1 else newSinceMix

        var nextStage = stage
        while (nextStage <= TraceGlyphs.STAGE_COUNT && TraceGlyphs.stage(nextStage).all { it.id in done }) nextStage++

        if (nextStage == ENDLESS_STAGE) {
            val pick = TraceGlyphs.all.filter { it.id != current.id }.random(random)
            return RampState(ENDLESS_STAGE, done, 0, pick, currentIsNew = false)
        }
        if (nextStage >= 2 && counted >= NEW_BEFORE_MIX) {
            val pick = TraceGlyphs.all.filter { it.stage < nextStage && it.id != current.id }.random(random)
            return RampState(nextStage, done, 0, pick, currentIsNew = false)
        }
        val next = TraceGlyphs.stage(nextStage).first { it.id !in done }
        return RampState(nextStage, done, counted, next, currentIsNew = true)
    }
}

/** The very first glyph a child meets: the plain horizontal line. */
fun startRamp(): RampState =
    RampState(stage = 1, completedNew = emptySet(), newSinceMix = 0, current = TraceGlyphs.stage(1).first(), currentIsNew = true)
