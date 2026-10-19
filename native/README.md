# Runout Soulseek native adapter

The workspace builds `librunout_soulseek.so` around the MIT `soulseek-rs-lib` pinned in `vendor/UPSTREAM`. Vendor sources and license are tracked; Cargo output is not. No .NET runtime or GPL implementation is embedded.

## Build

From the repository root:

```bash
scripts/build-native.sh
cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked
```

Set `ANDROID_NDK_HOME` to NDK r27d or newer. The default output is `app/build/generated/soulseekJni/<abi>/librunout_soulseek.so`. Gradle invokes this script automatically. Rust 1.91.0, Cargo.lock, API 28 and 16 KiB page alignment are fixed for reproducibility. Test builds advertise experimental Soulseek 177/1; arrange a production identifier before public distribution.

## Kotlin/JNI contract

`NativeSoulseek` provides `command(JSON): JSON`, `poll(): JSON array`, and `close()`. `nativeCreate` binds the instance's `openSharedFile(key): Int` callback. It returns a detached descriptor owned by Rust, or -1. The host maps opaque keys to granted Android documents; remote paths never become arbitrary local file paths.

Commands have an `op` field and return `{ "ok": true }` or `{ "ok": false, "error": "..." }`.

| Operation | Inputs |
| --- | --- |
| login | username, password; host and port overrides exist for local tests |
| logout | none |
| search / cancel_search | id, query, optional timeoutMs / id |
| browse | id, username, directory |
| download | id, username, path, size, absolute private localDirectory |
| pause / resume / cancel | id |
| message | username, text, clientId |
| ack | incoming server message id |
| contact | exact username |
| shares | files array: opaque key, virtual FLAC path, expected size |

Events use `type`: session, search_result, search_done, browse_result, transfer, message, message_sent, presence, upload, shares, or error. Transfer events carry the host id, state, bytes, total, speed, optional queue position, staged path and error. Message timestamps are server Unix seconds and incoming IDs are acknowledged only by an explicit host `ack` after persistence. Polling never acknowledges a message. Commands and polling run off Android's main thread.

## Narrow vendor changes

- Deferred private-message acknowledgement and removal of full-message logging.
- Explicit FLAC share manifest, host file opener and cancellation when sharing is revoked.
- Prompt shutdown for a client shared by background workers.
- Portable rlimit comparison for 32-bit Android.
- Bounded Android host upload queues, duplicate-request handling and recent upload history.

The adapter adds strict remote FLAC selection, local header checks, bounded discovery events, typed errors, storage descriptors and session/transfer tracking. Kotlin applies the complete application admission policy and durable publication.

## Verification

Core unit tests and an adapter loopback login/DM test run on the host. `SoulseekNativeIntegrationTest` uses the packaged Android library, local central-server broker and two actual peers to verify search, folder listing, download bytes, upload descriptors and message acknowledgements. No public server login, account creation or third-party messaging is performed.
