# mpod Android — Stage 1 audit and defect backlog

Audit date: 2026-07-15

Audited baseline: `1.0.4 (5)`, commit `d5fb07c`

Status: completed and accepted by the product owner on 2026-07-15

## Executive result

No P0 defect was found. The proposed backlog contains four P1 defects, four P2 defects, and two P3 maintenance items. The current APK remains suitable for feature testing against the test backend, but it is not release-ready.

The already reported visual regressions around theme selection, the Home episode menu, the bottom-navigation background, podcast fallback artwork, refresh animation, the time picker, and show notes are present in the accepted baseline and were not reopened without new contrary evidence.

## Evidence collected

- Compared the Android Figma screens under node `880:4200` and mobile components under node `700:4536` with the implementation and rendered application.
- Inspected Setup, Login, Home, Home empty state, Subscriptions, Subscriptions error state, Add podcast, no-podcast state, Settings, Show notes, player, episode rows, podcast cards, and bottom navigation.
- Traced all Android Retrofit calls to the Go router and parent-project API/product documentation.
- Queried the live test backend for auth/session, Settings, scheduler, and proxy response shapes with account `t`.
- Ran the complete local test/build gate and the full connected suite on the Pixel 9 emulator.
- Confirmed the physical phone has test app `com.prod.mpod.test`, version `1.0.4 (5)`, and system night mode enabled. A new interactive phone pass could not be completed because the device was locked.

Verification commands and result:

```text
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL

ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
13/13 passed, BUILD SUCCESSFUL
```

Automated baseline remains 42 local unit tests and 13 connected tests.

## Figma parity audit

| Surface/state | Result | Evidence and remaining risk |
|---|---|---|
| Setup and Login | Structurally matches | Code, previews, and connected auth-shell coverage inspected; live first-user setup was not destructively recreated |
| Home populated | Matches the approved base layout | Light emulator render checked; player, summary, queue rows, compact Play/Pause + Remove menu, and bottom navigation match the mobile design structure |
| Home empty/no podcasts | Implemented to the Figma state | Static and UI-test inspection; real backend was intentionally not emptied |
| Subscriptions populated | Matches the approved base layout | Light emulator render checked with real Planet Money data and fallback artwork |
| Subscriptions error | Implemented to the Figma state | Error banner and retry routing inspected; network-failure automation is missing |
| Add podcast modal | Matches the approved modal structure | RSS/OPML modes, dimmed backdrop, validation, and document picker traced; full real-device error matrix is missing |
| Settings | Matches the approved card/switch layout | Light emulator render checked; system/Light/Dark behavior and Material time picker are covered by existing tests |
| Show notes | Matches the approved modal structure | Real feed notes and scrolling were checked in the accepted baseline; the drawn scrollbar is not bound to scroll position |
| Podcast artwork fallback | Matches the approved asset | Loading, missing URL, and image-error paths all render `podcast_fallback` |
| Bottom navigation and system insets | Matches current accepted baseline | Light emulator render has no white side boundaries; Android 14 navigation inset is respected |
| Dark theme | Matches current accepted baseline | Physical phone reports system night mode; prior accepted phone render is reused because the phone was locked during this audit |
| Font/display scaling and TalkBack | Not yet verified | No full 1.3x/2.0x, display-size, keyboard-navigation, or TalkBack matrix exists |

No new blocking Figma mismatch was proven in Stage 1. Accessibility and exhaustive state-by-state visual acceptance remain Stage 4 work.

## Visible action trace

