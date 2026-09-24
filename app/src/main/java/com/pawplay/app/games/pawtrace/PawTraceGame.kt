package com.pawplay.app.games.pawtrace

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pawplay.app.games.MiniGame
import com.pawplay.app.games.pawmatch.HomeGlyphIcon
import com.pawplay.app.games.pawmatch.PawPrintIcon
import com.pawplay.app.ui.theme.PawCoral
import com.pawplay.app.ui.theme.PawLeaf
import com.pawplay.app.ui.theme.PawSunshine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Timings: soft and unhurried. Paw Trace ships silent for now, like the other games (docs/DECISIONS.md).
private const val IDLE_HINT_AFTER_MS = 5000L
private const val HINT_WALK_MS = 1700
private const val HINT_HOLD_MS = 450L
private const val HINT_BACK_MS = 400
private const val GAP_FILL_MS = 250
private const val SPARKLE_SHOW_MS = 1800L
private const val MARKER_SLIDE_MS = 380
private const val MARKER_HOP_MS = 460
private const val MARKER_HOP_DP = 30f
private const val MARKER_BOB_DP = 5f
private const val HAPPY_HOP_DP = 22f

// Marker sizes in dp (docs/DESIGN-SYSTEM.md, "Marker (the frog) and direction cue").
private const val FROG_DP = 48f
private const val RING_DP = 58f
private const val HALO_NEAR_DP = 74f
private const val HALO_FAR_DP = 90f
private const val CUE_PAW_DP = 20f
private const val FROG_HALF_VIEW = 54f // the frog head is cropped to a 54 of 80 view on Paw Match's 80 x 80 face grid
private const val TILE_REFERENCE_DP = 146f // the mockup's tile size; every tile measurement is a fraction of it

object PawTraceGame : MiniGame {
    override val id = "paw-trace"
    override val icon: @Composable (Dp) -> Unit = { size -> PawTraceTileIcon(size) }
    override val content: @Composable (onExit: () -> Unit) -> Unit = { onExit -> PawTraceScreen(onExit) }
}

/**
 * Session-only, in-memory: entering from the home screen always starts at the first glyph (the plain
 * horizontal line) and nothing is saved (docs/PRD.md story 36). The first glyph is on screen the moment
 * the game opens, so first run and every run look the same. All rules live in PawTraceLogic.kt and
 * PawTraceRamp.kt; this only turns touches into calls on [TraceSession] and draws the result.
 */
@Composable
private fun PawTraceScreen(onExit: () -> Unit) {
    val session = remember { TraceSession() }
    TraceScene(session, onExit)
}

