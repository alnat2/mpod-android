# mpod Android — handoff в новый чат

Дата: 2026-09-05
Проект: `/Users/cross/Documents/Vm-IX/my-projects/android-mpod`
Ветка: `codex/qa-obvious-bugs`
Базовый HEAD: `8686b0d`

## Цель работы

Нужно было провести финальную приёмку текущей release-сборки на физическом Android-телефоне без повторных установок и очистки данных. План проверки находится в `docs/android-qa-final-phone-acceptance-task.md`:

1. базовый smoke и восстановление состояния после force-stop;
2. runtime-переключение `Direct -> SOCKS5 -> HTTP -> Direct` только через UI приложения;
3. media notification и lock screen;
4. audio focus и noisy/headset route;
5. естественное завершение, локальное воспроизведение Smart Listening и cleanup;
6. итоговый отчёт `docs/android-qa-final-phone-acceptance-report.md`, затем решение Team Lead о commit/push.

Владелец продукта отдельно исключил проверку гарнитуры. Её нельзя считать ни Passed, ни Failed: статус — `Deferred by product owner`.

## Сборка и устройство

- APK: `artifacts/mpoddy-1.0.17-18-phone-acceptance.apk`.
- SHA-256: `cd105581f2d25ae393d06e2b3482bf3ece1c30e45d871a28bbeac3501ae051dc`.
- Размер: `4_915_716` bytes.
- Package: `com.prod.mpod`.
- Version: `1.0.17 (18)`; minSdk `26`; targetSdk `35`.
- Телефон: Xiaomi `23021RAA2Y`, device `topaz`, Android `15`.
- APK был установлен на телефон ровно один раз в рамках этой приёмки. Повторной установки и очистки данных не было.
- Последний ADB endpoint: `192.168.0.215:40367`.
- В конце сессии endpoint перешёл в состояние `offline`; для продолжения потребуется новый Wi-Fi debugging endpoint либо повторный `adb connect`, если старый порт снова станет доступен.
- Одновременно подключён эмулятор `emulator-5554`; его не использовать. Любая команда должна явно указывать физический serial.

## Что было сделано до телефона

- Полный non-device quality gate принудительно прогнан с `--rerun-tasks`:
  `testDebugUnitTest lintDebug lintRelease assembleDebugAndroidTest assembleRelease`.
- Результат: 133/133 Gradle tasks выполнены, build successful.
- Unit tests: 144, failures/errors/skipped: 0.
- `lintDebug` и `lintRelease`: 0 issues.
- Release APK собран с R8.

## Что выполнено на телефоне

### PHONE-01 — Passed

Субагент установил acceptance APK один раз и успел проверить:

- cold launch без crash/ANR;
- play/pause;
- seek `+15/-10`;
- скорость `1.5x`;
- сохранение позиции, скорости и очереди после force-stop;
- отсутствие autoplay после повторного запуска.

После этого субагент исчерпал usage limit и не создал отчёт. Проверку продолжил Team Lead на той же установке без очистки данных.

### PHONE-02 — почти полностью Passed, один evidence gap

Прокси переключались только через Settings приложения, без перезапуска и без ручной правки DataStore/БД.

#### SOCKS5

- SOCKS5: `192.168.0.223:1081`.
- В proxy log зафиксирован source IP телефона `192.168.0.215`.
- RSS: destination `192.168.0.223:8765`, fixture `/feed/valid`.
- Artwork: fixture `/artwork/alpha.png`.
- Smart Listening: после debounce пришёл `/audio/short_a.mp3`; в UI появился `Downloaded`.
- Media3: до появления downloaded-state запущен выпуск «542. Новинки Chrome 152…»; MediaSession была `PLAYING`, а SOCKS log в ту же секунду показал destination `web-standards.ru:443`.

#### HTTP

- HTTP proxy: `192.168.0.223:8081`.
- После UI-переключения без рестарта Refresh All дал реальные CONNECT/GET от `192.168.0.215`.
- Однозначный RSS: `GET 192.168.0.223:8765/feed/valid` в HTTP proxy log.
- Media3: при запуске недогруженного выпуска «В параллельных мирах много гигабайт» MediaSession была `PLAYING`, HTTP log показал `api.mave.digital:443` и `cdn.mave.digital:443`.
- Smart Listening: после debounce тот же выпуск получил `Downloaded`; сетевые соединения прошли через HTTP proxy.
- Отдельный новый artwork-request через HTTP не был получен: обложка попала в cache. Поэтому строгое требование задания «уникальный HTTP artwork request» остаётся единственным пробелом доказательств PHONE-02. Это не подтверждённый дефект приложения.

#### Возврат в Direct

