# Android APK signing

## Current local delivery

The owner chose the existing signing key for the current single-phone, non-Play-Store use. Build the explicit `localRelease` variant:

```sh
./gradlew :app:assembleLocalRelease
```

This produces the minified `app/build/outputs/apk/localRelease/app-localRelease.apk` with application ID `com.prod.mpod` and the same certificate as the previously installed local release. GitHub Actions uploads this explicitly named APK as `mpod-local-legacy-signed-apk` and checks its certificate digest and package. It does not publish the APK to Google Play.

For an update on the existing phone, verify that the installed app and new APK both have package ID `com.prod.mpod`, that their signing certificates match, and that the new `versionCode` is higher. Install the APK **over** the existing app; do not uninstall it or clear its data. Android then retains the app's local data. The current local APK is `1.0.19 (20)`; direct inspection of the archived `1.0.18 (19)` handoff APK confirmed the same package and certificate. The actual installed version on the phone has not been checked, and the new APK has not been installed there as part of this signing change.

The current key is in `app/debug.keystore` and has been available through Git history. It is adequate for the owner's controlled local update process, but it is not a private production key. Anyone with the key could sign an APK with the same app identity; that APK would still need to reach the phone and be installed.

## Private release path

The separate `release` variant remains guarded for a future private signing key. It requires a keystore outside Git and all four environment variables below. Missing values or a missing keystore fail the build; the checked-in debug certificate is rejected even if its keystore was copied to another path.

| Variable | Value |
|---|---|
| `MPOD_RELEASE_STORE_FILE` | Absolute path to the private keystore |
| `MPOD_RELEASE_STORE_PASSWORD` | Keystore password |
| `MPOD_RELEASE_KEY_ALIAS` | Signing key alias |
| `MPOD_RELEASE_KEY_PASSWORD` | Signing key password |

Set these in the local environment or a protected CI secret store. Do not commit the keystore, passwords, or an environment file containing them. Do not pass passwords on a command line or enable Gradle debug logging for a signing build.

The `validateReleaseSigning` Gradle task runs before `release` APK/AAB packaging. It lists missing variables, checks that the keystore file and alias exist, and compares the selected certificate with the current checked-in debug certificate. Debug tests and lint remain available without release secrets.

```sh
./gradlew :app:assembleRelease
```

Before using a privately signed APK to update an existing installation, verify the certificate transition. A plain APK signed only with a different key is rejected as an update; Android does not erase the installed app's data on that failure. Android 13+ supports signing-key rotation through a lineage, but this project has not configured or tested it. Do not uninstall the existing app as a build workaround.

## If distribution through Google Play is planned

Revisit signing before the first Play release. Choose how the app signing key will be protected and whether Play App Signing or a signing-key rotation is needed for continuity with the existing `com.prod.mpod` installation. The checked-in local key must not be treated as a private production key. Test the chosen update path and data retention before delivering a replacement APK. The current local signing decision does not settle the Play Store signing plan.
