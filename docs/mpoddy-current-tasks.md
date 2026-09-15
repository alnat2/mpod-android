# mpoddy Current Tasks

Единый оперативный источник текущего состояния Android-кандидата `mpoddy`.

Последняя редакция: 15 сентября 2026 года.

## Правила

- Одновременно активна только одна задача и назначен один следующий исполнитель.
- Активная задача находится первой и передаётся исполнителю целиком.
- QA `FAIL` имеет приоритет над прежним review `PASS` и возвращает только подтверждённый blocker.
- Commit включает только явно перечисленные task-файлы; посторонние локальные файлы не добавляются.
- Version bump, release APK и публикация выполняются только по отдельному решению.

## Фактическое состояние кандидата

- Ветка и удалённый baseline: `codex/qa-obvious-bugs` @ `eda4ece64d0be07ad28f5a69f0957b9afe5737ba`.
- `MPOD-BUG-02`: `341002d` (`fix(android): preserve last refresh on failures`).
- `MPOD-BUG-01`: `dda7734` (`fix(android): synchronize subscription carousel selection`).
- `MPOD-REVIEW-01` завершён с `PASS`, но последующая `MPOD-QA-02` завершена с `FAIL` на новом воспроизводимом blocker.
- Fresh release APK `1.0.17 (18)` собран из `eda4ece`; SHA-256: `1ce4c25e67ed3766e964988c841cb99535ebbcd5133eb9f821b01ebe04f04266`.
- QA-устройство: Pixel 9 AVD, API 37, `emulator-5554`. Установка на физический Xiaomi API 35 выполнена, но ручная приёмка там не засчитана из-за личного PIN.
- `MPOD-BUG-02` не проверялся после первого blocker BUG-01 и остаётся в статусе «review passed, product retest pending».
- Production-код и Git-история в ходе QA не изменялись.

---

## 1. АКТИВНО — 15.09.2026 12:58 MSK — Исправить post-Unsubscribe рассинхронизацию и некомпилируемый regression test

**ID:** `MPOD-BUG-01-R1`
**Направление:** Compose UI / subscriptions.
**Приоритет:** P1, release blocker.
**Статус:** подтверждён на fresh release, частота 1/1.
**Единственный следующий исполнитель:** Android-разработчик.

### Передать исполнителю целиком

> Исправить два подтверждённых blocker в текущей ветке `codex/qa-obvious-bugs` @ `eda4ece64d0be07ad28f5a69f0957b9afe5737ba`, не затрагивая `MPOD-BUG-02`.
>
> Blocker A — post-Unsubscribe рассинхронизация, Pixel 9 AVD API 37, частота 1/1:
>
> 1. Библиотека содержит два контролируемых подкаста: `Throttled30 Podcast` и `QA02 Beta Podcast`.
> 2. Для визуально выбранного `Throttled30 Podcast` выполняется Unsubscribe; snackbar подтверждает правильную цель.
> 3. После окончания 15-секундного countdown header показывает `1 podcast`, episode list показывает `QA02 Beta Episode 1`.
> 4. Карусель и summary продолжают показывать удалённый `Throttled30 Podcast`, действие остаётся `Pending` даже спустя дополнительные 16 секунд.
> 5. Состояние исправляется только после force-stop/relaunch.
>
> Expected: сразу после удаления карточка, summary, счётчики, episodes и podcast-scoped actions относятся к оставшемуся Beta; `Pending` исчезает.
>
> Evidence:
>
> - `/private/tmp/mpod-qa02-bug01-unsubscribe-desync.png`;
> - `/private/tmp/mpod-qa02-after-old-alpha-removal.xml`;
> - `/private/tmp/mpod-qa02-bug01-after-relaunch.xml`.
>
> Blocker B — committed unit-тест не компилируется:
>
> - `SubscriptionsCarouselMappingTest.kt:60,62-64,66` вызывает отсутствующий `selectedPodcastForCarouselPage`;
> - исправить тест так, чтобы он проверял реальный production mapping и не ссылался на несуществующий test-only/production helper;
> - не добавлять лишний production API только ради теста, если достаточно проверить `podcasts[podcastIndexForCarouselPage(...)]`.
>
> Разрешённый scope:
>
> - `app/src/main/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsScreen.kt`;
> - `app/src/main/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsViewModel.kt` только если доказана причина в переходе pending/deletion state;
> - `app/src/androidTest/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsScreenTest.kt`;
> - `app/src/test/java/com/example/mpod/ui/screens/subscriptions/SubscriptionsCarouselMappingTest.kt`;
> - узкий ViewModel regression test только при изменении ViewModel.
>
> Требования:
>
> 1. Сначала добавить regression, который переводит UI из двух podcasts в один после завершения pending unsubscribe и воспроизводит stale carousel card/summary.
> 2. Найти причину сохранения старого pager item/state после изменения списка ID; исправить identity/reset минимально, сохранив mid-drag синхронизацию и обе wrap-around границы.
> 3. После удаления выбранного подкаста authoritative selection должен немедленно указывать на существующий элемент; stale title/summary/action запрещены.
> 4. Исправить compile failure `SubscriptionsCarouselMappingTest` и подтвердить, что targeted unit class выполняется полностью.
> 5. Выполнить targeted unit и Compose regression, затем `testDebugUnitTest`, `connectedDebugAndroidTest` и `git diff --check`. Вернуть точные counts и команды.
>
> Не менять дизайн, `MPOD-BUG-02`, Room schema, versionName/versionCode, release APK или документацию. Не делать commit/push/revert/reset/rebase и не добавлять QA evidence в Git. Посторонние untracked-файлы не трогать.
>
> Формат результата: причина каждого blocker; точный scoped diff; FAIL-before/PASS-after нового regression; результаты targeted/full gate; `git status --short`. Если причина требует выхода за разрешённый scope — остановиться и вернуть доказательство.

**Критерий завершения:** новый regression защищает переход 2→1 после unsubscribe; card/summary/episodes/actions синхронны без relaunch; mapping unit class компилируется и проходит; полный gate зелёный; diff ограничен разрешённым scope.

**Следующий переход:** Team Lead code review; при `PASS` активировать `MPOD-QA-02-R1` на том же fresh candidate.

---

## 2. НЕАКТИВНО — Повторить продуктовую приёмку после BUG-01-R1

**ID:** `MPOD-QA-02-R1`
**Статус:** ожидает разработку и review `MPOD-BUG-01-R1`.
**Следующий исполнитель после активации:** Android-тестировщик.

Сначала повторить post-Unsubscribe сценарий и полный BUG-01 scope; затем выполнить ранее не начатые partial/all-failed/full-success проверки BUG-02 и короткий smoke.

---

## 3. НЕАКТИВНО — Устранить дублирующие initial/resume загрузки

**ID:** `MPOD-BUG-03`
**Приоритет:** P2.
**Статус:** выполнять только после закрытия `MPOD-QA-02-R1` и отдельного воспроизведения на актуальном кандидате.

Сначала подтвердить дублирование запросов наблюдаемым тестом. Без воспроизведения production-код не менять.

---

## Не возвращать в работу без новых фактов

- Не откатывать `341002d` или `dda7734`: rework должен быть минимальным дополнением к текущей ветке.
- Не считать прежний review `PASS` достаточным после фактического QA `FAIL`.
- Не отмечать SUB-05 или SUB-12 как `Verified` до успешного `MPOD-QA-02-R1`.
- Старый `MPOD-OPS-02` завершён checkpoint-коммитом `8655106` и больше не является текущей задачей.
