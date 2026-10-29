# TODO: Identified Bugs & Detailed Fix Plan

Этот документ содержит полный разбор выявленных багов, архитектурных недоработок и пошаговый план исправления каждого из них.

---

## 1. Баг: Ошибка воспроизведения и сломанные метаданные при расшаривании ссылок (Missing Audio Link Resolver)

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

## 2. Баг: Фрагментация UI и корявые старые баннеры статусов/ошибок (UI Fragmentation)

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

## 3. Баг: Процессные и архитектурные уязвимости при проектировании (Process Enforcement)

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

## Чек-лист проверки выполнения (Definition of Done)

- [x] `AudioLinkResolver` реализован и покрыт unit-тестами для Apple Podcasts URL и прямых аудиофайлов.
- [x] При шаринге ссылки из Apple Podcasts в эмуляторе трек корректно резолвится, получает настоящее название и играет в ExoPlayer.
- [x] `MpodStatusBanner` вынесен в `MpodUi.kt`.
- [x] Все локальные `StatusCard` удалены из `HomeScreen`, `SubscriptionsScreen`, `SettingsScreen`.
- [x] Ошибка ExoPlayer на `HomeScreen` отображается в нормальном баннере с красной рамкой, иконкой и кнопкой закрытия.
- [x] Все unit-тесты (`./gradlew testDebugUnitTest`) и сборка (`./gradlew assembleDebug`) проходят успешно.
- [x] Правило `.agents/rules/feature-integrity.md` создано и подключено.
