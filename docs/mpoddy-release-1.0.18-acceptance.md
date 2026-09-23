# mpoddy 1.0.18 (19) — Release Acceptance Task

## Актуальное состояние — закрыто 23.09.2026

`MPOD-REL-02` закрыта: исправление и отчёты отправлены в remote, итоговый APK собран из опубликованного commit `eb34273be19fd4ce593f282ae458a6ef26129c8b`. Файл: `build/release-handoff/mpoddy-1.0.18-19-eb34273-6880c4432152.apk`; SHA-256 `6880c44321522708c02fd8092196281830035f16dfd4737d54742ddc74a8935a`.

178/178 JVM, 65/65 instrumentation, debug/release lint без ошибок. Новый APK содержит тот же код и ресурсы, что прошли QA; изменена только запись Git revision. Обновление поверх существующего приложения на Pixel 9 проверено без потери данных. Итоговый handoff и существенные ограничения: `mpoddy-release-1.0.18-closure.md`; текущих активных задач нет (`mpoddy-current-tasks.md`).

Ниже сохранён исторический план и условия прежних запусков. Они не требуют повторной сборки, новой команды на уже выполненные этапы или возврата к старому APK. Более поздние решения и результаты закрытия выше имеют приоритет.

## Выполненный запуск — этап 1, 22.09.2026

По новой команде пользователя выполняется только проверка реальной работы подписок на уже собранном release APK. Она предшествует полному техническому gate ниже. Исполнитель — субагент GPT-5.6 Terra, Medium. Не пересобирать APK, не запускать полный набор автотестов/lint, не создавать новую среду и не обращаться к физическому телефону.

APK: `build/release-handoff/mpoddy-1.0.18-19-26112e1-b67cb330ccbe.apk`; SHA-256 `b67cb330ccbe1c8c32963e4c554106fd1b0868de0f3cb221ffac8f241e44b1bf`. Проверить checksum, package/version и выбранный `Pixel_9` API 37; установить поверх существующей версии без очистки данных и проверить видимую сохранность.

Обязательная последовательность реальных действий через UI:

1. Добавить `race.xml` с тремя эпизодами; повторное добавление не создаёт дубль.
2. Перевести race в revision 2, нажать Refresh: четвёртый эпизод появляется на текущем экране без навигации/перезапуска. Повторить без дублей; затем проверить сохранение после перезапуска.
3. Добавить baseline `aliases.xml` и `dc.xml` (по три эпизода). Для Refresh all задать `feed_revisions={"race":3,"aliases":2,"dc":2}` и `fail_feeds=["dc"]`: ожидаются 5/4/3 эпизодов соответственно и ошибка dc без потери старых данных.
4. Задать `fail_feeds=[]`, нажать штатный Retry: dc получает четвёртый эпизод; остальные не дублируются.
5. Проверить refresh одной обычной внешней подписки; отсутствие новых выпусков внешнего сервера не выдавать за проверку появления нового эпизода.

Новый параметр `/__control` — `feed_revisions`, объект с независимыми ревизиями 1–3 для каждой ленты; объект заменяется целиком. Ревизия N даёт N+2 эпизода. Старый `revision` остаётся fallback только для race. Проверка новой возможности QA-сервера: 1/1 focused test PASS; это не Android acceptance.

При первом воспроизводимом основном дефекте сохранить шаги, Expected/Actual, screenshot и узкое evidence запросов/логов, подтвердить одним повтором и остановить этап. Код не исправлять. Отчёт этапа — `build/qa-stage1-<timestamp>/stage1-report.md`; результат относится только к этапу 1. Прямой доступ к private Room не нужен для этих UI-проверок и не является blocker этапа 1.

## Роль и границы

Исполнитель — Android QA. Цель — проверить установленный production/release APK `com.prod.mpod`, а не только Gradle-задачи или debug-сборку.

QA не исправляет код, не меняет версию, не коммитит и не делает push. При первом воспроизводимом blocker остановить публикационный путь, сохранить evidence и вернуть `FAIL`. Google Play или иная публикация запрещены. Нельзя удалять приложение, выполнять `pm clear` или иначе стирать пользовательские данные для обхода ошибки.

