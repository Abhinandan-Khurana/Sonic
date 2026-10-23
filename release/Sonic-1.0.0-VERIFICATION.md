# Sonic 1.0.0 verification

Verified on 2026-10-23 (Asia/Kolkata), macOS, source branch `deck-v3` merged with `main`.

## Build and tests

- JDK from Android Studio, Android SDK 36, Gradle wrapper.
- `:app:testDebugUnitTest -x buildSoulseekNative`: BUILD SUCCESSFUL. 356 JVM and Robolectric tests,
  355 passed, 1 skipped (public relay check), no failures or errors.
- `:app:assembleRelease -x buildSoulseekNative`: BUILD SUCCESSFUL.
- Instrumentation tests (`connectedDebugAndroidTest`) were not run for this build.

## Native library

`librunout_soulseek.so` for all four ABIs was built on this Mac with Rust 1.91.0 and NDK r27d
(27.3.13750724) from the native sources at `bda1560`, the last commit to touch `native/`. The
release build packs those libraries; `-x buildSoulseekNative` only skips rebuilding them.

## Artifact checks

- Application ID `app.sonic`, label "Sonic.", version 1.0.0, version code 15, minimum SDK 28,
  target SDK 36.
- ARM64, ARMv7, x86 and x86-64 libraries present: Filament, gltfio, AndroidX graphics path and the
  Soulseek core (16 libraries).
- All 16 native libraries have ELF LOAD alignment of 16384 bytes.
- SDK `zipalign -c -P 16 4` and `apksigner verify` passed.
- Signing certificate SHA-256: `1d001a76582b5450dc6795285455321aacaced319cf08fc2040471883b5dd8b8`
  (the development key, as for 0.6.0 and 0.7.0).
- Launcher: two aliases on MainActivity, `LauncherLight` (enabled) and `LauncherDark` (disabled),
  switched by the app to follow its theme.
- The Array font (Indian Type Foundry, via Fontshare, ITF Free Font License) is embedded under
  `assets/fonts/licensed/`. It is not in the repository. Bundled notices include Filament
  Apache 2.0, native dependency notices, Soulseek MIT, Inter OFL and Silkscreen OFL.

Artifact SHA-256: `d483f477f866b21170593ff16564950771ab3859ad42dc4d95d6310fc912aa81` (65795237 bytes).

## Device checks

On the Pixel 6 API 37 ARM64 emulator:

- The release APK installs beside the debug build and opens on the intro.
- With the debug build and a made-up library of six FLAC albums: pull, drop and play;
  portrait and landscape in light and dark; all three decks; Flags; Settings; Storage with its
  delete warning; record details; Drops; Dig.
- The launcher icon moves from the cream sleeve to the black one when the app leaves the screen in
  the dark theme. The themed (monochrome) icon was checked as a rendered preview at launcher size,
  not with themed icons switched on in the emulator.
- The intro plays the cream sleeve in light and the black sleeve in dark.

Found and fixed during these checks: the crate reset to its first sleeve on rotation, and the
Hi-Res sticker drew blank in the dark theme.

## Limits

- No physical phone was tested for this build. Earlier builds were checked on a Pixel 6.
- No public-account interoperability, Bluetooth/USB DAC or TalkBack pass was done for this build.
- The APK is signed with the development key, not a production key.
