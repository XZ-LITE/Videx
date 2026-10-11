package com.example.videodownloader

import com.example.videodownloader.utils.detectPlatformName
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDetectionTest {

    @Test
    fun testYouTubePlatformDetection() {
        assertEquals("YouTube", detectPlatformName("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("YouTube", detectPlatformName("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("YouTube", detectPlatformName("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("YouTube", detectPlatformName("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun testTikTokPlatformDetection() {
        assertEquals("TikTok", detectPlatformName("https://www.tiktok.com/@user/video/1234567890123456789"))
        assertEquals("TikTok", detectPlatformName("https://vt.tiktok.com/ZSN12345/"))
        assertEquals("TikTok", detectPlatformName("https://vm.tiktok.com/ZSN12345/"))
    }

    @Test
    fun testInstagramPlatformDetection() {
        assertEquals("Instagram", detectPlatformName("https://www.instagram.com/reel/C12345678/"))
        assertEquals("Instagram", detectPlatformName("https://instagram.com/p/C12345678/"))
        assertEquals("Instagram", detectPlatformName("https://www.instagram.com/tv/C12345678/"))
    }

    @Test
    fun testVkPlatformDetection() {
        assertEquals("VK Видео", detectPlatformName("https://vk.com/video-123456_7890"))
        assertEquals("VK Видео", detectPlatformName("https://vkvideo.ru/video-123456_7890"))
        assertEquals("VK Видео", detectPlatformName("https://vk.com/clip-123456_7890"))
    }

    @Test
    fun testOtherPlatformsDetection() {
        assertEquals("Pinterest", detectPlatformName("https://pin.it/1234567"))
        assertEquals("Pinterest", detectPlatformName("https://www.pinterest.com/pin/1234567/"))
        assertEquals("X (Twitter)", detectPlatformName("https://x.com/user/status/123456"))
        assertEquals("Reddit", detectPlatformName("https://www.reddit.com/r/video/comments/123"))
        assertEquals("Медиа-поток", detectPlatformName("https://unknown-website.org/file.mp4"))
    }
}
