package com.pawplay.app.data

/**
 * Where one whole number per key is kept. Pure Kotlin so the rules below are unit-tested with a fake store
 * (one that throws, returns junk or fills up); the real one is `SharedPrefsIntStore` in BestScores.kt.
 *
 * [read] returns null when nothing is stored, and may throw: `ClassCastException` for a value that is not a whole number
 * (counts as nothing stored), anything else for a store that could not be read (nothing is written until a read succeeds).
 * [write] may throw too (storage full or refused): the best carries on in memory.
 */
interface IntStore {
    fun read(key: String): Int?
    fun write(key: String, value: Int)
}

/**
 * One game's best score (docs/PRD.md stories 70-71; docs/DECISIONS.md, "Best scores").
 *
 * The best always lives in memory first ([best]), so a write that fails leaves the right number for the rest
 * of the app session. Nothing here ever throws to the caller and nothing shows an error. A game owns its own key,
 * so one game can never read or change another's number. Call [load] (off the main thread) before the first [submit]
 * matters: a score submitted earlier is kept in memory and saved by [load] once it has seen what was stored.
 */
class BestScore(val key: String, private val store: IntStore) {
    private val lock = Any()

    @Volatile
    var best: Int = 0
        private set

    /** True once a read of the store has succeeded. Until then a new best is held in memory only, so it can never overwrite a saved one that has not been seen. */
    private var loaded = false

    /** The highest value known to be on disk (what a successful read found or a successful write stored). Nothing lower is ever written, so the disk never goes backwards. */
    private var onDisk = 0

    /** True once [load] has read the store successfully (so [best] includes what was saved). A failed read leaves this false. */
    val isLoaded: Boolean get() = synchronized(lock) { loaded }

    /**
     * What the store held when [load] last read it (0 for nothing or junk, and for a read that failed), before merging
     * with anything submitted since. A game that began before the read finished uses it to judge "new best" against the saved value.
     */
    @Volatile
    var storedAtLoad: Int = 0
        private set

    /**
     * Reads the stored best. Blocking: call it off the main thread (the games use `Dispatchers.IO` with a 500ms
     * cap). Nothing stored, or a value that is not a whole number (`ClassCastException`, what `getInt` throws), negative or
     * over [MAX], counts as 0 and may be replaced. A read that fails any other way is NOT "nothing stored": the store may
     * hold a real best that could not be seen, so nothing is written until a later [load] reads it (the games call it
     * each time they open); until then a new best lives in memory only. Merges with `max`, so a score submitted before a
     * slow read finishes is never lost, and is written now if it beats what was stored.
     */
    fun load() {
        var readOk = true
        val stored = try {
            store.read(key)
        } catch (e: ClassCastException) {
            null // not a whole number: nothing usable is stored
        } catch (e: Exception) {
            readOk = false
            null
        }
        val valid = if (stored != null && stored in 0..MAX) stored else 0
        synchronized(lock) {
            if (!readOk) return // unread: keep whatever is in memory, write nothing
            if (valid > best) best = valid
            loaded = true
            storedAtLoad = valid
            onDisk = valid
            if (best > onDisk) write(best) // in memory but not (or not yet) on disk
        }
    }

    /**
     * Offers [score]. It is clamped to 0..[MAX]; only a score that beats the best (equal does not) is kept and
     * written. Returns true when it did beat it. A failed write is swallowed: the in-memory [best] carries on.
     */
    fun submit(score: Int): Boolean {
        val clamped = score.coerceIn(0, MAX)
        synchronized(lock) {
            if (clamped <= best) return false
            best = clamped
            if (loaded) write(clamped)
        }
        return true
    }

    /** Called with the lock held, so two writes can never land out of order. Only ever raises the stored number. */
    private fun write(value: Int) {
        if (value <= onDisk) return
        try {
            store.write(key, value)
            onDisk = value
        } catch (e: Exception) {
            // Storage full or refused: play on with the in-memory best; a later write or load tries again.
        }
    }

    companion object {
        /** The largest number ever kept: six digits, so the score always fits its place on screen. */
        const val MAX = 999_999
    }
}
