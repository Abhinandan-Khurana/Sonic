# Runout 0.6.0 development verification

Verified on 2026-10-22 (Asia/Kolkata), macOS, source branch `deck-3d`.

## Build and tests

- JDK from Android Studio, Android SDK 36, Gradle wrapper.
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest -x buildSoulseekNative`: BUILD SUCCESSFUL.
- JVM tests: 235 total, 234 passed, 1 skipped, no failures or errors. Public relay check skipped.
- Lint: 0 errors, 63 warnings, 2 hints.
- `connectedDebugAndroidTest`, Pixel_6 API 37 ARM64 emulator: 5 tests, 3 passed, 1 skipped (public Soulseek), 1 failed (discovery performance).
- All three packaged-native integration tests passed: local FLAC deck playback, Android Keystore storage, local peers searching/downloading/sharing/messaging.
- Discovery benchmark failed its remote-reduction target of 250 ms: 526.34 ms initially, 402.62 ms on an isolated rerun. Local search passed its 150 ms target: 117.01 ms and 110.27 ms. This development build does not meet the remote discovery performance target.

## Native build limitation

Rust and Android NDK are absent on this Mac. Native source tests and a native rebuild were not run. `librunout_soulseek.so` for all four ABIs is reused from the existing generated artifacts, byte-identical to the recovered Claude build. As recorded for 0.5.1, these libraries originated from v0.4.6-dev.1; subsequent native-source changes are not included. This is an explicit exception to the full native-build instructions in docs/BUILDING.md, retained for this development artifact only. Device integration tests exercise the packaged native bytes.

## Artifact checks

- Application ID `app.runout.dev`, version 0.6.0, version code 13, minimum SDK 28, target SDK 36.
- ARM64, ARMv7, x86 and x86-64 libraries present, including Filament and gltfio.
- All 16 native libraries have ELF LOAD alignment of at least 16384 bytes.
- SDK `zipalign -c -P 16 4` and `apksigner verify` passed.
- Debug certificate SHA-256: `1d001a76582b5450dc6795285455321aacaced319cf08fc2040471883b5dd8b8`.
- Bundled notices include Filament Apache 2.0, native dependency notices, Soulseek MIT, Inter OFL and Silkscreen OFL.

## UI and limitations

The 3D deck was visually verified on Pixel_6 API 37 after an emulator reboot and an app-process restart. The first picture can take several seconds. Earlier in this session the emulator stalled permanently after two Filament frames; redraw, SSAO and frame-pacing experiments did not help and were removed. Restarting the emulator restored rendering. This does not establish a phone-level fix or guarantee against GPU stalls.

The recorded `deck-startup-smoke` flow passes on an onboarded installation. It only checks that the deck control is present; a human must separately inspect the rendered deck. An initial replay after emulator snapshot restoration failed because onboarding was incomplete. No manual playback success is claimed here; playback is covered by the packaged-native instrumentation test.

Artifact SHA-256: `e9871ef1094c1557782dee37f66776ca310f5bea29d3334e6a26d4062d5947a4` (73856257 bytes).

No physical phone, public-account interoperability, Bluetooth/USB DAC, or TalkBack validation was performed. Clear vinyl remains opaque, the disc handover and flip remain instant, and the model's printed-label font has known punctuation artifacts. APK is debug signed, not production signed.
