# Releasing Sonic

Releases are published on this repository's [releases page](https://github.com/Abhiroop1122/runout/releases).

| Kind | Tag | Variant | APK |
| :--- | :--- | :--- | :--- |
| **Release** | `v<version>` | `release`: `app.sonic`, label **Sonic.** | `Sonic-<version>.apk` |
| **Development pre-release** (optional) | `v<version>-dev.<n>` | `debug`: `app.sonic.dev`, label **Sonic. Dev** | `Sonic-<version>-development.apk` |

1.0.0 (version code 15) is the first stable release. Every APK supports Android 9 or newer on
arm64-v8a, armeabi-v7a, x86_64 and x86. Development pre-releases are only for builds that need to
be shared before a release; most versions will not have one.

The release variant is signed with the debug key of the maintainer's machine, so only the
maintainer builds releases. That keeps the signing certificate the same from one release to the
next, which Android requires for an update to install over the previous version.

## 1. Prepare

1. Start from a reviewed, clean source revision.
2. Set `versionName` and increase `versionCode` in `app/build.gradle.kts`.
3. Update `CHANGELOG.md` and `release/RELEASE_NOTES.md`. The release notes become the GitHub
   release body.

## 2. Build and test

Follow [BUILDING](docs/BUILDING.md) with JDK 21, SDK 36, Rust 1.91.0 and NDK r27d. Build the native
library from this revision first, then run the Gradle checks with the native task skipped:

```bash
bash scripts/build-native.sh
cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease -x buildSoulseekNative
./gradlew :app:connectedDebugAndroidTest -x buildSoulseekNative   # on an emulator or device
```

The APK is `app/build/outputs/apk/release/app-release.apk`. Install it on a device or emulator and
go through the screens touched by this release.

## 3. Check the artifact

```bash
APK=app/build/outputs/apk/release/app-release.apk
BT="$ANDROID_HOME/build-tools/36.1.0"     # any current build-tools version

"$BT/aapt2" dump badging "$APK" | head -1  # package app.sonic, versionCode, versionName
unzip -l "$APK" | grep librunout_soulseek  # one line each: arm64-v8a armeabi-v7a x86_64 x86
unzip -l "$APK" | grep assets/licenses/    # native dependency notices are bundled
"$BT/zipalign" -c -P 16 -v 4 "$APK"
"$BT/apksigner" verify --print-certs "$APK"
```

Check that every native library has 16 KiB-aligned ELF load segments (the last column of each
`LOAD` line must be `0x4000` or larger):

```bash
rm -rf /tmp/sonic-libs && unzip -q "$APK" 'lib/*' -d /tmp/sonic-libs
READELF=$(ls "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/llvm-readelf)
for so in /tmp/sonic-libs/lib/*/*.so; do echo "$so"; "$READELF" -lW "$so" | grep LOAD; done
```

Compare the certificate SHA-256 from `apksigner` with the previous release's verification report.
If it differs, the build will not update existing installs: stop and find out why.

## 4. Checksum and verification report

Copy the checked APK into `release/` and write its checksum from inside that directory, so the
checksum file names the APK without a path:

```bash
cp app/build/outputs/apk/release/app-release.apk release/Sonic-<version>.apk
cd release
shasum -a 256 Sonic-<version>.apk > Sonic-<version>.apk.sha256   # or sha256sum on Linux
shasum -a 256 -c Sonic-<version>.apk.sha256
```

Write `release/Sonic-<version>-VERIFICATION.md`. Record:

- date, host and source commit;
- tool versions, and test and lint counts, including anything skipped and why;
- whether the native library was built from this revision;
- application ID, version name and code, min and target SDK, the four ABIs, 16 KiB alignment,
  `zipalign` and `apksigner` results, and the certificate SHA-256;
- the APK's SHA-256 and size;
- what was checked by hand and on which device, and what was not (physical phones, public
  Soulseek accounts, Bluetooth or USB DACs, TalkBack).

Commit the version bump, changelog, release notes, checksum and report. The APK itself is never
committed; it is a release asset. Checksums and reports from earlier versions stay in `release/`
as history.

## 5. Publish

Push the commit to `Abhiroop1122/runout`, then create the tag and release on that exact commit:

```bash
gh release create v<version> \
  release/Sonic-<version>.apk \
  release/Sonic-<version>.apk.sha256 \
  release/Sonic-<version>-VERIFICATION.md \
  --repo Abhiroop1122/runout --target <full-commit-sha> \
  --title 'Sonic <version>' --latest \
  --notes-file release/RELEASE_NOTES.md
```

Download the published APK and check it against the published checksum:

```bash
gh release download v<version> --repo Abhiroop1122/runout --pattern 'Sonic-*' --dir /tmp/sonic-check
(cd /tmp/sonic-check && shasum -a 256 -c Sonic-<version>.apk.sha256)
```

Creating a release is separate from opening a pull request.

## Development pre-releases

When a build has to be shared before a release, build the debug variant
(`./gradlew :app:assembleDebug -x buildSoulseekNative`), run the same checks on
`app/build/outputs/apk/debug/app-debug.apk`, and name the files `Sonic-<version>-development.apk`,
`.apk.sha256` and `Sonic-<version>-VERIFICATION.md`. Publish it as a pre-release that does not take
the Latest label:

```bash
gh release create v<version>-dev.<n> \
  release/Sonic-<version>-development.apk \
  release/Sonic-<version>-development.apk.sha256 \
  release/Sonic-<version>-VERIFICATION.md \
  --repo Abhiroop1122/runout --target <full-commit-sha> \
  --prerelease --latest=false --title 'Sonic <version> development' \
  --notes-file release/RELEASE_NOTES.md
```

The debug variant installs as a separate app (`app.sonic.dev`) beside the release.

## Older installs and what is still missing

Sonic's application IDs are new, so Sonic does not update an older Runout install (`app.runout`
or `app.runout.dev`). The two apps sit side by side with separate private data. Music in the
user's download folder is not affected.

The APK is signed with a debug key, not a production key, and the Soulseek client still reports
the experimental version 177/1. A Play Store release would need production signing, an allocated
Soulseek client identifier and interoperability checks with real accounts on representative
networks.
