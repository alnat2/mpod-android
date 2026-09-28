# Android APK signing

`com.prod.mpod` is the distributable application ID. Its `release` build requires a private keystore outside Git and all four environment variables below. Missing values or a missing keystore fail the build with a signing error; the checked-in `app/debug.keystore` and copies of its certificate are rejected for this variant.

| Variable | Value |
|---|---|
| `MPOD_RELEASE_STORE_FILE` | Absolute path to the private keystore |
| `MPOD_RELEASE_STORE_PASSWORD` | Keystore password |
| `MPOD_RELEASE_KEY_ALIAS` | Signing key alias |
| `MPOD_RELEASE_KEY_PASSWORD` | Signing key password |

Set these in the local environment or a protected CI secret store. Do not commit the keystore, passwords, or an environment file containing them. Do not pass passwords on a command line or enable Gradle debug logging for a signing build.

With all four values set, build the distributable APK:

```sh
./gradlew :app:assembleRelease
```

Verify the resulting `app/build/outputs/apk/release/app-release.apk` with `apksigner verify --print-certs`, and compare its certificate SHA-256 digest with the owner's approved permanent certificate before delivery. A successful Gradle build alone does not establish the expected signer.

For local or CI checks of the minified build without a private key, use the explicit QA variant:

```sh
./gradlew :app:assembleQaRelease
```

Its output is `app/build/outputs/apk/qaRelease/app-qaRelease.apk`, signed with the checked-in test key and installed as `com.prod.mpod.signingtest`. It cannot update an existing `com.prod.mpod` installation. GitHub Actions uploads it as `mpod-qa-test-signed-apk` and verifies its signature and package. It is not a distributable production APK. Debug tests and lint do not need release signing secrets.

## Existing installations

Previously delivered `com.prod.mpod` APKs used the checked-in debug key. An APK signed with a different certificate cannot update those installations in place. The checked-in key has been available through Git history, so removing its current file would not make it a private production key.

Before delivering a privately signed `com.prod.mpod` APK, the owner must choose the permanent key and the treatment of existing installations. The safe paths need different preparation:

- If preserving all current local data is required, design and verify a full export/import or other migration path from the old installation before changing its package or removing it. The current OPML export covers subscriptions only, not the full Room/DataStore state or downloaded files.
- If a fresh installation is acceptable, explicitly back up the data the owner wants to retain, then schedule the reinstall with the owner. Uninstalling the old package removes its private app data. Do not perform it as a build workaround.

Record the approved certificate fingerprint and migration decision in the release handoff. Until then, `release` is a guarded build path, not an approved artifact for replacing the previously delivered APK.
