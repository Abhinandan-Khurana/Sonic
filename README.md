# RUNOUT

A vinyl listening room for Android, with offline FLAC playback, Soulseek music discovery and private messages, and Internet Archive Friday Drops.

## Version 0.4.1 — development build

Runout keeps the retro vinyl player home and adds **Home, Library, Discover and Messages**, with downloads and account status always within reach. Guided setup offers offline listening first, then explains download storage, optional FLAC sharing and background connection choices.

Search your local songs, albums and artists as you type. Submit an online search to stream Soulseek FLAC matches as peers respond. Switch between songs and albums, rank by match, availability or quality, and preview a peer's complete folder before downloading it. The search and connection work takes reference from Seeker's approach; Runout retains its own Kotlin UI and MIT native implementation.

- **Play offline without signing in.** Choose your FLAC music folder, browse your crate, and use the turntable, DSP, EQ, and Pure mode.
- **Use your Soulseek account online.** Search FLAC tracks, download individual files or an album folder, and send private messages by username.
- **Keep Internet Archive.** Friday Drops still offers the same weekly collection and three claims per week. Soulseek sign-in is required to claim; albums without FLAC consume no allowance.
- **Keep your files.** Choose a writable download folder. Completed FLAC files remain accessible outside Runout and survive uninstalling the app.
- **Share only when you choose.** Select a folder and enable FLAC sharing. The rest of your device library is not published automatically.
- **Optional stay-online mode.** Incoming private messages and sharing can remain available in the background with an ongoing notification and Disconnect action.

FLAC-only applies to music throughout scanning, imports, downloads, sharing, pressing and playback. Artwork, record containers and the turntable's synthesized effects remain supported. Renaming an MP3 to `.flac` does not make it acceptable.

[Download the development APK](https://github.com/Somchandra17/runout/releases/download/v0.4.1-dev.1/Runout-0.4.1-development.apk) · [Soulseek behavior and architecture](docs/SOULSEEK.md) · [Build instructions](docs/BUILDING.md) · [Documentation index](docs/README.md)

<img src="docs/images/home.png" width="270" alt="The Runout listening room: glass turntable above the record crate, with the Dig, Drops, Friends, Pressing Plant and Settings pills" />

## First use

1. On first launch, choose **Listen offline** or **Set up Soulseek**. Offline listening needs only a local FLAC folder.
2. For online features, sign in with your Soulseek ID or choose **Create an ID**. Optional remembered sign-in uses Android Keystore.
3. Setup walks through a download folder, optional sharing folder and app-only/background connection mode. Sharing choices and the selected share folder are remembered separately for each account.
4. Use **Discover** for local/online search and **Friday Drops** for Internet Archive. Downloads from both providers require a connected Soulseek account.
5. Preview an album folder to see its complete FLAC listing before confirming the download. Use the header's download button for progress, pause/resume and retry.

Temporary connection loss keeps your account and automatically reconnects. App-only mode pauses an idle connection after Runout goes into the background and resumes it when you return. Opening a folder picker preserves your account; if Android interrupts the network, Runout reconnects when you return. Connecting the same ID in another client pauses automatic retries to avoid repeatedly disconnecting that client; use **Reconnect** when ready.

Soulseek has no password reset. An unused username creates an account, including a mistyped existing name. Signing in disconnects another client using the same account. Its protocol does not encrypt credentials or private messages. Runout has no separate account server and never sends Soulseek credentials to Internet Archive.

## Existing installations

The migration keeps original music files and backs up changed library indexes as `*.json.before-flac`. Non-FLAC music disappears from Runout; valid FLAC tracks in mixed albums remain available. Existing valid FLAC downloads keep working offline.

Nostr connections, profile publication and online Nostr account screens are no longer started. Legacy keys, friend data and `.vinyl` records remain locally stored. Nostr identities are not converted into Soulseek identities.

The development APK is named **Runout Dev** and uses application ID `app.runout.dev`, so it installs alongside the existing Runout app without replacing or erasing it. Its private crate and account are separate. The normal release application ID remains `app.runout`; updating an existing release still requires its original signing key.

## Build and verify

The Android app stays Kotlin/Compose and Media3. A pinned MIT Rust library handles Soulseek behind a JNI bridge. Native libraries are built for ARM64, ARMv7, x86-64 and x86 with Android 9+ compatibility and 16 KiB page alignment.

Requirements: JDK 21, Android SDK 36, Rust 1.91.0, and Android NDK r27d or newer. See [the build guide](docs/BUILDING.md) for setup.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked
# With an Android device or emulator attached:
./gradlew connectedDebugAndroidTest
```

Tests use local fixtures and test servers. The Android integration test runs the packaged JNI library against two real local peers, checking login, FLAC search, folder browsing, file transfer, shared-file descriptors, private messages and acknowledgements. It never creates a public Soulseek account.

## Contributing

See the [contributor guide](CONTRIBUTING.md), [architecture map](docs/ARCHITECTURE.md), and [developer notes](docs/DEVELOPMENT.md) for source boundaries, local test fixtures, database migrations and native maintenance. A [GitHub Actions template](.github/workflows-templates/README.md) builds the native libraries and runs JVM tests/lint. It is ready to enable with a GitHub login that can write workflows; the current publishing login lacks that permission.

## Distribution status

This is a development integration. Test builds identify themselves with Soulseek's documented experimental client version. A public production release needs an appropriate client minor identifier and production signing. Ordinary automated tests use local fixtures; explicit public-network session/search checks are recorded separately in the release verification report. Peer availability and network conditions affect search and transfer success.

## License and credits

Runout remains MIT licensed; original authorship is preserved in [LICENSE](LICENSE). Soulseek support vendors [soulseek-rs-lib v19.1.0](https://github.com/michel/soulseek-rs/releases/tag/v19.1.0), with its MIT notice and documented integration changes under [native/](native/README.md). Native dependency notices are bundled in the APK.

The supplied [Seeker](https://github.com/jackBonadies/SeekerAndroid), [Soulseek.NET](https://github.com/jpdillingham/Soulseek.NET), and [Nicotine+ protocol documentation](https://nicotine-plus.org/doc/SLSKPROTOCOL.html) informed the investigation; their GPL implementations are not copied into Runout.

Inter and Silkscreen use the SIL Open Font License. AndroidX, Media3, Compose, OkHttp and Kyant Backdrop retain their respective notices. Internet Archive albums retain their credited Creative Commons licenses. Soulseek downloads are not automatically marked as original or openly licensed music.
