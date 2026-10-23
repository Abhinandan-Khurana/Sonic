# Runout 0.7.0 development verification

Verified on 2026-10-22 (Asia/Kolkata), macOS, source branch `deck-v3`.

## Build and tests

- JDK from Android Studio, Android SDK 36, Gradle wrapper.
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug -x buildSoulseekNative`: BUILD SUCCESSFUL.
- JVM tests: 238 total, 237 passed, 1 skipped (public relay check), no failures or errors.
- Lint: 0 errors, 68 warnings.
- Instrumentation tests (`connectedDebugAndroidTest`) were not run for this build.

## Native build limitation

Rust and Android NDK are absent on this Mac, so the native library was not rebuilt. `librunout_soulseek.so` for all four ABIs is taken from the 0.6.0 development APK, byte-identical to the libraries every development build since v0.4.6-dev.1 has carried; later native-source changes are not included. This is the same exception as 0.6.0, kept for this development artifact only.

## Artifact checks

- Application ID `app.runout.dev`, version 0.7.0, version code 14, minimum SDK 28, target SDK 36.
- ARM64, ARMv7, x86 and x86-64 libraries present: Filament, gltfio, AndroidX graphics path and the Soulseek core.
- All 16 native libraries have ELF LOAD alignment of at least 16384 bytes.
- SDK `zipalign -c -P 16 4` and `apksigner verify` passed.
- Debug certificate SHA-256: `1d001a76582b5450dc6795285455321aacaced319cf08fc2040471883b5dd8b8` (the same as 0.6.0, so it updates in place).
- The Array font (Indian Type Foundry, via Fontshare, ITF Free Font License) is embedded under `assets/fonts/licensed/`. It is not in the repository; its licence doesn't allow distribution through a public repository. Bundled notices include Filament Apache 2.0, native dependency notices, Soulseek MIT, Inter OFL and Silkscreen OFL.

Artifact SHA-256: `0caf04744e35cc8da91297aad17b4ed003bd195bdea20e0796b7481af7603ac1` (75307360 bytes).

## UI verification

Each screen was compared on the Pixel_6 API 37 ARM64 emulator against its board in `docs/design/runout-te.pen` (exported to PNG and HTML/CSS for the sizes):

- Home, portrait and landscape, empty platter, cued and pulled.
- Onboarding: the intro animation, sign in, the step pages and the welcome.
- Settings list and Library, Dig, Drops, Friends, and the Audio quality panel opened by holding the LED window.

The 3D animations were watched on the emulator:

- **Pull from the sleeve:** the record rises out.
- **Drag onto the platter:** the dashed ring shows, and the record lands into side A.
- **Flip:** lift, roll, land on side B.
- **Eject:** EJECT on the LED, then the record arcs back into its sleeve.
- **Platter occupied:** the record bounces back into its sleeve, with the error toast and "eject →".

Limitations:

- The emulator logged one "isn't responding" for an input injected with `adb shell input` while `screenrecord` was running during a cold start. The same eject driven through the emulator's own touch input ran cleanly.
- The emulator's UWB and NFC HAL services crash in a loop and made the system slow late in the session.
- No physical phone, public-account interoperability, Bluetooth/USB DAC, or TalkBack validation was performed. The APK is debug signed, not production signed.
