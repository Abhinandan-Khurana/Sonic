# Runout 0.5.1 development verification

Verified on 2026-10-21 (Asia/Kolkata), on macOS. Source branch: `ui-redesign`.

## What this build contains

Fixes from the owner's phone and a friend's phone after 0.5.0, recorded as `FU-1` to `FU-13` in `docs/design/UI-REDESIGN.md`:

- albums that vanished after a reinstall, and Settings › Library › Repair local albums;
- the home audio-quality chip and sheet (Track → Runout → Output);
- FLAC-only Friday Drops;
- interrupted downloads shown as a pause and resumed;
- the Dig search key, page-wide sheets, the Crackle switch, the landscape layout, and the sign-in keyboard overlap;
- the vinyl player isolated in `ui/Turntable.kt`.

## How it was built

- This Mac has no Android NDK or Rust toolchain. `librunout_soulseek.so` for all four ABIs is the one from the published `v0.4.6-dev.1` APK, unchanged.
- The native source now retries interrupted reads (`native/vendor/soulseek-rs-lib/src/peer/download_peer.rs`). That change is **not** in this APK; the Kotlin side handles the same error instead.
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -x buildSoulseekNative` printed `BUILD SUCCESSFUL`.

## Checks

- JVM/Compose XML: **233 tests, 232 passed, 1 skipped, 0 failures**. The skip is `LiveRelayCheck` (public relay lookup). New tests cover the vanishing-albums case, Repair, the signal chain, the Crackle switch, page-wide sheets, the Dig search key and the interrupted-read check.
- Lint XML: **0 errors, 61 warnings, 2 hints**.
- APK: `app.runout.dev`, version 0.5.1, version code 12, target SDK 36. `librunout_soulseek.so` is present for ARM64, ARMv7, x86 and x86-64.
- `zipalign -c -P 16 -v 4`: verification successful.
- Debug certificate SHA-256: `1d001a76582b5450dc6795285455321aacaced319cf08fc2040471883b5dd8b8`, the same as 0.5.0, so 0.5.1 updates 0.5.0 in place.
- Local checksum: `d0ae199fe649f2844a4a303d01762cb6d0c123d6d4a93495eb829ec19595bbbc`.
- The live archive query with `format:flac` returns 12,584 albums.

## Device

Emulator `emulator-5554` (AVD `Pixel_6`), Argent 0.25.1, installed over 0.5.0 with `adb install -r`. Checked by hand:

- The room opens with the shelf kept, and the Crackle switch shows on the home screen.
- Friday Drops picked a new week. The first drop listed its FLAC tracks and Claim was enabled.

Checked in Robolectric screenshots (`app/build/outputs/roborazzi/30_*` to `33_*`, `01_landscape_playing`, `03_portrait_playing`): the audio-quality sheet, Repair local albums, the Dig filters and New chat sheets shading the whole page, the landscape layout, and the portrait chip clearing the deck.

## Not verified

- **Sign-in with an on-screen keyboard (FU-1):** the emulator has a hardware keyboard.
- **Interrupted downloads (FU-12):** needs a phone with Background usage off.
- **Record card on a friend's phone (FU-13):** not reproduced. Cards need 0.5.x on both phones.
- **Output device detection:** USB DAC and Bluetooth were checked only in unit tests.
- **Repair on a real music folder:** covered by Robolectric tests with fixture files.
- **Not run:** `connectedDebugAndroidTest`, the Argent saved flows, TalkBack.
