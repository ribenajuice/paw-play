package com.pawplay.app.games.pawblocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pawplay.app.games.MiniGame
import com.pawplay.app.games.pawmatch.HomeGlyphIcon
import com.pawplay.app.games.pawmatch.PawPrintIcon
import com.pawplay.app.ui.theme.PawSky

object PawBlocksGame : MiniGame {
    override val id = "paw-blocks"
    override val icon: @Composable (Dp) -> Unit = { size -> PawBlocksTileIcon(size) }
    override val content: @Composable (onExit: () -> Unit) -> Unit = { onExit -> PawBlocksScreen(onExit) }
}

/**
 * Session-only, in-memory: entering from the home screen always starts on an empty 5x5 board with three blocks
 * in the tray, and nothing is saved (docs/PRD.md stories 39 and 46). All rules and timing live in the pure
 * `BlocksSession`; this only turns touches into calls on it and draws the result.
 */
@Composable
private fun PawBlocksScreen(onExit: () -> Unit) {
    val session = remember { BlocksSession() }
    BlocksScene(session, onExit)
}

@Composable
internal fun BlocksScene(session: BlocksSession, onExit: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val layout = remember(maxWidth, maxHeight) { blocksLayout(maxWidth.value, maxHeight.value) }
        val ui = remember(session) { BlocksUi(session) }
        SideEffect { ui.layout = layout } // a side effect, not a remember: nothing here may return Unit into remember

        // The frame loop runs only while something moves or is due (a finger down, an effect playing, a refill,
        // growth or clear-out waiting); a quiet board costs nothing. Any touch that changes things wakes it.
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
        // is cancelled, or whose finger vanishes, sends the block home.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(ui) {
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                var touched = false
                                for (change in event.changes) {
                                    val id = change.id.value
                                    val x = change.position.x / density
                                    val y = change.position.y / density
                                    val took = when {
                                        change.pressed && !change.previousPressed -> ui.onDown(id, x, y)
                                        change.pressed -> ui.onMove(id, x, y)
                                        change.previousPressed -> ui.onUp(id, x, y)
                                        else -> false
                                    }
                                    if (took) { change.consume(); touched = true }
                                }
                                // Nothing is down any more: a lift went missing, so the block goes home.
                                if (event.changes.none { it.pressed } && ui.cancelDrag()) touched = true
                                if (touched) wake.intValue++
                            }
                        }
                    } finally {
                        ui.cancelDrag() // the touch layer is going away mid-drag (screen left, window lost)
                    }
                },
        )

        // Home: always on screen, always live, drawn last so it stays on top, same corner as the other games.
        ExitButton(
            onClick = onExit,
            modifier = Modifier.offset(x = BlocksLayout.HOME_INSET.dp, y = BlocksLayout.HOME_INSET.dp),
        )
    }
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
