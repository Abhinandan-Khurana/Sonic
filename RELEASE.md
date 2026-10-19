# Releasing Runout

The current artifact is [Runout 0.4.1 development](https://github.com/Somchandra17/runout/releases/tag/v0.4.1-dev.1). It is a GitHub **pre-release**, built as **Runout Dev** (`app.runout.dev`) for Android 9 or newer, with ARM64, ARMv7, x86-64 and x86 support.

## Prepare and verify

1. Follow [BUILDING](docs/BUILDING.md) using JDK 21, SDK 36, Rust 1.91.0 and NDK r27d. Start from a reviewed source revision and update `CHANGELOG.md` and `release/RELEASE_NOTES.md`.
2. Run native unit tests, JVM tests and lint; build debug and instrumentation APKs. Run `connectedDebugAndroidTest` on an emulator or device. Record counts, platform, skipped checks and untested public-network behavior in the versioned verification report.
3. Check that the APK contains `librunout_soulseek.so` for all four ABIs, bundled native license notices and 16 KiB-aligned ELF load segments. Inspect the manifest application ID, version and signing certificate with the Android SDK tools.
4. Copy the verified debug APK to `release/Runout-<version>-development.apk`. Generate its checksum from that directory:

   ```bash
   cd release
   sha256sum Runout-<version>-development.apk > Runout-<version>-development.apk.sha256
   sha256sum -c Runout-<version>-development.apk.sha256
   ```

5. Commit the source, docs, checksum and verification report. Keep new APK binaries in release assets rather than Git history; the supplied v0.3 artifacts remain as historical files.

## Publish to the fork

Push the reviewed commit to `Somchandra17/runout`. Create a unique source tag for that exact commit, then publish a pre-release with the APK, checksum and verification report. Use a notes file so the release body preserves its formatting:

```bash
gh release create <tag>   release/Runout-<version>-development.apk   release/Runout-<version>-development.apk.sha256   release/Runout-<version>-VERIFICATION.md   --repo Somchandra17/runout --target <full-commit-sha>   --prerelease --latest=false --title 'Runout <version> development'   --notes-file release/RELEASE_NOTES.md
```

Download the published APK and verify it against the published checksum. GitHub's automatic source archives correspond to the release tag. Release creation is separate from opening a pull request.

## Signing and production readiness

The development APK is debug-signed. It installs alongside `app.runout` and has separate private state. It cannot replace the supplied v0.3 APK, whose signing certificate differs. The normal `assembleRelease` variant also retains prototype debug signing; do not describe it as production-signed.

A production release requires a suitable allocated Soulseek client identifier, production signing and real-account interoperability checks with representative peers and network conditions. Preserve the original production key when shipping an update under `app.runout`. These requirements do not turn a development pre-release into a stable production build.
