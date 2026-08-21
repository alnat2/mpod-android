---
name: frontend-implementation
description: Implement mpod Android UI components and screens in Jetpack Compose from Ready for Development Figma sections using Material 3, Hugeicons vector drawables, project docs, and strict design-to-code verification. Use when creating or updating mpod Android UI code from Figma, implementing Compose components, composing screens, or translating approved mobile Figma layouts into Jetpack Compose.
---

# mpod Android Frontend Implementation

Use this skill for mpod Android UI code work that implements Figma-approved mobile components or screens.

## Read First

Before implementing from Figma, read:

- `.agents/AGENTS.md`
- `docs/android-stage-1-audit.md`
- `docs/android-user-scenarios.md`
- `docs/android-delivery-plan.md`
- `app/src/main/java/com/example/mpod/ui/components/MpodUi.kt` for shared UI primitives and helpers
- `app/src/main/java/com/example/mpod/ui/theme/Theme.kt`, `Color.kt`, and `Type.kt` for styling tokens

## Hard Gates

- Implement only Figma elements from sections whose Figma status is `Ready for Development`.
- The status may be a Figma section status badge, not the section name. If the tool cannot read the status, use the user's screenshot or explicit confirmation; otherwise ask.
- Do not implement draft/reference sections unless the user explicitly approves that specific section.
- Do not invent behavior, variants, labels, icons, spacing, colors, or component states. If the Figma source or docs are unclear, ask before coding.
- Do not make assumptions to fill missing Figma, documentation, API, or code access. This skill is an execution checklist, not permission to guess.
- If the exact Figma file, page, frame, component, section, screenshot, or `Ready for Development` status cannot be accessed, stop and say: `I do not have access to the file/frame needed for this task.`
- If required code, docs, assets, or local files cannot be accessed, stop and say what access is missing before coding.
- If you have a design or implementation idea but the source is unavailable or ambiguous, stop and ask first: `I want to do X and Y. What do you say?`
- Never continue from memory, prior screenshots, or inferred design intent unless the user explicitly approves that fallback for the current task.
- Stay in the Android UI / Compose domain. If a visible symptom is caused by backend API behavior, service lifecycle, ExoPlayer media session, or database/room state, do not patch backend/core code blindly from this skill. State the suspected issue in chat.
- When installing or deploying to a device, follow `.agents/AGENTS.md`: default to the production/release variant (`com.prod.mpod`), using debug/test (`com.prod.mpod.test`) only when explicitly requested.

## Required Workflow

1. Confirm the target Figma section/component is `Ready for Development`.
2. Inspect the referenced Figma layout before writing code:
   - inspect the exact component/frame node (dimensions, padding in dp, font sizes and line heights in sp, corner radii, shadows)
   - inspect variants and state nodes individually
   - if a change touches a shared primitive such as `EpisodeRow`, `PlayerView`, `MpodUi`, `ShowNotesMobile`, or `AddPodcastModal`, inspect both the screen frame and the master component node before editing
   - do not push screen-specific measurements into shared primitives unless the shared component node itself confirms that change
   - when the change involves icons, inspect the exact icon node name used in the Figma component; verify exact Hugeicons vector drawable in `app/src/main/res/drawable/`
   - when the change involves semantic colors or borders, verify `MaterialTheme.colorScheme` tokens and do not hardcode arbitrary hex values
   - if any required Figma read fails or returns the wrong page/frame, stop and report the access problem instead of guessing
3. Cross-check behavior against Android project docs:
   - `docs/android-user-scenarios.md` for user flows and interaction requirements
   - `docs/android-stage-1-audit.md` for baseline decisions and accepted UI behavior
   - `docs/android-delivery-plan.md` for feature scope and scenario verification
4. Inspect existing Compose structure and primitives:
   - `app/src/main/java/com/example/mpod/ui/components/MpodUi.kt`
   - `app/src/main/java/com/example/mpod/ui/components/`
   - existing mpod Compose components before creating new ones
5. Implement with Jetpack Compose, Material 3, and Hugeicons:
   - use `SquareIconButton`, `MpodButton`, `MpodBottomSheet`, and `figmaDropShadow`
   - use Hugeicons vector drawables (`R.drawable.ic_huge_*`) with 24x24 viewport and consistent stroke
   - maintain proper accessibility semantics (`contentDescription`, `role = Role.Button`, `testTag`)
