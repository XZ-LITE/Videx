package com.example.videodownloader.logic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.videodownloader.AsyncLogger
import com.example.videodownloader.AudioControlBus
import com.example.videodownloader.DownloadedFileItem
import com.example.videodownloader.LogLevel
import com.example.videodownloader.MainActivity
import com.example.videodownloader.MediaType
import com.example.videodownloader.NotificationReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object AudioPlaybackManager {

    private const val CHANNEL_ID = "audio_player_playback_channel"
    private const val NOTIFICATION_ID = 2002

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var appContext: Context? = null
    private var lastPrevClickTime = 0L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

    private val _currentTrack = MutableStateFlow<DownloadedFileItem?>(null)
    val currentTrack: StateFlow<DownloadedFileItem?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playlist = MutableStateFlow<List<DownloadedFileItem>>(emptyList())
    val playlist: StateFlow<List<DownloadedFileItem>> = _playlist.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isRepeatOne = MutableStateFlow(false)
    val isRepeatOne: StateFlow<Boolean> = _isRepeatOne.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) {
                startPositionTracker()
            } else {
                stopPositionTracker()
            }
            updateNotification()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                val d = exoPlayer?.duration ?: 0L
                if (d > 0L) _durationMs.value = d
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            if (!id.isNullOrBlank()) {
                val found = _playlist.value.find { it.id == id }
                if (found != null) {
                    _currentTrack.value = found
                }
            }
            updateNotification()
        }
    }

    private fun startPositionTracker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                    val d = player.duration
                    if (d > 0L) _durationMs.value = d
                }
                delay(250)
            }
        }
    }

    private fun stopPositionTracker() {
        positionJob?.cancel()
        exoPlayer?.let { player ->
            _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun getMediaSession(context: Context): MediaSession? {
        ensureInitialized(context)
        return mediaSession
    }

    private fun ensureInitialized(context: Context) {
        if (exoPlayer != null) return
        val app = context.applicationContext
        appContext = app

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(app)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build().apply {
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
                addListener(playerListener)
            }

        exoPlayer = player

        mediaSession = MediaSession.Builder(app, player)
            .setId("VidexAudioMediaSession")
            .build()

        AudioControlBus.onPrevTrack = { playPreviousOrRestart() }
        AudioControlBus.onPlayPause = { playPause() }
        AudioControlBus.onNextTrack = { playNext() }
    }

    fun playTrack(context: Context, item: DownloadedFileItem, playlist: List<DownloadedFileItem>) {
        ensureInitialized(context)
        val app = context.applicationContext
        appContext = app

        try {
            val serviceIntent = Intent(app, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(serviceIntent)
            } else {
                app.startService(serviceIntent)
            }
        } catch (e: Exception) {
            AsyncLogger.log(LogLevel.WARN, "PlaybackService start fail: ${e.message}")
        }

        _playlist.value = playlist

        // Если этот трек уже загружен в плеер и играет или на паузе — не сбрасываем позицию
        if (_currentTrack.value?.id == item.id && exoPlayer?.playbackState != Player.STATE_IDLE) {
            if (exoPlayer?.isPlaying != true) {
                exoPlayer?.play()
            }
            return
        }

        val initialIndex = playlist.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        val mediaItems = playlist.map { audioItem -> buildMediaItem(audioItem) }

        exoPlayer?.apply {
            setMediaItems(mediaItems, initialIndex, 0L)
            prepare()
            playWhenReady = true
        }

        _currentTrack.value = item
        _positionMs.value = 0L
        _durationMs.value = item.durationMs
    }

    private fun buildMediaItem(audioItem: DownloadedFileItem): MediaItem {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(audioItem.name)
            .setArtist("Videx")
            .setArtworkUri(
                audioItem.thumbnailUrl?.takeIf { it.isNotBlank() }?.let {
                    if (it.startsWith("http://") || it.startsWith("https://")) Uri.parse(it)
                    else Uri.fromFile(File(it))
                } ?: if (audioItem.pathOrUri.startsWith("content://")) audioItem.pathOrUri.toUri() else Uri.fromFile(File(audioItem.pathOrUri))
            )
            .build()

        val uri = if (audioItem.pathOrUri.startsWith("content://")) {
            audioItem.pathOrUri.toUri()
        } else {
            Uri.fromFile(File(audioItem.pathOrUri))
        }

        return MediaItem.Builder()
            .setMediaId(audioItem.id)
            .setUri(uri)
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    fun playPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun playPreviousOrRestart() {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition
        val now = System.currentTimeMillis()
        if (currentPos > 3000L && (now - lastPrevClickTime > 2000L)) {
            player.seekTo(0L)
        } else {
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else {
                player.seekTo(0L)
            }
        }
        lastPrevClickTime = now
    }

    fun playNext() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            player.seekTo(0, 0L)
        }
    }

    fun seekTo(posMs: Long) {
        exoPlayer?.seekTo(posMs)
        _positionMs.value = posMs
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun toggleRepeat() {
        val newRepeat = !_isRepeatOne.value
        _isRepeatOne.value = newRepeat
        exoPlayer?.repeatMode = if (newRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun updatePlaylistIfNeeded(newPlaylist: List<DownloadedFileItem>) {
        val player = exoPlayer ?: return
        if (player.mediaItemCount != newPlaylist.size && newPlaylist.isNotEmpty()) {
            _playlist.value = newPlaylist
            val currentId = _currentTrack.value?.id
            val currentIndex = newPlaylist.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            val mediaItems = newPlaylist.map { buildMediaItem(it) }
            player.setMediaItems(mediaItems, currentIndex, player.currentPosition)
        }
    }

    fun onTrackDeleted(deletedItem: DownloadedFileItem) {
        val currentList = _playlist.value.filter { it.id != deletedItem.id }
        _playlist.value = currentList
        if (_currentTrack.value?.id == deletedItem.id) {
            if (currentList.isNotEmpty()) {
                playNext()
            } else {
                stop()
            }
        }
    }

    fun stop() {
        positionJob?.cancel()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _currentTrack.value = null
        _isPlaying.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L

        appContext?.let { ctx ->
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                nm.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
        }
    }

    fun release() {
        stop()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        AudioControlBus.onPrevTrack = null
        AudioControlBus.onPlayPause = null
        AudioControlBus.onNextTrack = null
    }

    private fun updateNotification() {
        val ctx = appContext ?: return
        val currentItem = _currentTrack.value ?: return
        val session = mediaSession ?: return
        val playing = _isPlaying.value

        try {
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Аудиопроигрыватель",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Управление воспроизведением аудио"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                ctx, 8888, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            @Suppress("DEPRECATION")
            val token = session.sessionCompatToken

            val prevIntent = Intent(ctx, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_AUDIO_PREV
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                ctx, 8001, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val playPauseIntent = Intent(ctx, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_AUDIO_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                ctx, 8002, playPauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val nextIntent = Intent(ctx, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_AUDIO_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                ctx, 8003, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notifBuilder = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(currentItem.name)
                .setContentText("${currentItem.sizeFormatted} • Videx")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentPendingIntent)
                .addAction(android.R.drawable.ic_media_previous, "Предыдущий", prevPendingIntent)
                .addAction(
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing) "Пауза" else "Воспроизведение",
                    playPausePendingIntent
                )
                .addAction(android.R.drawable.ic_media_next, "Следующий", nextPendingIntent)
                .setOngoing(playing)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(
                    MediaNotificationCompat.MediaStyle()
                        .setMediaSession(token)
                        .setShowActionsInCompactView(0, 1, 2)
                )

            notificationManager.notify(NOTIFICATION_ID, notifBuilder.build())
        } catch (e: Exception) {
            AsyncLogger.log(LogLevel.WARN, "Media notification error: ${e.message}")
        }
    }
}
