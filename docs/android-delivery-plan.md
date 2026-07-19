# mpod Android — delivery plan and quality baseline

Last updated: 2026-07-19 (scenario-driven functional process introduced)

Current Android baseline: `1.0.11 (12)`, Stage 3 completed; Stage 4 is functional readiness

## Purpose

This is the living delivery document for the native Android application. It is the only Android-specific source of truth for scope, decisions, implementation status, verification, acceptance, defects, and release readiness.

The complete functional scenario inventory and its verification status live in
[`docs/android-user-scenarios.md`](android-user-scenarios.md). This delivery plan defines sequencing and policy; the scenario map defines the complete user paths and expected results. A stage or feature status must not contradict a failed or open scenario row.

The document must be updated in the same commit as any change that alters a tracked status. A feature is not considered complete merely because its UI exists.

## Sources of truth

New decisions made in chat must be incorporated here in the same documentation stage. Use sources in this order:

1. Explicit decisions confirmed by the product owner in the Android project chat.
2. This living Android plan after it has been updated with those decisions.
3. The Android Figma screens and mobile components.
4. Parent-project product and API documentation under `/Users/cross/Documents/mpod` for shared product/backend behavior.
5. The actual backend contract and behavior.

If the sources disagree or required information is absent, stop and ask. Do not invent product behavior.

## Confirmed Android scope

| Area | Confirmed decision |
|---|---|
| Distribution | APK, outside Google Play for the current scope |
| Minimum OS | Android 14+ |
| UI language | English |
| Test backend | Debug/test build: hardcoded `192.168.0.222:5051` |
| Production backend | Release build: hardcoded `192.168.0.222:5050` |
| Test application ID | Debug/test build: `com.prod.mpod.test` |
| Production application ID | Release build: `com.prod.mpod` |
| Backend storage | Downloads remain on the mpod server, as in the web application |
| Default authenticated route | Subscriptions |
| Primary navigation | Home, Subscriptions, Settings, Add podcast |
| Theme | Follow the system by default; Settings exposes the approved Light/Dark switch behavior |
| Active episode | Stored by the backend and restored without autoplay |
| Mark all listened | Executes immediately, without a confirmation dialog |
| Subscription episode playback | No separate Play action exists for episodes outside the playlist |
| Design | Android screens and mobile components in the mpod Figma file |

Delivery order decision confirmed on 2026-07-16: a complete, reliable working application takes priority over visual polish and extended accessibility work. Figma fine-tuning, exhaustive font/display scaling, TalkBack review, and performance profiling do not block the working test build unless they make a core action unreadable, unreachable, or unusable.

Figma references:

- Android screens: <https://www.figma.com/design/3CmMv8wYlyNz9qDDdOd2Ka/mpod?node-id=880-4200>
- Mobile components: <https://www.figma.com/design/3CmMv8wYlyNz9qDDdOd2Ka/mpod?node-id=700-4536>
- Podcast fallback artwork: <https://www.figma.com/design/3CmMv8wYlyNz9qDDdOd2Ka/mpod?node-id=757-6696>

## Status vocabulary

| Status | Meaning |
|---|---|
| Not started | No production implementation exists |
| Implemented | Production code exists, but required verification is incomplete |
| Verified | Required automated and manual checks passed on the test backend |
| Accepted | Product owner checked the build and accepted the behavior |
| Blocked | A named external dependency or unanswered product decision prevents progress |

Statuses are evidence-based. `Implemented` must never be used as a synonym for `Verified` or `Accepted`.

## Current product baseline

The product owner accepted `1.0.4 (5)` as the current test baseline on 2026-07-15. This accepts the starting point for further work; it does not make the build a release candidate or automatically mark every individual feature as release-verified.

