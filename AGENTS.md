# Android mpod

Это Android-проект с Gradle wrapper. Не применяй к нему архитектурные ограничения web mpod автоматически.

## Область задачи

- Следуй текущему запросу и актуальному согласованному task/handoff. Старый QA report не переопределяет более новое решение пользователя.
- При работе с пользовательскими сценариями читай релевантные разделы `docs/android-user-scenarios.md`.
- `docs/android-delivery-plan.md` используй для delivery work после проверки его актуальности относительно текущей задачи.
- Не читай все task/report/handoff документы для каждого бага. Найди актуальный источник и нужные evidence.
- Для UI/Figma work прочитай `.agents/skills/frontend-implementation/SKILL.md`, если задача соответствует его назначению.

## Сборка и установка

- При запросе установить приложение на телефон по умолчанию собирай и устанавливай production/release вариант `com.prod.mpod`.
- Debug/test вариант `com.prod.mpod.test` используй только по явному запросу пользователя.
- Перед install проверь выбранный device, build variant и applicationId. Не удаляй app data и не выполняй uninstall для обхода ошибки без отдельного разрешения.
- Используй project Gradle wrapper и существующие build variants. Точные task names проверь в актуальной Gradle configuration.

## Проверка

- Bug fix должен иметь focused regression test либо воспроизводимый сценарий с объяснением, почему automated test непрактичен.
- Playback, background work и cleanup проверяй на затронутых state transitions и lifecycle сценариях.
- Emulator verification не называй phone verification. Если физический телефон не проверялся, явно сообщи это.
- Не смешивай developer implementation и QA acceptance: покажи, какие проверки реально выполнены.
