package com.pawplay.app.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pawplay.app.games.pawmatch.HomeGlyphIcon
import com.pawplay.app.games.pawmatch.PawPrintIcon
import com.pawplay.app.ui.theme.PawCardWhite
import com.pawplay.app.ui.theme.PawCream
import com.pawplay.app.ui.theme.PawLeaf
import com.pawplay.app.ui.theme.PawSky
import com.pawplay.app.ui.theme.PawSunshine
import kotlinx.coroutines.flow.first
import kotlin.math.min

// Sizes from docs/DESIGN-SYSTEM.md, "Good-game screen", in dp. The block below is drawn for a 300 x 440dp box.
private const val BLOCK_WIDTH = 300f
private const val BLOCK_HEIGHT = 440f
private const val ANIMAL_SIZE = 132f
private const val CARD_TOP = 112f
private const val CARD_HEIGHT = 208f
private const val CARD_RADIUS = 29f
private const val BUTTON_SIZE = 88f
private const val BUTTON_GAP = 32f
private const val PILL_TOP = 120f
private const val PILL_HEIGHT = 72f

private val CardEdge = Color(0xFFEADFCF)
private val PillFill = Color(0xFFFBF4EA)
private val PillFillBest = Color(0xFFFFF3C4)

/**
 * The kind ending shared by Paw Blocks and Paw Pop (docs/PRD.md stories 65 and 72; docs/DESIGN-SYSTEM.md,
 * "Good-game screen"): the game's own smiling [animal] (drawn by the game through the slot, so `ui/` imports no
 * game drawing), the [score], the [best] beside a small rosette, one big play-again button and one big home button.
 *
 * It fades in over 0.5s over [wash] (the game's dimmed backdrop; Blocks keeps the default cream). [isNewBest] (the
 * score beat the best from before this game; equal does not) adds a gently glowing best number, three small hops of
 * the animal and a few soft sparkles for about two seconds; nothing flashes more than three times a second, and with
 * animations turned off in the phone's settings the hop and twinkle are skipped. Never red, grey, shaking, sad, a
 * sound, or the words "game over".
 *
 * Both buttons share one [GoodGameGate]: they ignore touches for the first 0.6s with no visual change, and a
 * double-tap acts exactly once. The game's own corner home button is drawn above this screen and is never locked.
 * The score strip and paws are not shown here (a row of hollow paws would read as sad).
 *
 * Numbers are drawn at the design's sizes whatever the phone's font-size setting is.
 */