| Area | Current status | Existing evidence | Remaining work before release |
|---|---|---|---|
| Startup/session restoration | Verified | Unit coverage for 2xx/401/5xx/transport outcomes; Compose Retry test; valid-session offline cold-start and recovery; minified production release resolved a real `5050` session response to Login on Pixel 9 | Lifecycle, expired-session, slow-network, and process-recreation coverage |
| Session backup/transfer | Verified | CookiePrefs and pending playback mutations excluded from legacy backup, cloud backup, and device transfer; two connected resource-contract tests; cleared-data launch has no CookiePrefs or restored session | Final release backup/restore smoke check on the production variant |
| Initial setup and login | Verified | Real backend login/session/error/restart matrix plus setup API-contract and Compose coverage | Final release-candidate smoke check |
| Bottom navigation | Verified | Manual emulator/phone checks | Back-stack and process-recreation tests |
| Home queue | Implemented | Real backend flow; basic UI test | Complete interaction, error, empty-state, and lifecycle coverage |
| Playback service | Verified | Queue/retry automation plus real local audio play, pause, progress, tap/drag scrubbing, button seeks, natural completion, auto-next, and paused completion-window reconciliation on Pixel 9 | Audio focus/route/network and service/process reliability matrix in Stage 5 |
| Active episode restore | Verified | Backend integration, queue reconciliation tests, and real force-stop restore at saved position without autoplay | Physical-device restart smoke check in Stage 6 |
| Queue reorder | Verified | Pure reorder tests plus real long-press drag with authoritative backend order and offline rollback on Pixel 9 | Background/process lifecycle behavior in Stage 5 |
| Playback speed | Verified | All supported labels unit-tested; real 0.5x/1x/2x backend restore plus earlier offline/process-stop/reconnect restoration | Cross-device conflict behavior in Stage 5 |
| Subscriptions carousel/filter | Verified | Compose UI tests and real backend checks | Rotation/process-recreation behavior |
| Refresh one/all | Verified | Refresh-all completion plus per-podcast success, feed failure, visible error, and Try again recovery checked on the real test backend; polling unit and UI-state tests | Long-running/stuck-job lifecycle policy in Stage 5 |
| Podcast artwork/fallback | Verified | Exact web/Figma fallback checksum, real missing-image fallback, and successful Android decode/render from a fixture image URL | Cache and lifecycle behavior in Stage 5 |
| Episode playlist actions | Verified | API/Compose automation plus real add/remove, listened/unlistened, backend cleanup, and failed-add rollback on a temporary podcast | Background/process lifecycle behavior in Stage 5 |
| Show notes | Verified | Backend contract tests, UI test, real Planet Money notes and scrolling | Link handling decision and accessibility review |
| Mark all listened | Verified | Single backend-owned atomic endpoint; unit/UI contract coverage; real Pixel 9 one-episode fixture verified listened/playlist/active effects and idempotent repeat | Release-candidate smoke check |
| Episode download | Implemented | Backend action and UI states exist | Real download success, failure, cancellation, file lifecycle, and playback-from-download matrix |
| Podcast unsubscribe/undo | Verified | Countdown/unit coverage plus real Undo preserving backend state and final 15-second commit removing only the selected podcast | Background/process lifecycle behavior in Stage 5 |
| Add RSS feed | Verified | API/Compose automation plus real valid, duplicate, invalid-scheme, and unreachable-feed checks | Slow-response lifecycle behavior in Stage 5 |
| OPML import/export | Implemented | Android document intents and backend calls exist; earlier manual checks | Repeatable fixture-based instrumentation and permission/error cases |
| Daily refresh time | Verified | Material TimePicker unit/UI/manual checks; confirmed save survives failed status reload | Full API fixture integration and 12/24-hour device matrix |
| SOCKS5 switch/status | Implemented | Real backend status displayed; confirmed switch value survives failed status reload | Full failure/running/off API fixture matrix and acceptance |
| Theme | Verified | Unit/UI tests and physical-phone checks | Screen-by-screen contrast/accessibility audit |
| Logout | Verified | Backend response controls launch state; real logout cleared persisted cookies and a server-invalidated stored cookie resolved to Login | Final release-candidate smoke check |
| Empty/loading/error states | Implemented | States exist across main screens | Complete screenshot and interaction matrix |

## Test baseline

Current automated suite:

- 92 local unit tests.
- 39 connected Android/Compose UI/configuration tests.
- Debug and release Android lint.
- Debug app, Android-test APK, and minified release APK assembly.

Current verified command set:

```bash
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest assembleRelease
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

Production release regression evidence from 2026-07-19: R8 had removed Gson-reflected API model fields, so `GET /api/auth/session` returned HTTP 200 but conversion failed and Android falsely displayed `mpod is not reachable`. The API model package is now retained for reflection. The installed minified APK used package `com.prod.mpod`, requested `http://192.168.0.222:5050/api/auth/session`, received HTTP 200, and resolved the unauthenticated response to Login; the crash buffer was empty. Debug/test remains isolated as `com.prod.mpod.test` on port `5051`, protected by a variant contract unit test.

