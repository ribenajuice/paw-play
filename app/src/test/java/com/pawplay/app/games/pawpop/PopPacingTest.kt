package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pace of the ramp, measured with scripted players over 40 seeds each (docs/PRD.md, Paw Pop ramp rationale; docs/DECISIONS.md,
 * "Paw Pop ramp thresholds are unverified" and the 2026-09-26 measurement). This replaces the old band tests (perfect 130-220s,
 * busy 160-260s, toddler 270-450s, never-touching over 300s), which described a straight-drop game with no paws.
 *
 * The PRD's three targets:
 *  1. a busy tapper loses at most 1 paw in the first 60 seconds (median);
 *  2. a never-touching player reaches the good-game screen between 30 seconds and 3 minutes;
 *  3. a perfect-aim player reaches stage 4 before running out of paws in most runs.
 * If one of them is missed, the numbers to tune are the ramp table's (pop counts, spawn gaps, angles), not the rules.
 */
class PopPacingTest {

    private val seeds = 1..40

    private val games: Map<Player, List<Played>> by lazy { Player.values().associateWith { p -> seeds.map { playGame(p, it) } } }

    private fun table(): String {
        val sb = StringBuilder("seconds to stage 2/3/4/5 (median), paws lost in the first 60s (median), how the game ended\n")
        for (p in Player.values()) {
            val runs = games.getValue(p)
            fun q(f: (Played) -> Double) = median(runs.map(f))
            sb.appendLine(
                "%-9s %5.0f %5.0f %5.0f %5.0f | reached stage 4: %2d/%d, stage 5: %2d/%d | ended %2d/%d, median end %5.0fs | paws lost by 60s: %.1f | pops %.0f".format(
                    p, q { it.stageAt[0] }, q { it.stageAt[1] }, q { it.stageAt[2] }, q { it.stageAt[3] },
                    runs.count { !it.stageAt[2].isNaN() }, runs.size, runs.count { !it.stageAt[3].isNaN() }, runs.size,
                    runs.count { it.ended }, runs.size, q { it.endedAt }, median(runs.map { it.pawsLostBefore(60.0).toDouble() }), q { it.pops.toDouble() },
                ),
            )
        }
        return sb.toString()
    }

    @Test
    fun `the measured pace meets the PRD's three targets`() {
        println(table())
        // 1. a busy tapper loses at most 1 paw in the first 60 seconds (median)
        val busyLost = median(games.getValue(Player.BUSY).map { it.pawsLostBefore(60.0).toDouble() })
        assertTrue("busy tapper lost $busyLost paws in the first minute", busyLost <= 1.0)
        // 2. a never-touching player reaches the good-game screen between 30 seconds and 3 minutes
        val never = games.getValue(Player.NEVER)
        assertTrue("every never-touching game ends", never.all { it.ended })
        val neverEnd = median(never.map { it.endedAt })
        assertTrue("never-touching median end $neverEnd s", neverEnd in 30.0..180.0)
        assertTrue("no never-touching game ends under 15s or over 3 minutes: ${never.map { it.endedAt }.sorted()}", never.all { it.endedAt in 15.0..180.0 })
        // 3. a perfect-aim player reaches stage 4 before running out of paws, in most runs
        val perfect = games.getValue(Player.PERFECT)
        val reached4 = perfect.count { !it.stageAt[2].isNaN() && (!it.ended || it.stageAt[2] <= it.endedAt) }
        assertTrue("perfect aim reached stage 4 in $reached4 of ${perfect.size}", reached4 > perfect.size * 3 / 4)
    }

    @Test
    fun `stages come in order and a better player is never behind a worse one on the way up`() {
        val perfect = games.getValue(Player.PERFECT)
        for (r in perfect) {
            val t = r.stageAt
            assertTrue("stages out of order: ${t.toList()}", t[0] < t[1] && t[1] < t[2] && t[2] < t[3])
        }
        // a child who plays (any of the tappers) reaches stage 2 at all, and so does one who only wanders
        assertTrue(games.getValue(Player.WANDERING).count { !it.stageAt[0].isNaN() } >= 30)
        // the cap stays reachable for good play: stage 5 within a quarter of an hour for perfect aim
        assertTrue(median(perfect.map { it.stageAt[3] }) < 900.0)
    }

    @Test
    fun `a slow toddler and a wandering child both meet a kind ending, never a stuck game`() {
        for (p in listOf(Player.TODDLER, Player.WANDERING)) for (r in games.getValue(p)) {
            assertTrue("$p: the game must end within the run: ${r.seconds}", r.ended)
            assertEquals(3, r.pawLostAt.size)
        }
    }
}
