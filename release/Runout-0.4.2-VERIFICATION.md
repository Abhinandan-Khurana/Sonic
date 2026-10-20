# Runout 0.4.2 development verification

Verified on 2026-10-21 (Asia/Kolkata). Source branch: `slice-0-stop-the-damage`.

## Build and tests

- Built with JDK 21, Android SDK 36, Rust 1.91.0, and NDK r27d.
- JVM/Compose: **215 tests, 214 passed, 1 historical opt-in Nostr relay test skipped**, 0 failures.
- Native locked lib tests: **334 passed** (329 core, 5 adapter).
- Android instrumentation on API 35 (`emulator-5556`, x86-64): **5 tests, 4 passed, 1 explicit live-network test skipped**, 0 failures.
- Android lint: **0 errors, 60 warnings**.
- No public Soulseek login, download, or third-party message was run for this build.

## Artifact checks

The checks below are filled from the commands run against the APK copied into this release directory.

- Application: **Runout Dev**, `app.runout.dev`, version 0.4.2, version code 6, min SDK 28.
- Native ABIs: ARM64, ARMv7, x86-64, and x86.
- ELF alignment, APK alignment, licenses, and signing: see the command transcript recorded with this release.

This remains a debug-signed development pre-release. It can update the 0.4.1 development build only when the signing certificate matches. It is not a production release and does not replace `app.runout`.
