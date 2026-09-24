# Очистка остатков серверной архитектуры — 24.09.2026

**PASS.** Удалён код, который не участвует в текущем standalone-приложении. Основание: проверены ссылки во всех main/test/androidTest исходниках, потребители Hilt-зависимостей и фактический runtime-граф release.

## Удалено

- `BACKEND_SCHEME` / `BACKEND_ADDRESS` из обоих build types, включая старые адреса `192.168.0.222:5050` и `:5051`.
- Невызываемые `InstalledAppBuildInfo`, `currentInstalledAppBuildInfo`, `installedEnvironment`. Settings продолжает брать версию непосредственно из `BuildConfig.VERSION_NAME`.
- Неиспользуемые `ApiResponse`, `UserFacingApiException`, `requireApiBody`, `missingApiPayload`, `userFacingApiMessage` и JSON-парсер серверных ошибок `apiErrorMessage`.
- Зависимости Retrofit, converter-gson и OkHttp logging-interceptor; соответствующие записи version catalog. Gson также отсутствует в итоговом release runtime classpath.
- Невызываемые `formatSchedulerTimestamp`, `formatSettingsLastRefreshText` и конвертер старого числового duration `Double?.toDurationSeconds`. Действующее форматирование времени последнего refresh в repository не изменялось.
- Три test-класса с 10 тестами исключительно удалённого кода. Полезные тесты сохранены; в нескольких названиях слово Backend заменено на Saved/Stored/Import, поскольку они проверяют локальные данные.
- Избыточный domain-config старых IP/localhost. Он повторял общий `cleartextTrafficPermitted=true` и не ограничивал назначения запросов.

## Сохранено намеренно

Общая поддержка HTTP нужна для пользовательских RSS/audio URL. RSS namespace URI и URL-примеры в полях ввода не являются адресами backend. OkHttp, его Hilt-провайдер и динамический прокси реально используются RSS, Coil и Media3; они сохранены.

Исключения `CookiePrefs.xml` / `PendingPlaybackSync.xml` из backup и device transfer сохранены: старые файлы могут остаться после обновления. В XML добавлено пояснение. Исторические отчёты с прежними IP не переписывались и не считаются текущей конфигурацией.

## Проверки

| Проверка | Результат |
|---|---|
| Полный JVM suite | 168/168, 0 failures/errors/skips; прежде было 178, удалены 10 тестов неиспользуемых функций |
| Android: HTTP compatibility, backup rules, Settings | 12/12, 0 failures/errors/skips; Pixel_9 API 37, emulator-5554, debug/test package |
| Реальная локальная HTTP-лента | Клиент приложения получил RSS через MockWebServer и разобрал подкаст/выпуск на Android |
| HTTP-политика для произвольных feed/audio hosts | PASS; дополнительно проверен compiled XML release APK: общий HTTP разрешён, отдельных старых доменов нет |
| `lintDebug` / `lintRelease` | 0 errors; 83 / 73 warnings и по 1 information |
| Debug Android-test APK и minified release assembly | BUILD SUCCESSFUL; финальная команда завершилась за 25 секунд после первой полной компиляции |
| Release runtime dependencies | Retrofit, Gson, logging-interceptor отсутствуют |
| Содержимое release APK | Старые IP и `BACKEND_ADDRESS` / `BACKEND_SCHEME` отсутствуют в ZIP payloads (проверены UTF-8/UTF-16LE) |
| `git diff --check` | PASS |

Gradle работал с `--offline`: проверки исходников выполнялись, актуальность новых версий зависимостей по сети не проверялась. Снижение lint warnings относительно прежнего online-прогона нельзя целиком приписывать очистке.

Первый новый HTTP-тест не инициализировал Direct-настройки фабрики и получил ожидаемую защитную ошибку `Proxy configuration initialization timed out`. Подготовка теста исправлена через `createClient(AppSettings(isProxyEnabled = false))`; поведение приложения не ослаблялось. Первое падение сохранено, затем весь выбранный набор прошёл.

## Границы и evidence

Каталог `build/cleanup-legacy-20260924/`: оба журнала запуска, исходный test setup failure, XML результатов JVM/Android/lint, `results.json`, граф runtime dependencies, `artifact-check.json`, compiled network XML. Полный набор UI-тестов повторно не запускался: выполнены 12 целевых тестов, все остальные Android-тесты скомпилированы.

Новый production APK не устанавливался и не передавался как новый релиз. Версия не повышалась: assembly использована для проверки очистки. Итоговый APK предыдущей приёмки в `build/release-handoff/` сохранён и не содержит этих изменений. Физический телефон не использовался; установленное production-приложение на Pixel 9 не менялось и открыто после завершения тестов.
