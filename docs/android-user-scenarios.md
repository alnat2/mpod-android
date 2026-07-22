# mpod Android — functional user scenarios

Last updated: 2026-07-22

## Purpose

This is the working map for making the Android application functionally complete. Work is planned, implemented, tested, and accepted by complete user scenarios rather than by screens, callbacks, or test counts.

The scenario map covers the MVP needed for a reliable working application. Pixel-perfect visual parity, extended accessibility, and performance polish remain outside this map unless they make a core action unreachable, unreadable, misleading, or unusable.

## Source priority

Expected behavior is taken from these sources, in order:

1. Explicit product-owner decisions in the Android project chat.
2. This scenario map and `docs/android-delivery-plan.md` after confirmed decisions are recorded.
3. Android screens and mobile components in the mpod Figma file.
4. Shared product and API documentation in the parent-project `mpod/docs` checkout.
5. The actual backend contract.

When a required behavior is absent or the sources disagree, the scenario is marked `Open`. It must not be implemented from an assumption.

Explicit chat decisions override stale Figma states. In particular, Home has no header actions, the player exists only on Home, subscription episodes have no Play action outside the playlist, Mark all listened has no confirmation, and the authenticated start destination is Subscriptions. Podcast artwork is informational: the MVP has no tap action or separate podcast-detail destination.

## Scenario status

| Status | Meaning |
|---|---|
| Specified | Expected outcome is known, but the complete evidence has not been audited against this map |
| Open | A product decision is required before implementation or acceptance |
| Failed | The complete scenario was executed and did not reach the expected result |
| Verified | The complete required evidence passed against the test backend |
| Accepted | The product owner accepted the scenario in the handed-off APK |

Existing unit, UI, backend, and manual results are baseline evidence only. A scenario becomes `Verified` after its whole row is checked, including the authoritative result and applicable recovery path.

## Evidence levels

| Code | Evidence |
|---|---|
| C | Contract or state/business unit test |
| U | Compose test using the real user gesture and checking the visible result |
| E | End-to-end on the Pixel 9 emulator against test backend `5051`, including backend state where applicable |
| L | Android lifecycle/connectivity interruption check |
| D | Physical Android 14+ phone check |
| R | Minified production-variant smoke check against backend `5050` |

`U` alone never proves a backend operation. `C` alone never proves that a user can perform the action. Existing evidence can be reused after it is matched to the complete scenario and its dependencies have not changed.

## P0 — application entry and session

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| APP-01 | Launch while backend is unavailable | A dedicated unavailable state is shown; it is not confused with Login | C,U,E | Verified |
| APP-02 | Tap Retry after backend connectivity returns | Session bootstrap is repeated and opens Setup, Login, or Subscriptions according to the authoritative response | C,U,E | Verified |
| APP-03 | First launch against an unconfigured backend | Setup is shown and Login is not offered as a substitute | C,U,E | Specified |
| APP-04 | Complete first-user setup with valid credentials | One submission creates the user/session and opens Subscriptions | C,U,E | Specified |
| APP-05 | Submit Setup or Login with blank fields | Submission is blocked locally and a useful validation error is shown | U | Verified |
| APP-06 | Login with valid credentials | The authenticated session is persisted and Subscriptions opens | C,U,E | Verified |
| APP-07 | Login with invalid credentials | Backend error is shown; the user remains on Login and can retry | C,U,E | Verified |
| APP-08 | Launch with a valid persisted session | Subscriptions opens without another login | C,E,L | Verified |
| APP-09 | Launch with an expired or server-invalidated session | Login opens; the app does not show unavailable and does not retain an authenticated UI | C,E,L | Verified |
| APP-10 | Logout successfully | Playback stops, cookies are cleared, and Login opens | C,U,E,L | Verified |
| APP-11 | Logout request fails because backend is unavailable | Authenticated state is not falsely reported as logged out; recovery is available | C,U,E | Specified |
| APP-12 | Relaunch after logout or cleared app data | No session is restored through Android backup/device transfer | C,E,L,R | Specified |

## P1 — navigation and application shell

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| NAV-01 | Authenticate or restore a session | Subscriptions is the initial selected destination | U,E | Verified |
| NAV-02 | Switch among Home, Subscriptions, and Settings | Each destination opens once and selected-tab state is truthful | U,E | Verified |
| NAV-03 | Tap Add podcast from bottom navigation | Add modal opens above the current destination; closing it returns without an unintended mutation | U,E | Verified |
| NAV-04 | Press Android Back from a modal or secondary state | The top modal/state closes before leaving the application | U,E | Verified |
| NAV-05 | Background and restore the app on a primary destination | The user does not land on a wrong authenticated destination or duplicate screen | U,L | Verified |

