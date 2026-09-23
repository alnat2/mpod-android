# MPOD-REL-02 — закрыто, 23.09.2026

**PASS. Все задачи текущего списка закрыты.** По прямой команде пользователя исправление, regression tests, QA-отчёты и fixture-пакет зафиксированы и отправлены в `origin/codex/qa-obvious-bugs`.

## Итоговый APK

- Source commit: `eb34273be19fd4ce593f282ae458a6ef26129c8b`. Его hash на remote проверен через `git ls-remote` после push.
- Файл: `build/release-handoff/mpoddy-1.0.18-19-eb34273-6880c4432152.apk`.
- Package/version: `com.prod.mpod`, 1.0.18 (19), minSdk 26, targetSdk 35.
- Размер: 4 915 716 байт.
- SHA-256: `6880c44321522708c02fd8092196281830035f16dfd4737d54742ddc74a8935a`.
- `apksigner verify`: PASS. SHA-256 сертификата: `61f0b1bb4485fcf4333e005e1adb43115340eb6b63b8f378cce5319430a4d012`; прежний debug-key fallback release-конфигурации.
- `:app:assembleRelease` после публикации source commit: `BUILD SUCCESSFUL in 1m 6s`, 3 tasks executed, 51 up-to-date. Это инкрементальная сборка, не clean rebuild.

SHA-256 отличается от проверенного локального APK `585949d1…`, потому что AGP обновил `META-INF/version-control-info.textproto`: revision `26112e1…` → `eb34273…`. Сопоставлены все 218 ZIP entries: только этот файл имеет другое содержимое. DEX, manifest, ресурсы и остальные 217 entries совпадают побайтово; сертификат прежний. Полный QA повторно не запускался, поскольку исполняемое содержимое не изменилось. Версия существующего кандидата сохранена.

## Подтверждённые проверки

| Проверка | Результат |
|---|---|
| Полный JVM-прогон | 178/178, failures/errors/skips = 0 |
| Полный instrumentation-прогон, Pixel 9 API 37 | 65/65, failures/errors/skips = 0 |
| Debug / release lint | 0 errors; 117 / 96 warnings и по 1 information, без изменений числа/категорий относительно прошлого отчёта |
| QA fixture support | 9/9, новый прогон перед commit |
| APK package, версия, подпись | PASS |
| Update итогового APK без очистки данных | PASS, установленный SHA-256 совпадает с итоговым файлом |
| Сохранность после update | 6 подписок; Planet Money 356 выпусков; QA race 4 в очереди, Downloaded, позиция 0:23, скорость 1.5x, Play без autoplay; Settings и Last refresh 23.09 15:40 сохранены |
| Crash buffer после запуска | Пуст |
| Diff whitespace check | PASS |

Перед финальным update эмулятор был остановлен. Запущен существующий Pixel_9 с сохранёнными данными; новый AVD не создавался, wipe/uninstall/pm clear не выполнялись. Эмулятор оставлен работающим на Settings.

Реальные пользовательские проверки и исправление: `mpoddy-fix-and-stage2-2026-09-23-report.md`. Закрытие полного Refresh all и прямой инспекции Room/files: `mpoddy-open-items-2026-09-23-report.md`. Полный технический прогон: `mpoddy-stage3-2026-09-23-report.md`. Машиночитаемые данные итогового APK: `mpoddy-release-1.0.18-final.json` и JSON рядом с APK.

Локальное evidence финализации: `build/release-close-20260923/` — assembly log, подпись/badging, сравнение ZIP entries, UI до/после update, crash buffer, fixture tests. Сырые результаты полного QA находятся в каталогах `build/qa-*`, указанных в соответствующих отчётах; эти generated-каталоги не входят в Git. Сам APK сохранён локально в release-handoff, а не опубликован в GitHub Release.

## Существенные границы

Закрыт согласованный emulator gate. Физический телефон, акустическая оценка звука, OEM/физические аудиомаршруты и длительное расписание не проверялись. Прямая инспекция private-данных относится к дополнительному Google APIs AVD с тем же release-кодом, а не к исходному Google Play Pixel 9. Это явно согласованные/описанные границы, не скрытые PASS.

Коммит и push выполнены в рабочую ветку; merge в main, Google Play, GitHub Release и production deploy не выполнялись. Финальный служебный commit обновляет только документацию; source commit APK выше остаётся точным идентификатором его исходников.
