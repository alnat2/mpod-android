# QA acceptance: BUG-R01 и BUG-R03

**Дата:** 02.10.2026

**Кандидат:** `60bd3f57e77bd7165979114e03aaa1ee2d68a1dd`

**Рабочее дерево:** тестировался указанный commit; в рабочем дереве были пользовательские изменения в `docs/bugs.md` и `docs/bug-r01-r03-qa-instructions.md`. Файлы приложения не менялись.

**APK:** `localRelease`, `com.prod.mpod`, versionName `1.0.19`, versionCode `20`; SHA-256 `0c29289acdb04f8260abf1436a30f821e2188067dcb61c89c3026fbea20fc30e`. Сертификат SHA-256 `61f0b1bb4485fcf4333e005e1adb43115340eb6b63b8f378cce5319430a4d012`.

**Устройство:** AOSP API 35 arm64 AVD `emulator-5554`, Android 15, `userdebug`, root доступен. Физический телефон не проверялся.

## BUG-R01 — PASS

- `UnsubscribeDownloadTest`: 2/2 PASS; `SubscriptionsQueryTest`: 3/3 PASS в том же connected test прогоне. XML: `connected-results/debug/TEST-mpod-aosp35(AVD) - 15-_app-.xml`.
- **R01-A:** подтверждены медленная запись `.tmp` с ростом размера, Undo в 15-секундном окне и сохранение A/очереди/загрузки. Затем повторно подтверждены активные загрузки A и B и отписка при playback A. После удаления строки A и его файлы исчезли; B, его данные и очередь сохранились. Поздние снимки через 20 секунд и после relaunch не показали возврата A или его файлов.
- **R01-B:** локально скачанный файл A (podcast id 4, episode id 12) воспроизводился в MediaSession `PLAYING`, скорость 1.0; позиция выросла с 0 до 8999 ms. После начала playback новых audio GET для этого выпуска в fixture log не было. После истечения окна A, его episode/playlist row и файл удалены. B остался; его playlist row и скачанный файл были видны сразу после удаления. Через 20 секунд и после relaunch A не вернулся. MediaSession больше не показывала episode A.
- После фиксации evidence обе созданные QA-подписки удалены через UI; каталог загруженных QA-файлов пуст; reverse mapping снят, fixture server остановлен. App data и приложение не удалялись.

## BUG-R03 — PASS

- `SubscriptionsQueryTest`: 3/3 PASS на настоящей Room/SQLite. Пройдены тесты постоянного числа запросов при 1 и 30 podcast, доставки projection (metadata, флаги, очередь, пустой podcast) и равенства projection при изменении только позиции.
- На совпадающем коде `60bd3f5` mapping принимается по независимому полному unit-прогону `193/193 PASS` от 01.10.2026, зафиксированному в `docs/bugs.md`.
- В `localRelease` свежая QA-подписка A получила revision 2 без переоткрытия экрана. Room/UI показали 4 выпуска в порядке 4, 3, 2, 1; счётчик изменился на 4/4. Episode 4 появился в очереди, кнопка стала Remove from playlist, загрузка завершилась (`isDownloaded=1`, непустой путь) без переоткрытия. Удаление из очереди вернуло Add to playlist. Переключение episode 4 в listened немедленно обновило Room, счётчик и фильтр; Show all снова показал выпуск.
- Обязательный сценарий выполнен на `localRelease`: `QA aliases episode 5` запущен из Player; при переходе в Subscriptions выбран A обычным горизонтальным свайпом и выполнен Refresh. UI A показал `5 / 5 episodes` и новый `QA race episode 5`. MediaSession B до Refresh: `PLAYING`, скорость `1.0`, позиция `0 ms`; после Refresh: `PLAYING`, metadata `QA aliases episode 5`, позиция `21,539 ms`; следующий замер — `42,560 ms`. Воспроизведение продолжалось и позиция росла. Для continuity-прогона использован только локальный fixture с трёхминутным WAV, чтобы тестовый трек не закончился во время замеров; APK и код кандидата не менялись.
- Карусель переключилась обычным свайпом; отдельный дефект жеста не подтверждён.

## Evidence

Основной каталог evidence сохранён локально: `/tmp/mpod-r01-r03-qa.XG09uD/`.

- Build/install: `build-local-release.log`, `apk-sha256.txt`, `apk-signature.txt`, `apk-badging.txt`, `install-local-release.txt`.
- Instrumentation: `connected-results/debug/TEST-mpod-aosp35(AVD) - 15-_app-.xml`, `connected-tests-jdk.log`.
- R01: `r01-final-countdown.png`, `r01-final-after-countdown-room.txt`, `r01-final-after-countdown-files.txt`, `r01-final-after-countdown-media-session.txt`, `r01-after-20s-room.txt`, `r01-after-20s-files.txt`, `r01-after-relaunch-room.txt`, `r01-b-before-unsubscribe.png`, `r01-b-countdown.png`, `r01-b-before-delete-session.txt`, `r01-b-after-countdown-room.txt`, `r01-b-after-countdown-files.txt`, `r01-b-after-countdown-session.txt`, `r01-b-after-20s-room.txt`, `r01-b-after-20s-files.txt`, `r01-b-after-relaunch-room.txt`, `r01-b-after-relaunch-files.txt`.
- R03: `r03-after-refresh.png`, `r03-after-refresh-room.txt`, `r03-episode4-queued.png`, `r03-episode4-queued-room.txt`, `r03-episode4-downloaded.png`, `r03-episode4-downloaded-room.txt`, `r03-episode4-listened.png`, `r03-episode4-listened-room.txt`, `r03-continuity-after-refresh.png`, `r03-continuity-media-session.txt`, `r03-final-cleanup.txt`, `fixture/requests.jsonl`, `fixture-r03-continuity/requests.jsonl`.
- Fixture logs include the RSS revision 1/2 responses and corresponding audio events. Final Room/file snapshots show QA subscriptions removed.

**Ограничения:** R01 и R03 acceptance относятся к localRelease на AOSP AVD. Физический телефон и ручной сценарий ошибки удаления/Retry не проверялись; Retry покрыт автоматическим `UnsubscribeDownloadTest`. Полный release gate этим отчётом не подтверждается.
