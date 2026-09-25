package com.pawplay.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The saved best score never leaves the phone (docs/DECISIONS.md, "The saved number never leaves the phone"): no
 * permission at all (so no INTERNET), no backup, no device transfer. Reads the manifest and the two rules files
 * the way the glyph tests read their document. Run from `app/`, like the other scans of source files.
 */
class AppPrivacyTest {
    private val main = File("src/main")

    private fun withoutComments(xml: String) = xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    private fun manifest() = withoutComments(File(main, "AndroidManifest.xml").readText())

    @Test
    fun `the app asks for no permission at all, so it cannot reach a network`() {
        val m = manifest()
        assertFalse("a uses-permission entry appeared", m.contains("uses-permission"))
        assertFalse(m.contains("INTERNET"))
        assertFalse(m.contains("ACCESS_NETWORK_STATE"))
        assertFalse("a custom permission appeared", Regex("<permission\\b").containsMatchIn(m))
    }

    @Test
    fun `backup is off and both rules files are named`() {
        val m = manifest()
        assertTrue(m.contains("android:allowBackup=\"false\""))
        assertFalse(m.contains("android:allowBackup=\"true\""))
        assertTrue(m.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(m.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    private fun excludedDomains(section: String): Set<String> =
        Regex("<exclude\\s+domain=\"(\\w+)\"").findAll(section).map { it.groupValues[1] }.toSet()

    private val everything = setOf("root", "file", "database", "sharedpref", "external")

    @Test
    fun `the older backup rules exclude every domain, the saved best included, and include nothing`() {
        val xml = withoutComments(File(main, "res/xml/backup_rules.xml").readText())
        assertEquals(everything, excludedDomains(xml))
        assertFalse(xml.contains("<include"))
    }

    @Test
    fun `the newer rules exclude every domain from both cloud backup and device transfer, and include nothing`() {
        val xml = withoutComments(File(main, "res/xml/data_extraction_rules.xml").readText())
        val cloud = xml.substringAfter("<cloud-backup").substringBefore("</cloud-backup>")
        val transfer = xml.substringAfter("<device-transfer").substringBefore("</device-transfer>")
        assertTrue("no cloud-backup section", xml.contains("<cloud-backup"))
        assertTrue("no device-transfer section", xml.contains("<device-transfer"))
        assertEquals(everything, excludedDomains(cloud))
        assertEquals(everything, excludedDomains(transfer))
        assertFalse(xml.contains("<include"))
    }

    @Test
    fun `a dark-mode or font-size change does not restart the app and lose the game in progress`() {
        val m = manifest()
        val configChanges = Regex("android:configChanges=\"([^\"]*)\"").find(m)!!.groupValues[1].split('|').toSet()
        for (c in listOf("orientation", "screenSize", "smallestScreenSize", "screenLayout", "uiMode", "density", "fontScale", "locale", "layoutDirection", "keyboard", "keyboardHidden"))
            assertTrue("configChanges lacks $c", c in configChanges)
    }

    @Test
    fun `the preferences file is private and only holds the two best scores`() {
        val code = File(main, "java/com/pawplay/app/data/BestScores.kt").readText()
        assertTrue(code.contains("MODE_PRIVATE"))
        assertFalse(code.contains("MODE_WORLD"))
        assertFalse("commit() would block a frame", Regex("\\.commit\\(\\)").containsMatchIn(code))
        assertTrue(code.contains("\"paw_play_bests\""))
        // Each game owns its key: the shared code names neither.
        assertFalse(code.contains("best.paw-blocks"))
        assertFalse(code.contains("best.paw-pop"))
    }

    @Test
    fun `no other file in the app writes preferences, files or anything else to storage`() {
        val banned = listOf("getSharedPreferences", "openFileOutput", "FileOutputStream", "FileWriter", "getExternalFilesDir", "SQLiteDatabase", "DataStore", "MediaStore")
        val sources = File(main, "java").walkTopDown().filter { it.extension == "kt" }
        for (f in sources) {
            if (f.name == "BestScores.kt") continue
            val code = f.readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }.joinToString("\n")
            for (b in banned) assertFalse("${f.name} contains '$b'", code.contains(b))
        }
    }
}
