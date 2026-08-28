# mpod Android — functional user scenarios

Last updated: 2026-08-28 (Updated for standalone podcast player mpoddy with Room database, RSS/OPML parsing, Smart Listening, and DataStore)

## Purpose

This is the working map for making the Android application functionally complete. Work is planned, implemented, tested, and accepted by complete user scenarios rather than by screens, callbacks, or test counts.

The scenario map covers the MVP needed for a reliable working standalone application (`mpoddy`). Pixel-perfect visual parity, extended accessibility, and performance polish remain outside this map unless they make a core action unreachable, unreadable, misleading, or unusable.

## Source priority

Expected behavior is taken from these sources, in order:

1. Explicit product-owner decisions in the Android project chat.
2. This scenario map and `docs/android-delivery-plan.md` after confirmed decisions are recorded.
3. Android screens and mobile components in the mpod Figma file.
4. Standalone application requirements, local Room database contracts, RSS/OPML standards, and Jetpack Media3 lifecycle.

When a required behavior is absent or the sources disagree, the scenario is marked `Open`. It must not be implemented from an assumption.

Explicit chat decisions override stale Figma states. In particular, the first bottom navigation destination is labeled `Player` in bottom navigation, has no header actions, and remains the only player screen; subscription episodes have no Play action outside the playlist, Mark all listened has no confirmation dialog, and the start destination is Subscriptions. Episode actions are shown inline on the Player playlist and Subscriptions episode cards rather than through an episode action bottom sheet. Bottom sheets remain for playback-speed selection; Add podcast remains a modal overlay/card flow.

## Scenario status

| Status | Meaning |
|---|---|
| Specified | Expected outcome is known, but the complete evidence has not been audited against this map |
| Deferred | The product owner explicitly removed the scenario from the current release scope pending a redesign |
| Open | A product decision is required before implementation or acceptance |
| Failed | The complete scenario was executed and did not reach the expected result |
| Verified | The complete required evidence passed against the local database and real feeds/devices |
| Accepted | The product owner accepted the scenario in the handed-off APK |
| Retired | Scenario superseded by the transition to standalone local architecture (e.g. backend auth) |

## Evidence levels

| Code | Evidence |
|---|---|
| C | Contract, parser, or state/business unit test |
| U | Compose test using the real user gesture and checking the visible result |
| E | End-to-end on the Pixel 9 emulator with local Room database and real RSS feeds |
| L | Android lifecycle/connectivity interruption check |
| D | Physical Android 14+ phone check |
| R | Minified release-variant smoke check (`com.prod.mpod`) |

## P0 — application entry, local persistence, and startup

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| APP-01 | Cold launch with existing subscriptions | Room DB loads stored podcasts/episodes into memory on IO dispatcher; UI renders Subscriptions without an authentication step | C,U,E | Verified |
| APP-02 | Cold launch with empty database | App starts directly on Subscriptions showing the empty library state with Add RSS and Import OPML actions | C,U,E | Verified |
| APP-03 | Process recreation / rotation on startup | Database state and selected tab are restored without duplicated initialization or database locks | U,L | Verified |
| APP-04 | Relaunch after cleared app data | App starts cleanly with empty Room DB and default DataStore preferences (System theme, Direct proxy, default Smart Listening) | C,E,L,R | Verified |
| APP-05–APP-11 | Remote backend auth/login/logout/session | Retired with migration to standalone offline-capable player architecture (commit `8a25520`) | — | Retired |

## P1 — navigation and application shell

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| NAV-01 | Launch application | Subscriptions is the default initial selected destination | U,E | Verified |
| NAV-02 | Switch among Player, Subscriptions, and Settings | Each destination opens once and selected-tab state is truthful; Player opens the Player/Now playing route | U,E | Verified |
| NAV-03 | Tap Add podcast from bottom navigation | Add modal opens above the current destination; closing it returns without an unintended mutation | U,E | Verified |
| NAV-04 | Press Android Back from a modal or secondary state | The top modal/state closes before leaving the application | U,E | Verified |
| NAV-05 | Background and restore the app on a primary destination | The user does not land on a wrong destination or duplicate screen | U,L | Verified |

## P1 — adding podcasts and OPML import

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| ADD-01 | Open Add podcast and switch RSS/OPML modes | Correct fields and actions are shown; no submission occurs while switching | U | Verified |
| ADD-02 | Submit a blank or non-HTTP(S) RSS address | Invalid input is rejected locally and no request is sent | C,U | Verified |
| ADD-03 | Add a valid reachable RSS feed | Direct RSS parser fetches feed, inserts podcast and episodes into Room DB, modal closes, Subscriptions updates | C,U,E | Verified |
| ADD-04 | Add an already subscribed feed (including normalized URL variations) | Normalized URL match detects duplicate, duplicate error is shown, no duplicate Room entity created | C,U,E | Verified |
| ADD-05 | Add an unreachable or invalid feed | Feed parser error is shown, modal stays usable, and Room DB remains unchanged | C,U,E | Verified |
| ADD-06 | Submit RSS during a slow network request | Duplicate submission is blocked and loading state remains truthful until completion | U,E,L | Verified |
| ADD-07 | Open Android document picker and cancel | No import occurs and the Add modal remains usable without a false error | U,E | Verified |
| ADD-08 | Select a readable valid OPML file | Local XML parser reads file, inserts new feeds into Room DB, imported subscriptions appear after success | C,U,E | Verified |
| ADD-09 | Import OPML containing duplicates or skipped entries | Modal replaces the form with `Import completed`, exact imported/skipped counts, and `Done`; no duplicate subscriptions created | C,U,E | Verified |
| ADD-10 | Select an OPML file larger than 5,000,000 bytes | Stream limit rejects oversized file with approved error before parsing; no partial import | C,U,E | Verified |
| ADD-11 | Selected document cannot be reopened/read or parse fails | Specific error is surfaced and choosing/importing again is possible | C,U,E,L | Verified |
| ADD-12 | Background/restore during document selection or import | No crash, duplicate import, or false success occurs | U,L | Verified |

