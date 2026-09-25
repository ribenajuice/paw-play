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
    const val STAR_EXIT_MARGIN = 30f     // sideways only: a star that leaves by a side is removed this far outside
    const val HIT_REACH = 8f             // a star centre this close to the drawn edge is a hit
    const val BIG_HIT_REACH = 28f

    // ---- targets (story 66, 67)
    const val SKY_TOP = 100f             // the entry line, dp from the top of the play area: the cloud bank's lowest points. Targets come from above it, stars end at it
    const val EDGE_CLEAR = 8f            // a target's body stays this far from the side edges
    const val APPEAR_GAP = 12f           // a new target is at least this clear of every other
    const val FADE_BAND = 0.1f           // a miss fades over the last tenth of the height
    const val FIRST_SPAWN_AT = 0.05f
    const val EMPTY_SCREEN_WAIT = 0.5f   // next target within this long once nothing is left (PRD: within 1s)
    const val SPAWN_RETRY = 0.3f
    const val MIN_TARGET_SIZE = 56       // nothing is ever smaller (was 72)
    const val MIN_CRITTER_SIZE = 64
    const val CARRIER_MIN_SIZE = 72      // was 96; the 48dp gift still fits inside (founder-approved 2026-09-25)
    const val MIN_TURN_GAP = 0.9f        // no two turns are closer than this (seconds)
    const val NO_TURN_AFTER_ENTRY = 0.5f // a target makes no turn in its first half second below the line
    const val MAX_PATH_SPEED = 0.2f      // along its path a target never moves faster than this (screen heights a second)
    const val REPICK_LIMIT = 0.25f       // from stage 3, speed is re-picked at each turn by at most this fraction
    const val VEIL_ALPHA = 0.5f          // a target still above the line is drawn at half opacity
    const val SOLID_SECONDS = 0.15f      // and turns solid over this long once it is below the line

    // ---- score, paws and the ending (stories 68, 69)
    const val START_PAWS = 3
    const val POP_POINTS = 1
    const val CARRIER_POINTS = 3
    const val MAX_SCORE = 999_999
    const val GRACE_SECONDS = 3f         // after a paw is lost, misses cost nothing for this long
    const val GRACE_FADE_IN = 0.3f       // the ship's peach glow fades in over this long
    const val GRACE_FADE_OUT = 0.5f      // and out over the last of the grace
    const val ENDING_SECONDS = 0.5f      // targets fade softly this long once the third paw is lost

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

    // ---- the top strip and cloud bank (docs/DESIGN-SYSTEM.md, "Pop: the top strip, the entry line and a lost paw")
    const val PAWS_LEFT = 92f            // x of the first paw; the strip is home, paws, score
    const val PAWS_TOP = 32f             // vertically centred on the home button
    const val SCORE_MARGIN_RIGHT = 20f
    const val STRIP_TOP = 20f
    const val BANK_STRAIGHT = 86f        // the bank's straight part ends here; scallops hang from it to SKY_TOP
    const val SCALLOP_WIDTH = 24f
}

/** How many scallops the bank has on a play area [width] dp wide: 15 on the reference 360dp, each about 24dp wide. */
fun bankScallops(width: Float): Int = kotlin.math.ceil(width / PopMetrics.SCALLOP_WIDTH - 1e-4f).toInt().coerceAtLeast(1)

/** The peach glow's strength while the grace window has [graceLeft] seconds left: in over 0.3s, steady, out over the last 0.5s. */
fun graceGlow(graceLeft: Float): Float {
    if (graceLeft <= 0f) return 0f
    val elapsed = PopMetrics.GRACE_SECONDS - graceLeft
    val fadeIn = (elapsed / PopMetrics.GRACE_FADE_IN).coerceIn(0f, 1f)
    val fadeOut = (graceLeft / PopMetrics.GRACE_FADE_OUT).coerceIn(0f, 1f)
    return minOf(fadeIn, fadeOut)
}

