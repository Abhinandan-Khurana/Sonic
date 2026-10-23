# Building and testing Sonic

## Prerequisites

Use Linux or macOS with Bash 4 or newer (on macOS, Homebrew `bash`; the system Bash 3.2 cannot run the native script), JDK 21, Android SDK platform 36, Android build tools, Rust 1.91.0 through rustup, and Android NDK r27d (27.3.13750724) or newer. The Gradle wrapper supplies Gradle 8.14.3.

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

`preBuild` invokes `scripts/build-native.sh`. Native artifacts go into `app/build/generated/soulseekJni/`, then Gradle packages them into the APK. Do not distribute an APK whose native libraries were not built from the source revision being released; skipping the Gradle task is fine once the script has run (see below).

The script pins Rust 1.91.0 and the Cargo lockfile, targets Android API 28, and builds arm64-v8a, armeabi-v7a, x86_64 and x86. All native libraries use 16 KiB load-segment alignment. Native build artifacts and local SDK settings are ignored by Git.

Debug APK: `./gradlew assembleDebug`, output `app/build/outputs/apk/debug/app-debug.apk`. It installs as **Sonic. Dev** (`app.sonic.dev`) next to the release app, with its own private data.

Release APK: `./gradlew assembleRelease`, output `app/build/outputs/apk/release/app-release.apk`. It installs as **Sonic.** (`app.sonic`). The release build type is still signed with the local debug key, so an update installs over an existing copy only when it was built with the same key; the published releases are built by the maintainer for that reason. Neither ID replaces an older Runout install (`app.runout` or `app.runout.dev`): those stay as separate apps.

### Building the native library separately

`preBuild` runs the native task on every Gradle build. To build the libraries once and then iterate on Kotlin, run the script directly and skip the task afterwards:

```bash
bash scripts/build-native.sh            # writes app/build/generated/soulseekJni/<abi>/librunout_soulseek.so
./gradlew :app:assembleRelease -x buildSoulseekNative
```

`RUNOUT_JNI_OUTPUT` sends the libraries to another directory; copy them into `app/build/generated/soulseekJni/<abi>/` before packaging. Pass `-x buildSoulseekNative` to every Gradle command in that session: a failed run of the native task can leave the output directory empty, and `./gradlew clean` deletes it. Before installing or publishing, check that the APK contains `lib/<abi>/librunout_soulseek.so` for all four ABIs.

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

[The CI template](../.github/workflows-templates/README.md) runs native tests, builds all ABIs, runs JVM tests/lint and uploads reports and APKs on Ubuntu. It is not active yet: enabling it needs a GitHub login with the separate `workflow` scope, which the publishing login does not have. Run `scripts/enable-ci.sh` to copy the template into `.github/workflows/` and push it with such a login. Device instrumentation remains an explicit check in the release process.

## Android UI flows and explicit live checks

Install pinned Argent 0.29.0 outside the app and use the [checked-in flows](../.argent/README.md). These cover offline navigation, guided sign-in, streaming discovery and folder-picker recovery. Inspect each flow's starting state before running it. For two emulators, assign distinct ADB and gRPC ports; an explicitly configured gRPC port needs `-grpc-use-token` for Argent's authenticated transport discovery.

```bash
argent flow run .argent/flows/offline-navigation.yaml --device emulator-5554
# Requires an explicitly supplied test account, not ordinary CI:
argent flow run .argent/flows/soulseek-setup.yaml --device emulator-5554
argent flow run .argent/flows/live-discovery.yaml --device emulator-5554
argent flow run .argent/flows/picker-recovery.yaml --device emulator-5554
```

`DiscoveryPerformanceTest` runs on Android with 10,000 local tracks and 5,000 remote files, reporting conservative warm p95 timings to the `RunoutBenchmark` log tag. Test both API 35 and API 37 when available. Device timing excludes fixture/index construction and network time; the release report identifies the measured environment.

`SoulseekLiveNetworkTest` skips unless explicitly enabled. Never put a real username/password in runner arguments or source. Create ignored `.argent/secrets.env` with `ARGENT_SECRET_SLSK_USERNAME` and `ARGENT_SECRET_SLSK_PASSWORD`, set mode 0600, and use the helper after installing the debug and Android test APKs:

```bash
python3 scripts/check-live-soulseek.py --device emulator-5554 --seconds 900
adb -s emulator-5554 logcat -d -s RunoutLive:I '*:S'
```

The helper copies only those two values into private app storage through stdin, runs the opt-in probe and removes the device file in `finally`. An explicitly requested probe with a missing device account file fails rather than reporting a skipped success. Metrics contain connection state, counts and time to first batch; the test downloads nothing and sends no messages. Ordinary CI still uses local peers only.

Keep one client per real Soulseek ID: another emulator, Seeker or a second probe can displace the session under test. Do not run instrumentation while interacting with the same app through Argent, because instrumentation restarts its process. Remove local secrets and sign out of test devices when finished. Live account checks and published APKs must never contain test credentials.