Тестовые данные и точная методика подготовлены 21.09.2026 в приложении к этому документу. Это подготовка задания, а не выполненная приёмка. Запуск Gates A–G — только после команды пользователя начать QA. Передавать документ вместе с каталогом `tools/qa/`; отдельный новый чат или субагент автоматически не создаётся.

## Источник кандидата

Перед началом:

1. Обновить remote refs и подтвердить ветку `codex/qa-obvious-bugs`.
2. Зафиксировать exact published HEAD `26112e1f08bec8233151cbb637fa0432d0f080b7`; local HEAD должен совпадать с `origin/codex/qa-obvious-bugs`. Проверка remote относится к Git-репозиторию; у приложения нет backend-сервера.
3. Подтвердить наличие в истории минимум:
   - `3fb7f45` — Mark all listened consistency и cleanup races;
   - `67c0da0` — единственный process-level владелец Smart Listening;
   - `686423a` — запрет destructive Room migration и schema v1 baseline;
   - `e7cc41e` — RSS namespace URI compatibility.
4. Проверить `applicationId = com.prod.mpod`, `versionName = 1.0.18`, `versionCode = 19`.
5. Проверить `git status --short`. Известные локальные изменения подготовки: этот документ, `docs/mpoddy-current-tasks.md` и `tools/qa/`; они не меняют код APK. Пользовательский `AGENTS.md` не является частью кандидата. Не сбрасывать и не коммитить эти файлы. Изменения исходников приложения, ресурсов или Gradle относительно указанного commit — основание остановить сборку. Для QA-пакета отдельно сохранить checksum переданных файлов; не приписывать их опубликованному commit APK.

Если HEAD, версия или история не совпадают — `FAIL`, APK не собирать и не устанавливать.

## Устройства

Вся согласованная приёмка выполняется на Pixel 9 AVD, API 37, с release package `com.prod.mpod`. Физический телефон в этот release gate не входит и его отсутствие не является blocker.

Записать модель AVD, API, ABI, serial и наличие существующего `com.prod.mpod` до установки. В отчёте называть среду эмулятором, не телефоном.

Использовать существующий `Pixel_9`; не скачивать system images, не создавать другой AVD и не менять его образ ради диагностики. Ограничение доступа к приватной Room на этом образе описано в приложении, раздел «Как подтверждать внутреннее состояние». Прочитать его до начала QA: отсутствие прямого доступа нельзя скрыть итоговым `PASS`.

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

Конкретный feed, порядок задержек и границы доказательства: приложение, сценарий D1.

### D2. Cleanup/re-add

1. Добавить эпизод в playlist и дождаться downloaded-state.
2. Пометить прослушанным/запустить cleanup и сразу повторно сделать эпизод непрослушанным и добавить в playlist.
3. Дождаться новой загрузки.

Expected: новый файл существует, `isDownloaded=true`, Room содержит его точный путь, episode остаётся в playlist; старый cleanup не обнуляет состояние новой загрузки.

Сетевой сервер управляет загрузкой, но не может остановить локальный delete/CAS внутри cleanup. Детерминированное пересечение этих операций проверяют перечисленные в приложении существующие JVM-тесты; ручной D2 не объявлять самостоятельным доказательством точного внутреннего interleaving.

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

---

## Приложение — готовые тестовые данные и методика

### Состав пакета и локальная проверка

- `tools/qa/fixture_server.py` — локальный HTTP-сервер на Python 3.9+, только стандартная библиотека. Сам не вызывает ADB, не запускает эмуляторы и не устанавливает приложения.
- `tools/qa/fixtures/` — готовые XML/OPML, WAV, PNG и `manifest.json` с размерами и SHA-256 каждого файла.
- `tools/qa/test_support.py` — проверки самого QA-инструмента; не входят в counts Android-приложения.

Результат подготовки: **8/8 локальных тестов QA-инструмента PASS**, проверены checksum/размеры всех 10 файлов manifest и синтаксис валидных XML/OPML. `invalid.xml` намеренно невалиден. Gates A–G приложения не запускались; воспроизведение fixtures установленным Android-приложением в рамках подготовки не проверялось.