## P1 — adding podcasts and OPML import

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| ADD-01 | Open Add podcast and switch RSS/OPML modes | Correct fields and actions are shown; no submission occurs while switching | U | Verified |
| ADD-02 | Submit a blank or non-HTTP(S) RSS address | Invalid input is rejected locally and no request is sent | C,U | Verified |
| ADD-03 | Add a valid reachable RSS feed | Backend creates the subscription, modal closes, and Subscriptions shows it | C,U,E | Verified |
| ADD-04 | Add an already subscribed feed | Duplicate error is shown and no duplicate subscription is created | C,U,E | Verified |
| ADD-05 | Add an unreachable or invalid feed | Backend error is shown, modal stays usable, and library state is unchanged | C,U,E | Verified |
| ADD-06 | Submit RSS during a slow request | Duplicate submission is blocked and the loading state remains truthful until completion | U,E,L | Verified |
| ADD-07 | Open Android document picker and cancel | No import occurs and the Add modal remains usable without a false error | U,E | Verified |
| ADD-08 | Select a readable valid OPML file | File streams to backend; imported subscriptions appear after success | C,U,E | Verified |
| ADD-09 | Import OPML containing duplicates or skipped entries | The same modal replaces the form with `Import completed`, exact imported/skipped counts, and `Done`; no duplicate subscriptions are created | C,U,E | Verified |
| ADD-10 | Select an OPML file larger than 5,000,000 bytes | Android or backend rejects it with the approved size error; no partial import is claimed | C,U,E | Verified |
| ADD-11 | Selected document cannot be reopened/read or upload fails | A specific error is shown and Retry through choosing/importing again is possible | C,U,E,L | Verified |
| ADD-12 | Background/restore during document selection or upload | No crash, duplicate import, or false success occurs | U,L | Verified |

## P1 — subscriptions and refresh

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| SUB-01 | Open Subscriptions while data loads | Loading state is visible and actions cannot mutate unknown state | U,E | Specified |
| SUB-02 | Subscriptions load fails | Error is visible and Try again reloads the screen | U,E | Specified |
| SUB-03 | No podcasts are subscribed | Empty state offers Add RSS feed and Import OPML; both open the correct path | U,E | Specified |
| SUB-04 | All subscribed episodes are listened in Unlistened mode | Caught-up state is distinct from an empty library and can switch to Show all | C,U,E | Specified |
| SUB-05 | Swipe between podcast cards | Selected podcast, counts, artwork, and episode list change together and can return | U,E | Verified |
| SUB-06 | Toggle Show all / Show unlistened | Icon and visible podcasts/episodes match the selected filter | C,U,E | Verified |
| SUB-07 | Podcast artwork loads successfully | Real artwork is shown without replacing it with fallback | U,E | Verified |
| SUB-08 | Artwork is missing, invalid, or fails to decode/load | Approved Figma fallback artwork is shown | U,E | Verified |
| SUB-09 | Refresh one podcast successfully | Only that card shows refreshing; authoritative episodes/counts reload after completion | C,U,E | Verified |
| SUB-10 | Refresh one podcast fails | Failure is visible for that podcast and Retry repeats the same operation | C,U,E | Verified |
| SUB-11 | Refresh all podcasts successfully | Refreshing animation/state persists through the async backend job; library reloads only after completion | C,U,E | Verified |
| SUB-12 | One feed fails during Refresh all | Other feeds may finish; backend job error is visible and library remains usable | C,U,E | Specified |
| SUB-13 | Status polling temporarily fails or backend job is slow | UI does not claim completion; polling recovers without duplicate refresh jobs | C,E,L | Verified |
| SUB-14 | Episode list for one podcast fails while others load | Failure stays scoped to that podcast and its Retry does not discard the rest of the library | U,E | Specified |
| SUB-15 | Tap Unsubscribe, then Undo within 15 seconds | Podcast remains in backend and returns to normal UI state | C,U,E,L | Verified |
| SUB-16 | Let the 15-second unsubscribe countdown expire | Only selected podcast is deleted and its downloaded files/episodes disappear under backend lifecycle rules | C,U,E,L | Verified |
| SUB-17 | Final unsubscribe request fails | Podcast is restored/reloaded truthfully and the error can be retried | C,U,E,L | Verified |

