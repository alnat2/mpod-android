# mpoddy Android — product smoke report

Дата: 7 сентября 2026 года, 23:01–23:23 MSK
Задача: `MPOD-QA-01`
Исполнитель: Android QA subagent
Устройство: Xiaomi `23021RAA2Y` (`topaz`), Android 15
Package/build: `com.prod.mpod`, `1.0.17 (18)`
Результат: **FAILED — один P1 release blocker и один P2; P0 не найдено**

## Пройдено

- Запуск, нижняя навигация и возврат между Player, Subscriptions, Settings и Add Podcast.
- Player: Play/Pause, `-10`, `+15`, выбор скорости и восстановление исходных `1.5x`.
- Force-stop/relaunch: активный выпуск, позиция `0:29`, скорость и очередь из четырёх выпусков сохранились; autoplay отсутствовал.
- Фоновое воспроизведение и системная media card: metadata/artwork и Play/Pause работают.
- Show notes тестового выпуска.
- Добавление тестового выпуска в playlist и удаление с восстановлением исходного состояния.
- Mark listened/unlistened для `Test Podcast Alpha` с восстановлением исходного состояния.
- Add Podcast validation: `not-a-url` отклонён сообщением `Enter a valid http or https RSS feed URL.`
- Ошибка Refresh тестовой подписки при недоступном fixture показана без потери сохранённых выпусков.
- Refresh All завершился понятным списком четырёх ошибок и действием Try again; библиотека сохранилась.
- Theme Dark → System; исходный System восстановлен.

## MPOD-BUG-01 — P1, release blocker

Область: Subscriptions carousel/filter и podcast-scoped действия.

Воспроизведение:

1. Открыть Subscriptions в Show unlistened.
2. Медленно перелистнуть `Test Podcast Alpha` → `Веб-стандарты`; либо на `Веб-стандарты` переключить Show all.
3. Дождаться завершения анимации.

Expected: карточка, счётчик, список выпусков и действия относятся к одному подкасту.

Actual:

- во время swipe карточка уже показывает `Веб-стандарты`, а summary/list остаются `3 / 1 episodes` и `Episode 3 - No Enclosure` от `Test Podcast Alpha`;
- после Show all рассинхронизация сохраняется в settled-состоянии: карточка `Веб-стандарты`, а `337 / 0 episodes` и строки относятся к `6 Minute English`;
- после Mark listened карточка показывает `Test Podcast Alpha`, а summary/list переключаются на Podlodka.

Частота: 3/3 проверенных перехода состояния.

Evidence:

- `/tmp/mpod-product-smoke-resume/07-bug01-during-swipe.png`;
- `/tmp/mpod-product-smoke-resume/08-show-all.png`;
- `/tmp/mpod-product-smoke-resume/25-alpha-marked-listened.png`.

## MPOD-BUG-02 — P2

Область: Refresh All / Settings / достоверность состояния.

Воспроизведение:

1. До обновления Settings показывает `Last refresh today at 07.09 22:39`.
2. Выполнить Refresh All и дождаться четырёх feed failures.
3. Снова открыть Settings.

Expected: сохраняется время последнего полного успеха либо явно отображается partial/failed result.

Actual: Settings показывает новое обычное успешное время `Last refresh today at 07.09 23:13`.

Частота: 1/1.

Evidence:

- `/tmp/mpod-product-smoke-resume/09-settings.png`;
- `/tmp/mpod-product-smoke-resume/16-refresh-all-65s.png`;
- `/tmp/mpod-product-smoke-resume/17-settings-after-partial-refresh.png`.

## Не выполнялось

- Реальное добавление новой подписки: доступного fixture не было, лишняя пользовательская подписка не создавалась.
- Mark all listened и Unsubscribe/Undo: после подтверждения `MPOD-BUG-01` podcast-scoped mutations признаны небезопасными.
- Переключение элементов пользовательской очереди, offline playback и Smart Listening cleanup: пользовательская очередь не изменялась.
- Расширенный proxy/network audit: исключён из продуктового smoke.

Эти пункты повторяются на исправленном кандидате с отдельной тестовой подпиской; они не отменяют найденный P1.

## Финальное состояние телефона

- Приложение оставлено на Player, playback `PAUSED`.
- Активный выпуск: `542. Новинки Chrome 152…`, позиция около `1:26`, скорость `1.5x`.
- Очередь: четыре выпуска; ничего не удалено.
- `Test Podcast Alpha` возвращён в unlistened и удалён из playlist.
- Фильтр: Show unlistened; Theme: System.
- Подписки не удалялись; APK и production-код не изменялись.
