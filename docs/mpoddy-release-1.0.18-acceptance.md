# mpoddy 1.0.18 (19) — Release Acceptance Task

## Роль и границы

Исполнитель — Android QA. Цель — проверить установленный production/release APK `com.prod.mpod`, а не только Gradle-задачи или debug-сборку.

QA не исправляет код, не меняет версию, не коммитит и не делает push. При первом воспроизводимом blocker остановить публикационный путь, сохранить evidence и вернуть `FAIL`. Google Play или иная публикация запрещены. Нельзя удалять приложение, выполнять `pm clear` или иначе стирать пользовательские данные для обхода ошибки.

## Источник кандидата

Перед началом:

1. Обновить remote refs и подтвердить ветку `codex/qa-obvious-bugs`.
2. Зафиксировать exact published HEAD; local HEAD должен совпадать с `origin/codex/qa-obvious-bugs`.
3. Подтвердить наличие в истории минимум:
   - `3fb7f45` — Mark all listened consistency и cleanup races;
   - `67c0da0` — единственный process-level владелец Smart Listening;
   - `686423a` — запрет destructive Room migration и schema v1 baseline;
   - `e7cc41e` — RSS namespace URI compatibility.
4. Проверить `applicationId = com.prod.mpod`, `versionName = 1.0.18`, `versionCode = 19`.
5. Проверить чистоту task-файлов через `git status --short`. Пользовательский `AGENTS.md` не является частью кандидата и не должен попадать в commit/APK evidence.

Если HEAD, версия или история не совпадают — `FAIL`, APK не собирать и не устанавливать.

## Устройства

Вся согласованная приёмка выполняется на Pixel 9 AVD, API 37, с release package `com.prod.mpod`. Физический телефон в этот release gate не входит и его отсутствие не является blocker.

Записать модель AVD, API, ABI, serial и наличие существующего `com.prod.mpod` до установки. В отчёте называть среду эмулятором, не телефоном.

## Gate A — автоматические проверки

