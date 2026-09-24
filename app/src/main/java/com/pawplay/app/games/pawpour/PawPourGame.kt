package com.pawplay.app.games.pawpour

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pawplay.app.games.MiniGame
import com.pawplay.app.games.pawmatch.HomeGlyphIcon
import com.pawplay.app.games.pawmatch.PawPrintIcon
import com.pawplay.app.games.pawmatch.StarBadgeIcon
import com.pawplay.app.ui.theme.PawLeaf
import com.pawplay.app.ui.theme.PawSky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Timings: soft and unhurried. Paw Pour ships silent for now, matching Paw Match (docs/DECISIONS.md, 2026-09-25).
private const val POUR_MS = 520
private const val UNPOUR_MS = 700
private const val UNPOUR_GAP_MS = 200L
private const val REWIND_PAUSE_MS = 900L // about a second after the pour lands, per docs/PRD.md "Stuck"
private const val WIN_PAUSE_MS = 700L    // let the last tube's sparkle land before the celebration
private const val POUR_TILT_DEGREES = 16f
private const val TILE_REFERENCE_DP = 146f // the mockup's tile size; every tile measurement is a fraction of it

object PawPourGame : MiniGame {
    override val id = "paw-pour"
    override val icon: @Composable (Dp) -> Unit = { size -> PawPourTileIcon(size) }
    override val content: @Composable (onExit: () -> Unit) -> Unit = { onExit -> PawPourScreen(onExit) }
}

/** Three small tubes, the middle one raised, with a sky-blue paw badge in the corner. */
@Composable
private fun PawPourTileIcon(size: Dp) {
    val scale = size.value / TILE_REFERENCE_DP
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(size * 0.041f),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.offset(y = -size * 0.014f),
        ) {
            listOf(
                listOf(BandColor.SKY, BandColor.BUBBLEGUM, BandColor.SUNSHINE) to 0f,
                listOf(BandColor.CORAL, BandColor.SUNSHINE, BandColor.SKY) to 0.062f,
                listOf(BandColor.LEAF, BandColor.CORAL, BandColor.BUBBLEGUM) to 0f,
            ).forEach { (bands, raise) ->
                TubeCanvas(
                    bands = bands,
                    capacity = 3,
                    width = size * 0.164f,
                    bandHeight = size * 0.116f,
                    scale = scale,
                    showMarks = false,
                    modifier = Modifier.offset(y = -size * raise),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.155f)
                .clip(RoundedCornerShape(size * 0.055f))
                .background(PawSky),
            contentAlignment = Alignment.Center,
        ) {
            PawPrintIcon(modifier = Modifier.size(size * 0.09f), tint = Color.White)
        }
    }
}

/** A pour (or un-pour) in flight: [count] bands of [color] moving [from] -> [to]. */
private data class PourAnimation(val from: Int, val to: Int, val count: Int, val color: BandColor)

/**
 * Every round is fresh in-memory state — entering from the home screen always starts at round 1;
 * nothing is saved (docs/PRD.md story 16). Taps are ignored while anything is animating, being
 * checked, or a board is being dealt, so mashing the screen or multi-touch can't break the round
 * (story 18). Dealing happens off the main thread: the very first board appears a moment after
 * the screen opens (just the home button until then, no text), and "play again" keeps the win
 * overlay up until the next board is ready, so nothing flickers.
 */