## P1 — subscriptions, feed refresh, and Smart Listening

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| SUB-01 | Open Subscriptions while data loads from Room | Room Flow loads quickly; neutral loading state prevents mutating unready state | U,E | Verified |
| SUB-02 | Subscriptions load / DB query error | Error banner visible and Try again retries loading from Room DB | U,E | Verified |
| SUB-03 | No podcasts are subscribed | Empty state offers Add RSS feed and Import OPML; both open the correct modal mode | U,E | Verified |
| SUB-04 | All subscribed episodes are listened in Unlistened mode | Caught-up state is distinct from an empty library and can switch to Show all | C,U,E | Verified |
| SUB-05 | Swipe between podcast cards | Selected podcast, counts, artwork, and episode list change together; header summarizes counts (e.g. `12 podcasts · 2 unlistened`) | U,E | Verified |
| SUB-06 | Toggle Show all / Show unlistened | Icon and visible podcasts/episodes match the selected filter | C,U,E | Verified |
| SUB-07 | Podcast artwork loads successfully | Real artwork is loaded and cached via Coil/OkHttp | U,E | Verified |
| SUB-08 | Artwork is missing, invalid, or fails to load | Approved Figma fallback artwork drawable is rendered | U,E | Verified |
| SUB-09 | Refresh one podcast successfully | RSS feed is fetched directly; updated episodes and metadata persist to Room DB | C,U,E | Verified |
| SUB-10 | Refresh one podcast fails | Feed error is displayed on that podcast card; Retry repeats the refresh | C,U,E | Verified |
| SUB-11 | Refresh all podcasts successfully | Feeds are fetched in parallel; updated episodes persist to Room DB; progress indicator reflects completion | C,U,E | Verified |
| SUB-12 | One feed fails during Refresh all | Other feeds finish successfully; partial failure is surfaced without breaking the library | C,U,E | Verified |
| SUB-13 | Network drops during feed refresh | Network failure is caught gracefully; previous Room DB state remains intact | C,E,L | Verified |
| SUB-14 | Episode list for one podcast fails parsing while others load | Failure stays scoped to that podcast; other podcasts remain usable | U,E | Verified |
| SUB-15 | Tap Unsubscribe, then Undo within 15 seconds | Unsubscribe job is cancelled and podcast remains in Room DB and UI | C,U,E,L | Verified |
| SUB-16 | Let the 15-second unsubscribe countdown expire | Podcast and its episodes are deleted from Room DB; active episode cleared if belonging to this podcast | C,U,E,L | Verified |
| SUB-17 | Database error during delete | Error is surfaced and podcast remains visible in Room DB | C,U,E,L | Verified |
| SUB-18 | Re-enter or recreate Subscriptions after a successful load | Room Flow delivers cached state immediately without empty-library flicker | C,U,E,L | Verified |

## P1 — episode actions and authoritative playlist state

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| EPS-01 | Use an episode action in Subscriptions | Allowed actions are shown inline on the episode card; there is no Play, manual Download, or queue-drag action outside the playlist | U | Verified |
| EPS-02 | Add an episode to playlist | Backend playlist changes; row/count/menu update to In playlist / Remove | C,U,E | Verified |
| EPS-03 | Add to playlist fails | Optimistic UI rolls back only the target episode and a retryable error is shown | C,U,E | Verified |
| EPS-04 | Remove a non-active episode from playlist | Backend and both screens remove only that episode; unrelated playback is uninterrupted | C,U,E | Verified |
| EPS-05 | Remove the active episode from playlist | Backend active state and Home player reconcile without stale playback or unintended autoplay | C,U,E | Verified |
| EPS-06 | Mark an episode listened | Backend marks it listened, removes it from playlist, applies download cleanup, and UI reconciles | C,U,E | Verified |
| EPS-07 | Mark a listened episode unlistened | Backend/UI change to unlistened; it is not silently re-added to playlist and deleted media is not restored | C,U,E | Verified |
| EPS-08 | Mark listened/unlistened fails | Target optimistic state rolls back and the backend remains authoritative | C,U,E | Verified |
| EPS-09 | Mark all listened for selected podcast | One backend operation marks only that podcast, removes its playlist rows, clears its active episode, and returns `markedEpisodes` | C,U,E | Verified |
## P1 — episode actions and local playlist state

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| EPS-01 | Use an episode action in Subscriptions | Allowed actions are shown inline on the episode card (Add/Remove from playlist, Mark listened/unlistened, Show notes) | U | Verified |
| EPS-02 | Add an episode to playlist | Room DB `playlist_items` table is updated; row/count/menu update to In playlist / Remove | C,U,E | Verified |
| EPS-03 | Add to playlist fails | Optimistic UI rolls back target episode and error is surfaced | C,U,E | Verified |
| EPS-04 | Remove a non-active episode from playlist | Room DB removes item; unrelated playback is uninterrupted | C,U,E | Verified |
| EPS-05 | Remove the active episode from playlist | Room active state and Player reconcile without stale playback or unintended autoplay | C,U,E | Verified |
| EPS-06 | Mark an episode listened | Room DB marks it listened, removes it from playlist, triggers Smart Listening audio file cleanup, UI reconciles | C,U,E | Verified |
| EPS-07 | Mark a listened episode unlistened | Room DB changes episode to unlistened; not silently re-added to playlist; deleted media is not restored | C,U,E | Verified |
| EPS-08 | Mark listened/unlistened database error | Target state rolls back and Room DB state remains consistent | C,U,E | Verified |
| EPS-09 | Mark all listened for selected podcast | Single atomic Room DB transaction marks all episodes of that podcast listened and cleans playlist | C,U,E | Verified |
| EPS-10 | Repeat Mark all listened | Repeat succeeds idempotently with zero mutations | C,U,E | Verified |
| EPS-11 | Open Show notes with episode description | Notes render sanitized HTML/text in a scrollable modal | C,U,E | Verified |
| EPS-12 | Open Show notes when notes are absent | Truthful empty-notes state opens instead of a broken or blank modal | C,U,E | Verified |
| EPS-13 | Tap a link in Show notes | The URL opens through the Android system browser | U,E | Verified |

## P1 — Player, queue, and playback interaction

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| HOM-01 | Open Player while queue loads | Loading is visible; Room Flow delivers queue items reactively | U,E | Verified |
| HOM-02 | Open Player with no subscriptions | No-podcast state offers Add RSS and Import OPML | U,E | Verified |
| HOM-03 | Open Player with subscriptions but an empty playlist | Empty-playlist state is distinct from no subscriptions and navigation remains usable | U,E | Verified |
| HOM-04 | Open Player with a queue and no active episode | First queue item is displayed without autoplay | C,U,E | Verified |
| HOM-05 | Open Player with saved active playback | Correct episode and saved position restore from Room DB without autoplay | C,U,E,L | Verified |
| HOM-06 | Tap a queue row | That episode becomes active and starts playing via Media3 | C,U,E | Verified |
| HOM-07 | Use a Player queue item action | Play/Pause and Remove from playlist are available inline on the queue item | U | Verified |
| HOM-08 | Long-press and drag a queue row | Visible order and Room DB `playlist_items` order change together atomically via `@Transaction` | C,U,E | Verified |
| HOM-09 | Queue reorder fails | UI returns to previous DB order and shows a truthful error | C,U,E | Verified |
| HOM-10 | Local DB updates from background Smart Listening or feed refresh | Player reconciles reactively via Room Flow without duplicate/stale rows | C,E,L | Verified |
| HOM-11 | Use Player after its queue becomes empty | Player and active state clear; no stale playable card remains | C,U,E | Verified |

