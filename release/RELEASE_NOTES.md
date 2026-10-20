Runout 0.4.4 development plays the printed deck by hand, keeps records as vinyl, and writes a `.vinyl` record with every download.

## What changed

- Drag the arm to a band, use the cue and motor, swipe a finished record up to turn it over, and drag it down to put it away.
- Covers are the centre label of a black record, not a printed CD.
- A completed Soulseek or Internet Archive download writes `record.vinyl` in the album folder. It carries the cover, album metadata, and the source links. Missing covers are looked up from MusicBrainz and the Cover Art Archive.
- Interrupted Archive downloads resume from the partial file. Cancel removes unfinished staging. A corrupt partial is deleted instead of being resumed.

## Install

Download **Runout-0.4.4-development.apk** and verify it with the attached SHA-256 file. It installs as **Runout Dev** (`app.runout.dev`) and updates the previous development build when signed with the same certificate.

## Verification and limits

This is a debug-signed development pre-release, not a production release. No pull request is opened.
