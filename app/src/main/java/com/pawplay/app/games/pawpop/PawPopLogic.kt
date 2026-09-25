package com.pawplay.app.games.pawpop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/** A bubble or balloon on its way down. Everything but the position is fixed when it appears. */
class PopTarget(
    val kind: TargetKind,
    /** Index into [PopPalette]. */
    val colour: Int,
    /** Which animal, for a critter bubble (0 fox, 1 bear, 2 bunny, 3 frog). */
    val critter: Int,
    /** Nominal width in dp (72 to 112). */
    val size: Float,
    /** Sway centre and amplitude (dp), and phase (radians): `x = x0 + amp * sin(2 pi t / 3 + phase)`. Both follow the play area if it changes size. */
    var x0: Float,
    var amp: Float,
    val phase: Float,
    /** The gift inside, if this is a carrier. */
    val gift: GiftKind?,
    var x: Float,
    var y: Float,
) {
    /** 1 until the last tenth of the screen, then fading to 0 as its centre reaches the bottom. */
    var alpha: Float = 1f
    val isCarrier: Boolean get() = gift != null
}

/** One star in flight. [big] stars fly through what they hit. [degrees] is its tilt (0 straight up). */
class PopStar {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var big = false
    var degrees = 0f
}

/** A pop or an arrival still playing. Plain data: the drawing works out the picture from [age]. */
class PopFx {
    var arrival = false
    var x = 0f
    var y = 0f
    var size = 0f
    var colour = 0
    var kind: TargetKind = TargetKind.ROUND
    var critter = 0
    var age = 0f
    var duration = 0f
    /** How many sparkles this one counts against the limit of 60 (0 when the limit was already reached). */
    var sparkles = 0
}

/** A gift on its way to the ship. */
class PopGift {
    var kind: GiftKind = GiftKind.TRIPLE
    var x0 = 0f
    var y0 = 0f
    var age = 0f
    val progress: Float get() = (age / PopMetrics.GLIDE_SECONDS).coerceIn(0f, 1f)
}