### Important coverage gaps

- CI enforces unit tests, lint, and debug app/test APK assembly on every push and pull request. Connected Android 14 tests run for pull requests and manual workflow dispatch. The first upstream push run completed successfully on commit `1da88a1` (GitHub Actions run `29487361492`).
- ViewModels and Retrofit failure/retry paths have little direct automated coverage.
- PlaybackService has durable-sync automation and real completion/auto-next evidence, but still lacks the Stage 5 Media3 reliability matrix for audio focus, route changes, audio-network loss, and service/process termination.
- OPML, downloads, and Settings backend saves still lack complete end-to-end evidence; their focused checks remain in Stage 4.
- Process death, rotation, background/foreground, expired session, slow network, and timeout scenarios are not systematically covered. The critical valid-session offline cold-start and Retry recovery path has targeted unit, Compose, and Pixel 9 evidence.
- Accessibility, font scaling, display scaling, and 12/24-hour locale matrices are incomplete.
- The physical-phone pass is manual and does not yet use a written repeatable release checklist.

## Risk-based regression policy

Quality checks should be proportional to regression risk so accepted work is not repeatedly re-tested without reason.

- Always run the complete unit, lint, and build suite for code changes.
- Run connected UI tests affected by the change; run the complete connected suite before an APK handoff, stage acceptance, or release candidate.
- Deeply re-test every changed flow and its direct dependencies, including backend state transitions.
- Always re-test P0/P1 paths affected by shared navigation, authentication, networking, persistence, playback, theme, or reusable UI components.
- Keep already accepted, unchanged, low-risk flows on a short smoke checklist instead of repeating their full manual matrix.
- Re-open a previously accepted flow only when a dependency changed, an automated test failed, a regression was reported, or release-candidate validation requires it.
- Use the emulator for repeatable UI and lifecycle checks. Use the physical phone only for device-sensitive behavior, final stage acceptance, and release checks.
- Record reused evidence and the reason a full manual re-test was not required; never claim a scenario was re-tested when it was only inherited from the accepted baseline.

## Functional definition of done

A feature may move to `Verified` only when all applicable items pass:

1. Expected behavior is documented or explicitly confirmed.
2. The complete user action reaches the expected authoritative backend/playback state, not merely a callback or mocked UI state.
3. Success, loading, empty, disabled, retry, and failure states required by the flow are usable and truthful.
4. Unit tests cover business/state transformations and Compose/UI tests cover user-visible dispatch and reconciliation.
5. Real test-backend integration is checked when the feature uses the API.
6. The complete local unit, lint, assemble, and connected-test suite passes.
7. The changed flow is checked end-to-end on the Pixel 9 emulator.
8. Device-sensitive behavior is checked on the physical Android phone before acceptance of the working test build.
9. Version is bumped for an installable handoff build; only scoped files are committed and pushed.
10. The product owner receives the version, commit, checks actually performed, and known limitations, then accepts or rejects the stage.

Visual similarity alone, a Retrofit contract test alone, or a button callback test alone is not sufficient evidence that a feature works.

## Delivery stages

Each stage ends with: verification, documentation update, version bump when an APK is handed off, one scoped commit, push, report, and a stop for product-owner approval.

### Stage 0 — Living plan and baseline

Goal: establish this document and stop treating implementation presence as completion.

Deliverables:

- Product/status/test matrices.
- Definition of done.
- Prioritized stage sequence.
- Explicit documentation conflicts and unanswered decisions.

Exit criterion: completed — the product owner accepted the plan structure and `1.0.4 (5)` baseline on 2026-07-15.

### Stage 1 — Full audit and defect backlog

Goal: produce an evidence-backed audit of the current APK without implementing unrelated fixes.

Scope:

- Compare every Android screen and state with Figma.
- Trace every visible action to ViewModel/API/service behavior.
- Check parent API documentation against Android DTOs and calls.
- Apply the risk-based regression policy: deep-check unknown, changed, shared, and P0/P1 paths; use smoke checks for accepted unchanged flows.
- Classify findings as P0–P3 with reproduction, expected result, actual result, affected code, and missing test.
- Mark the feature matrix with verified evidence rather than assumptions.

