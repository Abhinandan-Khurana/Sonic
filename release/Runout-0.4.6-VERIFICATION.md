# Runout 0.4.6 development verification

Verified on 2026-10-21 (Asia/Kolkata). Source branch: `restore-original-ui`.

## What this build contains

- The original glass listening room is back. The deck is the only player.
- 33/45, pitch and power controls are removed. Records play at the recorded speed.
- Search cues a song on the platter with the needle up. START/STOP plays it.

## Checks

- `cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked`
  - `runout_soulseek`: 5 passed, 0 failed, 0 ignored.
  - `soulseek-rs`: 329 passed, 0 failed, 0 ignored.
- `./gradlew testDebugUnitTest lintDebug assembleDebug` — `BUILD SUCCESSFUL in 44s`, then a version-only rebuild `BUILD SUCCESSFUL in 5s`.
- JVM/Compose XML: **217 tests, 216 passed, 1 skipped, 0 failures**. Skip: `LiveRelayCheck` public relay lookup.
- Lint XML: **0 errors, 61 warnings, 4 hints**.
- `./gradlew connectedDebugAndroidTest` on `emulator-5554` (AVD `Medium_Phone_API_37.0`, API 37): **5 tests, 4 passed, 1 skipped, 0 failures**. Skip: `SoulseekLiveNetworkTest` (no public-network account).
- APK: `app.runout.dev`, version 0.4.6, version code 10, min SDK 28, target SDK 36.
- `librunout_soulseek.so` present for ARM64, ARMv7, x86, and x86-64.
- `zipalign -c -P 16 -v 4` — Verification successful.
- Debug certificate SHA-256 matches the downloaded `v0.4.5-dev.1` APK: `af474b32bbd550f30e43db7a5c1e291e4edb677485ef6368304ba4249cc6c083`.
- Local checksum: `d3872de1aaeea22ab5bc6de6212772c523b2b7b26e7997d726fa2b36afac6d85`.

## Device

Emulator `emulator-5554`, `Medium_Phone_API_37.0`, API 37, started headless with SwiftShader and no audio. Argent 0.29.0.

- `offline-navigation.yaml`: PASS, 21 passed, 0 failed.
- `offline-song-search.yaml`: PASS, 14 passed, 0 failed, 1 idle warning. Deck screen contained Opening and CUE.
- Portrait and landscape screenshots taken. Status and navigation bars were not visible. No app-name header. The deck itself still prints RUNOUT on the plinth.
- Dig, Drops, Friends, Pressing Plant, Settings, and ↓ 0 each opened and closed a glass sheet.
- At 640×360 dp landscape, pills, clock, and status did not overlap. The pill strip was scrollable.
- Dig search field stayed visible above the keyboard.
- START/STOP played the cued short fixture through to “Side A finished”.
- Notification shade showed Opening / Vela Park with play and previous controls.
- A media Play command while the deck still showed CUE started the session (`PLAYING`, position 0) without lowering the needle. Reported, not redesigned.

## Not verified

- No lock screen was available: this emulator has no screen lock (`isKeyguardShowing=false`). Lock-screen Play was not tapped. The media Play command above is the same session action.
- Headset hook and next/previous were not shown to move a playing side. Next was sent after the 2-second fixture had already stopped.
- Friends private-message field was not opened. It appears only after a signed-in conversation, and no Soulseek account was used.
- No public Soulseek login. The emulator had no audio output.
