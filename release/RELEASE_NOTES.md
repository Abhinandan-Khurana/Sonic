Runout 0.4.2 development stops data loss and privacy leaks found in the Slice 0 audit. It keeps the vinyl player, FLAC-only policy, offline playback, Soulseek, and Friday Drops.

## What changed

- A failed record read or folder scan no longer erases the shelf. Unavailable records remain visible, and every index rewrite gets a dated backup.
- Imported `.vinyl` files have bounded metadata, required audio checksums, no inherited ownership, and no exported Soulseek identity or private notes.
- Soulseek event failures no longer cause reconnect loops. Another client using the same ID remains a terminal displacement, and stopping the client keeps partial downloads.
- Friday Drops claims stay bound to the week that admitted them.
- First-run Back, Pressing drafts, message drafts, and received-record removal now require their explicit successful or confirmed action.
- Finding missing covers online is off unless enabled in Collection & storage. Existing users see a one-time notice. Device-bound folder and setup choices are excluded from backup.

## Install

Download **Runout-0.4.2-development.apk** and verify it with the attached SHA-256 file. It supports Android 9+ and ARM64, ARMv7, x86-64 and x86. It installs as **Runout Dev** (`app.runout.dev`) and updates the 0.4.1 development build when signed with the same certificate.

## Verification and limits

The attached verification report records the Gradle, Rust 1.91.0, Android, ABI, and signature checks. No new public Soulseek login or third-party message was performed for this build. The previous 0.4.1 public-network observation is not repeated as a result for this APK.

This is a **development pre-release**, debug-signed and using experimental client version 177/1. It is not a production-signed release. GitHub source archives correspond to the tagged source commit. No pull request is opened.
