Runout 0.4.1 development fixes public Soulseek search and connection handling, with Seeker-informed discovery and a guided retro interface.

## What changed

- Stream FLAC search results as peers respond. Protect new peer connections from search-burst eviction, consume native result deltas, cache identities/path components and reduce repeated ranking work.
- Keep your account through temporary network loss. Reconnect automatically when policy permits; distinguish app background pause, rejected credentials and another client using the same ID.
- Search local songs/albums/artists without an account. Combine them with Soulseek results, filter quality/availability and sort by match, availability or quality. Preview a peer's full FLAC folder before confirming an album download.
- Guided offline/Soulseek setup explains download storage, optional sharing and app-only/background connection tradeoffs. Consent and sharing folders are remembered per account. Folder pickers preserve the connection.
- Cancel pending needle playback when changing records; start a searched local song on its correct record side.
- Keep the vinyl player home, with Home/Library/Discover/Messages navigation, a mini player and global account/download access.
- Retain Internet Archive Friday Drops and FLAC validation throughout. Both download providers still require a connected Soulseek account; offline playback remains free of login.
- Update architecture, developer/contributor notes, Android flow tooling and performance/live-check instructions.

## Install

Download **Runout-0.4.1-development.apk** and verify it with the attached SHA-256 file. It supports Android 9+ and ARM64, ARMv7, x86-64 and x86. It installs as **Runout Dev** (`app.runout.dev`) alongside the original app, or updates the preceding development build when signed with the same certificate.

Start with **Listen offline** or **Set up Soulseek**. Another client using the same Soulseek ID will be disconnected when you connect. An unused username creates an account, including a typo; Soulseek has no password reset and does not encrypt credentials or messages in transit.

## Verification and limits

The attached verification report records JVM/Compose tests, native tests, Android integration/performance checks and explicit public-network observations. Device benchmarks measure warm metadata processing; they do not measure network latency or establish a speed comparison with Seeker. Public search and transfer success still depend on peers and reachability.

This is a **development pre-release**, debug-signed and using experimental client version 177/1. Production signing and a suitable production client identifier remain. GitHub source archives correspond to the tagged source commit. Original Runout and third-party notices remain in the repository and APK. No pull request is opened for this release.