| User action | Production path | Current assessment | Missing repeatable evidence |
|---|---|---|---|
| Setup/register, login | `AppLaunchViewModel` → auth API | Implemented | Setup-required real backend, invalid/timeout/expired-session UI tests |
| Logout | `AppLaunchViewModel.logout` → stop service → logout/session APIs | Defect A-02 | Logout failure and recovery test |
| Navigate main tabs | `AppNavigation` → Compose destinations | Verified baseline | Process recreation and back-stack matrix |
| Refresh all | `SubscriptionsViewModel.refreshAll` → `POST /podcasts/refresh-all` | Defect A-01 | Scheduler-running/completed/failed integration test |
| Refresh podcast | `refreshPodcast` → per-podcast refresh → reload | Implemented | Timeout, overlap, and feed-error integration tests |
| Show all/unlistened | local `SubscriptionVisibility` state | Verified baseline | Rotation/process recreation decision and test |
| Select podcast | local selected podcast state | Verified baseline | Rotation/process recreation test |
| Unsubscribe/undo | delayed ViewModel job → delete podcast | Implemented | 15-second commit/cancel and process-death tests |
| Mark all listened | optimistic update → per-episode PATCH calls → reload | Defect A-06 | Partial-failure and large-podcast tests |
| Add/remove playlist | optimistic episode update → playlist API → reload/invalidate | Implemented | Success/failure reconciliation integration tests |
| Mark listened/unlistened | optimistic update → episode PATCH → reload/invalidate | Implemented | File/queue lifecycle integration tests |
| Download episode | download API → reload; timed failure banner | Implemented | Success, failure, cancellation, and file lifecycle tests |
| Show notes | local modal using backend `showNotes`/description | Verified baseline | Links, HTML edge cases, TalkBack, and scrollbar behavior |
| Play/pause/seek/speed | MediaController → `PlaybackService` → playback/settings APIs | Defect A-04 | Device-level Media3/service and failure/retry tests |
| Reorder queue | drag/menu → optimistic reorder → playlist API | Implemented | Gesture instrumentation and backend rollback test |
| Add RSS | `AddPodcastViewModel` → create podcast | Implemented | Valid, duplicate, invalid-feed, timeout E2E tests |
| Import OPML | document picker → multipart import | Defect A-07 | Size, malformed file, permission, and cancellation tests |
| Export OPML | create-document picker → backend response → content resolver | Implemented | Empty/error/permission and byte-content tests |
| Save refresh time | Material TimePicker → settings PATCH → reload | Defect A-05 | PATCH-success/reload-failure integration test |
| Toggle proxy | settings PATCH → reload | Defect A-05 | Off/running/error and reload-failure integration tests |
| Toggle theme | persistent System/Light/Dark mode | Verified baseline | Full screen contrast and accessibility matrix |

## Prioritized defect backlog

### A-01 — P1 — Refresh all finishes before the backend refresh job

Status: resolved and verified in Android `1.0.5 (6)` on 2026-07-15.

Reproduction:

1. Open Subscriptions and press the refresh-all header action.
2. Backend accepts `POST /api/podcasts/refresh-all` and starts a background job.
3. Android immediately reloads subscriptions and clears the refreshing state.

Expected: the refresh indicator remains active while `GET /api/jobs/status` reports a running job; on completion Android reloads the library, and on failure it shows the backend failure.

Actual: `SubscriptionsViewModel.refreshAll()` reloads immediately after the accepted response and never observes job status. Newly fetched episodes may not appear, and the UI can claim the refresh finished while it is still running.

Affected code: `SubscriptionsViewModel.kt:63-84`; backend contract in parent `docs/product-decisions.md` requires clients to observe `GET /api/jobs/status`.

Missing test: fake/fixture backend integration covering accepted → running → completed/failed, overlapping requests, and the final reload.

### A-02 — P1 — Backend outage is treated as unauthenticated state

Status: resolved and verified in Android `1.0.6 (7)` on 2026-07-16.

Reproduction:

1. Keep a valid stored session, make the backend unreachable, then cold-start the app.
2. Alternatively, press Log out while the logout request and following session request cannot reach the backend.

Expected: show a distinct connection/retry state; do not claim that authentication state is known. Logout should either be confirmed or show that it could not be completed.

Actual: any session transport failure resolves to `Unauthenticated`. Logout ignores its response, then performs the same ambiguous session check. The user can see Login even though the server session may still be valid and return on the next successful launch.

Affected code: `AppLaunchViewModel.kt:35-44` and `84-91`.

Missing test: valid-cookie + offline startup, 401 versus timeout, logout 200 versus timeout/500, reconnect, and expired-session scenarios.

Confirmed decision: show a dedicated unavailable-backend state with a `Retry` action. Do not substitute cached read-only content for this state in the current scope.

Resolution evidence: session response handling now distinguishes HTTP 401 from transport/server failures; failed or ambiguous logout no longer opens Login. The full local gate passed with 51 unit tests, the Pixel 9 connected suite passed 14/14, and a valid stored session was manually cold-started offline, shown the dedicated state, then restored by Retry directly to Subscriptions without new credentials.

### A-03 — P1 — Session cookie is eligible for Android backup/transfer

Status: resolved and verified in Android `1.0.7 (8)` on 2026-07-16.

Reproduction:

1. Log in so `PersistentCookieJar` writes `CookiePrefs`.
2. Inspect Android backup/data-transfer configuration.

Expected: authentication cookies and other security-sensitive session state are excluded from cloud backup and device transfer, or app backup is explicitly disabled.

Actual: `allowBackup=true`; both XML files are untouched sample templates with no exclusions; `CookiePrefs` contains serialized session cookies. A restored cookie can authenticate against the same backend without re-entering credentials while the server session is valid.

Affected code: `AndroidManifest.xml:9-17`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `PersistentCookieJar.kt:12-57`.

Missing test/check: backup-rule validation plus clean-install/device-transfer session behavior.

Resolution evidence: `CookiePrefs.xml` is excluded from legacy backup, Android cloud backup, and device transfer while backup remains enabled for non-sensitive preferences. Two connected tests parse the packaged XML resources and require every exclusion. The complete 16/16 Pixel 9 suite passed; after clearing app data, the app opened Login and contained only the appearance preference file, with no session cookie file or restored authentication.

