# mpod Android — delivery plan and quality baseline

Last updated: 2026-07-15

Current Android baseline: `1.0.4 (5)`, commit `d5fb07c`

## Purpose

This is the living delivery document for the native Android application. It is the single place to track scope, implementation status, verification, acceptance, defects, and release readiness.

The document must be updated in the same commit as any change that alters a tracked status. A feature is not considered complete merely because its UI exists.

## Sources of truth

Use sources in this order:

1. Explicit decisions confirmed by the product owner in the Android project chat.
2. The Android Figma screens and mobile components.
3. Parent-project product and API documentation under `/Users/cross/Documents/mpod`.
4. The actual backend contract and behavior.
5. This document and Android-specific decisions.

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

The current build is a working test baseline, not a release candidate.

| Area | Current status | Existing evidence | Remaining work before release |
|---|---|---|---|
| Startup/session restoration | Verified | Unit coverage for launch-state resolution; exercised with test account | Lifecycle, expired-session, and backend-unavailable instrumentation coverage |
| Initial setup and login | Implemented | Connected to real backend and manually exercised | Dedicated UI and API integration matrix, validation and error-state acceptance |
| Bottom navigation | Verified | Manual emulator/phone checks | Back-stack and process-recreation tests |
| Home queue | Implemented | Real backend flow; basic UI test | Complete interaction, error, empty-state, and lifecycle coverage |
| Playback service | Implemented | Queue reconciliation unit tests; manual playback checks from earlier stages | Automated Media3/service integration, interruption, network loss, completion, and process-death scenarios |
| Active episode restore | Verified | Backend integration and queue reconciliation tests | Full device restart scenario in release checklist |
| Queue reorder | Implemented | Pure reorder unit tests and manual checks | Backend failure rollback and gesture instrumentation |
| Playback speed | Implemented | Backend persistence code and manual checks | Automated persistence and restoration test |
| Subscriptions carousel/filter | Verified | Compose UI tests and real backend checks | Rotation/process-recreation behavior |
| Refresh one/all | Verified | Real HTTP `200` checks, UI state tests, animation checked | Failure, timeout, overlapping-request and scheduler-running scenarios |
| Podcast artwork/fallback | Verified | Exact web/Figma fallback checksum and real failed-image case | Success/loading/cache matrix across multiple hosts |
| Episode playlist actions | Implemented | Add/remove callbacks and backend paths exist | Full success/failure reconciliation instrumentation |
| Show notes | Verified | Backend contract tests, UI test, real Planet Money notes and scrolling | Link handling decision and accessibility review |
| Mark all listened | Implemented | Unit/UI dispatch coverage and real fix from prior stage | Product decision about confirmation; partial-failure integration coverage |
| Episode download | Implemented | Backend action and UI states exist | Real download success, failure, cancellation, file lifecycle, and playback-from-download matrix |
| Podcast unsubscribe/undo | Implemented | Countdown and optimistic-state unit coverage | End-to-end 15-second commit/cancel and process/lifecycle behavior |
| Add RSS feed | Implemented | Real success/duplicate checks from earlier stage | Automated UI/API integration and malformed-feed cases |
| OPML import/export | Implemented | Android document intents and backend calls exist; earlier manual checks | Repeatable fixture-based instrumentation and permission/error cases |
| Daily refresh time | Verified | Material TimePicker unit/UI/manual checks | Save/reload backend integration test and 12/24-hour device matrix |
| SOCKS5 switch/status | Implemented | Real backend status displayed | Failure/running/off state matrix and acceptance |
| Theme | Verified | Unit/UI tests and physical-phone checks | Screen-by-screen contrast/accessibility audit |
| Logout | Implemented | Backend call and playback service stop exist | Expired-cookie and failure-path instrumentation |
| Empty/loading/error states | Implemented | States exist across main screens | Complete screenshot and interaction matrix |

## Confirmed implementation gap

The following is a code-confirmed functional gap and must enter the first implementation backlog after the audit:

- `Play` in an episode menu on Subscriptions currently dispatches to `Unit`, so it performs no action. The intended playlist/active-playback behavior must be confirmed before implementation.

## Documentation conflicts to resolve

- `docs/android-product-decisions.md` says the backend address can be modified in Settings. The approved current behavior is a hardcoded address; the Settings UI no longer exposes it.
- `docs/android-product-decisions.md` requires a confirmation dialog for `Mark all listened`, while the current Android behavior executes immediately. This requires an explicit product decision.
- `TODO.md` still says placeholder podcast artwork must be replaced, but the app now loads remote artwork and uses the exact approved fallback on missing/error states.

These files must be reconciled only after the product decisions above are confirmed.

## Test baseline

Current automated suite:

- 42 local unit tests.
- 13 connected Android/Compose UI tests.
- Android lint.
- Debug app and Android-test APK assembly.

Current verified command set:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

### Important coverage gaps

- No CI workflow currently enforces the test suite on every push.
- ViewModels and Retrofit failure/retry paths have little direct automated coverage.
- PlaybackService lacks device-level automated coverage.
- Setup/login/logout, RSS add, OPML, download, unsubscribe, and Settings backend saves lack complete end-to-end automation.
- Process death, rotation, background/foreground, expired session, offline recovery, slow network, and timeout scenarios are not systematically covered.
- Accessibility, font scaling, display scaling, and 12/24-hour locale matrices are incomplete.
- The physical-phone pass is manual and does not yet use a written repeatable release checklist.

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

Exit criterion: the product owner accepts this plan structure.

### Stage 1 — Full audit and defect backlog

Goal: produce an evidence-backed audit of the current APK without implementing unrelated fixes.

Scope:

- Compare every Android screen and state with Figma.
- Trace every visible action to ViewModel/API/service behavior.
- Check parent API documentation against Android DTOs and calls.
- Execute the critical manual test matrix on emulator and phone.
- Classify findings as P0–P3 with reproduction, expected result, actual result, affected code, and missing test.
- Mark the feature matrix with verified evidence rather than assumptions.

Exit criterion: an agreed prioritized defect backlog.

### Stage 2 — Critical functional completeness

Goal: eliminate agreed P0/P1 functional defects.

Expected focus, subject to the Stage 1 backlog:

- Subscription episode Play behavior after product confirmation.
- Playback lifecycle and backend synchronization.
- Destructive action and download/file-lifecycle correctness.
- Auth/session failure recovery.
- Data-contract mismatches and state reconciliation.

Exit criterion: no open P0/P1 functional defects in the agreed MVP scope.

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

## Questions requiring product-owner decisions

These questions must be answered before implementing the affected behavior:

1. When `Play` is selected for an episode in Subscriptions that is not in the playlist, should Android add it to the playlist and start playback, or is another behavior required?
2. Should `Mark all listened` execute immediately, or show the confirmation dialog currently described in Android product decisions?
3. Release signing details are intentionally deferred until Stage 6; they will be requested when a pre-release is approved.

## Stage report template

Every completed stage report must include:

- Scope completed.
- APK version, when installed.
- Commit and pushed branch.
- Automated checks and their counts.
- Emulator/backend/physical-device checks actually performed.
- Known limitations or deferred findings.
- Exact acceptance request for the product owner.
