# Videx

<p align="center">
  <a href="https://github.com/XZ-LITE/Videx/releases/latest"><img src="https://img.shields.io/github/v/release/XZ-LITE/Videx?style=for-the-badge&color=3B82F6&label=Release" alt="Release"></a>
  <a href="https://developer.android.com/about/versions/15"><img src="https://img.shields.io/badge/Android-15_Ready-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 15"></a>
  <a href="https://github.com/XZ-LITE/Videx/actions"><img src="https://img.shields.io/github/actions/workflow/status/XZ-LITE/Videx/release.yml?style=for-the-badge&label=CI/CD" alt="Build Status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/XZ-LITE/Videx?style=for-the-badge&color=22C55E" alt="License"></a>
</p>

**Videx** — открытое Android-приложение для скачивания видео, аудио и фото-каруселей с YouTube, TikTok, Instagram, VK, Pinterest и более чем 1000 других сайтов. Обработка ссылок происходит локально на устройстве с помощью CPython 3.13 и `yt-dlp`.

[Платформы](#поддерживаемые-платформы) • [Возможности](#возможности) • [Архитектура](#технологический-стек) • [Сборка](#сборка-из-исходников) • [Сообщество](#сообщество)

---

## Поддерживаемые платформы

> YouTube • Shorts • TikTok (без водяных знаков) • Instagram (Reels, посты, фото) • VK Видео • VK Клипы • Pinterest • X (Twitter) • Reddit • SoundCloud • Vimeo • Rutube и др.

---

## Возможности

### 📥 Загрузка медиа
- **Выбор качества**: Скачивание видео до 4K/60FPS в оригинальном качестве.
- **Извлечение аудио**: Сохранение звука в формате MP3 или M4A.
- **Скачивание альбомов**: Загрузка фото-каруселей и слайдшоу из TikTok и Instagram.
- **Очередь и фоновые задачи**: Многопоточная загрузка через `WorkManager` с уведомлениями и настройкой лимитов потоков для Wi-Fi и мобильной сети.

### ⚡ Автопилот и Транзит
- **Универсальная отправка**: При отправке ссылки в Videx через системную кнопку «Поделиться» файл скачивается в фоне и открывает шторку для отправки в любой мессенджер (Telegram, WhatsApp, VK, Viber, Discord).
- **Оптимизация видео и фото**: Конвертация видео в H.264/HEVC MP4 (1080p, 720p, 480p) и сжатие снимков для мгновенной пересылки в чаты.
- **Перехват ссылок**: Перехват скопированных ссылок из буфера обмена с загрузкой превью.

### 🎧 Плеер и Галерея
- **Видеоплеер (Media3 ExoPlayer)**: Управление жестами (яркость, громкость, скорость 1.0x-2.0x) и режим «Картинка-в-картинке» (PiP).
- **Аудиоплеер**: Фоновое воспроизведение с управлением через уведомление `MediaSession`.
- **Галерея и История**: Просмотр скачанных файлов, встроенная БД Room v5 с защитой от дубликатов и синхронизацией с системным хранилищем.

### 🛡️ Приватно и без рекламы
- **Локальная обработка**: Данные анализируются только на смартфоне без сторонних серверов.
- **0% рекламы**: Полное отсутствие баннеров, трекеров и аналитики.

---

## Технологический стек

| Компонент | Технология |
| :--- | :--- |
| **Язык** | Kotlin (JDK 17) |
| **UI** | Jetpack Compose, Material Design 3 |
| **Архитектура** | MVVM, ViewModels, StateFlow, Coroutines |
| **Медиаплеер** | AndroidX Media3 ExoPlayer, MediaSession |
| **База данных** | Room Database (SQLite v5) |
| **Фоновые задачи** | AndroidX WorkManager |
| **Python-движок** | Chaquopy (CPython 3.13, `yt-dlp`, `requests`) |
| **Конвертация** | FFmpeg 6.1.1 (64KB page-aligned под Android 15) |

---

## Сборка из исходников

### Требования
- Android Studio Ladybug (2024.2.1) или новее
- JDK 17
- Android SDK 36 (minSdk 26, targetSdk 36)

```bash
./gradlew assembleRelease
```

Готовый файл: `app/build/outputs/apk/release/app-release.apk`

---

## Установка готового приложения

Скачать релизный APK-файл можно из официального раздела **[GitHub Releases](https://github.com/XZ-LITE/Videx/releases)**.

---

## Партнёр проекта

Приложение создано при поддержке команды **ProxyGuide Team**:
- **Сообщество ProxyGuide**: [@ProxyGuide_Community](https://t.me/ProxyGuide_Community)
- **Telegram-бот**: [@ProxyGuidebot](https://t.me/ProxyGuidebot)

---

## Сообщество

- **Telegram-канал**: [@videx_app](https://t.me/videx_app)
- **Чат поддержки**: [@videx_chat](https://t.me/videx_chat)

---

## Лицензия

MIT License. См. файл [LICENSE](LICENSE).
