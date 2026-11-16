# mpoddy Current Tasks

Единый оперативный источник текущего состояния Android-кандидата `mpoddy`.

Последняя редакция: 24 сентября 2026 года.

## Активные задачи

### MPOD-ABK-01 (Этап 0) — Документация и контракты для аудиокниг
- **Статус:** `CLOSED — PASS`, 12.11.2026.
- **Что сделано:**
  - В `docs/android-user-scenarios.md` зафиксированы сценарии `ABK-01` … `ABK-14` (включая `ABK-05b` для защиты при удалении файла и `ABK-14` для ввода точного времени).
  - В `docs/android-delivery-plan.md` актуализированы решения:
    - Автономный движок аудиокниг (SAF `OpenDocumentTree`, прямой `ContentResolver.query`, лимит 2000 файлов).
    - Strictly Read-Only гарантия: защита от удаления файлов модулем `SmartListeningManager`.
    - Унификация перемотки: -15с / +30с для всех типов медиа.
    - Независимая скорость воспроизведения: `audiobookPlaybackSpeed` (1.0x default) в DataStore.
    - Нижняя навигация на 5 вкладок (`Player`, `Podcasts`, `Abooks`, `Settings`, `Add`).
    - Схема Room v3 с полиморфной очередью и таблицами `audiobooks`, `audiobook_tracks`, `audiobook_playlist_tracks`.
    - Динамическое скользящее окно ExoPlayer (2-track) для бесшовного воспроизведения.
    - Привязка макетов Figma (`1309:12604`) и палитры `mpoddy dark mode`.

### MPOD-ABK-02 (Этап 1) — Унификация перемотки (-15с / +30с) и DataStore скорость аудиокниг
- **Статус:** `CLOSED — PASS`, 13.11.2026.
- **Что сделано:**
  - В `PlayerView.kt` и `HomeScreen.kt` шаг перемотки унифицирован на -15с / +30с (кнопки, подписи, accessibility content descriptions, вызовы `onSeekBy(-15)` и `onSeekBy(30)`).
  - В `PlaybackService.kt` в конфигурацию `ExoPlayer.Builder` добавлены `setSeekBackIncrementMs(15_000L)` и `setSeekForwardIncrementMs(30_000L)`.
  - В `AppSettingsDataStore.kt` и `FakeAppSettingsStore.kt` добавлен ключ `audiobookPlaybackSpeed: Float = 1.0f` и метод `setAudiobookPlaybackSpeed(speed: Float)`.
  - Обновлены тесты `HomeScreenTest.kt` и `AppSettingsDataStoreTest.kt`.

### MPOD-ABK-03 (Этап 2) — Схема Room v3, сущности, DAO и миграция 2->3
- **Статус:** `CLOSED — PASS`, 13.11.2026.
- **Что сделано:**
  - Созданы Room-сущности: `AudiobookEntity`, `AudiobookTrackEntity`, `AudiobookPlaylistTrackEntity`.
  - Модифицирован `PlaylistItemEntity`: добавлены `audiobookId: Long?`, внешний ключ с `CASCADE`, уникальный индекс, Kotlin runtime `require` и SQLite `CHECK ((episodeId IS NOT NULL AND audiobookId IS NULL) OR (episodeId IS NULL AND audiobookId IS NOT NULL))` для строгой полиморфности.
  - Реализован `AudiobookDao`: CRUD для книг и глав, выборка глав для плейлиста, выборка текущей главы по инварианту (`ABK-06`), атомарный `@Transaction` сброс прогресса книги (`ABK-10`).
  - В `PlaylistDao` добавлены методы проверки, добавления, удаления и перемещения для аудиокниг.
  - В `MpodDatabaseMigrations` реализована `MIGRATION_2_3`: создание таблиц аудиокниг и пересоздание `playlist_items` с новыми связями.
  - `DatabaseModule` обновлён: зарегистрирована миграция 2->3, добавлен `provideAudiobookDao`.
  - Подтверждена Read-Only гарантия (`ABK-11`): `SmartListeningManager` изолирован от аудиокниг и оперирует исключительно `filesDir/podcasts` и `episodeId`.
  - Добавлены тесты `AudiobookDaoTest.kt` и обновлён `MpodDatabaseMigrationTest.kt`.

