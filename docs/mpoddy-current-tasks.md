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
- Текущий локальный и remote HEAD: `4392cce` (`docs: send Room migration guard to review`).
- `MPOD-BUG-01` и rework закрыты: `dda7734` + `da4c6a7`.
- `MPOD-BUG-02` закрыт: `341002d`.
- `MPOD-QA-02-R1`: `PASS` на Pixel 9 AVD API 37.
- `MPOD-MAINT-01` закрыт другой командой: `67c0da0` (`Fix duplicate Smart Listening start on Activity recreation`), опубликован в `origin/codex/qa-obvious-bugs`.
- `MPOD-REL-01` закрыт после review: `686423a` (`fix: require explicit Room migrations`), release APK собирается, commit опубликован в `origin/codex/qa-obvious-bugs`.
- `MPOD-BUG-03` закрыт: исходная concurrent-refresh гонка и follow-up cleanup/re-add гонка воспроизведены детерминированно и исправлены в `3fb7f45`.
- Финальные проверки `MPOD-BUG-03`: 173/173 JVM tests PASS; 64/64 connected tests PASS на Pixel 9 AVD API 37; debug/release lint PASS; `git diff --check` PASS.
- Room schema, `versionName`/`versionCode` и release APK в `MPOD-BUG-03` не менялись. Физический телефон не проверялся.
- Последний release-кандидат до `MPOD-BUG-03`: `mpoddy 1.0.17 (18)`, package `com.prod.mpod`, SHA-256 `d000c306e94b49ecec2778d7824457b307d1ae1e1b13434d272f08c344ce4372`. Он не содержит коммит `3fb7f45`.
- В рабочем дереве есть отдельные staged/unstaged изменения UI и task-документов; они не относятся к коммиту `3fb7f45` и не должны добавляться wildcard-командой.

---

## 1. АКТИВНО — 21.09.2026 — RSS namespace compatibility

**ID:** `MPOD-COMPAT-01`
**Направление:** RSS parser compatibility.
**Статус:** активировано; production-изменение разрешено только после детерминированного FAIL на валидном namespace fixture.
**Единственный следующий исполнитель:** Android-разработчик в отдельной Codex-задаче/worktree от `codex/qa-obvious-bugs`. Первый этап — локальный JVM test; устройство и эмулятор не нужны. Reviewer — текущая Codex-задача.

### Передать разработчику целиком

> Взять актуальное задание `MPOD-COMPAT-01` из `docs/mpoddy-current-tasks.md` и проверить RSS parser на корректную XML namespace-семантику.
>
> Фаза A — диагностика без production-изменений:
>
> 1. Добавить валидный RSS 2.0 fixture, где стандартные namespace URI объявлены под произвольными alias-prefixes, а не буквальными `itunes`, `content` и `dc`:
>    - iTunes: `http://www.itunes.com/dtds/podcast-1.0.dtd`;
>    - Content module: `http://purl.org/rss/1.0/modules/content/`;
>    - Dublin Core: `http://purl.org/dc/elements/1.1/`.
> 2. Проверить channel author/image/summary и item description/duration. Обычные RSS title/guid/enclosure/pubDate должны продолжать разбираться.
> 3. Сначала запустить targeted test на текущем production parser и сохранить PASS/FAIL. Если fixture неожиданно проходит полностью, production-код не менять и вернуть evidence.
>
> Фаза B — только при подтверждённом FAIL:
>
> 1. Минимально исправить `RssFeedParser`, сопоставляя расширения по namespace URI и local name, а не по буквальному prefix.
> 2. Сохранить поддержку существующих fixtures со стандартными prefixes, fallback description precedence, duration/date parsing и enclosure URL.
> 3. Не добавлять Atom/RSS 1.0 поддержку, сетевые изменения, UI, Room schema, version bump или unrelated parser refactor без отдельного воспроизведения.
> 4. Добавить контроль неизвестного namespace с тем же local name: он не должен ошибочно приниматься за iTunes/Content/DC.
>
> Проверки: targeted FAIL-before/PASS-after, все RSS/parser JVM tests, полный `testDebugUnitTest`, debug/release lint и `git diff --check`. Connected/device прогон не обязателен для чистого JVM parser fix; если затронут Android/integration scope, выполнить соответствующий connected test.
>
> После зелёных проверок создать локальный task-коммит только с файлами parser/tests и вернуть commit hash, точные counts, scope и ограничения. Не делать push. Не менять current-tasks документ — его обновляет reviewer.

**Критерий завершения:** валидные namespace aliases либо доказанно уже поддерживаются без production-правок, либо воспроизводящий fixture проходит после минимального namespace-aware исправления без регрессии стандартных RSS fixtures.

**Следующий переход:** текущая Codex-задача делает code review developer-коммита; при `APPROVED` закрывает `MPOD-COMPAT-01` и переводит кандидата в `MPOD-REL-02`.

---

## 2. НЕАКТИВНО — Финальный regression gate и новый APK

**ID:** `MPOD-REL-02`
**Статус:** выполнять после закрытия активных defect/maintenance задач по отдельному release-решению.

Нужны полный regression gate, решение о version bump, сборка нового `com.prod.mpod` release APK, SHA-256 и отдельная приёмка. Старый APK 1.0.17 (18) не содержит `MPOD-BUG-03`.

---

## Закрыто — не возвращать без новых фактов

- `MPOD-BUG-01`, `MPOD-BUG-01-R1`, `MPOD-BUG-02`, `MPOD-QA-02-R1`, `MPOD-BUG-03`, `MPOD-MAINT-01` и `MPOD-REL-01` закрыты.
- `MPOD-BUG-03`: commit `3fb7f45`; regression FAIL-before/PASS-after подтверждён; дополнительный review — `APPROVED`.
- Не откатывать `341002d`, `dda7734`, `da4c6a7` или `3fb7f45` ради искусственного FAIL-before.
- Старый `MPOD-OPS-02` завершён checkpoint-коммитом `8655106`.
