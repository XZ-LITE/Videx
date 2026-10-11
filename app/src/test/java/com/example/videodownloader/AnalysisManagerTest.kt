package com.example.videodownloader

import com.example.videodownloader.logic.AnalysisManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisManagerTest {

    @Test
    fun testParseMetadataSingleVideo() {
        val json = """
            {
                "title": "Крутое видео из YouTube",
                "thumbnail": "https://img.youtube.com/vi/123/0.jpg",
                "is_photo": false,
                "is_playlist": false,
                "available_qualities": ["1080", "720", "480"]
            }
        """.trimIndent()

        val meta = AnalysisManager.parseMetadataFromJson(json)
        assertNotNull(meta)
        assertEquals("Крутое видео из YouTube", meta?.title)
        assertEquals("https://img.youtube.com/vi/123/0.jpg", meta?.thumbnailUrl)
        assertFalse(meta?.isPhotoPost ?: true)
        assertFalse(meta?.isPlaylist ?: true)
        assertEquals(3, meta?.availableQualities?.size)
        assertEquals("1080", meta?.availableQualities?.get(0))
    }

    @Test
    fun testParseMetadataPhotoPost() {
        val json = """
            {
                "title": "TikTok Карусель",
                "thumbnail": "https://photo.tiktok.com/1.jpg",
                "is_photo": true,
                "is_playlist": false
            }
        """.trimIndent()

        val meta = AnalysisManager.parseMetadataFromJson(json)
        assertNotNull(meta)
        assertEquals("TikTok Карусель", meta?.title)
        assertTrue(meta?.isPhotoPost ?: false)
    }

    @Test
    fun testParseMetadataPlaylist() {
        val json = """
            {
                "title": "Плейлист с клипами",
                "thumbnail": "https://img.youtube.com/playlist.jpg",
                "is_photo": false,
                "is_playlist": true,
                "playlist_entries": [
                    {
                        "id": "vid1",
                        "title": "Трек 1",
                        "url": "https://youtube.com/watch?v=vid1",
                        "thumbnail": "https://img.youtube.com/vid1.jpg",
                        "duration": "03:45",
                        "author": "Музыкант"
                    },
                    {
                        "id": "vid2",
                        "title": "Трек 2",
                        "url": "https://youtube.com/watch?v=vid2",
                        "thumbnail": "https://img.youtube.com/vid2.jpg",
                        "duration": "04:10",
                        "author": "Музыкант"
                    }
                ]
            }
        """.trimIndent()

        val meta = AnalysisManager.parseMetadataFromJson(json)
        assertNotNull(meta)
        assertTrue(meta?.isPlaylist ?: false)
        assertEquals(2, meta?.playlistEntries?.size)
        assertEquals("vid1", meta?.playlistEntries?.get(0)?.id)
        assertEquals("Трек 1", meta?.playlistEntries?.get(0)?.title)
        assertEquals("03:45", meta?.playlistEntries?.get(0)?.duration)
    }

    @Test
    fun testParseMalformedJson() {
        val invalidJson = "{ invalid_json_string }"
        val meta = AnalysisManager.parseMetadataFromJson(invalidJson)
        assertNull(meta)
    }
}