Audit result: completed and accepted by the product owner on 2026-07-15 and recorded in
[`docs/android-stage-1-audit.md`](android-stage-1-audit.md). The accepted backlog has
no P0 items, four P1 items, four P2 items, and two P3 maintenance items.

Verification evidence:

- Unit tests, lint, debug APK, and Android-test APK: passed.
- Connected Pixel 9 suite: 13/13 passed.
- Main light-theme screens checked on the emulator against Figma.
- Test APK `1.0.4 (5)` and system dark mode confirmed on the physical phone; a new interactive phone pass was blocked by the device lock and was not claimed as completed.

Confirmed implementation decisions:

- Backend unavailable: dedicated state with a `Retry` action.
- Playback writes: Android durably retains the latest active episode and speed plus one coalesced update per episode. Completion cannot be replaced by later progress; `didSeek` remains set until acknowledgement. Network errors, HTTP 401/408/409/425/429, and 5xx retry with a 1/2/5/15/30-second capped backoff; semantic 4xx failures are discarded. Pending mutations survive process restart, are excluded from Android backup/device transfer, and are removed only after 2xx or a permanent client error.
- Mark all listened: backend-owned `POST /api/podcasts/{podcastId}/mark-all-listened`; atomic DB updates, idempotent `markedEpisodes` response, and explicitly separate filesystem cleanup/reconcile semantics.
- OPML import: 5 MB (`5,000,000` bytes) maximum enforced by both Android and backend; oversized requests return HTTP 413 with `OPML_TOO_LARGE`.

Exit criterion: completed — the product owner accepted the prioritized backlog and the required implementation decisions on 2026-07-15.

### Stage 2 — Critical functional completeness

Goal: eliminate agreed P0/P1 functional defects.

Expected focus, subject to the Stage 1 backlog:

- Playback lifecycle and backend synchronization.
- Destructive action and download/file-lifecycle correctness.
- Auth/session failure recovery.
- Data-contract mismatches and state reconciliation.

Exit criterion: no open P0/P1 functional defects in the agreed MVP scope.

Progress:

- Stage 2.1 completed in test build `1.0.5 (6)`: A-01 Refresh all now polls `GET /api/jobs/status` every three seconds, keeps the refreshing state active through the backend job, reloads subscriptions only after completion, and displays backend `lastError` on failure. Temporary status-request failures are retried, matching the established web behavior.
- Evidence: 47 unit tests, lint/build gate, 13/13 connected Pixel 9 tests, real test-backend `running → completed` check, empty crash log, and installation on the physical phone.
- Stage 2.2 completed in test build `1.0.6 (7)`: A-02 no longer maps an unavailable backend to Login. Cold start and failed logout use a dedicated web-aligned `mpod is not reachable` state with `Retry`; HTTP 401 remains the explicit unauthenticated outcome.
- Evidence: 51 unit tests, lint/build gate, 14/14 connected Pixel 9 tests, package-isolated offline cold start with a valid stored session, successful Retry restoration directly to Subscriptions, and an empty AndroidRuntime crash log.
- Stage 2.3 completed in test build `1.0.7 (8)`: A-03 keeps backup enabled for non-sensitive preferences while excluding `CookiePrefs.xml` from legacy backup, cloud backup, and device transfer.
- Evidence: 51 unit tests, lint/build gate, 16/16 connected Pixel 9 tests including direct parsing of both packaged backup-rule resources, cleared app-data launch to Login with no `CookiePrefs.xml`, and an empty crash buffer. Physical-phone validation remains intentionally deferred to the final stage by product-owner decision.
- Stage 2.4 completed in test build `1.0.8 (9)`: A-04 routes active episode, progress/seek/completion, and playback-speed writes through a persistent retry manager. Pending state is coalesced without losing completion or seek semantics, replayed after process restart, reconciled before queue restoration, and excluded from backup/device transfer.
- Evidence: 59 unit tests, lint/build gate, 17/17 connected Pixel 9 tests, plus real test-backend offline/process-stop/reconnect checks for seek progress, speed, and active playback. Each mutation existed on disk before process stop, cleared after successful retry, restored the confirmed UI state without autoplay, and produced no crash. Completion retry/fallback is unit-covered; the expanded Media3 completion/auto-next device matrix remains Stage 3 work.
- Stage 2.5 / A-05 completed in test build `1.0.9 (10)`: Settings retains the backend-confirmed refresh time or proxy value when the follow-up Settings/Scheduler/Proxy reload fails, clears the saving state, and reports that the save succeeded but status refresh failed. Follow-up status endpoints now require successful HTTP responses instead of silently treating failures as empty status.
- Evidence: 61 unit tests including confirmed-save/reload-failure and successful-reload paths, lint/build gate, and 17/17 connected Pixel 9 regression tests.
- Stage 2.6 / A-06 completed in test build `1.0.10 (11)`: Mark all listened now sends one `POST /api/podcasts/{podcastId}/mark-all-listened`, accepts the backend-owned `markedEpisodes` result, invalidates playback only after success, reloads authoritative subscriptions, and restores only the target optimistic podcast on failure. The former concurrent per-episode PATCH batching was removed.
- Evidence: 66 unit tests covering success, idempotent zero, backend error, transport failure, and scoped rollback; lint/build gate; 17/17 connected Pixel 9 tests; and a real temporary one-episode RSS fixture. Android marked the episode listened, backend removed it from playlist and cleared it from active playback, a repeated request returned `markedEpisodes: 0`, Planet Money state remained intact, and the fixture was removed after verification.

