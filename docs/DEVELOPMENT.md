# Developer notes

Use [BUILDING](BUILDING.md) for setup and [ARCHITECTURE](ARCHITECTURE.md) for the component map. The main source roots are `app/src/main/java/app/runout/` and `native/`.

Sonic was called Runout before 1.0. The rename changed the application IDs (`app.sonic`, and `app.sonic.dev` for debug builds) and the visible name. Internal names were kept on purpose: the Kotlin package and namespace `app.runout`, the `RunoutApp` class, the native crate and `librunout_soulseek.so`, the JNI symbols, the `Runout*` log tags and the repository name. Renaming any of them is a code change with no user-visible benefit; leave them unless there is a reason.

## Where to change behavior

Paths are relative to `app/src/main/java/app/runout/` unless they start with `native/`.

| Area | Entry points | Checks to prioritize |
| --- | --- | --- |
| Panels and navigation | `ui/ListeningRoom.kt`, `Panels.kt`, `settings/` (Settings, Sound, Soulseek, Storage and Flags pages), `SetupScreen.kt`, `DiscoveryScreen.kt`, `MainActivity.kt` | Compose behavior, portrait layout, keyboard, TalkBack |
| The 3D deck | `ui/Turntable.kt`, `ui/deck/` (Filament renderer, deck model, record maps) | GPU start-up time, colourways, record swap while playing |
| Search and ranking | `discovery/Discovery.kt`, `DiscoveryRepository.kt` | Incremental batches, full folder groups, 10k local/5k remote benchmarks |
| Connection lifecycle | `soulseek/SoulseekConnectionManager.kt`, `RunoutApp.kt` | Network loss, picker/background transitions, displacement, stale events |
| FLAC admission and migration | `library/FlacPolicy.kt`, `AlbumJson.kt`, `LibraryScanner.kt` | Disguised files, ID3-prefixed FLAC, mixed legacy albums |
| Account, messages and transfers | `soulseek/SoulseekRepository.kt`, `SoulseekDatabase.kt` | Account isolation, commit-before-ack, interrupted work |
| Consent and onboarding state | `soulseek/SoulseekPreferences.kt`, `SoulseekCredentialVault.kt` | Per-account sharing and background choices, optional remembered credentials |
| Completed download storage | `soulseek/DownloadStorage.kt` and repository publication | SAF grants, failed writes/rename, retry, retained files |
| Archive collection and allowance | `drops/Drops.kt` | Weekly determinism, no FLAC fallback, no claim on failure |
| Wire protocol / JNI | `native/bridge/src/lib.rs`, `soulseek/NativeSoulseek.kt`, `SoulseekTransport.kt` | Host tests, four ABIs, packaged Android peer test |
| Foreground services | `soulseek/SoulseekServices.kt` | Transfer and stay-online notifications, Android service timeouts |
| Playback and sound | `playback/`, `sound/` | Local signed-out playback, route/DSP behavior |
| Portable records | `sharing/`, `pressing/` | Archive traversal, checksums, ownership/license rules |

## Product decisions to keep

- Music is FLAC-only across scanning, importing, playback, sharing, search admission and downloads. Disguised MP3 files must fail byte validation. Artwork, `.vinyl` containers and the synthesised turntable sounds are separate assets.
- Local playback and local metadata search work without signing in or a network connection.
- Downloads from both Soulseek and Internet Archive need a connected Soulseek account. Archive requests never carry Soulseek credentials.
- Chat is Soulseek private messages. Public chatrooms are not implemented.
- Search never plays anything. "Put on the platter" cues the record with the needle up, and the deck's START/STOP plays it. The deck is the only player of your records; Android notification, lock-screen and headset controls stay.
- The UI follows the design in `docs/design/` (see the [docs index](README.md)). Changes outside that design need the maintainers' agreement first.

## Invariants that need to survive refactoring

