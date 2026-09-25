package com.pawplay.app.ui

import kotlin.math.PI
import kotlin.math.sin

/**
 * The timing of the good-game screen's moving parts as plain functions of "milliseconds since the screen appeared",
 * so the calm-motion rules (docs/DESIGN-SYSTEM.md, "Motion and timing") can be unit-tested: nothing flashes or blinks
 * faster than three times a second.
 */
object GoodGameMotion {
    /** The screen fades in over this long. */
    const val FADE_MS = 500L

    /** The animal's three hops: 18dp high, 0.67s each (1.5 a second), starting once the fade is over. */
    const val HOP_START_MS = FADE_MS
    const val HOP_MS = 670L
    const val HOP_COUNT = 3
    const val HOP_DP = 18f

    /** The best number's glow breathes 55% to 100% over 1.4s, twice, then rests at 55%. */
    const val GLOW_PERIOD_MS = 1400L
    const val GLOW_BREATHS = 2
    const val GLOW_REST = 0.55f

    /** Each soft sparkle twinkles for 1.3s, twice; they start at these staggered delays after the fade. */
    const val TWINKLE_MS = 1300L
    const val TWINKLE_REPEATS = 2
    val twinkleDelaysMs: LongArray = longArrayOf(0, 200, 450, 650, 900, 1100)

    /** When the last new-best effect is over and the clock can stop. */
    val newBestEndMs: Long = maxOf(
        HOP_START_MS + HOP_MS * HOP_COUNT,
        FADE_MS + GLOW_PERIOD_MS * GLOW_BREATHS,
        FADE_MS + twinkleDelaysMs.max() + TWINKLE_MS * TWINKLE_REPEATS,
    ) + 100

    /** How high the animal is, in dp above its resting place, [tMs] after the screen appeared. Zero when [animate] is off. */
    fun hopHeightDp(tMs: Long, animate: Boolean): Float {
        if (!animate) return 0f
        val s = tMs - HOP_START_MS
        if (s < 0 || s >= HOP_MS * HOP_COUNT) return 0f
        return HOP_DP * sin(PI.toFloat() * (s % HOP_MS).toFloat() / HOP_MS)
    }

    /** How strongly the best number glows, 0.55 to 1.0: breathing while [animate] is on, otherwise steady at 0.55. */
    fun glowFactor(tMs: Long, animate: Boolean): Float {
        if (!animate) return GLOW_REST
        val s = tMs - FADE_MS
        if (s < 0 || s >= GLOW_PERIOD_MS * GLOW_BREATHS) return GLOW_REST
        return GLOW_REST + (1f - GLOW_REST) * sin(PI.toFloat() * (s % GLOW_PERIOD_MS).toFloat() / GLOW_PERIOD_MS)
    }

    /** How grown sparkle number [index] is, 0 to 1 (a smooth swell and shrink), [tMs] after the screen appeared. */
    fun twinkle(tMs: Long, index: Int): Float {
        val s = tMs - FADE_MS - twinkleDelaysMs[index]
        if (s < 0 || s >= TWINKLE_MS * TWINKLE_REPEATS) return 0f
        return sin(PI.toFloat() * (s % TWINKLE_MS).toFloat() / TWINKLE_MS)
    }
}
