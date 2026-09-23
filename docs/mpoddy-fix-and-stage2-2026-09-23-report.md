# Исправление плеера и второй этап оставшейся приёмки — 23.09.2026

Обновление: третий технический этап завершён — `mpoddy-stage3-2026-09-23-report.md`. Ниже сохранён результат соответствующего предыдущего этапа.

**Исправление: PASS. Второй этап: проверенные сценарии PASS. Описанные ниже ограничения Refresh all и private Room/files впоследствии закрыты — см. `mpoddy-open-items-2026-09-23-report.md`. Полная приёмка не закрыта: технический третий этап ещё не выполнен.**

Среда: Pixel_9 AVD API 37, emulator-5554, production `com.prod.mpod`. Физический телефон не использовался. Продолжение после перерыва из-за лимита автоматической проверки разрешений выполнено по команде пользователя. Отклонённая команда до продолжения не исполнялась.

## Исправление и кандидат

В HomeScreen.kt состояние кнопки и обработчик Play/Pause теперь учитывают `playerError`. При ошибке предлагается Play; одно нажатие вызывает существующие prepare/play. Обычная буферизация с запросом воспроизведения по-прежнему предлагает Pause. Изменены только HomeScreen.kt и HomePlaybackIntentTest.kt; исправление не закоммичено и не опубликовано.

- База: `26112e1f08bec8233151cbb637fa0432d0f080b7` + локальное исправление. Patch сохранён в evidence; APK не следует называть сборкой чистого published HEAD.
- Версия без повышения: 1.0.18 (19).
- APK: `build/release-handoff/mpoddy-1.0.18-19-player-retry-fix-20260923.apk`, 4 915 716 байт.
- SHA-256: `585949d10d2f4e4748f8a7ceacd263b5377c44b42b9188f7af2d7c1c5588c39c`.
- Подпись проверена: существующий Android Debug fallback release-конфигурации, fingerprint `61f0b1bb4485fcf4333e005e1adb43115340eb6b63b8f378cce5319430a4d012`.
- Установлен через update без очистки данных на согласованный AVD; библиотека и сохранённый выпуск остались.
- Focused JVM regression: 4 теста, до исправления 1 failure, после исправления 4/4 PASS. `:app:assembleRelease` — BUILD SUCCESSFUL, 1m42s совместно с focused tests. Полный набор тестов/lint третьего этапа не запускался; встроенный lintVital сборки не заменяет его.

Evidence во всём отчёте относительно `build/qa-fix-stage2-20260923/`.

На новом APK повторён реальный сценарий обрыва незавершённого HTTP-аудиопотока остановкой временного сервера. До обрыва PLAYING; затем ERROR с позицией 16854 мс. В отличие от старого APK, обе кнопки стали Play. После восстановления источника одно нажатие Play возобновило выпуск с 16854 мс, error=null. При обычной буферизации кнопка оставалась Pause. Доказательства: `fix-buffering*`, `fix-before-cut*`, `fix-after-cut*`, `fix-one-tap-recovered*`, `unit-before.xml`, `unit-after.xml`, `source.patch`, `build-and-unit-after.log`.

## Второй этап — реальные действия через UI

