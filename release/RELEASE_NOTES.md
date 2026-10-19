Runout 0.4.0 development adds Soulseek FLAC search, downloads and private messages while retaining Internet Archive Friday Drops and account-free offline playback.

## Included

- FLAC-only music scanning, playback, imports, pressing, sharing and downloads, with byte-level format validation. Legacy non-FLAC media are hidden without deletion.
- Soulseek username/password sign-in, track and album-folder downloads, queue/progress display, pause/resume/retry and selected-folder FLAC sharing.
- Private messages and contacts stored locally, with incoming messages committed before acknowledgement. Nostr online sessions and profile publication are replaced.
- Soulseek sign-in required for downloads from both providers. Archive albums without FLAC or failed downloads consume no weekly claim.
- Completed music saved to a user-chosen writable folder and retained after uninstall. Optional remembered credentials protected by Android Keystore.
- Optional stay-online mode with a visible notification and Disconnect action.
- Current architecture, developer/contributor guides, exported database schema and a shared download-storage boundary.

## Install

Download **Runout-0.4.0-development.apk** from this release. It supports Android 9+ and ARM64, ARMv7, x86-64 and x86. It installs as **Runout Dev** (`app.runout.dev`) alongside the original app. Private state is separate; the original app is not replaced. Verify the APK with the attached SHA-256 file.

Play local FLAC without signing in. For downloads, open Account, sign in with your Soulseek ID, and choose a download folder. An unused username creates a Soulseek account; there is no password reset, and another client on the same account will be disconnected. The Account screen explains these details. The Soulseek protocol does not encrypt credentials or messages.

## Verification and limits

The attached verification report records passing JVM/UI, native and emulator integration tests, zero lint errors, and four-ABI native packaging with 16 KiB alignment. Integration tests use actual local peers and never sign in to public Soulseek.

This is a **development pre-release**, debug-signed and using experimental client version 177/1. Production signing, an appropriate production client identifier, and public-network interoperability checks remain. Search/transfer success depends on peer availability and network reachability. A valid FLAC container does not prove a lossless recording source or redistribution rights.

GitHub source archives correspond to this release's tagged source commit. Original Runout and third-party license notices are preserved in the repository and APK.
