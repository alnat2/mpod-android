# Третий этап: техническая регрессия — 23.09.2026

**PASS. 178/178 JVM tests, 65/65 instrumentation tests; debug/release lint — 0 errors.** Gradle завершился `BUILD SUCCESSFUL in 12m 1s`. Полный прогон выполнен один раз, без исключения упавших тестов и без повторов. Offline-повтор не потребовался: lint завершил сетевое ожидание самостоятельно.

## Проверяемое состояние

- HEAD `26112e1f08bec8233151cbb637fa0432d0f080b7` + локальное исправление плеера в `HomeScreen.kt` и `HomePlaybackIntentTest.kt`. Это не чистый published commit.
- SHA-256 diff этих двух файлов: `ff76e126fa3a2b4fa36d6d66f217ad6d7202928621254da7470ea395b8647776`; совпадает с patch, сохранённым при сборке исправленного release APK.
- APK: `build/release-handoff/mpoddy-1.0.18-19-player-retry-fix-20260923.apk`, 4 915 716 байт; SHA-256 `585949d10d2f4e4748f8a7ceacd263b5377c44b42b9188f7af2d7c1c5588c39c`. Release APK не пересобирался и не переустанавливался. Его checksum повторно подтверждён чтением установленного APK после тестов.
- Production package `com.prod.mpod`, 1.0.18 (19); подпись — ранее проверенный fallback, см. `mpoddy-fix-and-stage2-2026-09-23-report.md`.
- Instrumentation: существующий Pixel_9, API 37, arm64-v8a, `emulator-5554`; отдельный debug/test package. После завершения Gradle на устройстве остался production package. Удаление/очистка production-приложения не выполнялись.
- Физический телефон не использовался. Remote refs в этом этапе не обновлялись; соответствие patch локальному APK подтверждено отдельно.

## Выполненная команда и результаты

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_SERIAL=emulator-5554 ./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --console=plain
```

| Проверка | Результат |
|---|---|
| `testDebugUnitTest` | 178 tests, 0 failures, 0 errors, 0 skipped; 27 suites, новый прогон |
| `assembleDebugAndroidTest` | Успешно, test APK up-to-date; необходимая debug-сборка приложения обновлена Gradle |
| `connectedDebugAndroidTest` | 65 tests, 0 failures, 0 errors, 0 skipped; 11 классов, новый прогон |
| `lintDebug` | 0 errors; 117 warnings, 1 information |
| `lintRelease` | 0 errors; 96 warnings, 1 information |
| `git diff --check` | PASS |

Количество и категории предупреждений lint совпали с сохранённым предыдущим отчётом. Это PASS по отсутствию ошибок, а не отсутствие предупреждений. Их устранение в этот этап не входило.

Непосредственно проверены риски оставшегося технического этапа: 19 JVM-тестов согласованности Mark all listened, 21 SmartListeningManager, 1 ownership, 14 DownloadFinalizer, 4 исправления playback intent; на реальном Android — 8 транзакционных Room-тестов и 1 открытие сохранённой схемы v1 с существующими данными. Последнее не является миграцией 1→2: такой миграции в кандидате нет. Также прошли все остальные существующие JVM/UI-тесты, включая refresh status, подписки, Player, Settings, формы и accessibility.

## Контроль после тестов

Исходный release APK и все шесть подписок остались доступны. На экране Planet Money — 356 выпусков, включая выпуск от 23.09; в Settings сохранено `Last refresh today at 23.09 15:40`. Приложение возвращено на Settings. Crash buffer содержит только прежний системный Bluetooth crash от 22.09; новых записей от 23.09 нет.

Код приложения в этом этапе не менялся. Ручные сценарии прежних этапов, проверка файлов и сборка release повторно не запускались. Их evidence остаются в `mpoddy-fix-and-stage2-2026-09-23-report.md` и `mpoddy-open-items-2026-09-23-report.md`. Проверка звука на слух, физические аудиоустройства/OEM и длительное расписание не добавлялись к ранее согласованному объёму.

## Evidence и итоговый handoff

Каталог `build/qa-stage3-20260923/`: `gradle.log`, `results.json`, `unit/`, `instrumentation/`, XML/TXT/HTML lint, `source.patch`, `device-after.json`, `release-after.xml`, `release-library-after.xml`, `crash-after.txt`. Задержка lint подтверждена стеком ожидания Google Maven metadata; предупреждения/проверки не отключались.

Третий технический этап завершён, новых дефектов не обнаружено. Прежние два ограничения второго этапа закрыты отдельным отчётом. Выполненные проверки относятся к локальному исправленному кандидату. Исправление и отчёты ещё не закоммичены и не опубликованы; условие поставки APK из опубликованного исправленного commit пока не выполнено. Коммит, push и публикация в этом этапе не выполнялись.