1. Local playback must not initialize the native client or depend on an online account. Both download providers require a successful Soulseek session.
2. A message is acknowledged only after its Room transaction commits. A replay must not create duplicate history. "Sent" means the transport accepted it, not that it was delivered or read.
3. An old session's events must not affect a newer account. Preserve generation checks and owner checks when moving asynchronous work.
4. A completed transfer must refer to validated FLAC in the user's selected destination. Keep publication serialized and cancellation checks around finalization. Cancellation never deletes music that was already published.
5. Native share paths are virtual peer paths. Android document URIs are not filesystem paths; only the host opens a selected document and passes a descriptor. Sharing starts disabled for new accounts. Consent and folder selection are scoped to the exact account. Discard stale scans before publishing a manifest.
6. Never infer a redistribution license from a download provider or mark Soulseek albums as user-owned automatically.
7. Keep network, matching and file work off the main thread, with streaming results, bounded memory and stable-key lazy lists.

Account identity and a connected transport are separate: temporary loss must not empty the account UI or ask for credentials again. Manual disconnect and displacement must not start retry wars. Avoid activity-level disconnects; external pickers are expected flows.

Use `SoulseekTransport` injection and the Room/document-provider fixtures for repository tests. Do not add a second authentication gate in a panel: enforce policy in repository entry points so alternate callers remain covered. Keep platform storage operations in `DownloadStorage`, protocol mechanics in Rust, and presentation in Compose.

## Database evolution

