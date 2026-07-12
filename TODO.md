# mpod TODO

## Done

- [x] Match the Home screen closer to the Figma mobile layout.
- [x] Replace current app icons with the extracted Figma icon set, except play, -10, and +15.
- [x] Fix password visibility icons so the visible/hidden states switch correctly.
- [x] Add reusable mobile components and Android Studio previews for the Figma component set.
- [x] Connect the app to the test backend through the configured backend address.
- [x] Create and verify the test account `t` / `123`.
- [x] Fix session restore after app restart.
- [x] Clean podcast/feed text so HTML entities and tags do not leak into UI.
- [x] Improve Home performance by replacing expensive custom blur shadows with native Compose shadows.
- [x] Render the Home playlist lazily.
- [x] Avoid idle playback recompositions when the player state has not changed.

## Next

- [x] Audit the Subscriptions screen on Nexus 5 against Figma and current backend data.
- [x] Optimize Subscriptions if long episode lists still create jank.
- [x] Verify Settings screen spacing, typography, states, and lower-screen scrolling on Nexus 5.
- [x] Verify Add podcast modal states: RSS URL, OPML picker, loading, error, success.
- [x] Verify auth/setup screens against Figma, including password visibility behavior.
- [ ] Verify show notes modal layout, scrolling, close behavior, and long text.
- [ ] Verify empty, loading, and error states across Home, Subscriptions, Add podcast, and Settings.
- [ ] Run a focused performance pass on the slowest remaining flow with `gfxinfo`, and Perfetto if needed.
- [ ] Check real-device or physical-phone performance after emulator checks are stable.

## Functional Follow-Ups

- [ ] Implement playlist drag and drop/reorder behavior.
- [ ] Finish episode menu actions: add/remove playlist, listened/unlistened, download, show notes.
- [ ] Verify playback controls end to end: play/pause, seek -10/+15, speed, progress saving.
- [ ] Verify RSS add flow against the backend with success and duplicate/error cases.
- [ ] Verify OPML import/export flows with real files.
- [ ] Replace placeholder podcast artwork with real artwork once backend/database data is ready.
- [ ] Add focused tests for session restore, backend config, playlist actions, and feed text cleanup.
