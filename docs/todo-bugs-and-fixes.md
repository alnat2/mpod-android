# TODO: Identified Bugs & Detailed Fix Plan

Этот документ содержит полный разбор выявленных багов, архитектурных недоработок и пошаговый план исправления каждого из них.

---

## 1. Баг: Ошибка воспроизведения и сломанные метаданные при расшаривании ссылок (Missing Audio Link Resolver) — [РЕШЕНО]

**Статус:** `РЕШЕНО` в коммите `7a6a58a`. Проверено на эмуляторе (Apple Podcasts share flow) и полным набором unit-тестов.

### Симптомы
1. При шаринге ссылки из Apple Podcasts (или браузера) вида `https://podcasts.apple.com/.../id262026947` заголовок трека в плеере становится `id262026947`, а автор — `Audio`.
2. ExoPlayer падает с ошибкой источника (`UnrecognizedInputFormatException`), воспроизведение не запускается.
3. Плеер показывает ошибку: *"Could not play this episode. Check its audio source and try again."*
4. Битая запись сохраняется в БД (`episodes`) и встаёт на позицию 0 в плейлисте, ломая непрерывное воспроизведение.

### Первопричина (Root Cause)
* В `SingleTrackRepository` и `SharedIntentParser` заложена наивная предпосылка, что любой URL из интента или ввода — это прямая ссылка на аудиопоток (`.mp3`, `.m4a`).
* Веб-ссылка сохраняется в поле `EpisodeEntity.audioUrl` «как есть» и отдаётся в ExoPlayer, который пытается проиграть HTML-код страницы.
* Извлечение заголовка в `SingleTrackRepository.deriveTitle()` берёт последнюю часть пути URL (`path.substringAfterLast('/')`), получая ID каталога (`id262026947`) вместо названия эпизода.

### План исправления (How to Fix)

#### Шаг 1.1: Создать сервис `AudioLinkResolver`
1. Создать интерфейс и реализацию `AudioLinkResolver` в `com.example.mpod.data.network.resolver`:
   ```kotlin
   data class ResolvedAudioMetadata(
       val directAudioUrl: String,
       val title: String,
       val author: String? = null,
       val artworkUrl: String? = null,
       val durationSeconds: Int? = null
   )

   interface AudioLinkResolver {
       suspend fun resolve(rawUrl: String): Result<ResolvedAudioMetadata>
   }
   ```
2. Реализовать стратегии резолвинга:
   * **Стратегия 1: Прямой медиапоток (Direct Media Stream):**
     Если URL оканчивается на `.mp3`, `.m4a`, `.aac`, `.ogg`, `.wav` или HEAD-запрос возвращает MIME-тип `audio/*` — использовать URL напрямую.
   * **Стратегия 2: Apple Podcasts Resolver:**
     Если хост `podcasts.apple.com`:
     - Регулярным выражением извлекается `id` подкаста и `i=` (id эпизода).
     - Выполняется запрос к iTunes Lookup API: `https://itunes.apple.com/lookup?id=<EPISODE_ID>`.
     - Из ответа JSON берутся: `episodeUrl` (прямой mp3), `trackName` (настоящее название), `collectionName` (название подкаста), `artworkUrl600` (обложка), `trackTimeMillis` (длительность).
   * **Стратегия 3: Generic Web Page / OpenGraph Audio:**
     Если это обычная веб-страница:
     - Выполняется легковесный запрос и поиск мета-тегов: `<meta property="og:audio" content="...">`, `<audio src="...">`, `<link rel="alternate" type="application/rss+xml">`.
3. Зарегистрировать `AudioLinkResolver` в Hilt (`DataModule.kt`).

#### Шаг 1.2: Интегрировать резолвер в `SingleTrackRepository`
1. В `SingleTrackRepository.playSingleTrack(urlOrUri, title)`:
   - Сначала вызвать `audioLinkResolver.resolve(cleanUrl)`.
   - Если резолвинг успешен — сохранить в `EpisodeEntity` настоящий `directAudioUrl`, распарсенный `title`, имя подкаста/автора и обложку.
   - Если резолвинг не удался — вернуть ошибку и **не** загрязнять Room-базу и плейлист нерабочим треком.

#### Шаг 1.3: Обработка ошибок и UI-состояния при добавлении
1. В `AddPodcastViewModel`:
   - Пока идёт резолвинг ссылки — отображать состояние `isSubmitting = true`.
   - Если ссылка не содержит аудио — выводить в модалке понятное сообщение: *"Could not extract audio from this link. Make sure it's a valid podcast episode or direct audio link."*