## P0/P1 — media playback and lifecycle

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| PLY-01 | Tap Play on the displayed episode | Real audio starts via Media3 ExoPlayer, button changes to Pause, active episode persisted | C,U,E,D | Verified |
| PLY-02 | Tap Pause | Audio stops, position is retained and synced to Room DB, Resume continues same episode | C,U,E,D | Verified |
| PLY-03 | Tap rewind 10 or forward 15 | Player seeks by the requested amount within valid bounds and updates playback position | C,U,E | Verified |
| PLY-04 | Tap or drag the progress track | Playback moves to the absolute selected position and stores accepted position | C,U,E,D | Verified |
| PLY-05 | Seek backward / forward near boundary | UI previews drag and dispatches single final seek position upon release | C,E | Verified |
| PLY-06 | Change playback speed | Each supported value 0.5/0.75/1/1.3/1.5/2 takes effect in ExoPlayer and persists to DataStore | C,U,E | Verified |
| PLY-07 | Relaunch with a saved playback speed | Confirmed speed is restored before playback starts | C,E,L | Verified |
| PLY-08 | Play continuously | Left label shows elapsed time, right label shows remaining time as `max(duration - position, 0)` | C,U,E | Verified |
| PLY-09 | Finish an episode naturally | Media3 natural completion marks episode listened in Room, removes from playlist, triggers Smart Listening cleanup, and auto-starts next eligible item | C,E,D | Verified |
| PLY-10 | Pause or seek inside the final 15 seconds | Position is stored as progress; episode is not prematurely marked listened | C,E | Verified |
| PLY-11 | Finish the last playlist item | Completed item is marked listened; playback stops cleanly and queue becomes empty | C,E | Verified |
| PLY-12 | Playback state persistence | Active episode and position persist to Room DB on pause/stop and survive process recreation | C,E,L | Verified |
| PLY-13 | Guard against duplicate completion | Guard prevents race conditions when natural completion and UI mark-listened coincide | C,E,L | Verified |
| PLY-14 | Audio stream fails before or during playback | Player shows a recoverable error banner; user can retry | U,E,L,D | Verified |
| PLY-15 | Another app requests audio focus | Playback pauses/ducks according to Android audio focus rules | E,L,D | Verified |
| PLY-16 | Headphones/Bluetooth route disconnects | Playback pauses immediately on noisy route event without continuing through speaker | E,L,D | Verified |
| PLY-17 | Background, lock screen, notification controls | Media notification and lock screen show episode metadata and Play/Pause control only | E,L,D | Verified |
| PLY-18 | Service/app process is stopped during playback | On next launch, local state restores predictably without autoplay | C,E,L,D | Verified |
| PLY-19 | Media playback via proxy | Audio streams correctly when HTTP or SOCKS5 proxy is configured in Settings | C,E,L | Verified |

## P1 — Smart Listening and local file lifecycle

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| DLD-01 | Smart Listening auto-download unlistened episodes | Downloads latest unlistened episodes up to configured limit per podcast | C,U,E | Verified |
| DLD-02 | Smart Listening Wi-Fi constraint | Downloads occur only on unmetered/Wi-Fi connection when Wi-Fi only setting is enabled | C,U,E | Verified |
| DLD-03 | Smart Listening download failure | Transient failure is caught without corrupting database or blocking other downloads | C,U,E | Verified |
| DLD-04 | Play an episode with local download | Playback uses downloaded local audio file directly | E,D | Verified |
| DLD-05 | Mark a downloaded episode listened | Downloaded audio file is deleted if auto-delete setting is enabled | C,E | Verified |
| DLD-06 | Unsubscribe podcast with downloaded episodes | Downloaded audio files for that podcast are cleaned up from storage | C,E | Verified |
| DLD-07 | Audio file extension derivation | Downloaded file extension is derived from feed enclosure URL (e.g. .mp3, .m4a) | C,E | Verified |

## P1 — Settings, DataStore preferences, and OPML export

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| SET-01 | Open Settings | Theme, Smart Listening toggles, Proxy configuration, OPML Import/Export, and Build Info are rendered | U,E | Verified |
| SET-02 | First install follows system theme | App respects Android system night mode by default | C,U,E,D | Verified |
| SET-03 | Toggle Theme (System / Light / Dark) | Theme updates immediately across all screens and persists to DataStore | C,U,E,D | Verified |
| SET-04 | Configure Smart Listening settings | Enable/disable toggle, max episodes per podcast (1–10), and Wi-Fi only persist to DataStore | C,U,E | Verified |
| SET-05 | Configure Proxy (Direct / HTTP / SOCKS5) | Proxy type, host, and port persist to DataStore and configure OkHttp client factory | C,U,E | Verified |
| SET-06 | Proxy validation | Invalid host or port inputs are validated locally before saving | C,U,E | Verified |
| SET-07 | Export OPML to file | Opens Android document picker and writes valid OPML XML with all subscriptions | C,U,E | Verified |
| SET-08 | Cancel OPML export picker | Cancellation is a no-op without false error | U,E | Verified |
| SET-09 | Export file write failure | Specific recoverable error is displayed | C,U,E,L | Verified |
| SET-10 | View application version | Settings displays `Current app build: <versionName>` derived from `BuildConfig.VERSION_NAME` | U,E,R | Verified |
| SET-11 | Toggle 48dp MpodSwitch | Switch toggle meets 48dp accessibility touch target | C,U | Verified |

## P0/P1 — cross-cutting reliability and delivery

| ID | User scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| REL-01 | Rotate during modal or input entry | Entered RSS URL, OPML picker state, and modal visibility survive configuration change | U,L | Verified |
| REL-02 | Room database concurrency & transaction integrity | Multi-table mutations (reordering, unsubscribe, mark all listened) use `@Transaction` | C,E,L | Verified |
| REL-03 | Feed XML parsing resilience | Corrupted/malformed dates, HTML entities, and encoding variations parse safely without crash | C,U,E | Verified |
| REL-04 | Network offline / timeout during feed refresh | Graceful network error handling without wiping existing Room DB data | C,E,L | Verified |
| REL-05 | Process recreation with background playback | Foreground `PlaybackService` maintains MediaSession and audio continuity | C,E,L,D | Verified |
| REL-06 | Scrolling large subscription and episode lists | Smooth scrolling with LazyColumn and memoized state | U,E,D | Verified |
| REL-07 | Build release APK (`com.prod.mpod`) | Minified release APK with ProGuard rules for Room, Hilt, Media3, and Coroutines | C,R,D | Verified |

