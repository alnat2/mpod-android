# mpod Android — delivery plan and quality baseline

Last updated: 2026-07-16 (Stage 3 in progress)

Current Android baseline: `1.0.10 (11)`, Stage 3 in progress

## Purpose

This is the living delivery document for the native Android application. It is the only Android-specific source of truth for scope, decisions, implementation status, verification, acceptance, defects, and release readiness.

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
| Test backend | Hardcoded `192.168.0.222:5051` |
| Production backend | Port `5050`; production build is deferred until a pre-release exists |
| Test application ID | `com.prod.mpod.test` |
| Production application ID | `com.prod.mpod` |
| Backend storage | Downloads remain on the mpod server, as in the web application |
| Default authenticated route | Subscriptions |
| Primary navigation | Home, Subscriptions, Settings, Add podcast |
| Theme | Follow the system by default; Settings exposes the approved Light/Dark switch behavior |
| Active episode | Stored by the backend and restored without autoplay |
| Mark all listened | Executes immediately, without a confirmation dialog |
| Subscription episode playback | No separate Play action exists for episodes outside the playlist |
| Design | Android screens and mobile components in the mpod Figma file |

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
| Startup/session restoration | Verified | Unit coverage for 2xx/401/5xx/transport outcomes; Compose Retry test; valid-session offline cold-start and recovery exercised on Pixel 9 | Lifecycle, expired-session, slow-network, and process-recreation coverage |
| Session backup/transfer | Verified | CookiePrefs and pending playback mutations excluded from legacy backup, cloud backup, and device transfer; two connected resource-contract tests; cleared-data launch has no CookiePrefs or restored session | Final release backup/restore smoke check on the production variant |
| Initial setup and login | Implemented | Connected to real backend and manually exercised | Dedicated UI and API integration matrix, validation and error-state acceptance |
| Bottom navigation | Verified | Manual emulator/phone checks | Back-stack and process-recreation tests |
| Home queue | Implemented | Real backend flow; basic UI test | Complete interaction, error, empty-state, and lifecycle coverage |
| Playback service | Verified | Queue reconciliation and durable-mutation unit tests; Pixel 9 offline seek/active/speed recovery across process stop | Expanded Media3 interruption, completion/auto-next, audio-network-loss, and OS process-death matrix |
| Active episode restore | Verified | Backend integration and queue reconciliation tests | Full device restart scenario in release checklist |
| Queue reorder | Implemented | Pure reorder unit tests and manual checks | Backend failure rollback and gesture instrumentation |
| Playback speed | Verified | Durable backend sync tests plus Pixel 9 offline/process-stop/reconnect restoration to 2.0x | Cross-device conflict and full speed-option matrix |
| Subscriptions carousel/filter | Verified | Compose UI tests and real backend checks | Rotation/process-recreation behavior |
| Refresh one/all | Verified | Refresh-all accepted/running/completed flow checked on the real test backend; polling unit tests and UI state tests; per-podcast real checks from accepted baseline | Real backend failed-job scenario and long-running/stuck-job policy |
| Podcast artwork/fallback | Verified | Exact web/Figma fallback checksum and real failed-image case | Success/loading/cache matrix across multiple hosts |
| Episode playlist actions | Implemented | Add/remove callbacks and backend paths exist | Full success/failure reconciliation instrumentation |
| Show notes | Verified | Backend contract tests, UI test, real Planet Money notes and scrolling | Link handling decision and accessibility review |
| Mark all listened | Verified | Single backend-owned atomic endpoint; unit/UI contract coverage; real Pixel 9 one-episode fixture verified listened/playlist/active effects and idempotent repeat | Release-candidate smoke check |
| Episode download | Implemented | Backend action and UI states exist | Real download success, failure, cancellation, file lifecycle, and playback-from-download matrix |
| Podcast unsubscribe/undo | Implemented | Countdown and optimistic-state unit coverage | End-to-end 15-second commit/cancel and process/lifecycle behavior |
| Add RSS feed | Implemented | Real success/duplicate checks from earlier stage | Automated UI/API integration and malformed-feed cases |
| OPML import/export | Implemented | Android document intents and backend calls exist; earlier manual checks | Repeatable fixture-based instrumentation and permission/error cases |
| Daily refresh time | Verified | Material TimePicker unit/UI/manual checks; confirmed save survives failed status reload | Full API fixture integration and 12/24-hour device matrix |
| SOCKS5 switch/status | Implemented | Real backend status displayed; confirmed switch value survives failed status reload | Full failure/running/off API fixture matrix and acceptance |
| Theme | Verified | Unit/UI tests and physical-phone checks | Screen-by-screen contrast/accessibility audit |
| Logout | Implemented | Backend response now controls the launch state; failed/unknown logout no longer claims the user is logged out | Expired-cookie and device-level success/failure instrumentation |
| Empty/loading/error states | Implemented | States exist across main screens | Complete screenshot and interaction matrix |

## Test baseline

Current automated suite:

- 75 local unit tests.
- 27 connected Android/Compose UI/configuration tests.
- Android lint.
- Debug app and Android-test APK assembly.

Current verified command set:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

### Important coverage gaps

