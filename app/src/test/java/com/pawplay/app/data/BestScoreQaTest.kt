package com.pawplay.app.data

import com.pawplay.app.ui.GoodGameGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * QA's own checks of the saved best score (PRD stories 70-71) and the good-game screen's touch gate (story 72), through
 * the public classes only: equal is not new, the first score above zero is, the six-digit clamp, a store that throws on
 * read or write, two games on one store, and a slow read that must never overwrite a higher best.
 */
class BestScoreQaTest {
    private class FakeStore(
        val data: MutableMap<String, Int> = HashMap(),
        var failReads: Boolean = false,
        var failWrites: Boolean = false,
        var failWriteKey: String? = null,
    ) : IntStore {
        val writes = ArrayList<Pair<String, Int>>()
        @Synchronized override fun read(key: String): Int? {
            if (failReads) throw IllegalStateException("unreadable")
            return data[key]
        }
        @Synchronized override fun write(key: String, value: Int) {
            if (failWrites || key == failWriteKey) throw java.io.IOException("disk full")
            writes += key to value
            data[key] = value
        }
    }

    private fun loaded(key: String, store: IntStore) = BestScore(key, store).also { it.load() }

    // ------------------------------------------------------------------ what counts as a new best

    @Test
    fun `first ever score above zero is a new best, zero never is, equal never is, higher always is`() {
        val store = FakeStore()
        val b = loaded("k", store)
        assertEquals(0, b.best)
        assertFalse("0 against a best of 0", b.submit(0))
        assertTrue("first score above 0", b.submit(1))
        assertFalse("equal", b.submit(1))
        assertFalse("lower", b.submit(0))
        assertTrue(b.submit(2))
        assertEquals(listOf("k" to 1, "k" to 2), store.writes)
        assertEquals(2, store.data["k"])
    }

    @Test
    fun `scores are clamped to zero to 999999 and the number stored is never outside that`() {
        val store = FakeStore()
        val b = loaded("k", store)
        assertFalse(b.submit(-5))
        assertFalse(b.submit(Int.MIN_VALUE))
        assertTrue(b.submit(1_000_000))
        assertEquals(999_999, b.best)
        assertFalse("a bigger one than the cap is not a new best over the cap", b.submit(Int.MAX_VALUE))
        assertEquals(999_999, store.data["k"])
        assertTrue(store.writes.all { it.second in 0..999_999 })
        assertEquals(1, store.writes.size)
    }

    @Test
    fun `an unreadable value counts as zero and a broken value never crashes or shows through`() {
        for (junk in listOf(-1, -999, 1_000_000, Int.MAX_VALUE, Int.MIN_VALUE)) {
            val b = loaded("k", FakeStore(data = hashMapOf("k" to junk)))
            assertEquals("stored $junk", 0, b.best)
        }
        val unreadable = loaded("k", FakeStore(data = hashMapOf("k" to 500), failReads = true))
        assertEquals(0, unreadable.best)
        assertTrue("a score still counts and is kept", unreadable.submit(7))
        assertEquals(7, unreadable.best)
        assertEquals(999_999, loaded("k", FakeStore(data = hashMapOf("k" to 999_999))).best)
        assertEquals(0, loaded("k", FakeStore(data = hashMapOf("k" to 0))).best)
    }

    // ------------------------------------------------------------------ failing storage

    @Test
    fun `a store that refuses every write keeps the best in memory, throws nothing, and lower scores cannot replace it`() {
        val b = loaded("k", FakeStore(failWrites = true))
        assertTrue(b.submit(40))
        assertTrue(b.submit(90))
        assertFalse(b.submit(60))
        assertEquals(90, b.best)
    }