## Resolved scenario decisions

The product owner confirmed on 2026-07-19:

1. OPML partial success is shown inside the existing import modal as `Import completed`, exact imported/skipped counts, and `Done`; do not stack another dialog over the modal.
2. Links in Show notes are tappable and open through the Android system browser.
3. Android media notification and lock-screen controls expose Play/Pause only, together with episode/podcast metadata.
4. Downloads have no user-facing Cancel action in the MVP. An interrupted request either completes or returns to a retryable Download state without false success.
5. Settings has no Retry buttons. The header shows last refresh and current IP/geo when available; Feed daily refresh and SOCKS5 expose independent backend errors; local sections stay usable. Re-entering the screen or restarting the application reloads the backend-dependent data.
6. Web/Android synchronization is event-driven for the MVP: launch, foreground, entry to Home or Subscriptions, and manual Refresh reconcile shared state. There is no continuous polling and no immediate interruption of current audio before reconciliation.
7. Downloads are explicitly deferred from the current release acceptance pending redesign. Until that redesign, the existing Android behavior permits one download at a time and disables other Download actions while it is running; `DLD-01`–`DLD-09` must not be represented as Verified.
8. Release acceptance uses one release APK. A separate test application, a second application ID, Test/Production coexistence, and upgrade/co-installation checks are not mpod requirements. After all PRD scenarios and regression tests pass, release is switched to production server `5050`, assembled, and smoke-tested for login, subscriptions, playback, speed, episode completion, Settings, MediaSession, and background playback. With no critical defects, the APK is ready for release.
9. Settings displays only the user-facing application version (`Current app build: <versionName>`). Android `versionCode` remains an internal monotonically increasing update number and is not shown together with environment, package, server, or backend metadata. Every new production APK installed for product-owner testing or handed off increments the patch `versionName` and increases `versionCode` by one; neither value is reused.
10. The first bottom-navigation item is labeled `Player`, uses the updated Figma icon, and continues opening the existing Home/Now playing destination; it is not a new route or screen.
11. Playback completion is explicit. Ordinary progress, including a pause or seek inside the final 15 seconds or at the reported duration, never marks an episode listened and never removes it from the playlist. Android sends `completed: true` only from Media3 natural-completion events.
12. When completion of the last playlist item returns `nextEpisodeId`, Android starts that backend-selected episode. Existing playback state is resumed; if no playback state exists, playback starts at `0:00`.
13. Player time labels are explicit: the left label is elapsed playback position; the right label is remaining time calculated as `max(durationSeconds - positionSeconds, 0)`. The right label is not the episode's fixed total duration.
14. Episode actions are inline on the mobile Player playlist item and Subscriptions episode card. The episode action bottom sheet is not part of the current mobile UX. Bottom sheets remain for playback-speed selection; Add podcast remains a modal overlay/card flow.

There are no known unanswered product questions blocking the functional scenario audit.

## Backend follow-ups

1. `BE-FU-01`: during EV-W8, `DELETE /api/podcasts/23` returned success and removed the podcast, but `/api/playlist` retained episode `11764`; `/api/playback/queue` already filtered that orphan. The orphan made the next full reorder fail with `INVALID_PLAYLIST_ORDER` until the row was explicitly deleted. Backend podcast deletion must remove every affected playlist row in the committed database state.

`BE-FU-02` is resolved by backend commit `6c0ce47`: completion side effects now require explicit `completed: true`.

## Verification ledger

This ledger records why scenario statuses changed. Git remains the change history for the document itself.

