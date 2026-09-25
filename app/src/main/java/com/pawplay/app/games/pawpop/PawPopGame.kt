package com.pawplay.app.games.pawpop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawplay.app.data.BestScore
import com.pawplay.app.data.rememberBestScore
import com.pawplay.app.games.MiniGame
import com.pawplay.app.games.pawmatch.HomeGlyphIcon
import com.pawplay.app.games.pawmatch.PawPrintIcon
import com.pawplay.app.ui.GoodGameScreen
import com.pawplay.app.ui.PAW_FADE_MS
import com.pawplay.app.ui.PawLivesRow
import com.pawplay.app.ui.ScoreNumber
import com.pawplay.app.ui.theme.PawSunshine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

object PawPopGame : MiniGame {
    override val id = "paw-pop"
    override val icon: @Composable (Dp) -> Unit = { size -> PawPopTileIcon(size) }
    override val content: @Composable (onExit: () -> Unit) -> Unit = { onExit -> PawPopScreen(onExit) }
}

/** This game's own saved-best key (docs/DECISIONS.md, "Best scores"). Paw Blocks has its own; neither game can see the other's. */
internal const val BEST_KEY = "best.paw-pop"

/** How long opening the game may wait for the saved best to be read before counting it as 0. */
private const val BEST_READ_CAP_MS = 500L

/**
 * Entering from the home screen always starts a fresh game: ship in the middle, already firing, first target due within a
 * second, stage 1, score 0 and 3 paws (docs/PRD.md stories 51, 60, 68). Only the best score is saved (story 70); the
 * session is not. All rules and timing live in the pure `PopSession`; this only turns frames and touches into calls on it and
 * draws the result.
 *
 * The home button shows at once. The game follows as soon as the saved best has been read off the main thread (a phone that
 * cannot read it, or takes over 0.5s, counts it as 0; nothing is shown either way).
 */
@Composable
private fun PawPopScreen(onExit: () -> Unit) {
    val best = rememberBestScore(BEST_KEY)
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(best) {
        val read = launch(Dispatchers.IO) { best.load() }
        withTimeoutOrNull(BEST_READ_CAP_MS) { read.join() } // a slow read keeps going in the background and merges when done
        loaded = true
    }
    var game by remember { mutableIntStateOf(0) } // play again = the next number = a fresh session
    if (!loaded) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PopSkyTop, PopSkyBottom))))
            ExitButton(onClick = onExit, modifier = Modifier.offset(x = PopMetrics.HOME_INSET.dp, y = PopMetrics.HOME_INSET.dp))
        }
    } else {
        val session = remember(game) { PopSession(bestBefore = best.best) }
        PopScene(session, best, onExit, onPlayAgain = { game++ })
    }
}

@Composable
internal fun PopScene(session: PopSession, best: BestScore, onExit: () -> Unit, onPlayAgain: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val ui = remember(session) { PopUi(session, onScore = { best.submit(it) }) }
        val width = maxWidth.value
        val height = maxHeight.value
        SideEffect { session.setArea(width, height) } // a side effect, not a remember: nothing here may return Unit into remember
        val bank = remember(width) { PopBank(width) }
        val belowBank = remember(width, height) { belowBank(width, height) }

        // True once the game is over; only flips once, so the rest of the scene does not recompose every frame.
        val over by remember(ui) { derivedStateOf { ui.frame.longValue; session.phase == PopPhase.OVER } }

        // The sky is still, so it is its own layer with its gradient made once (in `drawWithCache`): the moving scene
        // above it redraws every frame, and this layer's recorded drawing is reused as it is, not run again.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val brush = skyBrush(size.height / density)
                    onDrawBehind { scale(density, Offset.Zero) { drawSky(width, height, brush, bank) } }
                },
        )

        // The game never rests: a frame every frame, for as long as the screen is here. After the app was in the
        // background the first frame is one short step (the session caps a step at 50ms), so a pause never costs a paw.
        // The frame callback is made once, not on every turn of the loop.
        LaunchedEffect(ui) {
            val tick: (Long) -> Unit = { ui.onFrame(it) }
            while (true) withFrameNanos(tick)
        }

        Canvas(Modifier.fillMaxSize()) {
            ui.frame.longValue // reading it makes this redraw every frame
            scale(density, Offset.Zero) { drawScene(ui, belowBank) }
        }

        // One full-screen touch layer under the home button: a finger may land anywhere and steers at once, and
        // the newest finger steers. A cancelled touch, or a lift that went missing, leaves the ship where it is.
        // Gone once the game is over.
        if (!over) Box(
            Modifier
                .fillMaxSize()
                .pointerInput(ui) {
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    val took = ui.onPointer(
                                        change.id.value, change.position.x / density, change.position.y / density,
                                        change.pressed, change.previousPressed, change.isConsumed,
                                    )
                                    if (took != PopUi.Took.NOTHING) change.consume()
                                }
                                // Nothing is down any more: whatever was steering has gone, so the ship stays put.
                                if (event.changes.none { it.pressed }) ui.cancelAll()
                            }
                        }
                    } finally {
                        ui.cancelAll() // the touch layer is going away (screen left, window lost)
                    }
                },
        )

        // The top strip: paws, then the score at the far right. Neither is a button; taps on them do nothing.
        // Hidden on the good-game screen (a row of hollow paws would read as sad).
        if (!over) PopStrip(ui)

        if (over) {
            val critter = remember(session) { Random.nextInt(4) } // which of the four animals smiles this time
            GoodGameScreen(
                score = session.score,
                best = maxOf(best.best, session.score),
                isNewBest = session.isNewBest,
                animal = { mod -> PopGoodGameAnimal(critter, happy = session.isNewBest, modifier = mod) },
                onPlayAgain = onPlayAgain,
                onHome = onExit,
                wash = Color.White.copy(alpha = 0.20f),
            )
        }

        // Home: always on screen, always live, drawn last so it stays on top (even over the good-game screen).
        ExitButton(
            onClick = onExit,
            modifier = Modifier.offset(x = PopMetrics.HOME_INSET.dp, y = PopMetrics.HOME_INSET.dp),
        )
    }
}