## P1 — episode actions and authoritative playlist state

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| EPS-01 | Open an episode menu in Subscriptions | Only allowed actions are shown; there is no Play or queue-drag action outside the playlist | U | Verified |
| EPS-02 | Add an episode to playlist | Backend playlist changes; row/count/menu update to In playlist / Remove | C,U,E | Verified |
| EPS-03 | Add to playlist fails | Optimistic UI rolls back only the target episode and a retryable error is shown | C,U,E | Verified |
| EPS-04 | Remove a non-active episode from playlist | Backend and both screens remove only that episode; unrelated playback is uninterrupted | C,U,E | Verified |
| EPS-05 | Remove the active episode from playlist | Backend active state and Home player reconcile without stale playback or unintended autoplay | C,U,E | Verified |
| EPS-06 | Mark an episode listened | Backend marks it listened, removes it from playlist, applies download cleanup, and UI reconciles | C,U,E | Verified |
| EPS-07 | Mark a listened episode unlistened | Backend/UI change to unlistened; it is not silently re-added to playlist and deleted media is not restored | C,U,E | Verified |
| EPS-08 | Mark listened/unlistened fails | Target optimistic state rolls back and the backend remains authoritative | C,U,E | Verified |
| EPS-09 | Mark all listened for selected podcast | One backend operation marks only that podcast, removes its playlist rows, clears its active episode, and returns `markedEpisodes` | C,U,E | Verified |
| EPS-10 | Repeat Mark all listened or receive a failure | Repeat succeeds with zero changes; failure restores only the selected podcast and is retryable | C,U,E | Verified |
| EPS-11 | Open Show notes with backend notes | Correct episode notes open in a scrollable modal | C,U,E | Verified |
| EPS-12 | Open Show notes when notes are absent | A truthful empty-notes state opens instead of a broken or blank modal | C,U,E | Verified |
| EPS-13 | Tap a link in Show notes | The URL opens through the Android system browser | U,E | Verified |

## P1 — Home, queue, and player interaction

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| HOM-01 | Open Home while queue loads or load fails | Loading is visible; failure has a usable Retry and does not show invented queue data | U,E | Verified |
| HOM-02 | Open Home with no subscriptions | No-podcast state offers Add RSS and Import OPML | U,E | Specified |
| HOM-03 | Open Home with subscriptions but an empty playlist | Empty-playlist state is distinct from no subscriptions and navigation remains usable | U,E | Verified |
| HOM-04 | Open Home with a queue and no backend active episode | First queue item is displayed without autoplay | C,U,E | Verified |
| HOM-05 | Open Home with saved active playback | Correct episode and saved position restore without autoplay | C,U,E,L | Verified |
| HOM-06 | Tap a queue row | That episode becomes active and starts playing | C,U,E | Verified |
| HOM-07 | Open a Home episode menu | Menu contains only Play/Pause and Remove from playlist, matching web behavior | U | Verified |
| HOM-08 | Long-press and drag a queue row | Visible order and authoritative backend order change together | C,U,E | Verified |
| HOM-09 | Queue reorder fails | UI returns to backend order and shows a truthful error | C,U,E | Verified |
| HOM-10 | Queue changes from another client/backend operation | Home reconciles without duplicate/stale rows and preserves the current item when still valid | C,E,L | Verified |
| HOM-11 | Use Home after its queue becomes empty | Player and active state clear; no stale playable card remains | C,U,E | Verified |

