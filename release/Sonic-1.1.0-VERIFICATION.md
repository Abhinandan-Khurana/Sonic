# Sonic 1.1.0 verification

Verified on 2026-10-25 (Asia/Kolkata), macOS, branch `feedback-1.0.1` from `main`.

## Build and tests

- JDK from Android Studio, Android SDK 36, Gradle wrapper.
- `:app:testDebugUnitTest -x buildSoulseekNative`: 368 JVM and Robolectric tests, 367 passed,
  1 skipped (public relay check), no failures or errors.
- `:app:assembleRelease -x buildSoulseekNative`: BUILD SUCCESSFUL.
- Instrumentation tests (`connectedDebugAndroidTest`) were not run for this build.

## Native library

`librunout_soulseek.so` for all four ABIs is the one built for 1.0.0 with Rust 1.91.0 and NDK r27d
from the native sources at `bda1560`. Nothing under `native/` changed since.

## Artifact checks

- Application ID `app.sonic`, label "Sonic.", version 1.1.0, version code 1010099 (the new scheme:
  major × 1,000,000 + minor × 10,000 + patch × 100 + 99 for a release), minimum SDK 28, target SDK 36.
- ARM64, ARMv7, x86 and x86-64 libraries present (16 libraries), every ELF LOAD segment aligned to
  16384 bytes.
- SDK `zipalign -c -P 16 4` and `apksigner verify` passed.
- Signing certificate SHA-256: `1d001a76582b5450dc6795285455321aacaced319cf08fc2040471883b5dd8b8`,
  the same as 1.0.0, so it updates 1.0.0 in place.
- No dependency metadata block (`dependenciesInfo` off). The in-app update check is on
  (`UPDATE_CHECK`); F-Droid builds would turn it off.

Artifact SHA-256: `7b58777aea6239055c15c71b1e9203e2de978560a935614001bfcdb67b514b8b` (65836201 bytes).

## Device checks

On the Pixel 6 API 37 ARM64 emulator:

- The release APK installs over 1.0.0 as an update (version code 15 to 1010099).
- The track wheel picker: a song on the other side turns the record over and plays; only the song
  under the bar plays on a tap.
- Settings › Sound opens with the signal chain; About's update line checked GitHub and read "up to
  date"; Storage opens on the music beside the phone; the shared record picture carries the sleeve.
- Hand-tested by the owner before publishing.

## Limits

- No physical phone for this build. Downloads with the screen off (wake and Wi-Fi locks) still
  need a test on a real phone.
- The APK is signed with the development key, not a production key.