@Composable
internal fun TraceScene(session: TraceSession, onExit: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val layout = remember(maxWidth, maxHeight) { traceLayout(maxWidth.value, maxHeight.value) }
        remember(layout.boxSize) { session.updateTuning(TraceTuning(layout.unitDp.toDouble())) }

        // The session is plain Kotlin, so these carry "something changed" to the drawing.
        val revision = remember { mutableIntStateOf(0) }
        var phase by remember { mutableStateOf(session.phase) }
        var serial by remember { mutableIntStateOf(session.serial) }
        var touchEpoch by remember { mutableIntStateOf(0) }
        val fill = remember(serial) { mutableStateMapOf<Int, Float>() }      // gap-fill progress per just-finished stroke
        val sparkles = remember(serial) { mutableStateListOf<Int>() }         // strokes whose sparkle is showing
        val walk = remember { Animatable(0f) }                                // 0..1: how far the idle hint has walked the frog
        val scope = rememberCoroutineScope()

        // What the drawing last saw, so a touch that changed nothing (off the path, a second finger) redraws nothing.
        val seen = remember { intArrayOf(-1, -1) } // glyph serial, paint version
        val onTouched = remember(serial) {
            { finished: List<Int>, touchChanged: Boolean ->
                // Gap-fill progress starts at 0 before anything can draw the finished stroke in full.
                for (stroke in finished) fill[stroke] = 0f
                if (seen[0] != session.serial || seen[1] != session.paint.version) {
                    seen[0] = session.serial; seen[1] = session.paint.version
                    revision.intValue++
                }
                phase = session.phase
                serial = session.serial
                if (touchChanged) touchEpoch++
                for (stroke in finished) {
                    scope.launch { animate(0f, 1f, animationSpec = tween(GAP_FILL_MS)) { value, _ -> fill[stroke] = value } }
                    sparkles += stroke
                    scope.launch { delay(SPARKLE_SHOW_MS); sparkles.remove(stroke) }
                }
            }
        }
        val currentOnTouched by rememberUpdatedState(onTouched)

        // After about 5s with no touch the frog walks its stroke once, drifts back, and repeats after each further pause.
        LaunchedEffect(touchEpoch, phase, serial) {
            walk.snapTo(0f)
            if (phase != TracePhase.TRACING || session.fingersDown > 0) return@LaunchedEffect
            while (true) {
                delay(IDLE_HINT_AFTER_MS)
                walk.animateTo(1f, tween(HINT_WALK_MS, easing = FastOutSlowInEasing))
                delay(HINT_HOLD_MS)
                walk.animateTo(0f, tween(HINT_BACK_MS))
            }
        }

        val celebrating = phase == TracePhase.CELEBRATING

        // One full-screen touch layer under everything, so a finger that lands anywhere (blank space, the side margins)
        // and slides onto the path starts painting the moment it reaches it. The home and play-on buttons sit above
        // it and take their own touches; a finger that lands on them never reaches this layer. Only the first finger
        // paints (see PrimaryPointer). Positions are turned into glyph units relative to the glyph box.
        val currentLayout by rememberUpdatedState(layout)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(session) {
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val box = currentLayout
                                val unitPx = box.boxSize * density / 100.0
                                val originX = box.boxLeft * density
                                val originY = box.boxTop * density
                                val finished = ArrayList<Int>()
                                var touchChanged = false
                                for (change in event.changes) {
                                    val id = change.id.value
                                    val x = (change.position.x - originX) / unitPx
                                    val y = (change.position.y - originY) / unitPx
                                    if (change.pressed && !change.previousPressed) {
                                        touchChanged = true
                                        finished += session.pointerDown(id, x, y)
                                        change.consume()
                                    } else if (change.pressed) {
                                        finished += session.pointerMove(id, x, y)
                                        change.consume()
                                    } else if (change.previousPressed) {
                                        touchChanged = true
                                        session.pointerUp(id)
                                        change.consume()
                                    }
                                }
                                // Nothing is down any more: forget every finger, so a missed lift can never leave the game waiting for one.
                                if (event.changes.none { it.pressed }) {
                                    if (session.fingersDown != 0) touchChanged = true
                                    session.releaseAll()
                                }
                                currentOnTouched(finished, touchChanged)
                            }
                        }
                    } finally {
                        session.releaseAll() // the touch handler is going away mid-touch (screen left, window lost)
                    }
                },
        )

        val paint = session.paint
        val pathCache = remember(paint.glyph) { GlyphPathCache(paint.glyph) }

        // The glyph box: drawing only, it takes no touches itself.
        Box(
            modifier = Modifier
                .offset(x = layout.boxLeft.dp, y = layout.boxTop.dp)
                .size(layout.boxSize.dp),
        ) {
            val pop = if (celebrating) {
                rememberInfiniteTransition(label = "glyph-pop").animateFloat(
                    1f, 1.035f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pop",
                )
            } else {
                null
            }
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val p = pop?.value ?: 1f
                    scaleX = p; scaleY = p
                },
            ) {
                GlyphCanvas(paint, pathCache, revision, fill, walk)
                if (sparkles.isNotEmpty()) FinishedSparkles(session, sparkles)
                if (celebrating) CelebrationEffects(pathCache)
                FrogMarker(session, revision, serial, celebrating, walk, layout.boxSize, layout.boxTop)
            }
        }

        if (celebrating) {
            PlayOnButton(
                onClick = {
                    if (session.playOn()) currentOnTouched(emptyList(), true)
                },
                modifier = Modifier.offset(x = layout.playOnLeft.dp, y = layout.playOnTop.dp),
            )
        }

        // Home: always on screen, always live, same corner as the other games.
        ExitButton(
            onClick = onExit,
            modifier = Modifier.offset(x = TraceLayout.HOME_INSET.dp, y = TraceLayout.HOME_INSET.dp),
        )
    }
}

// ------------------------------------------------------------------ the glyph: guide, paint, cue

/**
 * The guide band, the paint (with its glow), the done lines and the paw-print direction cue. Two layers: the
 * guide never changes for a glyph and size, so it is drawn from a cached path; the paint layer rebuilds only its
 * own paths, and only when the painting changed.
 */