## P0/P1 — playback and synchronization

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| PLY-01 | Tap Play on the displayed episode | Real audio starts, button changes to Pause, and backend active episode is updated | C,U,E,D | Specified |
| PLY-02 | Tap Pause | Audio stops, position is retained/synced, and Resume continues the same episode | C,U,E,D | Specified |
| PLY-03 | Tap rewind 10 or forward 15 | Player seeks by the requested amount within valid bounds and backend receives seek/progress semantics | C,U,E | Verified |
| PLY-04 | Tap or drag the progress track | Playback moves to the absolute selected position and backend stores the authoritative accepted position | C,U,E,D | Specified |
| PLY-05 | Seek backward by less than the backend acceptance threshold | UI and subsequent reload reconcile to the documented backend rule instead of making a false persistence claim | C,E | Specified |
| PLY-06 | Change playback speed | Each supported value 0.5/0.75/1/1.3/1.5/2 takes effect and persists through backend settings | C,U,E | Verified |
| PLY-07 | Relaunch with a saved playback speed | Confirmed speed is restored before playback; pending newer local value is not overwritten | C,E,L | Verified |
| PLY-08 | Play continuously | Progress syncs periodically without flooding or moving backward unexpectedly | C,E | Verified |
| PLY-09 | Finish an episode naturally | Backend marks completion, cleans queue/download state, and eligible next episode starts automatically | C,E,D | Specified |
| PLY-10 | Pause or seek inside the final 15 seconds | Backend completion rule is honored; queue/player reconcile without stale episode or unintended autoplay | C,E | Verified |
| PLY-11 | Finish the last eligible queue item | Completed item disappears and player reaches a truthful empty/non-playing state | C,E | Verified |
| PLY-12 | Playback progress/active/speed write fails transiently | Latest semantic state persists locally, retries with backoff, survives process restart, and clears only after success | C,E,L | Verified |
| PLY-13 | A delayed completion retry returns after another episode starts | Retry cannot hijack the newer active playback | C,E,L | Verified |
| PLY-14 | Audio stream fails before or during playback | Player shows a recoverable error; retry does not corrupt queue/progress | U,E,L,D | Specified |
| PLY-15 | Another app requests audio focus | mpod pauses/ducks and resumes only according to Android media behavior, without corrupting backend progress | E,L,D | Specified |
| PLY-16 | Headphones/Bluetooth route disconnects | Audio does not unexpectedly continue through the speaker; playback state remains recoverable | E,L,D | Specified |
| PLY-17 | Background, lock screen, notification controls, or return to app | Media notification and system lock-screen surface show episode/podcast metadata and Play/Pause only; playback and in-app state remain consistent | E,L,D | Specified |
| PLY-18 | Service/app process is stopped during playback | On next launch, backend/local state restores predictably without autoplay or lost confirmed progress | C,E,L,D | Specified |
| PLY-19 | Episode completes during a network outage, another episode starts, then connectivity returns | Delayed completion synchronizes and cleans the old episode but cannot replace, pause, seek, or otherwise hijack the newer playback | C,E,L | Verified |

## P1 — shared web/Android backend reconciliation

The MVP uses event-driven reconciliation, not continuous polling. Android reloads authoritative shared state on application launch, return to foreground, entry to Home or Subscriptions, and manual Refresh. A web-side change does not immediately interrupt current Android audio before one of these reconciliation events.

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| SYN-01 | Web changes playlist order/content while Android is inactive, then Android launches, foregrounds, or enters Home | Android replaces stale queue state with backend state and retains the active item only if it remains valid | C,E,L | Verified |
| SYN-02 | Web changes playback speed while Android is inactive, then Android launches or foregrounds | Android loads and applies the authoritative backend speed without continuous polling | C,E,L | Verified |
| SYN-03 | Web marks listened/Mark all listened/removes the episode Android considered active | At the next reconciliation event Android stops/clears stale playback and adopts the authoritative queue without autoplay | C,E,L | Verified |
| SYN-04 | Web changes state while Android is actively playing and no reconciliation event occurs | Current audio is not interrupted immediately; the change is applied at the next defined reconciliation event | E,L | Verified |

## P1 — downloads and file lifecycle

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| DLD-01 | Download an episode successfully | Progress/busy state is truthful; backend stores the server file and menu becomes Downloaded | C,U,E | Specified |
| DLD-02 | Tap Download again or select another episode while one download is running | Only one download runs at a time; all other Download actions are disabled until it finishes or fails, and no duplicate/parallel request is started | U,E | Specified |
| DLD-03 | Download fails | A dismissible failure is shown for the correct episode; normal Download action can retry | C,U,E | Specified |
| DLD-04 | Play an episode whose server download exists | Playback succeeds through the backend audio endpoint and uses backend file/source rules | E,D | Specified |
| DLD-05 | Mark a downloaded episode listened | Backend clears downloaded state and deletes or reconciles the server file under lifecycle rules | C,E | Specified |
| DLD-06 | Remove a downloaded episode from playlist | Backend applies the documented cleanup rule without corrupting unrelated files | C,E | Specified |
| DLD-07 | Mark a cleaned episode unlistened | File is not recreated and UI does not claim it remains downloaded | C,E | Specified |
| DLD-08 | Unsubscribe a podcast with downloaded episodes | Backend removes the podcast and applies cleanup to all affected server files | C,E | Specified |
| DLD-09 | Interrupt/background/kill app during a download | A surviving backend request may complete; otherwise Android returns to a failed/retryable Download state and never claims a complete file; no Cancel action is required for MVP | E,L | Specified |