| Проверка | Фактический результат | Evidence |
|---|---|---|
| Отмена импорта OPML | Возврат в доступную форму без ложной ошибки | import-cancel.xml |
| Импорт двух подписок и одного дубликата | Imported: 2, Skipped: 1; повтор того же файла — Imported: 0, Skipped: 3 | import-selected.xml; settings-initial.xml содержит повторный результат импорта, несмотря на имя файла |
| Отмена экспорта | Нет ложного success/error | export-cancel.xml |
| Экспорт OPML | Сохранён отдельный файл; XML разобран, все 7 тогдашних подписок ровно по одному разу | export.opml, exported.xml |
| Тема и автообновление | Dark и включённое автообновление пережили force-stop/relaunch. Light/System переключаются; возвращены System и auto refresh off | settings-dark-auto.xml, settings-persisted.xml, dark-persisted.png, final-settings.xml |
| Валидация прокси | В режимах SOCKS5 и HTTP пустой host не позволяет сохранить: Save Proxy disabled. Вернули Direct; подключение через реальный прокси и валидация портов не проверялись | proxy-invalid-host.xml, http-invalid-host.xml, settings-restored.xml |
| Build Info / Smart Listening controls | Версия v1.0.18 видна, ручных настроек Smart Listening нет | final-settings.xml |
| Отписка с Undo | QA import-a сохранилась, счётчик 7 подписок не изменился | unsubscribe-pending.xml, unsubscribe-undone.xml |
| Окончательная отписка | Удалена только созданная этим прогоном QA import-b: 7 → 6 подписок. Её скачанный активный выпуск исчез из очереди, Player пуст, MediaSession NONE | downloaded-before-delete.xml, unsubscribe-complete.xml, queue-after-unsubscribe* |
| Автозагрузка и пересоздание Activity | Повторная загрузка QA race 4 шла при повороте экрана; после возврата есть Downloaded. Для этого скачивания при audio_delay=0.15 зарегистрирован ровно один GET, id=30 | download-landscape.xml, readd-stable.xml, fixtures-stage2/requests.jsonl |
| Mark all listened одновременно с refresh/загрузкой | Скачанный race 4 и загружаемый race 3 в очереди. В 15-секундном окне refresh выполнен Mark all listened: 5/0 непрослушанных, очередь пустая, повтор не меняет результат. Загрузка id=24 закрылась клиентом после 303104 байт до окончания refresh id=26 | refresh-in-flight.xml, mark-all-during-refresh.xml, race-listened.xml, repeated-mark-all.xml, cleanup-empty-queue.xml, HTTP journal |
| Cleanup и быстрое повторное добавление | Скачанный race 4 помечен listened, возвращён в unlistened и добавлен снова; Downloaded восстановился и сохранился после перезапуска | readd-downloaded.xml, readd-cleaned.xml, readd-stable.xml, readd-player-local.xml |
| Пригодность нового локального файла | После перезапуска с fail_audio=true выпуск воспроизводится; в этом окне ни одного GET аудио. Затем поставлен на паузу | readd-local-playing*, HTTP journal |
| Обычная внешняя подписка | В Planet Money стало 356 выпусков вместо 355; появился выпуск Is our national debt finally too much? (update) от 23.09. Он сохранился после перезапуска. Мгновенное появление именно этого внешнего выпуска на уже открытом экране отдельно не фиксировалось | before-full-refresh.xml, readd-restart.xml |

## Ограничения на момент этого прогона (пункты 1–2 закрыты последующей проверкой)

1. **Общий успешный Refresh all / Last refresh.** Все доступные ленты обновлялись, но сохранённый старый URL `http://10.0.2.2:8765/feed/throttled30` возвращает Connection reset из AVD. Совместимый локальный ответ подготовлен и доступен с хоста, но запрос из AVD до него не дошёл. Это ограничение среды, не подтверждённый дефект приложения. Старую подписку не удаляли и её URL не меняли. `full-refresh-result.xml`, `last-refresh-result.xml`, `final-settings.xml` фиксируют отсутствие полного успеха и Last refresh never.
2. **Private Room/files.** Не подтверждены физическое удаление всех файлов, отсутствие orphan-файлов и внутренние транзакционные инварианты. Исчезновение Downloaded и успешное повторное локальное воспроизведение подтверждают пользовательское поведение, но не заменяют прямую инспекцию. Детерминированные concurrency-тесты остаются частью третьего этапа.
3. Звук на слух не проверялся. Состояния воспроизведения подтверждены UI и MediaSession. Полная proxy/network и длительная scheduler-проверка не выполнялись.

Дополнительных дефектов в выполненных сценариях не обнаружено. В сохранённом crash buffer имеется старый crash com.google.android.bluetooth от 22.09; новым crash приложения он не является. Полная проверка логов остаётся в техническом этапе.

## Состояние для продолжения

Установлен новый APK выше. Подписок 6: прежние 5 и QA import-a; QA import-b удалена в тесте. В очереди скачанный QA race episode 4, PAUSED около 23 секунд; остальные race помечены прослушанными. System theme, автообновление и прокси выключены. Исходный автоповорот 1 и user_rotation 0 восстановлены. Временный сервер остановлен, reverse tcp:8765 снят; AVD оставлен работающим. Импортный/экспортный файлы сохранены в Downloads AVD.

Следующий согласованный этап — технические проверки на исправленном исходном состоянии. Отдельно остаются перечисленные ограничения; полный PASS до их разрешения не заявлять. Коммит, push, публикация и третий этап в этой работе не выполнялись.