@Composable
private fun GlyphCanvas(paint: TracePaint, cache: GlyphPathCache, revision: State<Int>, fill: Map<Int, Float>, walk: Animatable<Float, *>) {
    val guideLayer = remember(cache) {
        Modifier.fillMaxSize().drawWithCache {
            val guide = cache.ensure(size.width / 100f).guide
            val dp = density
            onDrawBehind { drawGuide(guide, dp) }
        }
    }
    val paintLayer = remember(cache, paint) {
        Modifier.fillMaxSize().drawWithCache {
            revision.value // the painting changed: rebuild the paint paths below
            val unit = size.width / 100f
            val dp = density
            val paths = cache.ensure(unit)

            // Paint under the finger: every stroke that is still open, or has only just finished and is filling its gap.
            val fingerPaint = Path()
            val settled = Path()          // finished strokes whose gap-fill has run its course, drawn whole
            val filling = ArrayList<Int>()
            paint.strokes.forEachIndexed { i, s ->
                if (s.done && (fill[i] ?: 1f) >= 1f) {
                    settled.addPath(paths.polylines[i])
                } else {
                    addRuns(fingerPaint, s.stroke, s.touched, unit)
                    if (s.done) filling += i
                }
            }
            val marker = paint.marker()
            val cues = if (marker != null) paint.cues(marker) else emptyList()

            onDrawBehind {
                drawPaint(fingerPaint, dp)
                drawPaint(settled, dp)
                for (i in filling) {
                    val a = fill[i] ?: 1f
                    drawPaint(paths.polylines[i], dp, alpha = a)
                    drawDoneLine(paths.polylines[i], dp, alpha = a)
                }
                drawDoneLine(settled, dp)
                if (walk.value == 0f && !paint.isComplete) {
                    val alphas = listOf(0.95f, 0.65f, 0.4f)
                    cues.forEachIndexed { k, cue ->
                        drawPaw(
                            (cue.x * unit).toFloat(), (cue.y * unit).toFloat(), CUE_PAW_DP * dp,
                            cue.angleDegrees.toFloat(), TraceGuideEdge, alphas[k],
                        )
                    }
                }
            }
        }
    }
    Box(guideLayer)
    Box(paintLayer)
}

/** Three sunshine sparkles beside each stroke that just finished, twinkling for about 1.8s. */
@Composable
private fun FinishedSparkles(session: TraceSession, strokes: List<Int>) {
    val twinkle = rememberInfiniteTransition(label = "stroke-sparkle").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "twinkle",
    )
    Canvas(Modifier.fillMaxSize()) {
        val unit = size.width / 100f
        val paint = session.paint
        for (si in strokes) {
            if (si >= paint.strokes.size) continue
            val stroke = paint.strokes[si].stroke
            for ((f, ox, oy, r, lag) in listOf(
                listOf(0.22f, -5f, -6f, 4.4f, 0f), listOf(0.5f, 6f, -5f, 5.2f, 0.4f), listOf(0.78f, -6f, 5f, 3.8f, 0.8f),
            )) {
                val p = stroke.point((f * stroke.last).roundToInt())
                val t = tri((twinkle.value + lag / 1.3f) % 1f)
                drawSparkle((p.x.toFloat() + ox) * unit, (p.y.toFloat() + oy) * unit, r * unit * (0.55f + 0.55f * t), PawSunshine, 0.6f + 0.4f * t)
            }
        }
    }
}

// ------------------------------------------------------------------ celebration

/** A white shimmer along every stroke, sunshine sparkles and coral hearts around the glyph (about 2s, then it loops until play-on). */
@Composable
private fun CelebrationEffects(cache: GlyphPathCache) {
    val transition = rememberInfiniteTransition(label = "celebration")
    val shimmer = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "shimmer")
    val twinkle = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sparkles")
    val hearts = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "hearts")
    // The stroke paths are built once for this glyph and size, not on every frame of the looping celebration.
    val layer = remember(cache) {
        Modifier.fillMaxSize().drawWithCache {
            val unit = size.width / 100f
            val paths = cache.ensure(unit)
            onDrawBehind { drawCelebration(paths, unit, shimmer.value, twinkle.value, hearts.value) }
        }
    }
    Box(layer)
}

private fun DrawScope.drawCelebration(paths: GlyphPathCache, unit: Float, shimmer: Float, twinkle: Float, hearts: Float) {
    paths.exact.forEachIndexed { i, path -> drawShimmer(path, paths.lengthsPx[i], 6f * unit, shimmer) }
    for ((x, y, r, lag) in SPARKLES) {
        val t = tri((twinkle + lag / 1.3f) % 1f)
        drawSparkle(x * unit, y * unit, r * unit * (0.55f + 0.55f * t), PawSunshine, 0.6f + 0.4f * t)
    }
    for ((x, y, r, lag) in HEARTS) {
        val p = (hearts + lag / 1.8f) % 1f
        drawHeart(x * unit, (y + 1f - 3.5f * p) * unit, r * unit, PawCoral, 0.3f + 0.7f * tri(p))
    }
}