Данные синтетические. Аудио — тихий тон 440 Гц, PCM WAV, mono 16 kHz, 16 bit, 30 секунд, 960044 байта. Artwork — сине-жёлтая клетка 64×64. Внешние ленты, аккаунты и интернет для этих fixtures не нужны. Fixtures не заменяют проверку реальной RSS-ленты, если она требуется основным сценарием.

Локальная проверка пакета, без Android/ADB:

```bash
python3 -B -m unittest discover -s tools/qa -p test_support.py -v
```

Повторная генерация того же набора в отдельный каталог, без запуска сервера:

```bash
python3 -B tools/qa/fixture_server.py --export-only --output build/qa-fixtures-check
```

### Запуск fixtures во время QA

Все команды ниже выполняет тестировщик после команды на старт приёмки, из корня проекта. Команды ADB всегда адресованы выбранному эмулятору; физический телефон не используется. Путь `QA_ADB` указан для текущего Mac; на другом компьютере заменить только путь к установленному Android SDK.

```bash
export QA_ADB='/Users/cross/Library/Android/sdk/platform-tools/adb'
```

```bash
"$QA_ADB" devices -l
```

В следующей команде заменить значение на serial проверенного `Pixel_9`, не выбирать первое подключённое устройство автоматически:

```bash
export QA_SERIAL='<pixel_9_serial>'
```

```bash
"$QA_ADB" -s "$QA_SERIAL" emu avd name
```

```bash
"$QA_ADB" -s "$QA_SERIAL" shell getprop ro.build.version.sdk
```

Создать новое имя каталога evidence для каждого запуска, чтобы не перезаписать прошлый журнал:

```bash
export QA_RUN="$(mktemp -d "$PWD/build/qa-1.0.18-XXXXXXXX")"
```

Каталог `build/` должен существовать после Gate A. В отдельном терминале с тем же значением `QA_RUN` запустить сервер и оставить его работающим:

```bash
python3 -B tools/qa/fixture_server.py --output "$QA_RUN"
```

Сервер слушает только loopback Mac. Связать loopback эмулятора с ним:

```bash
"$QA_ADB" -s "$QA_SERIAL" reverse tcp:8765 tcp:8765
```

```bash
curl --fail --silent --show-error http://127.0.0.1:8765/__state
```

Этот curl подтверждает только работу на Mac. Доступ из приложения подтверждается записью его запроса в `requests.jsonl` после добавления feed. Если порт занят, выяснить владельца, не завершать чужой процесс. Для этих fixtures используется Proxy Direct; записать прежние настройки и восстановить после проверки. После перезапуска эмулятора проверить reverse заново.

В отдельном терминале наблюдать начало и завершение запросов:

```bash
tail -f "$QA_RUN/requests.jsonl"
```

Журнал содержит время Unix, request id, path, User-Agent, Range, состояние задержек на момент начала и события `start` / `end` / `disconnected`. `end` означает отправку HTTP-ответа сервером, а не commit в Room. Новые настройки действуют только на новые запросы. Логи не содержат cookies/authorization.

### Адреса и ожидаемые значения

| Адрес в Add RSS | Назначение и ожидаемый результат |
|---|---|
| `http://127.0.0.1:8765/race.xml` | `QA race`, 3 эпизода; при revision 2 сервер возвращает четвёртый, сохраняя GUID первых трёх |
| `http://127.0.0.1:8765/aliases.xml` | iTunes `p:`, Content `c:`, Dublin Core `d:`; author `QA Alias Author` |
| `http://127.0.0.1:8765/dc.xml` | author только через `d:creator`: `QA Dublin Core Author` |
| `http://127.0.0.1:8765/import-a.xml` и `import-b.xml` | Только для OPML/refresh smoke; не добавлять вручную до первого импорта |
| `http://127.0.0.1:8765/invalid.xml` | HTTP 200, намеренно незавершённый XML: recoverable parse error без подписки |
| `http://127.0.0.1:8765/missing.xml` | HTTP 404: recoverable error без подписки |