/** The gift's curve: a quadratic from the carrier to the ship's nose, floating up a little first. */
object PopGlide {
    fun ease(k: Float): Float {
        val t = k.coerceIn(0f, 1f)
        return if (t < 0.5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
    }

    private fun bezier(a: Float, c: Float, b: Float, k: Float): Float {
        val e = ease(k)
        val u = 1f - e
        return u * u * a + 2f * u * e * c + e * e * b
    }

    fun x(x0: Float, x1: Float, k: Float): Float = bezier(x0, x0 + 0.2f * (x1 - x0), x1, k)
    fun y(y0: Float, y1: Float, k: Float): Float = bezier(y0, y0 - 26f, y1, k)
}

/** The glow's brightness multiplier with [remaining] seconds left: steady, then one-second breaths between 100% and 35%, then away. */
fun glowBreath(remaining: Float): Float {
    if (remaining > PopMetrics.BREATH_SECONDS) return 1f
    val tau = PopMetrics.BREATH_SECONDS - remaining
    return if (tau < 1.75f) 0.675f + 0.325f * cos(2f * PI.toFloat() * tau)
    else 0.675f * ((PopMetrics.BREATH_SECONDS - tau) / 0.25f).coerceIn(0f, 1f)
}

/** A target's opacity when its centre is at [y] on a play area [height] tall: a miss fades over the last tenth. */
fun missFade(y: Float, height: Float): Float = ((height - y) / (PopMetrics.FADE_BAND * height)).coerceIn(0f, 1f)

/**
 * One sitting of Paw Pop: the ship, its stars, the targets, the gift and the effects. Session-only, nothing is
 * saved, and a new session always starts with the ship in the middle, a first target due within a second, and
 * stage 1 (story 51, 60). Nothing here can end the game, and nothing is scored, timed or shown as a number.
 *
 * Time comes in only through [step], in seconds. One step never covers more than [PopMetrics.MAX_STEP]: a long
 * gap (the app was in the background, a slow frame) becomes a single 50ms step, so nothing jumps and nothing
 * arrives in a burst. All randomness comes from [random], so a seeded session is fully reproducible.
 *
 * Positions are dp on the play area; y is down. The ship's box ends [PopMetrics.SHIP_BOTTOM_MARGIN] above the bottom.
 * The pointer functions take the horizontal position in dp; the finger's height never matters.
 */
class PopSession(
    private val random: Random = Random.Default,
    startPops: Int = 0,
    width: Float = 360f,
    height: Float = 692f,
) {
    var width: Float = max(width, 1f)
        private set
    var height: Float = max(height, 1f)
        private set

    /** Seconds of play so far. Only the drawing's phases (sway, glow swell, flame) read it. */
    var time: Double = 0.0
        private set

    /** Targets popped this session, by any means. Hidden: only the ramp reads it. */
    var pops: Int = startPops
        private set

    val stage: PopStage get() = PopRamp.stageFor(pops)

    // ------------------------------------------------------------------ the ship

    var shipX: Float = this.width / 2f
        private set
    private var aimX: Float = shipX
    private var pilot: Long = NO_POINTER

    val shipCentreY: Float get() = height - PopMetrics.SHIP_BOTTOM_MARGIN - PopMetrics.SHIP_SIZE / 2f
    val nose: Float get() = height - PopMetrics.SHIP_BOTTOM_MARGIN - PopMetrics.SHIP_SIZE

    /** True while a finger is steering. */
    val isSteering: Boolean get() = pilot != NO_POINTER

    /** Whether [id] is the finger steering right now. */
    fun isPilot(id: Long): Boolean = id == pilot

    private fun shipRange(): Float = min(PopMetrics.SHIP_REACH + PopMetrics.SIDE_MARGIN, width / 2f)
    private fun clampShip(x: Float): Float = x.coerceIn(shipRange(), width - shipRange())

    // Fingers that are down, in the order they landed (fixed arrays, no allocation): where each is now, and where it was when
    // the steering finger last lifted or was cancelled, which is the place a hand-over is measured from.
    private val padIds = LongArray(PopMetrics.MAX_POINTERS)
    private val padX = FloatArray(PopMetrics.MAX_POINTERS)
    private val padRef = FloatArray(PopMetrics.MAX_POINTERS)
    private var padCount = 0

    private fun padIndex(id: Long): Int {
        for (i in 0 until padCount) if (padIds[i] == id) return i
        return -1
    }

    private fun padRemove(i: Int) {
        for (k in i until padCount - 1) { padIds[k] = padIds[k + 1]; padX[k] = padX[k + 1]; padRef[k] = padRef[k + 1] }
        padCount--
    }

    private fun padResetRefs() { for (i in 0 until padCount) padRef[i] = padX[i] }

    /**
     * A finger landed anywhere: it steers now, and any finger that was steering before it no longer does (the newest
     * finger steers). An older finger that is still down is remembered, see [touchMove].
     */
    fun touchDown(id: Long, x: Float) {
        if (!x.isFinite()) return
        val old = padIndex(id)
        if (old >= 0) padRemove(old)
        if (padCount == PopMetrics.MAX_POINTERS) padRemove(0) // more fingers than a hand: forget the oldest
        padIds[padCount] = id; padX[padCount] = x; padRef[padCount] = x; padCount++
        pilot = id
        aimX = clampShip(x)
    }

    /**
     * A finger moved. The steering finger steers. An older finger still down never steers by resting (a palm that
     * landed first and stays cannot capture the ship), but once nothing is steering it takes over as soon as it has moved
     * [PopMetrics.ADOPT_SLOP] dp from where it was when the steering finger lifted: a finger that keeps dragging carries
     * on at once, a resting hand does not.
     */
    fun touchMove(id: Long, x: Float) {
        if (!x.isFinite()) return
        val i = padIndex(id)
        if (i < 0) return // never seen landing: it is not ours to follow
        padX[i] = x
        if (id == pilot) {
            aimX = clampShip(x)
        } else if (pilot == NO_POINTER && abs(x - padRef[i]) >= PopMetrics.ADOPT_SLOP) {
            pilot = id
            aimX = clampShip(x)
        }
    }

    /** A finger lifted. If it was steering, the ship finishes gliding to where it was and then stays put (an older finger that is still down takes over only by moving, see [touchMove]). */
    fun touchUp(id: Long) {
        val i = padIndex(id)
        if (i >= 0) padRemove(i)
        if (id == pilot) {
            pilot = NO_POINTER
            padResetRefs()
        }
    }

    /** A finger's touch was cancelled (a system gesture, an app switch): if it was steering, the ship just stays where it is. */
    fun touchCancel(id: Long) {
        val i = padIndex(id)
        if (i >= 0) padRemove(i)
        if (id == pilot) {
            pilot = NO_POINTER
            aimX = shipX
            padResetRefs()
        }
    }

    /**
     * Every finger is up, or the touch layer is going away: nothing steers and nothing is remembered. A ship still gliding
     * to where the last finger lifted carries on; only a steering finger that vanished without a lift stops it.
     */
    fun touchCancelAll() {
        padCount = 0
        if (pilot == NO_POINTER) return
        pilot = NO_POINTER
        aimX = shipX
    }

    // ------------------------------------------------------------------ what is on screen

    private val targetList = ArrayList<PopTarget>(PopMetrics.MAX_TARGETS)
    val targetCount: Int get() = targetList.size
    fun target(i: Int): PopTarget = targetList[i]

    val stars: Array<PopStar> = Array(PopMetrics.MAX_STARS) { PopStar() }
    var starCount: Int = 0
        private set

    val fx: Array<PopFx> = Array(PopMetrics.MAX_FX) { PopFx() }
    var fxCount: Int = 0
        private set

    /** The gift gliding to the ship, or null. */
    var gift: PopGift? = null
        private set
    private val giftSlot = PopGift()

    /** Sparkles on screen now (pops, arrivals and the wave's own), never above [PopMetrics.MAX_SPARKLES]. */
    var sparkleCount: Int = 0
        private set

    // ------------------------------------------------------------------ effects

    /** The running star-changing effect (triple, big or ribbon), or null. */
    var starEffect: GiftKind? = null
        private set
    var starEffectLeft: Float = 0f
        private set
    private var ribbonAge = 0f

    /** Seconds of slow drift left; 0 when it is off. */
    var slowLeft: Float = 0f
        private set

    var waveActive: Boolean = false
        private set
    var waveY: Float = 0f
        private set
    private var waveAge = 0f
    private var wavePops = false
    /** Whether the wave is drawn with its ten riding sparkles (only when they fit under the limit). */
    var waveSparkly: Boolean = false
        private set

    /** How far the ribbon has grown above the ship's nose, in dp (it grows from the nose at 1200 dp/s). */
    val ribbonLength: Float get() = min(nose + 10f, ribbonAge * PopMetrics.RIBBON_GROW)

    // ------------------------------------------------------------------ scheduling state

    private var fireIn = PopMetrics.FIRST_SHOT_AFTER

    /** How many times the ship's 0.4s beat has come round, whether or not a star was made (tests read it; nothing draws it). */
    internal var fireTicks: Int = 0
        private set
    internal var starsLaunched: Int = 0
        private set
    /** Tests only: keeps the beat but makes no stars, so nothing gets popped while a schedule is measured. */
    internal var holdFireForTest: Boolean = false
    /** Tests only: nothing new appears, so a test can watch exactly the targets it put there. */
    internal var holdSpawnForTest: Boolean = false
    private var spawnIn = PopMetrics.FIRST_SPAWN_AT
    private var firstTarget = true

    /** Targets made this session (ordinary and carriers). */
    var spawned: Int = 0
        private set
    private var sinceCarrier = 0
    private var carrierAfter = PopMetrics.FIRST_CARRIER_AFTER
    private var lastGift: GiftKind? = null
    var carriersMade: Int = 0
        private set

    // ------------------------------------------------------------------ time

    /** Advances the game by [dtSeconds] (clamped to 50ms; zero, negative or not-a-number does nothing). */
    fun step(dtSeconds: Float) {
        if (!(dtSeconds > 0f) || !dtSeconds.isFinite()) return
        val dt = min(dtSeconds, PopMetrics.MAX_STEP)
        time += dt

        runTimers(dt) // effects and pictures that were already running; anything started below begins at its full time
        moveShip(dt)
        moveTargets(dt)
        spawnTargets(dt)
        fire(dt)
        moveStars(dt)
        runRibbon(dt)
        runWave(dt)
        runGift(dt)
    }

    private fun moveShip(dt: Float) {
        val dx = aimX - shipX
        if (abs(dx) < PopMetrics.SHIP_SNAP) {
            shipX = aimX
        } else {
            val top = PopMetrics.SHIP_TOP_SPEED * width
            val v = (dx * PopMetrics.SHIP_EASE).coerceIn(-top, top)
            var move = v * dt
            if (abs(move) > abs(dx)) move = dx
            shipX += move
        }
        shipX = clampShip(shipX)
    }

    /** The sway's angle now, wrapped each turn (in double) so a long session never loses float precision. */
    private fun swayPhaseNow(): Float = (time % PopMetrics.SWAY_SECONDS).toFloat() * (2f * PI.toFloat() / PopMetrics.SWAY_SECONDS)

    /** [time] wrapped into 0 until [period] (done in double), for drawing phases: a long session never loses float precision. */
    fun wrapped(period: Double): Float = (time % period).toFloat()

    private fun moveTargets(dt: Float) {
        val drift = stage.drift * height * (if (slowLeft > 0f) PopMetrics.SLOW_FACTOR else 1f)
        val swayPhase = swayPhaseNow()
        var i = 0
        while (i < targetList.size) {
            val t = targetList[i]
            t.y += drift * dt
            t.x = t.x0 + t.amp * sin(swayPhase + t.phase)
            t.alpha = missFade(t.y, height)
            if (t.alpha <= 0f) targetList.removeAt(i) // a miss: it is simply gone, and nothing counts or reacts
            else i++
        }
    }

    // ------------------------------------------------------------------ spawning

    private fun spawnTargets(dt: Float) {
        if (holdSpawnForTest) return
        spawnIn -= dt
        if (targetList.isEmpty() && spawnIn > PopMetrics.EMPTY_SCREEN_WAIT) spawnIn = PopMetrics.EMPTY_SCREEN_WAIT
        if (spawnIn > 0f) return
        val st = stage
        if (targetList.size >= min(st.maxTargets, PopMetrics.MAX_TARGETS)) {
            spawnIn = 0f // ready the moment there is room
            return
        }
        spawnIn = if (spawnTarget(st)) st.spawnEvery else PopMetrics.SPAWN_RETRY
    }

    /** Targets on screen that are not carriers (the wave skips carriers, so they do not count for it). */
    private fun ordinaryOnScreen(): Int {
        var n = 0
        for (i in 0 until targetList.size) if (!targetList[i].isCarrier) n++
        return n
    }

    private fun carrierOnScreen(): Boolean {
        for (i in 0 until targetList.size) if (targetList[i].isCarrier) return true
        return false
    }

    private fun spawnTarget(st: PopStage): Boolean {
        val carrier = sinceCarrier >= carrierAfter && !carrierOnScreen()
        var kind = st.kinds[random.nextInt(st.kinds.size)]
        val colour = st.colours[random.nextInt(st.colours.size)]
        var size = st.minSize + random.nextInt(st.maxSize - st.minSize + 1)
        if (st.smallChance > 0f && random.nextFloat() < st.smallChance) size = PopMetrics.SMALL_SIZE
        val critter = random.nextInt(4)
        var gift: GiftKind? = null
        if (carrier) {
            kind = if (random.nextBoolean()) TargetKind.ROUND else TargetKind.OVAL
            size = max(size, PopMetrics.CARRIER_MIN_SIZE)
            var g = GiftKind.ALL[random.nextInt(GiftKind.ALL.size)]
            while (g == lastGift) g = GiftKind.ALL[random.nextInt(GiftKind.ALL.size)] // never the same twice in a row
            gift = g
        }
        val s = size.toFloat()
        val amp = st.sway
        val phase = random.nextFloat() * 2f * PI.toFloat()
        val sway = sin(swayPhaseNow() + phase)
        val y = if (firstTarget) PopMetrics.FIRST_TARGET_TOP - kind.top * s else -kind.top * s - 2f
        val lo = s / 2f + amp + PopMetrics.EDGE_CLEAR
        val hi = width - s / 2f - amp - PopMetrics.EDGE_CLEAR
        var x0 = Float.NaN
        for (attempt in 0 until 10) {
            val candidate = if (hi > lo) lo + random.nextFloat() * (hi - lo) else width / 2f
            if (clearOfOthers(kind, s, candidate + amp * sway, y)) { x0 = candidate; break }
        }
        if (x0.isNaN()) return false // no room right now: try again shortly
        firstTarget = false
        if (carrier) {
            lastGift = gift
            sinceCarrier = 0
            carrierAfter = PopMetrics.CARRIER_GAP_MIN + random.nextInt(PopMetrics.CARRIER_GAP_MAX - PopMetrics.CARRIER_GAP_MIN + 1)
            carriersMade++
        } else {
            sinceCarrier++
        }
        spawned++
        targetList.add(PopTarget(kind, colour, critter, s, x0, amp, phase, gift, x0 + amp * sway, y))
        return true
    }

    /** True when a target of [kind] and [size] centred at ([x], [y]) would be at least 12dp clear of every target on screen. */
    private fun clearOfOthers(kind: TargetKind, size: Float, x: Float, y: Float): Boolean {
        val r = kind.reach * size
        for (i in 0 until targetList.size) {
            val t = targetList[i]
            val dx = t.x - x
            val dy = t.y - y
            val gap = sqrt(dx * dx + dy * dy) - t.kind.reach * t.size - r
            if (gap < PopMetrics.APPEAR_GAP) return false
        }
        return true
    }

    // ------------------------------------------------------------------ stars

    private fun fire(dt: Float) {
        fireIn -= dt
        if (fireIn > 0f) return
        fireIn += PopMetrics.FIRE_EVERY // the pace is the same whatever is running
        fireTicks++
        if (holdFireForTest) return
        val effect = starEffect
        if (effect == GiftKind.RIBBON) return // the ribbon stands in for the stars while it runs
        val count = if (effect == GiftKind.TRIPLE) 3 else 1
        if (starCount + count > PopMetrics.MAX_STARS) return
        val speed = PopMetrics.STAR_SPEED * height
        if (count == 3) {
            for (a in TRIPLE_ANGLES) launchStar(speed, a, false)
        } else {
            launchStar(speed, 0f, effect == GiftKind.BIG)
        }
    }

    private fun launchStar(speed: Float, degrees: Float, big: Boolean) {
        starsLaunched++
        val s = stars[starCount++]
        s.x = shipX
        s.y = nose - PopMetrics.STAR_LAUNCH_ABOVE_NOSE
        s.vy = speed
        s.vx = speed * tan(degrees * PI.toFloat() / 180f)
        s.big = big
        s.degrees = degrees
    }

    private fun removeStar(i: Int) {
        val last = starCount - 1
        val gone = stars[i]
        stars[i] = stars[last]
        stars[last] = gone
        starCount = last
    }

    private fun moveStars(dt: Float) {
        var i = 0
        while (i < starCount) {
            val s = stars[i]
            s.y -= s.vy * dt
            s.x += s.vx * dt
            var alive = !(s.y < -PopMetrics.STAR_EXIT_MARGIN || s.x < -PopMetrics.STAR_EXIT_MARGIN || s.x > width + PopMetrics.STAR_EXIT_MARGIN)
            if (alive) {
                var j = 0
                while (j < targetList.size) {
                    if (hits(s, targetList[j])) {
                        pop(j)
                        if (!s.big) { alive = false; break } // one star pops one target; a big one flies on
                    } else {
                        j++
                    }
                }
            }
            if (alive) i++ else removeStar(i)
        }
    }

    /** A star centre within 8dp (28dp for a big star) of the drawn edge is a hit: tested against the target's hit ellipse grown by that. */
    private fun hits(s: PopStar, t: PopTarget): Boolean {
        val reach = if (s.big) PopMetrics.BIG_HIT_REACH else PopMetrics.HIT_REACH
        val k = t.kind
        val nx = (s.x - (t.x + k.dx * t.size)) / (k.rx * t.size + reach)
        val ny = (s.y - (t.y + k.dy * t.size)) / (k.ry * t.size + reach)
        return nx * nx + ny * ny <= 1f
    }

    // ------------------------------------------------------------------ pops and gifts

    /** Pops the target at [index]: it is out of play at once, the picture just finishes. */
    private fun pop(index: Int) {
        val t = targetList.removeAt(index)
        pops++
        if (fxCount < PopMetrics.MAX_FX) {
            val f = fx[fxCount++]
            f.arrival = false
            f.x = t.x
            f.y = t.y
            f.size = t.size
            f.colour = t.colour
            f.kind = t.kind
            f.critter = t.critter
            f.age = 0f
            f.duration = if (t.kind == TargetKind.CRITTER) PopMetrics.CRITTER_POP_SECONDS else PopMetrics.POP_SECONDS
            f.sparkles = if (sparkleCount + PopMetrics.POP_SPARKLES <= PopMetrics.MAX_SPARKLES) PopMetrics.POP_SPARKLES else 0
            sparkleCount += f.sparkles
        }
        val g = t.gift
        if (g != null && gift == null) { // at most one gift in flight; a second would simply not be made
            giftSlot.kind = g
            giftSlot.x0 = t.x
            giftSlot.y0 = t.y
            giftSlot.age = 0f
            gift = giftSlot
        }
    }

    private fun runGift(dt: Float) {
        val g = gift ?: return
        g.age += dt
        if (g.age >= PopMetrics.GLIDE_SECONDS) {
            gift = null
            arrive(g.kind)
        }
    }

    /** The gift reached the ship: it sparkles, and the effect begins. */
    private fun arrive(kind: GiftKind) {
        if (fxCount < PopMetrics.MAX_FX) {
            val f = fx[fxCount++]
            f.arrival = true
            f.age = 0f
            f.duration = PopMetrics.ARRIVE_SECONDS
            f.x = shipX
            f.sparkles = if (sparkleCount + PopMetrics.ARRIVE_SPARKLES <= PopMetrics.MAX_SPARKLES) PopMetrics.ARRIVE_SPARKLES else 0
            sparkleCount += f.sparkles
        }
        when (kind) {
            GiftKind.TRIPLE, GiftKind.BIG, GiftKind.RIBBON -> { // a new one replaces the current one and starts its own full time
                starEffect = kind
                starEffectLeft = kind.seconds
                ribbonAge = 0f
            }
            GiftKind.SLOW -> slowLeft = kind.seconds // alongside anything else; the same gift again restarts its time
            GiftKind.WAVE -> {
                waveActive = true
                waveAge = 0f
                waveY = nose + 20f
                wavePops = ordinaryOnScreen() >= PopMetrics.WAVE_MIN_ORDINARY // it pops only if at least one ordinary target is on screen as the gift arrives
                if (waveSparkly) sparkleCount -= PopMetrics.WAVE_SPARKLES
                waveSparkly = sparkleCount + PopMetrics.WAVE_SPARKLES <= PopMetrics.MAX_SPARKLES
                if (waveSparkly) sparkleCount += PopMetrics.WAVE_SPARKLES
            }
        }
    }

    // ------------------------------------------------------------------ ribbon and wave

    private fun runRibbon(dt: Float) {
        if (starEffect != GiftKind.RIBBON) return
        ribbonAge += dt
        val top = nose - ribbonLength
        var i = 0
        while (i < targetList.size) {
            val t = targetList[i]
            val k = t.kind
            val ex = t.x + k.dx * t.size
            val ey = t.y + k.dy * t.size
            val ry = k.ry * t.size
            val inColumn = abs(ex - shipX) <= PopMetrics.RIBBON_HALF_WIDTH + k.rx * t.size * 0.9f
            if (inColumn && ey + ry > top && t.y < nose) pop(i) else i++
        }
    }

    private fun runWave(dt: Float) {
        if (!waveActive) return
        waveAge += dt
        waveY = nose + 20f - waveAge * ((nose + 60f) / PopMetrics.WAVE_SECONDS)
        if (wavePops) {
            var i = 0
            while (i < targetList.size) {
                val t = targetList[i]
                if (!t.isCarrier && waveY <= t.y) pop(i) else i++ // one after another as it reaches each; carriers are skipped
            }
        }
        if (waveY < -40f) {
            waveActive = false
            if (waveSparkly) { sparkleCount -= PopMetrics.WAVE_SPARKLES; waveSparkly = false }
        }
    }

    private fun runTimers(dt: Float) {
        if (starEffect != null) {
            starEffectLeft -= dt
            if (starEffectLeft <= 0f) { starEffect = null; starEffectLeft = 0f; ribbonAge = 0f }
        }
        if (slowLeft > 0f) slowLeft = max(0f, slowLeft - dt)
        var i = 0
        while (i < fxCount) {
            val f = fx[i]
            f.age += dt
            if (f.age >= f.duration) {
                sparkleCount -= f.sparkles
                val last = fxCount - 1
                fx[i] = fx[last]
                fx[last] = f
                fxCount = last
            } else {
                i++
            }
        }
    }

    // ------------------------------------------------------------------ the play area changes

    /** The play area changed size (a system bar appeared, the window resized): everything keeps its place as a fraction of it. */
    fun setArea(newWidth: Float, newHeight: Float) {
        if (!(newWidth >= 1f) || !(newHeight >= 1f) || !newWidth.isFinite() || !newHeight.isFinite()) return
        if (newWidth == width && newHeight == height) return
        val fx0 = newWidth / width
        val fy0 = newHeight / height
        shipX *= fx0
        aimX *= fx0
        for (i in 0 until starCount) { stars[i].x *= fx0; stars[i].y *= fy0 }
        for (i in 0 until targetList.size) { // a target keeps its place as a fraction of the width, and its whole sway stays on screen
            val t = targetList[i]
            t.y *= fy0
            t.x0 *= fx0
            val room = max(0f, (newWidth - t.size) / 2f - PopMetrics.EDGE_CLEAR)
            t.amp = min(t.amp, room)
            val lo = t.size / 2f + PopMetrics.EDGE_CLEAR + t.amp
            t.x0 = if (newWidth - lo >= lo) t.x0.coerceIn(lo, newWidth - lo) else newWidth / 2f
            t.x = t.x0 + t.amp * sin(swayPhaseNow() + t.phase)
        }
        for (i in 0 until fxCount) { fx[i].x *= fx0; fx[i].y *= fy0 }
        gift?.let { it.x0 *= fx0; it.y0 *= fy0 }
        waveY *= fy0
        width = newWidth
        height = newHeight
        shipX = clampShip(shipX)
        aimX = clampShip(aimX)
    }

    // ------------------------------------------------------------------ for tests

    /** Puts a target on screen where the test wants it (sway centre [x], centre [y]); it counts as spawned. */
    internal fun addTargetForTest(kind: TargetKind, x: Float, y: Float, size: Float = 100f, gift: GiftKind? = null, colour: Int = 0): PopTarget {
        val t = PopTarget(kind, colour, 0, size, x, 0f, 0f, gift, x, y)
        targetList.add(t)
        return t
    }

    internal fun clearTargetsForTest() = targetList.clear()

    /** Hands a gift straight to the ship as if it had just arrived. */
    internal fun arriveForTest(kind: GiftKind) = arrive(kind)

    /** Sparkles counted afresh from everything alive, to check the running total against. */
    internal fun recountSparkles(): Int {
        var n = if (waveSparkly) PopMetrics.WAVE_SPARKLES else 0
        for (i in 0 until fxCount) n += fx[i].sparkles
        return n
    }

    private companion object {
        const val NO_POINTER = Long.MIN_VALUE
        val TRIPLE_ANGLES = floatArrayOf(-PopMetrics.FAN_DEGREES, 0f, PopMetrics.FAN_DEGREES)
    }
}
