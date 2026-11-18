package com.example.videodownloader.ui.island

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.videodownloader.SettingsManager

sealed interface IslandState {
    object Hidden : IslandState

    data class ExpandedPrompt(
        val url: String,
        val title: String,
        val thumbnail: String?,
        val platform: String,
        val isAnalyzing: Boolean = false,
        val timeoutProgress: Float = 1.0f
    ) : IslandState

    data class Downloading(
        val url: String,
        val title: String,
        val progress: Int,
        val speed: String,
        val isAudio: Boolean,
        val isTransit: Boolean
    ) : IslandState

    data class Success(
        val title: String,
        val isAudio: Boolean,
        val isTransit: Boolean,
        val message: String = "Готово"
    ) : IslandState

    data class Error(
        val message: String
    ) : IslandState
}

@Composable
fun DynamicIslandContainer(
    state: IslandState,
    onDownloadVideo: (String) -> Unit,
    onDownloadAudio: (String) -> Unit,
    onDownloadTransit: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state is IslandState.Hidden) return

    val view = LocalView.current
    val playHaptic = {
        if (SettingsManager.islandVibration.value) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    // Spring physics configuration (Apple Dynamic Island bouncy feeling)
    val islandSpring = spring<Dp>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val targetWidth = when (state) {
        is IslandState.Hidden -> 0.dp
        is IslandState.ExpandedPrompt -> 360.dp
        is IslandState.Downloading -> 220.dp
        is IslandState.Success -> 180.dp
        is IslandState.Error -> 260.dp
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = islandSpring,
        label = "IslandWidth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 12.dp, end = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .width(animatedWidth)
                .wrapContentHeight()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color(0x66000000),
                    ambientColor = Color(0x33000000)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xF20B0E14))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x406366F1),
                            Color(0x22FFFFFF),
                            Color(0x308B5CF6)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        // Свайп вверх для мгновенного закрытия
                        if (dragAmount < -15) {
                            playHaptic()
                            onDismiss()
                        }
                    }
                },
            color = Color.Transparent
        ) {
            when (state) {
                is IslandState.ExpandedPrompt -> {
                    IslandExpandedCard(
                        prompt = state,
                        onVideoClick = {
                            playHaptic()
                            onDownloadVideo(state.url)
                        },
                        onAudioClick = {
                            playHaptic()
                            onDownloadAudio(state.url)
                        },
                        onTransitClick = {
                            playHaptic()
                            onDownloadTransit(state.url)
                        },
                        onDismissClick = {
                            playHaptic()
                            onDismiss()
                        }
                    )
                }

                is IslandState.Downloading -> {
                    IslandDownloadingPill(
                        downloading = state,
                        onCancelClick = {
                            playHaptic()
                            onCancelDownload()
                        }
                    )
                }

                is IslandState.Success -> {
                    IslandSuccessBadge(success = state)
                }

                is IslandState.Error -> {
                    IslandErrorBadge(
                        error = state,
                        onDismiss = {
                            playHaptic()
                            onDismiss()
                        }
                    )
                }

                is IslandState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun IslandExpandedCard(
    prompt: IslandState.ExpandedPrompt,
    onVideoClick: () -> Unit,
    onAudioClick: () -> Unit,
    onTransitClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Swipe Handle Bar
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Content Row: Thumbnail + Info + Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Platform Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2533)),
                contentAlignment = Alignment.Center
            ) {
                if (!prompt.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(prompt.thumbnail)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (prompt.isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Metadata Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Platform tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (prompt.platform.lowercase()) {
                                    "tiktok" -> Color(0xFFFE2C55)
                                    "instagram" -> Color(0xFFE1306C)
                                    "youtube" -> Color(0xFFFF0000)
                                    else -> Color(0xFF2563EB)
                                }
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = prompt.platform.uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    if (prompt.isAnalyzing) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Анализ ссылки...",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = prompt.title.ifBlank { "Медиа из ${prompt.platform}" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Close button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Закрыть",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons Row: [🎬 MP4] [🎵 MP3] [⚡ Транзит]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Button 1: MP4 Video
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF2563EB), Color(0xFF4F46E5))
                        )
                    )
                    .clickable(onClick = onVideoClick),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "MP4",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Button 2: MP3 Audio
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2638))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    .clickable(onClick = onAudioClick),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFA5B4FC),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "MP3",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Button 3: Transit
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0891B2), Color(0xFF2563EB))
                        )
                    )
                    .clickable(onClick = onTransitClick),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = Color(0xFF67E8F9),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Транзит",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Timeout progress line
        if (prompt.timeoutProgress > 0f) {
            LinearProgressIndicator(
                progress = { prompt.timeoutProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape),
                color = Color(0xFF6366F1),
                trackColor = Color(0x1AFFFFFF),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun IslandDownloadingPill(
    downloading: IslandState.Downloading,
    onCancelClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Circular progress indicator with percentage
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center
        ) {
            if (downloading.progress > 0) {
                CircularProgressIndicator(
                    progress = { downloading.progress / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0x22FFFFFF),
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = "${downloading.progress}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0x22FFFFFF)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Center: Speed & Title
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (downloading.speed.isNotBlank()) downloading.speed else "Загрузка...",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = if (downloading.isTransit) "⚡ Режим транзита" else (if (downloading.isAudio) "🎵 Аудио" else "🎬 Видео"),
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Cancel Button
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancelClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Отмена",
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun IslandSuccessBadge(
    success: IslandState.Success
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xF2062517))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (success.isTransit) "Отправка в мессенджер..." else "Сохранено!",
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IslandErrorBadge(
    error: IslandState.Error,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xF2300D15))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = error.message,
            color = Color(0xFFFCA5A5),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