## P1 — Settings and export

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| SET-01 | Open Settings while backend-dependent data loads or fails | Feed daily refresh and SOCKS5 show independent loading/error states without Retry; local Theme, Export, Session, and build information remain usable; re-entering Settings or restarting the app reloads data | U,E | Verified |
| SET-02 | Open the daily refresh time control and cancel | Android time picker uses device 12/24-hour mode; cancel leaves the saved time unchanged | C,U,E | Verified |
| SET-03 | Select a new time and save | Exact `HH:mm` value is persisted by backend and confirmed state is shown | C,U,E | Verified |
| SET-04 | Open Settings without changing refresh time | Save is disabled/secondary and does not make a redundant write | U | Verified |
| SET-05 | Refresh-time save fails | Old confirmed value remains and the error is retryable | C,U,E | Verified |
| SET-06 | Save succeeds but status reload fails | Saved confirmed value remains; UI distinguishes save success from status-refresh failure | C,U,E | Verified |
| SET-07 | Proxy is not configured | Status is truthful and the switch cannot claim a running proxy | C,U,E | Verified |
| SET-08 | Enable or disable configured SOCKS5 proxy | Backend setting and visible switch/status agree after save/reload | C,U,E | Verified |
| SET-09 | Proxy reports running, unknown, or error | Current IP/geo/error/status are represented truthfully and unrelated Settings remain usable | C,U,E | Verified |
| SET-10 | First install follows system theme | Dark device gives Dark; light device gives Light | C,U,E,D | Specified |
| SET-11 | Toggle Use dark theme off/on | Off selects explicit Light; on selects explicit Dark; choice persists across restart | C,U,E,D | Specified |
| SET-12 | Export OPML and choose a writable destination | Exported file is written through Android provider and contains the authoritative subscription list | C,U,E | Verified |
| SET-13 | Cancel export destination selection | No file/error/success is falsely reported | U,E | Verified |
| SET-14 | Export request or destination write fails | Specific recoverable error is shown and existing destination content is not falsely reported as valid | C,U,E,L | Verified |
| SET-15 | View build/environment information | Displayed build identifies the installed APK sufficiently to distinguish test and production variants | U,E,R | Verified |

## P0/P1 — cross-cutting reliability and delivery

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| REL-01 | Rotate during a non-submitted form or modal | Entered data and modal intent are not silently corrupted or submitted twice | U,L | Verified |
| REL-02 | Rotate/background during a submitted mutation | No duplicate backend mutation or false result occurs; completion/retry is truthful | C,E,L | Verified |
| REL-03 | Backend returns 401 during an authenticated action | App exits stale authenticated state and reaches Login without leaking the failed mutation | C,E,L | Verified |
| REL-04 | Backend returns structured 4xx/5xx or malformed/empty success payload | User sees a truthful recoverable outcome; app does not crash or invent success | C,U,E | Verified |
| REL-05 | Network is offline, slow, times out, then returns | Core screen remains usable or recoverable; retry does not duplicate mutations | C,E,L | Verified |
| REL-06 | Process is recreated with pending destructive/mutating UI | Backend remains authoritative; no mutation occurs merely because stale UI state was restored | C,E,L | Verified |
| REL-07 | Library/queue contains long titles and enough rows to scroll | Core actions remain reachable and operate on the intended item | U,E,D | Specified |
| REL-10 | Build the release APK against production and run the approved smoke path | Release uses server `5050`; login, subscriptions, playback, speed, episode completion, Settings, MediaSession, and background playback work without a critical defect | C,R,D | Specified |
| REL-12 | Complete regression gate and release handoff | All PRD scenarios and regression checks pass; release APK version/checksum/commit/backend and known limitations are recorded | C,U,E,D,R | Specified |
| REL-13 | Backend accepts a connection but a core request exceeds the 30-second network timeout | Loading remains visible and duplicate submission is blocked; timeout ends in the screen/action-specific error state rather than an infinite spinner; the documented reload/retry path can recover | C,U,E,L | Specified |

## Resolved scenario decisions

The product owner confirmed on 2026-07-19:

