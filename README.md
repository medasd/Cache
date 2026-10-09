# Очистка кэша (Pixel 8, Android 16+)

Kotlin · Jetpack Compose · Material 3 (динамические цвета Pixel, светлая/тёмная тема) · без сторонних библиотек, кроме AndroidX и kotlinx-coroutines.

## Что умеет приложение

| Действие | Как работает | Нужно от вас |
|---|---|---|
| Список приложений | `PackageManager` + `QUERY_ALL_PACKAGES` | ничего |
| Размер кэша каждого приложения | `StorageStatsManager.queryStatsForPackage` | один раз включить «Доступ к данным об использовании» |
| Очистка кэша **самого приложения** | удаление `cacheDir` / `externalCacheDirs` | ничего (автоматически) |
| Очистка кэша **чужих приложений** | Android запрещает; открывается экран «О приложении» нужного приложения | 2 касания на приложение |

**Большая кнопка «ОЧИСТИТЬ КЭШ»** — очищает свой кэш, затем строит очередь из приложений с кэшем ≥ 1 МБ и по одному
открывает их настройки. В настройках: «Хранилище и кеш» → «Очистить кеш» → назад. Приложение само проверяет, сколько
освободилось, и через ~1 с открывает следующее.

**«ОЧИСТИТЬ КЭШ ВСЕХ ДОСТУПНЫХ»** — только то, что Android разрешает без вас (свой кэш). Итоговая карточка покажет,
какие приложения требуют ручного действия, и предложит пройти по ним очередью.

Нажатие на приложение в списке открывает его настройки напрямую.

## Структура

```
CacheClean/
├── settings.gradle.kts
├── build.gradle.kts                 (версии плагинов)
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties (+ jar), gradlew, gradlew.bat
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/ (strings, colors, themes, иконка)
        └── java/com/example/cacheclean/
            ├── MainActivity.kt
            ├── data/  AppCache.kt · CacheRepository.kt · IconLoader.kt · Intents.kt
            └── ui/    UiState.kt · MainViewModel.kt · MainScreen.kt · theme/Theme.kt
```

Версии: AGP 8.13.2, Gradle 8.14.3, Kotlin 2.3.21, Compose BOM 2025.12.00, compileSdk/targetSdk 36, minSdk 34, JDK 17.
(Compose 1.12+ требует compileSdk 37 и AGP 9 — при желании обновите позже через Android Studio.)

## Открыть в Android Studio и собрать APK

1. Установите свежий Android Studio. Распакуйте архив, **File → Open** → папка `CacheClean`.
2. Дождитесь Gradle Sync (первый раз скачает зависимости). Если Studio предложит установить **Android SDK Platform 36** — согласитесь.
3. Сборка APK:
   - меню **Build → Build Bundle(s) / APK(s) → Build APK(s)**, или
   - терминал в папке проекта (Windows): `gradlew.bat assembleDebug` (macOS/Linux: `./gradlew assembleDebug`).
   - Результат: `app/build/outputs/apk/debug/app-debug.apk`.
4. Установка на Pixel 8:
   - по USB с включённой «Отладкой по USB»: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
   - или перекиньте APK на телефон, откройте и разрешите установку из этого источника.
   - либо просто нажмите **Run ▶** в Android Studio с подключённым телефоном.

## Собрать APK без Android Studio (GitHub Actions)

В проекте есть готовый workflow `.github/workflows/build-apk.yml`: GitHub сам собирает APK на своём сервере.

1. Создайте аккаунт на github.com и новый репозиторий (**New repository**, можно приватный).
2. Загрузите в него **содержимое** папки `CacheClean` вместе со скрытой папкой `.github`:
   - через сайт: **Add file → Upload files**, перетащите все файлы и папки из `CacheClean`;
   - если `.github` не загрузилась: **Add file → Create new file**, в имени укажите `.github/workflows/build-apk.yml` и вставьте содержимое этого файла;
   - либо через git: `git init`, `git add .`, `git commit -m "init"`, `git remote add origin <URL>`, `git push -u origin main`.
3. Откройте вкладку **Actions**. Сборка стартует сама после загрузки (или нажмите **Build APK → Run workflow**). Занимает 5–10 минут.
4. Когда появится зелёная галочка: откройте запуск → раздел **Artifacts** → скачайте **CacheClean-debug-apk** (внутри zip лежит `app-debug.apk`).
5. Распакуйте, перекиньте APK на Pixel 8 или установите `adb install -r app-debug.apk`.

Если сборка упала (красный крест) — откройте запуск, шаг **Build debug APK**, скопируйте текст ошибки.

## Первый запуск

1. Откройте приложение → карточка «Нужен доступ» → «Открыть настройки» → включите переключатель для «Очистка кэша».
   Если переключатель серый (APK установлен не из Play): **Настройки → Приложения → Очистка кэша → ⋮ → «Разрешить ограниченные настройки»**, затем повторите.
2. Вернитесь в приложение — появится общий размер кэша и список приложений.
3. Нажмите «ОЧИСТИТЬ КЭШ».

## Быстрая альтернатива с компьютера (без приложения)

С включённой отладкой по USB команда `adb shell pm trim-caches 999G` просит систему освободить кэш приложений
(выполняется с правами shell). Это не часть приложения, а отдельный способ.
