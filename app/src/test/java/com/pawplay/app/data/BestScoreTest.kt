package com.pawplay.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The saved best score's rules (docs/PRD.md stories 70-71), against a fake store that can be empty, hold junk, throw or fill up. */
class BestScoreTest {

    /** A store that can misbehave on purpose. */
    private class FakeStore(
        var stored: MutableMap<String, Any> = HashMap(),
        var readFails: Boolean = false,
        var writeFails: Boolean = false,
    ) : IntStore {
        var writes = 0
        var reads = 0

        override fun read(key: String): Int? {
            reads++
            if (readFails) throw IOException("unreadable")
            return when (val v = stored[key]) {
                null -> null
                is Int -> v
                else -> throw ClassCastException("${v.javaClass.simpleName} cannot be cast to Integer") // what getInt does for a non-integer
            }
        }

        override fun write(key: String, value: Int) {
            writes++
            if (writeFails) throw IOException("storage full")
            stored[key] = value
        }
    }

    private val blocks = "best.paw-blocks"
    private val pop = "best.paw-pop"

    // ------------------------------------------------------------------ first run and reading

    @Test
    fun `first ever launch has nothing stored, so the best is 0 and a first score above 0 is new`() {
        val store = FakeStore()
        val b = BestScore(blocks, store)
        assertEquals(0, b.best) // before load
        b.load()
        assertEquals(0, b.best)
        assertFalse("0 does not beat 0", b.submit(0))
        assertEquals(0, store.writes)
        assertTrue(b.submit(3))
        assertEquals(3, b.best)
        assertEquals(3, store.stored[blocks])
    }

    @Test
    fun `a stored best is read back, including the two ends of the range`() {
        for (v in listOf(0, 1, 250, 999_999)) {
            val b = BestScore(blocks, FakeStore(stored = hashMapOf(blocks to v)))
            b.load()
            assertEquals(v, b.best)
        }
    }

    @Test
    fun `a stored value that is negative, too big or not a whole number reads as 0 with no error`() {
        for (junk in listOf<Any>(-1, Int.MIN_VALUE, 1_000_000, Int.MAX_VALUE, "12", 12L, 3.5f, true)) {
            val store = FakeStore(stored = hashMapOf(blocks to junk))
            val b = BestScore(blocks, store)
            b.load()
            assertEquals("stored $junk", 0, b.best)
            assertTrue("a real score still counts afterwards", b.submit(1))
            assertEquals(1, b.best)
        }
    }

    @Test
    fun `an unreadable store reads as 0 and play carries on, with the score kept in memory only`() {
        val store = FakeStore(readFails = true)
        val b = BestScore(blocks, store)
        b.load()
        assertEquals(0, b.best)
        assertFalse("a failed read is not a read", b.isLoaded)
        assertTrue(b.submit(40))
        assertEquals(40, b.best)
        assertEquals("nothing is written over a store that could not be read", 0, store.writes)
        assertNull(store.stored[blocks])
    }

    @Test
    fun `a failed read never lets a low score overwrite a real saved best, and the next successful read keeps the higher one`() {
        val store = FakeStore(stored = hashMapOf(blocks to 500), readFails = true)
        val b = BestScore(blocks, store)
        b.load() // fails: the 500 is there but unseen
        assertTrue(b.submit(7))
        assertEquals(7, b.best)
        assertEquals(500, store.stored[blocks])
        assertEquals(0, store.writes)
        store.readFails = false
        b.load() // the game is opened again and the read works
        assertTrue(b.isLoaded)
        assertEquals("the real saved best comes back", 500, b.best)
        assertEquals(500, b.storedAtLoad)
        assertEquals("and was never touched", 500, store.stored[blocks])
        assertEquals(0, store.writes)
    }

    @Test
    fun `a best made while the store could not be read is saved by the first read that works, if it beats what is there`() {
        val store = FakeStore(stored = hashMapOf(blocks to 30), readFails = true)
        val b = BestScore(blocks, store)
        b.load()
        b.submit(90)
        assertEquals(30, store.stored[blocks])
        store.readFails = false
        b.load()
        assertEquals(90, b.best)
        assertEquals(90, store.stored[blocks])
    }

    @Test
    fun `a read that fails part way through the app session does not disturb a best already loaded`() {
        val store = FakeStore(stored = hashMapOf(blocks to 40))
        val b = BestScore(blocks, store)
        b.load()
        assertTrue(b.isLoaded)
        store.readFails = true
        b.load()
        assertTrue("still counts as loaded from before", b.isLoaded)
        assertEquals(40, b.best)
        assertEquals(40, b.storedAtLoad)
        assertTrue(b.submit(41))
        assertEquals(41, store.stored[blocks]) // the disk was seen earlier this session, so this is safe to write
    }

