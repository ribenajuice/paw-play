package com.pawplay.app.games.pawblocks

import kotlin.random.Random

/** Something the session did on its own, when its time came. The screen animates it; the state is already updated. */
sealed interface SessionEvent {
    /** The board got one empty row (bottom) and one empty column (right); every placed cell kept its place. */
    data class Grew(val size: Int) : SessionEvent

    /** All three tray slots were dealt again, together. */
    object Refilled : SessionEvent

    /**
     * Nothing in the tray fit, so the fullest [rows] were cleared quietly ([removed] is every cell that went) and one
     * paw was spent: [pawsLeft] is how many are left now (the paw at that index fades).
     */
    class ClearedOut(val rows: List<Int>, val removed: List<ClearedCell>, val pawsLeft: Int) : SessionEvent

    /** Nothing in the tray fit and no paws are left: the game is over, exactly once. The screen shows the good-game screen. */
    data class GameEnded(val score: Int) : SessionEvent
}

/** Whether the game is being played, or has ended kindly (story 65). Once over, a session never plays again: play again is a new session. */
enum class BlocksPhase { PLAYING, GAME_OVER }

/** What a legal placement did. [critter] is the animal that peeks up (a rotation index 0-5), only when a line cleared. */
class Placed(
    val slot: Int,
    val shape: BlockShape,
    val row: Int,
    val col: Int,
    val rows: List<Int>,
    val cols: List<Int>,
    val removed: List<ClearedCell>,
    val critter: Int?,
    /** The points this placement added to the score (story 63), before the score's cap. */
    val points: Int = 0,
) {
    val cleared: Boolean get() = rows.isNotEmpty() || cols.isNotEmpty()
}

/**
 * One sitting of Paw Blocks: the board, the tray of three, how many lines the child has made, the score, the three
 * paws, and the quiet things the game does by itself (grow the board, deal three new blocks, clear space, and
 * finally end kindly). Session-only: nothing here is saved (only the best score is, by the screen, through `data/`);
 * a new session always starts on an empty 5x5 board at stage 1 with score 0 and 3 paws (stories 46 and 65).
 *
 * Time is passed in by the caller in milliseconds (the screen's frame clock, tests' virtual clock), so
 * the schedule below is deterministic and unit-tested without waiting:
 *
 *  - A block placed at time t that completes a line keeps the board "busy" until t + drop + 1000ms (the
 *    animation). The board is playable straight away; only the game's own follow-ups wait.
 *  - **Growth** (the ramp moved on and the board is smaller than its stage says) waits for the busy time and
 *    for any finger to lift, then takes 520ms.
 *  - **Refill** happens 500ms after the last of the three is placed and every animation has ended: all
 *    three slots at once, exactly once.
 *  - **Stuck** means none of the blocks left in the tray has a spot (story 45). 1000ms after his last move (and any
 *    animation), never while a block is being dragged and never while the board is still growing: with a paw left
 *    it clears the fullest rows and spends ONE paw (however many rows it takes); with no paw left the game ends
 *    ([BlocksPhase.GAME_OVER], event [SessionEvent.GameEnded] exactly once). A fresh tray is never stuck (story 44).
 *  - After the game has ended, [place], [tick] and [setHolding] do nothing.
 *
 * [bestBefore] is the saved best when this game began: [isNewBest] is `score > bestBefore` (equal is not new).
 * [startScore] and [startPaws] exist for tests.
 */
