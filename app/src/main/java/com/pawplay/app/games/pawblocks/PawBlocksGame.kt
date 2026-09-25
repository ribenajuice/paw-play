package com.pawplay.app.games.pawblocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pawplay.app.data.BestScore
import com.pawplay.app.data.rememberBestScore
import com.pawplay.app.games.MiniGame
import com.pawplay.app.games.pawmatch.HomeGlyphIcon
import com.pawplay.app.games.pawmatch.PawPrintIcon
import com.pawplay.app.ui.GoodGameScreen
import com.pawplay.app.ui.PAW_FADE_MS
import com.pawplay.app.ui.PawLivesRow
import com.pawplay.app.ui.ScoreNumber
import com.pawplay.app.ui.theme.PawSky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object PawBlocksGame : MiniGame {
    override val id = "paw-blocks"
    override val icon: @Composable (Dp) -> Unit = { size -> PawBlocksTileIcon(size) }
    override val content: @Composable (onExit: () -> Unit) -> Unit = { onExit -> PawBlocksScreen(onExit) }
}

/** This game's own saved-best key (docs/DECISIONS.md, "Best scores"). Paw Pop has its own; neither game can see the other's. */
internal const val BEST_KEY = "best.paw-blocks"

/** How long opening the game may wait for the saved best to be read before counting it as 0. */
private const val BEST_READ_CAP_MS = 500L

/**
 * Entering from the home screen always starts a fresh game on an empty 5x5 board with three blocks in the tray, score
 * 0 and 3 paws (docs/PRD.md stories 39, 46 and 64). Only the best score is saved (story 70); the session is not. All
 * rules and timing live in the pure `BlocksSession`; this only turns touches into calls on it and draws the result.
 *
 * The home button shows at once. The play area follows as soon as the saved best has been read off the main thread
 * (a phone that cannot read it, or takes over 0.5s, counts it as 0; nothing is shown either way).
 */
@Composable
private fun PawBlocksScreen(onExit: () -> Unit) {
    val best = rememberBestScore(BEST_KEY)
    var loaded by remember { mutableStateOf(false) }
    var readDone by remember { mutableStateOf(best.isLoaded) }
    LaunchedEffect(best) {
        val read = launch(Dispatchers.IO) { best.load() }
        withTimeoutOrNull(BEST_READ_CAP_MS) { read.join() } // a slow read keeps going in the background and merges when done
        loaded = true
        read.join() // play is not held up: only "new best" waits for the saved value
        readDone = true
    }
    var game by remember { mutableIntStateOf(0) } // play again = the next number = a fresh session
    // Bumped each time the saved best has been folded into the session's "new best" judgement; the good-game screen reads it,
    // so a slow read that ends after the game did still updates the screen (its animal, celebration and best number).
    val judged = remember { mutableIntStateOf(0) }
    if (!loaded) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { HomeCorner(onExit) }
    } else {
        val session = remember(game) { BlocksSession(bestBefore = best.best, bestKnown = best.isLoaded) }
        // A read that outlasted the cap: once it ends, "new best" is judged against the saved value too.
        LaunchedEffect(session, readDone) {
            if (readDone) {
                session.learnBest(best.storedAtLoad)
                judged.intValue++
            }
        }
        BlocksScene(session, best, onExit, onPlayAgain = { game++ }, judged = judged)
    }
}

