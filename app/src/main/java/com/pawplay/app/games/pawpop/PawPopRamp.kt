package com.pawplay.app.games.pawpop

/**
 * Every number of Paw Pop's rules in one place (docs/PRD.md Milestone 6, docs/DESIGN-SYSTEM.md "Paw Pop").
 * Distances are dp on the play area (inside any system bars); the PRD's "sh" is the play area's height, so
 * speeds given per screen height are multiplied by the live height.
 */
object PopMetrics {
    // ---- the ship
    const val SHIP_SIZE = 88f            // drawn box; the fins span 74dp of it
    const val SHIP_BOTTOM_MARGIN = 24f   // box bottom to the bottom of the play area (and above the gesture bar)
    const val SHIP_REACH = 38f           // centre to the tip of a fin, with the rim
    const val SIDE_MARGIN = 8f           // the whole ship stays this far inside both sides
    const val SHIP_TOP_SPEED = 1.5f      // screen widths per second
    const val SHIP_EASE = 7f             // speed = distance * this, so it slows softly on arrival
    const val SHIP_SNAP = 0.25f          // closer than this (dp) and it is simply there
    const val MAX_POINTERS = 10          // fingers remembered at once
    const val ADOPT_SLOP = 8f            // an older finger takes over steering only after moving this far (dp)

    // ---- stars
    const val FIRE_EVERY = 0.4f
    const val FIRST_SHOT_AFTER = 0.25f
    const val STAR_RADIUS = 14f          // 28dp across
    const val BIG_STAR_RADIUS = 28f      // 56dp across
    const val STAR_SPEED = 0.9f          // screen heights per second
    const val FAN_DEGREES = 12f
    const val STAR_LAUNCH_ABOVE_NOSE = 6f
    const val STAR_EXIT_MARGIN = 30f
    const val HIT_REACH = 8f             // a star centre this close to the drawn edge is a hit
    const val BIG_HIT_REACH = 28f

    // ---- targets
    const val EDGE_CLEAR = 8f            // sway-included distance kept from the side edges
    const val APPEAR_GAP = 12f           // a new target is at least this clear of every other
    const val SWAY_SECONDS = 3f
    const val FADE_BAND = 0.1f           // a miss fades over the last tenth of the height
    const val FIRST_SPAWN_AT = 0.05f
    const val FIRST_TARGET_TOP = 40f     // the first target's body reaches down to this line: already partly on screen
    const val EMPTY_SCREEN_WAIT = 0.5f   // next target within this long once nothing is left (PRD: within 1s)
    const val SPAWN_RETRY = 0.3f
    const val SMALL_SIZE = 72
    const val CARRIER_MIN_SIZE = 96

    // ---- carriers and gifts
    const val FIRST_CARRIER_AFTER = 4    // ordinary targets before the first carrier, so it is the 5th target
    const val CARRIER_GAP_MIN = 6
    const val CARRIER_GAP_MAX = 9
    const val GLIDE_SECONDS = 0.6f
    const val TRIPLE_SECONDS = 8f
    const val BIG_SECONDS = 8f
    const val RIBBON_SECONDS = 6f
    const val SLOW_SECONDS = 8f
    const val SLOW_FACTOR = 0.5f
    const val WAVE_SECONDS = 1.5f
    const val WAVE_MIN_ORDINARY = 1     // the wave pops only if at least this many ordinary (non-carrier) targets are on screen as it arrives
    const val RIBBON_HALF_WIDTH = 28f
    const val RIBBON_GROW = 1200f        // dp per second, from the nose
    const val BREATH_SECONDS = 2f        // the glow breathes for this long before it is over

    // ---- limits on screen (a new one that would exceed a limit is not made)
    const val MAX_TARGETS = 8
    const val MAX_STARS = 15
    const val MAX_SPARKLES = 60
    const val MAX_GIFTS = 1
    const val MAX_FX = 16
    const val POP_SPARKLES = 7
    const val ARRIVE_SPARKLES = 6
    const val WAVE_SPARKLES = 10

    // ---- time
    const val MAX_STEP = 0.05f           // one step never covers more than this; a long gap just becomes one 50ms step
    const val POP_SECONDS = 0.35f
    const val CRITTER_POP_SECONDS = 0.6f
    const val ARRIVE_SECONDS = 0.6f

    // ---- the home button, as in the other games
    const val HOME_SIZE = 56f
    const val HOME_INSET = 20f
}

/**
 * One row of the PRD's ramp table. Stages are invisible to the child and are counted in hidden pops (targets popped
 * by any means, this session). [drift] is per screen height per second; [colours] index [PopPalette].
 */
class PopStage(
    val number: Int,
    val startsAtPops: Int,
    val spawnEvery: Float,
    val drift: Float,
    val maxTargets: Int,
    val kinds: List<TargetKind>,
    val colours: IntArray,
    val minSize: Int,
    val maxSize: Int,
    val sway: Float,
    val smallChance: Float,
)

/**
 * | Stage | Starts after | New target every | Drift (sh/s) | Max | What is new                               |
 * | 1     | start        | 3.5 s            | 0.07         | 4   | round bubbles, 3 colours, 104-112dp       |
 * | 2     | 10 pops      | 3.0 s            | 0.08         | 5   | + oval balloons, 5 colours, 96-104dp      |
 * | 3     | 25 pops      | 2.5 s            | 0.09         | 6   | + heart and moon balloons, all 6 colours  |
 * | 4     | 45 pops      | 2.0 s            | 0.10         | 7   | + critter bubbles, 88-104dp               |
 * | 5     | 70 pops      | 1.7 s            | 0.11         | 8   | + a few small 72dp targets, sway 24dp     |
 */
object PopRamp {
    private val round = listOf(TargetKind.ROUND)
    private val withOval = round + TargetKind.OVAL
    private val withHeartMoon = withOval + TargetKind.HEART + TargetKind.MOON
    private val all = withHeartMoon + TargetKind.CRITTER

    val stages: List<PopStage> = listOf(
        PopStage(1, 0, 3.5f, 0.07f, 4, round, intArrayOf(0, 2, 4), 104, 112, 16f, 0f),
        PopStage(2, 24, 3.0f, 0.08f, 5, withOval, intArrayOf(0, 1, 2, 4, 5), 96, 104, 16f, 0f),
        PopStage(3, 60, 2.5f, 0.09f, 6, withHeartMoon, intArrayOf(0, 1, 2, 3, 4, 5), 96, 104, 16f, 0f),
        PopStage(4, 108, 2.0f, 0.10f, 7, all, intArrayOf(0, 1, 2, 3, 4, 5), 88, 104, 16f, 0f),
        PopStage(5, 165, 1.7f, 0.11f, 8, all, intArrayOf(0, 1, 2, 3, 4, 5), 88, 104, 24f, 0.2f),
    )

    fun stageFor(pops: Int): PopStage {
        var s = stages[0]
        for (i in 1 until stages.size) if (pops >= stages[i].startsAtPops) s = stages[i]
        return s
    }
}
