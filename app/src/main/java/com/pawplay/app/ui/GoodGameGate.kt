package com.pawplay.app.ui

/**
 * The touch rule of the good-game screen (docs/PRD.md story 72), as plain logic so it can be tested without a phone.
 *
 * Both big buttons share one gate. It says no for the first [GUARD_MS] after the screen appears (a mashing child
 * cannot skip it), and yes **at most once** ever: a double-tap on play again starts exactly one game, and home
 * together with play again does only the first of them.
 */
class GoodGameGate(private val guardMs: Long = GUARD_MS) {
    private var fired = false

    /** True if a tap [elapsedMs] after the screen appeared should act. Once it has said yes it never says yes again. */
    fun accept(elapsedMs: Long): Boolean {
        if (fired || elapsedMs < guardMs) return false
        fired = true
        return true
    }

    companion object {
        const val GUARD_MS = 600L
    }
}