У каждой валидной ленты три эпизода с title `QA <feed> episode N`, GUID `qa-<feed>-N`, duration 30 секунд и enclosure `/audio/<feed>-N.wav`. Дата эпизода N — `2026-09-21T10:00:0N Z`, в UI может отображаться в часовом поясе устройства. Ожидаемые Show notes: эпизоды 1/3 — `QA content 1` / `QA content 3`, эпизод 2 — `QA summary 2`. Summary подкаста — `QA channel summary`.

Неизвестный namespace `x:` содержит `POISON`-значения и ложные duration/enclosure/pubDate. Они не должны подменить распознанные поля. Для D5 добавить и `aliases.xml`, и `dc.xml`, затем повторить refresh и перезапуск приложения. Метаданные, не показанные в UI, проверять только доступными доказательствами из раздела о внутреннем состоянии.

### OPML и ожидаемые counts

Передать готовый файл в Downloads выбранного эмулятора:

```bash
"$QA_ADB" -s "$QA_SERIAL" push tools/qa/fixtures/import.opml /sdcard/Download/mpoddy-qa-import.opml
```

Выбрать его через штатный Add → OPML picker. В нём три записи: import-a, import-b, повтор import-a. Если этих подписок ещё нет, результат **imported 2 / skipped 1**. Повторный импорт — **imported 0 / skipped 3**. Если они уже существовали, записать исходное состояние и скорректировать ожидаемые counts, не удаляя чужие данные. Cancel — без мутации и ложного результата.

Экспортировать библиотеку через Settings в отдельный OPML. Проверить XML и наличие каждой текущей подписки ровно один раз; экспорт всей библиотеки не обязан совпадать побайтно с входной fixture.

### Управление задержками и ошибками

Обычный режим / сброс настроек сервера (не сбрасывает данные приложения):

```bash
curl --fail --silent --show-error -H 'Content-Type: application/json' -d '{"revision":1,"feed_revisions":{},"feed_delay":0,"audio_delay":0,"fail_feeds":[],"fail_audio":false}' http://127.0.0.1:8765/__control
```

`feed_delay` — секунды до отправки ответа, 0–20; меньше read timeout приложения 30 секунд. `audio_delay` — пауза между блоками WAV по 8192 байта, 0–1 секунда. При 0.25 передача длится примерно 29 секунд, при 0.75 — 88 секунд. Smart Listening начинает сетевую загрузку примерно через **15 секунд** после добавления в playlist. Считать начало по журналу, а не только по таймеру.

Partial refresh failure — только тестовая лента race возвращает 503:

```bash
curl --fail --silent --show-error -H 'Content-Type: application/json' -d '{"fail_feeds":["race"]}' http://127.0.0.1:8765/__control
```

Все fixture-ленты возвращают 503:

```bash
curl --fail --silent --show-error -H 'Content-Type: application/json' -d '{"fail_feeds":["race","aliases","dc","import-a","import-b"]}' http://127.0.0.1:8765/__control
```

Это all-failed Refresh all только если библиотека состоит из этих fixtures. При наличии других подписок не выдавать его за all-failed всей библиотеки. Сетевой offline/retry из Gate F выполняется отдельно штатным отключением/возвратом сети на выбранном эмуляторе; не удалять подписки ради изменения состава библиотеки.

Контролируемая ошибка аудио (для ещё не скачанного эпизода; локально скачанный файл продолжит играть):

```bash
curl --fail --silent --show-error -H 'Content-Type: application/json' -d '{"fail_audio":true}' http://127.0.0.1:8765/__control
```

После проверки вернуть обычный режим и выполнить retry.

### D1 — refresh, Mark all listened и отмена загрузки

1. В обычном режиме добавить `race.xml`, убедиться, что есть ровно эпизоды 1–3. Добавить episode 1 в playlist и дождаться downloaded-state. Не запускать playback: он создаёт дополнительные audio-запросы.
2. Включить медленное аудио:

