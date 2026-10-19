# Runout 0.4.1 development verification

Verified on 2026-10-20 (Asia/Kolkata). Source tag: `v0.4.1-dev.1` on `Somchandra17/runout`.

## Build and local tests

- Debug APK and instrumentation APK built with JDK 21, SDK 36, Rust 1.91.0 and NDK r27d.
- JVM/Compose tests: **148 total, 147 passed, 1 historical opt-in Nostr relay test skipped**, no failures or errors.
- Native core/adapter tests: **332 passed** (329 core, 3 adapter).
- Android local integration/performance tests: **4 passed on API 35 and 4 on API 37**, both x86-64 emulators. Actual packaged JNI, two local peers, FLAC search/browse/transfer/descriptors/messages/acknowledgements, offline playback and Android Keystore are exercised.
- Android lint: **0 errors**, 60 nonfatal warnings and 5 hints. Existing and new warnings remain visible in the generated report.
- Four native ABIs: ARM64, ARMv7, x86-64 and x86; min API 28. ELF LOAD segments and APK native ZIP alignment pass 16 KiB checks. Native license assets are present.
- APK v2 signature verified. Certificate SHA-256: `af474b32bbd550f30e43db7a5c1e291e4edb677485ef6368304ba4249cc6c083` (same development certificate as 0.4.0).

## Search performance

Warm nearest-rank p95 (slowest of ten samples after three warmups), excluding fixture/index construction, network time and UI rendering:

| Emulator | 10,000 local tracks | 5,000 remote files |
| --- | ---: | ---: |
| Android 15 / API 35 | 16.3 ms | 134.0 ms |
| Android 17 / API 37 | 33.1 ms | 115.8 ms |

The initial API 35 remote reducer measured 344.6 ms and failed its 250 ms target. The optimized reducer passes on both versions. This measures Runout metadata work, not a comparative Seeker benchmark. Network results stream before the bounded collection window finishes.

## Explicit public-network check

An existing user-supplied account was provisioned privately and used deliberately; ordinary tests remain local-only. No new account, public download or third-party message was created by the live probe.

The session lasted **900.137 seconds without a disconnect**. Login completed in 1.001 seconds. Three 30-second public searches returned:

| Query | FLAC files | First batch |
| --- | ---: | ---: |
| Pink Floyd | 1,952 | 5.409 s |
| Radiohead | 732 | 0.801 s |
| Miles Davis | 45 | 1.804 s |

The initial unpatched native probe returned zero files for all three queries. A first patched 100-second run returned 5,646 FLAC files; the longer run returned 2,729. Counts vary with peers, timing and the bounded peer scheduler. They are observations, not availability guarantees.

The x86-64 native library SHA-256 tested in the soak is `cbd8f5512d577c1017fa79fd884f2c99ade1ae6555cc424f40db719f75223db3`; it is byte-identical to the library in the published APK. Later APK changes affect Kotlin UI/playback and sanitized debug connection diagnostics only. All four packaged native libraries were compared with the soak build and are unchanged.

## Device flows

Argent 0.29.0 checks are run against real emulator UI. Offline navigation passes on both Android versions; a selected fixture folder exposes only its valid FLAC album. Offline discovery searches a song and starts it on the vinyl deck. Guided real-account sign-in, remember opt-in and setup complete successfully. Automated keyboard checks disable emulator stylus handwriting, which otherwise defers injected text.

Guided sign-in passes 20 steps, live UI discovery 9 steps and the final picker flow 15 steps. Picker checks include more than 6.5 seconds outside Runout and are repeated after public search. Android 17 interrupts background sockets in app-only mode; the app retains the account and reconnects in approximately one second on return. The UI labels that transition as reconnecting. It does not claim an uninterrupted socket through system background restrictions.

Two deliberate Wi-Fi/mobile-data interruptions pass both offline identity-retention checks and automatic online recovery. Offline checks lasted 8.1 and 14.1 seconds including UI assertions; restoration-to-online flow completion took 2.6 and 2.3 seconds. No credential entry, account recreation or manual reconnect occurred. Network state was restored in a finally block even for a failed assertion during test development.

Notification permission denial leaves background consent off. A subsequent explicit grant enables the notification-backed `SoulseekOnlineService`; Android reports it as a foreground service with an ongoing Disconnect notification. An eight-second app switch retains the same native connection with no session loss or reconnect; consent is then returned to app-only mode. A fresh Radiohead search after the two network recoveries returns new FLAC results through the UI.

## Artifact and limits

The universal debug APK installs as **Runout Dev**, `app.runout.dev`, version 0.4.1 / code 5, Android 9+. Its private state stays separate from the original `app.runout`. See the attached SHA-256 file for the artifact digest. New APKs are release assets rather than committed binaries.

This is a development pre-release using experimental Soulseek 177/1. Production signing and a suitable production client identifier remain. Tests use x86-64 emulators, not a physical ARM phone or every carrier/router configuration. A FLAC container proves the accepted format, not recording provenance. Large font settings keep app navigation usable; dense physical deck/sleeve labels still have limited room at maximum scaling.

Credentials and generated account screenshots are excluded from source and release assets. Test account files and remembered sign-in are removed after verification. No pull request is opened.
