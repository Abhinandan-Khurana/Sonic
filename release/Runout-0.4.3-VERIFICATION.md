# Runout 0.4.3 development verification

Verified on 2026-10-21 (Asia/Kolkata). Source branch: `slice-4-inked-home`.

## What this build contains

- Idle vinyl rack: vertical scroll, tap to unbox, then the existing drag onto the platter.
- Inked home chrome and a first printed transcription deck. The full pen.dev gesture storyboard is not finished.

## Checks actually run

- `./gradlew testDebugUnitTest lintDebug assembleDebug` — `BUILD SUCCESSFUL in 22s`.
- JVM/Compose XML: **218 tests, 217 passed, 1 historical opt-in skip, 0 failures**.
- Lint XML: **0 errors, 60 warnings**.
- APK: `app.runout.dev`, version 0.4.3, version code 7, min SDK 28, label **Runout Dev**.
- Native ABIs present: ARM64, ARMv7, x86-64, x86. `zipalign -c -P 16` reported `Verification successful`.
- Debug certificate SHA-256: `af474b32bbd550f30e43db7a5c1e291e4edb677485ef6368304ba4249cc6c083` (same development certificate as 0.4.2).
- No new public Soulseek login was run. Connected Android tests were not rerun for this UI build.

This remains a debug-signed development pre-release. It can update the 0.4.2 development build because the signing certificate matches.