```bash
curl --fail --silent --show-error -H 'Content-Type: application/json' -d '{"audio_delay":0.75}' http://127.0.0.1:8765/__control
```

3. Добавить episode 2 в playlist. Дождаться `start` для `/audio/race-2.wav` с User-Agent `mpoddy/1.0.18 (Android Podcast Player)`.
4. Подготовить поздний приход episode 4:

```bash
curl --fail --silent --show-error -H 'Content-Type: application/json' -d '{"revision":2,"feed_delay":15}' http://127.0.0.1:8765/__control
```

5. В UI запустить Refresh выбранного `QA race`. После `start /race.xml`, до его `end`, выполнить Mark all listened. Зафиксировать время/видео, что это попало в 15-секундное окно; если нет, наблюдение не доказывает нужный порядок.
6. После завершения refresh и cleanup проверить, что episode 1–3 listened и отсутствуют в playlist. Episode 4 может остаться unlistened, если пришёл после snapshot. Зафиксировать этот результат **до** повторного Mark all listened.
7. Повторный Mark all listened при новом unlistened episode 4 закономерно меняет его состояние: это не проверка no-op. Дождаться полного завершения, затем выполнить ещё один повтор на полностью listened подкасте — он должен быть no-op.
8. Вернуть обычный режим. Возврат revision 1 не удаляет уже добавленный episode 4 из Room. При повторе использовать уже существующий состав как новый scope; не ожидать снова чистого состояния 1–3 и не очищать БД.

Сохранить request ids refresh/download, скриншоты до/после, logcat и результат соответствующих автоматических тестов. `disconnected` подтверждает закрытие соединения со стороны клиента; само по себе не доказывает отсутствие позднего commit или orphan-файла.

### D2 — cleanup/re-add

1. В обычном режиме вернуть episode 1 в unlistened, добавить в playlist, дождаться downloaded-state.
2. Зафиксировать исходное состояние, пометить listened, сразу вернуть в unlistened и добавить в playlist. Делать действия через UI, не редактировать БД.
3. Дождаться новой загрузки и устойчивого downloaded-state, перейти между экранами, перезапустить процесс и убедиться, что эпизод остаётся в playlist и локально воспроизводится.
4. Для проверки локального чтения после загрузки включить `fail_audio=true`, перезапустить процесс и запустить этот эпизод: он должен играть без нового audio-запроса. Это подтверждает пригодность локального файла; не доказывает отсутствие других orphan-файлов. Вернуть `fail_audio=false`.

Точный момент удаления старого файла и условного сброса metadata сетевыми задержками не управляется. Обязательная детерминированная часть — уже существующие тесты из полного Gate A:

- `SubscriptionsMarkAllListenedConsistencyTest.cleanupWithoutMetadata_readdedDownloadCommitsBeforeCleanupReturns_remainsConsistent`;
- `SubscriptionsMarkAllListenedConsistencyTest.cleanupWithOldMetadata_readdWaitsForReset_thenDownloadsConsistently`;
- `SubscriptionsMarkAllListenedConsistencyTest.overlappingRepositoryAndDirectCleanup_readdMustNotLoseNewDownloadMetadata`;
- `SubscriptionsMarkAllListenedConsistencyTest.cancelledEpisodeReaddedWhileCommitFinishes_keepsOldOwnerUntilItCanReschedule`.

Не запускать их повторно, если в отчёте полного Gate A уже есть их успешные результаты. Не подменять ими факт выполнения ручного сценария на release.

### D3 — Activity recreation во время одной загрузки

1. Выбрать ещё не скачанный episode 3, установить `audio_delay=0.75` и добавить в playlist.
2. Дождаться `start /audio/race-3.wav`. В течение передачи повернуть экран несколько раз, перейти Player → Subscriptions → Settings, свернуть и вернуть приложение. Playback не запускать, из playlist не удалять.
3. Дождаться завершения запроса и downloaded-state. Проверить пригодность локального файла методом D2, затем вернуть обычный режим.
4. Сопоставить requests с User-Agent и lifecycle-действиями. В этом сценарии ожидается одна полная загрузка; дополнительные соединения при сетевой ошибке сначала отличить от двух одновременных владельцев. Число HTTP-запросов само по себе не равно числу jobs. Автоматическая проверка владельца — `SmartListeningOwnershipTest` и `SmartListeningManagerTest.startObserving_calledTwice_idempotentAndSingleJob` из Gate A.