### MPOD-ABK-04 (Этап 3) — Движок SAF-сканирования, Natural Sort, обложки и Rescan
- **Статус:** `CLOSED — PASS`, 13.11.2026.
- **Что сделано:**
  - Реализован `NaturalOrderComparator` с поддержкой числовых и текстовых чанков для естественной сортировки глав (1, 2 ... 10).
  - Реализован `AudiobookStorageScanner`:
    - Прямые низкоуровневые запросы к `ContentResolver.query` по `DocumentsContract` на `Dispatchers.IO` без оверхеда `DocumentFile`.
    - Распознавание структур каталогов (Корень -> Автор -> Книга -> Главы, Корень -> Книга -> Главы, вложенные папки CD1/CD2/Disc1, а также одиночные аудиофайлы).
    - Извлечение обложек: файлы `cover.jpg` / `folder.jpg` / `.png` в каталоге, fallback на встроенный APIC/covr фрейм через `MediaMetadataRetriever` с кэшированием в `cacheDir/audiobook_covers/`.
    - Извлечение метаданных (длительность, название трека, автор, альбом).
    - Ограничение глубины сканирования до 3 уровней и лимит на 2000 файлов.
    - Логика Rescan (`ABK-05`): сохранение позиций и прогресса существующих треков, добавление новых, отметка недоступных (`isAvailable = false`) без сброса активной очереди `playlist_items` и `audiobook_playlist_tracks`.
  - В `AppSettingsDataStore` и `FakeAppSettingsStore` добавлено поле `audiobooksFolderUri` и метод `setAudiobooksFolderUri`.
  - Создан `AudiobookRepository` для координации сканирования, сохранения настроек и доступа к книгам/главам.
  - Добавлены тесты `NaturalOrderComparatorTest`, `AudiobookRepositoryTest`, расширен `AppSettingsDataStoreTest`.

### MPOD-ABK-05 (Этап 4) — Логика очереди и бесшовный плеер
- **Статус:** `CLOSED — PASS`, 14.11.2026.
- **Что сделано:**
  - Полиморфная модель очереди `PlayableItem` (`EpisodeItem` и `AudiobookItem`).
  - Разрешение скользящего окна из 2 треков (`SlidingWindowResolver`) с предзагрузкой следующего трека/главы для бесшовного воспроизведения без пауз.
  - Обработка `STATE_ENDED` и ротация скользящего окна в `PlaybackService`.
  - Защита и перехват недоступных файлов (`ABK-05b`) с пропуском на следующий доступный трек.
  - Атомарное удаление книги из очереди и сброс прогресса в Room (`ABK-10`) по завершении последней главы.
  - Добавлены тесты `SlidingWindowResolverTest` и `PlaylistRepositoryPlayableItemsTest`.

### MPOD-ABK-06 (Этап 5) — UI и Compose-компоненты по макетам Figma
- **Статус:** `CLOSED — PASS`, 16.11.2026.
- **Что сделано:**
  - Нижняя навигация на 5 вкладок (`Player`, `Podcasts`, `Abooks`, `Settings`, `Add`) по макету `1269:7839`.
  - Экран `AudiobooksScreen` и `AudiobooksViewModel` с навигацией по папкам авторов и книг, хлебными крошками (`1304:10573`).
  - Компонент `FilemanagerItem` (`1288:9444`) с поддержкой типов `Directory`, `AudiobookDirectory`, `AudioFile`.
  - Модальное окно `ChaptersModal` (`1309:12489`) с двумя режимами (`FileManager` для выбора глав в очередь без воспроизведения `ABK-07`, и `Player` с прогрессом и Pause/Play/Replay `ABK-08`).
  - Цифровой ввод точного времени `PlayerMobileTimeInput` (`1346:15503`) с динамическим сдвигом цифр `HH:MM:SS` (`ABK-14`).
  - Разделение статусов плеера в `PlayerView` (`1353:15629`): отображение скорости и вызова глав для аудиокниг, заметок для подкастов.
  - Поддержка кастомной команды выбора главы через `EXTRA_PREFERRED_TRACK_ID`.
  - Unit-тесты `PlayerMobileTimeInputTest`, `AudiobooksViewModelTest`, обновление `MpodAccessibilityTest`.
  - Независимый diff review проведен в Swarm на `Claude Sonnet 4.6 (Thinking)`: все замечания (C1–C3, M2–M5, M7) исправлены и верифицированы.

### MPOD-ABK-07 (Этап 6) — Финализация, версии, регрессионные тесты и релизная готовность
- **Статус:** `CLOSED — PASS`, 16.11.2026.
- **Что сделано:**
  - `versionCode` инкрементирован до 36, `versionName` поднят до `1.0.35` в `app/build.gradle.kts`.
  - Актуализирован `docs/android-user-scenarios.md`: все сценарии `ABK-01` … `ABK-14` переведены в статус `Verified`.
  - Верифицированы Room миграции v1 -> v2 и v2 -> v3 в `MpodDatabaseMigrationTest`.
  - Покрыты unit-тестами парсинг времени, сдвиг цифр, полиморфное скользящее окно, индексация глав и файловый менеджер.
  - Актуализирован `docs/android-delivery-plan.md` (базовый билд `1.0.35 (36)`).

