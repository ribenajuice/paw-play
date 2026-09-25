package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The standing rules and independence, as tests (docs/ARCHITECTURE.md: each game "knows nothing about the others").
 * Run from `app/`, like the other games' scans of their own sources.
 */
class PawBlocksIndependenceTest {

    private fun sources(dir: String): List<File> =
        File("src/main/java/com/pawplay/app/games/$dir").listFiles { f -> f.extension == "kt" }!!.toList()

    private fun blocksSources() = sources("pawblocks")

    @Test
    fun `no text, network, intents, links, ads, billing, audio or storage anywhere in the Paw Blocks package`() {
        val banned = listOf(
            "Text(", "BasicText", "stringResource", "java.net", "okhttp", "HttpURLConnection", "Intent", "startActivity", "Uri", "WebView", "LocalUriHandler",
            "billing", "admob", "firebase", "analytics", "MediaPlayer", "SoundPool", "ToneGenerator", "AudioTrack", "AudioManager", "INTERNET", "SharedPreferences", "DataStore", "openFileOutput", "File(",
            "getSharedPreferences", "FileOutputStream", "Vibrat", "Settings.",
        )
        val files = blocksSources()
        assertEquals("the scan must see every file", 13, files.size)
        for (f in files) {
            val code = f.readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }.joinToString("\n")
            for (b in banned) assertFalse("${f.name} contains '$b'", code.contains(b, ignoreCase = b.first().isLowerCase()))
        }
    }

    /**
     * Amended 2026-09-25 (PRD stories 63-65, 70-73): a score, three paws, a best score and a kind ending are allowed. What
     * is still never allowed: a combo, streak, timer, multiplier, or anything that reads as money or a comparison.
     */
    @Test
    fun `there is a score, paws and a kind ending, but no combo, streak, timer, currency or comparison in the code`() {
        val code = blocksSources().joinToString("\n") { f -> f.readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }.joinToString("\n") }
        for (word in listOf("combo", "streak", "multiplier", "countdown", "timer", "coin", "currency", "gem", "leaderboard", "share", "revive", "extra life", "purchase"))
            assertFalse("'$word' in Paw Blocks code", code.contains(word, ignoreCase = true))
        for (word in listOf("game over!", "you lost", "try again", "\"game over\""))
            assertFalse("wording '$word' in Paw Blocks code", code.contains(word, ignoreCase = true))
        assertTrue(code.contains("GoodGameScreen"))
        assertTrue(code.contains("START_PAWS"))
    }

    @Test
    fun `Paw Blocks reaches shared code only through data and ui, and never touches Paw Pop or its key`() {
        val code = blocksSources().joinToString("\n") { it.readText() }
        assertFalse(code.contains("games.pawpop"))
        assertFalse(code.contains("best.paw-pop"))
        assertTrue(code.contains("\"best.paw-blocks\""))
        val shared = Regex("^import com\\.pawplay\\.app\\.(data|ui)\\.(\\w+)", RegexOption.MULTILINE).findAll(code).map { it.groupValues[1] + "." + it.groupValues[2] }.toSet()
        val allowed = setOf(
            "data.BestScore", "data.rememberBestScore", "ui.GoodGameScreen", "ui.PawLivesRow", "ui.PAW_FADE_MS", "ui.ScoreNumber",
        )
        // theme colours are `ui.theme.<name>`, matched as `ui.theme`
        assertTrue("unexpected shared imports: ${shared - allowed - setOf("ui.theme")}", (allowed + "ui.theme").containsAll(shared))
    }

    private fun crossGameImports(): List<String> =
        blocksSources().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l ->
                val m = Regex("^import com\\.pawplay\\.app\\.games\\.(pawmatch|pawpour|pawkitchen|pawtrace)\\.(\\w+)").find(l)
                m?.let { "${f.name}:${i + 1} ${m.groupValues[1]}.${m.groupValues[2]}" }
            }
        }

    /**
     * Ratchet on independence: Paw Blocks imports nothing from Paw Kitchen, Paw Pour or Paw Trace, and from Paw Match
     * only the two public icons every game uses (`HomeGlyphIcon`, `PawPrintIcon`). The animal, home button, sparkle
     * and drawing helpers are its own copies (to move to ui/ in one follow-up touching all games).
     */
    @Test
    fun `Paw Blocks imports nothing from any other game but the two public Paw Match icons`() {
        val imports = crossGameImports().map { it.substringAfter(' ') }
        assertEquals(emptyList<String>(), imports.filter { it.startsWith("pawkitchen.") || it.startsWith("pawpour.") || it.startsWith("pawtrace.") })
        val allowed = setOf("pawmatch.HomeGlyphIcon", "pawmatch.PawPrintIcon")
        assertTrue("unexpected cross-game imports: ${imports.toSet() - allowed}", allowed.containsAll(imports.toSet()))
    }

    @Test
    fun `no other game imports Paw Blocks, and the catalog lists it once, after Paw Trace`() {
        for (dir in listOf("pawmatch", "pawpour", "pawkitchen", "pawtrace"))
            for (f in sources(dir)) assertFalse("${f.name} imports pawblocks", f.readText().contains("games.pawblocks"))
        val catalog = File("src/main/java/com/pawplay/app/games/GameCatalog.kt").readText()
        assertEquals(1, Regex("\\bPawBlocksGame,").findAll(catalog).count())
        assertTrue(catalog.indexOf("PawTraceGame,") < catalog.indexOf("PawBlocksGame,"))
        assertEquals("paw-blocks", PawBlocksGame.id)
    }

    @Test
    fun `manifest asks for no permissions and the app has no network, ads, billing or media libraries`() {
        val manifest = File("src/main/AndroidManifest.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(manifest.contains("uses-permission"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        val gradle = (File("build.gradle.kts").takeIf { it.exists() } ?: File("build.gradle")).readText().lowercase()
        for (lib in listOf("okhttp", "retrofit", "volley", "coil", "glide", "billing", "play-services", "firebase", "admob", "ads", "exoplayer", "media3", "analytics", "crashlytics", "ktor"))
            assertFalse("dependency '$lib'", gradle.lines().filter { it.contains("implementation") || it.contains("api(") }.any { it.contains(lib) })
    }

    @Test
    fun `the design document lists every family colour and mark, so the two cannot drift apart`() {
        val doc = File("../docs/DESIGN-SYSTEM.md").readText()
        for (f in Family.values()) {
            val hex = "#%06X".format(f.colour and 0xFFFFFF)
            assertTrue("$hex missing from DESIGN-SYSTEM.md", doc.contains(hex))
            assertTrue("${f.mark} missing", doc.contains(f.mark.name.lowercase()))
        }
        for (s in BlockShapes.all) assertTrue("${s.id} missing", doc.contains("`${s.id}`"))
    }
}