### A-04 — P1 — Playback synchronization failures are silent and important writes are not retried

Status: resolved and verified in Android `1.0.8 (9)` on 2026-07-16.

Reproduction:

1. Start playback, then make `PUT /api/playback/active` or `POST /api/playback` fail.
2. Pause, seek, change speed, or let an episode complete while the failure is present.
3. Restore connectivity and restart playback/app.

Expected: critical playback writes are retried or surfaced as unsynced; completion and active-episode state reconcile deterministically after connectivity returns.

Actual: active episode, progress, speed, completion, and queue-reconciliation requests are wrapped in silent `runCatching` paths. A failed one-shot active/completion/pause write can leave the backend restoring stale state with no user-visible indication.

Affected code: `PlaybackService.kt:101-119`, `161-169`, `173-263`, and `283-308`.

Missing test: device-level Media3/service suite with transient 5xx/timeout/offline failures, retry, pause, seek, auto-next, completion, and process restart.

Resolution evidence: active playback, progress/seek/completion, and speed mutations now enter a persistent coalescing store before the network request and retry with bounded backoff after transient failures. Completion cannot be overwritten by later progress, backward-seek intent remains until acknowledgement, delayed completion uses fresh retry time, and pending state is reconciled before queue restoration after process restart. The full gate passed with 59 unit and 17/17 connected tests. Pixel 9 real-backend checks verified offline seek, speed, and active mutations across forced process stop and reconnect; the pending XML cleared only after successful retry, Home restored position/speed/active state without autoplay, and the crash buffer remained empty. A broader automated Media3 completion/auto-next/interruption matrix remains tracked for Stage 3.

### A-05 — P2 — Settings can hide a failed post-save reload

Status: resolved and verified in Android `1.0.9 (10)` on 2026-07-16.

Reproduction:

1. Allow the settings PATCH to succeed.
2. Fail the immediately following settings/status reload.

Expected: retain the confirmed value and show that status refresh failed, or show a clear save/reload error.

Actual: both refresh-time and proxy flows only clear the saving flag; no error is set, so the user cannot tell whether the displayed state is authoritative.

Affected code: `SettingsViewModel.kt:47-81`.

Missing test: PATCH success followed by failure of settings, scheduler, or proxy GET.

Resolution evidence: Android applies the successful PATCH response as confirmed local state before reloading Settings/Scheduler/Proxy. A failed reload retains the confirmed value, clears the relevant saving flag, and shows an explicit saved-but-status-refresh-failed message. Status GET responses now require a successful HTTP response and body. Unit tests cover both confirmed-save/reload-failure and successful replacement by reloaded state; the complete local and connected gates passed.

### A-06 — P2 — Mark all listened is non-atomic and can partially complete

Status: resolved and verified in Android `1.0.10 (11)` on 2026-07-16.

Reproduction:

1. Run Mark all listened on a podcast with several unlistened episodes.
2. Make one PATCH fail after earlier PATCH requests have succeeded.

Expected: atomic backend operation, or explicit partial-result handling that identifies remaining episodes and offers a reliable retry.

Actual: Android sends concurrent per-episode requests in batches. A failure can leave a subset listened; the UI reloads and shows a generic error. Queue invalidation only runs when every request succeeds.

Affected code: `SubscriptionsViewModel.kt:186-216` and `350-370`.

Missing test: mixed success/failure batches, queue state after partial completion, and retry.

Confirmed backend contract:

- `POST /api/podcasts/{podcastId}/mark-all-listened` owns the operation.
- One SQLite transaction updates `episodes.is_listened`, playlist state, `active_playback`, and `downloaded_path` for the podcast.
- `markedEpisodes` counts only episodes that actually changed from unlistened to listened.
- Repeating the request is safe and returns `markedEpisodes: 0`, while still reconciling playlist and active-playback state.
- Database and filesystem changes are not described as one atomic transaction. Files follow the existing lifecycle rules. A critical deletion failure before the DB commit fails the operation without applying DB changes. A deletion failure after the DB commit may leave a disposable orphan file for reconcile/cleanup; it must not leave partially applied DB state.
- A missing podcast returns the standard `PODCAST_NOT_FOUND` error.
- Backend delivery includes updating parent `docs/product-decisions.md`; Android delivery includes keeping this contract and its DTO/error handling in sync.

Success response:

```json
{
  "success": true,
  "markedEpisodes": 12
}
```