### Очередь следующих этапов (MPOD-ABK)
Все этапы эпика `MPOD-ABK` (`MPOD-ABK-01` … `MPOD-ABK-07`) успешно завершены и закрыты.

## Последняя закрытая задача — MPOD-REL-02

**Название:** Финальный regression gate и новый APK.
**Статус:** `CLOSED — PASS`, 23.09.2026.

- Исправление плеера, regression tests, QA-отчёты и fixture-пакет закоммичены и отправлены в `origin/codex/qa-obvious-bugs` по прямой команде пользователя.
- Commit исходников APK: `eb34273be19fd4ce593f282ae458a6ef26129c8b`; наличие на remote подтверждено через `git ls-remote`.
- Кандидат: `mpoddy 1.0.18 (19)`, production package `com.prod.mpod`. Финализация уже проверенного кандидата выполнена без изменения версии и исполняемого кода.
- Итоговый APK: `build/release-handoff/mpoddy-1.0.18-19-eb34273-6880c4432152.apk`, 4 915 716 байт.
- SHA-256: `6880c44321522708c02fd8092196281830035f16dfd4737d54742ddc74a8935a`.
- Release assembly из опубликованного commit: `BUILD SUCCESSFUL in 1m 6s`. Подпись проверена; используется прежний debug-key fallback release-конфигурации.
- От проверенного APK отличается только `META-INF/version-control-info.textproto`: теперь внутри записан commit `eb34273` вместо `26112e1`. Все остальные 217 ZIP entries, включая DEX, ресурсы и manifest, побайтово идентичны. Поэтому результаты полного QA сохранены без повторного прогона.
- Проверки: 178/178 JVM tests, 65/65 instrumentation tests; debug/release lint — 0 errors, прежние предупреждения; QA support — 9/9.
- Итоговый APK установлен поверх существующего на Pixel 9 API 37 без очистки данных. Сохранились 6 подписок, скачанный выпуск в очереди, позиция 0:23, скорость 1.5x и Settings; crash buffer пуст.
- Полный Refresh all подтверждён на исходном Pixel 9. Прямая сверка Room/files выполнена на том же исполняемом коде в дополнительном Google APIs Pixel 7 API 37 / 16 KB AVD; ограничения этих проверок сохранены в отчётах.

**Итоговый handoff:** `docs/mpoddy-release-1.0.18-closure.md` и `docs/mpoddy-release-1.0.18-final.json`.

**Границы:** закрыт согласованный emulator gate и локальная поставка APK. Физический телефон и звук на слух не проверялись; Google Play, GitHub Release и production deploy не выполнялись. Это не основание заново открывать задачи текущего списка: ограничения не входят в согласованный объём.

## Порядок в репозитории — 24.09.2026

Интеграция по команде пользователя: fast-forward рабочей ветки в `main`, без изменения приложения и Gradle-конфигурации относительно проверенного source commit. Статус EPS-09 синхронизирован с завершённой приёмкой.

Удалены временный QA AVD, локальные сборки/кэши, `.DS_Store` и два прежних APK. Итоговый APK и JSON сохранены; QA evidence сохранено, включая архив 179 исходных Gradle report-файлов `build/qa-stage3-20260923/original-gradle-reports.zip`. Локальный `AGENTS.md` и stash от 15.09 сохранены; stash не объявлен мусором без проверки уникальных изменений.

## Очистка исходников — 24.09.2026

Удалены неиспользуемые адреса/backend-конфигурация, старые API-обработчики, функции служебной информации/форматирования и Retrofit/Gson/logging зависимости. HTTP-подкасты, прокси и защитные backup-исключения сохранены. Проверки: 168/168 JVM, 12/12 целевых Android, debug/release lint без ошибок, release assembly PASS. Подробности: `docs/mpoddy-legacy-cleanup-2026-09-24-report.md`.

Это выполненная очистка исходников после закрытого релиза, а не новая приёмка APK: сохранённый итоговый APK из `MPOD-REL-02` не пересоздавался и не содержит очистки. Новую поставку/установку без отдельного запроса не запускать.

## Остальные закрытые задачи

- `MPOD-BUG-01`, `MPOD-BUG-01-R1`, `MPOD-BUG-02`, `MPOD-QA-02-R1`, `MPOD-BUG-03`, `MPOD-MAINT-01`, `MPOD-REL-01`, `MPOD-COMPAT-01`.
- `MPOD-BUG-03`: commit `3fb7f45`, regression FAIL-before/PASS-after, review `APPROVED`.
- `MPOD-OPS-02`: checkpoint commit `8655106`.

Не возвращать закрытые задачи без нового воспроизведения или blocker-замечания. Не откатывать `341002d`, `dda7734`, `da4c6a7` или `3fb7f45` ради искусственного FAIL-before.