/** Where the game is: playing, the last paw just fell and targets are fading (0.5s), or over (the good-game screen shows). */
enum class PopPhase { PLAYING, ENDING, OVER }

/**
 * One row of the PRD's ramp table (revised 2026-09-25). Stages are invisible to the child and are counted in hidden pops
 * (targets popped by any means, this session). [driftMin]..[driftMax] is the downward speed in screen heights a second,
 * picked per target; [turnDegrees] is the angle from straight down of each leg, [runMin]..[runMax] the seconds of straight
 * run before a turn, [repickSpeed] whether the speed is re-picked at every turn (stage 3 and up). At stage 5 about
 * [smallChance] of the targets are [minSize]..[smallMax] and the rest [smallMax] + 1..[maxSize]. [colours] index [PopPalette].
 */
class PopStage(
    val number: Int,
    val startsAtPops: Int,
    val spawnEvery: Float,
    val maxTargets: Int,
    val kinds: List<TargetKind>,
    val colours: IntArray,
    val minSize: Int,
    val maxSize: Int,
    val driftMin: Float,
    val driftMax: Float,
    val turnDegrees: Float,
    val runMin: Float,
    val runMax: Float,
    val repickSpeed: Boolean,
    val smallChance: Float = 0f,
    val smallMax: Int = 0,
)

/**
 * | Stage | Starts after | New target every | Max | Size    | Downward speed | Turn | Run       | What is new                       |
 * | 1     | start        | 3.5 s            | 4   | 88-96   | 0.06-0.09      | 20   | 2.5-3.5 s | round bubbles only, 3 colours     |
 * | 2     | 24 pops      | 3.0 s            | 5   | 80-96   | 0.06-0.10      | 30   | 2.0-3.0 s | + oval balloons, 5 colours        |
 * | 3     | 60 pops      | 2.5 s            | 6   | 72-88   | 0.07-0.11      | 40   | 1.5-2.5 s | + heart and moon, speed re-picked |
 * | 4     | 108 pops     | 2.0 s            | 7   | 64-80   | 0.07-0.12      | 45   | 1.2-2.0 s | + critter bubbles                 |
 * | 5     | 165 pops     | 1.7 s            | 8   | 56-72   | 0.08-0.12      | 50   | 0.9-1.6 s | smallest and sharpest             |
 * The pop counts are the ones tuned by measuring scripted players (docs/DECISIONS.md, 2026-09-26).
 */
object PopRamp {
    private val round = listOf(TargetKind.ROUND)
    private val withOval = round + TargetKind.OVAL
    private val withHeartMoon = withOval + TargetKind.HEART + TargetKind.MOON
    private val all = withHeartMoon + TargetKind.CRITTER

    val stages: List<PopStage> = listOf(
        PopStage(1, 0, 3.5f, 4, round, intArrayOf(0, 2, 4), 88, 96, 0.06f, 0.09f, 20f, 2.5f, 3.5f, false),
        PopStage(2, 24, 3.0f, 5, withOval, intArrayOf(0, 1, 2, 4, 5), 80, 96, 0.06f, 0.10f, 30f, 2.0f, 3.0f, false),
        PopStage(3, 60, 2.5f, 6, withHeartMoon, intArrayOf(0, 1, 2, 3, 4, 5), 72, 88, 0.07f, 0.11f, 40f, 1.5f, 2.5f, true),
        PopStage(4, 108, 2.0f, 7, all, intArrayOf(0, 1, 2, 3, 4, 5), 64, 80, 0.07f, 0.12f, 45f, 1.2f, 2.0f, true),
        PopStage(5, 165, 1.7f, 8, all, intArrayOf(0, 1, 2, 3, 4, 5), 56, 72, 0.08f, 0.12f, 50f, 0.9f, 1.6f, true, 1f / 3f, 64),
    )

    fun stageFor(pops: Int): PopStage {
        var s = stages[0]
        for (i in 1 until stages.size) if (pops >= stages[i].startsAtPops) s = stages[i]
        return s
    }
}
