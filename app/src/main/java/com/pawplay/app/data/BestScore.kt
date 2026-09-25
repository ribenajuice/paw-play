package com.pawplay.app.data

/**
 * Where one whole number per key is kept. Pure Kotlin so the rules below are unit-tested with a fake store
 * (one that throws, returns junk or fills up); the real one is `SharedPrefsIntStore` in BestScores.kt.
 *
 * [read] returns null when nothing is stored, and may throw (an unreadable file, a value that is not a whole
 * number). [write] may throw too (storage full or refused). [BestScore] treats every failure as "nothing stored".
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

    /** True once [load] has looked at the store. Until then a new best is held in memory only, so it can never overwrite a higher saved one. */
    private var loaded = false

    /**
     * Reads the stored best. Blocking: call it off the main thread (the games use `Dispatchers.IO` with a 500ms
     * cap). A missing, unreadable, non-integer, negative or over-[MAX] value counts as 0. Merges with `max`, so a
     * score submitted before a slow read finishes is never lost, and is written now if it beats what was stored.
     */
    fun load() {
        val stored = try {
            store.read(key)
        } catch (e: Exception) {
            null
        }
        val valid = if (stored != null && stored in 0..MAX) stored else 0
        val unsaved: Int
        synchronized(lock) {
            if (valid > best) best = valid
            loaded = true
            unsaved = if (best > valid) best else 0 // in memory but not (or not yet) on disk
        }
        if (unsaved > 0) write(unsaved)
    }

    /**
     * Offers [score]. It is clamped to 0..[MAX]; only a score that beats the best (equal does not) is kept and
     * written. Returns true when it did beat it. A failed write is swallowed: the in-memory [best] carries on.
     */
    fun submit(score: Int): Boolean {
        val clamped = score.coerceIn(0, MAX)
        val writeNow: Boolean
        synchronized(lock) {
            if (clamped <= best) return false
            best = clamped
            writeNow = loaded
        }
        if (writeNow) write(clamped)
        return true
    }

    private fun write(value: Int) {
        try {
            store.write(key, value)
        } catch (e: Exception) {
            // Storage full or refused: play on with the in-memory best.
        }
    }

    companion object {
        /** The largest number ever kept: six digits, so the score always fits its place on screen. */
        const val MAX = 999_999
    }
}
