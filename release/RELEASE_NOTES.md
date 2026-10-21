Fixes from the first phones running 0.5.0, plus an audio-quality readout on the home screen.

## What changed

- **Albums no longer vanish after a reinstall.** One unreadable `.flac` made a scan roll back to the previous, empty shelf. A partial scan now keeps everything it read and saves it.
- **Settings › Library › Repair local albums** rebuilds the shelf from your folder and lists each file it couldn't read, with the reason: an MP3 renamed to `.flac`, a damaged FLAC, a cut-off download. Your music files are never changed.
- **Audio quality:** a chip on the home screen ("24/96 → Phone speaker") opens the chain Track → Runout → Output, each with its spec, and a verdict such as "Bit-perfect" or "Lossless, resampled to 48 kHz".
- **Pure vs Hi-res output, explained:** Pure is about processing (nothing touches the samples); Hi-res is about the format (each file at its own rate in 32-bit float). They combine.
- **Friday Drops are FLAC only.** This week is picked again.
- **Downloads with Background usage off:** "Interrupted system call" now shows as a pause with the reason, Downloads links to App info, and the download continues when you come back to Runout.
- **Dig:** a round search key. The filters sheet and Friends' New chat sheet shade the whole page.
- **Home:** Crackle is a switch. In landscape the tab bar is at the bottom and nothing runs into the sleeves.
- **Sign-in:** the titles no longer slide over the record collage when the keyboard opens.

## Install

Download **Runout-0.5.1-development.apk** and verify it with the attached SHA-256 file. It installs as **Runout Dev** (`app.runout.dev`) and updates 0.5.0 in place.

Coming from **0.4.x**? Uninstall it first: 0.5.x is signed with a different debug certificate. Uninstalling clears only Runout Dev's own data. Your music files stay where they are.

## Verification and limits

This is a debug-signed development pre-release. The native Soulseek library is the one from 0.4.6, unchanged; the native fix for interrupted reads is in the source and arrives with the next native build. See the verification report for what was and wasn't tested.