Stage 2 exit criterion: completed on 2026-07-16. All audited Stage 2 defects A-01 through A-06 are resolved and verified; no P0/P1 functional defect from the agreed Stage 1 backlog remains open. Physical-phone final validation remains deferred to the final release stage by product-owner decision.

### Stage 3 — Automated regression coverage

Goal: make critical user flows repeatable and prevent recurrence of accepted defects.

Minimum critical flows:

- Setup, login, session restore, logout.
- Add RSS, duplicate/invalid RSS, OPML import/export.
- Refresh one/all, filter subscriptions, artwork fallback.
- Add/remove playlist, reorder, mark listened/unlistened, mark all listened.
- Show notes, download success/failure.
- Active episode restore, play/pause, seek, speed, progress sync, completion/next episode.
- Settings save/reload, proxy states, themes.

Also add CI for unit tests, lint, and debug assembly. Connected tests may use a separate emulator job if runtime constraints require it.

Progress:

- Stage 3.1 / A-07 completed: OPML imports no longer allocate the selected document with `readBytes()`. Android rejects known files over `5,000,000` bytes before the request, enforces the same limit while streaming when provider metadata is absent or incorrect, and distinguishes size and provider-read failures in the UI. Unit coverage includes the exact limit, known and streaming over-limit paths, provider-open failure, and provider-read failure; the final Stage 3 gate passes.
- Stage 3.2 / A-08 completed: GitHub Actions runs unit tests, Android lint, debug APK assembly, and Android-test APK assembly on every push and pull request. Pull requests and manual dispatch additionally run the connected suite on an Android 14 Google APIs emulator. Reports are retained as workflow artifacts. The first upstream push run (`29487361492`) completed successfully on commit `1da88a1`; its connected job was correctly skipped by the push cadence.
- Stage 3.3 completed: repeatable API-contract coverage now pins session/login/register/logout, RSS add, streaming OPML import/export, Settings PATCH fields, proxy status, and scheduler status endpoints. Connected Compose coverage verifies login/setup credential dispatch, authentication loading/error UI, RSS validation and trimming, OPML mode/error/loading actions, Settings refresh save, proxy, export, logout, loading, and backend-error states. The Pixel 9 connected suite passes 27/27 tests.
- Stage 3.4 completed: API-contract tests pin podcast refresh/unsubscribe/mark-all, playlist read/add/remove/reorder, and episode read/listened/download requests. Connected UI coverage now dispatches Home playback/seek/speed/notes and playlist removal; Subscriptions add/remove playlist, listened/unlistened, download, unsubscribe/Undo, load retry, download-error dismissal, and both empty-library add paths. The Pixel 9 connected suite passes 36/36 tests. Backend filesystem download lifecycle and long-press queue dragging remain explicit manual release checks.
- Stage 3.5 completed: playback contract coverage pins queue load, active episode, progress, seek, completion, and speed writes. Unit coverage now includes every supported playback speed, invalid-speed rejection, initial and changed queue reconciliation, completion fallback, missing fallback behavior, delayed-completion auto-next, and protection against a late retry hijacking newer playback. Real audio focus, noisy-route handling, Bluetooth/headset interruption, audio-network loss, and OS service/process termination remain device-only release checks; the final Stage 3 gate passes.