| Evidence ID | Date | Scope | Result/source |
|---|---|---|---|
| EV-4.1 | 2026-07-16 | `APP-01`, `APP-02`, `APP-06`–`APP-10` | Stage 4.1 real test-backend session/startup matrix, lifecycle checks, and auth contract/Compose evidence recorded in `docs/android-delivery-plan.md` |
| EV-4.2 | 2026-07-16 | `ADD-02`–`ADD-05`, `SUB-05`–`SUB-11` | Stage 4.2 temporary RSS fixture, real backend refresh success/failure/recovery, filters, and artwork success/fallback |
| EV-4.3 | 2026-07-16 | `EPS-01`–`EPS-04`, `EPS-09`, `EPS-11`, `HOM-08`, `HOM-09` | Stage 4.3 three-episode fixture, authoritative playlist/mark-all/show-notes results, real drag reorder, and offline rollback |
| EV-4.4 | 2026-07-16 to 2026-07-19 | `HOM-04`, `HOM-05`, `PLY-03`, `PLY-06`–`PLY-08`, `PLY-12`; historical evidence for the retired threshold contract | Stage 4.4 authenticated MP3 fixtures, durable sync recovery, seek/speed/progress evidence, and the backend completion rule then in force; commits `0f1a0dc` and `47c73f0`. Current explicit-completion evidence for `PLY-10`/`PLY-11` is recorded in `EV-M3` |
| EV-PROD | 2026-07-19 | `REL-10` baseline evidence only | Minified release startup defect fixed in commit `d755f99`; the release build reached production server `5050`. The row remains Specified until the complete approved production smoke path is executed |
| EV-PROD-PREFLIGHT | 2026-07-26 | `REL-10` partial preflight | Release APK built from Android HEAD `8bdb68d`, package `com.prod.mpod`, version `1.0.11 (12)`, min SDK 34, target SDK 36, size 5,879,637 bytes, SHA-256 `d07037fe533835f9cf3a9c7322424164230969d5010bc9ba28e56fcee259da57`. Static inspection found production `192.168.0.222:5050` and no `5051`. APK Signature Scheme v2 verification passed for the local-network acceptance artifact. The APK installed on the physical phone, reported the expected package/version, reached production Login, and therefore established `5050` connectivity. Authenticated production smoke remains required before `REL-10` can be promoted. |
| EV-W1 | 2026-07-19 | `APP-05`, `NAV-01`–`NAV-05` | Blank Login/Setup dispatch tests; all-destination bottom-nav test; real `5051` login to Subscriptions; Home/Settings/Add navigation; system Back; background restore; and process recreation on Pixel 9. Full gate: 94 unit, 44 connected, debug/release lint and APK assembly |
| EV-W1-PARTIAL | 2026-07-19 | `APP-12` remains Specified; partial evidence for `APP-03`, `APP-04`, `APP-11` | Isolated HTTP connected tests protected `setupRequired → register` and the initial failed-logout recovery path; backend router tests protected real first setup. Shared `5051` was not reset or forced to fail at this point, and `APP-12` still required its final release/device backup smoke check. `APP-11` was subsequently promoted by `EV-W14`; `APP-03` and `APP-04` were completed on an isolated real backend by `EV-W16` |
| EV-W2 | 2026-07-19 | `ADD-01`, `ADD-06`–`ADD-12` | Compose/ViewModel and multipart contract coverage plus real Pixel 9 emulator checks against `5051`: mode switching without submission; picker cancellation; mixed OPML result `1 imported / 1 skipped`; repeat result `0 / 2`; local 5,000,001-byte rejection; invalid-OPML error followed by successful retry; and double-submit plus background/restore during a five-second RSS request producing exactly one subscription. Process loss while the document picker was open produced no crash, import, or false result. Temporary subscriptions were removed. Full gate: 94 unit, 48 connected, debug/release lint and APK assembly |
| EV-W3 | 2026-07-19 | `SUB-13`, `SUB-15`–`SUB-17` | Real `5051` checks with a temporary feed: a slow Refresh all stayed visibly running and non-repeatable across background/restore until backend completion; failed final unsubscribe during a connectivity interruption kept the podcast visible and `Try again` repeated DELETE immediately and removed only that podcast. Earlier real Undo/final-countdown evidence remains applicable. A lifecycle reload bug that cleared active mutation guards and an incorrect unsubscribe Retry route were fixed. Full gate: 95 unit, 53 connected, debug/release lint and APK assembly |
| EV-W3-PARTIAL | 2026-07-19 | Historical partial evidence for `SUB-01`–`SUB-04`, `SUB-12`, `SUB-14` | Compose/state tests protected loading without mutation actions, load-error Retry, both empty-library add paths, distinct caught-up/Show all behavior, refresh failure UI, and scoped episode failure with another usable podcast. The remaining evidence was subsequently completed by `EV-W13`, `EV-W15`–`EV-W18` |
| EV-W5 | 2026-07-19 | `SET-01`–`SET-09` | Independent refresh/proxy loading and failure instrumentation; unchanged-save suppression; failed-save exact retry; confirmed-save/status-failure retention; unconfigured/off/running/unknown/error proxy states; Material 12-hour picker cancel; real Pixel 9 `04:00 → 04:05 → 04:00` save/restore; real proxy `on → off → on`; re-entry reload and empty crash buffer. Full gate: 97 unit, 74 connected, debug/release lint and APK assembly |
| EV-W6 | 2026-07-19 | `SET-12`–`SET-15` | Android provider success/cancel/HTTP failure/write failure/duplicate-submit/resume-race instrumentation; real DocumentsUI save produced `mpod-subscriptions.opml`, not `.opml.xml`; its 269 bytes matched the authenticated `5051` response exactly and parsed as XML. Test UI displayed version/code, Test, package, `5051`, and backend commit; unit mapping covers the production package and the minified release APK compiled successfully. Full gate: 99 unit, 81 connected, debug/release lint and APK assembly |
| EV-W6-PARTIAL | 2026-07-19 | `SET-10`, `SET-11` remain Specified | Pixel 9 emulator clean-data launch followed system Dark; explicit Light and Dark each survived force-stop, and the emulator/test data were restored to system Light/System. Physical-device evidence is intentionally deferred to the final phone pass, so these rows were not promoted |
| EV-W7 | 2026-07-21 | `HOM-10`, `SYN-01`–`SYN-04` | Pixel 9 and real `5051` multi-client checks: background queue reorder and speed change were applied on foreground while the valid active episode kept playing; foreground backend changes caused no immediate interruption or polling update, then entering Subscriptions applied both; externally marking the active episode listened cleared backend active/queue state and Android reconciled to the next episode paused without autoplay. Android now reconciles backend speed with queue invalidations from Home and Subscriptions while preserving a pending local speed write. Backend queue `[16,18,26]`, null active, speed `1.3x`, listened flags, and playback position were restored. Full gate: 101 unit, 82 connected, debug/release lint and APK assembly |
| EV-W8 | 2026-07-21 | `PLY-11`, `PLY-13`, `PLY-19`; partial `PLY-09` | Authenticated 20/60/20-second MP3 fixture on Pixel 9 and real `5051`. Online natural completion moved from A to B playing. With network denied only to mpod, B started from buffer while backend remained active A and A completion persisted on disk; after recovery pending cleared, backend removed A and selected B, and MediaSession continued B instead of being hijacked. A race that classified a playing request from post-response state was fixed by retaining submission-time state. Finishing sole C cleared backend active/queue and MediaSession; a stale Home card exposed and fixed missing service-to-Home completion invalidation. Redundant paused reconciliation writes are suppressed. Temporary podcasts were removed and Planet Money queue `[16,18,26]`, null active, positions `51/242`, and speed `1.3x` were restored. `PLY-09` remains Specified only because its required physical-phone evidence is deferred. Full gate: 103 unit, 83 connected, debug/release lint and APK assembly |
| EV-W9 | 2026-07-22 | `REL-03` | A real persisted Android session was invalidated through `POST /api/auth/logout` on test backend `5051` while Settings remained open. Saving a locally selected `04:05` received an authenticated `401`; Android immediately stopped playback, cleared the persisted cookie, replaced the authenticated shell with Login, and did not retain the failed mutation. An independent authenticated backend session confirmed `dailyRefreshTime` remained `04:00`. Login succeeded again afterward and the crash buffer was empty. Policy, real-interceptor, launch-navigation, and cookie-clearing tests protect the path. Full gate: 105 unit, 87 connected, debug/release lint and APK assembly |
| EV-W9-PARTIAL | 2026-07-22 | `REL-13` remains Specified | The shared core client now has one explicit 30-second call deadline with matching connect/read/write limits. Instrumentation verifies the production client values and a controlled delayed Settings save verifies truthful timeout termination, duplicate-submit blocking, unchanged confirmed state, and successful retry. A real 30-second `5051` path was not manufactured by pausing or disrupting the shared backend, so the required E2E evidence is still incomplete and the row was not promoted |
| EV-W10 | 2026-07-22 | `REL-04`, `REL-05`; partial `REL-13` | All response roots used by startup, Home, Subscriptions, Settings, OPML, playback queue, and playback-sync confirmations now distinguish a missing payload from a valid empty value. Structured backend errors remain user-facing, malformed/empty 2xx responses cannot invent successful state, and retries preserve authoritative data. Pixel 9 through an isolated QA proxy showed the exact structured `503` message, a stable `Could not load podcasts.` result for malformed `200`, and successful Home recovery through Retry without changing the shared backend. Existing real offline/slow/lifecycle evidence plus the controlled 30-second client, delayed-save termination, duplicate-write blocking, and recovery cover `REL-05`. Playback sync keeps malformed confirmations retryable. The proxy stopped receiving emulator traffic during the attempted manual timeout pass, so that attempt is not counted as E2E evidence and `REL-13` remains Specified. Full gate: 109 unit, 91/91 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W11 | 2026-07-22 | `REL-01`, `REL-02`, `REL-06` | Busy state for login/register, Add podcast/OPML, refresh-all/per-podcast refresh, unsubscribe, Mark all listened, and Home reorder is now claimed synchronously before coroutine dispatch, so immediate duplicate calls cannot enter the request queue. Add podcast mode and RSS draft are owned by an activity-scoped ViewModel and `SavedStateHandle`; Settings draft time and open picker use saveable state. State-restoration and delayed-request tests cover drafts, modal intent, no false submission, and exactly-one dispatch. On Pixel 9 using the current `com.prod.mpod.test` package, RSS draft, OPML mode, and the open TimePicker survived real rotation without submission/save. Force-stop at `13 sec` in the Planet Money unsubscribe Undo window, followed by a wait past the original deadline and relaunch, kept `1 podcast`, restored no stale Undo, and dispatched no delayed deletion. Earlier observations from the unrelated legacy `com.example.mpod` package were discarded. Full gate: 109 unit, 99/99 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W12 | 2026-07-22 | `PLY-05`; partial `PLY-01`, `PLY-02`, `PLY-04` pending physical-phone evidence | The current `com.prod.mpod.test` build played real Planet Money audio, changed Play to Pause after buffering, paused at `2:03` with backend position `123`, and resumed the same episode. A 75% tap moved to `21:29` and stored `1283`. A real backward drag exposed that the progress bar dispatched a seek for every pointer event, serializing many playback writes and leaving backend position stale. The bar now previews drag locally and emits exactly one final seek on release; connected tests require one callback for tap and drag. Repeating the 75% → 40% drag while paused moved UI to `11:24` and backend to `684`. Rewind 10 showed `0:41` locally while backend correctly retained `51` under the documented sub-30-second rule; force-stop/relaunch restored `0:51` without autoplay. Position was returned to `51` after QA and the crash buffer was empty. `PLY-01`, `PLY-02`, and `PLY-04` retain complete contract/UI/emulator evidence but remain `Specified` because their rows explicitly require the deferred physical-phone check. Full gate: 109 unit, 99/99 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W13 | 2026-07-26 | `SUB-01`; partial `REL-07` pending physical-phone evidence | A cold real login on the current `com.prod.mpod.test` build against `5051` visibly passed through `Loading subscriptions`; the frame contained no Refresh, Unsubscribe, Mark all listened, or episode actions, then resolved to the authoritative one-podcast library. The connected loading-state test independently proves those mutation actions are absent. For `REL-07`, a new connected regression renders 80 unlistened episodes, scrolls semantically to the last long-titled row, and proves Add to playlist dispatches only episode `1080`. On Pixel 9, the real 350-row unlistened Planet Money list was deeply scrolled to the 75-character episode `Everything’s more expensive!! Pet care!! Concert tickets!! (Two Indicators)` (`episodeId=73`). Its options remained reachable; Add to playlist produced exact backend order `[16,18,26,73]`, the same row immediately exposed Remove from playlist, and removal restored `[16,18,26]`. No unrelated backend state changed, emulator network conditioning was restored to normal, and the crash buffer was empty. `REL-07` remains `Specified` because its row explicitly requires the deferred physical-phone check. Full gate: 109 unit, 100/100 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W14 | 2026-07-26 | `APP-11` | Connected launch tests cover both `503` and a refused logout connection. They immediately call Logout twice to prove one HTTP mutation, preserve the unavailable state, and recover through an authoritative authenticated session response after the test server returns. On Pixel 9 against real `5051`, the emulator network was disabled only after authenticated Settings had loaded. Logout showed `mpod is not reachable` rather than Login, retained the non-empty persisted cookie store, and exposed Retry. After network restoration, Retry returned directly to the authoritative one-podcast Subscriptions screen without credentials. Emulator connectivity was restored and the crash buffer was empty. Full gate: 109 unit, 101/101 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W15 | 2026-07-26 | `SUB-02` | A connected ViewModel regression starts with a structured initial `503`, proves that no library is invented, retries the exact load, and reaches the authoritative podcast/episode state. The existing Compose test performs the visible `Try again` gesture. On Pixel 9, a host-only HTTP proxy passed authentication and every unrelated request through to real `5051` while returning `503 Controlled backend failure` only for the initial `GET /api/podcasts`. Subscriptions showed that exact error and `Try again`, never exposed mutation actions, then the real gesture after passthrough restoration loaded the authoritative one-podcast Planet Money library without restarting or changing backend state. The emulator proxy was cleared, direct backend access restored, and the crash buffer was empty. Full gate: 109 unit, 102/102 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W16 | 2026-07-26 | `APP-03`, `APP-04`, `SUB-03`, `HOM-02` | A clean temporary SQLite database and the actual parent-project backend ran on isolated port `15051`; a host-only proxy routed the test APK's expected `5051` address to that instance without touching shared state. The real session response reported `setupRequired:true`; Pixel 9 showed Setup with no Login substitute. One Create account gesture produced exactly one `POST /api/auth/register`, one user, one session, a non-empty persisted cookie store, and authenticated Subscriptions. The backend remained at zero podcasts. Both Subscriptions and Home showed the distinct no-podcast state; Add RSS feed opened the RSS mode and Import OPML opened the OPML mode from each screen without submission. Connected coverage immediately invokes Register twice and requires one request, while existing Home/Subscriptions Compose tests dispatch both empty-state actions. The isolated backend/proxy were stopped, its cookie/app data cleared, direct `5051` connectivity restored, and the crash buffer was empty. Full gate: 109 unit, 102/102 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W17 | 2026-07-26 | `SUB-04`, `SUB-14` | The actual backend ran on a new temporary database with two locally served RSS feeds. A host-only proxy returned a controlled `503` only for Alpha's episode endpoint while every other request reached the isolated backend. Pixel 9 showed the failure on Alpha's card and its scoped episode message; swiping to Beta still showed the real healthy episode and actions. After passthrough restoration, the visible Try again reloaded both podcasts, removed the global/scoped errors, and restored Alpha's episode without losing Beta. A new connected ViewModel regression preserves the same partial state and exact recovery. Both backend podcasts were then authoritatively marked listened; after a real force-stop/relaunch, Subscriptions displayed `All caught up`, not the empty-library state. Tapping Show all restored the listened Alpha episode and changed the header action to Show unlistened. Existing contract/Compose coverage protects filtering and the gesture. The isolated backend, RSS server, and proxy were stopped, test app data cleared, direct `5051` restored, and the crash buffer was empty. Full gate: 109 unit, 103/103 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W18 | 2026-07-26 | `SUB-12`, `REL-13` | An isolated actual backend with two controlled RSS feeds accepted Refresh all, kept the library usable while the async job ran, and entered its real retry path when Beta failed. Unit polling coverage and a connected terminal-job regression require the backend `failed`/`lastError` result to stop the spinner, expose the exact job error, retain the loaded podcast and episode, and dispatch one refresh job. Auditing the initial authenticated load exposed duplicate Home/Subscriptions request chains caused by `init` plus the first lifecycle resume; both ViewModels now claim a synchronous in-flight guard, with immediate-double-call regressions. A host-only proxy then delayed the real test backend's single `GET /api/podcasts` for 35 seconds. Cold launch plus background/foreground stayed on `Loading subscriptions` with no mutation actions and did not issue another request; the 30-second client deadline produced `Could not load subscriptions.` and `Try again`. Returning the proxy to passthrough and tapping that UI-derived action restored the authoritative one-podcast Planet Money library. The proxy and test data were cleared, direct `5051` restored, and the crash buffer was empty. Full gate: 109 unit, 106/106 connected, debug/release lint, debug/test APKs, and minified release APK |
| EV-W19 | 2026-07-26 | `PLY-01`, `PLY-02`, `PLY-04`, `PLY-09`, `PLY-17`, `PLY-18`, `SET-10`, `SET-11`, `REL-07` | Physical Xiaomi `23021RAA2Y` on Android 15 ran the current test APK against `5051`. System Dark was followed on a clean install; explicit Light and Dark each persisted through force-stop. Real Planet Money playback changed Play/Pause and MediaSession state, retained/synced position, and a 75% progress tap stored the authoritative `1299`. Natural completion marked episode 16 listened, removed it from the queue, and auto-started episode 18. Background and lock-screen metadata remained correct. The first phone pass exposed Previous/Next controls that violated the Play/Pause-only decision; MediaSession commands are now restricted for the notification controller, a connected regression protects the command set, and the rebuilt APK showed only Pause in both notification and lock screen. Stopping the service/app after confirmed progress restored episode 18 at the backend-confirmed position, paused and without autoplay. The real 360-row subscription list was deeply scrolled; the options menu remained reachable for `Strange threadfellows: How the U.S. military shaped what we all wear` and exposed the intended episode actions. Audio-focus and noisy-route rows remain open until their dedicated physical checks are conclusive. Full gate: 109 unit, 107/107 connected, debug/release lint, debug/test APKs, and minified release APK. |
| EV-W20 | 2026-07-26 | `PLY-14` | A temporary LAN proxy accepted traffic only for test backend `192.168.0.222:5051` and returned controlled `503` responses only for episode audio. On the physical Android 15 phone, a cold player at confirmed `9:18` made four real attempts for episode 18, then Home showed `Could not play this episode. Check its audio source and try again.`, Play remained available, and MediaSession reported a source error at the retained position. After the proxy returned to passthrough, the real Play gesture on the shifted error-state UI cleared the error and resumed the same episode at 1.3x without restart. Backend remained active on episode 18 with queue `[18,26]`; only ordinary played progress advanced. The phone proxy was cleared and the restricted process was stopped. |
| EV-W21 | 2026-07-26 | `APP-12`, `PLY-15` | On the physical phone, Logout removed mpod's MediaSession and opened Login. A force-stop/cold relaunch stayed on Login. Clearing the test package and launching cold again also stayed on Login; no cookie/session preferences returned, while the backup/transfer XML exclusion remains protected by connected coverage. Test login was then restored with the agreed `t/123`. For audio focus, mpod played episode 18 while Chrome opened a LAN-only 20-second WAV fixture. A real Chrome Play gesture acquired full `AUDIOFOCUS_GAIN`; Chrome MediaSession became playing and mpod immediately became paused at the retained position with no error. Pausing Chrome and returning to mpod did not incorrectly auto-resume after that permanent focus loss. The temporary audio server was stopped; Chrome itself was not force-stopped. |
| EV-W22 | 2026-07-26 | `PLY-16` | On the physical Android 15 phone, `HL S3` was the active A2DP route, Bluetooth reported `mIsPlaying:true`, STREAM_MUSIC selected `bt_a2dp`, and mpod MediaSession played episode 18 at 1.3x. The product owner physically disconnected the headphones. Bluetooth became disconnected with no active device and STREAM_MUSIC returned to speaker, but mpod immediately entered PAUSED at retained position `799174`; Home showed Play and no player error. Audio therefore did not continue unexpectedly through the speaker and the player remained recoverable. |
| EV-W23 | 2026-07-26 | `REL-10` | The minified `com.prod.mpod` release ran the approved authenticated smoke path on Xiaomi `23021RAA2Y`, Android 15, against production `5050`. Login restored the real eight-podcast library; Home restored the four-item queue and 1.3x speed. Subscriptions, Settings refresh/proxy/theme data, playback, speed change/restore, MediaSession, and background playback passed. Sustained playback exposed an R8-only crash: Gson lost the anonymous generic `TypeToken`, pending playback reloaded empty, and `submitPlayback` dereferenced the missing value. Persistence now deserializes a concrete array and sync falls back to the submitted request; the rebuilt minified APK played through repeated 15-second sync intervals without a new crash. Production also exposed that restricting the notification controller did not restrict DefaultMediaNotificationProvider actions for a multi-item queue; a provider-level filter now publishes exactly one Play/Pause action. Completing `Children in warzones` removed it from the backend queue, changed the count from four to three, and selected `Podlodka #486 – Spec-Driven Development` next without autoplay. Full gate after the fixes: 110/110 unit, 107/107 connected, debug/release lint, debug/test APKs, and minified release APK. `REL-10` is Verified; `REL-12` remains for immutable artifact metadata and handoff. |
| EV-W24 | 2026-07-26 | `REL-12` | Final acceptance artifact `app/build/outputs/apk/release/app-release.apk` was rebuilt from Android revision `67ad83ff3c650b830bb8b1b3d58aadaf83e7bf82`, installed successfully on the physical phone, retained the authenticated production session, and reopened the authoritative subscription state. Package `com.prod.mpod`, version `1.0.11 (12)`, min SDK 34, target SDK 36, size 5,879,637 bytes, SHA-256 `a693235de4645ae925d9be9e198ea75e4865c0b249bd0b521338b97d65b60e44`, production endpoint `192.168.0.222:5050`, backend baseline `ac8a679f3dd38cbd800cb535f3b7eff5bc61b312`, APK Signature Scheme v2. The local-network release setup is accepted for the current scope; `DLD-01`–`DLD-09` are explicitly deferred pending redesign. All 118 in-scope scenarios are Verified and nine are Deferred; no scenario remains Specified, Open, or Failed. |
| EV-M1 | 2026-07-27 | Maintenance evidence for `SUB-07`, `SUB-08`, `SET-01`, `SET-15`, `NAV-02`, `HOM-10`, `PLY-17`, and `SYN-01`–`SYN-04` | Product-owner review exposed authenticated artwork failures, UTC scheduler display, excess Settings build metadata, navigation jank, and an audible interruption when entering Home/Subscriptions. Commit `7810855` uses the backend image endpoint with the authenticated shared image loader, formats scheduler instants in the device zone, and shows only `Current app build`. Commit `a72145b` skips ExoPlayer queue rebuild/prepare when queue and active item are unchanged, moves subscription loading off the UI dispatcher, removes unnecessary navigation transitions, memoizes visibility filtering, and preserves loaded Home content during refresh. Full gate: 117/117 unit, 107/107 connected, debug/release lint, debug/release APK assembly. Pixel 9 emulator retained a continuously `PLAYING` MediaSession with advancing position across repeated Home/Subscriptions/Settings transitions. The product owner reviewed the corrected application and reported the changes acceptable. This maintenance evidence does not replace the wave-24 production artifact metadata or checksum. |
| EV-M2 | 2026-07-28 | Build/release maintenance evidence for `REL-12` | GitHub automation was consolidated into one Release workflow using JetBrains Runtime 21. It preserves unit tests, Android-test APK compilation, Debug/Release lint, reports, and minified Release assembly while taking `versionName` and `versionCode` from Gradle. The separate quality/emulator workflow was removed by product-owner decision; connected device tests remain a local/manual gate. Production no longer constructs OkHttp BASIC request logging, and focused tests retain that logging only for Debug. The exact workflow commands passed locally under Android Studio JBR 21: 119/119 unit tests, Android-test APK compilation, Debug/Release lint, and Release assembly. Static artifact inspection confirmed package `com.prod.mpod`, version `1.0.12 (13)`, no debuggable manifest flag, and no `HttpLoggingInterceptor` symbol after R8. This maintenance evidence does not replace the wave-24 acceptance APK revision or checksum. |
| EV-M3 | 2026-07-28 | Playback completion contract maintenance for `PLY-10`, `PLY-11` | Test backend `5051` was updated from stale build `e831ed9` to backend commit `6c0ce47`. A direct `completed:false` probe at 20/30 seconds retained the episode and position. Pixel 9 then paused a real fixture at 21/30; backend stored ordinary progress and kept it unlistened/in playlist. Natural completion of the last item consumed backend `nextEpisodeId`: a fallback without playback state started from zero, while a fallback with authoritative 120/600-second state initially exposed an Android bug by starting at zero. Queue target resolution now restores the backend saved position and the repeated E2E opened exactly at `2:00`. The same pass exposed Play/Pause using `isPlaying` during buffering; the UI and action now follow `playWhenReady`, so Pause remains truthful while buffering. Old client-side 15-second reconciliation was removed. Full gate: 121/121 unit tests, 107/107 connected Pixel 9 tests, Debug/Release lint, and minified Release assembly. Temporary fixture state was removed and the original test playlist/active state was restored. |
| EV-M4 | 2026-07-29 | Subscriptions continuity evidence for `SUB-18` | A session-scoped in-memory cache now distinguishes first load from confirmed empty state, renders cached subscriptions immediately during silent reconciliation, retains cached content on refresh failure, and clears on logout/login/register/session expiry. Focused recreation, failure, and session tests passed, followed by the full 122/122 unit and 109/109 connected gates, Debug/Release lint, and minified Release assembly. On Pixel 9, a two-second emulator network delay showed `Loading subscriptions` on cold process launch without `No podcasts`, then retained the one-podcast library during a delayed Settings → Subscriptions return. No production backend mutation was performed. |
| EV-STANDALONE | 2026-08-25 | `APP-01`–`APP-04`, `NAV-01`–`NAV-05`, `ADD-01`–`ADD-12`, `SUB-01`–`SUB-18`, `EPS-01`–`EPS-13`, `HOM-01`–`HOM-11`, `PLY-01`–`PLY-19`, `DLD-01`–`DLD-07`, `SET-01`–`SET-11`, `REL-01`–`REL-07` | Converted app into standalone player `mpoddy`. Replaced backend client with Room local DB (`MpodDatabase`), XML-based RSS/Atom feed parser (`RssFeedParser`), OPML import/export engine (`OpmlParser`), `SmartListeningManager`, and Jetpack DataStore preferences (`AppSettingsDataStore`). Removed all remote auth/session dependencies (commits `8a25520`, `5f72581`). |
| EV-HARDENING | 2026-08-27 to 2026-08-28 | `HOM-08`, `SET-03`, `SET-10`, `SET-11`, `ADD-04`, `REL-02`, `REL-03` | Hardening and bugfixes: atomic Room `@Transaction` for playlist reordering (`4130c7e`), RFC-822/ISO-8601 thread-safe date parsing with epoch fallback (`2825efd`, `4e8bc56`, `f54cd4e`), OkHttp client caching (`f54cd4e`), feed URL normalization for duplicate checks (`dd7aa5a`), DataStore theme migration (`ac5a9fd`, `0de5260`), 48dp `MpodSwitch` touch target (`345f347`), and JVM unit test suite (`bdf4d06`, `2a936b0`). |

## Execution order

After the product owner reviews this map, work proceeds in functional waves:

1. Audit P0/P1 scenario rows against existing evidence; do not rerun unchanged evidence without a dependency reason.
2. Execute unknown and high-risk scenarios end-to-end, recording `Verified`, `Failed`, and the exact evidence.
3. Fix failed scenarios in small scenario-scoped commits, then rerun that scenario and affected regression paths.
4. Run the cross-cutting reliability matrix.
5. Switch release configuration to production server `5050`, build one release APK, and perform the approved production smoke pass on the physical phone.

Each implementation batch ends with a scoped commit and report. The next batch does not start until approval, unless the product owner explicitly authorizes completing a whole named wave without intermediate confirmation.
