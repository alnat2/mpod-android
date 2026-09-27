# BUG-R01 — developer verification

Дата: 27.09.2026. Статус: готово к код-ревью; QA acceptance не запрашивалась и не объявляется выполненной.
База: `7efdc8a`. Коммит исправления — коммит, добавляющий этот отчёт (см. `git log -1 -- docs/bug-r01-verification.md`).

## Изменение

Отписка подавляет планирование загрузок своего подкаста до завершения удаления из Room. Регистрация владельца и подавление согласованы одним lock; планировщик перечитывает запись эпизода, поэтому старый снимок очереди не запускает удалённый эпизод. Уже зарегистрированные загрузки отменяются и ожидаются. OkHttp callback имеет отдельный сигнал завершения: владелец coroutine остаётся доступен до закрытия тела ответа и файлового потока, включая отмену и ошибки.

Общий cleanup временно подавляет новые загрузки эпизода, очищает связанный путь и файлы с префиксом `ep_<id>_`, включая `.tmp` и файл, ещё не записанный в Room. Ошибка удаления/исключение сохраняет подкаст и возможность повторить очистку. Guard снимается в `finally`. Если наблюдатель попытался запланировать повторно добавленный эпизод во время успешного cleanup, после освобождения guard проверяется актуальная очередь/Room и загрузка может возобновиться.

После очистки Room DELETE атомарно каскадирует эпизоды и playlist. Очистка activeEpisodeId и invalidation очереди доводятся до конца при отмене вызывающей coroutine после начала DELETE. Сетевые/файловые ожидания проходят вне Room-транзакции. ViewModel показывает ошибку существующим баннером; `Try again` повторяет отписку, а не refresh. До истечения 15 секунд Undo cleanup/удаление не вызываются.

## Файлы

- `app/src/main/java/com/example/mpod/playback/SmartListeningManager.kt`
- `app/src/main/java/com/example/mpod/data/repository/PodcastRepository.kt`
- `app/src/main/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsViewModel.kt`
- `app/src/main/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsScreen.kt` — привязка существующего Retry.
- `app/src/test/java/com/example/mpod/data/repository/PodcastUnsubscribeTest.kt` — 8 новых regression-сценариев.
- `app/src/test/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsMarkAllListenedConsistencyTest.kt` — связанные сценарии повторного добавления теперь требуют ожидать окончания cleanup перед новой загрузкой.
- `app/src/androidTest/java/com/example/mpod/ui/screens/subscriptions/UnsubscribeDownloadTest.kt` — реальные UI/Room/OkHttp/files проверки.

## Developer verification

- FAIL-before: в отдельной копии `HEAD` в `/tmp`, без изменения рабочих исходников, три управляемых теста исходного протокола завершились FAIL: отписка перед download-state commit, отписка при удерживаемом writer OkHttp, старый снимок очереди во время DELETE. В исправленной версии эти сценарии PASS.
- `:app:testDebugUnitTest`: **183/183 PASS**, включая 8 новых тестов: pre-commit, post-commit, удерживаемый callback writer, stale scheduling, сохранённый и несвязанный файл с ошибкой удаления/retry, отмена отписки, Undo. Проверены сохранение владельца другого подкаста, очистка activeEpisodeId и событие invalidation с выбором актуального playback target вместо удалённого текущего эпизода.
- `:app:connectedDebugAndroidTest` с классами `UnsubscribeDownloadTest` и `MarkAllListenedTransactionTest`: **10/10 PASS** на **Pixel_9 API 37, emulator-5554**, пакет **com.prod.mpod.test**. Тесты используют отдельную in-memory Room и каталог файлов в cache; production-данные не очищались.
  - Через UI эпизод добавлен в очередь; настоящий OkHttp получает 128 KiB с ограничением 1024 байта/250 мс. Зафиксирован растущий `.tmp`; выполнены Unsubscribe → Undo → Unsubscribe с настоящим 15-секундным countdown. После завершения UI показывает `No podcasts`, Room не содержит подкаст/эпизод/playlist row, activeEpisodeId очищен, владелец завершён, каталог загрузок пуст, HTTP-запрос ровно один.
  - Отказ удаления сохранённого аудио: после countdown в Room остаётся исходный путь, файл и activeEpisodeId сохранены; UI `Try again` после снятия отказа удаляет данные и файл.
- `:app:lintDebug`, `:app:lintRelease`, `:app:assembleRelease`: **PASS**.
- `git diff --check`: **PASS**.

## Ограничения

Физический телефон, звук на слух и реальный ExoPlayer/MediaSession при отписке во время воспроизведения не проверялись. Для текущего playback target выполнена JVM-проверка production resolver и invalidation; это не phone verification. Instrumentation gate ограничен двумя указанными классами, весь connected suite не запускался. QA acceptance и внешняя публикация не выполнялись. BUG-R02–R07 не входят в это исправление; в частности, общая атомарность UI-state и stop/start ownership остаются отдельными задачами.