Critical regression matrix:

| Flow | Automated evidence | Required manual release check |
|---|---|---|
| Setup, login, session restore, logout | Auth API contract, launch-state unit matrix, Login/Setup/Retry Compose tests | Expired-cookie recovery and real logout success/failure across process restart |
| RSS add and validation | URL validation/dispatch UI tests and exact create-podcast request contract | Real valid, duplicate, malformed, unreachable, and slow feed fixture |
| OPML import/export | Exact-limit/over-limit/provider-read unit tests, streaming multipart contract, modal and Settings UI tests | Android document-picker cancel/permission/reopen, real import result, exported file contents |
| Refresh, filtering, artwork | Refresh job unit matrix, visibility and fallback UI tests, refresh API contracts | Failed/stuck backend job and successful artwork loading/cache across multiple hosts |
| Playlist and destructive actions | Add/remove/reorder/listened/mark-all API and state tests plus Home/Subscriptions UI dispatch | Background/process lifecycle during reorder, mutations, and unsubscribe Undo |
| Show notes and download | Show-notes data/UI tests, download endpoint and failure-banner UI tests | Real download success/failure/cancel, server file cleanup, listened cleanup, playback from downloaded file |
| Playback and synchronization | Queue/active/progress/seek/completion/speed contracts, retry persistence/coalescing, completion/next decisions, Home control UI | Real audio completion/auto-next, focus/noisy route, Bluetooth/headset, audio-network loss, service/process kill |
| Settings, proxy and theme | Save/reload unit tests, Settings contracts, proxy/theme/time UI tests | 12/24-hour picker, proxy off/running/error against backend, final light/dark screen audit |

The manual column is the written release checklist for scenarios that depend on Android system services, external document providers, backend filesystem state, real audio delivery, or timed lifecycle behavior. These checks are not claimed as automated and remain mandatory in their assigned later stage.

- Stage 3.6 completed: the complete local gate and Pixel 9 connected suite pass after removing the unused Room stack and generated template tests (A-10). Test build `1.0.11 (12)` contains 85 product unit tests and 35 connected product tests. The first upstream CI quality gate also passes.

Stage 3 exit criterion: completed on 2026-07-16. Automated coverage protects the known contracts and state transformations, but it does not replace the end-to-end functional checks assigned to Stage 4.

### Stage 4 — Complete the working application

Goal: prove and fix every core user scenario end-to-end against the test backend before spending more time on polish. The authoritative inventory is now [`docs/android-user-scenarios.md`](android-user-scenarios.md); the numbered list below is a grouping summary, not a substitute for the individual scenarios.

Required scope:

1. **Session and startup** — setup, login, valid-session restore, expired session, logout success/failure, unavailable backend, Retry, and process restart.
2. **Subscriptions** — real RSS add including duplicate/invalid/unreachable feeds; refresh one/all through backend completion/failure; show-all/unlistened; artwork success/fallback; unsubscribe plus Undo and final backend state.
3. **Episodes and playlist** — add/remove playlist, reorder with the real gesture and backend result, mark listened/unlistened, mark all listened including playlist/active cleanup, show notes, and truthful error rollback.
4. **Playback** — active episode restore without autoplay, play/pause, seek, speed, progress persistence, completion and automatic next episode, queue reconciliation, and recovery after a failed write.
5. **Downloads** — real download success/failure, server file state, playback from the downloaded episode, and cleanup after listened/removal lifecycle actions.
6. **Import/export** — real OPML import/export through Android document providers, exact exported contents, cancel/error/oversize cases.
7. **Settings** — refresh time save, proxy on/off/running/error, theme persistence, and logout using authoritative backend results.
8. **Application states** — usable loading, empty, disabled, retry, and backend-error behavior for every core screen. A visual defect is fixed here only when it prevents or misrepresents a core action.

Progress:

