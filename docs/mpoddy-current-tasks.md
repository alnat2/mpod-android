# mpoddy Current Tasks

Единый оперативный источник текущего состояния Android-кандидата `mpoddy`.

Последняя редакция: 15 сентября 2026 года.

## Правила

- Одновременно активна только одна задача и назначен один следующий исполнитель.
- Активная задача находится первой и передаётся исполнителю целиком.
- Закрытая разработка не возвращается в работу без нового воспроизведения или замечания review.
- Commit включает только явно перечисленные task-файлы; посторонние локальные файлы не добавляются.
- После разработки следуют code review и продуктовый retest. Version bump, release APK и публикация выполняются только по отдельному решению.

## Фактическое состояние кандидата

- Ветка: `codex/qa-obvious-bugs`.
- Базовый checkpoint: `8655106` (`fix(android): checkpoint verified bugfix baseline`).
- `MPOD-BUG-02` закоммичен как `341002d` (`fix(android): preserve last refresh on failures`).
- `MPOD-BUG-01` восстановлен после ошибочного локального `reset` и закоммичен как `dda7734` (`fix(android): synchronize subscription carousel selection`).
- Локальная ветка опережает `origin/codex/qa-obvious-bugs`; push ожидает явного подтверждения отправки именно в этот remote/branch.
- Посторонние untracked IDE-файлы, отчёты, evidence и APK не входят в эти коммиты и не должны добавляться wildcard-командой.
- Откатывать `341002d` или заново выполнять разработку `MPOD-BUG-02` не требуется.

---

## 1. АКТИВНО — 15.09.2026 11:40 MSK — Проверить объединённый кандидат BUG-01 и BUG-02

**ID:** `MPOD-REVIEW-01`
**Направление:** Team Lead / code review.
**Приоритет:** release gate.
**Статус:** ожидает push локального `dda7734`, затем review.
**Единственный следующий исполнитель:** Team Lead / code reviewer.

### Передать исполнителю целиком

> Провести read-only code review двух исправлений в ветке `codex/qa-obvious-bugs` после подтверждения, что локальный и удалённый HEAD совпадают.
>
> Проверяемые коммиты:
>
> - `341002d` — `MPOD-BUG-02`, правдивый результат Refresh All;
> - `dda7734` — `MPOD-BUG-01`, синхронизация выбранной карточки подписки, summary, списка выпусков и podcast-scoped действий.
>
> Для `MPOD-BUG-01` допустимый scope состоит ровно из трёх файлов:
>
> - `app/src/main/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsScreen.kt`;
> - `app/src/androidTest/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsScreenTest.kt`;
> - `app/src/test/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsCarouselMappingTest.kt`.
>
> Для `MPOD-BUG-02` допустимый scope состоит ровно из трёх файлов:
>
> - `app/src/main/java/com/example/mpod/data/repository/PodcastRepository.kt`;
> - `app/src/test/java/com/example/mpod/data/repository/PodcastRepositoryRefreshAllTest.kt`;
> - `app/src/androidTest/java/com/example/mpod/ui/screens/RefreshAllStatusUiTest.kt`.
>
> Проверить:
>
> 1. В `SubscriptionsScreen` карточка и связанные данные используют один authoritative page (`pagerState.currentPage`), а wrap-around нормализуется существующим helper.
> 2. Regression покрывает промежуточное состояние свайпа, settle, оба wrap-around перехода, visibility filter и podcast-scoped callbacks.
> 3. `refreshAllPodcasts()` записывает `lastRefreshTime` только при полном успехе; partial/all-failed возвращают failure и не записывают успешное время.
> 4. В task-коммитах нет посторонних файлов, version bump, release APK или изменения дизайна.
> 5. Замечание о NUL-байтах из прежнего review не воспроизводится в текущем workspace: проверить фактические байты текущего `PodcastRepository.kt`, а не старый путь из другого checkout.
>
> Не изменять код, не делать revert/reset/rebase и не добавлять локальные untracked-файлы. Если review проходит, вернуть `PASS` и разрешить активацию `MPOD-QA-02`. Если нет — вернуть только конкретные blocker-замечания с файлом, строкой и ожидаемым контрактом.

**Критерий завершения:** оба commit diff проверены; посторонних изменений нет; получен однозначный `PASS` либо узкий список blocker-замечаний.

**Следующий переход:** при `PASS` активировать `MPOD-QA-02`; при `FAIL` создать отдельную узкую rework-задачу без отката уже принятого исправления.

---

## 2. НЕАКТИВНО — Повторная продуктовая приёмка BUG-01 и BUG-02

**ID:** `MPOD-QA-02`
**Направление:** Android QA.
**Статус:** ожидает `PASS` задачи `MPOD-REVIEW-01`.
**Следующий исполнитель после активации:** Android-тестировщик.

Проверить на одном свежем APK из точного reviewed HEAD:

1. Во время и после свайпа карточка подписки, summary, artwork, выпуски и действия относятся к одному подкасту.
2. Переходы через обе границы карусели и Show all / Show unlistened не создают рассинхронизацию.
3. При полном Refresh All время `Last refresh` обновляется.
4. При partial/all-failed Refresh All показывается ошибка, библиотека остаётся доступной, а `Last refresh` не записывается как успешный.
5. Выполнить короткий smoke Home, Subscriptions, player и Settings.

Вернуть commit, APK SHA-256, устройство/API, Expected/Actual и evidence только для фактических отклонений.

---

## 3. НЕАКТИВНО — Устранить дублирующие initial/resume загрузки

**ID:** `MPOD-BUG-03`
**Приоритет:** P2.
**Статус:** выполнять только после закрытия `MPOD-QA-02` и отдельного воспроизведения на актуальном кандидате.

Сначала подтвердить дублирование запросов наблюдаемым тестом. Без воспроизведения production-код не менять.

---

## Не возвращать в работу без новых фактов

- `MPOD-BUG-01` уже реализован в `dda7734`; старый текст про ожидание разработки устарел.
- `MPOD-BUG-02` уже реализован в `341002d`; revert ради искусственного FAIL-before запрещён.
- Старый активный `MPOD-OPS-02` завершён checkpoint-коммитом `8655106` и больше не является текущей задачей.