/** Three paws beside the home button and the score opposite it (docs/DESIGN-SYSTEM.md, "Top strip"), on the cloud bank. */
@Composable
private fun BoxWithConstraintsScope.PopStrip(ui: PopUi) {
    val s = ui.session
    val paws by remember(ui) { derivedStateOf { ui.frame.longValue; s.paws } }
    val score by remember(ui) { derivedStateOf { ui.frame.longValue; s.score } }
    val fadeIndex by remember(ui) { derivedStateOf { ui.frame.longValue; s.pawFadeIndex } }
    val fadeStart by remember(ui) { derivedStateOf { ui.frame.longValue; s.pawFadeStartMs } }
    // Time only matters while a paw fades; outside that window the value is constant, so the row is not redrawn every frame.
    val now by remember(ui) { derivedStateOf { ui.frame.longValue; s.timeMs.coerceIn(s.pawFadeStartMs, s.pawFadeStartMs + PAW_FADE_MS) } }
    PawLivesRow(
        paws = paws,
        fadingIndex = fadeIndex.takeIf { it >= 0 },
        fadeStartMs = fadeStart,
        nowMs = now,
        modifier = Modifier.offset(x = PopMetrics.PAWS_LEFT.dp, y = PopMetrics.PAWS_TOP.dp),
    )
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = PopMetrics.STRIP_TOP.dp, end = PopMetrics.SCORE_MARGIN_RIGHT.dp)
            .height(PopMetrics.HOME_SIZE.dp),
        contentAlignment = Alignment.CenterEnd,
    ) { ScoreNumber(score) }
}

/** The good-game screen's animal: one of Pop's four critters, smiling calmly, or the happy face on a new best. */
@Composable
private fun PopGoodGameAnimal(critter: Int, happy: Boolean, modifier: Modifier) {
    Canvas(modifier) { goodGameCritter(critter, happy) }
}

// Same 56dp home button as the other games. It is private in each of them, so it is repeated here rather than
// importing another game's internals or editing another game (docs/DECISIONS.md: worth moving to ui/ now that
// there are six copies).
@Composable
private fun ExitButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(PopMetrics.HOME_SIZE.dp)
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

private const val TILE_REFERENCE = 146f // the mockup's tile size; every measurement below is a fraction of it

/**
 * A teal oval balloon (50 across, centre (104, 46)), a star (outer radius 14, tilted 12 degrees, centre (62, 48))
 * on its way up with two small sparkles, the friendly ship (72 box, centre (50, 100)), and a teal paw badge
 * bottom-right. Every measure is a fraction of [size].
 */
@Composable
private fun PawPopTileIcon(size: Dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            scale(this.size.width / TILE_REFERENCE, Offset.Zero) {
                // The balloon is drawn at 50 across with its 3 tile-units rim (not constant in dp, the whole tile scales).
                translate(104f, 46f) {
                    scale(0.5f, 0.5f, Offset.Zero) { targetBody(TargetKind.OVAL, TargetColors[4], 50f, 0) }
                }
                drawStar(62f, 48f, 14f, 12f)
                drawSparkle(76f, 72f, 4.6f, PawSunshine)
                drawSparkle(54f, 76f, 3.2f, PawSunshine)
                translate(50f, 100f) { scale(72f / 88f, 72f / 88f, Offset.Zero) { drawShip(0f, 0f, 1f) } }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.155f)
                .clip(RoundedCornerShape(size * 0.055f))
                .background(PopBadgeTeal),
            contentAlignment = Alignment.Center,
        ) {
            PawPrintIcon(modifier = Modifier.size(size * 0.09f), tint = Color.White)
        }
    }
}