Version 1 is the first Soulseek database. The [Room Gradle plugin](https://developer.android.com/jetpack/androidx/releases/room) isolates each build task's intermediate schema output and copies the consistent schema into `app/schemas/app.runout.soulseek.SoulseekDatabase/`. Commit the generated schema whenever entities change. For an installed schema change, increment the version, register an explicit Room migration and verify retained messages, transfers and offline albums. Do not add `fallbackToDestructiveMigration` to make an upgrade pass.

Legacy library indexes remain JSON and use the non-destructive `AlbumJson` migration. Room does not replace the existing crate, pressing or Archive indexes.

## Native maintenance

The core is vendored source, not a downloaded Android binary: MIT `soulseek-rs` v19.1.0, with the exact commit recorded in `native/vendor/UPSTREAM`. Keep adapter changes in `native/bridge` when possible; describe every vendor delta in `native/README.md`. Preserve MIT attribution and update the bundled dependency notices (`app/src/main/assets/licenses/`) when Cargo dependencies change.

The native script runs on Linux and macOS hosts with Bash 4 or newer. All four ABIs are built with the locked dependency graph and 16 KiB alignment. A host Rust test alone cannot verify JNI method names, Android descriptors or native packaging: run the Android instrumentation tests after changing those boundaries.

Default tests bind local servers only. Public Soulseek interoperability is an explicit opt-in exercise using an account you control. Never include real credentials in fixtures or public issue reports. The client still reports the experimental Soulseek version 177/1; an allocated client identifier is outstanding.

## Testing

The commands are in [BUILDING](BUILDING.md#tests). To run the instrumentation tests on one device without the opt-in live network probe, install both APKs and exclude it:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e notClass app.runout.SoulseekLiveNetworkTest \
  app.sonic.dev.test/androidx.test.runner.AndroidJUnitRunner
```

The packaged-JNI test uses two loopback peers and covers search, browse, FLAC transfer, file descriptors, messages and acknowledgements. Useful JVM regression suites include `SoulseekConnectionTest`, `SoulseekRepositoryTest`, `DiscoveryTest`, `RunoutFlowTest`, `RecordCardTest` and `TurntableDeckTest`.

For a headless test emulator that Argent can drive, give it distinct ADB and gRPC ports and a gRPC token:

```bash
"$ANDROID_HOME/emulator/emulator" -avd <avd-name> \
  -port 5554 -grpc 8554 -grpc-use-token \
  -no-window -no-audio -no-snapshot-save -gpu swiftshader_indirect
```

Omit `-no-window` for a visible emulator. Do not run instrumentation and UI automation against the same app and device at the same time: instrumentation restarts the process.

## Device flows and performance

Use the pinned [Argent flows](../.argent/README.md) for offline navigation, sign-in, live discovery, picker recovery, network loss and deck start-up. A completed gesture is not proof of an action: assert the resulting screen/data. Focus fields, wait for keyboard layout, then type. On the test emulator, disable stylus handwriting first (`adb -s <serial> shell settings put secure stylus_handwriting_enabled 0`). Folder-picker Back may go to the parent folder before it cancels. Use one real session per account across all emulators; a second native probe, Seeker or another client will displace the first.

`DiscoveryPerformanceTest` measures warm local matching over 10,000 tracks and reduction of 5,000 remote files on Android. It reports the slowest of ten samples as a conservative nearest-rank p95, with targets of 150 ms and 250 ms respectively. This excludes index/fixture construction, network latency and rendering. Host timings are useful for regressions but do not substitute for device results. Preserve streaming and avoid full accumulated result copies, per-comparator normalization, per-byte formatted hashes, and re-running local matching on every peer batch.

## Debugging and known limits

Check `app/build/reports/` first. Record the visible account/transfer state and the storage provider in a bug report; redact credentials and message bodies. Distinguish peer reachability from local folder access failures. Logs should identify operations without exposing private content.

Debug log tags: `RunoutSession` for sanitized connection state, reasons and lifecycle (no usernames, passwords, query text or message bodies), `RunoutLive` for the opt-in live probe and `RunoutBenchmark` for timing. Use `RunoutSession` to tell an app-initiated pause from Android interrupting background sockets.

The application scope and `AppGraph` are intentionally small and explicit. There is no background work scheduler or hosted backend. Android may stop foreground data-sync work; the UI supports reopening and resuming. Avoid expanding the native/host command surface for UI-only changes.

Known gaps:

- **Internet Archive is not part of search.** `SearchSource` has only `ALL`, `LOCAL` and `SOULSEEK`, so "All sources" means this device plus Soulseek. Archive albums appear only through Friday Drops. `DropsRepository.claim()` rejects any identifier outside the current week's selection, so arbitrary Archive results cannot be routed through it. An Archive search provider would need its own model, source labels, pagination and cancellation, a FLAC availability check per item (search metadata alone does not prove a FLAC exists) and the same publication boundary. The download allowance for such results has not been decided.
- Message history loads per account and may need database pagination for very large archives.
- Maximum font scaling still crowds the dense deck and sleeve labels.
- Testing has been mostly on emulators and one Pixel 6. Other phones, every Android background policy and every mobile network have not been verified.

## References

[SeekerAndroid](https://github.com/jackBonadies/SeekerAndroid) (inspected at `ff1dcc6`) informed streaming results, folder grouping, availability and connection-state behavior; none of its GPL code was copied. The [Nicotine+ protocol reference](https://nicotine-plus.org/doc/SLSKPROTOCOL.html), [Nicotine+](https://github.com/nicotine-plus/nicotine-plus), [Soulseek.NET](https://github.com/jpdillingham/Soulseek.NET) and [Argent](https://github.com/software-mansion/argent) are investigation and tooling references, not bundled runtimes. Sonic is MIT licensed.

## Release discipline

Published releases are the **release variant** (`app.sonic`, label **Sonic.**), signed with the maintainer's debug key so that later releases update it in place. Debug builds (`app.sonic.dev`, **Sonic. Dev**) are for development and install beside the release app. Debug keys differ between machines, so an APK you build yourself will not update a published one; do not advise uninstalling to work around a signature mismatch without warning that it removes the app's private data.

Follow [RELEASE.md](../RELEASE.md) for checks, tags, checksums and publication. The [inactive CI template](../.github/workflows-templates/README.md) builds and uploads test artifacts when enabled; it does not publish GitHub releases. Run `scripts/enable-ci.sh` to copy it into `.github/workflows/`; pushing a workflow file needs a login with the separate `workflow` scope.
