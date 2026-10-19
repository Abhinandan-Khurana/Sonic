# Building and testing Runout 0.4

## Prerequisites

Use Linux with Bash, JDK 21, Android SDK platform 36, Android build tools, Rust 1.91.0 through rustup, and Android NDK r27d (27.3.13750724) or newer. The Gradle wrapper supplies Gradle 8.14.3.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.3.13750724"
rustup toolchain install 1.91.0 --profile minimal
```

Install SDK platform `platforms;android-36`, build tools, and the selected NDK with your SDK manager. The native script adds the four Android Rust targets using rustup. Set `CARGO` and `RUSTUP` if they are outside their normal `$HOME/.cargo/bin` locations.

## Build

```bash
./gradlew assembleDebug
```

`preBuild` invokes `scripts/build-native.sh`. Native artifacts go into `app/build/generated/soulseekJni/`, then Gradle packages them into the APK. Do not distribute an APK assembled with the native task excluded.

The script pins Rust 1.91.0 and the Cargo lockfile, targets Android API 28, and builds arm64-v8a, armeabi-v7a, x86_64 and x86. All native libraries use 16 KiB load-segment alignment. Native build artifacts and local SDK settings are ignored by Git.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. It installs as **Runout Dev** (`app.runout.dev`) alongside the original app, because the supplied v0.3 APK uses a different signing key. The original app's private data are never removed to install this build.

Release APK: `./gradlew assembleRelease`, output `app/build/outputs/apk/release/app-release.apk`. As in the prototype, release signing currently uses the local debug key. Supply your production signing configuration before public release. Matching the existing app's signing key is required for an update that preserves its private data.

## Tests

```bash
cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked
./gradlew testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

The first command runs the core and adapter's local unit tests. It intentionally excludes upstream integration suites requiring a separate Soulfind installation. Repository tests inject a transport seam, use real Room persistence and document-provider storage, and verify authentication gates, durable message acknowledgements, replay deduplication, account isolation and validated FLAC publication.

The device integration test uses the actual packaged native library and a central test server bound to device loopback. Two embedded peers search, browse, share and transfer a real FLAC fixture and exchange private messages. Public Soulseek servers and accounts are never used by these tests.

Compose screenshots are written to `app/build/outputs/roborazzi/`. Unit reports are under `app/build/reports/tests/`; Android integration reports are under `app/build/reports/androidTests/`; lint reports are under `app/build/reports/lint-results-debug.html`.

The legacy `LiveRelayCheck` remains opt-in for historical Nostr code and is skipped by default. It is unrelated to the production Soulseek session.

## Continuous integration

[The CI template](../.github/workflows-templates/README.md) runs native tests, builds all ABIs, runs JVM tests/lint and uploads reports and APKs on Ubuntu. It is not active in this fork yet: the publishing login lacks GitHub's separate workflow-write permission. Copy the template into `.github/workflows/android.yml` using an authorized login to enable it. Device instrumentation remains an explicit check in the release process.
