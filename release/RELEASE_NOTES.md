Runout 0.4.5 development puts the glass listening room back on the home screen and keeps the Soulseek download backend.

## What changed

- Home is the glass crate and glass turntable again, the same room as the first Runout build. The inked deck and the idle disc rack are not the home screen.
- Soulseek search shows FLAC only, and every search word has to match.
- Downloads stay in the order they were added. Pause all, Resume all, and Turtle mode are on the download screen.
- A pause frees the download slot. Resume all continues a download Android interrupted.
- Pure mode passes the decoder's PCM through without converting it. A saved FLAC is refused if its bytes do not match the download.

## Install

Download **Runout-0.4.5-development.apk** and verify it with the attached SHA-256 file. It installs as **Runout Dev** (`app.runout.dev`) and updates the previous development build when signed with the same certificate.

## Verification and limits

This is a debug-signed development pre-release, not a production release. Pure mode does not claim bit-perfect USB output unless the phone reports a matching USB mixer. No pull request is opened.
