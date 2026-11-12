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

### Очередь следующих этапов (MPOD-ABK)
1. **`MPOD-ABK-02` (Этап 1):** Унификация перемотки (-15с / +30с) в `PlaybackService`/`PlayerViewModel` и настройка `audiobookPlaybackSpeed` в `AppSettingsStore`.
2. **`MPOD-ABK-03` (Этап 2):** Схема Room v3, DDL, миграция 2->3, сущности (`AudiobookEntity`, `AudiobookTrackEntity`, `AudiobookPlaylistTrackEntity`, полиморфный `PlaylistItemEntity`), атомарный сброс прогресса и Read-Only защита от SmartListening.
3. **`MPOD-ABK-04` (Этап 3):** Движок SAF-сканирования (прямой `ContentResolver.query`, natural sort, парсер обложек, лимит 2000, Rescan).
4. **`MPOD-ABK-05` (Этап 4):** Логика очереди и бесшовный плеер («1 книга = 1 элемент», 2-track sliding window, обработка `STATE_ENDED`, перехват `ABK-05b`).
5. **`MPOD-ABK-06` (Этап 5):** UI и Compose-компоненты по макетам Figma (экран Abooks, 5 вкладок BottomNav, модалка глав `1309:12489`, ввод времени `PlayerMobileTimeInput` `1346:15503`, палитра mpoddy dark mode).
6. **`MPOD-ABK-07` (Этап 6):** Регрессионное тестирование, Room migration tests, diff review и сборка релизного APK.

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
