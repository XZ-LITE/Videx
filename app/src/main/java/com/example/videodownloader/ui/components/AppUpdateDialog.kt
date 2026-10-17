package com.example.videodownloader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import com.example.videodownloader.logic.AppUpdateChecker
import com.example.videodownloader.logic.AppUpdateInfo
import kotlinx.coroutines.launch

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onInstallStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadSpeedBytes by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isDownloading) {
                    // Состояние загрузки
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Загрузка Videx v${updateInfo.latestVersion}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Карточка состояния загрузки
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val percent = (downloadProgress * 100).toInt().coerceIn(0, 100)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Скачивание файла...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val loadedStr = com.example.videodownloader.HistoryManager.formatBytes(downloadedBytes)
                                val totalStr = if (totalBytes > 0) com.example.videodownloader.HistoryManager.formatBytes(totalBytes) else "..."
                                val speedStr = if (downloadSpeedBytes > 0) "${com.example.videodownloader.HistoryManager.formatBytes(downloadSpeedBytes)}/с" else "..."

                                Text(
                                    text = "$loadedStr / $totalStr",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = speedStr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = {
                                AppUpdateChecker.cancelCurrentDownload()
                                isDownloading = false
                                errorMessage = null
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(42.dp)
                        ) {
                            Text("Отмена", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                } else {
                    // Анонс обновления
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Новая версия!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "v${updateInfo.latestVersion}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "доступна на GitHub",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Что нового в этой версии:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val codeBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            val annotatedNotes = remember(updateInfo.releaseNotes) {
                                parseMarkdownToAnnotatedString(
                                    if (updateInfo.releaseNotes.isNotBlank()) updateInfo.releaseNotes else "• Улучшения производительности и исправления ошибок.",
                                    primaryColor,
                                    codeBgColor
                                )
                            }
                            
                            Text(
                                text = annotatedNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Позже", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                isDownloading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val res = AppUpdateChecker.downloadAndInstallApk(
                                        context,
                                        updateInfo.downloadUrl,
                                        updateInfo.latestVersion
                                    ) { prog, loaded, total, speed ->
                                        downloadProgress = prog
                                        downloadedBytes = loaded
                                        totalBytes = total
                                        downloadSpeedBytes = speed
                                    }

                                    if (res.isSuccess) {
                                        val apkFile = res.getOrThrow()
                                        AppUpdateChecker.promptInstallApk(context, apkFile)
                                        onInstallStarted()
                                        onDismiss()
                                    } else {
                                        isDownloading = false
                                        val err = res.exceptionOrNull()?.message
                                        if (err != "Загрузка отменена") {
                                            errorMessage = err ?: "Не удалось скачать файл обновления"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Обновить", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Парсер простого Markdown для красивого отображения Release Notes с GitHub.
 * Поддерживает жирный шрифт (**bold**), ссылки ([text](url)) и блоки кода (`code`).
 */
fun parseMarkdownToAnnotatedString(text: String, primaryColor: Color, codeBgColor: Color): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        for ((index, line) in lines.withIndex()) {
            var currentLine = line
            var isHeader = false

            if (currentLine.startsWith("### ")) {
                currentLine = currentLine.removePrefix("### ")
                isHeader = true
            } else if (currentLine.startsWith("## ")) {
                currentLine = currentLine.removePrefix("## ")
                isHeader = true
            } else if (currentLine.startsWith("# ")) {
                currentLine = currentLine.removePrefix("# ")
                isHeader = true
            }

            val lineStyle = if (isHeader) {
                SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = primaryColor)
            } else {
                SpanStyle()
            }

            withStyle(lineStyle) {
                var i = 0
                while (i < currentLine.length) {
                    // Парсинг жирного текста (**text**)
                    if (currentLine.startsWith("**", i)) {
                        val end = currentLine.indexOf("**", i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(currentLine.substring(i + 2, end))
                            }
                            i = end + 2
                            continue
                        }
                    }
                    // Парсинг блоков кода (`text`)
                    if (currentLine.startsWith("`", i)) {
                        val end = currentLine.indexOf("`", i + 1)
                        if (end != -1) {
                            withStyle(SpanStyle(background = codeBgColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)) {
                                append(currentLine.substring(i + 1, end))
                            }
                            i = end + 1
                            continue
                        }
                    }
                    // Парсинг ссылок [text](url)
                    if (currentLine.startsWith("[", i)) {
                        val closeBracket = currentLine.indexOf("]", i + 1)
                        if (closeBracket != -1 && currentLine.length > closeBracket + 1 && currentLine[closeBracket + 1] == '(') {
                            val closeParen = currentLine.indexOf(")", closeBracket + 2)
                            if (closeParen != -1) {
                                val linkText = currentLine.substring(i + 1, closeBracket)
                                withStyle(SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)) {
                                    append(linkText)
                                }
                                i = closeParen + 1
                                continue
                            }
                        }
                    }
                    append(currentLine[i])
                    i++
                }
            }
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}
