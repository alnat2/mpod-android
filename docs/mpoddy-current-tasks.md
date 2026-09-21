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
- Текущий локальный HEAD: `686423a` (`fix: require explicit Room migrations`). Push этого коммита не выполнялся.
- `MPOD-BUG-01` и rework закрыты: `dda7734` + `da4c6a7`.
- `MPOD-BUG-02` закрыт: `341002d`.
- `MPOD-QA-02-R1`: `PASS` на Pixel 9 AVD API 37.
- `MPOD-MAINT-01` закрыт другой командой: `67c0da0` (`Fix duplicate Smart Listening start on Activity recreation`), опубликован в `origin/codex/qa-obvious-bugs`.
- `MPOD-BUG-03` закрыт: исходная concurrent-refresh гонка и follow-up cleanup/re-add гонка воспроизведены детерминированно и исправлены в `3fb7f45`.
- Финальные проверки `MPOD-BUG-03`: 173/173 JVM tests PASS; 64/64 connected tests PASS на Pixel 9 AVD API 37; debug/release lint PASS; `git diff --check` PASS.
- Room schema, `versionName`/`versionCode` и release APK в `MPOD-BUG-03` не менялись. Физический телефон не проверялся.
- Последний release-кандидат до `MPOD-BUG-03`: `mpoddy 1.0.17 (18)`, package `com.prod.mpod`, SHA-256 `d000c306e94b49ecec2778d7824457b307d1ae1e1b13434d272f08c344ce4372`. Он не содержит коммит `3fb7f45`.
- В рабочем дереве есть отдельные staged/unstaged изменения UI и task-документов; они не относятся к коммиту `3fb7f45` и не должны добавляться wildcard-командой.

---

## 1. АКТИВНО — 21.09.2026 — Безопасные Room-миграции

**ID:** `MPOD-REL-01`
**Направление:** Room persistence / release safety.
**Статус:** реализация завершена в `686423a`; ожидается code review.
**Единственный следующий исполнитель:** reviewer Android/Room. Проверять локальный commit `686423a`; production APK и физическое устройство для review не требуются.

### Передать ревьюеру целиком

> Проверить `MPOD-REL-01` в commit `686423a`.
>
> - Production database builder больше не вызывает `fallbackToDestructiveMigration()`: при будущей несовместимой версии без явной migration приложение должно отказать в открытии, а не удалять пользовательские данные.
> - `MpodDatabase` остаётся version 1; вымышленная migration 1→2 не добавлялась.
> - Включён `exportSchema`, schema v1 зафиксирована в `app/schemas/com.example.mpod.data.local.MpodDatabase/1.json` и подключена как androidTest asset.
> - Добавлен Room `MigrationTestHelper` baseline: создаёт базу из архивной schema v1, записывает podcast/episode/playlist и повторно открывает её текущим `MpodDatabase` без потери данных.
> - Проверки: 175/175 JVM tests PASS; targeted migration baseline PASS; 65/65 connected tests PASS на Pixel 9 AVD API 37; debug/release lint PASS; `git diff --check` PASS.
> - Проверить scope из шести файлов, отсутствие schema/version bump, release APK и push.

**Критерий завершения:** review подтверждает отсутствие destructive fallback, корректность schema archive/test wiring и честную границу: реальная migration добавляется только вместе с будущим schema bump.

**Следующий переход:** при `APPROVED` закрыть `MPOD-REL-01` и активировать `MPOD-COMPAT-01`; при blocker исправить его в этой же задаче.

---

## 2. НЕАКТИВНО — RSS namespace compatibility

**ID:** `MPOD-COMPAT-01`
**Статус:** гипотеза; production-изменение не разрешено без fixture, который воспроизводит несовместимость.

---

## 3. НЕАКТИВНО — Финальный regression gate и новый APK

**ID:** `MPOD-REL-02`
**Статус:** выполнять после закрытия активных defect/maintenance задач по отдельному release-решению.

Нужны полный regression gate, решение о version bump, сборка нового `com.prod.mpod` release APK, SHA-256 и отдельная приёмка. Старый APK 1.0.17 (18) не содержит `MPOD-BUG-03`.

---

## Закрыто — не возвращать без новых фактов

- `MPOD-BUG-01`, `MPOD-BUG-01-R1`, `MPOD-BUG-02`, `MPOD-QA-02-R1`, `MPOD-BUG-03` и `MPOD-MAINT-01` закрыты.
- `MPOD-BUG-03`: commit `3fb7f45`; regression FAIL-before/PASS-after подтверждён; дополнительный review — `APPROVED`.
- Не откатывать `341002d`, `dda7734`, `da4c6a7` или `3fb7f45` ради искусственного FAIL-before.
- Старый `MPOD-OPS-02` завершён checkpoint-коммитом `8655106`.