@Composable
private fun PawPourScreen(onExit: () -> Unit) {
    var round by remember { mutableStateOf<RoundState?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(true) } // true until the first board is dealt
    var showWin by remember { mutableStateOf(false) }
    var animation by remember { mutableStateOf<PourAnimation?>(null) }
    // Each tube counts its own wrong taps, so a wobble on one tube never restarts another's.
    val wobbles = remember { mutableStateMapOf<Int, Int>() }
    var playAgainPending by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        round = withContext(Dispatchers.Default) { newRoundState(round = 1) }
        busy = false
    }

    suspend fun runAnimation(move: PourAnimation, durationMs: Int) {
        progress.snapTo(0f)
        animation = move
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }

    suspend fun playPour(start: RoundState, from: Int, to: Int, count: Int) {
        busy = true
        runAnimation(PourAnimation(from, to, count, start.board.tubes[from].last()), POUR_MS)
        val poured = start.poured(from, to)
        round = poured
        animation = null
        selected = null

        if (poured.isWon) {
            delay(WIN_PAUSE_MS)
            showWin = true
            return
        }
        // Is the round still finishable? If not, wait a beat so the child sees what they did,
        // then gently un-pour back to the latest position that can still be finished.
        val checked = withContext(Dispatchers.Default) { poured.checkedFinishable() }
        var current = checked
        round = current
        if (current.needsRewind) {
            delay(REWIND_PAUSE_MS)
            for (step in current.rewindSteps()) {
                val back = PourAnimation(step.move.to, step.move.from, step.move.count, current.board.tubes[step.move.to].last())
                runAnimation(back, UNPOUR_MS)
                current = current.undoLast()
                round = current
                animation = null
                delay(UNPOUR_GAP_MS)
            }
        }
        busy = false
    }

    fun onTubeTap(index: Int) {
        val current = round ?: return
        if (busy || showWin) return
        when (val outcome = current.board.tap(selected, index)) {
            TapOutcome.Ignore -> Unit
            is TapOutcome.Select -> selected = outcome.tube
            TapOutcome.Deselect -> selected = null
            is TapOutcome.Reject -> {
                wobbles[outcome.tube] = (wobbles[outcome.tube] ?: 0) + 1
                selected = null
            }
            is TapOutcome.Pour -> {
                busy = true // set now, so a second tap in the same frame is already ignored
                scope.launch { playPour(current, outcome.from, outcome.to, outcome.count) }
            }
        }
    }

    fun onPlayAgain() {
        val finished = round ?: return
        if (playAgainPending) return
        playAgainPending = true
        scope.launch {
            val next = withContext(Dispatchers.Default) { finished.nextRound() }
            round = next
            selected = null
            animation = null
            busy = false
            showWin = false
            playAgainPending = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExitButton(onClick = onExit, modifier = Modifier.padding(20.dp))
            val current = round
            if (current != null) {
                PourBoard(
                    board = current.board,
                    selected = selected,
                    animation = animation,
                    progress = progress.value,
                    wobbles = wobbles,
                    onTubeTap = ::onTubeTap,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (showWin) {
            WinOverlay(onPlayAgain = ::onPlayAgain, onHome = onExit)
        }
    }
}

@Composable
private fun PourBoard(
    board: Board,
    selected: Int?,
    animation: PourAnimation?,
    progress: Float,
    wobbles: Map<Int, Int>,
    onTubeTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val layout = remember(board.tubes.size, board.capacity, maxWidth, maxHeight) {
            layoutBoard(board.tubes.size, board.capacity, maxWidth.value, maxHeight.value)
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(maxWidth, layout.contentHeight.dp),
        ) {
            board.tubes.forEachIndexed { index, bands ->
                val position = layout.positions[index]
                var shownBands = bands
                var partialCount = 0
                var partialFraction = 1f
                var tilt = 0f
                if (animation != null && index == animation.from) {
                    partialCount = animation.count
                    partialFraction = 1f - progress
                    val towardsRight = layout.positions[animation.to].x > position.x
                    tilt = (if (towardsRight) POUR_TILT_DEGREES else -POUR_TILT_DEGREES) * bell(progress)
                } else if (animation != null && index == animation.to) {
                    shownBands = bands + List(animation.count) { animation.color }
                    partialCount = animation.count
                    partialFraction = progress
                }
                PourTube(
                    bands = shownBands,
                    capacity = board.capacity,
                    width = layout.tubeWidth.dp,
                    bandHeight = layout.bandHeight.dp,
                    raised = index == selected || (animation != null && index == animation.from),
                    finished = board.isTubeComplete(index),
                    partialCount = partialCount,
                    partialFraction = partialFraction,
                    tiltDegrees = tilt,
                    wobbleNonce = wobbles[index] ?: 0,
                    onClick = { onTubeTap(index) },
                    // The tube that is pouring floats above its neighbours while it tips.
                    modifier = Modifier
                        .offset(position.x.dp, position.y.dp)
                        .zIndex(if (animation != null && index == animation.from) 1f else 0f),
                )
            }
            if (animation != null) {
                PourStream(layout, animation, progress)
            }
        }
    }
}

/** A thin ribbon of the poured colour arcing from the source's lip down into the target. */
@Composable
private fun PourStream(layout: BoardLayout, animation: PourAnimation, progress: Float) {
    val color = animation.color.style().color
    Canvas(modifier = Modifier.fillMaxSize()) {
        val from = layout.positions[animation.from]
        val to = layout.positions[animation.to]
        val towardsRight = to.x > from.x
        val startX = (from.x + layout.tubeWidth / 2f + (if (towardsRight) 1f else -1f) * layout.tubeWidth * 0.3f) * density
        val startY = (from.y - LIFT_HEADROOM + 8f) * density
        val endX = (to.x + layout.tubeWidth / 2f) * density
        val endY = (to.y + 16f) * density
        val path = Path().apply {
            moveTo(startX, startY)
            quadraticBezierTo(endX, startY, endX, endY)
        }
        // Fades in, flows, fades out with the pour.
        drawPath(
            path,
            color.copy(alpha = (bell(progress) * 2.5f).coerceAtMost(1f)),
            style = Stroke(width = layout.tubeWidth * 0.12f * density, cap = StrokeCap.Round),
        )
    }
}

// The exit button and win overlay match Paw Match's exactly ("same look and pattern"). They are
// private there, so they're repeated here rather than making Paw Pour depend on Paw Match's
// internals or editing Paw Match; the shared icons they use are the public ones from AnimalIcons.kt.

@Composable
private fun ExitButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Back to home" },
        contentAlignment = Alignment.Center,
    ) {
        HomeGlyphIcon(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun WinOverlay(onPlayAgain: () -> Unit, onHome: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            // Swallow every tap that isn't on a button, so the exit button underneath can't be hit through it.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            StarBadgeIcon(modifier = Modifier.size(96.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BigIconButton(onClick = onPlayAgain, background = PawLeaf, description = "Play again") {
                    PawPrintIcon(modifier = Modifier.size(40.dp), tint = Color.White)
                }
                BigIconButton(onClick = onHome, background = PawSky, description = "Back to home") {
                    HomeGlyphIcon(modifier = Modifier.size(40.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BigIconButton(
    onClick: () -> Unit,
    background: Color,
    description: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