@Composable
internal fun BlocksScene(
    session: BlocksSession,
    best: BestScore,
    onExit: () -> Unit,
    onPlayAgain: () -> Unit,
    /** Changes whenever the saved best has been learned (see [PawBlocksScreen]); read where the ending is drawn. */
    judged: IntState = remember { mutableIntStateOf(0) },
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val layout = remember(maxWidth, maxHeight) { blocksLayout(maxWidth.value, maxHeight.value) }
        // The best is saved the moment a placement lifts the score past it, so leaving early never loses it.
        val ui = remember(session) { BlocksUi(session, onScore = { best.submit(it) }) }
        SideEffect { ui.layout = layout } // a side effect, not a remember: nothing here may return Unit into remember

        // True once the game has ended; only flips once, so the rest of the scene does not recompose every frame.
        val over by remember(ui) { derivedStateOf { ui.frame.longValue; ui.revision.intValue; session.phase == BlocksPhase.GAME_OVER } }

        // The frame loop runs only while something moves or is due (a finger down, an effect playing, a refill,
        // growth, a clear-out or the ending waiting); a quiet board costs nothing. Any touch that changes things wakes it.
        // Coming back from the background does not count as time passed: the stuck wait, the refill and the rest carry on from where they were.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(ui, lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) ui.resumed() }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val wake = remember { mutableIntStateOf(0) }
        LaunchedEffect(ui, wake.intValue) {
            do {
                withFrameMillis { ui.onFrame(it) }
            } while (ui.active)
        }

        // Draws the play area. Reading `frame` and `revision` here makes it redraw every frame while the loop runs.
        Canvas(Modifier.fillMaxSize()) {
            val now = ui.frame.longValue
            ui.revision.intValue
            scale(density, Offset.Zero) { drawScene(ui, now) }
        }

        // One full-screen touch layer under the home button: a finger may land anywhere, but only a finger that
        // lands on a tray block drags, and only the first such finger is followed (see DragTracker). A drag that
        // is cancelled, or whose finger vanishes, sends the block home. Gone once the game has ended.
        if (!over) Box(
            Modifier
                .fillMaxSize()
                .pointerInput(ui) {
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                var changed = false
                                for (change in event.changes) {
                                    val took = ui.onPointer(
                                        change.id.value, change.position.x / density, change.position.y / density,
                                        change.pressed, change.previousPressed, change.isConsumed,
                                    )
                                    if (took != BlocksUi.Took.NOTHING) change.consume()
                                    if (took == BlocksUi.Took.CHANGED) changed = true
                                }
                                // Nothing is down any more: a lift went missing, so the block goes home.
                                if (event.changes.none { it.pressed } && ui.cancelDrag()) changed = true
                                // Only a drag starting or ending wakes the frame loop: it already runs on every frame while a finger drags.
                                if (changed) wake.intValue++
                            }
                        }
                    } finally {
                        ui.cancelDrag() // the touch layer is going away mid-drag (screen left, window lost, game ended)
                    }
                },
        )

        // The top strip: paws, then the score at the far right. Neither is a button; taps on them do nothing.
        // Hidden on the good-game screen (a row of hollow paws would read as sad).
        if (!over) BlocksStrip(ui)

        if (over) {
            // Read here, so the ending follows the final judgement even when the saved best is only learned after it shows.
            judged.intValue
            val isNewBest = session.isNewBest
            GoodGameScreen(
                score = session.score,
                best = maxOf(best.best, session.score),
                isNewBest = isNewBest,
                animal = { mod -> BlocksGoodGameAnimal(happy = isNewBest, modifier = mod) },
                onPlayAgain = onPlayAgain,
                onHome = onExit,
            )
        }

        // Home: always on screen, always live, drawn last so it stays on top (even over the good-game screen),
        // same corner as the other games.
        HomeCorner(onExit)
    }
}

