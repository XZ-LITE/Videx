# Полный технический аудит проекта Videx (MyApplication)

> **Дата аудита:** 14 октября 2026 г.  
> **Версия приложения:** 1.1.5 (versionCode 18)  
> **Стек технологий:** Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00), Python 3.13 (Chaquopy), Media3 ExoPlayer 1.4.1, Room 2.6.1, WorkManager 2.9.0, Coil 2.6.0, OkHttp 4.12.0, FFmpeg.

---

## Содержание
1. [Критические баги и риски аварийного завершения (Crashes & Runtime Bugs)](#1-критические-баги-и-риски-аварийного-завершения-crashes--runtime-bugs)
2. [Лаги, просадки FPS и проблемы под капотом (Performance & Jank)](#2-лаги-просадки-fps-и-проблемы-под-капотом-performance--jank)
3. [Проблемы визуальной части и UI/UX (Visual & UX Bugs)](#3-проблемы-визуальной-части-и-uiux-visual--ux-bugs)
4. [Безопасность и хранение секретов (Security & Privacy)](#4-безопасность-и-хранение-секретов-security--privacy)
5. [Архитектурные недочеты (Architecture & Code Quality)](#5-архитектурные-недочеты-architecture--code-quality)
6. [Сводная таблица задач и приоритеты доработки](#6-сводная-таблица-задач-и-приоритеты-доработки)

---

## 1. Критические баги и риски аварийного завершения (Crashes & Runtime Bugs)

### 🔴 1.1. Вылет `WorkManager`: Несовместимость Expedited Work с `APPEND_OR_REPLACE`
* **Файл:** `app/src/main/java/com/example/videodownloader/logic/DownloadManager.kt` (строки 175–202, 235–262)
* **Проблема:**  
  Для задач скачивания вызывается `.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`, а затем запрос отправляется через:
  ```kotlin
  workManager.enqueueUniqueWork(
      slotName,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      downloadWorkRequest
  )
  ```
  В Android `WorkManager` (начиная с версии 2.7+) связывание задач (`ExistingWorkPolicy.APPEND` или `APPEND_OR_REPLACE`) вместе с **Expedited Work Request** категорически запрещено и вызывает фатальное исключение:
  `java.lang.IllegalArgumentException: Expedited work cannot be used with APPEND or APPEND_OR_REPLACE`.
* **Последствие:** При добавлении второго файла в фоновую очередь приложение падает с крэшем.
* **Решение:** Использовать обычный (не-expedited) `OneTimeWorkRequest` для задач очереди либо использовать `ExistingWorkPolicy.KEEP` / разделять цепочки задач.

---

### 🔴 1.2. Ошибка вычисления свободного места для выбранной папки (SAF)
* **Файл:** `app/src/main/java/com/example/videodownloader/DownloadWorker.kt` (строки 609–631)
* **Проблема:**  
  В функции `getAvailableSpaceForUri()` используется код:
  ```kotlin
  val rootId = DocumentsContract.getTreeDocumentId(uri)
  val rootUri = DocumentsContract.buildRootUri(uri.authority, rootId)
  val cursor = context.contentResolver.query(rootUri, ...)
  ```
  Метод `getTreeDocumentId(uri)` возвращает Document ID дерева (например, `"primary:Download"`), тогда как `buildRootUri` ожидает Root ID (например, `"primary"`).
* **Последствие:** Формируется невалидный URI `content://.../root/primary%3ADownload`. Запрос к `ContentResolver` выбрасывает исключение, проверка свободного места всегда сваливается в блок `catch`, и размер доступного пространства вычисляется для внутренней памяти устройства, игнорируя выбранную пользователем SD-карту или внешний диск.
* **Решение:** Извлекать корректный `rootId` (до двоеточия) либо использовать `StatFs` для пути монтирования тома.

---

### 🔴 1.3. Ложное создание `File("content://...")` и невалидные дескрипторы
* **Файл:** `app/src/main/java/com/example/videodownloader/YtDlpBridge.kt` (строки 326, 348, 366)
* **Проблема:**  
  После сохранения файла через Storage Access Framework или MediaStore возвращается Content URI, после чего выполняется:
  ```kotlin
  if (firstSavedFile == null) firstSavedFile = File(newDoc.uri.toString())
  // или: File(savedUriOrPath)
  ```
  На Android 10+ пути начинаются с `content://media/...` или `content://com.android.externalstorage...`. Конструктор `java.io.File` не работает со схемами URI.
* **Последствие:** Создается псевдо-файл. При последующих вызовах `file.exists()`, `file.length()`, `file.readBytes()` возвращаются `false`, `0` или ошибки ввода-вывода.
* **Решение:** Возвращать из `downloadVideo` модель `Result<SavedMediaResult>`, хранящую как чистый `Uri`, так и путь файловой системы (если он доступен), и работать с потоками через `ContentResolver.openInputStream(uri)`.

---

### 🔴 1.4. Неработающее фоновое воспроизведение аудио и отсутствие `MediaSessionService`
* **Файлы:**
  - `app/src/main/java/com/example/videodownloader/ui/screens/AudioPlayerScreen.kt` (строки 112–155, 362–373)
  - `app/src/main/AndroidManifest.xml` (строка 15)
  - `app/src/main/java/com/example/videodownloader/NotificationReceiver.kt` (строки 13–18, 43–54)
* **Проблема:**  
  1. `ExoPlayer` и `MediaSession` создаются напрямую внутри Composable-функции `AudioPlayerScreen`.
  2. При нажатии кнопки «Назад» или переходе на другие экраны срабатывает блок `onDispose { exoPlayer.release() }`, который уничтожает плеер. Воспроизведение прерывается.
  3. В манифесте объявлено разрешение `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, однако **сервис с типом `mediaPlayback` не зарегистрирован**. Уведомление создается обычным вызовом `notificationManager.notify(2002, ...)`. На Android 14+ система принудительно глушит фоновые процессы без зарегистрированного Foreground Service.
  4. В `NotificationReceiver` управление воспроизведением реализовано через статические коллбэки `AudioControlBus.onPlayPause`. При выгрузке UI из памяти они обнуляются (`null`), и кнопки в системной шторке перестают отвечать на нажатия.
* **Решение:** Реализовать полноценный `MediaSessionService` (Media3), управляющий `ExoPlayer` независимо от жизненного цикла Compose-экранов.

---

### 🔴 1.5. Баги OTA-обновления ядра `yt-dlp`
* **Файлы:**
  - `app/src/main/java/com/example/videodownloader/OtaUpdater.kt` (строки 69–76)
  - `app/src/main/python/ytdlp_wrapper.py` (строки 16–24)
* **Проблема:**  
  1. `OtaUpdater` скачивает первый попавшийся подходящий ассет релиза (`yt-dlp`, `.tar.gz` или `.zip`) и сохраняет его как `.zip`. Если скачивается tarball (`.tar.gz`), стандартный Python `zipimport` через `sys.path.insert(0, ...)` не может прочитать архив (поддерживаются только ZIP).
  2. Добавление пути в `sys.path` **не обновляет** уже импортированные модули Python (`sys.modules`), если движок был предварительно прогрет вызовом `pre_load()`. Старая версия ядра продолжает использоваться в текущей сессии вплоть до полного перезапуска процесса приложения.
* **Решение:** Фильтровать только официальный zipapp-архив `yt-dlp.zip` / `yt-dlp` и выполнять инвалидацию кэша модулей через `importlib.invalidate_caches()` и `importlib.reload()`.

---

### 🔴 1.6. Ограничение архитектур NDK и сбои на эмуляторах / x86_64
* **Файл:** `app/build.gradle.kts` (строки 26–30, 140–188)
* **Проблема:**  
  В `defaultConfig` задано:
  ```kotlin
  ndk {
      abiFilters.clear()
      abiFilters.add("arm64-v8a")
  }
  ```
  При этом задача `downloadFFmpeg` скачивает x86_64-бинарник, который даже не попадает в APK.
* **Последствие:** Приложение невозможно запустить на стандартных 64-битных эмуляторах Android Studio x86_64 (без ARM-трансляции) и на 32-битных смартфонах.

---

## 2. Лаги, просадки FPS и проблемы под капотом (Performance & Jank)

### 🟡 2.1. Критический лаг при скролле списка Истории (N+1 I/O Queries)
* **Файлы:**
  - `app/src/main/java/com/example/videodownloader/ui/screens/HistoryScreen.kt` (строки 952–954)
  - `app/src/main/java/com/example/videodownloader/HistoryManager.kt` (строки 439–464)
* **Проблема:**  
  Внутри карточки `HistoryItemCard` прямо при привязке элемента списка вызывается:
  ```kotlin
  LaunchedEffect(item.pathOrUri) {
      HistoryManager.checkAndUpdateFileStatus(context, item)
  }
  ```
  При каждом быстром скролле `LazyColumn` на каждый элемент:
  - Вызывается обращение к диску и `openFileDescriptor` через ContentResolver;
  - Запускается декодирование видео-кадра через `MediaMetadataRetriever`;
  - Делается запись в SQLite через `dao.update()`;
  - Обновление БД триггерит `Flow` в `MainViewModel` ➔ список рекомпозируется заново ➔ начинается бесконечный цикл проверок.
* **Последствие:** Зависания интерфейса, пропуски кадров и рывки при прокрутке.
* **Решение:** Убрать `checkAndUpdateFileStatus` из карточки элемента. Выполнять синхронизацию статусов пакетно только при открытии экрана или свайпе вниз (Pull-to-Refresh).

---

### 🟡 2.2. Блокирующий синхронный Disk I/O на Main Thread
* **Файл:** `app/src/main/java/com/example/videodownloader/logic/AnalysisManager.kt` (строки 128–145)
* **Проблема:**  
  В методе `startBackgroundAnalysis` до запуска корутины происходит синхронное чтение файла с диска:
  ```kotlin
  val jsonStr = passportFile.readText() // Синхронное чтение на Main Thread!
  val meta = parseMetadataFromJson(jsonStr) // Синхронный парсинг JSON!
  ```
* **Последствие:** Микрофризы UI при вставке ссылки.
* **Решение:** Перенести чтение паспорта и парсинг внутрь блока `withContext(Dispatchers.IO)`.

---

### 🟡 2.3. Нагрузка на Garbage Collector в `AsyncLogger`
* **Файл:** `app/src/main/java/com/example/videodownloader/AsyncLogger.kt` (строки 95–113)
* **Проблема:**  
  1. При каждом сообщении лога создается новый `SimpleDateFormat("HH:mm:ss", Locale.getDefault())`, что создает избыточные временные объекты.
  2. Обновление состояния выполняет `(list + entry).takeLast(1000)`, копируя массив до 1000 элементов при каждом вызове.
* **Последствие:** Высокая частота сборки мусора (GC Churn) и лаги RenderThread.
* **Решение:** Использовать кольцевой буфер фиксированного размера (Ring Buffer / ArrayDeque) и единый экземпляр `DateTimeFormatter` / кэшированный `DateFormat`.

---

### 🟡 2.4. Множественный опрос буфера обмена на старте
* **Файлы:**
  - `app/src/main/java/com/example/videodownloader/ui/screens/MainScreen.kt` (строки 159–180)
  - `app/src/main/java/com/example/videodownloader/MainActivity.kt` (строки 261–269)
* **Проблема:**  
  На старте и при каждом получении фокуса вызываются 5 последовательных проверок буфера с задержками `delay(250)`, `delay(350)` и т.д.
* **Последствие:** В Android 12+ система спамит всплывающими тостами («Videx вставил текст из буфера обмена»).
* **Решение:** Опрашивать буфер единожды строго при наличии флага `hasWindowFocus()` и только если включена настройка `useClipboardBubble`.

---

### 🟡 2.5. Отсутствие оптимизации R8 / ProGuard для Release
* **Файл:** `app/build.gradle.kts` (строки 53–60)
* **Проблема:**  
  Для `release`-сборки выставлено `isMinifyEnabled = false`.
* **Последствие:** Огромный размер итогового APK, отсутствие оптимизации мертвого кода и отсутствие обфускации.
* **Решение:** Включить `isMinifyEnabled = true`, протестировать и настроить `proguard-rules.pro` для Chaquopy, Media3 и Coil.

---

## 3. Проблемы визуальной части и UI/UX (Visual & UX Bugs)

### 🎨 3.1. Поломка Edge-to-Edge режима после выхода из Галереи
* **Файл:** `app/src/main/java/com/example/videodownloader/ui/screens/GalleryScreen.kt` (строки 128–136)
* **Проблема:**  
  В блоке `DisposableEffect.onDispose` прописано:
  ```kotlin
  WindowCompat.setDecorFitsSystemWindows(window, true)
  ```
  В `MainActivity` настроен современный сквозной режим `enableEdgeToEdge()`.
* **Последствие:** После закрытия просмотра фото сквозной режим для всего окна сбрасывается. Под системным статус-баром и панелью навигации на всех экранах приложения появляются белые или черные полосы.
* **Решение:** Убрать принудительный сброс `setDecorFitsSystemWindows(true)` либо возвращать `false`.

---

### 🎨 3.2. «Слепые» системные значки статус-бара при светлой теме
* **Файл:** `app/src/main/java/com/example/videodownloader/ui/screens/AudioPlayerScreen.kt` (строки 79–94)
* **Проблема:**  
  При открытии аудиоплеера статус-бар принудительно переводится в ночной вид (`isAppearanceLightStatusBars = false`), но в `onDispose` исходное значение не восстанавливается.
* **Последствие:** Если приложение работает в светлой теме, после закрытия плеера системные часы, уровень батареи и иконки сети становятся белыми на белом фоне.
* **Решение:** В `onDispose` восстанавливать светлый/темный режим значков в зависимости от `SettingsManager.appTheme` и системных настроек.

---

### 🎨 3.3. Хрупкая сериализация и сбои при спецсимволах в подсказках
* **Файл:** `app/src/main/java/com/example/videodownloader/SettingsManager.kt` (строка 907)
* **Проблема:**  
  Кадры динамических подсказок сохраняются строкой через разделители:
  ```kotlin
  val encoded = finalFrames.joinToString(";;;") { "${it.iconKey}:::${it.text}" }
  ```
* **Последствие:** Если пользователь введет в текст подсказки подстроку `;;;` или `:::`, парсинг ломается, карточки отображаются некорректно или обрезаются.
* **Решение:** Сериализовать конфигурацию через `org.json.JSONArray`.

---

### 🎨 3.4. Сканирование папок (`syncWithStorage`) не видит файлы на Android 11+
* **Файл:** `app/src/main/java/com/example/videodownloader/HistoryManager.kt` (строки 771–800)
* **Проблема:**  
  Метод пытается перечислять файлы через `File(folderPath).listFiles()`. В режиме Scoped Storage без разрешения `MANAGE_EXTERNAL_STORAGE` прямое чтение публичных папок возвращает пустой список.
* **Решение:** Выполнять синхронизацию через запросы к `MediaStore.Video.Media` и `MediaStore.Images.Media`.

---

## 4. Безопасность и хранение секретов (Security & Privacy)

### 🔒 4.1. Уязвимости в настройках `WebView`
* **Файл:** `app/src/main/java/com/example/videodownloader/ui/screens/settings/WebViewScreen.kt` (строки 90–99)
* **Проблема:**  
  - Включен `allowFileAccess = true`: при загрузке внешних страниц (авторизация соцсетей) вредоносные скрипты могут получить доступ к файлам в приватной директории приложения.
  - Включен `mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW`: отключает защиту от подгрузки незащищенного HTTP-контента на HTTPS-страницах (Man-in-the-Middle).
* **Решение:** Установить `allowFileAccess = false`, `allowContentAccess = false` и `mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW`.

---

### 🔒 4.2. Захардкоженные пароли хранилища ключей в репозитории
* **Файл:** `app/build.gradle.kts` (строки 40–47)
* **Проблема:**  
  Пароли keystore (`storePassword = "androidappvidex"`, `keyPassword = "androidappvidex"`, `keyAlias = "videxkey"`) прописаны в открытом виде, а сам файл `release.keystore` лежит в git.
* **Решение:** Перенести учетные данные подписи в `local.properties` или переменные окружения CI/CD.

---

### 🔒 4.3. Отключенная проверка SSL-сертификатов в yt-dlp
* **Файл:** `app/src/main/python/ytdlp_wrapper.py` (строка 682)
* **Проблема:**  
  В параметрах `ydl_opts` включен флаг `'nocheckcertificate': True`.
* **Решение:** Удалить флаг или сделать его настраиваемым только для исключительных случаев отладки.

---

### 🔒 4.4. Запрос `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
* **Файл:** `app/src/main/AndroidManifest.xml` (строка 10)
* **Проблема:**  
  Разрешение на обход оптимизации батареи часто становится причиной отклонения приложения при модерации в Google Play Store, если для работы достаточно Foregound Service с `dataSync`.

---

## 5. Архитектурные недочеты (Architecture & Code Quality)

1. **God Object `SettingsManager.kt` (1023 строки):**  
   Класс содержит более 50 полей `StateFlow` и напрямую выполняет операции с `SharedPreferences`. Рекомендуется разбить на доменные настройки (`NetworkPreferences`, `UiPreferences`, `DownloadPreferences`) и мигрировать на **Jetpack DataStore**.
2. **Монолитные экраны Compose:**  
   `MainScreen.kt` (1753 строки), `PlayerScreen.kt` (1438 строк), `HistoryScreen.kt` (1227 строк). Экраны содержат слишком много вспомогательной бизнес-логики, диалогов и низкоуровневых анимаций. Рекомендуется декомпозировать на отдельные файлы компонентов.
3. **Отсутствие Dependency Injection:**  
   Компоненты приложения связаны через статические синглтоны (`Object`), что усложняет модульное тестирование и управление жизненным циклом зависимостей.

---

## 6. Сводная таблица задач и приоритеты доработки

| № | Приоритет | Файл / Компонент | Проблема | Решение |
| :- | :--- | :--- | :--- | :--- |
| 1 | **P0 (Блокер)** | `DownloadManager.kt` | Падение `WorkManager` при очереди загрузок | Использовать `ExistingWorkPolicy.APPEND` без флага `Expedited` для задач очереди |
| 2 | **P0 (Блокер)** | `DownloadWorker.kt` | Ошибка вычисления свободного места SAF | Брать `rootId` тома вместо `documentId` дерева |
| 3 | **P0 (Блокер)** | `AudioPlayerScreen.kt` | Музыка прерывается в фоне, нет сервиса | Вынести плеер в `MediaSessionService` (Media3) |
| 4 | **P0 (Безопасность)** | `build.gradle.kts` | Пароли keystore в коде | Вынести в `local.properties` |
| 5 | **P1 (Производительность)** | `HistoryScreen.kt` | Фризы скролла (LaunchedEffect на каждый элемент) | Проверять статус файлов пакетно, а не при отрисовке элементов LazyColumn |
| 6 | **P1 (Производительность)** | `AnalysisManager.kt` | Disk I/O на главном потоке при вставке ссылки | Перенести чтение паспортов в `withContext(Dispatchers.IO)` |
| 7 | **P1 (Производительность)** | `AsyncLogger.kt` | Нагрузка на GC от SimpleDateFormat и копирования списков | Использовать кольцевой буфер и кэшированный форматтер даты |
| 8 | **P1 (Визуал/UX)** | `GalleryScreen.kt` | Ломается сквозной Edge-to-Edge после выхода | Убрать `setDecorFitsSystemWindows(true)` из `onDispose` |
| 9 | **P1 (Визуал/UX)** | `AudioPlayerScreen.kt` | Белые системные иконки на белом статус-баре | Восстанавливать системную тему статус-бара в `onDispose` |
| 10 | **P2 (Безопасность)** | `WebViewScreen.kt` | Небезопасный `allowFileAccess` и `MIXED_CONTENT` | Отключить доступ к локальным файлам и смешанному контенту |
| 11 | **P2 (Функциональность)** | `OtaUpdater.kt` | Сбой распаковки `.tar.gz` и отсутствие hot-reload модулей | Фильтровать только чистые `.zip` и перезагружать модули через `importlib.reload` |
| 12 | **P2 (Совместимость)** | `HistoryManager.kt` | `syncWithStorage` не находит файлы на Android 11+ | Использовать MediaStore Queries вместо `File.listFiles()` |
