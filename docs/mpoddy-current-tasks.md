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
- Текущий локальный HEAD: `e7cc41e` (`fix: resolve XML namespace semantic aliases in RssFeedParser`); remote HEAD остаётся `4392cce`.
- `MPOD-BUG-01` и rework закрыты: `dda7734` + `da4c6a7`.
- `MPOD-BUG-02` закрыт: `341002d`.
- `MPOD-QA-02-R1`: `PASS` на Pixel 9 AVD API 37.
- `MPOD-MAINT-01` закрыт другой командой: `67c0da0` (`Fix duplicate Smart Listening start on Activity recreation`), опубликован в `origin/codex/qa-obvious-bugs`.
- `MPOD-REL-01` закрыт после review: `686423a` (`fix: require explicit Room migrations`), release APK собирается, commit опубликован в `origin/codex/qa-obvious-bugs`.
- `MPOD-COMPAT-01` закрыт после review: `e7cc41e`; alias-prefix fixture подтвердил FAIL-before/PASS-after, независимый итоговый прогон 176/176 JVM tests и debug/release lint прошли. Push не выполнялся.
- `MPOD-BUG-03` закрыт: исходная concurrent-refresh гонка и follow-up cleanup/re-add гонка воспроизведены детерминированно и исправлены в `3fb7f45`.
- Финальные проверки `MPOD-BUG-03`: 173/173 JVM tests PASS; 64/64 connected tests PASS на Pixel 9 AVD API 37; debug/release lint PASS; `git diff --check` PASS.
- Room schema, `versionName`/`versionCode` и release APK в `MPOD-BUG-03` не менялись. Физический телефон не проверялся.
- Последний release-кандидат до `MPOD-BUG-03`: `mpoddy 1.0.17 (18)`, package `com.prod.mpod`, SHA-256 `d000c306e94b49ecec2778d7824457b307d1ae1e1b13434d272f08c344ce4372`. Он не содержит коммит `3fb7f45`.
- В рабочем дереве есть отдельные staged/unstaged изменения UI и task-документов; они не относятся к коммиту `3fb7f45` и не должны добавляться wildcard-командой.

---

## 1. АКТИВНО — 21.09.2026 — Финальный regression gate и новый APK

**ID:** `MPOD-REL-02`
**Направление:** release regression / Android delivery.
**Статус:** ready; все известные defect/maintenance/compatibility задачи закрыты. До изменения версии, push, установки или публикации требуется отдельное решение пользователя.
**Единственный следующий исполнитель:** после release-решения — Android release engineer; финальная продуктовая приёмка — Android QA на выбранном устройстве.

### Передать исполнителю после release-решения

> Выполнить финальный regression gate из точного опубликованного HEAD ветки `codex/qa-obvious-bugs`, затем собрать новый production/release APK `com.prod.mpod` только после подтверждения versionName/versionCode и разрешения на push/install/publish.
>
> Обязательные предварительные условия:
>
> 1. Подтвердить exact local/remote HEAD и наличие `67c0da0`, `686423a`, `e7cc41e`; не собирать release из неполной ветки.
> 2. Получить точные `versionName` и `versionCode`; не выбирать их самостоятельно.
> 3. Отдельно подтвердить разрешение на push, установку на устройство и публикацию. Сборка APK сама по себе публикацией не является.
>
> Regression gate:
>
> 1. Полные JVM, connected tests на Pixel 9 AVD API 37, debug/release lint и `git diff --check`.
> 2. Release assembly/minification и launch smoke `com.prod.mpod` без очистки пользовательских данных.
> 3. Smoke основных сценариев: cold launch, Subscriptions, refresh, Mark all listened, Player/queue, Settings; отдельная проверка persistence после обновления APK.
> 4. Зафиксировать APK path, размер, SHA-256, commit, version, устройство/API и ограничения. Эмулятор не называть физическим телефоном.
>
> При любом FAIL не публиковать APK и не исправлять несколько проблем одновременно: сохранить первый blocker, Expected/Actual и evidence, затем открыть узкую rework-задачу.

**Критерий завершения:** новый versioned production APK собран из подтверждённого published commit, полный gate зелёный, APK идентифицирован SHA-256 и прошёл согласованную приёмку.

**Следующий переход:** при PASS закрыть Android-кандидат и сохранить release evidence; при FAIL активировать одну rework-задачу по первому blocker.

---

## Закрыто — не возвращать без новых фактов

- `MPOD-BUG-01`, `MPOD-BUG-01-R1`, `MPOD-BUG-02`, `MPOD-QA-02-R1`, `MPOD-BUG-03`, `MPOD-MAINT-01`, `MPOD-REL-01` и `MPOD-COMPAT-01` закрыты.
- `MPOD-BUG-03`: commit `3fb7f45`; regression FAIL-before/PASS-after подтверждён; дополнительный review — `APPROVED`.
- Не откатывать `341002d`, `dda7734`, `da4c6a7` или `3fb7f45` ради искусственного FAIL-before.
- Старый `MPOD-OPS-02` завершён checkpoint-коммитом `8655106`.
