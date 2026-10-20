# Runout 0.4.5 development verification

Verified on 2026-10-20 (Asia/Kolkata). Source branch: `glass-room`.

## What this build contains

- Home is the glass listening room from the first Runout build: crate, picture disc, and glass turntable. The inked deck and idle disc rack are not the home screen.
- Soulseek search keeps FLAC results whose artist, album, folder, or title matches every search word.
- Downloads stay in add order. Pause all, Resume all, and Turtle mode are present. A pause frees the slot. Resume all continues an interrupted Soulseek download.
- Pure mode passes the decoder's PCM through without converting it. A saved FLAC is refused when its bytes do not match the download. USB bit-perfect output was not proven on a connected DAC.

## Checks

- `./gradlew testDebugUnitTest lintDebug assembleDebug` — `BUILD SUCCESSFUL in 36s`.
- JVM/Compose XML: **224 tests, 223 passed, 1 skipped, 0 failures**.
- Lint XML: **0 errors, 61 warnings, 5 hints**.
- APK: `app.runout.dev`, version 0.4.5, version code 9, min SDK 28, target SDK 36.
- Native library `librunout_soulseek.so` is present for ARM64, ARMv7, x86, and x86-64.
- `zipalign -c -P 16 -v 4` — Verification successful.
- Debug certificate SHA-256: `af474b32bbd550f30e43db7a5c1e291e4edb677485ef6368304ba4249cc6c083`.
- Local checksum: `7f61bc3e17c0e732886937eab178fdd462aaf1abef1e6d6f8477426418eb1500`.
- No new public Soulseek login was run. Connected device tests were not rerun.