### Как подтверждать внутреннее состояние — существенное ограничение

В текущем коде БД — `databases/mpoddy.db`, таблицы `podcasts`, `episodes`, `playlist_items`; аудио — `files/podcasts/ep_<id>_<timestamp>.wav`. `isDownloaded`, `localFilePath`, `isListened`, позиция эпизода находятся в Room. **Active episode id, speed и настройки находятся в DataStore**, поэтому одной копии SQLite для всей persistence-проверки недостаточно.

Существующий `Pixel_9` использует Google Play API 37/16 КБ, `user` build, `ro.debuggable=0`. При предварительной диагностике получен ответ `adbd cannot run as root in production builds`; root-консоль также не дала рабочего доступа. `run-as` не является способом чтения non-debuggable release. Проверенный способ прямого чтения его private Room/files в рамках этой подготовки **не получен**. Скрипт обхода защиты или создания другого AVD в пакет не входит.

Разделять доказательства:

| Что проверяется | Доступное evidence |
|---|---|
| Реальный release UI, playlist/listened, сохранность после reinstall/relaunch | Скриншоты/видео до и после на `com.prod.mpod` |
| Загрузка, отмена, повторный запрос, работа из локального файла | `requests.jsonl`, logcat `SmartListening`, сценарий с `fail_audio=true` после скачивания |
| Точное пересечение транзакции/cleanup, условный сброс metadata, отсутствие late commit в моделируемом порядке | Результаты существующих `SubscriptionsMarkAllListenedConsistencyTest`, `SmartListeningManagerTest`, `MarkAllListenedTransactionTest` из Gate A |
| Реальные значения `localFilePath`, содержимое Room и отсутствие любых orphan-файлов установленного release | Требуют прямого доступа к private data; на текущем AVD не подтверждены |

UI, логи и успешные JVM-тесты нельзя назвать прямой инспекцией Room release-приложения. Если прямые проверки D1–D3 остаются обязательными, их недоступность означает **FAIL — environment/evidence blocker**, а не дефект приложения. Остальные согласованные проверки можно описать по факту, но полный `PASS` при незакрытом обязательном evidence не выдавать. Не менять критерий самостоятельно и не создавать другой эмулятор/диагностическую сборку для обхода ограничения. Решение о таком изменении среды или критериев — отдельное действие после отчёта.

### Evidence и завершение работы

В отдельном терминале во время QA сохранять logcat без предварительного `logcat -c`:

```bash
"$QA_ADB" -s "$QA_SERIAL" logcat -v threadtime SmartListening:V AndroidRuntime:E '*:S' > "$QA_RUN/smart-listening-logcat.txt"
```

Для первого blocker дополнительно сохранить полный logcat в локальный evidence и скриншот; перед передачей проверить отсутствие секретов/чужих личных данных. Сопоставлять события с системным временем эмулятора, не предполагать, что оно точно равно времени Mac.

Итоговый пакет: exact APK commit, APK metadata из Gate B, отчёты Gate A с точными counts, `requests.jsonl`, fixture `manifest.json`, скриншоты/видео по сценариям, logcat и итоговый отчёт Gates A–G. Отдельно перечислить недоказанные внутренние состояния; не переносить старые test counts в новый прогон.

После сохранения evidence восстановить proxy/network/rotation и другие изменённые настройки. Остановить только свой fixture-сервер и свой процесс записи logcat через Ctrl-C в их терминалах. Удалить только созданное для QA перенаправление:

```bash
"$QA_ADB" -s "$QA_SERIAL" reverse --remove tcp:8765
```

Библиотеку, файлы приложения, APK и каталоги evidence не очищать. Подготовка этого пакета не даёт разрешения на публикацию или запуск приёмки без команды пользователя.
