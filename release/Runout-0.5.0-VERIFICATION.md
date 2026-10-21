# Runout 0.5.0 development verification

Verified on 2026-10-21 (Asia/Kolkata), on macOS. Source branch: `ui-redesign`.

## What this build contains

The UI redesign recorded in `docs/design/UI-REDESIGN.md`:

- the TE-detailed glass deck;
- the icon tab bar;
- full-screen onboarding with the intro and a single sign-in;
- albums-only Dig;
- the Drops shelf with previews;
- Friends chats;
- the Settings list;
- Pressing Plant removed.

The checklist marks 76 items done and 3 partial (see "Not verified").

## How it was built

- This Mac has no Android NDK or Rust toolchain. The native Soulseek library was therefore **not rebuilt**. `librunout_soulseek.so` for all four ABIs was taken unchanged from the published `v0.4.6-dev.1` APK.
- `git diff v0.4.6-dev.1..HEAD -- native scripts/build-native.sh` is empty, so the native sources are identical.
- Native `cargo` tests were not re-run for this release; they passed for 0.4.6 on the same sources.
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -x buildSoulseekNative` printed `BUILD SUCCESSFUL in 1m 7s`.

## Checks

- JVM/Compose XML: **215 tests, 214 passed, 1 skipped, 0 failures**. The skip is `LiveRelayCheck` (public relay lookup).
- Lint XML: **0 errors, 59 warnings, 2 hints**.
- APK: `app.runout.dev`, version 0.5.0, version code 11, target SDK 36. `librunout_soulseek.so` is present for ARM64, ARMv7, x86 and x86-64.
- `zipalign -c -P 16 -v 4`: verification successful.
- Debug certificate SHA-256: `1d001a76582b5450dc6795285455321aacaced319cf08fc2040471883b5dd8b8`. This **differs** from 0.4.5/0.4.6 (`af474b32…`), so the build does not update over them.
- Local checksum: `0af1f7ff2199e828895e9e2d5f1d4c48a62f152a6683d2b517b9a35eca05fd61`.

## Device

Emulator `emulator-5554` (AVD `Pixel_6`), Argent 0.25.1. A Soulseek test account was signed in by the owner. Checked by hand:

- **Onboarding:**
  - The intro plays (frames captured) and hands over to the sign-in.
  - The offline path picks a music folder and ends in the room.
  - The download, share and rhythm steps were checked in Robolectric tests with screenshots.
- **Home:**
  - Tab bar, status dots and refresh key all shown.
  - Pull-out animation, long-press menu, details card, and drag onto the platter (cues A1).
  - Deck keys and the orange finish, at half platter speed.
- **Dig:**
  - "moon safari" on Soulseek returned 40 album folders with Air's Moon Safari first.
  - The folder preview showed clean names (01 La Femme D'argent … 10 Le Voyage De Penelope) and the spec tag.
- **Drops:**
  - The shelf of 10 sleeves and three claim lights.
  - The previews of two drops correctly reported no FLAC; the archive metadata confirms MP3/Ogg only.
- **Settings:** the list, with Sound / Soulseek / Library / About and their summaries.
- **Landscape:** the deck sits left, the tab bar top right, the crate right.

## Not verified

- **30-second listen:** this week's drops opened during the test had no FLAC, so nothing was streamed.
- **Dig covers (DG-5):** result cards show generated sleeves, not real covers.
- **Back with the on-screen keyboard (BG-3):** the emulator has a hardware keyboard, so no on-screen keyboard appeared.
- **TalkBack (BG-4):** the accessibility tree is still to be re-checked with TalkBack on a phone.
- **Record cards in chat:** not sent or received over the network; covered by unit tests only.
- **Not run:** `connectedDebugAndroidTest`, the Argent saved flows, the lock screen and headset buttons.
