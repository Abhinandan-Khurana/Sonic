Runout is redrawn as a Teenage Engineering catalogue: black ink on white, dot-matrix type, and the 3D record player as the one rich object in the room.

## What changed

- **A new look, everywhere:** every screen is rebuilt to the TE design boards. Square corners, hairline rules, no glass or shadows, and the boards' exact sizes and type.
  - The interface face is **Array** by the Indian Type Foundry (via Fontshare), embedded in the app. Its dotted bold sets the titles.
  - Inter prints the stickers on the sleeves.
- **Home:** a top bar says which side is up and where the needle is ("side a · a2 · 2 / 4"), with the line out, downloads and your record count.
  - The deck sits on a band with its own caption.
  - The crate stands on a floor line, with a faint reflection under the front sleeve and tilted HI-RES and label stickers.
  - Under the album, a track wheel lists the songs by side: the one playing is sharp, the rest soft. Tap one to drop the needle there.
  - The first time in, "?" helps explain the crate, the track wheel and the LED window.
- **Pages:** Dig, Drops, Friends, Settings and Downloads are full pages under one nav bar.
  - Hold the deck's LED window for Audio quality. Crackle now lives in Settings › Sound.
  - Dig lists albums as two compact lines. Every "?" opens a short help panel.
- **Onboarding:** a 3-second intro tells the needle-drop story: a sleeve, the disc, the platter, the arm, then RUNOUT typed out. Tap to skip it; it's skipped when animations are off. Each step shows a big number, and the welcome greets you by your Soulseek ID.
- **The record moves in 3D:**
  - **Pulling:** it rises out of its sleeve, and lands on the platter exactly where the deck draws it.
  - **Flip:** tap the record with the needle up and it lifts, rolls over and lands on the other side.
  - **Eject:** the record flies home to its sleeve.
  - **Busy platter:** drop a second record onto a busy platter and it bounces back with "first, remove the current record from the platter." and an eject key.
- **The deck:** the RG-1 v3, wider, with red LED windows and big flush keys on the right.

## Install

Download **Runout-0.7.0-development.apk** and check it against the attached SHA-256 file. It installs as **Runout Dev** (`app.runout.dev`) and updates 0.5.x and 0.6.0 in place (same certificate). Coming from 0.4.x, uninstall it first.

## Not tested, and limits

- Tested on the Android emulator only, not on a phone. How smoothly the 3D record moves on older phones is unknown.
- On the emulator, the first 3D frames can take a few seconds after a fresh install; a flat deck stands in until then.
- The track wheel's "?" and the other first-time helps show until a record first lands on the platter.
- Array is embedded in this APK under its licence, which doesn't allow sharing the font files in a public repository. A build from source without them uses Silkscreen instead.
- The native Soulseek library is reused from the earlier development build (no Rust/NDK toolchain on this Mac). See the verification report.
- This is a debug-signed development pre-release.