    @Test
    fun `two games share one store without seeing each other, and one failing does not hurt the other`() {
        val store = FakeStore(failWriteKey = "best.paw-pop")
        val blocks = loaded("best.paw-blocks", store)
        val pop = loaded("best.paw-pop", store)
        assertTrue(blocks.submit(50))
        assertTrue(pop.submit(200))
        assertEquals(50, blocks.best)
        assertEquals("the failing game still shows its own number", 200, pop.best)
        assertEquals(50, store.data["best.paw-blocks"])
        assertNull("nothing reached the failing key", store.data["best.paw-pop"])
        assertFalse(blocks.submit(150 - 100)) // equal
        // Fresh keepers over the same disk (a new app run): each reads only its own key.
        val again = loaded("best.paw-blocks", store)
        val popAgain = loaded("best.paw-pop", store)
        assertEquals(50, again.best)
        assertEquals("the pop write never happened, so the new run sees 0", 0, popAgain.best)
        store.data["best.paw-pop"] = 33
        assertEquals(50, loaded("best.paw-blocks", store).best)
        assertEquals(33, loaded("best.paw-pop", store).best)
    }

    @Test
    fun `a broken value under one key does not disturb the other key`() {
        val store = FakeStore(data = hashMapOf("best.paw-blocks" to -7, "best.paw-pop" to 12))
        assertEquals(0, loaded("best.paw-blocks", store).best)
        assertEquals(12, loaded("best.paw-pop", store).best)
    }

    // ------------------------------------------------------------------ a slow read

    @Test
    fun `a score made before a slow read finishes never overwrites a higher stored best`() {
        val store = FakeStore(data = hashMapOf("k" to 400))
        val b = BestScore("k", store)
        assertTrue("held in memory only", b.submit(60))
        assertTrue(store.writes.isEmpty())
        b.load()
        assertEquals(400, b.best)
        assertTrue("nothing written over the 400", store.writes.isEmpty())
        assertEquals(400, store.data["k"])
    }

    @Test
    fun `a score made before a slow read finishes is saved once the read shows it beat the stored best`() {
        val store = FakeStore(data = hashMapOf("k" to 20))
        val b = BestScore("k", store)
        b.submit(60)
        b.load()
        assertEquals(60, b.best)
        assertEquals(60, store.data["k"])
        assertEquals(1, store.writes.size)
    }

    @Test
    fun `a real slow read on another thread, with scores arriving meanwhile, ends with the right number in memory and on disk`() {
        for (stored in listOf(0, 250, 900)) {
            val gate = CountDownLatch(1)
            val started = CountDownLatch(1)
            val store = object : IntStore {
                val disk = java.util.concurrent.ConcurrentHashMap<String, Int>().also { if (stored > 0) it["k"] = stored }
                override fun read(key: String): Int? { started.countDown(); gate.await(5, TimeUnit.SECONDS); return disk[key] }
                override fun write(key: String, value: Int) { disk[key] = value }
            }
            val b = BestScore("k", store)
            val reader = thread { b.load() }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue(b.submit(100))   // while the read is still out
            assertTrue(b.submit(300))
            assertFalse(b.submit(300))
            gate.countDown()
            reader.join(5000)
            val expected = maxOf(stored, 300)
            assertEquals("stored $stored", expected, b.best)
            assertEquals("stored $stored: disk", expected, store.disk["k"])
        }
    }

    @Test
    fun `loading twice, or after a failure, is harmless`() {
        val store = FakeStore(data = hashMapOf("k" to 10))
        val b = BestScore("k", store)
        store.failReads = true
        b.load()
        assertEquals(0, b.best)
        store.failReads = false
        b.load()
        assertEquals(10, b.best)
        b.load()
        assertEquals(10, b.best)
        assertTrue(store.writes.isEmpty())
    }

    // ------------------------------------------------------------------ the good-game touch gate

    @Test
    fun `the two big buttons ignore every touch for the first 0_6 seconds, then act exactly once between them`() {
        val gate = GoodGameGate()
        for (ms in listOf(-100L, 0L, 1L, 300L, 599L)) assertFalse("at $ms", gate.accept(ms))
        assertTrue(gate.accept(600))
        for (ms in listOf(600L, 601L, 900L, 60_000L)) assertFalse("second tap at $ms", gate.accept(ms))
    }

    @Test
    fun `a bored child hammering both buttons from the first frame gets exactly one action`() {
        val gate = GoodGameGate()
        var acted = 0
        for (ms in 0L..5_000L step 3) if (gate.accept(ms)) acted++
        assertEquals(1, acted)
    }
}
