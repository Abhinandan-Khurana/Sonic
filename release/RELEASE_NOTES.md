Sonic. 1.1.0 is the first update after 1.0.0: what the first listeners asked for, and updates from inside the app.

## What's new

- **Track wheel picker.** Scroll a song under the bar and tap to play it. A song on the other side turns the record over and plays.
- **Updates in the app.** Settings › About checks GitHub; Flags can do it once a day. The first check asks whether you want stable releases or development builds too. A newer release opens its page in your browser.
- **Clearer audio quality.** The signal chain is back at the top of Settings › Sound, and only the step that changes the sound explains itself.
- **Storage** shows your music beside the whole phone in one line and one bar.
- **Shared record pictures** carry the SONIC sleeve.
- **Fixes:** pause all and resume all, downloads with the screen off, and the gap above the keyboard in chats.

The full list is in the [changelog](https://github.com/Abhiroop1122/Sonic/blob/main/CHANGELOG.md).

## Install

- Android 9 or newer, on ARM64, ARMv7, x86-64 or x86.
- Download `Sonic-1.1.0.apk` and `Sonic-1.1.0.apk.sha256` into one folder, then check them:

  ```bash
  sha256sum -c Sonic-1.1.0.apk.sha256        # Linux
  shasum -a 256 -c Sonic-1.1.0.apk.sha256    # macOS
  ```

- It updates Sonic 1.0.0 in place (same app, same signing key). From now on, Sonic can tell you about the next one itself.
- FLAC only.

## Known limits

- The APK is signed with the maintainer's development key, not a production key. A future build signed with a production key would need an uninstall first, which removes Sonic's library list, chats and settings (not your music).
- The Soulseek client still reports the experimental version 177/1.
- Bit-perfect USB output needs a phone that offers it, and today matches 16-bit files; 24-bit output to integer-only DACs is planned.
- Android doesn't tell apps which Bluetooth codec is in use, so Sonic says "lossy over Bluetooth" without naming it.

Sonic is MIT licensed. Credits are in the [README](https://github.com/Abhiroop1122/Sonic#licence-and-credits).
