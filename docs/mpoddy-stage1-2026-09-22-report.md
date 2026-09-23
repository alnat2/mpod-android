# Stage 1 — release APK emulator QA

Evidence directory (repository-relative): `build/qa-stage1-20260922-1200/`.

Date: 2026-09-22. Scope: Stage 1 only; no application source changes, builds, automated Android tests, phone access, data clearing, or uninstall. The fixture server gained independently controlled feed revisions; its focused support test passed.

## Preflight and install

| Check | Result | Evidence |
|---|---|---|
| APK identity | PASS | SHA-256 `b67cb330ccbe1c8c32963e4c554106fd1b0868de0f3cb221ffac8f241e44b1bf`; metadata: `com.prod.mpod`, 1.0.18 (19), commit `26112e1f08bec8233151cbb637fa0432d0f080b7` |
| APK certificate | PASS | V2 certificate SHA-256 `61f0b1bb4485fcf4333e005e1adb43115340eb6b63b8f378cce5319430a4d012` matches handoff metadata |
| Target | PASS | Existing Pixel_9, API 37, serial `emulator-5554`; no physical device used |
| Upgrade | PASS | Pre: 1.0.17 (18). `adb install -r` result: Success. Post: 1.0.18 (19), signing package identity remained compatible |
| Visible data preservation | PASS | Before and after: `1 podcast · 1 unlistened`, existing Throttled30 Podcast and its one episode. `pre-update.png`, `pre-update.xml`, `post-update.xml` |

## UI results

| Step | Result | Evidence |
|---|---|---|
| Add baseline controlled `race` RSS (3 episodes) | PASS | App request id 2: `GET /race.xml` 200. Selected horizontally in subscription carousel: `QA race`, `3 / 3 episodes`, episodes 1–3 in `race-selected.xml` |
| Refresh selected `race` after server revision 2 | PASS | App request id 6: `GET /race.xml` 200. UI immediately showed `4 / 4 episodes` and episode 4 in `race-refreshed.xml` |
| Repeat Refresh with unchanged revision | PASS | UI remained `4 / 4 episodes`; no duplicate episode 4 in `race-refresh-repeat.xml` |
| Repeat Add Feed duplicate prevention | PASS | Re-entered `race.xml`; UI: `This podcast is already in your library.` in `duplicate-complete.xml`. No extra fixture GET was made. |
| Restart persistence | PASS | After `am force-stop` and normal launch, selected `QA race` still displayed `4 / 4 episodes` in `relaunch.xml`. |
| aliases and dc baselines | PASS | Library changed to 3 then 4 podcasts; `QA aliases` showed `3 / 3 episodes` in `aliases-selected-2.xml`; dc later showed `3 / 3` in `dc-error-selected.xml`. |
| Refresh all with one controlled failure | PASS | Fixture requests: aliases id 6 = 200 (UI `4 / 4`); race id 8 = 200 (UI `5 / 5` in `race-refresh-all.xml`); dc id 7 = 503 while its UI retained `3 / 3` in `dc-error-selected.xml`. UI named dc's HTTP 503. |
| dc recovery and retry | PASS | Restored dc and used its visible Refresh control as retry; fixture id 10 = 200 and UI immediately showed `4 / 4 episodes` in `dc-retry.xml`. |
| External RSS refresh | PASS, bounded | Public official NPR feed `https://feeds.npr.org/510289/podcast.xml` returned `200 application/xml` to a host read-only check and was added through the release UI. A subsequent global Refresh reported only the pre-existing unavailable Throttled30 subscription; no new external releases were asserted. Evidence: `external-added.xml`, `external-refresh.xml`. |

## Fixture and limits

Fixture servers used `127.0.0.1:8765` with a temporary `adb reverse tcp:8765 tcp:8765`. They were stopped at end and the reverse was removed. The AVD remains running. No proxy was changed (`http_proxy=null`). Controlled request evidence is in `fixtures-live/requests.jsonl` and `fixtures-remaining/requests.jsonl`. The initial short-lived fixture invocation caused an `unexpected end of stream` before it was relaunched in a persistent session; this was a QA harness lifecycle error, not an app finding.

The screenshot `race-ui-not-visible.png` shows the previous podcast selected; that was resolved by one horizontal carousel swipe, not an application blocker. `race-selected.xml` is the relevant UI evidence.

The pre-existing Throttled30 test subscription returned `Connection reset` during Refresh all. It was preserved and is not classified as an application defect. This meant the global refresh status remained unsuccessful even after dc recovered.


## Final evidence review and completion

The parent agent completed the remaining evidence checks on the same Pixel_9/API37 release package. Result: **PASS for the tested Stage 1 subscription workflows**, with the limitations below; this is not full release acceptance.

| Check | Result | Evidence |
|---|---|---|
| Selected aliases after Refresh all | PASS | `review-aliases.xml`: QA aliases, 4 / 4 episodes, visible episode 4. |
| Actual banner Try again button | PASS | Set dc revision 3 (five episodes), fail dc, tap the banner Try again. `review-try-again-failure-settled.xml` shows dc503 and retained data. Clear failure and tap the same banner button: `review-try-again-recovered.xml` immediately shows 5 / 5 and episode 5 while refresh-all is still completing; no navigation/relaunch used. `fixtures-review/requests.jsonl`: dc request 3=503, request 7=200. |
| External podcast contents and selected Refresh | PASS, bounded | `review-external-selected.xml`: Planet Money, 355 / 355 episodes and actual episode titles. Tap its own Refresh; `review-external-refreshed.xml` and `.png` show the same selected podcast, 355 / 355, completed Refresh control, no error banner. No newly published external episode was claimed. |

The old Throttled30 test endpoint remains unavailable and produces a scoped error during global Refresh. A completely successful Refresh all across the entire existing library and a new successful global Last refresh timestamp were therefore **not demonstrated**. Healthy fixture updates, retained data on failure, actual Try again recovery, and successful selected external refresh were demonstrated.

Preservation evidence covers the visible existing subscription and episode only. Existing playlist, position and Settings were not fully inventoried; playback and broader persistence remain Stage 2 work. Private Room/file inspection and full automated regression were not performed. The suspected immediate-refresh UI issue did not reproduce in these runs; this does not prove all possible races are absent.

Final test subscriptions: QA race 5 episodes, QA aliases 4, QA dc 5, Planet Money 355, plus the retained original Throttled30 subscription. The controlled fixture URLs will be unavailable while the temporary fixture server is stopped; these test-only refresh errors are expected until fixtures are restarted. No subscriptions were removed. Review fixture process and its reverse mapping were removed after evidence capture; AVD retained for the next stage.
