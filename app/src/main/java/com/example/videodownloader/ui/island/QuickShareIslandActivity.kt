package com.example.videodownloader.ui.island

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.example.videodownloader.MainActivity
import com.example.videodownloader.SettingsManager
import com.example.videodownloader.logic.island.IslandOverlayService
import com.example.videodownloader.utils.extractUrlFromText
import com.example.videodownloader.utils.isValidUrl

/**
 * Невидимая транзитная активность для перехвата "Поделиться" из других приложений (TikTok, Reels, Shorts).
 * Мгновенно передает ссылку в плавающий Dynamic Island и завершается с нулевой анимацией,
 * оставляя пользователя в просматриваемой ленте.
 */
class QuickShareIslandActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)
        finishWithoutAnimation()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finishWithoutAnimation()
    }

    private fun handleIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action == Intent.ACTION_SEND && incomingIntent.type == "text/plain") {
            val rawText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val cleanUrl = extractUrlFromText(rawText)

            if (cleanUrl.isNotBlank() && isValidUrl(cleanUrl)) {
                if (Settings.canDrawOverlays(this) && SettingsManager.islandEnabled.value) {
                    // Активируем парящий Dynamic Island
                    IslandOverlayService.showIslandForUrl(this, cleanUrl)
                } else {
                    // Если разрешение не выдано или островок выключен — открываем обычный Videx
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, rawText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(mainIntent)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun finishWithoutAnimation() {
        finish()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }
}
