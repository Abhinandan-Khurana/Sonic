# Runout handoff for Hermes

Updated: 2026-10-20, Asia/Kolkata.

This file transfers project context to Hermes or another coding agent. Continue the existing Android project; this handoff does not request a rewrite, a different application framework, or a Hermes runtime inside the APK. The workflows below use ordinary Git, Gradle, Cargo, Android SDK tools and Argent, so they do not depend on Codex tools or conversation history.

## Start here

1. Read this file, [README.md](README.md), [architecture](docs/ARCHITECTURE.md) and [developer notes](docs/DEVELOPMENT.md). Follow any applicable repository instructions.
2. Inspect `git status`, the current branch and recent commits before editing. Preserve any work added after this handoff.
3. Treat the released app and the remaining work below as separate. The latest application implementation is already pushed and released; Internet Archive is **not integrated into unified search**.
4. Keep the user's retro vinyl home, FLAC-only policy, account-free offline playback, Internet Archive downloads and Soulseek messaging.
5. Do not open a pull request unless the user changes their explicit instruction. The user has authorized pushing work to their fork and publishing development APKs; that does not authorize merging upstream or treating a development build as production-ready.

## Repository and release baseline

| Item | Value |
| --- | --- |
| Local checkout on this machine | `/home/som/Documents/github/runout` |
| User's fork / `origin` | <https://github.com/Somchandra17/runout> |
| Current working branch at handoff | `main` |
| Latest application implementation commit | `64e12227fa42490f2f90a0b1f57e17abc017d352` |
| Previous integration commit | `7d7b3b4186114b08f84aad9f6741ab4ee516ce94` |
| Released tag | `v0.4.1-dev.1` |
| App version | `0.4.1`, version code `5` |
| Development application ID / name | `app.runout.dev` / **Runout Dev** |
| Normal application ID | `app.runout` |
| Android compatibility | min SDK 28; compile/target SDK 36 |

The checkout was clean at the application commit before creating this handoff. A later documentation commit containing this file does not change the released APK's source baseline. Re-check the actual repository state rather than assuming the above commit is still HEAD.

