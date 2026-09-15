# mpoddy Current Tasks

Единый оперативный источник текущего состояния Android-кандидата `mpoddy`.

Последняя редакция: 15 сентября 2026 года.

## Правила

- Одновременно активна только одна задача и назначен один следующий исполнитель.
- Активная задача находится первой и передаётся исполнителю целиком.
- QA `FAIL` возвращает только подтверждённый blocker; после rework обязательны review и повторная продуктовая проверка.
- Commit включает только явно перечисленные task-файлы; посторонние локальные файлы не добавляются.
- Version bump, release APK и публикация выполняются только по отдельному решению.

## Фактическое состояние кандидата

- Ветка: `codex/qa-obvious-bugs`.
- Удалённый baseline до публикации rework: `eda4ece64d0be07ad28f5a69f0957b9afe5737ba`.
- `MPOD-BUG-02`: `341002d` (`fix(android): preserve last refresh on failures`).
- Исходный `MPOD-BUG-01`: `dda7734` (`fix(android): synchronize subscription carousel selection`).
- `MPOD-BUG-01-R1`: `da4c6a7` (`fix(android): sync carousel after unsubscribe`).
- Rework устраняет stale carousel/summary/Pending после удаления выбранного подкаста и чинит некомпилируемый mapping test.
- Повторный code review: `PASS`; targeted mapping 6/6, targeted Compose 1/1, полный unit gate 153/153, полный connected gate 56/56 на Pixel 9 API 37, `git diff --check` чистый.
- Новый release APK после `da4c6a7` ещё не собирался; APK/SHA от `eda4ece` не использовать для повторной приёмки.
- `MPOD-BUG-02` прошёл code review, но его product retest не был выполнен из-за первого BUG-01 blocker.

---

## 1. АКТИВНО — 15.09.2026 20:01 MSK — Повторная продуктовая приёмка после BUG-01-R1

**ID:** `MPOD-QA-02-R1`
**Направление:** Android QA / product acceptance.
**Приоритет:** release gate.
**Статус:** ready после публикации `da4c6a7`.
**Единственный следующий исполнитель:** Android-тестировщик.

### Передать исполнителю целиком

> Провести повторную продуктовую приёмку ветки `codex/qa-obvious-bugs`, содержащей `341002d`, `dda7734` и rework `da4c6a7`.
>
> Перед началом:
>
> 1. Подтвердить точный HEAD и совпадение с `origin/codex/qa-obvious-bugs`.
> 2. Собрать свежий release APK из этого HEAD с `--rerun-tasks`; записать имя, version и SHA-256.
> 3. Установить APK поверх существующих данных без очистки приложения.
>
> Сначала повторить прежний blocker BUG-01:
>
> 1. Подготовить минимум два подкаста с различимыми карточками и выпусками.
> 2. Выбрать один подкаст, выполнить Unsubscribe и дождаться конца 15-секундного countdown без Undo.
> 3. Подтвердить без relaunch, что header, выбранная карточка, summary, счётчики, episode list и podcast-scoped actions относятся к оставшемуся подкасту.
> 4. Подтвердить, что удалённый title и действие `Pending` исчезли.
>
> Затем выполнить полный BUG-01 scope:
>
> 1. Mid-drag и settle синхронизация card/summary/episodes.
> 2. Обе wrap-around границы.
> 3. Show all / Show unlistened.
> 4. Refresh, Mark all listened и Unsubscribe направлены на визуально выбранный podcast ID.
>
> Затем выполнить BUG-02:
>
> 1. Зафиксировать предыдущий успешный `Last refresh`.
> 2. Partial-failure Refresh All: успешный feed обновляется, ошибка и Retry видимы, библиотека пригодна к работе, общий `Last refresh` не меняется.
> 3. All-failed Refresh All: ошибки отражены, библиотека сохранена, общий timestamp не меняется.
> 4. Full-success Refresh All: только здесь общий `Last refresh` обновляется.
>
> После целевых сценариев выполнить короткий smoke Home, Subscriptions, player и Settings.
>
> Не изменять код, тесты, документацию, versionName/versionCode или Git-историю. При FAIL остановиться на первом воспроизводимом blocker и вернуть Expected/Actual, частоту и evidence. Evidence не добавлять в Git.
>
> Формат результата: `PASS` или `FAIL`; commit; APK/version/SHA-256; устройство/API; результат post-Unsubscribe, полного BUG-01, BUG-02 и smoke; известные ограничения.

**Критерий завершения:** post-Unsubscribe blocker не воспроизводится, весь BUG-01/BUG-02 scope и короткий smoke пройдены на свежем APK из точного опубликованного HEAD.

**Следующий переход:** при `PASS` отметить SUB-05 и SUB-12 как `Verified`, закрыть `MPOD-QA-02-R1` и активировать `MPOD-BUG-03`; при `FAIL` создать одну узкую задачу по первому подтверждённому blocker.

---

## 2. НЕАКТИВНО — Устранить дублирующие initial/resume загрузки

**ID:** `MPOD-BUG-03`
**Приоритет:** P2.
**Статус:** выполнять только после закрытия `MPOD-QA-02-R1` и отдельного воспроизведения на актуальном кандидате.

Сначала подтвердить дублирование запросов наблюдаемым тестом. Без воспроизведения production-код не менять.

---

## Не возвращать в работу без новых фактов

- Не откатывать `341002d`, `dda7734` или `da4c6a7` ради искусственного FAIL-before.
- Не считать SUB-05 или SUB-12 `Verified` до успешного `MPOD-QA-02-R1`.
- Старый `MPOD-OPS-02` завершён checkpoint-коммитом `8655106` и больше не является текущей задачей.