- CI enforces unit tests, lint, and debug app/test APK assembly on every push and pull request. Connected Android 14 tests run for pull requests and manual workflow dispatch; the first remote run still needs verification after push.
- ViewModels and Retrofit failure/retry paths have little direct automated coverage.
- PlaybackService now has durable-sync unit coverage and a connected persistence check, but still lacks a complete automated Media3 device matrix for interruptions, completion/auto-next, and audio-network loss.
- Setup/login/logout, RSS add, OPML, download, unsubscribe, and Settings backend saves lack complete end-to-end automation.
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

## Definition of done for every feature

A feature may move to `Verified` only when all applicable items pass:

1. Expected behavior is documented or explicitly confirmed.
2. Implementation matches the relevant Figma state.
3. Success, loading, empty, disabled, retry, and failure states are handled.
4. Unit tests cover business/state transformations.
5. Compose/UI tests cover user-visible dispatch and state changes.
6. Real test-backend integration is checked when the feature uses the API.
7. The complete local unit, lint, assemble, and connected-test suite passes.
8. The changed flow is checked on the Pixel 9 emulator.
9. Device-sensitive changes are checked on the physical Android phone.
10. Version is bumped for an installable handoff build.
11. Only scoped files are committed and pushed.
12. The product owner receives the version, commit, checks performed, and known limitations, then accepts or rejects the stage.

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

- Stage 3.1 / A-07 implemented: OPML imports no longer allocate the selected document with `readBytes()`. Android rejects known files over `5,000,000` bytes before the request, enforces the same limit while streaming when provider metadata is absent or incorrect, and distinguishes size and provider-read failures in the UI. Unit coverage includes the exact limit, known and streaming over-limit paths, provider-open failure, and provider-read failure. Final Stage 3 gate and emulator fixture verification remain pending.
- Stage 3.2 / A-08 implemented: GitHub Actions runs unit tests, Android lint, debug APK assembly, and Android-test APK assembly on every push and pull request. Pull requests and manual dispatch additionally run the connected suite on an Android 14 Google APIs emulator. Reports are retained as workflow artifacts. Local workflow structure and commands are verified; the first upstream workflow execution remains pending.
- Stage 3.3 completed: repeatable API-contract coverage now pins session/login/register/logout, RSS add, streaming OPML import/export, Settings PATCH fields, proxy status, and scheduler status endpoints. Connected Compose coverage verifies login/setup credential dispatch, authentication loading/error UI, RSS validation and trimming, OPML mode/error/loading actions, Settings refresh save, proxy, export, logout, loading, and backend-error states. The Pixel 9 connected suite passes 27/27 tests.

Exit criterion: the agreed critical matrix is automated or explicitly documented as a manual release check.

### Stage 4 — Figma parity and accessibility

Goal: obtain screen-by-screen visual acceptance after functional behavior is stable.

Scope:

- Android screens and mobile components only.
- Light and dark themes.
- Loading, empty, error, disabled, modal, menu, and long-content states.
- Android 14 system bars, navigation inset, keyboard, font scaling, touch targets, TalkBack labels, and contrast.

Exit criterion: all MVP screens are accepted against Figma with no open P1 visual/accessibility defects.

### Stage 5 — Reliability and performance

Goal: make the app resilient to normal mobile lifecycle and network conditions.

Scope:

- Rotation and process recreation.
- Background/foreground and audio interruptions.
- Offline, slow, timeout, retry, server restart, expired session.
- Long podcast/episode/queue lists.
- Startup, frame rendering, CPU, and memory evidence on emulator and phone.

Exit criterion: critical flows recover predictably and measured regressions are documented or fixed.

### Stage 6 — Pre-release packaging

Goal: create the first production-configured release candidate.

Scope:

- Separate test and production build configuration.
- Production application ID `com.prod.mpod`.
- Production backend on port `5050`.
- Release signing information supplied by the product owner.
- Backup/data extraction, logging, network security, versioning, and APK upgrade behavior.
- Clean-install and upgrade-install checks.

Exit criterion: signed pre-release APK passes the release checklist on the physical phone.

### Stage 7 — Release candidate acceptance

Goal: final product-owner acceptance.

Scope:

- Full critical regression matrix.
- No open P0/P1 issues.
- Accepted list of deferred P2/P3 issues.
- Final APK checksum, version, commit, environment, and test report.

Exit criterion: explicit approval to treat the APK as the production release.

## Priorities

| Priority | Definition |
|---|---|
| P0 | Data loss, security/auth bypass, unusable startup, or playback/app-wide crash with no workaround |
| P1 | Core MVP flow is broken or gives an incorrect authoritative state |
| P2 | Important defect with a reasonable workaround, major visual mismatch, or missing non-critical coverage |
| P3 | Polish, minor inconsistency, low-risk technical debt, or deferred enhancement |

## Deferred product-owner input

There are no unanswered product questions blocking Stage 1. Release signing details are intentionally deferred and will be requested only when Stage 6 begins.

## Stage report template

Every completed stage report must include:

- Scope completed.
- APK version, when installed.
- Commit and pushed branch.
- Automated checks and their counts.
- Emulator/backend/physical-device checks actually performed.
- Known limitations or deferred findings.
- Exact acceptance request for the product owner.
