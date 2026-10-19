# Runout 0.4.0 development verification

Verified on 2026-10-19. This is a local development build; no public Soulseek account was created or used.

- Android debug and release APK builds completed successfully.
- JVM unit/UI tests: 128 total, 127 passed, 1 opt-in historical live-relay test skipped.
- Native core and adapter tests: 329 passed (326 upstream/core tests, 3 adapter tests).
- Android emulator integration: 3 passed on Android 17 / API 37, x86-64.
- Android lint: zero errors. 50 nonfatal warnings remain in the report.
- Native libraries built for ARM64, ARMv7, x86-64 and x86 with API 28 and 16 KiB load-segment alignment.
- Packaged native libraries and bundled license notices checked in the development APK.

The emulator tests exercise real local TCP connections between two embedded Soulseek peers: login, FLAC search, normalized folder browsing, byte-identical downloads, retry, file-descriptor uploads, private messages and explicit acknowledgements. They also check the real Android player can play a local FLAC without Soulseek sign-in and remembered credentials round-trip through Android Keystore without plaintext storage.

The development APK installs as Runout Dev, application ID app.runout.dev, alongside an existing Runout installation. The supplied v0.3 APK and the local build have different signing certificates. The normal release application ID is unchanged; updating that app requires its original signing key.

Before public production distribution: arrange a production Soulseek client identifier, configure production signing and check interoperability with a real account and representative network conditions. The tested adapter currently identifies as experimental Soulseek 177/1.

Source tag: `v0.4.0-dev.1` on `Somchandra17/runout`. The final debug APK is 47,678,119 bytes and its v2 signature was verified. Signing certificate SHA-256: `af474b32bbd550f30e43db7a5c1e291e4edb677485ef6368304ba4249cc6c083`. APK zip alignment and all four native ELF load alignments pass the 16 KiB checks.

See Runout-0.4.0-development.apk.sha256 for the artifact checksum and ../docs/SOULSEEK.md for implementation behavior and limits.