Из exact published HEAD выполнить:

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:assembleDebugAndroidTest
```

```bash
./gradlew :app:lintDebug
```

```bash
./gradlew :app:lintRelease
```

```bash
ANDROID_SERIAL=<pixel_9_serial> ./gradlew :app:connectedDebugAndroidTest
```

```bash
./gradlew :app:assembleRelease
```

```bash
git diff --check
```

Записать точные counts, failures/errors/skips и BUILD SUCCESSFUL для каждой команды. При transient build-tool ошибке допускается один последовательный повтор с объяснением; тестовый или lint finding повтором не скрывать.

## Gate B — идентификация APK

Для собранного release APK:

1. Записать абсолютный путь, размер и SHA-256.
2. Проверить подпись через `apksigner verify`.
3. Через `aapt dump badging` подтвердить package `com.prod.mpod`, versionCode `19`, versionName `1.0.18`, minSdk 26 и targetSdk 35.
4. Зафиксировать, используется release keystore или предусмотренный проектом debug fallback. Не публиковать APK, подписанный fallback-ключом, как публичный production release.
5. Сохранить exact commit рядом с APK evidence.

## Gate C — установка без потери данных

1. Проверить, установлен ли `com.prod.mpod`, и записать текущую версию.
2. Если установлена 1.0.17 (18) с данными — обновить через `adb install -r`, без uninstall/clear.
3. Если приложение не установлено — выполнить fresh install, создать контрольные данные, затем повторно установить тот же APK через `adb install -r` и проверить сохранение данных. Явно отметить, что это не полноценный upgrade 18→19.
4. После установки подтвердить package/version через package manager.
5. Запустить приложение и проверить отсутствие `FATAL EXCEPTION`, ANR и crash loop.

Expected: существующие subscriptions, playlist, listened/downloaded flags, active episode, position и Settings не исчезают после обновления/reinstall. Room не пересоздаёт базу разрушительно.

## Gate D — глубокая проверка изменённых рисков

### D1. Mark all listened / Room / queue / cleanup

Подготовить подкаст минимум с тремя непрослушанными эпизодами; два добавить в playlist, дождаться хотя бы одной Smart Listening загрузки.

1. Во время refresh выбранного подкаста выполнить `Mark all listened`.
2. Дождаться завершения обоих действий и повторно открыть Subscriptions и Player.
3. Проверить: все эпизоды из operation scope прослушаны; они отсутствуют в playlist/queue; активная загрузка не выполняет поздний commit; локальные файлы и `isDownloaded/localFilePath` согласованы.
4. Сразу повторить `Mark all listened`.

Expected: повтор — no-op без дубликатов, stale queue, orphan-файла или crash. Новый эпизод, пришедший строго после transaction snapshot, может остаться непрослушанным — это допустимая сериализация.

### D2. Cleanup/re-add

1. Добавить эпизод в playlist и дождаться downloaded-state.
2. Пометить прослушанным/запустить cleanup и сразу повторно сделать эпизод непрослушанным и добавить в playlist.
3. Дождаться новой загрузки.

Expected: новый файл существует, `isDownloaded=true`, Room содержит его точный путь, episode остаётся в playlist; старый cleanup не обнуляет состояние новой загрузки.

### D3. Smart Listening lifecycle owner

1. Добавить недогруженный эпизод в playlist.
2. Несколько раз пересоздать Activity: rotate, background/foreground, перейти между Player/Subscriptions/Settings.
3. Наблюдать network/download и конечный файл.

Expected: одна логическая загрузка, без duplicate request/files/jobs; Activity recreation не останавливает process-level observer.

### D4. Room migration safety

1. Обновить приложение поверх существующей версии без очистки данных.
2. Проверить subscriptions, episodes, playlist и active playback state.
3. Перезапустить процесс и повторно проверить данные.

Expected: version 1 schema открывается без потери данных. Не симулировать несуществующую migration 1→2 и не повреждать пользовательскую БД ради теста.

### D5. RSS namespace aliases end-to-end

Добавить контролируемый валидный RSS 2.0 feed, где iTunes, Content и Dublin Core URI объявлены под нестандартными prefixes.

Expected: корректно отображаются author, artwork, summary/description и duration; title/guid/enclosure/pubDate также корректны. Теги с тем же local name из неизвестного namespace игнорируются. Feed сохраняется в Room и переживает повторный refresh.

## Gate E — основной пользовательский smoke

### Startup и navigation

- Cold launch с существующими данными открывает Subscriptions без login/backend gate.
- Переключение Player / Subscriptions / Settings не создаёт дубликаты экранов.
- Add podcast modal открывается и закрывается Back без мутации.
- После background/foreground выбранный раздел и данные остаются корректными.

### Add / RSS / OPML

- Валидный RSS добавляется и создаёт podcast/episodes.
- Duplicate feed отклоняется без второй подписки.
- Невалидный или недоступный URL показывает recoverable error и не стирает библиотеку.
- OPML import: cancel — no-op; валидный файл — корректные imported/skipped counts.
- OPML export создаёт читаемый файл; cancel не показывает ложный success/error.

### Subscriptions

- Carousel selection, artwork, header, counts и episode list относятся к одному podcast после swipe и settle.
- Show all / Show unlistened отображают правильные данные.
- Refresh one и Refresh all success обновляют Room/UI.
- Partial/all-failed refresh сохраняют предыдущий successful `Last refresh`; retry остаётся доступен.
- Unsubscribe + Undo сохраняет podcast; окончательное удаление проверять только на тестовой подписке.

### Playlist и Player

- Add/remove playlist немедленно согласуются между Subscriptions и Player.
- Tap queue item запускает правильный episode без самопроизвольного autoplay другого элемента.
- Play/Pause, seek −10/+15, progress drag и speed работают; position/speed сохраняются.
- Reorder сохраняет порядок после перехода между экранами и перезапуска процесса.
- Natural completion помечает episode listened, удаляет из queue, очищает файл и корректно выбирает следующий; последний episode завершает playback без stale card.
- Background notification/lock screen показывают правильные metadata и Play/Pause.

### Settings

- Theme System/Light/Dark применяется и сохраняется.
- Auto refresh state сохраняется.
- Proxy Direct/HTTP/SOCKS5 проходит validation; не менять реальную рабочую конфигурацию без возможности восстановить её.
- Build Info показывает `Current app build: 1.0.18`.
- Smart Listening controls отсутствуют.

## Gate F — lifecycle и persistence

- Rotate во время Add modal/input сохраняет введённый URL и состояние.
- Process stop/relaunch сохраняет library, playlist, active episode, position, speed, theme и proxy settings без autoplay.
- Offline launch не стирает Room; refresh failure оставляет прежние данные.
- После возврата сети retry восстанавливает работу.
- Проверить отсутствие новых AndroidRuntime crashes и повторяющихся критических Smart Listening/Room errors в logcat.

## Gate G — ограничения выбранной среды

Физический телефон не используется по решению пользователя. Audio focus с другим приложением, реальные Bluetooth/проводные route changes и OEM-specific background behavior отметить `NOT RUN — physical device outside agreed gate`. Это ограничение не превращает успешный Pixel 9 AVD gate в `NEEDS PHYSICAL DEVICE`.

## Формат результата

Итог только один: `PASS` или `FAIL` для согласованного Pixel 9 AVD gate.

Отчёт обязан содержать:

- exact commit и соответствие remote;
- APK path, SHA-256, size, signature type, package/version;
- устройства/API/serial;
- результаты Gates A–G с PASS/FAIL/NOT RUN;
- для каждого FAIL: первый точный шаг, Expected, Actual, частота, screenshot/logcat и сохранность данных;
- список унаследованных, но не перепроверенных сценариев;
- ограничения эмулятора и физического устройства;
- подтверждение, что публикация, uninstall и data clear не выполнялись.

При blocker не исправлять код самостоятельно. Вернуть отчёт текущей Codex-задаче для отдельной rework-задачи.
