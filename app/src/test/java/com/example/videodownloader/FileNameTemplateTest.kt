package com.example.videodownloader

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameTemplateTest {

    private fun sanitizeFileName(rawName: String): String {
        return rawName.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    private fun formatFileName(template: String, title: String, platform: String, date: String): String {
        val cleanTitle = sanitizeFileName(title)
        val cleanPlatform = sanitizeFileName(platform)
        val formatted = when (template) {
            "DATE_TITLE" -> "${date}_$cleanTitle"
            "PLATFORM_TITLE" -> "${cleanPlatform}_$cleanTitle"
            "TITLE_ONLY" -> cleanTitle
            else -> "$cleanTitle - $cleanPlatform"
        }
        return formatted.take(100)
    }

    @Test
    fun testSanitizeFileName() {
        val badName = "Cool Video: How to Win? <Part 1> | *HD*"
        val expected = "Cool Video_ How to Win_ _Part 1_ _ _HD_"
        assertEquals(expected, sanitizeFileName(badName))
    }

    @Test
    fun testFormatFileNameTemplates() {
        val title = "Крутой клип"
        val platform = "TikTok"
        val date = "2026-10-11"

        assertEquals("Крутой клип - TikTok", formatFileName("TITLE_PLATFORM", title, platform, date))
        assertEquals("2026-10-11_Крутой клип", formatFileName("DATE_TITLE", title, platform, date))
        assertEquals("TikTok_Крутой клип", formatFileName("PLATFORM_TITLE", title, platform, date))
        assertEquals("Крутой клип", formatFileName("TITLE_ONLY", title, platform, date))
    }

    @Test
    fun testFileNameLengthCapping() {
        val longTitle = "A".repeat(150)
        val formatted = formatFileName("TITLE_ONLY", longTitle, "YouTube", "2026-10-11")
        assertEquals(100, formatted.length)
    }
}
