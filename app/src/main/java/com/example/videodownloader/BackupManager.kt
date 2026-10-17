package com.example.videodownloader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val BACKUP_VERSION = 1

    /**
     * Создает резервную копию всех настроек и истории в формате JSON
     * и записывает в указанный URI (через SAF).
     */
    suspend fun exportData(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val historyDao = db.historyDao()
            val allHistory = historyDao.getAll()
            
            val prefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

            val rootJson = JSONObject()
            rootJson.put("version", BACKUP_VERSION)
            rootJson.put("timestamp", System.currentTimeMillis())
            rootJson.put("date_created", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

            // 1. Экспорт настроек (SharedPreferences) с жесткой типизацией
            val settingsJson = JSONObject()
            val booleans = JSONObject()
            val ints = JSONObject()
            val longs = JSONObject()
            val floats = JSONObject()
            val strings = JSONObject()
            val stringSets = JSONObject()

            for ((key, value) in prefs.all) {
                when (value) {
                    is Boolean -> booleans.put(key, value)
                    is Int -> ints.put(key, value)
                    is Long -> longs.put(key, value)
                    is Float -> floats.put(key, value.toDouble())
                    is String -> strings.put(key, value)
                    is Set<*> -> {
                        val array = JSONArray()
                        value.forEach { if (it is String) array.put(it) }
                        stringSets.put(key, array)
                    }
                }
            }
            settingsJson.put("booleans", booleans)
            settingsJson.put("ints", ints)
            settingsJson.put("longs", longs)
            settingsJson.put("floats", floats)
            settingsJson.put("strings", strings)
            settingsJson.put("stringSets", stringSets)
            rootJson.put("settings", settingsJson)

            // 2. Экспорт истории
            val historyArray = JSONArray()
            for (item in allHistory) {
                val itemJson = JSONObject()
                itemJson.put("id", item.id)
                itemJson.put("pathOrUri", item.pathOrUri)
                itemJson.put("name", item.name)
                itemJson.put("sizeBytes", item.sizeBytes)
                itemJson.put("sizeFormatted", item.sizeFormatted)
                itemJson.put("dateFormatted", item.dateFormatted)
                itemJson.put("mediaType", item.mediaType.name)
                itemJson.put("sourceUrl", item.sourceUrl)
                if (item.thumbnailUrl != null) {
                    itemJson.put("thumbnailUrl", item.thumbnailUrl)
                }
                itemJson.put("durationMs", item.durationMs)
                itemJson.put("timestamp", item.timestamp)
                itemJson.put("isMissing", item.isMissing)
                
                historyArray.put(itemJson)
            }
            rootJson.put("history", historyArray)

            // Запись в файл
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(rootJson.toString(2))
                }
            } ?: throw Exception("Не удалось открыть файл для записи")

            Result.success(Unit)
        } catch (e: Exception) {
            AsyncLogger.log(LogLevel.ERROR, "Ошибка экспорта бэкапа: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Восстанавливает настройки и историю из выбранного JSON-файла.
     */
    suspend fun importData(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // Защита от OOM: ограничение размера файла бэкапа до 15 МБ
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val fileSize = pfd?.statSize ?: 0L
            pfd?.close()

            if (fileSize > 15 * 1024 * 1024) {
                throw IllegalArgumentException("Файл слишком большой (> 15 МБ). Выберите корректный файл резервной копии Videx.")
            }

            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("Не удалось прочитать файл")

            val rootJson = JSONObject(jsonString)
            
            // Проверка версии
            val version = rootJson.optInt("version", 0)
            if (version == 0) throw Exception("Некорректный формат файла резервной копии")

            // 1. Импорт настроек с жесткой проверкой типов (Защита от ClassCastException)
            val settingsJson = rootJson.optJSONObject("settings")
            if (settingsJson != null) {
                val prefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                
                settingsJson.optJSONObject("booleans")?.let { b ->
                    b.keys().forEach { editor.putBoolean(it, b.getBoolean(it)) }
                }
                settingsJson.optJSONObject("ints")?.let { i ->
                    i.keys().forEach { editor.putInt(it, i.getInt(it)) }
                }
                settingsJson.optJSONObject("longs")?.let { l ->
                    l.keys().forEach { editor.putLong(it, l.getLong(it)) }
                }
                settingsJson.optJSONObject("floats")?.let { f ->
                    f.keys().forEach { editor.putFloat(it, f.getDouble(it).toFloat()) }
                }
                settingsJson.optJSONObject("strings")?.let { s ->
                    s.keys().forEach { editor.putString(it, s.getString(it)) }
                }
                settingsJson.optJSONObject("stringSets")?.let { sets ->
                    sets.keys().forEach { key ->
                        val arr = sets.getJSONArray(key)
                        val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
                        editor.putStringSet(key, set)
                    }
                }
                
                // Защита прав SAF при импорте папки назначения
                val persistedUris = context.contentResolver.persistedUriPermissions.map { it.uri.toString() }
                val customDir = prefs.getString("custom_dir_uri", null)
                if (customDir != null && customDir !in persistedUris) {
                    editor.remove("custom_dir_uri")
                    editor.remove("custom_dir_name")
                    AsyncLogger.log(LogLevel.WARN, "Сброшены права SAF на сохраненную папку загрузок.")
                }

                editor.commit() // Синхронное сохранение
                
                // Перезагружаем стейты настроек в память
                SettingsManager.init(context)
            }

            // 2. Импорт истории
            val historyArray = rootJson.optJSONArray("history")
            var itemsRestored = 0
            if (historyArray != null) {
                val db = AppDatabase.getDatabase(context)
                val historyDao = db.historyDao()
                
                val newItems = mutableListOf<DownloadedFileItem>()
                for (i in 0 until historyArray.length()) {
                    val itemJson = historyArray.getJSONObject(i)
                    
                    val mediaTypeStr = itemJson.optString("mediaType", "VIDEO")
                    val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (_: Exception) { MediaType.VIDEO }
                    
                    val pathOrUri = itemJson.optString("pathOrUri", "")
                    
                    // 🛡️ Проверка реального существования файла на новом устройстве
                    val fileExists = when {
                        pathOrUri.isBlank() -> false
                        pathOrUri.startsWith("content://") -> {
                            try {
                                context.contentResolver.openInputStream(Uri.parse(pathOrUri))?.use { true } ?: false
                            } catch (_: Exception) { false }
                        }
                        else -> java.io.File(pathOrUri).exists()
                    }
                    
                    val isMissing = if (!fileExists) true else itemJson.optBoolean("isMissing", false)
                    
                    // Очистка пути к миниатюре, если файл отсутствует
                    var thumbUrl = if (itemJson.has("thumbnailUrl")) itemJson.getString("thumbnailUrl") else null
                    if (thumbUrl != null && !java.io.File(thumbUrl).exists()) {
                        thumbUrl = null
                    }
                    
                    val item = DownloadedFileItem(
                        id = itemJson.optString("id", java.util.UUID.randomUUID().toString()),
                        pathOrUri = pathOrUri,
                        name = itemJson.optString("name", "Unknown"),
                        sizeBytes = itemJson.optLong("sizeBytes", 0L),
                        sizeFormatted = itemJson.optString("sizeFormatted", "0 B"),
                        dateFormatted = itemJson.optString("dateFormatted", ""),
                        mediaType = mediaType,
                        sourceUrl = itemJson.optString("sourceUrl", ""),
                        thumbnailUrl = thumbUrl,
                        durationMs = itemJson.optLong("durationMs", 0L),
                        timestamp = itemJson.optLong("timestamp", System.currentTimeMillis()),
                        isMissing = isMissing
                    )
                    newItems.add(item)
                }
                
                if (newItems.isNotEmpty()) {
                    // 🔄 Транзакционная вставка: либо обе операции успешны, либо ни одна
                    db.runInTransaction {
                        runBlocking {
                            historyDao.clearAll()
                            historyDao.insertAll(newItems)
                        }
                    }
                    itemsRestored = newItems.size
                }
            }
            
            AsyncLogger.log(LogLevel.INFO, "Бэкап успешно восстановлен! Записей: $itemsRestored")
            Result.success(itemsRestored)
            
        } catch (e: Exception) {
            AsyncLogger.log(LogLevel.ERROR, "Ошибка импорта бэкапа: ${e.message}")
            Result.failure(e)
        }
    }
}