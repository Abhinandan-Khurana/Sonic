# Soulseek in Sonic

## User behavior

Offline FLAC playback requires neither a Soulseek connection nor a Sonic account. Soulseek usernames and passwords are used directly for online access. The same successful session gates online Soulseek search, both download providers, messaging and sharing. Local metadata search works offline. Internet Archive requests remain ordinary HTTPS requests and receive no Soulseek credentials.

The Account screen explains account creation, the absence of password recovery, single-session behavior and plaintext protocol limitations. Remembered credentials are encrypted using an Android Keystore AES-GCM key and stored in `noBackupFilesDir`. Signing out closes the native session, clears saved credentials and pauses work; it leaves completed music alone.

## Components

`SoulseekConnectionManager` owns the single native session and reconnect policy. `SoulseekRepository` owns account-scoped transfer/contact/conversation state in Room. `DownloadStorage` handles staging and destination document copies for both providers; `SoulseekTransport` isolates the wire interface. Native calls run on background coroutines. `NativeSoulseek` owns one JNI session. The core is vendored from soulseek-rs v19.1.0, commit `dc39e24ad7909b93f600e60f19807db1f8f1c3ad`, with MIT notices preserved.

The adapter provides search, peer-folder listing, queue/progress states, pause/resume/cancel/retry, private messages, presence and explicit shared-folder manifests. It participates in distributed search as a leaf. Direct and server-brokered peer connections are supplied by the core; unreachable peers can still fail, and the app surfaces those states.

`SoulseekTransferService` hosts downloads/uploads as data-sync work. Optional `SoulseekOnlineService` keeps the peer session available using a declared special-use foreground service and visible Disconnect notification. Android service timeouts stop the session and preserve progress. Guided setup offers app-only and stay-online modes with their battery/data tradeoffs. Sharing/background consent and the selected share folder persist per exact account; both begin disabled for a new account. Without stay-online mode, an idle connection pauses after five seconds in the background and automatically resumes when Sonic returns. External folder pickers suppress Sonic's idle pause. Android may still interrupt a cached app's sockets while the picker is open; the account is retained and the foreground transition prompts an immediate reconnect.

Temporary network loss retains account identity and retries after 1, 2, 4, 8, 16 and then 30 seconds while connection policy permits. A network becoming available prompts an immediate retry. Rejected credentials and another-client displacement stop automatic retries; explicit reconnect is available. Manual disconnect stays paused across restarts; logout also clears remembered credentials. Active downloads require a live authenticated connection and pause on loss.

## Finding songs and albums

Discover combines local metadata matches with an explicit online search. Local search accepts case/diacritic differences and small typos; it never reads audio during a query. Online results stream while the 30-second collection window remains open. A stop action retains matches already received. Results cap at 5,000 FLAC files and explain when refinement is needed.

Songs, Albums and All change presentation/ranking. Filters expose advertised quality and available upload slots; unknown quality is not discarded by default. Best match is the default sort, with availability/queue/speed as tie breakers. Quality and Availability are explicit alternatives. Each album group belongs to one peer and one complete path, so similarly named releases or different peers do not merge. Album preview fetches the full folder listing and asks for confirmation before downloads.

### Seeker reference

The source comparison used [Seeker at ff1dcc6](https://github.com/jackBonadies/SeekerAndroid/tree/ff1dcc6de55a412e8d6eadaddf36deca54517413). Its `SearchFragment` receives incremental response callbacks, splits folders, presents queue/free-slot metadata, and avoids expensive full result diffing. Its `SessionService` distinguishes a remembered account from a disconnected transport. These behaviors informed Sonic's independent batching, stable-key list, folder grouping and reconnect design. Seeker also uses a response-inactivity timeout; Sonic currently keeps a fixed bounded collection window and displays results immediately rather than waiting for completion. No Seeker or Soulseek.NET implementation code is copied; Sonic retains its MIT core. This is a source comparison, not a measured speed comparison between the two apps.

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
