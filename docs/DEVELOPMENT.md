# Developer notes

Use [BUILDING](BUILDING.md) for setup and [ARCHITECTURE](ARCHITECTURE.md) for the component map. The main source roots are `app/src/main/java/app/runout/` and `native/`.

## Where to change behavior

| Area | Entry points | Checks to prioritize |
| --- | --- | --- |
| Panels and navigation | `ui/ListeningRoom.kt`, `ui/SoulseekPanels.kt`, `MainActivity.kt` | Compose behavior, portrait layout, keyboard, TalkBack |
| FLAC admission and migration | `library/FlacPolicy.kt`, `AlbumJson.kt`, `LibraryScanner.kt` | Disguised files, ID3-prefixed FLAC, mixed legacy albums |
| Account, messages and transfers | `soulseek/SoulseekRepository.kt`, `SoulseekDatabase.kt` | Account isolation, commit-before-ack, interrupted work |
| Completed download storage | `soulseek/DownloadStorage.kt` and repository publication | SAF grants, failed writes/rename, retry, retained files |
| Archive collection and allowance | `drops/Drops.kt` | Weekly determinism, no FLAC fallback, no claim on failure |
| Wire protocol / JNI | `native/bridge/src/lib.rs`, `NativeSoulseek.kt` | Host tests, four ABIs, packaged Android peer test |
| Playback and sound | `playback/`, `sound/` | Local signed-out playback, route/DSP behavior |
| Portable records | `sharing/`, `pressing/` | Archive traversal, checksums, ownership/license rules |

## Invariants that need to survive refactoring

1. Local playback must not initialize the native client or depend on an online account. Both download providers require a successful Soulseek session.
2. A message is acknowledged only after its Room transaction commits. A replay must not create duplicate history.
3. An old session's events must not affect a newer account. Preserve generation checks and owner checks when moving asynchronous work.
4. A completed transfer must refer to validated FLAC in the user's selected destination. Keep publication serialized and cancellation checks around finalization.
5. Native share paths are virtual peer paths. Android document URIs are not filesystem paths; only the host opens a selected document and passes a descriptor. Sharing starts disabled.
6. Never infer a redistribution license from a download provider or mark Soulseek albums as user-owned automatically.

Use `SoulseekTransport` injection and the Room/document-provider fixtures for repository tests. Do not add a second authentication gate in a panel: enforce policy in repository entry points so alternate callers remain covered. Keep platform storage operations in `DownloadStorage`, protocol mechanics in Rust, and presentation in Compose.

## Database evolution

Version 1 is the first Soulseek database. The [Room Gradle plugin](https://developer.android.com/jetpack/androidx/releases/room) isolates each build task's intermediate schema output and copies the consistent schema into `app/schemas/app.runout.soulseek.SoulseekDatabase/`. Commit the generated schema whenever entities change. For an installed schema change, increment the version, register an explicit Room migration and verify retained messages, transfers and offline albums. Do not add `fallbackToDestructiveMigration` to make an upgrade pass.

Legacy library indexes remain JSON and use the non-destructive `AlbumJson` migration. Room does not replace the existing crate, pressing or Archive indexes.

## Native maintenance

The core is vendored source, not a downloaded Android binary. `native/vendor/UPSTREAM` records the exact commit. Keep adapter changes in `native/bridge` when possible; describe every vendor delta in `native/README.md`. Preserve MIT attribution and update bundled dependency notices when Cargo dependencies change.

The Android script currently supports Linux hosts with Bash. All four ABIs are built with the locked dependency graph and 16 KiB alignment. A host Rust test alone cannot verify JNI method names, Android descriptors or native packaging: run the Android instrumentation tests after changing those boundaries.

Default tests bind local servers only. Public Soulseek interoperability remains a separate manual exercise using an account you control. Never include real credentials in fixtures or public issue reports. The current experimental 177/1 client identifier must be replaced with a suitable allocated production identifier before a production release.

## Debugging and known limits

Check `app/build/reports/` first. Record the visible account/transfer state and the storage provider in a bug report; redact credentials and message bodies. Distinguish peer reachability from local folder access failures. Logs should identify operations without exposing private content.

The application scope and `AppGraph` are intentionally small and explicit. There is no background work scheduler or hosted backend. Android may stop foreground data-sync work; the UI supports reopening and resuming. Search is bounded; message history currently loads per account and may need database pagination for very large archives. Avoid expanding the native/host command surface for UI-only changes.

## Release discipline

The published development APK is the **debug variant** (`app.runout.dev`, label **Runout Dev**). The `release` build variant currently inherits prototype debug signing and is not a production update. Debug certificates differ between machines; a previous development installation can be updated only with the same certificate. Do not advise uninstalling the original app to work around a signature mismatch.

Follow [RELEASE.md](../RELEASE.md) for checks, source tags, checksum verification and publication. The [inactive CI template](../.github/workflows-templates/README.md) builds and uploads test artifacts when enabled; it does not publish GitHub releases.