@Composable
fun GoodGameScreen(
    score: Int,
    best: Int,
    isNewBest: Boolean,
    animal: @Composable (Modifier) -> Unit,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    wash: Color = PawCream.copy(alpha = 0.84f),
) {
    val gate = remember { GoodGameGate() }
    val shownAtNanos = remember { System.nanoTime() }
    fun sinceShownMs(): Long = (System.nanoTime() - shownAtNanos) / 1_000_000L
    val context = LocalContext.current
    val animate = isNewBest && !animationsOff(context)

    // One clock for every moving part, from the moment the screen appears. It is started once and never restarted, whatever
    // `animate` does afterwards (a slow read of the saved best can turn it on late); it idles once the last effect is over.
    val clock = remember { mutableLongStateOf(0L) }
    val animateNow by rememberUpdatedState(animate)
    LaunchedEffect(Unit) {
        val c = GoodGameClock()
        while (true) {
            val t = c.sinceStartMs(withFrameNanos { it })
            val a = animateNow
            clock.longValue = c.shownMs(t, a)
            if (c.isDone(t, a)) {
                if (a) break
                snapshotFlow { animateNow }.first { it } // rest until (unless) a new best turns up late
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = (clock.longValue.toFloat() / GoodGameMotion.FADE_MS).coerceIn(0f, 1f) }
            // Nothing under this screen can be touched through it.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Box(Modifier.fillMaxSize().background(wash))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // On a very short window the block shrinks about its centre rather than scroll.
            val fit = min(1f, maxHeight.value / (BLOCK_HEIGHT + 32f))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .requiredSize(BLOCK_WIDTH.dp, BLOCK_HEIGHT.dp)
                    .graphicsLayer { scaleX = fit; scaleY = fit },
            ) {
                animal(
                    Modifier
                        .align(Alignment.TopCenter)
                        .size(ANIMAL_SIZE.dp)
                        .graphicsLayer { translationY = -GoodGameMotion.hopHeightDp(clock.longValue, animate) * density },
                )
                ScoreCard(score, best, isNewBest, animate, clock, Modifier.align(Alignment.TopCenter).offset(y = CARD_TOP.dp))
                if (animate) Sparkles(clock, Modifier.fillMaxSize())
                Row(
                    Modifier.align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP.dp),
                ) {
                    BigButton(PawLeaf, "Play again", onClick = { if (gate.accept(sinceShownMs())) onPlayAgain() }) {
                        PawPrintIcon(Modifier.size(40.dp), tint = Color.White)
                    }
                    BigButton(PawSky, "Back to home", onClick = { if (gate.accept(sinceShownMs())) onHome() }) {
                        HomeGlyphIcon(Modifier.size(40.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Play again and home: 88dp circles (far over the 72dp minimum). No ripple, dimming or spinner while they are still
 * asleep (the gate ignores touches for the first 0.6s), so nothing looks different when they wake.
 */
@Composable
private fun BigButton(background: Color, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(BUTTON_SIZE.dp)
            .background(background, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The white card: this game's score in big digits, a divider, and the best score with its rosette in a pill. */
@Composable
private fun ScoreCard(score: Int, best: Int, isNewBest: Boolean, animate: Boolean, clock: LongState, modifier: Modifier) {
    val shape = RoundedCornerShape(CARD_RADIUS.dp)
    Box(
        modifier
            .size(BLOCK_WIDTH.dp, CARD_HEIGHT.dp)
            .background(PawCardWhite, shape)
            .border(3.dp, if (isNewBest) PawSunshine else CardEdge, shape),
    ) {
        // No label and no icon: the number alone is this game.
        Box(Modifier.align(Alignment.TopCenter).offset(y = 10.dp).fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
            NumberText(score, 68.dp)
        }
        Box(Modifier.align(Alignment.TopStart).offset(x = 34.dp, y = 104.dp).size(232.dp, 2.dp).background(CardEdge))
        val pillWidth = (56 + 14 + best.toString().length * 27 + 40).dp
        val pillShape = RoundedCornerShape((PILL_HEIGHT / 2).dp)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = PILL_TOP.dp)
                .width(pillWidth)
                .height(PILL_HEIGHT.dp)
                .drawBehind { if (isNewBest) drawGlow(GoodGameMotion.glowFactor(clock.longValue, animate)) }
                .background(if (isNewBest) PillFillBest else PillFill, pillShape)
                .border(if (isNewBest) 2.5.dp else 2.dp, if (isNewBest) PawSunshine else CardEdge, pillShape),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RosetteIcon(Modifier.size(56.dp))
                NumberText(best, 44.dp)
            }
        }
    }
}

/** Three flat sunshine rounded rectangles 6, 12 and 18dp out from the pill (35%, 22%, 12%), scaled by [factor] (breathing 55% to 100%). */
private fun DrawScope.drawGlow(factor: Float) {
    val alphas = floatArrayOf(0.12f, 0.22f, 0.35f) // outermost first, so the nearer ones stack on top
    for ((i, out) in floatArrayOf(18f, 12f, 6f).withIndex()) {
        val o = out.dp.toPx()
        drawRoundRect(
            PawSunshine,
            Offset(-o, -o),
            Size(size.width + 2 * o, size.height + 2 * o),
            CornerRadius(size.height / 2 + o),
            alpha = alphas[i] * factor,
        )
    }
}

private val SparkleArt = Path().apply {
    moveTo(0f, -1f); quadraticBezierTo(0.18f, -0.18f, 1f, 0f); quadraticBezierTo(0.18f, 0.18f, 0f, 1f)
    quadraticBezierTo(-0.18f, 0.18f, -1f, 0f); quadraticBezierTo(-0.18f, -0.18f, 0f, -1f); close()
}

// x, y (dp inside the 300 x 440 block), radius (7 to 15), colour: around the animal and the card's sides, clear of the buttons.
private class Twinkle(val x: Float, val y: Float, val r: Float, val colour: Color)

private val Twinkles = listOf(
    Twinkle(58f, 30f, 11f, PawSunshine), Twinkle(246f, 40f, 15f, Color.White), Twinkle(12f, 156f, 9f, PawSunshine),
    Twinkle(290f, 140f, 12f, PawSunshine), Twinkle(14f, 298f, 7f, Color.White), Twinkle(284f, 306f, 10f, PawSunshine),
)

/** Six soft four-point sparkles twinkling on their own schedule (see [GoodGameMotion]); only drawn on a new best. */
@Composable
private fun Sparkles(clock: LongState, modifier: Modifier) {
    Canvas(modifier) {
        val d = density
        withTransform({ scale(d, d, Offset.Zero) }) {
            for ((i, t) in Twinkles.withIndex()) {
                val k = GoodGameMotion.twinkle(clock.longValue, i)
                if (k <= 0f) continue
                withTransform({ translate(t.x, t.y); scale(t.r * k, t.r * k, Offset.Zero) }) { drawPath(SparkleArt, t.colour, alpha = 0.95f) }
            }
        }
    }
}

/** True if the phone's animations are turned off (Settings, animation scale 0): then the hop, breathing and twinkle are skipped. */
internal fun animationsOff(context: Context): Boolean = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (e: Exception) {
    false
}