class BlocksSession(
    private val random: Random = Random.Default,
    startClears: Int = 0,
    startBoard: Board? = null,
    startTray: List<BlockShape?>? = null,
    /** The saved best score when this game began; the good-game screen's "new best" compares against it. */
    val bestBefore: Int = 0,
    startScore: Int = 0,
    startPaws: Int = BlocksRamp.START_PAWS,
) {
    var board: Board = startBoard ?: Board.empty(BlocksRamp.boardSizeFor(BlocksRamp.stageFor(startClears)))
        private set

    /** Lines the child has made himself. Clear-outs never add to it. */
    var clears: Int = startClears
        private set

    val stage: Int get() = BlocksRamp.stageFor(clears)

    /** The score (story 63): only ever goes up, never past [PawBlocksScoring.MAX_SCORE]. */
    var score: Int = startScore.coerceIn(0, PawBlocksScoring.MAX_SCORE)
        private set

    /** Free clear-outs left (the paws on screen). Only ever goes down. */
    var paws: Int = startPaws.coerceAtLeast(0)
        private set

    var phase: BlocksPhase = BlocksPhase.PLAYING
        private set

    /** When the game ended, on the caller's clock (0 until it does). */
    var gameOverAt: Long = 0L
        private set

    /** True when this game's score beats the best from before it began. Equal does not. */
    val isNewBest: Boolean get() = score > bestBefore

    private val trayMutable: Array<BlockShape?> = arrayOfNulls(BlocksRamp.TRAY_SIZE)

    /** The three slots; null where the block has been placed. A copy, made on each call: use [slot] in per-frame code. */
    val tray: List<BlockShape?> get() = trayMutable.toList()

    /** The block in slot [i] (0-2), or null if it has been placed. No allocation. */
    fun slot(i: Int): BlockShape? = trayMutable.getOrNull(i)

    /** The stage the tray was dealt at: the tray's cell size follows it, so it does not jump mid-tray. */
    var trayStage: Int = stage
        private set

    /** Counts refills, so a screen can tell "a new tray" from "same tray". */
    var trayDeals: Int = 0
        private set

    private var lastTrio: List<String>? = null
    private var busyUntil = Long.MIN_VALUE
    private var lastMoveAt = Long.MIN_VALUE
    private var trayEmptiedAt = Long.MIN_VALUE
    private var holding = false

    init {
        val first = startTray ?: TrayDealer.deal(board, stage, null, random).also { trayDeals = 1 }
        require(first.size == BlocksRamp.TRAY_SIZE) { "a tray has ${BlocksRamp.TRAY_SIZE} slots" }
        first.forEachIndexed { i, s -> trayMutable[i] = s }
        lastTrio = first.filterNotNull().map { it.id }.takeIf { it.size == BlocksRamp.TRAY_SIZE }
    }

    val isTrayEmpty: Boolean get() = trayMutable.all { it == null }

    private fun remaining(): List<BlockShape> = trayMutable.filterNotNull()

    /** True while the tray holds blocks and none of them has a legal spot (a clear-out is on its way). */
    val isStuck: Boolean
        get() {
            var any = false
            for (shape in trayMutable) {
                if (shape == null) continue
                any = true
                if (board.hasSpot(shape)) return false
            }
            return any
        }

    private val growthPending: Boolean get() = board.size < BlocksRamp.boardSizeFor(stage)

    /** True while the session still has something it will do by itself: refill, grow, clear out or end. False once the game is over. */
    val hasPending: Boolean get() = phase == BlocksPhase.PLAYING && (isTrayEmpty || growthPending || isStuck)

    fun canPlace(slot: Int, row: Int, col: Int): Boolean =
        phase == BlocksPhase.PLAYING && (trayMutable.getOrNull(slot)?.let { board.canPlace(it, row, col) } ?: false)

    /** A finger has picked a block up, or let it go. A clear-out, growth or the ending never starts under a held block. */
    fun setHolding(value: Boolean, nowMs: Long) {
        if (phase != BlocksPhase.PLAYING) return
        if (holding && !value) lastMoveAt = maxOf(lastMoveAt, nowMs) // a full second of peace after he lets go
        holding = value
    }

    /**
     * Places the block in [slot] with its top-left at [row], [col] if that is legal, else does nothing and
     * returns null. A block can only be placed once: its slot is empty afterwards.
     */
    fun place(slot: Int, row: Int, col: Int, nowMs: Long): Placed? {
        if (phase != BlocksPhase.PLAYING) return null
        val shape = trayMutable.getOrNull(slot) ?: return null
        if (!board.canPlace(shape, row, col)) return null
        val result = board.placeAndClear(shape, row, col)
        board = result.board
        trayMutable[slot] = null
        lastMoveAt = nowMs
        var critter: Int? = null
        if (result.cleared) {
            clears++ // one clear, however many lines
            critter = BlocksRamp.critterIndex(clears)
            busyUntil = maxOf(busyUntil, nowMs + BlocksTiming.DROP_MS + BlocksTiming.LINE_CLEAR_MS)
        }
        if (isTrayEmpty) trayEmptiedAt = nowMs
        val points = PawBlocksScoring.placementPoints(result.rows.size + result.cols.size) // rows and columns each count as a line
        score = PawBlocksScoring.add(score, points)
        return Placed(slot, shape, row, col, result.rows, result.cols, result.removed, critter, points)
    }

    /**
     * Lets the session do whatever is due at [nowMs]: grow, refill, clear out or end, in that order. Returns what
     * it did (usually nothing). Cheap to call every frame.
     */
    fun tick(nowMs: Long): List<SessionEvent> {
        if (!hasPending) return emptyList()
        val events = ArrayList<SessionEvent>(2)

        // Growth: once the line-clear animation is over and no finger holds a block.
        if (growthPending && !holding && nowMs >= busyUntil) {
            board = board.grown()
            busyUntil = maxOf(busyUntil, nowMs) + BlocksTiming.GROWTH_MS
            events += SessionEvent.Grew(board.size)
        }

        // Refill: 500ms after the last block and every animation, all three at once, exactly once.
        if (isTrayEmpty && !growthPending && nowMs >= maxOf(trayEmptiedAt, busyUntil) + BlocksTiming.REFILL_DELAY_MS) {
            val trio = TrayDealer.deal(board, stage, lastTrio, random)
            trio.forEachIndexed { i, s -> trayMutable[i] = s }
            lastTrio = trio.map { it.id }
            trayStage = stage
            trayDeals++
            events += SessionEvent.Refilled
        }

        // Stuck (nothing left in the tray fits): a second after his last move, with the board quiet and no block held.
        // A paw left: clear the fullest rows and spend one paw. No paw left: the game ends, once.
        if (!holding && !growthPending && nowMs >= maxOf(lastMoveAt, busyUntil) + BlocksTiming.CLEAR_OUT_DELAY_MS) {
            if (paws > 0) {
                val out = GentleClearOut.clear(board, remaining())
                if (out != null) {
                    board = out.board // clears never count toward the ramp and score nothing
                    paws--
                    busyUntil = nowMs + BlocksTiming.CLEAR_OUT_MS
                    events += SessionEvent.ClearedOut(out.rows, out.removed, paws)
                }
            } else if (isStuck) {
                phase = BlocksPhase.GAME_OVER
                gameOverAt = nowMs
                events += SessionEvent.GameEnded(score)
            }
        }
        return events
    }
}
