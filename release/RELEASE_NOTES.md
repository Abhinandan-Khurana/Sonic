Sonic. is an Android player for FLAC albums. Each album is a record in a crate, and you play it on a 3D turntable. Dig finds more music on Soulseek, and every Friday, Drops offers ten Creative Commons albums from the Internet Archive.

This is 1.0.0, the first stable release. The app was called Runout until now.

## Highlights

- **A new name and icon.** The launcher icon is the SONIC sleeve: cream while the app is light, black while it's dark, and one-tone as a themed icon on Android 13+.
- **Light and dark.** Settings › Flags › theme picks phone, light or dark. Dark is true black.
- **Three decks.** The RG-1 comes in white / teal, mono white and dark. It matches the theme until you pick one.
- **Swap records by hand.** Hold a record over the one playing and the deck swaps them without stopping the music. Eject still flies a record home to its sleeve.
- **Two fingers on the deck** lay it flat or stand it back up.
- **Storage.** Swipe up with two fingers on Settings to see what your music takes up in each folder, records largest first. A record's folder can be deleted from there.
- **Downloads that mend themselves.** A failed song is asked for again, or fetched from someone else with the identical file. A red tab marks a record missing a track.
- **Flags.** Auto-flip, auto-play, a sleep timer and the intro on every launch, all off until you switch them on.
- **Friday Drops** are the same ten albums for everyone each week from 30 October, and a new week arrives even with the app open.
- **Sharing on Wi-Fi only,** with a limit on uploads at once.
- **Easier on the battery.** The Soulseek connection and the 3D deck stay quiet when nothing is happening.

The full list is in the [changelog](https://github.com/Abhiroop1122/runout/blob/main/CHANGELOG.md).

## Install

- Android 9 or newer, on ARM64, ARMv7, x86-64 or x86.
- Download `Sonic-1.0.0.apk` and `Sonic-1.0.0.apk.sha256` into one folder, then check them:

  ```bash
  sha256sum -c Sonic-1.0.0.apk.sha256        # Linux
  shasum -a 256 -c Sonic-1.0.0.apk.sha256    # macOS
  ```

- It installs as a new app, **Sonic.** (`app.sonic`). It sits next to any Runout install; it doesn't replace it or read its data. Music in your folders is untouched.
- FLAC only. Other formats, and MP3s renamed to `.flac`, are not picked up.

## Known limits

- The APK is signed with the maintainer's development key, not a production key. Later releases signed the same way update this one. A future build signed with a production key would not, and would need an uninstall, which removes Sonic's library list, chats and settings (not your music).
- The Soulseek client still reports the experimental version 177/1.
- Testing has been on emulators and one Pixel 6. Other phones, background policies and mobile networks have not been checked.
- Bit-perfect USB output needs a phone that offers it. The Pixel 6 mixes USB at 48 kHz, and Sonic shows that as resampled.
- Dig searches this phone and Soulseek. Internet Archive albums come only through Friday Drops.
- At the largest font sizes, the deck and sleeve labels are crowded.

Sonic is MIT licensed. Credits for the Soulseek library, the fonts and the projects that informed it are in the [README](https://github.com/Abhiroop1122/runout#license-and-credits).