/** Three paws beside the home button and the score opposite it (docs/DESIGN-SYSTEM.md, "Top strip"). */
@Composable
private fun BoxWithConstraintsScope.BlocksStrip(ui: BlocksUi) {
    val paws by remember(ui) { derivedStateOf { ui.frame.longValue; ui.revision.intValue; ui.session.paws } }
    val score by remember(ui) { derivedStateOf { ui.frame.longValue; ui.revision.intValue; ui.session.score } }
    val fadeIndex by remember(ui) { derivedStateOf { ui.frame.longValue; ui.pawFadeIndex } }
    val fadeStart by remember(ui) { derivedStateOf { ui.frame.longValue; ui.pawFadeStart } }
    // Time only matters while a paw fades; outside that window the value is constant, so the row is not redrawn every frame
    // (this function itself no longer reads the frame clock, so it does not recompose on every frame).
    val now by remember(ui) { derivedStateOf { val start = ui.pawFadeStart; ui.frame.longValue.coerceIn(start, start + PAW_FADE_MS) } }
    PawLivesRow(
        paws = paws,
        fadingIndex = fadeIndex.takeIf { it >= 0 },
        fadeStartMs = fadeStart,
        nowMs = now,
        modifier = Modifier.offset(x = BlocksLayout.pawLeft(0).dp, y = BlocksLayout.PAWS_TOP.dp),
    )
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = BlocksLayout.STRIP_TOP.dp, end = BlocksLayout.SCORE_MARGIN_RIGHT.dp)
            .height(BlocksLayout.HOME_SIZE.dp),
        contentAlignment = Alignment.CenterEnd,
    ) { ScoreNumber(score) }
}

/** The good-game screen's animal: the peek-up fox with its calm face, or the happy one for a new best. */
@Composable
private fun BlocksGoodGameAnimal(happy: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        if (happy) drawHappyCritter(GOOD_GAME_CRITTER, size.minDimension) else drawCalmCritter(GOOD_GAME_CRITTER, size.minDimension)
    }
}

private const val GOOD_GAME_CRITTER = 0 // the fox, as in the design

@Composable
private fun HomeCorner(onExit: () -> Unit) {
    ExitButton(
        onClick = onExit,
        modifier = Modifier.offset(x = BlocksLayout.HOME_INSET.dp, y = BlocksLayout.HOME_INSET.dp),
    )
}

// Same 56dp home button as the other games. It is private in each of them, so it is repeated here rather than
// importing another game's internals or editing another game (docs/DECISIONS.md: worth moving to ui/ now that
// there are five copies).
@Composable
private fun ExitButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BlocksLayout.HOME_SIZE.dp)
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
 * A 3 x 3 patch of the board (cells 32 of 146, top-left at 25, 25): an orange square and a pink bar of 2 placed,
 * a sky ghost in the last column and a blue bar of 3 being lowered into it (lifted 13, moved 7 right, tilted 6
 * degrees, with its shadow), and a bubblegum-pink paw badge bottom-right. Every measure is a fraction of [size].
 */
@Composable
private fun PawBlocksTileIcon(size: Dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            scale(this.size.width / TILE_REFERENCE, Offset.Zero) {
                val s = 32f
                val gx = 25f
                val gy = 25f
                for (r in 0 until 3) for (c in 0 until 3) drawEmptyCell(gx + c * s, gy + r * s, s)
                drawBlock(BlockShapes.byId.getValue("sq"), gx, gy, s)
                drawBlock(BlockShapes.byId.getValue("bar2h"), gx, gy + 2 * s, s)
                for (r in 0 until 3) {
                    val topLeft = Offset(gx + 2 * s + 1f, gy + r * s + 1f)
                    drawRoundRect(PawSky, topLeft, Size(s - 2f, s - 2f), CornerRadius(7f), alpha = 0.22f)
                    drawRoundRect(BlocksGhostEdge, topLeft, Size(s - 2f, s - 2f), CornerRadius(7f), style = Stroke(2.5f))
                }
                val bar = BlockShapes.byId.getValue("bar3v")
                val hx = gx + 2 * s + 7f
                val hy = gy - 13f
                rotate(6f, Offset(hx + s / 2, hy + 1.5f * s)) {
                    drawBlockShadow(bar, hx, hy, s)
                    drawBlock(bar, hx, hy, s)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.155f)
                .clip(RoundedCornerShape(size * 0.055f))
                .background(BlocksBadgePink),
            contentAlignment = Alignment.Center,
        ) {
            PawPrintIcon(modifier = Modifier.size(size * 0.09f), tint = Color.White)
        }
    }
}
