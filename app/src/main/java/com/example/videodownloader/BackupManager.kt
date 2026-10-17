package com.example.videodownloader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

            // 1. Экспорт настроек (SharedPreferences)
            val settingsJson = JSONObject()
            for ((key, value) in prefs.all) {
                when (value) {
                    is Boolean -> settingsJson.put(key, value)
                    is String -> settingsJson.put(key, value)
                    is Int -> settingsJson.put(key, value)
                    is Float -> settingsJson.put(key, value)
                    is Long -> settingsJson.put(key, value)
                }
            }
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
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("Не удалось прочитать файл")

            val rootJson = JSONObject(jsonString)
            
            // Проверка версии
            val version = rootJson.optInt("version", 0)
            if (version == 0) throw Exception("Некорректный формат файла резервной копии")

            // 1. Импорт настроек
            val settingsJson = rootJson.optJSONObject("settings")
            if (settingsJson != null) {
                val prefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                
                settingsJson.keys().forEach { key ->
                    val value = settingsJson.get(key)
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Double -> editor.putFloat(key, value.toFloat()) // JSON парсит float как double
                        is Long -> editor.putLong(key, value)
                    }
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
                    
                    val item = DownloadedFileItem(
                        id = itemJson.optString("id", java.util.UUID.randomUUID().toString()),
                        pathOrUri = itemJson.optString("pathOrUri", ""),
                        name = itemJson.optString("name", "Unknown"),
                        sizeBytes = itemJson.optLong("sizeBytes", 0L),
                        sizeFormatted = itemJson.optString("sizeFormatted", "0 B"),
                        dateFormatted = itemJson.optString("dateFormatted", ""),
                        mediaType = mediaType,
                        sourceUrl = itemJson.optString("sourceUrl", ""),
                        thumbnailUrl = if (itemJson.has("thumbnailUrl")) itemJson.getString("thumbnailUrl") else null,
                        durationMs = itemJson.optLong("durationMs", 0L),
                        timestamp = itemJson.optLong("timestamp", System.currentTimeMillis()),
                        isMissing = itemJson.optBoolean("isMissing", false)
                    )
                    newItems.add(item)
                }
                
                if (newItems.isNotEmpty()) {
                    historyDao.clearAll() // Очищаем старую историю
                    historyDao.insertAll(newItems)
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