6. Verify:
   - run `./gradlew testDebugUnitTest` to ensure unit and UI logic tests pass
   - run `./gradlew compileDebugKotlin` / `./gradlew assembleDebug` to ensure compilation
   - update or check Compose Previews in `MpodComponentPreviews.kt`
   - for component-level Figma work, do not stop at "looks close"; explicitly verify:
     - visible control/button count and container size (e.g. 44.dp container, 10.dp radius, 1.dp border)
     - inner icon rendering and identity match (e.g. exact 24.dp icon rendering)
     - text line count, wrap/truncation (`TextOverflow.Ellipsis`), and fit match
     - left/middle/right layout alignment matches
     - touch targets meet Android accessibility standards (min 44–48dp touch targets)
     - if any one of these fails, the component is not done

## Implementation Rules

- Compose mpod components on top of Jetpack Compose Material 3 primitives and `MpodUi.kt`; do not build an ad-hoc design system.
- Keep reusable components in `app/src/main/java/com/example/mpod/ui/components/` and screen-level composables in `app/src/main/java/com/example/mpod/ui/screens/`.
- Use semantic `MaterialTheme.colorScheme` tokens (`primary`, `background`, `surface`, `surfaceVariant`, `onSurface`, `onSurfaceVariant`, `outline`, `tertiary`).
- Do not use raw hardcoded hex colors for app UI unless defining tokens in `Color.kt` or for documented asset colors.
- Use `Arrangement.spacedBy(...)` for layout spacing inside `Row` and `Column`.
- Use `Modifier.size(...)` when width and height are equal.
- Use exact user-facing labels from Figma and docs. Do not silently rename actions.
- Use `semantics { this.contentDescription = ... }` and `testTag(...)` for testability and accessibility.
- Keep Compose composables thin and state-driven; delegate actions to lambdas / ViewModels.

## Current Project Facts

- Project root: Android Gradle Project (`app/`)
- Language & Framework: Kotlin + Jetpack Compose + Material 3
- Architecture: MVVM + Hilt + Media3 ExoPlayer + StateFlow
- Icon library: Hugeicons (Android Vector Drawables in `app/src/main/res/drawable/ic_huge_*.xml`)
- Typography: `InterFontFamily` (`app/src/main/java/com/example/mpod/ui/theme/Type.kt`)
- Main Figma file: `3CmMv8wYlyNz9qDDdOd2Ka`
- Component nodes: `EpisodeItem` (`467-2600`), `PlayerMobile`, etc.

## Component Names

Use normalized Compose component names:

- `EpisodeRow` / `PlayerPlaylistItem` / `SubscriptionEpisodeItem`
- `PlayerView`
- `ShowNotesMobile`
- `AddPodcastModal`
- `SquareIconButton`
- `MpodButton`
- `MpodBottomSheet`
- `TopBar` / `MpodTopBar`
- `BottomNavBar`

## Ask Before Coding When

- The Figma status is not visible or not confirmed as `Ready for Development`.
- A component exists in Figma but has no clear behavior in the docs or Android user scenarios.
- A Figma layout conflicts with Android navigation or platform conventions.
- A Hugeicons vector drawable equivalent is not available in `res/drawable/`.
- The exact Figma icon exists conceptually but the rendered vector appears visually different.
- Semantic token names match between Figma and code, but the concrete values may be different.
- The implementation would require backend/service behavior not already documented.

## Done Check

Before finishing a component or screen:

- The Figma source was inspected at the specific node level.
- Exact icon identity and vector drawable were verified for any changed icon-bearing control.
- `MaterialTheme.colorScheme` token values were cross-checked when a change depended on semantic colors, borders, or selected states.
- The component matches the approved layout and documented behavior.
- Container sizes, radii, borders, and inner icon dimensions are strictly verified (e.g. 44dp button, 24dp icon, 10dp corner radius).
- Text fit was checked explicitly, including line count, lineHeight, and truncation/wrapping behavior.
- Touch target sizing and accessibility semantics (`contentDescription`, `role`, `testTag`) are present.
- Compose previews in `MpodComponentPreviews.kt` are updated and clean.
- `./gradlew testDebugUnitTest` passes.
- `./gradlew compileDebugKotlin` / `./gradlew assembleDebug` passes.
