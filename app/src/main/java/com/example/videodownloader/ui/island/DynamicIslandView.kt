package com.example.videodownloader.ui.island

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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

    // Эталонная пружинная физика Apple Spring
    val islandSpring = spring<Dp>(
        dampingRatio = 0.74f,
        stiffness = Spring.StiffnessMediumLow
    )

    val targetWidth = when (state) {
        is IslandState.Hidden -> 0.dp
        is IslandState.ExpandedPrompt -> 350.dp
        is IslandState.Downloading -> 205.dp
        is IslandState.Success -> 175.dp
        is IslandState.Error -> 220.dp
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
                    elevation = 20.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = Color.Black.copy(alpha = 0.7f),
                    ambientColor = Color.Black.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFA0A0C10))
                .border(
                    BorderStroke(0.6.dp, Color.White.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(28.dp)
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        // Свайп вверх для мгновенного скрытия
                        if (dragAmount < -12) {
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ряд 1: Превью + Метаданные + Кнопка закрытия
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Превью ролика
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp)),
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
                        imageVector = when {
                            prompt.platform.contains("YouTube", ignoreCase = true) -> Icons.Default.PlayArrow
                            prompt.platform.contains("TikTok", ignoreCase = true) -> Icons.Default.MusicNote
                            prompt.platform.contains("Instagram", ignoreCase = true) -> Icons.Default.PhotoLibrary
                            else -> Icons.Default.Link
                        },
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (prompt.isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Текстовый блок
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${prompt.platform.uppercase()} • ${if (prompt.isAnalyzing) "АНАЛИЗ..." else "МЕДИА"}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = prompt.title.ifBlank { "Медиа из ${prompt.platform}" },
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Кнопка закрытия
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Ряд 2: Три тактильные кнопки-пилюли
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Кнопка 1: MP4 Видео (Основной акцент)
            Surface(
                modifier = Modifier
                    .weight(1.1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onVideoClick),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "MP4",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Кнопка 2: MP3 Аудио
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onAudioClick),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "MP3",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Кнопка 3: Транзит
            Surface(
                modifier = Modifier
                    .weight(1.1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onTransitClick),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Транзит",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Ряд 3: Тонкая полоска таймаута автозакрытия
        if (prompt.timeoutProgress > 0f) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { prompt.timeoutProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.08f),
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
            .height(42.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Круговой индикатор прогресса
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (downloading.progress > 0) {
                CircularProgressIndicator(
                    progress = { downloading.progress / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Скорость и состояние
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (downloading.speed.isNotBlank()) downloading.speed else "Загрузка...",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "${downloading.progress}% • ${if (downloading.isTransit) "Транзит" else if (downloading.isAudio) "Аудио" else "Видео"}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.5.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Кнопка отмены
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancelClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Отмена",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp)
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
            .height(42.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF34D399),
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (success.isTransit) "Отправка ✈️" else "Сохранено ✓",
            color = Color.White,
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
            .height(42.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFF87171),
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = error.message,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
