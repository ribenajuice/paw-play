package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The standing rules and independence, as tests (docs/ARCHITECTURE.md: each game "knows nothing about the others").
 * Run from `app/`, like the other games' scans of their own sources.
 */
class PopIndependenceTest {

    private fun sources(dir: String): List<File> =
        File("src/main/java/com/pawplay/app/games/$dir").listFiles { f -> f.extension == "kt" }!!.toList()

    private fun popSources() = sources("pawpop")

    private fun code(f: File): String =
        f.readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }.joinToString("\n")

    @Test
    fun `no text, network, intents, links, ads, billing, audio or storage anywhere in the Paw Pop package`() {
        val banned = listOf(
            "Text(", "BasicText", "stringResource", "java.net", "okhttp", "HttpURLConnection", "Intent", "startActivity", "Uri", "WebView", "LocalUriHandler",
            "billing", "admob", "firebase", "analytics", "MediaPlayer", "SoundPool", "ToneGenerator", "AudioTrack", "AudioManager", "INTERNET", "SharedPreferences", "DataStore", "openFileOutput", "File(",
            "Vibrator", "HapticFeedback", "Toast",
        )
        val files = popSources()
        assertEquals("the scan must see every file", 8, files.size)
        for (f in files) {
            val text = code(f)
            for (b in banned) assertFalse("${f.name} contains '$b'", text.contains(b, ignoreCase = b.first().isLowerCase()))
        }
    }

    @Test
    fun `nothing is scored, counted for show, timed or lost, and no number or countdown is drawn`() {
        val text = popSources().joinToString("\n") { code(it) }
        for (word in listOf("score", "combo", "streak", "highScore", "gameOver", "game over", "countdown", "lives", "lifeCount", "leaderboard", "coin", "currency", "purchase"))
            assertFalse("'$word' in Paw Pop code", text.contains(word, ignoreCase = true))
        // nothing is drawn as text: no Text, no drawText, no text measurer
        for (word in listOf("drawText", "TextMeasurer", "rememberTextMeasurer", "NativeCanvas", "nativeCanvas")) assertFalse(word, text.contains(word))
    }

    private fun crossGameImports(): List<String> =
        popSources().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l ->
                val m = Regex("^import com\\.pawplay\\.app\\.games\\.(pawmatch|pawpour|pawkitchen|pawtrace|pawblocks)\\.(\\w+)").find(l)
                m?.let { "${f.name}:${i + 1} ${m.groupValues[1]}.${m.groupValues[2]}" }
            }
        }

    /**
     * Ratchet on independence: Paw Pop imports nothing from Paw Blocks, Paw Kitchen, Paw Pour or Paw Trace, and from Paw
     * Match only the two public icons every game uses (`HomeGlyphIcon`, `PawPrintIcon`). The home button, the sparkle, the
     * animals and the drawing helpers are its own copies (to move to ui/ in one follow-up touching all games).
     */
    @Test
    fun `Paw Pop imports nothing from any other game but the two public Paw Match icons`() {
        val imports = crossGameImports().map { it.substringAfter(' ') }
        assertEquals(emptyList<String>(), imports.filter { !it.startsWith("pawmatch.") })
        val allowed = setOf("pawmatch.HomeGlyphIcon", "pawmatch.PawPrintIcon")
        assertTrue("unexpected cross-game imports: ${imports.toSet() - allowed}", allowed.containsAll(imports.toSet()))
        // and no fully qualified reference sneaks past the import scan
        for (f in popSources()) for (other in listOf("pawkitchen", "pawpour", "pawtrace", "pawblocks")) assertFalse("${f.name} mentions $other", code(f).contains("games.$other"))
    }

    @Test
    fun `no other game imports Paw Pop, and the catalog lists it once, right after Paw Blocks`() {
        for (dir in listOf("pawmatch", "pawpour", "pawkitchen", "pawtrace", "pawblocks"))
            for (f in sources(dir)) assertFalse("${f.name} imports pawpop", f.readText().contains("games.pawpop"))
        val catalog = File("src/main/java/com/pawplay/app/games/GameCatalog.kt").readText()
        assertEquals(1, Regex("\\bPawPopGame,").findAll(catalog).count())
        assertTrue(catalog.indexOf("PawBlocksGame,") < catalog.indexOf("PawPopGame,"))
        assertEquals("paw-pop", PawPopGame.id)
    }

    @Test
    fun `the hub shell is untouched by this game`() {
        val home = File("src/main/java/com/pawplay/app/home/HomeScreen.kt").readText()
        assertFalse(home.contains("pawpop") || home.contains("PawPop"))
    }

    @Test
    fun `the game keeps nothing between plays`() {
        val text = popSources().joinToString("\n") { code(it) }
        for (word in listOf("rememberSaveable", "SavedState", "Bundle", "Parcel", "Preferences", "Serializable", "ObjectOutputStream")) assertFalse(word, text.contains(word))
    }

    @Test
    fun `manifest asks for no permissions and the app has no network, ads, billing or media libraries`() {
        val manifest = File("src/main/AndroidManifest.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(manifest.contains("uses-permission"))
        val gradle = (File("build.gradle.kts").takeIf { it.exists() } ?: File("build.gradle")).readText().lowercase()
        for (lib in listOf("okhttp", "retrofit", "volley", "coil", "glide", "billing", "play-services", "firebase", "admob", "ads", "exoplayer", "media3", "analytics", "crashlytics", "ktor"))
            assertFalse("dependency '$lib'", gradle.lines().filter { it.contains("implementation") || it.contains("api(") }.any { it.contains(lib) })
    }
}
