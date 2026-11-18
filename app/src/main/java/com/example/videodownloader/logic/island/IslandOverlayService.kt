package com.example.videodownloader.logic.island

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.videodownloader.AsyncLogger
import com.example.videodownloader.DownloadWorker
import com.example.videodownloader.LogLevel
import com.example.videodownloader.SettingsManager
import com.example.videodownloader.logic.AnalysisManager
import com.example.videodownloader.logic.DownloadManager
import com.example.videodownloader.ui.theme.VideoDownloaderTheme
import com.example.videodownloader.ui.island.DynamicIslandContainer
import com.example.videodownloader.ui.island.IslandState
import com.example.videodownloader.utils.detectPlatformName
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class IslandOverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.example.videodownloader.action.ISLAND_SHOW"
        const val ACTION_HIDE = "com.example.videodownloader.action.ISLAND_HIDE"
        const val ACTION_TEST = "com.example.videodownloader.action.ISLAND_TEST"
        const val EXTRA_URL = "extra_url"

        fun showIslandForUrl(context: Context, url: String) {
            if (!Settings.canDrawOverlays(context) || !SettingsManager.islandEnabled.value) {
                return
            }
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_URL, url)
            }
            context.startService(intent)
        }

        fun showTestIsland(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_TEST
            }
            context.startService(intent)
        }

        fun hideIsland(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private val lifecycleOwner = ServiceLifecycleOwner()

    private val _islandState = MutableStateFlow<IslandState>(IslandState.Hidden)
    val islandState = _islandState.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timeoutJob: Job? = null
    private var downloadObservationJob: Job? = null
    private var analysisJob: Job? = null

    private var currentActiveUrl: String = ""
    private var isViewAttached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        lifecycleOwner.onStart()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: ""
                if (url.isNotBlank()) {
                    displayIslandForUrl(url)
                } else {
                    stopSelf()
                }
            }
            ACTION_TEST -> {
                displayTestIsland()
            }
            ACTION_HIDE -> {
                hideAndStop()
            }
            else -> {
                hideAndStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureOverlayAttached() {
        if (isViewAttached && composeView != null) return

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)

            setContent {
                val appTheme by SettingsManager.appTheme.collectAsState()
                val dynamicColors by SettingsManager.useDynamicColors.collectAsState()

                VideoDownloaderTheme(
                    appTheme = appTheme,
                    dynamicColor = dynamicColors
                ) {
                    val state by _islandState.collectAsState()
                    DynamicIslandContainer(
                        state = state,
                        onDownloadVideo = { url -> startDownload(url, isAudio = false, isTransit = false) },
                        onDownloadAudio = { url -> startDownload(url, isAudio = true, isTransit = false) },
                        onDownloadTransit = { url -> startDownload(url, isAudio = false, isTransit = true) },
                        onCancelDownload = { cancelCurrentDownload() },
                        onDismiss = { hideAndStop() }
                    )
                }
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 12
        }

        try {
            windowManager?.addView(view, params)
            composeView = view
            isViewAttached = true
        } catch (e: Exception) {
            AsyncLogger.log(LogLevel.ERROR, "Ошибка отображения Dynamic Island: ${e.message}")
        }
    }

    private fun displayIslandForUrl(url: String) {
        currentActiveUrl = url
        val platform = detectPlatformName(url)

        ensureOverlayAttached()

        _islandState.value = IslandState.ExpandedPrompt(
            url = url,
            title = "Загрузка информации...",
            thumbnail = null,
            platform = platform,
            isAnalyzing = true,
            timeoutProgress = 1.0f
        )

        // Запуск таймера автозакрытия
        startTimeoutTimer(SettingsManager.islandTimeoutSec.value)

        // Анализ ссылки
        analysisJob?.cancel()
        analysisJob = serviceScope.launch {
            AnalysisManager.setUrl(url, autoAnalyze = true)
            // Отслеживаем обновление названия и обложки
            AnalysisManager.resultTitle.collect { title ->
                val thumb = AnalysisManager.resultThumbnail.value
                val isAnalyzing = AnalysisManager.isAnalyzing.value
                val currentState = _islandState.value
                if (currentState is IslandState.ExpandedPrompt && currentState.url == url) {
                    _islandState.value = currentState.copy(
                        title = title.ifBlank { "Медиа из $platform" },
                        thumbnail = thumb.ifBlank { null },
                        isAnalyzing = isAnalyzing
                    )
                }
            }
        }
    }

    private fun displayTestIsland() {
        ensureOverlayAttached()
        _islandState.value = IslandState.ExpandedPrompt(
            url = "https://www.tiktok.com/@test/video/123",
            title = "🔥 Трендовый ролик Videx (Демо-превью)",
            thumbnail = null,
            platform = "TikTok",
            isAnalyzing = false,
            timeoutProgress = 1.0f
        )
        startTimeoutTimer(SettingsManager.islandTimeoutSec.value)
    }

    private fun startTimeoutTimer(seconds: Int) {
        timeoutJob?.cancel()
        val totalMs = (seconds.coerceAtLeast(3)) * 1000L
        val intervalMs = 50L

        timeoutJob = serviceScope.launch {
            var elapsed = 0L
            while (elapsed < totalMs) {
                delay(intervalMs)
                elapsed += intervalMs
                val progress = 1.0f - (elapsed.toFloat() / totalMs)
                val currentState = _islandState.value
                if (currentState is IslandState.ExpandedPrompt) {
                    _islandState.value = currentState.copy(timeoutProgress = progress)
                } else {
                    break
                }
            }
            if (_islandState.value is IslandState.ExpandedPrompt) {
                hideAndStop()
            }
        }
    }

    private fun startDownload(url: String, isAudio: Boolean, isTransit: Boolean) {
        timeoutJob?.cancel()
        analysisJob?.cancel()

        val prompt = _islandState.value as? IslandState.ExpandedPrompt
        val title = prompt?.title ?: "Медиа"
        val thumb = prompt?.thumbnail

        _islandState.value = IslandState.Downloading(
            url = url,
            title = title,
            progress = 0,
            speed = "Старт...",
            isAudio = isAudio,
            isTransit = isTransit
        )

        // Запуск через глобальный DownloadManager
        DownloadManager.startDownload(
            context = this@IslandOverlayService,
            url = url,
            title = title,
            isAudio = isAudio,
            thumbnailUrl = thumb,
            isAutopilot = isTransit
        )

        // Наблюдение за прогрессом загрузки
        observeDownloadProgress(url, title, isAudio, isTransit)
    }

    private fun observeDownloadProgress(url: String, title: String, isAudio: Boolean, isTransit: Boolean) {
        downloadObservationJob?.cancel()
        downloadObservationJob = serviceScope.launch {
            var hasStarted = false

            DownloadManager.activeTasks.collect { tasks ->
                val matchingTask = tasks.find { it.url == url || it.url == "url_hash_${url.hashCode()}" }

                if (matchingTask != null) {
                    hasStarted = true
                    val currentProgress = matchingTask.progress
                    val currentSpeed = matchingTask.speed

                    _islandState.value = IslandState.Downloading(
                        url = url,
                        title = title,
                        progress = currentProgress,
                        speed = currentSpeed,
                        isAudio = isAudio,
                        isTransit = isTransit
                    )
                } else if (hasStarted) {
                    // Задача завершилась (пропала из activeTasks)
                    onDownloadComplete(title, isAudio, isTransit)
                    cancel()
                }
            }
        }
    }

    private fun onDownloadComplete(title: String, isAudio: Boolean, isTransit: Boolean) {
        _islandState.value = IslandState.Success(
            title = title,
            isAudio = isAudio,
            isTransit = isTransit
        )

        serviceScope.launch {
            if (isTransit) {
                delay(600)
                shareTransitFile()
            }
            delay(2400)
            hideAndStop()
        }
    }

    private fun shareTransitFile() {
        try {
            // Открываем общий диалог отправки в мессенджеры для последнего скачанного файла
            val downloadsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Videx")
            val latestFile = downloadsDir.listFiles()?.maxByOrNull { it.lastModified() }
            if (latestFile != null && latestFile.exists()) {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", latestFile)
                val mimeType = if (latestFile.extension.lowercase() in listOf("mp3", "m4a", "ogg")) "audio/*" else "video/*"

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(shareIntent, "Отправить через").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(chooser)
            }
        } catch (e: Exception) {
            AsyncLogger.log(LogLevel.WARN, "Ошибка транзитного шаринга: ${e.message}")
        }
    }

    private fun cancelCurrentDownload() {
        // Отмена через WorkManager
        try {
            val workManager = WorkManager.getInstance(applicationContext)
            workManager.cancelAllWorkByTag("url_hash_${currentActiveUrl.hashCode()}")
        } catch (_: Exception) {}
        hideAndStop()
    }

    private fun hideAndStop() {
        timeoutJob?.cancel()
        downloadObservationJob?.cancel()
        analysisJob?.cancel()
        _islandState.value = IslandState.Hidden

        if (isViewAttached && composeView != null) {
            try {
                windowManager?.removeView(composeView)
            } catch (_: Exception) {}
            isViewAttached = false
            composeView = null
        }
        stopSelf()
    }

    override fun onDestroy() {
        hideAndStop()
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
        lifecycleOwner.onDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }
}