1. OPML partial success is shown inside the existing import modal as `Import completed`, exact imported/skipped counts, and `Done`; do not stack another dialog over the modal.
2. Links in Show notes are tappable and open through the Android system browser.
3. Android media notification and lock-screen controls expose Play/Pause only, together with episode/podcast metadata.
4. Downloads have no user-facing Cancel action in the MVP. An interrupted request either completes or returns to a retryable Download state without false success.
5. Settings has no Retry buttons. Feed daily refresh and SOCKS5 expose independent backend errors; local sections stay usable. Re-entering the screen or restarting the application reloads the backend-dependent data.
6. Web/Android synchronization is event-driven for the MVP: launch, foreground, entry to Home or Subscriptions, and manual Refresh reconcile shared state. There is no continuous polling and no immediate interruption of current audio before reconciliation.
7. Until Downloads is redesigned, Android permits one download at a time and disables other Download actions while it is running. Parallel download behavior is outside the current MVP design.
8. Release acceptance uses one release APK. A separate test application, a second application ID, Test/Production coexistence, and upgrade/co-installation checks are not mpod requirements. After all PRD scenarios and regression tests pass, release is switched to production server `5050`, assembled, and smoke-tested for login, subscriptions, playback, speed, episode completion, Settings, MediaSession, and background playback. With no critical defects, the APK is ready for release.

There are no known unanswered product questions blocking the functional scenario audit.

## Backend follow-ups

1. `BE-FU-01`: during EV-W8, `DELETE /api/podcasts/23` returned success and removed the podcast, but `/api/playlist` retained episode `11764`; `/api/playback/queue` already filtered that orphan. The orphan made the next full reorder fail with `INVALID_PLAYLIST_ORDER` until the row was explicitly deleted. Backend podcast deletion must remove every affected playlist row in the committed database state.
2. `BE-FU-02`: the documented rule `positionSeconds >= durationSeconds - 15` treats every position, including zero, as completed when duration is 15 seconds or less. Android no longer sends a redundant paused zero-position update during queue reconciliation, but the backend/product rule for a real short episode still needs an explicit decision; Android must not invent a different threshold independently.

## Verification ledger

This ledger records why scenario statuses changed. Git remains the change history for the document itself.

