package com.pawplay.app.games.pawmatch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawplay.app.games.MiniGame
import com.pawplay.app.ui.AdaptiveSquareGrid
import com.pawplay.app.ui.theme.PawCoral
import com.pawplay.app.ui.theme.PawLeaf
import com.pawplay.app.ui.theme.PawSky
import com.pawplay.app.ui.theme.PawSunshine
import kotlinx.coroutines.delay

private const val MISMATCH_DELAY_MS = 700L
private val CARD_GAP = 16.dp
private val MAX_CARD_SIZE = 170.dp
private val MIN_CARD_SIZE = 48.dp // touch-target floor, docs/DESIGN-SYSTEM.md

object PawMatchGame : MiniGame {
    override val id = "paw-match"
    override val icon: @Composable (Dp) -> Unit = { size -> PawMatchTileIcon(size) }
    override val content: @Composable (onExit: () -> Unit) -> Unit = { onExit -> PawMatchScreen(onExit) }
}

@Composable
private fun PawMatchTileIcon(size: Dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        FoxIcon(modifier = Modifier.size(size * 0.4f))
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.155f)
                .clip(RoundedCornerShape(size * 0.055f))
                .background(PawSunshine),
            contentAlignment = Alignment.Center,
        ) {
            PawPrintIcon(modifier = Modifier.size(size * 0.09f), tint = PawCoral)
        }
    }
}

/**
 * Every round is fresh in-memory state — entering from the home screen
 * always starts at level 1 (3 pairs); see docs/DECISIONS.md on why
 * difficulty is session-scoped, not persisted.
 */
@Composable
private fun PawMatchScreen(onExit: () -> Unit) {
    var round by remember { mutableStateOf(newRound(level = 1)) }

    LaunchedEffect(round.pendingReveal) {
        if (round.pendingReveal.size == 2) {
            val (firstId, secondId) = round.pendingReveal
            val first = round.cards.first { it.id == firstId }
            val second = round.cards.first { it.id == secondId }
            // A match celebrates immediately; a mismatch gets a short pause
            // so the child actually sees both faces before they flip back —
            // never an instant, confusing snap-back (docs/PRD.md AC5).
            if (first.critter != second.critter) delay(MISMATCH_DELAY_MS)
            round = round.resolvePending()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExitButton(onClick = onExit, modifier = Modifier.padding(20.dp))
            AdaptiveSquareGrid(
                itemCount = round.cards.size,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                gap = CARD_GAP,
                maxItemSize = MAX_CARD_SIZE,
                minItemSize = MIN_CARD_SIZE,
            ) { index, size ->
                val card = round.cards[index]
                GameCard(
                    card = card,
                    size = size,
                    onClick = { round = round.tapCard(card.id) },
                )
            }
        }

        if (round.isComplete) {
            WinOverlay(
                onPlayAgain = { round = round.nextLevel() },
                onHome = onExit,
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun GameCard(card: Card, size: Dp, onClick: () -> Unit) {
    val borderColor = when {
        card.isMatched -> PawLeaf
        card.isRevealed -> PawSky
        else -> PawSunshine
    }
    // Corner radius and border scale down with the card too, so a shrunk
    // card at the 6-pair cap doesn't look like a tiny version of a
    // differently-proportioned shape.
    val cornerRadius = size * 0.16f
    val borderWidth = (size.value * 0.024f).dp.coerceAtLeast(2.dp)

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(cornerRadius))
            .clickable(enabled = !card.isRevealed && !card.isMatched, onClick = onClick)
            .semantics {
                contentDescription = if (card.isRevealed || card.isMatched) {
                    card.critter.name
                } else {
                    "face-down card"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(targetState = card.isRevealed || card.isMatched, label = "card-face") { faceUp ->
            if (faceUp) {
                CritterIcon(critter = card.critter, modifier = Modifier.size(size * 0.55f))
            } else {
                PawPrintIcon(modifier = Modifier.size(size * 0.42f), tint = PawCoral.copy(alpha = 0.35f))
            }
        }
    }
}

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
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            StarBadgeIcon(modifier = Modifier.size(96.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BigIconButton(
                    onClick = onPlayAgain,
                    background = PawLeaf,
                    description = "Play again, a bit harder",
                ) {
                    PawPrintIcon(modifier = Modifier.size(40.dp), tint = Color.White)
                }
                BigIconButton(
                    onClick = onHome,
                    background = PawSky,
                    description = "Back to home",
                ) {
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