- Stage 4.1 session/startup completed on 2026-07-16. The Pixel 9 was exercised against the real test backend through valid-session cold restore, authoritative logout with an empty persisted cookie store, rejected credentials with the backend error, successful login, process restart, offline cold start with the unavailable state, network restoration plus Retry directly to Subscriptions, and a server-invalidated previously stored cookie resolving to Login rather than the offline state. The test session was restored after the check and the crash buffer was empty.
- Initial setup was not destructively recreated on the shared test backend. The backend's isolated router tests cover `setupRequired → register → authenticated` and rejection of a second setup; Android API-contract and Compose tests cover the corresponding request and credential dispatch.
- The first full connected gate exposed seven stale selectors left behind by the earlier accessibility-label change. They were updated to target the current descriptive player and episode actions. The final gate passes with 88 unit tests, lint, both debug APK assemblies, and 38/38 connected tests.
- Stage 4.2 subscriptions/RSS completed on 2026-07-16 against the real test backend and a temporary local RSS fixture. Android accepted a valid feed, rejected the duplicate, rejected an `ftp` URL before dispatch, and surfaced `Failed to fetch feed` for an unreachable HTTP feed. Refresh-all completed; individual refresh was verified through success, a deliberately unavailable feed, visible podcast/banner error, and successful `Try again` recovery. Show-all/unlistened toggled correctly. A missing image rendered the approved fallback, while a valid local PNG reached the ordinary loaded-cover state.
- Unsubscribe was verified in both authoritative outcomes: Undo inside the countdown preserved the fixture in UI and backend, while a second attempt without Undo removed only that fixture after the 15-second window. All temporary podcasts and the fixture server were removed after the check. No application defect or production-code change was required; the existing API-contract, ViewModel, and connected UI coverage remains the repeatable regression evidence for these paths.
- Stage 4.3 episodes/playlist completed on 2026-07-16 with a temporary three-episode podcast while preserving the user's Planet Money state. Show notes rendered the RSS description. UI actions added three episodes and backend returned the exact queue order; listened/unlistened transitions were authoritative, marking listened removed the affected playlist row, and returning it to unlistened did not silently re-add it. Home removal deleted only the selected row. A real long-press drag changed backend order, while the same drag offline restored the prior UI order and showed `Could not reorder playlist.` An offline Add to playlist likewise restored `3 / 0 episodes`, showed the scoped error, and left backend unchanged.
- Mark all listened set all three fixture episodes listened, removed the remaining fixture playlist row, and left the unrelated active episode and original playlist `[9, 1]` intact. The fixture podcast and server were removed afterward. No application-code change was required, so the accepted 88-unit/lint/assemble/38-connected gate from Stage 4.1 is reused under the risk-based regression policy rather than rerun for a documentation-only commit.
- Stage 4.4 playback completed on 2026-07-16 with authenticated local MP3 fixtures. A force-stop restored backend active episode A at `0:00` with `Play`, proving no autoplay. Play/Pause states and progress were real; a 30-second episode paused at 3 seconds persisted `positionSeconds: 3` while remaining active and unlistened. Rewind updated the local player to zero while the backend correctly retained 3 under its documented rule that backward changes under 30 seconds are ignored. Forward 15 reached the exact completion boundary, removed the finished episode, selected the eligible backend fallback at zero, and did not autoplay. Speed restoration was exercised at 0.5x, 1x, and the original 2x.
- Natural completion of a 4-second A transitioned automatically to the queued 30-second B, which was observed playing at `0:02 / 0:28` with `Pause`. This exposed and fixed a real protocol mismatch: a pause or seek inside the backend's 15-second completion window removed/cleared the episode server-side while Android kept showing it. PlaybackService now recognizes the same boundary and, after a successful paused update, reconciles to the backend/local next target without autoplay. Three unit tests protect the exact boundary, paused reconciliation, and non-hijacking of continuing/newer playback.
- The durable failed-write/process-stop recovery evidence from Stage 2.4 remains applicable because this change did not alter the persistent retry store or transport; its retry/coalescing tests passed in the full gate. All temporary podcasts/media were removed and backend state was restored to active `9`, playlist `[9, 1]`, speed `Speed 2x`. Final evidence: 91 unit tests, lint, both debug APK assemblies, and 38/38 connected Pixel 9 tests.
- Stage 4.4 was reopened after the product owner reported that the visible progress track did not seek. The track had only display semantics and no gesture or Media3 absolute-seek path; the earlier verification covered the ±10/15-second buttons but incorrectly described that as complete seek coverage. The unchanged Figma track now supports both tap and horizontal drag, exposes an adjustable progress semantic action, and dispatches an absolute position to Media3. A Compose test performs a real tap at 75% and a real drag from 20% to 60%. On the Pixel 9, an actual Planet Money episode moved from `0:52` to `16:57` by tap and then to `11:17` by drag; the backend authoritatively stored `positionSeconds: 677`. The test state was restored to `0:51`/`positionSeconds: 51`, the crash buffer was empty, and the revised full gate passes with 91 unit tests, lint, both debug APK assemblies, and 39/39 connected tests.

