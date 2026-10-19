# Soulseek in Runout

## User behavior

Offline FLAC playback requires neither a Soulseek connection nor a Runout account. Soulseek usernames and passwords are used directly for online access. The same successful session gates Soulseek search, both download providers, messaging and sharing. Internet Archive requests remain ordinary HTTPS requests and receive no Soulseek credentials.

The Account screen explains account creation, the absence of password recovery, single-session behavior and plaintext protocol limitations. Remembered credentials are encrypted using an Android Keystore AES-GCM key and stored in `noBackupFilesDir`. Signing out closes the native session, clears saved credentials and pauses work; it leaves completed music alone.

## Components

`SoulseekRepository` owns session and account-scoped transfer/contact/conversation state in Room. `DownloadStorage` handles staging and destination document copies for both providers; `SoulseekTransport` isolates the wire interface. Native calls run on background coroutines. `NativeSoulseek` owns one JNI session. The core is vendored from soulseek-rs v19.1.0, commit `dc39e24ad7909b93f600e60f19807db1f8f1c3ad`, with MIT notices preserved.

The adapter provides search, peer-folder listing, queue/progress states, pause/resume/cancel/retry, private messages, presence and explicit shared-folder manifests. It participates in distributed search as a leaf. Direct and server-brokered peer connections are supplied by the core; unreachable peers can still fail, and the app surfaces those states.

`SoulseekTransferService` hosts downloads/uploads as data-sync work. Optional `SoulseekOnlineService` keeps the peer session available using a declared special-use foreground service and visible Disconnect notification. Android service timeouts stop the session and preserve progress. Sharing and stay-online mode start disabled each process lifetime. Without stay-online mode, an idle session disconnects in the background and can reconnect when Runout returns to the foreground.

## FLAC policy and files

`FlacPolicy` is shared by scanning, record imports, pressing, playback and downloads. It checks the real filename and native FLAC structure, including STREAMINFO and an encoded frame header, while allowing valid ID3-prefixed FLAC. Remote quality attributes are optional; final quality comes from the downloaded file rather than peer claims. This establishes the file format, not whether a FLAC was historically transcoded from a lossy source.

Soulseek downloads use unique private staging directories and stable peer/path/size identities. Completed bytes are validated, copied to a hidden partial document in the user-selected destination, verified there, then renamed to FLAC before being indexed. Failed copies and interrupted publication can be retried; existing completed music is not deleted by cancellation or logout.

Internet Archive retains its deterministic Friday collection and three weekly claims. MP3/Ogg fallback is removed. An album lacking FLAC, failing validation or failing publication uses no claim. Archive downloads also appear in the transfer list; retrying them reuses validated staged tracks from the current week's album.

Sharing enumerates only a separately selected folder. Both Kotlin and native boundaries enforce FLAC, and opaque document keys map to on-demand file descriptors. Non-seekable documents are staged on demand when native validation or an upload needs a seekable descriptor; the temporary file is unlinked after opening. Disabling sharing cancels active uploads and removes the share manifest. The rest of the device library is never auto-shared.

## Private messages

Conversations use exact Soulseek usernames. Incoming messages are committed to Room before acknowledgement; replayed messages are deduplicated. Outgoing messages move from pending to sent only when the transport accepts them. Sent does not mean delivered or read. Public/private chatrooms, read receipts and end-to-end encryption are not part of this integration.

## Migration and compatibility

Nostr clients are no longer instantiated and automatic profile publication is removed. Previous keys and friend data remain on disk as legacy data. `.vinyl` record sharing retains its ownership/license rules and accepts only FLAC audio payloads. Existing valid FLAC records and downloads remain available offline. Changed legacy indexes receive a `*.json.before-flac` backup; original non-FLAC media are hidden rather than deleted.

The development adapter uses experimental protocol version 177/1. A production release must reserve an appropriate minor identifier under the reusable core's client-version contract and configure production signing. Automated local interoperability checks do not establish compatibility with every public peer, mobile carrier or NAT/router configuration.