- Proxy toggle выключен через UI; XML подтвердил `checked="false"`.
- Перед direct refresh: SOCKS log — 19 строк, HTTP log — 9 строк, fixture — 6 строк.
- После refresh `Test Podcast Alpha`: SOCKS — 19, HTTP — 9, fixture — 7.
- Новый `/feed/valid` появился только в fixture log. Возврат в Direct подтверждён.

### PHONE-03 — Passed

- Системная media card отображает mpoddy, корректные episode/podcast metadata и Play/Pause.
- Нажатие Play в системной шторке перевело mpod MediaSession в `PLAYING`; повторное нажатие — в `PAUSED`.
- На lock screen отображались те же metadata и Play/Pause.
- Скриншоты и UI XML сохранены в `docs/qa-evidence/phone-acceptance/`.

### PHONE-04 — audio focus Passed; headset/noisy Deferred

- mpoddy был запущен и владел audio focus.
- В Chrome открыт только локальный fixture URL `http://192.168.0.223:8765/audio/long_30s.mp3?focus=2`.
- После физического нажатия Play на телефоне Chrome MediaSession стала `PLAYING`, а mpod MediaSession автоматически стала `PAUSED` на позиции `162633`; top audio focus перешёл Chrome.
- Crash и самостоятельного возобновления mpod не было.
- Проверка отключения проводной/Bluetooth-гарнитуры не выполнялась по прямому указанию владельца: `Deferred by product owner`.

### PHONE-05 — Passed

- Fixture-сервер был полностью остановлен; `nc` вернул `connection refused` для `192.168.0.223:8765`.
- Несмотря на это скачанный Smart Listening выпуск `Episode 1 - Hello World` начал играть из локального файла: MediaSession `PLAYING`, buffer `3030 ms`.
- Через несколько секунд выпуск естественно завершился и MediaSession перешла к следующему — «542. Новинки Chrome 152…».
- После завершения `Episode 1` исчез из unlistened/downloaded UI Test Podcast Alpha.
- Для проверки unsubscribe-cleanup использована подписка «Думаем дальше» со скачанным выпуском «В параллельных мирах много гигабайт».
- После 12-секундного undo-window число подписок изменилось `9 -> 8`, а выпуск и podcast отсутствовали в очереди; stale downloaded/queue-state не обнаружен.
- Proxy оставлен выключенным, то есть приложение было возвращено в Direct до потери ADB-соединения.

## Доказательства

Постоянные файлы в репозитории:

- `docs/qa-evidence/phone-acceptance/socks5-proxy.log`;
- `docs/qa-evidence/phone-acceptance/http-proxy.log`;
- `docs/qa-evidence/phone-acceptance/fixture-server.json`;
- `docs/qa-evidence/phone-acceptance/notification.png`;
- `docs/qa-evidence/phone-acceptance/notification.xml`;
- `docs/qa-evidence/phone-acceptance/lockscreen.png`;
- `docs/qa-evidence/phone-acceptance/lockscreen.xml`;
- `docs/qa-evidence/phone-acceptance/cleanup-after-natural-completion.xml`;
- `docs/qa-evidence/phone-acceptance/unsubscribe-after.xml`;
- `docs/qa-evidence/phone-acceptance/queue-after-unsubscribe.xml`.

Дополнительные временные UI dumps находятся в `/tmp`: `mpod-phone-window.xml`, `mpod-player.xml`, `mpod-settings.xml`, `mpod-cleanup.xml`, `mpod-unsubscribe-after.xml`, `mpod-queue-after.xml`.

`fixture-server.json` обновлён после последнего audio-focus прогона и содержит запрос `long_30s.mp3?focus=2`.

## Текущее состояние и что осталось

1. Создать итоговый `docs/android-qa-final-phone-acceptance-report.md` на основании фактов выше.
2. Решить, достаточно ли текущего HTTP evidence без отдельного artwork cache-miss. Если нет — после переподключения телефона добавить новый feed/уникальный artwork URL и выполнить один HTTP refresh без переустановки.
3. После переподключения проверить, что тестовый Chrome-аудиопоток остановлен, и выполнить `adb -s <serial> shell svc power stayon false`: команда восстановления не прошла, потому что устройство стало `offline`.
4. Локальный fixture-сервер на `8765` остановлен. SOCKS5/HTTP proxy-процессы могли остаться запущенными; проверить перед завершением.
5. Не коммитить и не пушить до Team Lead verdict по итоговому phone report.

## Git

- Commit и push после телефонной проверки не выполнялись.
- Рабочее дерево уже было существенно dirty до этого handoff: изменения production-кода, тестов и документации принадлежат текущему bugfix-пакету; `.idea/misc.xml`, `.idea/codeStyles/`, `app_launch_screenshot.png`, `update-report.sh` считать потенциально посторонними и не включать автоматически.
- Новые evidence-файлы и этот handoff также пока не закоммичены.