Process correction recorded on 2026-07-19: earlier work grouped checks by technical feature and sometimes treated callback, contract, or partial manual evidence as proof of a complete user path. From this point, existing results are baseline evidence only. Each scenario is traced from the real gesture through visible state to authoritative backend/Media3 state and applicable failure recovery before it can be marked `Verified`.

For each scenario wave: match reusable evidence, execute the missing real path, record the result, fix failures in scenario-scoped commits, and rerun the scenario plus affected dependencies.

Exit criterion: every visible core action has repeatable evidence of the expected final state; the complete regression gate passes; no open P0/P1 functional defect remains.

### Stage 5 — Mobile reliability gate

Goal: ensure the functionally complete application keeps working under normal Android lifecycle and connectivity changes.

Required scope:

- Rotation and process recreation during auth, playlist mutations, downloads, Settings saves, and playback.
- Background/foreground during unsubscribe Undo, document-provider flows, downloads, and playback.
- Offline, slow response, timeout, retry, backend restart, and expired session.
- Audio focus, noisy route, Bluetooth/headset changes, audio-network loss, service/process termination, completion, and auto-next.
- Long podcast, episode, and queue lists only to the point required to exclude broken actions, crashes, or unusable stalls.

Exit criterion: core state is not corrupted or falsely reported, interrupted actions recover predictably, and no P0/P1 reliability defect remains.

### Stage 6 — Working test build acceptance

Goal: hand the product owner one current test APK that is demonstrably usable as the complete MVP.

Required scope:

- Bump the test APK version and install it on the physical Android 14+ phone.
- Run the written core-flow checklist on the phone against port `5051`.
- Check clean install and upgrade over the previous test APK without losing valid local state unexpectedly.
- Record APK checksum, version, commit, backend environment, checks performed, and every known limitation.

Exit criterion: explicit product-owner acceptance of the test build as a working application.

## Deferred until after the working test build

These items remain useful, but are not allowed to displace functional work:

- Pixel-level Figma parity and cosmetic animation tuning.
- Exhaustive TalkBack, font/display scaling, and non-blocking contrast polish.
- Detailed CPU, memory, frame-time, and startup profiling unless a real functional slowdown or crash is observed.
- Final production signing credentials, TLS/network-security policy, backup/logging policy, and release packaging.

After Stage 6 acceptance, these will be planned from the actual remaining defects instead of being treated as prerequisites for a working app.

## Priorities

| Priority | Definition |
|---|---|
| P0 | Data loss, security/auth bypass, unusable startup, or playback/app-wide crash with no workaround |
| P1 | Core MVP flow is broken or gives an incorrect authoritative state |
| P2 | Important defect with a reasonable workaround, major visual mismatch, or missing non-critical coverage |
| P3 | Polish, minor inconsistency, low-risk technical debt, or deferred enhancement |

## Deferred product-owner input

The scenario inventory contains five focused open questions covering OPML partial results, show-notes links, media notification controls, interrupted downloads, and Settings load Retry. They do not invalidate completed baseline work, but their named rows cannot be accepted until decided. Release signing details remain intentionally deferred until production packaging begins after acceptance of the working test build.

## Stage report template

Every completed stage report must include:

- Scope completed.
- APK version, when installed.
- Commit and pushed branch.
- Automated checks and their counts.
- Emulator/backend/physical-device checks actually performed.
- Known limitations or deferred findings.
- Exact acceptance request for the product owner.
