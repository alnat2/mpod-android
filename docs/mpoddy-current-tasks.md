# mpoddy Current Tasks

Единый оперативный источник текущего состояния Android-кандидата `mpoddy`.

Последняя редакция: 21 сентября 2026 года.

## Правила

- Одновременно активна только одна задача и назначен один следующий исполнитель.
- Активная задача находится первой и передаётся исполнителю целиком.
- Закрытая разработка не возвращается в работу без нового воспроизведения или blocker-замечания.
- Commit включает только явно перечисленные task-файлы; посторонние локальные файлы не добавляются.
- Version bump, release APK, push и публикация выполняются только по отдельному решению.

## Фактическое состояние кандидата

- Ветка: `codex/qa-obvious-bugs`.
- Текущий локальный HEAD: `3fb7f45` (`fix: make mark-all-listened cleanup race-safe`). Push не выполнялся; совпадение с remote HEAD после этого коммита не заявляется.
- `MPOD-BUG-01` и rework закрыты: `dda7734` + `da4c6a7`.
- `MPOD-BUG-02` закрыт: `341002d`.
- `MPOD-QA-02-R1`: `PASS` на Pixel 9 AVD API 37.
- `MPOD-BUG-03` закрыт: исходная concurrent-refresh гонка и follow-up cleanup/re-add гонка воспроизведены детерминированно и исправлены в `3fb7f45`.
- Финальные проверки `MPOD-BUG-03`: 173/173 JVM tests PASS; 64/64 connected tests PASS на Pixel 9 AVD API 37; debug/release lint PASS; `git diff --check` PASS.
- Room schema, `versionName`/`versionCode` и release APK в `MPOD-BUG-03` не менялись. Физический телефон не проверялся.
- Последний release-кандидат до `MPOD-BUG-03`: `mpoddy 1.0.17 (18)`, package `com.prod.mpod`, SHA-256 `d000c306e94b49ecec2778d7824457b307d1ae1e1b13434d272f08c344ce4372`. Он не содержит коммит `3fb7f45`.
- В рабочем дереве есть отдельные staged/unstaged изменения UI и task-документов; они не относятся к коммиту `3fb7f45` и не должны добавляться wildcard-командой.

---

## 1. АКТИВНО — 21.09.2026 — Единый lifecycle-владелец Smart Listening

**ID:** `MPOD-MAINT-01`
**Направление:** Android lifecycle / Smart Listening.
**Приоритет:** P3.
**Статус:** подтверждённое дублирование вызова без подтверждённого пользовательского сбоя.
**Единственный следующий исполнитель:** Codex как Android-разработчик в локальном репозитории на рабочем компьютере. Телефон и эмулятор для первого этапа не нужны.

### Передать исполнителю целиком

> Устранить дублирующий запуск Smart Listening observer. Сейчас `SmartListeningManager.startObserving()` вызывается и из `MpodApplication.onCreate()`, и из `MainActivity.onCreate()`. Сам manager защищён `if (observationJob != null) return`, поэтому подтверждённого пользовательского дефекта нет, но ownership размазан между process- и activity-lifecycle.
>
> Целевой контракт:
>
> 1. Единственный production-владелец запуска — `MpodApplication` на время жизни процесса.
> 2. Удалить injection `SmartListeningManager` и вызов `startObserving()` из `MainActivity`.
> 3. Не добавлять activity-level `stopObserving()`: recreation Activity не должна останавливать process-level observer или активные Smart Listening jobs.
> 4. Не менять автоматическую политику загрузки, debounce, cleanup, Room schema, UI и тексты.
>
> Реализация и проверки:
>
> 1. Сначала добавить или уточнить focused test, подтверждающий идемпотентность повторного `startObserving()` и отсутствие второго observer/job owner.
> 2. Выполнить минимальное production-изменение только в lifecycle wiring.
> 3. Проверить, что существующие cancellation/download/cleanup tests Smart Listening не регрессировали.
> 4. Прогнать targeted test, полный `testDebugUnitTest`, `connectedDebugAndroidTest` на Pixel 9 AVD API 37, debug/release lint и `git diff --check`.
> 5. Code review, затем локальный task-коммит только с явно перечисленными файлами. Не делать push, release APK или публикацию без отдельного решения.

**Разрешённый scope:** `MainActivity.kt`, узкий lifecycle/Smart Listening regression test; `SmartListeningManager.kt` только если тест докажет необходимость изменения его idempotency-контракта.

**Критерий завершения:** в production остаётся один process-level вызов `startObserving()`, повторный запуск остаётся безопасным и тестируемым, Activity recreation не владеет остановкой observer, все проверки проходят.

**Следующий переход:** code review; затем решить, активировать ли `MPOD-COMPAT-01` или перейти к release gate `MPOD-REL-02`.

---

## 2. НЕАКТИВНО — Безопасные Room-миграции до изменения схемы

**ID:** `MPOD-REL-01`
**Статус:** активировать только перед фактическим изменением Room schema.

Запретить destructive fallback для production migration path и добавить migration tests до увеличения версии схемы.

---

## 3. НЕАКТИВНО — RSS namespace compatibility

**ID:** `MPOD-COMPAT-01`
**Статус:** гипотеза; production-изменение не разрешено без fixture, который воспроизводит несовместимость.

---

## 4. НЕАКТИВНО — Финальный regression gate и новый APK

**ID:** `MPOD-REL-02`
**Статус:** выполнять после закрытия активных defect/maintenance задач по отдельному release-решению.

Нужны полный regression gate, решение о version bump, сборка нового `com.prod.mpod` release APK, SHA-256 и отдельная приёмка. Старый APK 1.0.17 (18) не содержит `MPOD-BUG-03`.

---

## Закрыто — не возвращать без новых фактов

- `MPOD-BUG-01`, `MPOD-BUG-01-R1`, `MPOD-BUG-02`, `MPOD-QA-02-R1` и `MPOD-BUG-03` закрыты.
- `MPOD-BUG-03`: commit `3fb7f45`; regression FAIL-before/PASS-after подтверждён; дополнительный review — `APPROVED`.
- Не откатывать `341002d`, `dda7734`, `da4c6a7` или `3fb7f45` ради искусственного FAIL-before.
- Старый `MPOD-OPS-02` завершён checkpoint-коммитом `8655106`.