- [Release and assets](https://github.com/Somchandra17/runout/releases/tag/v0.4.1-dev.1)
- [Universal development APK](https://github.com/Somchandra17/runout/releases/download/v0.4.1-dev.1/Runout-0.4.1-development.apk)
- [Detailed verification report](release/Runout-0.4.1-VERIFICATION.md)
- [Committed APK checksum](release/Runout-0.4.1-development.apk.sha256)

Published APK SHA-256:

```text
31d885d61f8b0e64e8622e003540b8a2c60504018385b84b6683876bbfb5b9cd
```

The published download was downloaded again and its checksum and signature verified. No PR was created.

## User intent and decisions to preserve

- Music must be FLAC-only across scanning, importing, playback, sharing, search admission and downloads. Disguised MP3 files must fail byte validation. Artwork, `.vinyl` containers and synthesized turntable sounds are separate assets.
- Local playback and local metadata search work without signing in or having a network connection.
- Downloads from both Soulseek and Internet Archive require a connected Soulseek account. There is no separate Runout account server, and Archive never receives Soulseek credentials.
- Online chat uses Soulseek private messages. Public chatrooms are not implemented.
- First-use setup offers offline listening or Soulseek setup, followed by download storage, optional sharing and explained app-only/background connection choices.
- Sharing consent, background consent and the selected sharing folder are scoped to the exact account. A folder grant alone is not consent to publish its files.
- Preserve and polish the retro vinyl player home. The current main destinations are Home, Library, Discover and Messages; account/download access is global.
- Search should be fast for both songs and albums. Seeker is the user's reference for responsiveness and discovery behavior.
- The user prefers implementation and verification over repeated confirmation questions. Use judgment within the requested task; ask when a material product decision is genuinely unresolved.

## Latest conversation: Internet Archive search is missing

The user asked where Archive results appear when searching. The confirmed answer was:

> Archive is currently under Discover → Friday Drops. The search box searches the local library and Soulseek only. “All sources” does not include Archive, which makes that label misleading.

This gap is **not fixed**. The next user request was to create this handoff, not to implement Archive search. Do not describe Archive search as shipped or silently expand this documentation task into an implementation.

Relevant code:

- `discovery/Discovery.kt`: `SearchSource` has only `ALL`, `LOCAL`, `SOULSEEK`.
- `discovery/DiscoveryRepository.kt`: combines local metadata and Soulseek results; no Archive search provider.
- `ui/DiscoveryScreen.kt`: search, filters, sorting and album/folder presentation.
- `ui/RunoutScreen.kt`: Discover switches between **Find music** and **Friday Drops**.
- `drops/Drops.kt`: Archive's existing weekly discovery/download implementation.

All Kotlin paths above are relative to `app/src/main/java/app/runout/`.

Friday Drops currently chooses ten Creative Commons netlabel albums each week and allows three successful claims. `DropsRepository.claim()` explicitly rejects an identifier outside the current week's selection. **Do not route arbitrary Archive search results into that method and expect general downloads to work.**

If the user authorizes unified Archive search, a sensible continuation is to add a distinct Archive search/provider model with source labels, pagination/cancellation, FLAC availability checks and the existing validated publication boundary. Preserve Friday Drops and its allowance semantics. The allowance policy for arbitrary Archive search downloads has not been decided; do not silently remove the weekly limit or apply it to a new catalog. Verify Archive API behavior against official documentation before implementation. Item-level search metadata alone must not be treated as proof that downloadable FLAC exists.

## What is implemented in 0.4.1

- Direct Soulseek integration through a Kotlin repository and Rust JNI adapter; the app does not launch a Soulseek CLI process.
- One native connection owner, retained account identity during connection loss, bounded automatic reconnect, and distinct failure reasons.
- Intentional/manual disconnect and another-client displacement stop automatic reconnect fights. Opening Seeker or another client with the same ID can displace Runout.
- Process lifecycle handling replaces activity-level disconnects. External picker/permission flows suppress Runout's idle pause. Android can still interrupt cached-app sockets; the account remains and reconnection occurs on return.
- Optional notification-backed background mode. Default app-only mode pauses an idle session after five seconds in the background and resumes when appropriate.
- FLAC search results stream incrementally. Native replies are drained every 100 ms in batches of at most 100 files, with a 5,000-FLAC cap.
- Android peer admission is bounded to 48 connections. A ten-second grace prevents newly admitted discovery peers being evicted before replying; explicit browse/download work can take priority.
- Stable cached file identities/path components, metadata-only local indexing, normalized local matching with small typo tolerance, and no repeated local scan for every peer batch.
- Song/album/all views, quality/availability filters and match/availability/quality sorting. Unknown quality is visible by default.
- Results group by exact peer and full directory before list virtualization. Album preview fetches the full FLAC folder listing and requires separate download confirmation.
- Downloads use private staging plus validated copies into the user's selected Android document folder. Completed music survives logout and app uninstall.
- Durable private messages, transfer/contact/message account isolation, commit-before-ack and replay deduplication.
- Search can start a local song on its correct record side. Changing records cancels pending playback from the previous needle-settling ramp.
- Internet Archive Friday Drops remains functional; no generic Archive results appear in unified search.

## Architecture and source map

| Responsibility | Main entry points |
| --- | --- |
| App composition and process/network observation | `RunoutApp.kt`, `AppGraph` |
| UI state collection, actions, Android pickers and permission | `MainActivity.kt` |
| Navigation, onboarding, discovery | `ui/RunoutScreen.kt`, `SetupScreen.kt`, `DiscoveryScreen.kt` |
| Vinyl room and controls | `ui/ListeningRoom.kt`, `Controls.kt`, `Room.kt`, turntable/crate components |
| Search models, local index, grouping/ranking | `discovery/Discovery.kt`, `DiscoveryRepository.kt` |
| Single connection, retry, generations, remembered account | `soulseek/SoulseekConnectionManager.kt` |
| Transfers, messages, shares, account state, publication | `soulseek/SoulseekRepository.kt` |
| Consent/onboarding persistence | `soulseek/SoulseekPreferences.kt` |
| Room storage and optional secure credentials | `soulseek/SoulseekDatabase.kt`, `SoulseekCredentialVault.kt` |
| Android document destination and staging | `soulseek/DownloadStorage.kt` |
| Injectable transport / JNI handle | `soulseek/SoulseekTransport.kt`, `NativeSoulseek.kt` |
| Foreground services | `soulseek/SoulseekServices.kt` |
| FLAC admission and legacy indexes | `library/FlacPolicy.kt`, `AlbumJson.kt`, `LibraryScanner.kt` |
| Internet Archive selection/claims | `drops/Drops.kt` |
| Playback and sound | `playback/Deck.kt`, `PlaybackService.kt`, `sound/` |
| Protocol adapter and pinned MIT core | `native/bridge/src/lib.rs`, `native/vendor/soulseek-rs-lib/` |

Kotlin owns application policy, persistence, Android storage and playback; Rust owns protocol framing and peer networking. UI code requests repository actions rather than opening sockets or querying storage. The legacy `account/`, `nostr/` and friend-card code remains for historical data/tests; the production graph does not start Nostr sessions.

Keep these invariants:

1. `hasAccount` and `connected` are different. Temporary loss must not show a fresh-sign-in flow or erase account history; downloads and sends still need a live authenticated connection.
2. Keep one connection owner and generation checks. Old native events or share scans must not affect a replacement account/session.
3. Acknowledge an incoming message only after its database transaction commits. “Sent” means transport acceptance, not a delivery/read receipt.
4. Preserve byte validation and serialized publication. Cancellation must not delete already published music.
5. Use SAF document grants, never broad storage access or URI-to-filesystem guesses. Native shares use opaque keys and host-opened descriptors.
6. Retain stable persisted identities. Room schema is version 1 with exported schemas; installed-data changes require explicit migrations, not destructive recreation.
7. Keep network, matching and file work off the UI thread; retain streaming, bounded memory and stable-key lazy lists.

## Build on another machine

Use Linux/Bash, JDK 21, Android SDK platform/build tools 36, Rust 1.91.0 and Android NDK r27d (`27.3.13750724`) or newer. Gradle wrapper version is 8.14.3. See [BUILDING.md](docs/BUILDING.md) for details.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.3.13750724"
export CARGO="$HOME/.cargo/bin/cargo"
export RUSTUP="$HOME/.cargo/bin/rustup"

rustup toolchain install 1.91.0 --profile minimal
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked
```

`preBuild` runs `scripts/build-native.sh`, adding Android Rust targets and building all four ABIs into `app/build/generated/soulseekJni/`. Keep this task enabled when distributing an APK. The native task uses the locked dependencies, Android API 28 and 16 KiB alignment.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

`assembleRelease` currently also uses prototype debug signing. It is not a production-signed update. Preserve the existing signing certificate when updating an installed development build; do not tell the user to uninstall their original app to bypass a signature mismatch.

## Verified tools on this machine

These paths existed when preparing this handoff; they are local conveniences, not portable prerequisites:

```bash
export JAVA_HOME='/home/som/.local/share/runout-build/jdk/jdk-21.0.12.1+1'
export ANDROID_HOME='/home/som/Android/Sdk'
export ANDROID_NDK_HOME='/home/som/.local/share/runout-build/android-ndk-r27d'
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$HOME/.cargo/bin:$PATH"
```

Argent 0.29.0 is installed outside the repository at `/home/som/.local/share/runout-build/argent/node_modules/.bin/argent`. Project telemetry is disabled in `.argent/config.json`. Hermes can call the same CLI directly; no particular MCP connection is required.

Two test AVDs are available. Both were stopped after testing:

```bash
# Android 17 / API 37; existing AVD has a different directory basename.
"$ANDROID_HOME/emulator/emulator" -avd Medium_Phone_API_37.0 \
  -port 5554 -grpc 8554 -grpc-use-token \
  -no-window -no-audio -no-snapshot-save -gpu swiftshader_indirect

# Android 15 / API 35; this AVD uses the XDG configuration location.
ANDROID_AVD_HOME='/home/som/.config/.android/avd' \
  "$ANDROID_HOME/emulator/emulator" -avd Runout_API_35 \
  -port 5556 -grpc 8556 -grpc-use-token \
  -no-window -no-audio -no-snapshot-save -gpu swiftshader_indirect
```

Run long-lived emulator commands in separate terminals/process sessions. Assign distinct ADB/gRPC ports. For a visible emulator, omit `-no-window`. Re-check the actual device inventory before installation or input; these serials describe the test setup, not a user's physical phone.

## Verification baseline and how to repeat it

The 0.4.1 report records **148 JVM/Compose tests: 147 passed, one historical opt-in Nostr relay test skipped; 332 native tests passed; four local Android tests passed on each of API 35 and 37; zero lint errors**. There were 60 nonfatal lint warnings and five hints. These are historical release results, not claims about future changes.

```bash
# After booting the intended emulator/device:
./gradlew connectedDebugAndroidTest
```

For a specific device after installing both APKs:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e notClass app.runout.SoulseekLiveNetworkTest \
  app.runout.dev.test/androidx.test.runner.AndroidJUnitRunner
```

The local packaged-JNI test uses two actual loopback peers and covers search, browse, FLAC transfer, file descriptors, messages and acknowledgements. Other checks exercise Android Keystore and account-free playback. Useful regression suites include `SoulseekConnectionTest`, `SoulseekRepositoryTest`, `DiscoveryTest`, `RunoutFlowTest` and `TurntableDeckTest`.

`DiscoveryPerformanceTest` warms up, then measures ten samples over 10,000 local tracks and 5,000 remote files. Nearest-rank p95 targets are 150 ms local and 250 ms remote, excluding construction, network and rendering. Release observations were 16.3/134.0 ms on API 35 and 33.1/115.8 ms on API 37. They are not a speed comparison with Seeker.

Reports: `app/build/reports/tests/`, `app/build/reports/androidTests/`, `app/build/reports/lint-results-debug.html`; fixture UI captures: `app/build/outputs/roborazzi/`.

### Device flows and live-account testing

Read [.argent/README.md](.argent/README.md) and each flow's prerequisite. Available flows cover offline navigation/song playback, Soulseek setup, public discovery, picker return and network loss/recovery. Ordinary tests need no public account.

- Do not run instrumentation and UI automation against the same app/device simultaneously: instrumentation restarts the process.
- gRPC input can trigger emulator stylus handwriting and defer typed text. On the test emulator, disable it with `adb -s <serial> shell settings put secure stylus_handwriting_enabled 0`. Focus, wait for layout/keyboard, type, then assert the result. Do not blindly retry a secret that might already be present.
- Folder-picker Back may navigate to the parent before cancelling. The checked-in picker flow expects the storage root or one level below.
- Real Soulseek permits one session per account. Do not sign into two emulators, Seeker and Runout, or a separate native probe with the same ID concurrently.
- The prior user-supplied test credentials were deliberately removed. **This handoff contains no username or password.** Do not recover credentials from chat history, logs or temporary files; obtain fresh secure provisioning if a live test is needed.
- Explicit live checks use ignored `.argent/secrets.env`, mode 0600, with `ARGENT_SECRET_SLSK_USERNAME` and `ARGENT_SECRET_SLSK_PASSWORD`. Never put values in runner arguments or committed files. Use `python3 scripts/check-live-soulseek.py --device <serial> --seconds 900` after reading its behavior.
- The helper provisions a private device file and removes it in `finally`. Also sign out and remove local test secrets after testing. Restore network settings in `finally` for interruption tests. Never send unsolicited messages to test chat.

The released native library passed a 900.137-second public session with no disconnect, three searches returning 2,729 FLAC files, and first batches in 0.801–5.409 seconds. App UI checks passed two Wi-Fi/mobile-data interruptions without re-entering credentials, picker return, notification denial/grant, background connection retention and a fresh search after reconnection. Those observations do not guarantee every peer or carrier works.

Debug log tags: `RunoutSession` for sanitized connection/lifecycle reasons, `RunoutLive` for explicit probe metrics, `RunoutBenchmark` for timing. Do not log credentials or private message content.

## References, licensing and remaining limits

- [SeekerAndroid](https://github.com/jackBonadies/SeekerAndroid), inspected at commit `ff1dcc6de55a412e8d6eadaddf36deca54517413`, informed streaming, folder grouping, availability and connection-state behavior. Its GPL implementation was not copied.
- [Nicotine+ protocol reference](https://nicotine-plus.org/doc/SLSKPROTOCOL.html), [Nicotine+](https://github.com/nicotine-plus/nicotine-plus), [Soulseek.NET](https://github.com/jpdillingham/Soulseek.NET) and [Argent](https://github.com/software-mansion/argent) are investigation/tooling references, not bundled replacement runtimes.
- Runout remains MIT. The vendored core is MIT `soulseek-rs` v19.1.0 at `dc39e24ad7909b93f600e60f19807db1f8f1c3ad`. See `native/vendor/UPSTREAM` and [native/README.md](native/README.md) for the patch inventory and JNI contract. Preserve notices and document future vendor changes.
- Production signing and an appropriate Soulseek production client identifier remain outstanding. Development uses experimental protocol version 177/1.
- Soulseek credentials/messages are plaintext on the protocol; optional remembered credentials use Android Keystore locally. The onboarding explains this.
- Physical ARM devices, every Android background policy and every mobile network/router have not been verified. Maximum font scaling still constrains dense physical deck/sleeve labels. Very large message histories may need database pagination.
- CI is an **inactive template** under `.github/workflows-templates/`. The publishing login previously lacked workflow-write permission. Do not claim CI is active or work around GitHub's permission rejection by adding an active workflow without the required authorization/access.
- Use [RELEASE.md](RELEASE.md) for future publication. New APKs belong in GitHub release assets, with checksums and a verification report, not Git history. Keep the production app's signing identity and user files intact.

## Suggested first message to Hermes

> Continue the Runout Android project from this repository. Read Handsoff.md first, inspect the current Git state and preserve the existing FLAC-only, offline, Soulseek and retro-player behavior. The latest unresolved user discussion concerns missing Internet Archive results in unified search; it is documented as unimplemented, not an approved code change in this handoff. Establish the current requested scope with me before changing that feature. Use the documented build/test tooling, keep credentials out of source, and do not open a PR.

