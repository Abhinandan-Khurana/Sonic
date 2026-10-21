A redesign of the whole app around the glass listening room, from a screen-by-screen interview with the owner. The full checklist is in `docs/design/UI-REDESIGN.md`.

## What changed

- **Deck:**
  - Teenage Engineering-style keys with real travel, in soft anodised orange with a glass finish.
  - Engraved labels and ticks, an orange counterweight, and a finer dot-matrix screen.
  - The platter turns slower and calmer, with strobe dots that stand still at speed. Records still play at their recorded speed.
- **Home:**
  - Four icon tabs: Dig, Drops, Friends, Settings.
  - Status shows as coloured dots. There is no clock.
  - A shorter caption under the crate. Long-press a record for Send, Details or Remove.
  - Discs slide out with a spin, and land with a thud and a haptic tick.
- **Onboarding:**
  - A needle-drop intro, then one full-screen Soulseek sign-in. You stay signed in, and an ⓘ explains how Soulseek IDs work.
  - Each step moves on by itself once you choose.
  - It ends with a greeting that drops you into the room.
- **Dig:**
  - Its own screen, albums only, from This device or Soulseek.
  - Tap an album folder to see it like the back of a sleeve, with clean track names.
  - Better ranking.
- **Friday Drops:** a shelf of sleeves with three claim lights. Tap a drop for its tracks and a 30-second listen, then Claim. Albums with no FLAC say so and can't be claimed.
- **Friends:** chats and friends with online dots, + to start a chat, and records sent as cards.
- **Settings:** a short list (Sound, Soulseek, Library, About). The EQ has hardware-style sliders.
- **Pressing Plant:** removed. Records you pressed before still play.

## Install

Download **Runout-0.5.0-development.apk** and verify it with the attached SHA-256 file. It installs as **Runout Dev** (`app.runout.dev`).

**Uninstall Runout Dev 0.4.x first.** This build is signed with a different debug certificate, so Android won't install it over 0.4.6. Uninstalling clears Runout Dev's own data: you'll pick your music folder and sign in again. Your music files stay where they are.

## Verification and limits

This is a debug-signed development pre-release, not a production release. See the verification report for what was and wasn't tested.
