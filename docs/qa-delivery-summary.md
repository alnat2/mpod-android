# QA Bugfix Delivery — mpoddy Android

**Branch:** `codex/qa-obvious-bugs`
**Commit:** `f64c9c1`
**Date:** 2026-08-31
**Author:** opencode agent
**Reviewer:** Team Lead

---

## What was done

QA sweep of the mpoddy Android podcast player. The delivery remains under Team Lead review. A product-owner clarification made after the initial implementation removes all user-facing Smart Listening settings.

### Quality Gates

| Gate | Status |
|------|--------|
| `testDebugUnitTest` | ✅ GREEN |
| `lintDebug` | ✅ GREEN |
| `assembleDebug` | ✅ GREEN |
| `connectedDebugAndroidTest` | ✅ GREEN (52/52, 0 skipped) |

---

## Bugs Fixed

### Product clarification — Smart Listening is fully automatic
**Decision:** The absence of Smart Listening settings is not a defect. The feature is automatic and offers no enable/disable, per-podcast limit, Wi-Fi-only, or cleanup controls.
**Correction:** Removed the mistakenly added Smart Listening preferences, Settings card, policy checks, and `ACCESS_NETWORK_STATE` permission. Automatic scheduling and lifecycle cleanup remain internal behavior.

### BUG-02 (P2) — Auto refresh does nothing
**Problem:** Settings stored toggle/time but no scheduler existed. Feature was completely non-functional.
**Fix:** Created `AutoRefreshWorker` (WorkManager periodic 24h) + `AutoRefreshScheduler`. Wired in `SettingsViewModel.init{}`. Added `hilt-work` dependency. Disabled default WorkManager initializer.

### BUG-03 (P2) — Binary theme toggle, no System mode
**Problem:** Toggle only switched Light/Dark. System mode was lost after first toggle.
**Fix:** Replaced with 3-way segmented buttons: System / Light / Dark. Material 3 `SingleChoiceSegmentedButtonRow`.

### BUG-04 (P2) — Mark listened doesn't clean up files
**Problem:** `setEpisodeListened()` and `markAllListened()` in Subscriptions only flipped the Room flag. Downloaded files stayed on disk.
**Fix:** Both methods now call `smartListeningManager.cleanupEpisodeFile()` for each affected episode.

### BUG-05 (P3) — OPML import no file size limit
**Problem:** Oversized OPML files could cause memory pressure.
**Fix:** `importOpml()` reads bytes first, rejects files > 5MB with explicit error message.

### BUG-06 (P3) — Interrupted download leaves orphan file
**Problem:** Downloads wrote directly to final filename. Interruption left partial file.
**Fix:** Write to `.tmp` first, rename on completion. Interrupted downloads leave only the disposable `.tmp`.

---

## Improvements

| # | What | Change |
|---|------|--------|
| QA-01 | Duplicate RSS feed handling | Returns `Result.failure("This podcast is already in your library.")` instead of silently returning existing |
| QA-06 | HTTP proxy type selector | Added SOCKS5 / HTTP toggle in Settings. `PlaybackService` now reads proxy settings on startup. |
| Seek values | Corrected to PLY-03 spec | Rewind: -15 → -10, Forward: +30 → +15. Labels, content descriptions, callbacks all updated. |

---

## Files Changed (25)

### New files (4)
- `AutoRefreshScheduler.kt` — WorkManager scheduling logic
- `AutoRefreshWorker.kt` — HiltWorker for background refresh
- `AppSettingsTest.kt` — Download settings regression test
- `AutoRefreshSchedulerTest.kt` — Delay computation regression test

### Modified files (21)
- Manifest, build.gradle, libs.versions.toml
- SmartListeningManager, PodcastRepository, PlaybackService, NetworkModule
- AppSettingsDataStore, SettingsScreen, SettingsViewModel
- SubscriptionsViewModel, PlayerView, HomeScreen, MpodApplication
- ThemeModeTest, OpmlParserTest, ProxyHttpClientFactoryTest
- SettingsScreenTest, HomeScreenTest, AddPodcastModalTest
- QA bugfix report

---

## Tests Added/Updated

| Test | Type | Coverage |
|------|------|----------|
| `AppSettingsTest.kt` | Unit (new) | General settings defaults |
| `AutoRefreshSchedulerTest.kt` | Unit (new) | `computeDelayToNextRun()` edge cases |
| `ThemeModeTest.kt` | Unit (updated) | 3-way mode selector |
| `OpmlParserTest.kt` | Unit (updated) | Oversized OPML rejection |
| `ProxyHttpClientFactoryTest.kt` | Unit (updated) | SOCKS5 + HTTP proxy types |
| `SettingsScreenTest.kt` | Connected (updated) | Theme selector, proxy section |
| `HomeScreenTest.kt` | Connected (updated) | Seek values -10/+15 |
| `AddPodcastModalTest.kt` | Connected (updated) | Validation error timing |

---

## Remaining

| Item | Status |
|------|--------|
| QA-10: Player with real audio | Partially ready — fixture server has audio files at `http://localhost:8765/feed/playback`. Needs manual execution on emulator. |
| Push to main | ❌ NOT pushed. Awaiting your review. |

---

## How to verify

```bash
git checkout codex/qa-obvious-bugs
git log --oneline -1   # f64c9c1

# Run all gates
./gradlew testDebugUnitTest lintDebug assembleDebug

# Run connected tests (requires emulator)
./gradlew connectedDebugAndroidTest
```

---

*Ready for review. Do NOT merge to main until approved.*
