# Releasing Runout

Artifacts live on this repository's [releases page](https://github.com/Abhiroop1122/runout/releases).
There are two kinds:

| Kind | Tag | When |
| :--- | :--- | :--- |
| **Release** | `v<version>` | A build worth putting in front of users. GitHub marks it *Latest*. |
| **Development pre-release** | `v<version>-dev.<n>` | An intermediate build, kept so every step of the work can be inspected and installed. |

Both are built as **Runout Dev** (`app.runout.dev`) for Android 9 or newer, with ARM64, ARMv7,
x86-64 and x86 support. The 0.4.0 to 0.5.0 development builds are all kept as pre-releases; the
0.5.1 build is the current release.

## Prepare and verify

1. Follow [BUILDING](docs/BUILDING.md) using JDK 21, SDK 36, Rust 1.91.0 and NDK r27d. Start from a
   reviewed source revision and update `CHANGELOG.md` and `release/RELEASE_NOTES.md`.
2. Run native unit tests, JVM tests and lint; build debug and instrumentation APKs. Run
   `connectedDebugAndroidTest` on an emulator or device. Record counts, platform, skipped checks
   and untested public-network behavior in the versioned verification report.
3. Check that the APK contains `librunout_soulseek.so` for all four ABIs, bundled native license
   notices and 16 KiB-aligned ELF load segments. Inspect the manifest application ID, version and
   signing certificate with the Android SDK tools.
4. Copy the verified debug APK to `release/Runout-<version>-development.apk`. Generate its checksum
   from that directory:

   ```bash
   cd release
   sha256sum Runout-<version>-development.apk > Runout-<version>-development.apk.sha256
   sha256sum -c Runout-<version>-development.apk.sha256
   ```

5. Commit the source, docs, checksum and verification report. Remove the previous version's
   checksum and report from `release/`: they stay attached to their GitHub release. Keep APK
   binaries in release assets, never in Git.

## Publish a development pre-release

Push the reviewed commit to `Abhiroop1122/runout`. Create a unique source tag for that exact
commit, then publish a pre-release with the APK, checksum and verification report. Use a notes
file so the release body preserves its formatting:

```bash
gh release create v<version>-dev.<n>   release/Runout-<version>-development.apk   release/Runout-<version>-development.apk.sha256   release/Runout-<version>-VERIFICATION.md   --repo Abhiroop1122/runout --target <full-commit-sha>   --prerelease --latest=false --title 'Runout <version> development'   --notes-file release/RELEASE_NOTES.md
```

## Publish a release

A release is the same artifact promoted out of the pre-release stage. Take the verified APK and
its report, drop the `development` wording from the file names, and publish without
`--prerelease`:

```bash
cp release/Runout-<version>-development.apk      release/Runout-<version>.apk
cp release/Runout-<version>-development.apk.sha256 release/Runout-<version>.apk.sha256
cp release/Runout-<version>-VERIFICATION.md      release/Runout-<version>-VERIFICATION.md

# The checksum file names the file it is for; keep them in step.
(cd release && sha256sum Runout-<version>.apk > Runout-<version>.apk.sha256)

gh release create v<version>   release/Runout-<version>.apk   release/Runout-<version>.apk.sha256   release/Runout-<version>-VERIFICATION.md   --repo Abhiroop1122/runout --target <full-commit-sha>   --title 'Runout <version>'   --notes-file release/RELEASE_NOTES.md
```

Then remove the development tag it supersedes, so one version has one release:

```bash
gh release delete v<version>-dev.<n> --repo Abhiroop1122/runout --yes --cleanup-tag
```

Keep the bytes unchanged when promoting: rename the asset and re-cut the checksum file, do not
rebuild. A release that differs from the pre-release it was promoted from has to be built and
verified again.

Download the published APK and verify it against the published checksum. GitHub's automatic source
archives correspond to the release tag. Release creation is separate from opening a pull request.

## Signing and production readiness

The APK is debug-signed. It installs alongside `app.runout` and has separate private state. It
cannot replace the supplied v0.3 APK, whose signing certificate differs. The normal `assembleRelease`
variant also retains prototype debug signing; do not describe it as production-signed.

A production release requires a suitable allocated Soulseek client identifier, production signing
and real-account interoperability checks with representative peers and network conditions. Preserve
the original production key when shipping an update under `app.runout`. These requirements do not
turn a development pre-release into a stable production build.
