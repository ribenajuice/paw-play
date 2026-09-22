package com.pawplay.app.games.pawmatch

/**
 * Pure game-state logic for Paw Match, kept free of Android/Compose so it's
 * covered by fast JVM unit tests (see PawMatchLogicTest). The Composable UI
 * in PawMatchGame.kt only ever calls into this — it never reimplements the
 * rules itself.
 */

enum class Critter { FOX, BEAR, BUNNY, OWL, CAT, FROG }

data class Card(
    val id: Int,
    val critter: Critter,
    val isRevealed: Boolean = false,
    val isMatched: Boolean = false,
)

// docs/DECISIONS.md — progressive difficulty, capped at 6 pairs.
const val STARTING_PAIRS = 3
const val MAX_PAIRS = 6

fun pairsForLevel(level: Int): Int =
    (STARTING_PAIRS + (level - 1)).coerceIn(STARTING_PAIRS, MAX_PAIRS)

data class RoundState(
    val level: Int,
    val cards: List<Card>,
    /** Ids of the 0, 1, or 2 cards currently face-up and unresolved. */
    val pendingReveal: List<Int> = emptyList(),
) {
    val isComplete: Boolean get() = cards.all { it.isMatched }
    val matchedPairs: Int get() = cards.count { it.isMatched } / 2
    val totalPairs: Int get() = cards.size / 2
}

private fun defaultShuffle(pairCount: Int): List<Critter> {
    val chosen = Critter.entries.shuffled().take(pairCount)
    return (chosen + chosen).shuffled()
}

fun newRound(level: Int, shuffledCritters: (Int) -> List<Critter> = ::defaultShuffle): RoundState {
    val pairCount = pairsForLevel(level)
    val critters = shuffledCritters(pairCount)
    val cards = critters.mapIndexed { index, critter -> Card(id = index, critter = critter) }
    return RoundState(level = level, cards = cards)
}

fun RoundState.nextLevel(shuffledCritters: (Int) -> List<Critter> = ::defaultShuffle): RoundState =
    newRound(level = level + 1, shuffledCritters = shuffledCritters)

/** Tapping an already-revealed/matched card, or a 3rd card mid-check, does nothing — never punished. */
fun RoundState.tapCard(cardId: Int): RoundState {
    val card = cards.find { it.id == cardId } ?: return this
    if (card.isRevealed || card.isMatched || pendingReveal.size >= 2) return this

    val updatedCards = cards.map { if (it.id == cardId) it.copy(isRevealed = true) else it }
    return copy(cards = updatedCards, pendingReveal = pendingReveal + cardId)
}

/** Resolves the two pending cards: match → locked face-up; mismatch → both flip back down. */
fun RoundState.resolvePending(): RoundState {
    if (pendingReveal.size != 2) return this
    val (firstId, secondId) = pendingReveal
    val first = cards.find { it.id == firstId }
    val second = cards.find { it.id == secondId }
    val isMatch = first != null && second != null && first.critter == second.critter

    val updatedCards = cards.map {
        when (it.id) {
            firstId, secondId -> it.copy(isMatched = isMatch, isRevealed = isMatch)
            else -> it
        }
    }
    return copy(cards = updatedCards, pendingReveal = emptyList())
}
