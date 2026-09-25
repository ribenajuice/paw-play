package com.pawplay.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Independence of the shared code and of the two scored games (docs/DECISIONS.md, "Shared pieces for the two
 * scored games"): `ui/` and `data/` import no game's code except the two public Paw Match icons every game already
 * uses, no game imports another game's code, and each game owns its own saved-best key. Run from `app/`.
 */
class SharedCodeIndependenceTest {
    private val base = File("src/main/java/com/pawplay/app")

    private fun kotlinIn(dir: File): List<File> = dir.walkTopDown().filter { it.extension == "kt" }.toList()

    private fun code(f: File) = f.readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }.joinToString("\n")

    private fun gameImports(files: List<File>): List<String> = files.flatMap { f ->
        f.readLines().mapIndexedNotNull { i, l ->
            Regex("^import com\\.pawplay\\.app\\.games\\.(\\w+)\\.(\\w+)").find(l)?.let { "${f.name}:${i + 1} ${it.groupValues[1]}.${it.groupValues[2]}" }
        }
    }

    // ui/ has a `theme` sub-package (colours); only ui/ files at the top level and data/ are scanned for game imports.
    private fun sharedFiles(): List<File> = kotlinIn(File(base, "ui")) + kotlinIn(File(base, "data"))

    @Test
    fun `the scan sees the shared files`() {
        val names = sharedFiles().map { it.name }.toSet()
        for (n in listOf("BestScore.kt", "BestScores.kt", "GoodGameScreen.kt", "GoodGameGate.kt", "PawLives.kt", "Rosette.kt")) assertTrue("$n not scanned", n in names)
    }

    @Test
    fun `ui and data import no game code but the two public Paw Match icons`() {
        val imports = gameImports(sharedFiles()).map { it.substringAfter(' ') }
        val allowed = setOf("pawmatch.HomeGlyphIcon", "pawmatch.PawPrintIcon")
        assertTrue("unexpected game imports in ui/ or data/: ${imports.toSet() - allowed}", allowed.containsAll(imports.toSet()))
        // and by any other spelling
        for (f in sharedFiles()) for (g in listOf("pawblocks", "pawpop", "pawkitchen", "pawpour", "pawtrace"))
            assertFalse("${f.name} mentions $g", code(f).contains("games.$g"))
    }

    @Test
    fun `ui and data have no network, links, ads, billing, audio or text-to-others`() {
        val banned = listOf("java.net", "okhttp", "HttpURLConnection", "Intent", "startActivity", "Uri", "WebView", "LocalUriHandler", "billing", "admob", "firebase", "analytics", "MediaPlayer", "SoundPool", "ToneGenerator", "AudioTrack", "INTERNET", "Vibrat")
        for (f in sharedFiles()) for (b in banned) assertFalse("${f.name} contains '$b'", code(f).contains(b, ignoreCase = b.first().isLowerCase()))
    }

    @Test
    fun `the good-game screen has no forbidden wording, money or comparison`() {
        val screen = code(File(base, "ui/GoodGameScreen.kt")) + code(File(base, "ui/PawLives.kt"))
        for (w in listOf("game over", "you lost", "try again", "failed", "coin", "gem", "buy", "purchase", "leaderboard", "share", "rank"))
            assertFalse("'$w' in the good-game code", Regex("\\b${Regex.escape(w)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(screen))
    }

    private fun gameDir(name: String) = File(base, "games/$name")

    @Test
    fun `no game imports another game's code, except Paw Match's public pieces, and the two scored games only its two icons`() {
        val games = listOf("pawmatch", "pawpour", "pawkitchen", "pawtrace", "pawblocks", "pawpop")
        for (g in games) {
            val files = kotlinIn(gameDir(g)).filter { it.exists() }
            for (other in games - g) for (f in files)
                for ((i, line) in f.readLines().withIndex()) {
                    val m = Regex("^import com\\.pawplay\\.app\\.games\\.$other\\.(\\w+)").find(line) ?: continue
                    val what = "$other.${m.groupValues[1]}"
                    val ok = when {
                        other == "pawmatch" && g in listOf("pawblocks", "pawpop") -> what in setOf("pawmatch.HomeGlyphIcon", "pawmatch.PawPrintIcon")
                        other == "pawmatch" -> true // the older games' existing use of Paw Match's public icons and critters
                        else -> false
                    }
                    assertTrue("${f.name}:${i + 1} ($g) imports $what", ok)
                }
        }
    }

    @Test
    fun `each scored game owns its saved-best key and neither names the other's`() {
        val blocks = kotlinIn(gameDir("pawblocks")).joinToString("\n") { code(it) }
        assertTrue(blocks.contains("\"best.paw-blocks\""))
        assertFalse(blocks.contains("best.paw-pop"))
        val popDir = gameDir("pawpop")
        if (popDir.exists()) {
            val pop = kotlinIn(popDir).joinToString("\n") { code(it) }
            assertFalse("Pop names Blocks' key", pop.contains("best.paw-blocks"))
        }
        // The other four games store nothing and know no key at all.
        for (g in listOf("pawmatch", "pawpour", "pawkitchen", "pawtrace"))
            for (f in kotlinIn(gameDir(g))) {
                assertFalse("${f.name} mentions a saved best", code(f).contains("best.paw-"))
                assertFalse("${f.name} uses data/", code(f).contains("com.pawplay.app.data"))
            }
    }

    @Test
    fun `the four other games are untouched by the score work and still have no score or lose state`() {
        for (g in listOf("pawmatch", "pawpour", "pawkitchen", "pawtrace"))
            for (f in kotlinIn(gameDir(g))) {
                val c = code(f)
                assertFalse("${f.name} imports the shared score code", c.contains("com.pawplay.app.ui.GoodGame") || c.contains("com.pawplay.app.ui.PawLives") || c.contains("ScoreNumber"))
            }
    }
}