2. В `MainActivity`:
   - Если шаринг пришёл извне и резолвинг не удался — передавать сообщение об ошибке на `HomeScreen`, чтобы пользователь понимал причину.

---

## 2. Баг: Фрагментация UI и корявые старые баннеры статусов/ошибок (UI Fragmentation) — [РЕШЕНО]

**Статус:** `РЕШЕНО` в коммите `1d36f96`. Канонический `MpodStatusBanner` вынесен в `MpodUi.kt`, все локальные `StatusCard` удалены, проверено на эмуляторе в темной теме.

### Симптомы
1. На экране плеера (`HomeScreen.kt`) баннер ошибки ExoPlayer выглядит как убогая серая плашка без иконки, без красной рамки и без кнопки закрытия.
2. На экране подписок (`SubscriptionsScreen.kt`) для рефреша используется обновленный `RefreshErrorBanner`, но для ошибок загрузки списка — старый серый блок.
3. На экране настроек (`SettingsScreen.kt`) используется третий изолированный вариант карточки статуса.

### Первопричина (Root Cause)
* В дизайн-системе [MpodUi.kt](file:///Users/cross/Desktop/VirtualBuddyShared/Vm-IX/my-projects/android-mpod/app/src/main/java/com/example/mpod/ui/components/MpodUi.kt) отсутствует централизованный компонент баннера статуса и ошибок.
* Каждый экран создавал собственные `private fun StatusCard` на скорую руку.
* При предыдущем редизайне баннер был обновлён только локально в `SubscriptionsScreen.kt`, а проектный аудит выполнен не был.

### План исправления (How to Fix)

#### Шаг 2.1: Создать универсальный `MpodStatusBanner` в `MpodUi.kt`
Создать единый канонический компонент в `MpodUi.kt`:
```kotlin
enum class StatusBannerType {
    Error,
    Info,
    Loading
}

@Composable
fun MpodStatusBanner(
    message: String,
    type: StatusBannerType = StatusBannerType.Error,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```
**Стилизация компонента (строго по дизайн-системе):**
* Форма: `RoundedCornerShape(16.dp)` с тенью `figmaDropShadow(style = FigmaShadowStyle.Sm, radius = 16.dp)`.
* Цвета:
  - При `Error`: рамка `BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)`, иконка `ic_huge_alert_circle` красного цвета, контейнер `MaterialTheme.colorScheme.surface`.
  - При `Info`/`Loading`: рамка `BorderStroke(1.dp, MaterialTheme.colorScheme.outline)`, индикатор загрузки или информационная иконка.
* Типографика: `14.sp`, `lineHeight = 20.sp`, `FontWeight.Medium`, цвет `MaterialTheme.colorScheme.onSurface`.
* Элементы управления:
  - Кнопка действия (например, «Try again»): `MpodButton` высотой 36 dp.
  - Кнопка закрытия: `SquareIconButton` с иконкой `ic_huge_multiplication_sign` (36 dp, radius 8 dp), закрывающая баннер.

#### Шаг 2.2: Заменить старые карточки на всех экранах
1. **`HomeScreen.kt`:**
   - Заменить использование `StatusCard` при ошибке воспроизведения (`playbackSummary.errorMessage`) на `MpodStatusBanner(type = StatusBannerType.Error, onDismiss = ...)`.
   - Заменить `StatusCard` при ошибке загрузки плейлиста (`state.errorMessage`) на `MpodStatusBanner(actionLabel = "Try again", onAction = ...)`.
   - Удалить `private fun StatusCard`.
2. **`SubscriptionsScreen.kt`:**
   - Заменить `RefreshErrorBanner` на `MpodStatusBanner`.
   - Заменить `SubscriptionsStatusCard` на `MpodStatusBanner`.
   - Удалить оба приватных метода из файла.
3. **`SettingsScreen.kt`:**
   - Заменить `SettingsStatusCard` на `MpodStatusBanner`.
   - Удалить приватный метод `SettingsStatusCard`.

---

## 3. Баг: Процессные и архитектурные уязвимости при проектировании (Process Enforcement) — [РЕШЕНО]

**Статус:** `РЕШЕНО` в коммите `f37b44f`. Правила `feature-integrity.md` созданы и внедрены в `.agents/AGENTS.md` и `frontend-implementation/SKILL.md`.

### Симптомы
1. Агент отчитывается «всё готово», протестировав только синтетический happy-path.
2. Правка дизайна делается локально на одном экране, оставляя легаси в остальных местах.
3. При вёрстке допускается двойной паддинг и обрезка текста, противоречащие Figma.

### Первопричина (Root Cause)
* Отсутствие автоматических ограничений и чек-листа верификации, обязывающих агента тестировать реальные сценарии и проверять дизайн-систему глобально.

### План исправления (How to Fix)

#### Шаг 3.1: Создать файл правил `.agents/rules/feature-integrity.md`
Добавить правила:
1. **Rule 1: Real-world Input Matrix.** При проектировании фич импорта/шаринга/ссылок обязательна матрица форматов: прямые файлы, платформенные ссылки (Apple Podcasts, Castbox), HTML-страницы. Запрещено сдавать задачу, проверив только идеальный `.mp3`.
2. **Rule 2: Zero Local Status Duplication.** Полный запрет на приватные `StatusCard` / `ErrorBanner` в экранах. Любые уведомления — только через `MpodStatusBanner`.
3. **Rule 3: Cross-screen Refactor Sweep.** Любое обновление визуального паттерна обязано сопровождаться поиском по проекту (`grep_search`) и обновлением всех экранов.

#### Шаг 3.2: Зафиксировать проверки в `.agents/AGENTS.md` и `frontend-implementation/SKILL.md`
Внести эти пункты в секцию `Hard Gates` и `Verification Checklist` скилла `frontend-implementation`.

---

## 4. Баг: Утечка треков быстрого прослушивания (Quick Listening / Single Tracks) на экран подписок и краш Refresh All — [РЕШЕНО]

**Статус:** `РЕШЕНО`. Проверено на эмуляторе (воспроизведение в плеере, чистый SubscriptionsScreen без карточек mpod://) и полным набором unit-тестов.

### Симптомы
1. При шаринге/добавлении единичного трека (например, эпизода TEDx Shorts через Apple Podcasts) в табе `SubscriptionsScreen` ошибочно появляется карточка подписки с заголовком «TEDx Shorts», описанием «Single and shared audio tracks from TEDx Shorts» и кнопками «Refresh» и «Unsubscribe».
2. При нажатии «Refresh All» или рефреше карточки трека приложение пытается запросить RSS-ленту по несуществующему URL схемы `mpod://...`, OkHttp выбрасывает `IllegalArgumentException: Expected URL scheme 'http' or 'https' but was 'mpod'`, и глобальное обновление падает с ошибкой.
3. При нажатии «Unsubscribe» на этой карточке срабатывает каскадное удаление подкаста, которое уничтожает эпизод и выбрасывает его из плейлиста прямо во время прослушивания.
4. При экспорте OPML системные URL схемы `mpod://` попадают в XML-файл экспорта подписок.
5. Если у пользователя нет RSS-подписок, но играет Quick Listen трек, фильтрация `podcasts` без проверки очереди блокирует плеер заглушкой `NoPodcastsEmptyState` на `HomeScreen`.

### Первопричина (Root Cause)
* В `PodcastDao.kt` запросы `getSubscriptionRowsFlow()`, `getAllPodcastsFlow()` и `getAllPodcasts()` фильтровали только точное совпадение: `WHERE feedUrl != 'mpod://single_tracks'`.
* В `SingleTrackRepository.kt` для треков с известным шоу был внедрён динамический URL: `val feedUrl = "mpod://single_tracks/${showSlug}"`.
* Из-за несовпадения строк Room обрабатывал запись как полноценную RSS-подписку.

### План исправления (How to Fix)

#### Шаг 4.1: Укрепление запросов базы данных (`PodcastDao.kt`)
Заменить точное сравнение на префиксный фильтр по схеме `mpod://%`:
* В `getSubscriptionRowsFlow()`: `WHERE pod.feedUrl NOT LIKE 'mpod://%'`.
* В `getAllPodcastsFlow()`: `WHERE feedUrl NOT LIKE 'mpod://%'`.
* В `getAllPodcasts()`: `WHERE feedUrl NOT LIKE 'mpod://%'`.

#### Шаг 4.2: Защита операций синхронизации и экспорта (`PodcastRepository.kt`)
* В `refreshPodcast(podcastId)`: добавить проверку `if (podcast.feedUrl.startsWith("mpod://")) return Result.success(Unit)`.
* В `exportOpml()`: дополнительно защитить выборку подкастов от системных URL.

#### Шаг 4.3: Нормализация URI и очистка (`SingleTrackRepository.kt`)
* Зафиксировать схему внутренних URL: `mpod://single_tracks/${showSlug}` / `mpod://single_tracks`.
* Добавить метод очистки осиротевших записей подкастов `mpod://%`, у которых нет эпизодов.

#### Шаг 4.4: Защитный слой в представлении (`SubscriptionsViewModel.kt` и `HomeScreen.kt`)
* В `SubscriptionsViewModel.kt` в `mapSubscriptionRows` добавить фильтр `filterNot { it.podcast.feedUrl.startsWith("mpod://") }`.
* В `HomeScreen.kt` изменить условие `!state.hasPodcasts` на `!state.hasPodcasts && state.queue.isEmpty()`, гарантируя, что Quick Listen трек всегда отображается в плеере и очереди даже при 0 RSS-подписок.

#### Шаг 4.5: Синхронизация `AddPodcastModal.kt` с макетом Figma (`694:2747`)
* Удален `Modifier.weight(1f)` у табов модального окна, из-за которого текст «Import OPML» обрезался до «Import» на экранах 320 dp.
* Табам возвращена автоматическая ширина по контенту (`hug contents`) с центрированием `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`.
* Размер кнопки закрытия шапки приведен к параметрам Figma: `size = 44.dp`, `iconSize = 24.dp`, `radius = 10.dp`.
* Текст кнопки действия в режиме OPML приведен к макету Figma (`694:2767`): `Import file` вместо ошибочного `Add Feed`.
* Плейсхолдер ссылки в режиме Audio URL приведен к макету: `https://feeds.example.com/podcast.xml`.

---

## 5. Баг: Коллизия названий кириллических подкастов в Single Tracks и задержка/сбои старых выпусков Apple Podcasts — [РЕШЕНО]

**Статус:** `РЕШЕНО`.

### Симптомы
1. В Show notes на экране плеера у выпуска *«Удача на дорогах»* отображалось чужое название подкаста (*«Утренняя ванна - Удача на дорогах»*) вместо *«Нуар в большом городе»*.
2. Ссылки на старые эпизоды Apple Podcasts (старше последних 60 выпусков подкаста) резолвились с задержкой 10–20+ секунд или падали с ошибкой источника (*«Could not play this episode. Check its audio source and try again.»*).
3. При перезагрузке приложения плеер пытался воспроизвести веб-страницу вместо аудиопотока.

### Первопричина (Root Cause)
1. **Коллизия slug в `SingleTrackRepository`:** Регулярка `[^a-z0-9_-]+` удаляла все кириллические буквы, сжимая любые русские названия в один общий URL `mpod://single_tracks/_`. В результате все русскоязычные единичные треки склеивались в один подкаст (тот, что был добавлен первым).
2. **Отсутствие RSS-фоллбэка в `DefaultAudioLinkResolver`:** API iTunes отдает только 60 последних выпусков. Для более старых выпусков резолвер переходил к скачиванию тяжелых веб-страниц, игнорируя официальный `feedUrl` подкаста, уже присутствующий в ответе iTunes.
3. **Отсутствие валидации и исцеления записей:** Если в поле `audioUrl` попадала веб-ссылка, `SingleTrackRepository` не обновлял существующую запись в Room при повторном открытии ссылки с исправленным медиа-URL.

### Решение (Fix)
1. В `SingleTrackRepository.kt`:
   - Генерация slug переведена на поддержку Unicode-букв и цифр (`[^\p{L}\p{N}_-]+`). Теперь «Нуар в большом городе» (`mpod://single_tracks/нуар_в_большом_городе`) и «Утренняя ванна» (`mpod://single_tracks/утренняя_ванна`) имеют строго изолированные подкасты.
   - Добавлено исцеление существующих записей в Room: при резолвинге прямого MP3 устаревшие поля `audioUrl`, `title` и `duration` обновляются в базе данных.
   - Запрещено сохранение веб-страниц Apple Podcasts в качестве `audioUrl`.
2. В `DefaultAudioLinkResolver.kt`:
   - Добавлен автоматический RSS-фоллбэк: если эпизод не найден в окне 60 выпусков iTunes API, резолвер извлекает `feedUrl` подкаста, быстро скачивает RSS-фид и находит прямой MP3 выпуска по названию/slug или GUID.

---

## 6. Баг: Захардкоженное слово «today» в строке обновления на экране Settings — [РЕШЕНО]

**Статус:** `РЕШЕНО`.

### Симптомы
1. В шапке экрана `Settings` отображалось: *"Last refresh today at 27.10 10:00"*, даже когда прошло несколько дней с момента последнего обновления.
2. Слово *"today"* зашивалось в `DataStore` в момент обновления ленты и никогда не актуализировалось при наступлении следующих дней.

### Первопричина (Root Cause)
* В `PodcastRepository.kt` результат форматирования генерировался со статическим текстом: `"Last refresh today at ${formatter.format(Date())}"` и сохранялся в `appSettingsDataStore.setLastRefreshTime(...)`.
* `SettingsViewModel.kt` передавал эту строку в UI «как есть», без относительного расчёта даты.

### Решение (Fix)
1. Создан утилитный класс `SettingsRefreshTimeFormatter` (`ui/screens/settings/SettingsRefreshTimeFormatter.kt`):
   - Осуществляет динамическое относительное форматирование на основе текущего системного времени:
     - Если обновление было сегодня: `Last updated today at HH:mm`.
     - Если обновление было в предыдущие дни: `Last updated d MMM at HH:mm` (например, `Last updated 27 Oct at 10:00`).
     - Если обновлений не было: `Last updated: never`.
   - Обеспечивает полную обратную совместимость: корректно парсит устаревшие строки из памяти реальных устройств (`Last refresh today at 27.10 10:00`) и сразу выводит их в актуальном виде без сброса данных приложения.
2. В `PodcastRepository.kt`:
   - Сохранение переведено на точный миллисекундный таймстемп `System.currentTimeMillis().toString()`, исключая захардкоженный текст в хранилище.
3. В `SettingsViewModel.kt`:
   - `lastRefreshHeaderText` формируется через `SettingsRefreshTimeFormatter.format(prefs.lastRefreshTimeFormatted)`.
   - Дефолтное значение обновлено на `Last updated: never`.
4. Описание карточки `Auto refresh` оставлено неизменным согласно дизайну Figma (`"Turn on for a scheduled update."`).
5. Написан набор модульных тестов `SettingsRefreshTimeFormatterTest.kt` для всех сценариев (legacy формат с телефона, epoch millis, ISO-8601, граничные случаи).

---

## Чек-лист проверки выполнения (Definition of Done)

- [x] `AudioLinkResolver` реализован и покрыт unit-тестами для Apple Podcasts URL и прямых аудиофайлов.
- [x] При шаринге ссылки из Apple Podcasts в эмуляторе трек корректно резолвится, получает настоящее название и играет в ExoPlayer.
- [x] `MpodStatusBanner` вынесен в `MpodUi.kt`.
- [x] Все локальные `StatusCard` удалены из `HomeScreen`, `SubscriptionsScreen`, `SettingsScreen`.
- [x] Ошибка ExoPlayer на `HomeScreen` отображается в нормальном баннере с красной рамкой, иконкой и кнопкой закрытия.
- [x] Правило `.agents/rules/feature-integrity.md` создано и подключено.
- [x] `PodcastDao` фильтрует все `mpod://%` URL в подписках и библиотеке.
- [x] `PodcastRepository.refreshPodcast` игнорирует `mpod://` подкасты без сетевых запросов и ошибок.
- [x] `SubscriptionsViewModel` не пропускает `mpod://` в UI даже на унаследованных данных.
- [x] `HomeScreen` отображает плеер и очередь при наличии треков Quick Listen даже без RSS-подписок.
- [x] Верстка `AddPodcastModal` (все 3 режима: RSS, OPML, Audio URL) на 100% соответствует Figma (`694:2747`): «Import OPML» полностью виден без обрезки букв, кнопка закрытия 44dp, кнопка действия в OPML — «Import file».
- [x] Устранена коллизия кириллических заголовков подкастов: каждый подкаст получает уникальный Unicode slug.
- [x] Добавлен быстрый фоллбэк на RSS-фид при обработке старых выпусков Apple Podcasts.
- [x] Unit-тесты для Unicode-тегов фидов и извлечения RSS-фида написаны и подтверждены.
- [x] Динамическое форматирование даты последнего обновления на экране Settings (`Last updated today at HH:mm` / `Last updated 27 Oct at 10:00`) реализовано и протестировано с поддержкой обратной совместимости.