// Glyph-unit positions round the box, from the mockup's celebration: x, y, size, delay.
private val SPARKLES = listOf(
    listOf(10f, 22f, 4.5f, 0f), listOf(92f, 14f, 5.5f, 0.35f), listOf(4f, 68f, 3.6f, 0.7f), listOf(96f, 62f, 4.4f, 1f),
    listOf(28f, -4f, 3.2f, 0.5f), listOf(74f, -2f, 3.6f, 0.15f), listOf(16f, 96f, 4.2f, 0.9f), listOf(86f, 94f, 3.8f, 0.55f),
    listOf(50f, 102f, 3.4f, 0.3f), listOf(-2f, 44f, 3f, 0.2f), listOf(102f, 40f, 3f, 0.8f),
)
private val HEARTS = listOf(
    listOf(12f, 44f, 7f, 0.2f), listOf(90f, 44f, 6.5f, 0.8f), listOf(78f, -10f, 5f, 1.1f), listOf(24f, -8f, 4.6f, 0.5f),
)

/** 0 -> 1 -> 0 over a 0..1 phase. */
private fun tri(p: Float) = 1f - abs(2f * p - 1f)

// ------------------------------------------------------------------ the frog

/**
 * The marker. Stands on the next thing to paint, ahead of the paint so a fingertip never hides it; hops to
 * the next stroke when one finishes; walks the stroke when the child pauses; hops happily on the finished
 * glyph. Purely visual: it never takes a touch.
 */
@Composable
private fun FrogMarker(
    session: TraceSession,
    revision: State<Int>,
    serial: Int,
    celebrating: Boolean,
    walk: Animatable<Float, *>,
    boxDp: Float,
    boxTopDp: Float,
) {
    revision.value // repositions as the paint grows
    val paint = session.paint
    val marker = paint.marker()
    val unitDp = boxDp / 100f
    val markerTarget = when {
        celebrating -> Offset(50f * unitDp, -8f * unitDp)
        marker != null -> paint.strokes[marker.stroke].stroke.point(marker.index).let { Offset(it.x.toFloat() * unitDp, it.y.toFloat() * unitDp) }
        else -> null
    }
    val visible = markerTarget != null
    val target = markerTarget ?: Offset(50f * unitDp, -8f * unitDp)

    val slide = remember { Animatable(target, Offset.VectorConverter) }
    val lastSerial = remember { intArrayOf(serial) }
    val lastStroke = remember { intArrayOf(marker?.stroke ?: -1) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(serial, target) {
        if (lastSerial[0] != serial) {
            lastSerial[0] = serial
            lastStroke[0] = marker?.stroke ?: -1
            slide.snapTo(target)
        } else {
            slide.animateTo(target, tween(MARKER_SLIDE_MS, easing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)))
        }
    }
    // Hop between strokes: a small arc up while the frog slides across.
    LaunchedEffect(serial, marker?.stroke) {
        val stroke = marker?.stroke ?: -1
        if (lastStroke[0] != stroke && stroke >= 0 && lastSerial[0] == serial) {
            lastStroke[0] = stroke
            hop.snapTo(0f)
            hop.animateTo(1f, tween(MARKER_HOP_MS, easing = LinearEasing))
        }
    }
    val transition = rememberInfiniteTransition(label = "frog")
    val bob = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val happyHop = transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 1000; 0f at 0; 1f at 350; 0f at 1000 }),
        label = "happy-hop",
    )

    val walkSamples = if (marker != null) {
        val stroke = paint.strokes[marker.stroke].stroke
        min(paint.tuning.hintWalk / SAMPLE_STEP, (stroke.last - marker.index).toDouble())
    } else {
        0.0
    }

    Box(
        Modifier
            .offset {
                val density = this.density
                val base = if (!celebrating && marker != null && walk.value > 0f) {
                    val p = paint.strokes[marker.stroke].stroke.pointAt(marker.index + walk.value * walkSamples)
                    Offset(p.x.toFloat() * unitDp, p.y.toFloat() * unitDp)
                } else {
                    slide.value
                }
                val lift = if (celebrating) {
                    // On a short screen the happy hop is trimmed so the halo never runs off the top edge.
                    -min(HAPPY_HOP_DP, max(0f, boxTopDp + base.y - HALO_FAR_DP / 2f - 2f)) * happyHop.value
                } else {
                    -MARKER_BOB_DP * bob.value - MARKER_HOP_DP * sin(PI.toFloat() * hop.value)
                }
                IntOffset(
                    ((base.x - HALO_FAR_DP / 2f) * density).roundToInt(),
                    ((base.y - HALO_FAR_DP / 2f + lift) * density).roundToInt(),
                )
            }
            .size(HALO_FAR_DP.dp)
            .graphicsLayer { alpha = if (visible) 1f else 0f },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            drawCircle(PawSunshine, radius = HALO_FAR_DP / 2f * density, center = c, alpha = 0.14f)
            drawCircle(PawSunshine, radius = HALO_NEAR_DP / 2f * density, center = c, alpha = 0.30f)
            drawCircle(Color.White, radius = RING_DP / 2f * density, center = c)
        }
        FrogFace(size = FROG_DP.dp, happy = celebrating)
    }
}

