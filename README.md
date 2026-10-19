# RUNOUT

A vinyl listening room for Android, with offline FLAC playback, Soulseek music discovery and private messages, and Internet Archive Friday Drops.

## Version 0.4.0 — development build

- **Play offline without signing in.** Choose your FLAC music folder, browse your crate, and use the turntable, DSP, EQ, and Pure mode.
- **Use your Soulseek account online.** Search FLAC tracks, download individual files or an album folder, and send private messages by username.
- **Keep Internet Archive.** Friday Drops still offers the same weekly collection and three claims per week. Soulseek sign-in is required to claim; albums without FLAC consume no allowance.
- **Keep your files.** Choose a writable download folder. Completed FLAC files remain accessible outside Runout and survive uninstalling the app.
- **Share only when you choose.** Select a folder and enable FLAC sharing. The rest of your device library is not published automatically.
- **Optional stay-online mode.** Incoming private messages and sharing can remain available in the background with an ongoing notification and Disconnect action.

FLAC-only applies to music throughout scanning, imports, downloads, sharing, pressing and playback. Artwork, record containers and the turntable's synthesized effects remain supported. Renaming an MP3 to `.flac` does not make it acceptable.

[Download the development APK](https://github.com/Somchandra17/runout/releases/download/v0.4.0-dev.1/Runout-0.4.0-development.apk) · [Soulseek behavior and architecture](docs/SOULSEEK.md) · [Build instructions](docs/BUILDING.md) · [Documentation index](docs/README.md)

## First use

1. Choose your local FLAC folder and play without creating an account.
2. Open **Account** when you want online features. Enter your Soulseek username and password, or use an unused username to create an account through Soulseek's normal login operation.
3. Select a download folder before downloading from **Search** or **Drops**.
4. Use **Messages** for private conversations and **Downloads** for download progress, pause/resume, retry, and uploads.
5. Enable sharing and stay-online mode only if you want them.

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

This is a development integration. Test builds identify themselves with Soulseek's documented experimental client version. A public production release needs an appropriate client minor identifier, production signing and real-account interoperability checks. No public-network sign-in or publication was performed as part of the automated tests.

## License and credits

Runout remains MIT licensed; original authorship is preserved in [LICENSE](LICENSE). Soulseek support vendors [soulseek-rs-lib v19.1.0](https://github.com/michel/soulseek-rs/releases/tag/v19.1.0), with its MIT notice and documented integration changes under [native/](native/README.md). Native dependency notices are bundled in the APK.

The supplied [Seeker](https://github.com/jackBonadies/SeekerAndroid), [Soulseek.NET](https://github.com/jpdillingham/Soulseek.NET), and [Nicotine+ protocol documentation](https://nicotine-plus.org/doc/SLSKPROTOCOL.html) informed the investigation; their GPL implementations are not copied into Runout.

Inter and Silkscreen use the SIL Open Font License. AndroidX, Media3, Compose, OkHttp and Kyant Backdrop retain their respective notices. Internet Archive albums retain their credited Creative Commons licenses. Soulseek downloads are not automatically marked as original or openly licensed music.