    @Test
    fun `the disk never goes backwards when a score arrives while the read's own write is still in flight`() {
        val writes = java.util.Collections.synchronizedList(ArrayList<Int>())
        val disk = java.util.concurrent.atomic.AtomicInteger(0)
        val firstWriteStarted = java.util.concurrent.CountDownLatch(1)
        val letFirstWriteFinish = java.util.concurrent.CountDownLatch(1)
        val store = object : IntStore {
            override fun read(key: String): Int? = null
            override fun write(key: String, value: Int) {
                if (writes.isEmpty()) { writes += value; firstWriteStarted.countDown(); letFirstWriteFinish.await(5, java.util.concurrent.TimeUnit.SECONDS) }
                else writes += value
                disk.set(value)
            }
        }
        val b = BestScore(blocks, store)
        b.submit(50) // before the read: memory only
        val loader = Thread { b.load() }.also { it.start() } // will write 50, slowly
        assertTrue(firstWriteStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val submitter = Thread { b.submit(60) }.also { it.start() } // a newer best arrives meanwhile
        Thread.sleep(150)
        letFirstWriteFinish.countDown()
        loader.join(5000); submitter.join(5000)
        assertEquals("the disk ends on the newer best", 60, disk.get())
        assertEquals("and only ever rose", writes.sorted(), writes.toList())
    }

    // ------------------------------------------------------------------ submitting

    @Test
    fun `only a score that beats the best is new and written, equal is not`() {
        val store = FakeStore()
        val b = BestScore(blocks, store)
        b.load()
        assertTrue(b.submit(10))
        assertFalse(b.submit(10))
        assertFalse(b.submit(9))
        assertTrue(b.submit(11))
        assertEquals(11, b.best)
        assertEquals("only the two real improvements were written", 2, store.writes)
    }

    @Test
    fun `scores are clamped to 999999 and a negative score is nothing`() {
        val store = FakeStore()
        val b = BestScore(blocks, store)
        b.load()
        assertFalse(b.submit(-5))
        assertFalse(b.submit(Int.MIN_VALUE))
        assertEquals(0, b.best)
        assertTrue(b.submit(5_000_000))
        assertEquals(999_999, b.best)
        assertEquals(999_999, store.stored[blocks])
        assertFalse("the cap cannot be beaten", b.submit(Int.MAX_VALUE))
        assertEquals(1, store.writes)
    }

    @Test
    fun `a write that fails is swallowed and the best stays in memory for the rest of the session`() {
        val store = FakeStore(writeFails = true)
        val b = BestScore(blocks, store)
        b.load()
        assertTrue("still reports a new best", b.submit(70))
        assertEquals(70, b.best)
        assertFalse(b.submit(60))
        assertTrue(b.submit(80))
        assertEquals(80, b.best)
        assertNull("nothing reached the disk", store.stored[blocks])
        // Space frees up: the next improvement is saved.
        store.writeFails = false
        assertTrue(b.submit(90))
        assertEquals(90, store.stored[blocks])
    }

    @Test
    fun `the in-memory best survives leaving and re-entering a game even if the disk never accepted it`() {
        val store = FakeStore(writeFails = true)
        val keeper = BestScore(blocks, store) // BestScores keeps this one object for the whole app session
        keeper.load()
        keeper.submit(120)
        keeper.load() // the game opens again and reads the (empty) disk
        assertEquals(120, keeper.best)
        keeper.load()
        assertEquals(120, keeper.best)
    }

    @Test
    fun `a score that arrives before the read finishes is kept, never lowers a higher saved best, and is saved once the read is done`() {
        val store = FakeStore(stored = hashMapOf(blocks to 30))
        val b = BestScore(blocks, store)
        assertTrue(b.submit(50)) // a score arrives before the read has finished
        assertEquals("nothing is written before the read has seen what is saved", 0, store.writes)
        assertEquals(50, b.best)
        b.load()
        assertEquals(50, b.best)
        assertEquals("the higher one is saved now", 50, store.stored[blocks])

        val store2 = FakeStore(stored = hashMapOf(blocks to 200))
        val b2 = BestScore(blocks, store2)
        b2.submit(50)
        b2.load()
        assertEquals("the saved one was higher", 200, b2.best)
        assertEquals("and it was never overwritten by the lower score", 200, store2.stored[blocks])
        assertEquals(0, store2.writes)
    }

    @Test
    fun `a failed read followed by an early score does not save it until a read works`() {
        val store = FakeStore(readFails = true)
        val b = BestScore(blocks, store)
        b.submit(9)
        b.load()
        assertEquals(9, b.best)
        assertNull(store.stored[blocks])
        store.readFails = false
        b.load()
        assertEquals(9, b.best)
        assertEquals(9, store.stored[blocks])
    }

    @Test
    fun `opening a game again retries a write that failed before`() {
        val store = FakeStore(writeFails = true)
        val b = BestScore(blocks, store)
        b.load()
        b.submit(70)
        assertNull(store.stored[blocks])
        store.writeFails = false
        b.load() // the game is opened again: the disk now has room and the best in memory is higher than what it holds
        assertEquals(70, store.stored[blocks])
        assertEquals(70, b.best)
    }

    // ------------------------------------------------------------------ two games, two numbers

    @Test
    fun `each game has its own number and a failure for one never touches the other`() {
        val store = FakeStore(stored = hashMapOf(blocks to 100, pop to 7))
        val b = BestScore(blocks, store)
        val p = BestScore(pop, store)
        b.load(); p.load()
        assertEquals(100, b.best)
        assertEquals(7, p.best)
        assertTrue(p.submit(8))
        assertEquals(100, b.best)
        assertEquals(100, store.stored[blocks])
        assertEquals(8, store.stored[pop])

        // One key holds junk: only that game reads 0.
        val mixed = FakeStore(stored = hashMapOf(blocks to "junk", pop to 9))
        val b2 = BestScore(blocks, mixed)
        val p2 = BestScore(pop, mixed)
        b2.load(); p2.load()
        assertEquals(0, b2.best)
        assertEquals(9, p2.best)
    }

    @Test
    fun `only the one integer for the game's own key is ever written`() {
        val store = FakeStore()
        val b = BestScore(blocks, store)
        b.load()
        b.submit(12); b.submit(30)
        assertEquals(setOf(blocks), store.stored.keys)
        assertTrue(store.stored.values.all { it is Int })
    }

    @Test
    fun `the cap is six digits`() {
        assertEquals(999_999, BestScore.MAX)
    }
}