// ------------------------------------------------------------------ buttons

/** The one big play-on button: appears only when a glyph is complete. It hops gently and never advances by itself. */
@Composable
private fun PlayOnButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
    val hop = rememberInfiniteTransition(label = "play-on-hop").animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 1100; 0f at 0; 1f at 495; 0f at 1100 }),
        label = "hop",
    )
    Box(
        modifier = modifier
            .size(TraceLayout.PLAY_ON_SIZE.dp)
            .graphicsLayer {
                val h = hop.value
                translationY = -7f * density * h
                val s = (0.6f + 0.4f * appear.value) * (1f + 0.06f * h)
                scaleX = s; scaleY = s
                alpha = appear.value
            }
            .drawBehind { drawCircle(PawLeaf, radius = size.minDimension / 2f + 6f * density, alpha = 0.22f) }
            .clip(CircleShape)
            .background(PawLeaf)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .semantics { contentDescription = "Next" },
        contentAlignment = Alignment.Center,
    ) {
        PawPrintIcon(modifier = Modifier.size(46.dp), tint = Color.White)
    }
}

// Same 56dp home button as Paw Match, Paw Pour and Paw Kitchen. It is private in each of them, so it is
// repeated here rather than importing another game's internals or editing another game
// (docs/DECISIONS.md: worth moving to ui/ now that there are four copies).
@Composable
private fun ExitButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(TraceLayout.HOME_SIZE.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Back to home" },
        contentAlignment = Alignment.Center,
    ) {
        HomeGlyphIcon(modifier = Modifier.size(28.dp))
    }
}

// ------------------------------------------------------------------ home tile

private const val TILE_WAVE = "M10,60 C16,28 30,28 37,60 C44,92 57,92 64,60 C71,28 84,28 90,60"
private const val TILE_PAINTED = 0.54f
private const val TILE_FROG_AT = 0.68f

/**
 * A wavy line, its first half painted grape, with the frog at the paint's tip and two sparkles, and a grape
 * paw badge bottom-right. Every measure is a fraction of [size], like the other tile icons.
 */
@Composable
private fun PawTraceTileIcon(size: Dp) {
    val wave = remember { sampleStroke(parseStrokePath(TILE_WAVE)) }
    val artSize = size * (112f / TILE_REFERENCE_DP)
    val tipIndex = (TILE_FROG_AT * wave.last).roundToInt()
    val tip = wave.point(tipIndex)
    val unit = artSize / 100f // one art unit, as a Dp

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Box(Modifier.size(artSize).offset(y = -size * (4f / TILE_REFERENCE_DP))) {
            Canvas(Modifier.fillMaxSize()) {
                val u = this.size.width / 100f
                val whole = wave.polyline(u)
                val painted = wave.polyline(u, upTo = (TILE_PAINTED * wave.last).roundToInt())
                drawPath(whole, TraceGuideEdge, style = roundStroke(18f * u))
                drawPath(whole, TraceGuideFill, style = roundStroke(14f * u))
                drawPath(painted, TraceGlow, alpha = 0.4f, style = roundStroke(20f * u))
                drawPath(painted, TraceGrape, style = roundStroke(14f * u))
                val tx = tip.x.toFloat() * u
                val ty = tip.y.toFloat() * u
                drawCircle(PawSunshine, radius = 15f * u, center = Offset(tx, ty), alpha = 0.28f)
                drawCircle(Color.White, radius = 12f * u, center = Offset(tx, ty))
                drawSparkle(tx + 16f * u, ty - 14f * u, 4.4f * u)
                drawSparkle(tx - 18f * u, ty + 16f * u, 3.2f * u)
            }
            FrogFace(
                size = unit * 22f,
                happy = false,
                modifier = Modifier.offset(x = unit * (tip.x.toFloat() - 11f), y = unit * (tip.y.toFloat() - 11f)),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.155f)
                .clip(RoundedCornerShape(size * 0.055f))
                .background(TraceGrape),
            contentAlignment = Alignment.Center,
        ) {
            PawPrintIcon(modifier = Modifier.size(size * 0.09f), tint = Color.White)
        }
    }
}
