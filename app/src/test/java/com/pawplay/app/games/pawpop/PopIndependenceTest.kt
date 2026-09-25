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
    fun `no text, network, intents, links, ads, billing, audio or direct storage anywhere in the Paw Pop package`() {
        // Amended 2026-09-25: Paw Pop reaches its saved best only through data/BestScore (its own key), never SharedPreferences,
        // files or a database itself; everything else on this list is still never allowed.
        val banned = listOf(
            "Text(", "BasicText", "stringResource", "java.net", "okhttp", "HttpURLConnection", "Intent", "startActivity", "Uri", "WebView", "LocalUriHandler",
            "billing", "admob", "firebase", "analytics", "MediaPlayer", "SoundPool", "ToneGenerator", "AudioTrack", "AudioManager", "INTERNET", "SharedPreferences", "DataStore", "openFileOutput", "File(",
            "getSharedPreferences", "FileOutputStream", "Vibrator", "HapticFeedback", "Toast", "Settings.",
        )
        val files = popSources()
        assertEquals("the scan must see every file", 9, files.size)
        for (f in files) {
            val text = code(f)
            for (b in banned) assertFalse("${f.name} contains '$b'", text.contains(b, ignoreCase = b.first().isLowerCase()))
        }
    }

    /**
     * Amended 2026-09-25 (PRD stories 68-73): a score, three paws, a best score and a kind ending are allowed. Still never: a
     * combo, streak, multiplier, countdown, currency, purchase, leaderboard, sharing, "continue" or "revive", or a sad ending.
     */
    @Test
    fun `there is a score, paws and a kind ending, but no combo, streak, countdown, currency, comparison or continue in the code`() {
        val text = popSources().joinToString("\n") { code(it) }
        for (word in listOf("combo", "streak", "multiplier", "countdown", "leaderboard", "coin", "currency", "purchase", "revive", "extra life", "share", "gem"))
            assertFalse("'$word' in Paw Pop code", Regex("\\b${Regex.escape(word)}", RegexOption.IGNORE_CASE).containsMatchIn(text))
        for (word in listOf("game over!", "you lost", "try again", "\"game over\""))
            assertFalse("wording '$word' in Paw Pop code", text.contains(word, ignoreCase = true))
        assertTrue(text.contains("GoodGameScreen"))
        assertTrue(text.contains("START_PAWS"))
        // nothing is drawn as text: no Text, no drawText, no text measurer (the score is the shared ScoreNumber)
        for (word in listOf("drawText", "TextMeasurer", "rememberTextMeasurer", "NativeCanvas", "nativeCanvas")) assertFalse(word, text.contains(word))
    }

    @Test
    fun `Paw Pop reaches shared code only through data and ui, owns its own key, and never names Paw Blocks or its key`() {
        val code = popSources().joinToString("\n") { it.readText() }
        assertFalse(code.contains("games.pawblocks"))
        assertFalse(code.contains("best.paw-blocks"))
        assertTrue(code.contains("\"best.paw-pop\""))
        val shared = Regex("^import com\\.pawplay\\.app\\.(data|ui)\\.(\\w+)", RegexOption.MULTILINE).findAll(code).map { it.groupValues[1] + "." + it.groupValues[2] }.toSet()
        val allowed = setOf("data.BestScore", "data.rememberBestScore", "ui.GoodGameScreen", "ui.PawLivesRow", "ui.PAW_FADE_MS", "ui.ScoreNumber", "ui.theme")
        assertTrue("unexpected shared imports: ${shared - allowed}", allowed.containsAll(shared))
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
    fun `the game keeps nothing between plays but the best score, which lives in data`() {
        val text = popSources().joinToString("\n") { code(it) }
        for (word in listOf("rememberSaveable", "SavedState", "Bundle", "Parcel", "Preferences", "Serializable", "ObjectOutputStream")) assertFalse(word, text.contains(word))
        // a saved paw count, stage or progress would be a second thing kept: the only write is the best score
        assertEquals(1, Regex("\\.submit\\(").findAll(text).count())
        assertFalse(text.contains("best.paw-pop\"") && text.contains(".write("))
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
