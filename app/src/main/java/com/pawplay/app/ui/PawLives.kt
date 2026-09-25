package com.pawplay.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawplay.app.ui.theme.InkColor
import com.pawplay.app.ui.theme.PawCoral

// The paws (the rescue picture) and the score number, shared by Paw Blocks and Paw Pop so the two games show
// exactly the same thing (docs/DESIGN-SYSTEM.md, "Score, paws and the good-game screen"). No text, no button.

/** How long one paw takes to fade into its outline. */
const val PAW_FADE_MS = 600L

/** Size of one paw and the gap between paws, in dp. */
val PawLifeSize: Dp = 32.dp
val PawLifeGap: Dp = 8.dp

/** Three paws side by side: [PawLifeCount] * 32dp plus two 8dp gaps. */
const val PawLifeCount = 3

// One paw on Paw Match's 80-unit grid (ellipses as cx, cy, rx, ry).
private val PawEllipses = arrayOf(
    floatArrayOf(40f, 52f, 20f, 16f), // pad
    floatArrayOf(18f, 30f, 8f, 10f),
    floatArrayOf(34f, 18f, 8f, 10f),
    floatArrayOf(52f, 18f, 8f, 10f),
    floatArrayOf(64f, 32f, 8f, 10f),
)

private val FullEdge = Stroke(width = 4.5f, join = StrokeJoin.Round)
private val UsedEdge = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f)))
private val UsedFill = Color.White.copy(alpha = 0.4f)
private val UsedInk = InkColor.copy(alpha = 0.55f)
private val Shine = Color.White.copy(alpha = 0.7f)

/**
 * Draws one paw with its top-left at the current origin on an 80-unit grid. [full] and [used] are the opacity of
 * the two looks (they cross-fade): the **full** paw is coral with an ink outline and a shine; the **used** paw is
 * the same silhouette, hollow, dotted and shineless, so the two differ by shape and pattern, not only by colour.
 */
internal fun DrawScope.drawPaw(full: Float, used: Float) {
    if (used > 0f) {
        for (e in PawEllipses) {
            val tl = Offset(e[0] - e[2], e[1] - e[3])
            val sz = Size(2 * e[2], 2 * e[3])
            drawOval(UsedFill, tl, sz, alpha = used)
            drawOval(UsedInk, tl, sz, alpha = used, style = UsedEdge)
        }
    }
    if (full > 0f) {
        for (e in PawEllipses) {
            val tl = Offset(e[0] - e[2], e[1] - e[3])
            val sz = Size(2 * e[2], 2 * e[3])
            drawOval(PawCoral, tl, sz, alpha = full)
            drawOval(InkColor, tl, sz, alpha = full, style = FullEdge)
        }
        rotate(-25f, Offset(33f, 46f)) {
            drawOval(Shine, Offset(33f - 8f, 46f - 3.6f), Size(16f, 7.2f), alpha = full)
        }
    }
}

/**
 * The three paws. [paws] is how many are left **now**; a paw that was just lost is slot [fadingIndex] (the rightmost
 * full one goes first, so it is [paws] itself), which fades from full to its outline over [PAW_FADE_MS] starting at
 * [fadeStartMs] on the same clock as [nowMs]. Before the start it still looks full, after the end it is an outline;
 * a used paw never returns. Not tappable.
 */
@Composable
fun PawLivesRow(
    paws: Int,
    fadingIndex: Int?,
    fadeStartMs: Long,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    val width = PawLifeSize * PawLifeCount + PawLifeGap * (PawLifeCount - 1)
    Canvas(
        modifier
            .size(width, PawLifeSize)
            .semantics { contentDescription = "Paws left: $paws" },
    ) {
        val unit = PawLifeSize.toPx() / 80f
        val step = (PawLifeSize + PawLifeGap).toPx()
        for (i in 0 until PawLifeCount) {
            val used = when {
                i == fadingIndex -> ((nowMs - fadeStartMs).toFloat() / PAW_FADE_MS).coerceIn(0f, 1f)
                i < paws -> 0f
                else -> 1f
            }
            withTransform({ translate(i * step, 0f); scale(unit, unit, Offset.Zero) }) { drawPaw(full = 1f - used, used = used) }
        }
    }
}

/**
 * The score: a plain number, 32dp tall figures in ink, weight 800, equal-width digits, no comma, label or icon. It
 * counts up to a new value in under 0.4s with no flash, bounce, sound or colour change. Not a button. Six digits
 * (the cap) drop to 26dp so they still fit beside the paws.
 */
@Composable
fun ScoreNumber(value: Int, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(value, tween(durationMillis = COUNT_UP_MS, easing = LinearEasing), label = "score")
    NumberText(shown, if (value >= 100_000) 26.dp else 32.dp, modifier, TextAlign.End)
}

/** Under 0.4s, per the design. */
const val COUNT_UP_MS = 300

/** Plain digits, figures of [height] dp whatever the phone's font-size setting is (the layout is drawn for these sizes). */
@Composable
internal fun NumberText(value: Int, height: Dp, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    val sp = with(LocalDensity.current) { height.toSp() }
    Text(
        text = value.toString(),
        modifier = modifier,
        color = InkColor,
        maxLines = 1,
        softWrap = false,
        textAlign = align,
        style = TextStyle(fontSize = sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
    )
}
