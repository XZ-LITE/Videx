# Улучшение запуска приложения (Splash Screen & Optimization)

Внедрение официального Splash Screen API для устранения «белого экрана» и оптимизация процесса инициализации для более быстрого отклика.

## User Review Required

> [!IMPORTANT]
> Будет добавлена новая зависимость `androidx.core:core-splashscreen`. Потребуется синхронизация Gradle после внесения правок.

## Proposed Changes

### [Core]

#### [MODIFY] [build.gradle.kts](file:///D:/AndroidStudioProjects/MyApplication/app/build.gradle.kts)
- Добавление зависимости `androidx.core:core-splashscreen`.

### [Resources]

#### [MODIFY] [colors.xml](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/res/values/colors.xml)
- Добавление цветов `splash_background` для соответствия теме приложения.

#### [MODIFY] [themes.xml](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/res/values/themes.xml)
- Создание темы `Theme.App.Starting` на базе `Theme.SplashScreen`.
- Настройка иконки и фона заставки.

### [Logic & UI]

#### [MODIFY] [MainActivity.kt](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/videodownloader/MainActivity.kt)
- Вызов `installSplashScreen()` в `onCreate`.
- Настройка `setKeepOnScreenCondition` для удержания заставки до готовности настроек.
- Удаление дублирующего вызова `YtDlpBridge.warmUp`.

#### [MODIFY] [SettingsManager.kt](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/videodownloader/SettingsManager.kt)
- Добавление флага готовности `isInitialized`.

#### [MODIFY] [App.kt](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/videodownloader/App.kt)
- Гарантия того, что `warmUp` вызывается только один раз и максимально рано.

## Verification Plan

### Automated Tests
- `gradlew assembleDebug` — проверка успешной сборки с новой зависимостью.

### Manual Verification
1. Запустить приложение с холодного старта.
2. Проверить отсутствие белого экрана (вместо него должна быть заставка с иконкой).
3. Убедиться, что переход от заставки к главному экрану плавный и происходит только после готовности данных.