| Evidence ID | Date | Scope | Result/source |
|---|---|---|---|
| EV-4.1 | 2026-07-16 | `APP-01`, `APP-02`, `APP-06`–`APP-10` | Stage 4.1 real test-backend session/startup matrix, lifecycle checks, and auth contract/Compose evidence recorded in `docs/android-delivery-plan.md` |
| EV-4.2 | 2026-07-16 | `ADD-02`–`ADD-05`, `SUB-05`–`SUB-11` | Stage 4.2 temporary RSS fixture, real backend refresh success/failure/recovery, filters, and artwork success/fallback |
| EV-4.3 | 2026-07-16 | `EPS-01`–`EPS-04`, `EPS-09`, `EPS-11`, `HOM-08`, `HOM-09` | Stage 4.3 three-episode fixture, authoritative playlist/mark-all/show-notes results, real drag reorder, and offline rollback |
| EV-4.4 | 2026-07-16 to 2026-07-19 | `HOM-04`, `HOM-05`, `PLY-03`, `PLY-06`–`PLY-08`, `PLY-10`, `PLY-12` | Stage 4.4 authenticated MP3 fixtures, durable sync recovery, threshold reconciliation, and seek/speed/progress evidence; commits `0f1a0dc` and `47c73f0`. Device-required playback rows remain Specified until the final phone pass |
| EV-PROD | 2026-07-19 | `REL-10` baseline evidence only | Minified release startup defect fixed in commit `d755f99`; the release build reached production server `5050`. The row remains Specified until the complete approved production smoke path is executed |
| EV-W1 | 2026-07-19 | `APP-05`, `NAV-01`–`NAV-05` | Blank Login/Setup dispatch tests; all-destination bottom-nav test; real `5051` login to Subscriptions; Home/Settings/Add navigation; system Back; background restore; and process recreation on Pixel 9. Full gate: 94 unit, 44 connected, debug/release lint and APK assembly |
| EV-W1-PARTIAL | 2026-07-19 | `APP-03`, `APP-04`, `APP-11`, `APP-12` remain Specified | Isolated HTTP connected tests protect `setupRequired → register` and failed logout recovery; backend router tests protect real first setup. Shared `5051` cannot be reset or forced to fail safely, and `APP-12` still requires its final release/device backup smoke check, so these rows were not promoted |
| EV-W2 | 2026-07-19 | `ADD-01`, `ADD-06`–`ADD-12` | Compose/ViewModel and multipart contract coverage plus real Pixel 9 emulator checks against `5051`: mode switching without submission; picker cancellation; mixed OPML result `1 imported / 1 skipped`; repeat result `0 / 2`; local 5,000,001-byte rejection; invalid-OPML error followed by successful retry; and double-submit plus background/restore during a five-second RSS request producing exactly one subscription. Process loss while the document picker was open produced no crash, import, or false result. Temporary subscriptions were removed. Full gate: 94 unit, 48 connected, debug/release lint and APK assembly |
| EV-W3 | 2026-07-19 | `SUB-13`, `SUB-15`–`SUB-17` | Real `5051` checks with a temporary feed: a slow Refresh all stayed visibly running and non-repeatable across background/restore until backend completion; failed final unsubscribe during a connectivity interruption kept the podcast visible and `Try again` repeated DELETE immediately and removed only that podcast. Earlier real Undo/final-countdown evidence remains applicable. A lifecycle reload bug that cleared active mutation guards and an incorrect unsubscribe Retry route were fixed. Full gate: 95 unit, 53 connected, debug/release lint and APK assembly |
| EV-W3-PARTIAL | 2026-07-19 | `SUB-01`–`SUB-04`, `SUB-12`, `SUB-14` remain Specified | Compose/state tests protect loading without mutation actions, load-error Retry, both empty-library add paths, distinct caught-up/Show all behavior, refresh failure UI, and scoped episode failure with another usable podcast. Complete `5051` evidence would require an isolated empty library, controlled initial-load/episode endpoint failures, and allowing backend Refresh all to exhaust its 30s/2m/5m feed retry schedule; shared Planet Money state was not destructively altered and incomplete rows were not promoted |
| EV-W5 | 2026-07-19 | `SET-01`–`SET-09` | Independent refresh/proxy loading and failure instrumentation; unchanged-save suppression; failed-save exact retry; confirmed-save/status-failure retention; unconfigured/off/running/unknown/error proxy states; Material 12-hour picker cancel; real Pixel 9 `04:00 → 04:05 → 04:00` save/restore; real proxy `on → off → on`; re-entry reload and empty crash buffer. Full gate: 97 unit, 74 connected, debug/release lint and APK assembly |
| EV-W6 | 2026-07-19 | `SET-12`–`SET-15` | Android provider success/cancel/HTTP failure/write failure/duplicate-submit/resume-race instrumentation; real DocumentsUI save produced `mpod-subscriptions.opml`, not `.opml.xml`; its 269 bytes matched the authenticated `5051` response exactly and parsed as XML. Test UI displayed version/code, Test, package, `5051`, and backend commit; unit mapping covers the production package and the minified release APK compiled successfully. Full gate: 99 unit, 81 connected, debug/release lint and APK assembly |
| EV-W6-PARTIAL | 2026-07-19 | `SET-10`, `SET-11` remain Specified | Pixel 9 emulator clean-data launch followed system Dark; explicit Light and Dark each survived force-stop, and the emulator/test data were restored to system Light/System. Physical-device evidence is intentionally deferred to the final phone pass, so these rows were not promoted |
| EV-W7 | 2026-07-21 | `HOM-10`, `SYN-01`–`SYN-04` | Pixel 9 and real `5051` multi-client checks: background queue reorder and speed change were applied on foreground while the valid active episode kept playing; foreground backend changes caused no immediate interruption or polling update, then entering Subscriptions applied both; externally marking the active episode listened cleared backend active/queue state and Android reconciled to the next episode paused without autoplay. Android now reconciles backend speed with queue invalidations from Home and Subscriptions while preserving a pending local speed write. Backend queue `[16,18,26]`, null active, speed `1.3x`, listened flags, and playback position were restored. Full gate: 101 unit, 82 connected, debug/release lint and APK assembly |
| EV-W8 | 2026-07-21 | `PLY-11`, `PLY-13`, `PLY-19`; partial `PLY-09` | Authenticated 20/60/20-second MP3 fixture on Pixel 9 and real `5051`. Online natural completion moved from A to B playing. With network denied only to mpod, B started from buffer while backend remained active A and A completion persisted on disk; after recovery pending cleared, backend removed A and selected B, and MediaSession continued B instead of being hijacked. A race that classified a playing request from post-response state was fixed by retaining submission-time state. Finishing sole C cleared backend active/queue and MediaSession; a stale Home card exposed and fixed missing service-to-Home completion invalidation. Redundant paused reconciliation writes are suppressed. Temporary podcasts were removed and Planet Money queue `[16,18,26]`, null active, positions `51/242`, and speed `1.3x` were restored. `PLY-09` remains Specified only because its required physical-phone evidence is deferred. Full gate: 103 unit, 83 connected, debug/release lint and APK assembly |
| EV-W9 | 2026-07-22 | `REL-03` | A real persisted Android session was invalidated through `POST /api/auth/logout` on test backend `5051` while Settings remained open. Saving a locally selected `04:05` received an authenticated `401`; Android immediately stopped playback, cleared the persisted cookie, replaced the authenticated shell with Login, and did not retain the failed mutation. An independent authenticated backend session confirmed `dailyRefreshTime` remained `04:00`. Login succeeded again afterward and the crash buffer was empty. Policy, real-interceptor, launch-navigation, and cookie-clearing tests protect the path. Full gate: 105 unit, 87 connected, debug/release lint and APK assembly |
| EV-W9-PARTIAL | 2026-07-22 | `REL-13` remains Specified | The shared core client now has one explicit 30-second call deadline with matching connect/read/write limits. Instrumentation verifies the production client values and a controlled delayed Settings save verifies truthful timeout termination, duplicate-submit blocking, unchanged confirmed state, and successful retry. A real 30-second `5051` path was not manufactured by pausing or disrupting the shared backend, so the required E2E evidence is still incomplete and the row was not promoted |
| EV-W10 | 2026-07-22 | `REL-04`, `REL-05`; partial `REL-13` | All response roots used by startup, Home, Subscriptions, Settings, OPML, playback queue, and playback-sync confirmations now distinguish a missing payload from a valid empty value. Structured backend errors remain user-facing, malformed/empty 2xx responses cannot invent successful state, and retries preserve authoritative data. Pixel 9 through an isolated QA proxy showed the exact structured `503` message, a stable `Could not load podcasts.` result for malformed `200`, and successful Home recovery through Retry without changing the shared backend. Existing real offline/slow/lifecycle evidence plus the controlled 30-second client, delayed-save termination, duplicate-write blocking, and recovery cover `REL-05`. Playback sync keeps malformed confirmations retryable. The proxy stopped receiving emulator traffic during the attempted manual timeout pass, so that attempt is not counted as E2E evidence and `REL-13` remains Specified. Full gate: 109 unit, 91/91 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W11 | 2026-07-22 | `REL-01`, `REL-02`, `REL-06` | Busy state for login/register, Add podcast/OPML, refresh-all/per-podcast refresh, unsubscribe, Mark all listened, and Home reorder is now claimed synchronously before coroutine dispatch, so immediate duplicate calls cannot enter the request queue. Add podcast mode and RSS draft are owned by an activity-scoped ViewModel and `SavedStateHandle`; Settings draft time and open picker use saveable state. State-restoration and delayed-request tests cover drafts, modal intent, no false submission, and exactly-one dispatch. On Pixel 9 using the current `com.prod.mpod.test` package, RSS draft, OPML mode, and the open TimePicker survived real rotation without submission/save. Force-stop at `13 sec` in the Planet Money unsubscribe Undo window, followed by a wait past the original deadline and relaunch, kept `1 podcast`, restored no stale Undo, and dispatched no delayed deletion. Earlier observations from the unrelated legacy `com.example.mpod` package were discarded. Full gate: 109 unit, 99/99 connected, debug/release lint, debug/test APKs, and minified release APK |

## Execution order

After the product owner reviews this map, work proceeds in functional waves:

1. Audit P0/P1 scenario rows against existing evidence; do not rerun unchanged evidence without a dependency reason.
2. Execute unknown and high-risk scenarios end-to-end, recording `Verified`, `Failed`, and the exact evidence.
3. Fix failed scenarios in small scenario-scoped commits, then rerun that scenario and affected regression paths.
4. Run the cross-cutting reliability matrix.
5. Switch release configuration to production server `5050`, build one release APK, and perform the approved production smoke pass on the physical phone.

Each implementation batch ends with a scoped commit and report. The next batch does not start until approval, unless the product owner explicitly authorizes completing a whole named wave without intermediate confirmation.
