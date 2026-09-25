package com.pawplay.app.data

import com.pawplay.app.games.pawblocks.BlocksSession
import com.pawplay.app.games.pawpop.PopSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * A saved best that is read slowly (longer than the games' 500ms cap) must never let a small score claim a new best
 * next to a higher saved one. The games start playing without it, then call `learnBest(best.storedAtLoad)` once the
 * read ends, exactly as PawBlocksScreen and PawPopScreen do.
 */
class SlowReadNewBestTest {
    /** A store whose read waits until [release] is called, like a very slow disk. */
    private class SlowStore(private val saved: Int?) : IntStore {
        val release = CountDownLatch(1)
        override fun read(key: String): Int? {
            release.await(5, TimeUnit.SECONDS)
            return saved
        }
        override fun write(key: String, value: Int) = Unit
    }

    /** Starts the read on another thread and returns it; the caller "waits the cap", plays, then releases the store. */
    private fun slowLoad(best: BestScore): Thread = Thread { best.load() }.also { it.start() }

    @Test
    fun `blocks - a small score during a slow read does not claim a new best, and never does once a higher saved best arrives`() {
        val store = SlowStore(saved = 50)
        val best = BestScore("best.paw-blocks", store)
        val reader = slowLoad(best)
        assertFalse("the read has not finished", best.isLoaded)
        val session = BlocksSession(Random(1), bestBefore = best.best, bestKnown = best.isLoaded, startScore = 7)
        assertFalse("best is not known yet: no claim", session.isNewBest)
        best.submit(7) // the game saves in memory only while unread
        store.release.countDown(); reader.join()
        assertTrue(best.isLoaded)
        session.learnBest(best.storedAtLoad)
        assertEquals(50, session.bestBefore)
        assertFalse("7 does not beat a saved 50", session.isNewBest)
        assertEquals(50, best.best)
    }

    @Test
    fun `blocks - a score that beats the saved best after a slow read is a new best, an equal one is not`() {
        for ((score, expected) in listOf(51 to true, 50 to false, 49 to false)) {
            val store = SlowStore(saved = 50)
            val best = BestScore("k", store)
            val reader = slowLoad(best)
            val session = BlocksSession(Random(1), bestBefore = best.best, bestKnown = best.isLoaded, startScore = score)
            store.release.countDown(); reader.join()
            session.learnBest(best.storedAtLoad)
            assertEquals("score $score vs saved 50", expected, session.isNewBest)
        }
    }

    @Test
    fun `blocks - first ever game on a slow read with nothing saved still gets its new best`() {
        val store = SlowStore(saved = null)
        val best = BestScore("k", store)
        val reader = slowLoad(best)
        val session = BlocksSession(Random(1), bestBefore = best.best, bestKnown = best.isLoaded, startScore = 3)
        assertFalse(session.isNewBest)
        store.release.countDown(); reader.join()
        session.learnBest(best.storedAtLoad)
        assertTrue(session.isNewBest)
        assertFalse(BlocksSession(Random(1), bestBefore = 0, startScore = 0).isNewBest)
    }

    @Test
    fun `pop - a small score during a slow read never claims a new best against a higher saved one`() {
        val store = SlowStore(saved = 40)
        val best = BestScore("best.paw-pop", store)
        val reader = slowLoad(best)
        val session = PopSession(Random(1), bestBefore = best.best, bestKnown = best.isLoaded, startScore = 9)
        assertFalse(session.isNewBest)
        store.release.countDown(); reader.join()
        session.learnBest(best.storedAtLoad)
        assertFalse(session.isNewBest)
        val beat = PopSession(Random(1), bestBefore = 0, bestKnown = false, startScore = 41).also { it.learnBest(40) }
        assertTrue(beat.isNewBest)
        val equal = PopSession(Random(1), bestBefore = 0, bestKnown = false, startScore = 40).also { it.learnBest(40) }
        assertFalse(equal.isNewBest)
    }

    @Test
    fun `a session that started after the read finished is unchanged, and learning a lower value never lowers the best`() {
        val store = SlowStore(saved = 20).also { it.release.countDown() }
        val best = BestScore("k", store)
        best.load()
        val session = BlocksSession(Random(1), bestBefore = best.best, bestKnown = best.isLoaded, startScore = 21)
        assertTrue(session.isNewBest)
        session.learnBest(5)
        assertEquals(20, session.bestBefore)
        assertTrue(session.isNewBest)
    }
}
