package com.pawplay.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The Android side of the saved best scores (docs/DECISIONS.md, "Best scores"): one private preferences file,
 * one whole number per game, nothing else. No backup (see the manifest), no network, no permission.
 *
 * A [BestScore] per key is kept for the life of the app process, so the in-memory best survives leaving a game
 * and coming back even if the disk never accepted a write.
 */
object BestScores {
    /** The private preferences file; it holds only the integers named by each game's own key. */
    const val FILE_NAME = "paw_play_bests"

    private val keepers = ConcurrentHashMap<String, BestScore>()

    @Volatile
    private var store: IntStore? = null

    /**
     * Called once from `MainActivity.onCreate`. Only asks the platform to start reading the file on its own
     * background thread, so it is normally loaded before a game opens; it never blocks and never throws.
     */
    fun warmUp(context: Context) {
        storeFor(context)
    }

    /** The keeper for [key] (a game's own key string), made on first use. */
    fun forKey(context: Context, key: String): BestScore =
        keepers.computeIfAbsent(key) { BestScore(it, storeFor(context)) }

    private fun storeFor(context: Context): IntStore {
        store?.let { return it }
        return synchronized(this) {
            store ?: makeStore(context).also { store = it }
        }
    }

    private fun makeStore(context: Context): IntStore = try {
        SharedPrefsIntStore(context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))
    } catch (e: Exception) {
        MemoryIntStore() // the platform refused the file: play on with the in-memory best
    }
}

/** Reads through `getInt` (which throws for a value that is not an integer; [BestScore] treats that as 0) and writes with `apply()`, never `commit()`. */
internal class SharedPrefsIntStore(private val prefs: SharedPreferences) : IntStore {
    override fun read(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    override fun write(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
}

/** Keeps nothing on disk. Used only if the platform will not hand out the preferences file. */
internal class MemoryIntStore : IntStore {
    override fun read(key: String): Int? = null
    override fun write(key: String, value: Int) = Unit
}

/** The [BestScore] for [key]. Reading it from disk is the caller's job: call `load()` on `Dispatchers.IO`. */
@Composable
fun rememberBestScore(key: String): BestScore {
    val context = LocalContext.current
    return remember(key) { BestScores.forKey(context, key) }
}
