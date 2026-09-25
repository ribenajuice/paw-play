package com.pawplay.app.games.pawpop

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawplay.app.games.MiniGame
import com.pawplay.app.games.pawmatch.HomeGlyphIcon
import com.pawplay.app.games.pawmatch.PawPrintIcon
import com.pawplay.app.ui.theme.PawSunshine

object PawPopGame : MiniGame {
    override val id = "paw-pop"
    override val icon: @Composable (Dp) -> Unit = { size -> PawPopTileIcon(size) }
    override val content: @Composable (onExit: () -> Unit) -> Unit = { onExit -> PawPopScreen(onExit) }
}

/**
 * Session-only, in-memory: entering from the home screen always starts with the ship in the middle, already firing,
 * and the first target due within a second; nothing is saved (docs/PRD.md stories 51 and 60). All rules and timing
 * live in the pure `PopSession`; this only turns frames and touches into calls on it and draws the result.
 */
@Composable
private fun PawPopScreen(onExit: () -> Unit) {
    val session = remember { PopSession() }
    PopScene(session, onExit)
}

@Composable
internal fun PopScene(session: PopSession, onExit: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val ui = remember(session) { PopUi(session) }
        val width = maxWidth.value
        val height = maxHeight.value
        SideEffect { session.setArea(width, height) } // a side effect, not a remember: nothing here may return Unit into remember

        // The sky is still, so it is drawn once (it reads no state) and only again if the play area changes size.
        Canvas(Modifier.fillMaxSize()) { scale(density, Offset.Zero) { drawSky(width, height) } }

        // The game never rests: a frame every frame, for as long as the screen is here. After the app was in the
        // background the first frame is one short step (the session caps a step at 50ms).
        LaunchedEffect(ui) {
            while (true) withFrameNanos { ui.onFrame(it) }
        }

        Canvas(Modifier.fillMaxSize()) {
            ui.frame.longValue // reading it makes this redraw every frame
            scale(density, Offset.Zero) { drawScene(ui) }
        }

        // One full-screen touch layer under the home button: a finger may land anywhere and steers at once, and
        // the newest finger steers. A cancelled touch, or a lift that went missing, leaves the ship where it is.
        Box(
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

        // Home: always on screen, always live, drawn last so it stays on top, same corner as the other games.
        ExitButton(
            onClick = onExit,
            modifier = Modifier.offset(x = PopMetrics.HOME_INSET.dp, y = PopMetrics.HOME_INSET.dp),
        )
    }
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