Resolution evidence: Android now calls the backend-owned endpoint exactly once, treats `markedEpisodes: 0` as idempotent success, invalidates playback only after success, reloads authoritative state, and restores only the affected optimistic podcast on failure. The concurrent per-episode PATCH implementation was removed. Unit tests cover success count, zero-count repeat, `PODCAST_NOT_FOUND`, transport failure, and scoped rollback. A Pixel 9 real-backend fixture verified listened state, playlist removal, active-playback clearing, repeat safety, and cleanup without changing the existing Planet Money state.

### A-07 — P2 — OPML import reads the whole selected file into memory without a limit

Status: resolved in Stage 3.1; final Stage 3 verification pending.

Reproduction:

1. Select a very large document from the system picker.
2. Android calls `readBytes()` before upload.

Expected: enforce an agreed maximum size and stream the request body, with a clear validation error.

Actual: the complete provider stream is allocated as one byte array. A large or hostile document can cause excessive memory use or an out-of-memory crash.

Affected code: `AddPodcastViewModel.kt:62-98`.

Missing test: maximum accepted size, over-limit rejection, provider read failure, cancellation, and streaming upload.

Confirmed decision: Android and backend must both enforce a 5 MB (`5,000,000` bytes) OPML limit. Backend rejects an oversized multipart request before parsing it and returns HTTP `413 Request Entity Too Large` with the standard error body:

```json
{
  "error": {
    "code": "OPML_TOO_LARGE",
    "message": "OPML file is too large"
  }
}
```

Resolution evidence: Android now uses a streaming multipart request body. A known provider size above the limit is rejected before opening the stream; unknown or incorrect metadata is guarded by a byte counter during upload. Exact-limit, both over-limit paths, provider-open failure, and provider-read failure are unit-covered. The user receives a specific 5 MB validation message or a selected-file read error instead of a misleading backend-connectivity error.

### A-08 — P2 — No CI enforces the existing test gate

Status: resolved in Stage 3.2; first upstream workflow run pending.

Reproduction: push a commit that fails unit tests, lint, or debug assembly.

Expected: the shared repository blocks or visibly fails the change before APK handoff.

Actual: all checks depend on a developer remembering to run local commands; no CI workflow exists.

Affected area: repository delivery process.

Missing gate: unit tests, lint, debug app/test APK assembly on every change; connected tests at the agreed cadence.

Resolution evidence: `.github/workflows/android.yml` runs the complete local gate on every push and pull request and uploads reports even after failures. A dependent Android 14 emulator job runs the connected suite for pull requests and manual dispatch, keeping routine branch pushes fast while enforcing device coverage before merge. The first remote GitHub Actions execution is required before the CI row can be marked fully verified.

### A-09 — P3 — Dead Play branch remains in the Subscriptions action handler

Expected: the Subscriptions action surface reflects the confirmed rule that Play exists only on Home.

Actual: the non-playback menu does not expose Play, which is correct, but `SubscriptionsScreen` still contains an unreachable `EpisodeRowAction.Play -> Unit` branch. This is misleading maintenance code, not a current user-visible defect.

Affected code: `SubscriptionsScreen.kt:279`.

Missing test: none required beyond removing the unreachable action or separating menu action types.

### A-10 — P3 — Unused Room stack and template tests add noise

Expected: dependencies and tests represent actual application behavior.

Actual: Room runtime/compiler are configured without any Room database usage, and generated example unit/instrumentation tests remain in the suite.

Affected code: `app/build.gradle.kts`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`.

Missing verification: dependency cleanup build and updated test count.

## Release-only blockers already assigned to later stages

These are not Stage 2 functional fixes but must block a production APK:

- The only current application ID is the test ID and backend is the test LAN address.
- The current `release` build type uses the debug signing key and has optimization disabled.
- Cleartext traffic is enabled for the entire application rather than a test-only network policy.
- Production port `5050`, release signing, upgrade install, logging policy, and production backup/network rules are not configured.

These items remain Stage 6 work unless the product owner changes their priority.

## Execution status

1. Stage 2A / A-01 refresh-job correctness: completed.
2. Stage 2B / A-02 auth/offline state and logout correctness: completed.
3. Stage 2C / A-03 session backup protection: completed.
4. Stage 2D / A-04 playback synchronization and durable retry: completed.
5. Stage 2E / A-05 and A-06 settings/mark-all consistency: completed.
6. Stage 3 remains: A-07 input hardening, A-08 CI, and the complete critical regression matrix.
7. Opportunistic cleanup remains: A-09 and A-10 only when adjacent code is changed.

## Accepted implementation decisions

The product owner accepted the prioritized backlog and confirmed:

1. An unavailable backend uses a dedicated state with a `Retry` action.
2. Mark all listened uses the accepted backend-owned contract above, with an atomic DB transaction and explicitly non-atomic filesystem cleanup.
3. OPML import is limited to 5 MB on both Android and backend, with the accepted `OPML_TOO_LARGE` error contract.

Stage 1 and Stage 2 are complete. Stage 3 is the next planned stage.
