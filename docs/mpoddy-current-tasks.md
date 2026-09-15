# mpoddy Current Tasks

Единый оперативный источник текущего состояния Android-кандидата `mpoddy`.

Последняя редакция: 15 сентября 2026 года.

## Правила

- Одновременно активна только одна задача и назначен один следующий исполнитель.
- Активная задача находится первой и передаётся исполнителю целиком.
- Диагностическая фаза не меняет production-код; исправление разрешается только после детерминированного воспроизведения.
- Commit включает только явно перечисленные task-файлы; посторонние локальные файлы не добавляются.
- Version bump, release APK и публикация выполняются только по отдельному решению.

## Фактическое состояние кандидата

- Ветка и удалённый HEAD: `codex/qa-obvious-bugs` @ `43b36ea412d3b1539d3e81a33a4ae3ea17c6d465`.
- `MPOD-BUG-01` и rework закрыты: `dda7734` + `da4c6a7`.
- `MPOD-BUG-02` закрыт: `341002d`.
- `MPOD-QA-02-R1`: `PASS` на Pixel 9 AVD API 37.
- Fresh release: `mpoddy 1.0.17 (18)`, package `com.prod.mpod`.
- APK SHA-256: `d000c306e94b49ecec2778d7824457b307d1ae1e1b13434d272f08c344ce4372`.
- Post-Unsubscribe 2→1, mid-drag/settle, обе wrap-around границы, filters и scoped actions прошли.
- Refresh All partial/all-failed сохранили прежний timestamp; full success обновил timestamp. Home, Subscriptions, Player и Settings smoke прошли.
- Ограничение QA: Player проверялся как короткий UI/control smoke, не как длительная real-audio приёмка.

---

## 1. АКТИВНО — 15.09.2026 20:21 MSK — Согласованное Mark all listened при concurrent refresh

**ID:** `MPOD-BUG-03`
**Направление:** Room / playlist / Smart Listening consistency.
**Приоритет:** P2.
**Статус:** риск подтверждён чтением кода, но пользовательский дефект ещё не воспроизведён.
**Единственный следующий исполнитель:** Android-разработчик, начиная только с диагностического regression test.

### Передать исполнителю целиком

> Исследовать и при подтверждении минимально исправить consistency race между `Mark all listened` выбранного подкаста и concurrent feed refresh в ветке `codex/qa-obvious-bugs` @ `43b36ea412d3b1539d3e81a33a4ae3ea17c6d465`.
>
> Наблюдаемый риск: `SubscriptionsViewModel.markAllListened()` сначала читает набор episodes через DAO, затем отдельно вызывает bulk update в repository и после этого очищает playlist/files по ранее прочитанному набору. Concurrent refresh может изменить набор между этими этапами, из-за чего Room listened-state, playlist/queue cleanup и локальные файлы могут относиться к разным episode IDs.
>
> Фаза A — обязательная диагностика без production-изменений:
>
> 1. Добавить детерминированный regression test с управляемыми barriers/fakes, где feed refresh меняет набор episodes между исходным чтением и bulk update.
> 2. Проверить согласованность точного набора ID для Room listened-state, playlist removal, queue reconciliation и Smart Listening file cleanup.
> 3. Покрыть минимум два порядка событий: новый episode появляется конкурентно и существующий episode исчезает/заменяется.
> 4. Проверить повторный Mark all listened: ноль лишних мутаций, отсутствие duplicate cleanup и стабильный результат.
> 5. Если тест не воспроизводит расхождение, production-код не менять; вернуть доказательство и предложить закрытие риска.
>
> Фаза B — только после детерминированного FAIL:
>
> 1. Перенести orchestration за repository-level consistency boundary и использовать один согласованный набор episode IDs для Room mutation и playlist cleanup.
> 2. Не изображать filesystem и Room как одну ACID-транзакцию: ошибка удаления файла должна оставаться наблюдаемой и повторяемой.
> 3. После операции не должно быть stale queue/downloaded-state или файла, потерявшего связь с Room.
> 4. Сохранить обычное поведение Mark all listened и существующий UI; дизайн и тексты не менять.
>
> Разрешённый scope после подтверждённого FAIL:
>
> - `SubscriptionsViewModel.kt` и его tests;
> - `PodcastRepository.kt` и узкие repository tests;
> - связанные DAO transaction/query методы и tests только при доказанной необходимости;
> - playlist/Smart Listening cleanup orchestration tests.
>
> Запрещено менять `MPOD-BUG-01/02`, Room schema, unrelated lifecycle, versionName/versionCode, release APK или документацию. Не делать commit/push/revert/reset/rebase до code review. Посторонние файлы не добавлять.
>
> Проверки при production-изменении: targeted regression FAIL-before/PASS-after, обычное и повторное выполнение, `testDebugUnitTest`, `connectedDebugAndroidTest`, debug/release lint и `git diff --check`.
>
> Формат результата: воспроизведён риск или нет; точная interleaving-схема; причина; scoped diff; тесты и counts; остаточные ограничения; `git status --short`.

**Критерий завершения:** либо детерминированно доказано отсутствие расхождения без production-правок, либо regression воспроизводит прежний race и проходит после минимального исправления с одним согласованным набором IDs.

**Следующий переход:** Team Lead code review; затем узкая QA concurrency-проверка. При отсутствии воспроизведения закрыть гипотезу и активировать `MPOD-MAINT-01`.

---

## 2. НЕАКТИВНО — Единый lifecycle-владелец Smart Listening

**ID:** `MPOD-MAINT-01`
**Приоритет:** P3.
**Статус:** подтверждённое дублирование без текущего пользовательского сбоя.

После активации оставить запуск автоматического наблюдения в `MpodApplication`, удалить лишний запуск из `MainActivity`, сохранить idempotent start/stop tests и проверить process recreation. Политику Smart Listening не перерабатывать.

---

## 3. НЕАКТИВНО — Безопасные Room-миграции до изменения схемы

**ID:** `MPOD-REL-01`
**Статус:** активировать только перед фактическим изменением Room schema.

Запретить destructive fallback для production migration path и добавить migration tests до увеличения версии схемы.

---

## 4. НЕАКТИВНО — RSS namespace compatibility

**ID:** `MPOD-COMPAT-01`
**Статус:** гипотеза; production-изменение не разрешено без fixture, который воспроизводит несовместимость.

---

## 5. НЕАКТИВНО — Финальный regression gate и новый APK

**ID:** `MPOD-REL-02`
**Статус:** выполнять после закрытия активных defect/maintenance задач по отдельному release-решению.

---

## Не возвращать в работу без новых фактов

- `MPOD-BUG-01`, `MPOD-BUG-01-R1`, `MPOD-BUG-02` и `MPOD-QA-02-R1` закрыты.
- Не откатывать `341002d`, `dda7734` или `da4c6a7` ради искусственного FAIL-before.
- Старый `MPOD-OPS-02` завершён checkpoint-коммитом `8655106`.
