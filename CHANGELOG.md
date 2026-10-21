# Changelog

All notable changes to **Runout** are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## Unreleased

- **Library:** fixed a race between the quiet refresh that runs at startup and a scan the user starts at the same time. The startup refresh cancelled the newer scan, so the shelf could stay empty — the same symptom as the "albums vanish" bug fixed in 0.5.1. The startup refresh no longer cancels a scan that is already running.

---

## 0.5.1 development — fixes from the first phones (2026-10-21)

- **Library:**
  - Fixed: after a reinstall, albums appeared for a second and then vanished. One unreadable `.flac` made the whole scan roll back to the previous shelf, which was empty. A partial scan now keeps everything it read, merged with what it couldn't see, and saves it.
  - New: Settings › Library › **Repair local albums**. It rebuilds the shelf from the folder alone and lists every file it couldn't read, with the reason (for example an MP3 renamed to `.flac`, or a damaged or cut-off FLAC). Music files are never changed.
- **Audio quality on the home screen:** a small chip such as "24/96 → Phone speaker" opens the signal chain: Track → Runout → Output, each with its bit depth and rate, and a plain verdict ("Bit-perfect", "Lossless, resampled to 48 kHz", "Lossy over Bluetooth"). Runout now knows the output device: speaker, wired, USB DAC or Bluetooth.
- **Pure and Hi-res output** are explained where they're switched: Pure is about processing, Hi-res about the format, and they combine.
- **Friday Drops:** only albums with FLAC files are picked. This week is picked again.
- **Downloads:** "Stream read error: Interrupted system call" was Android pausing the app (Background usage off). It now shows as a pause with the reason, Downloads shows a note with a link to App info, and interrupted downloads continue when Runout is back.
- **Dig:** a round search key beside the field. Its filters sheet, and Friends' New chat sheet, now shade the whole page.
- **Home:** Crackle is a switch. In landscape the tab bar sits at the bottom right, and the status line no longer runs into the sleeves.
- **Sign-in:** with the keyboard up, the record collage fades and the headline folds away instead of sliding under the titles.
- **Code:** `ui/Turntable.kt` holds only the vinyl player, with a map at the top.
- **Native (source only):** download reads retry an interrupted system call. Not in this APK: the native library is reused unchanged from 0.4.6.


## 0.5.0 development — UI redesign (2026-10-21)

From a screen-by-screen interview with the owner; the full checklist is in `docs/design/UI-REDESIGN.md`.

- **Deck:**
  - Teenage Engineering details on the glass plinth: keys with real travel, anodised-orange START/STOP and counterweight, engraved labels and ticks, and a finer dot-matrix screen.
  - Drawn in layers, so only the record redraws each frame.
  - The platter turns slower on screen, with a real-strobe effect.
- **Home:**
  - The footer is four icon tabs. The clock and Change folder are gone from the top.
  - Status shows as coloured dots.
  - The caption is title and artist plus a spec tag.
  - A long press opens a record's menu.
  - Disc pull-out and landing are animated, with haptics and a thud.
- **Onboarding:**
  - Full screen, opening with a needle-drop intro.
  - One Soulseek sign-in that always remembers you; an ⓘ explains how IDs are created.
  - Steps move on by themselves, and it ends with a greeting that pops into the room.
- **Dig:**
  - A full page, albums only, from two sources.
  - Tap-to-preview cards, peer marks and a floating filters key.
  - Sleeve-style previews with clean track names, and better ranking.
- **Friday Drops:** a sleeve shelf, claim lights, and a preview with track list and 30-second listens.
- **Friends:** a full page with chats, friends with online dots, a + to start a chat, and records sent as cards.
- **Settings:** a list of pages, short copy with ⓘ, and a TE-style EQ. Downloads controls are icon keys.
- **Pressing Plant:** removed. Records pressed earlier stay playable.

## [0.4.6] - 2026-10-21 (development)

### Changed
- The original glass listening room is back. The deck is the only player.
- 33/45, pitch and power controls are removed. Records play at the recorded speed.
- Search cues a song on the platter with the needle up. START/STOP plays it.

---

## [0.4.5] - 2026-10-20 (development)

### Changed
- Home is the glass listening room again: crate, picture disc, and glass turntable. The inked deck and idle disc rack are not the home screen.
- Soulseek search keeps a result only when the file is FLAC and every search word matches.
- The download list stays in the order downloads were added. Pause all, Resume all, and Turtle mode are on the download screen.
- Pure mode passes the decoder's PCM through without converting it. A saved download is refused if its bytes do not match the source.

---

## [0.4.4] - 2026-10-21 (development)

### Added
- The printed deck is now played by hand: drag the arm to a band, use the cue and motor, swipe a finished record up to turn it over, and drag it down to put it away.
- Settings can show labelled deck controls. They stay off unless enabled.
- Each completed download writes `record.vinyl` beside the music, with cover art, album metadata, and the source download links.

### Changed
- Covers sit in the centre label of a black record. They are no longer printed across the disc like a CD.
- Next and previous stay on the current side. Flip is the only way to the other side.
- Lowering the needle no longer fades the first moments in from silence.
- Internet Archive downloads resume a partial file. Cancel removes unfinished staging, and a corrupt partial is deleted instead of being reused.

---

## [0.4.3] - 2026-10-21 (development)

### Added
- Idle home opens on a full-screen vinyl rack. Scroll up or down to move records, tap the facing disc to unbox it, then drag it onto the platter.
- First inked home shell: paper room, soft pills, Settings, Crate, and a printed transcription deck when a record is in the room.

---

## [0.4.2] - 2026-10-21 (development)

### Fixed
- Retain unavailable records and shelves through failed reads or folder scans, and create a dated backup before every index rewrite.
- Bound `.vinyl` metadata reads, clean abandoned imports, require checksums, strip private provenance, and prevent imported records from inheriting sender ownership.
- Isolate Soulseek event failures, preserve another-client displacement, and stop native downloads without discarding partial files.
- Bind Friday Drops claims to the admitted week.
- Keep onboarding, Pressing drafts, message drafts, and received-record removal from being lost or completed accidentally.
- Make online cover lookup opt-in for new and existing installs, and exclude device-bound setup choices from Android backup.

---

## [0.4.1] - 2026-10-20 (development)

### Fixed
- Keep account identity through network loss and app background pauses; reconnect without asking for credentials again. Distinguish another-client displacement from temporary connection loss.
- Protect newly connected discovery peers long enough to reply, fixing public searches that previously returned zero results under connection churn. Explicit browsing/downloads can take priority over discovery.
- Stream bounded native result batches, cache result identities and path components, avoid repeated folder-wide matching and filename collation, and keep local indexing separate from network updates.
- Preserve sessions during external folder pickers and notification permission prompts, scope sharing consent and folders to each account, and discard stale sharing scans after account/session changes.
- Cancel the previous needle-settling ramp when loading a different record, and play locally searched songs on their correct side.

### Added
- Guided offline/Soulseek onboarding with download folder, optional sharing and explained connection modes.
- Retro Home, Library, Discover and Messages navigation, global transfers/account access and mini player; retain the vinyl home.
- Unified metadata search, song/album views, availability/quality filters, sorting and complete peer folder preview before album download.
- Argent device flows, Android performance checks and an explicit private-credential public-network probe.

---

## [0.4.0] - 2026-10-19 (development)

### Added
- Soulseek FLAC search, track/folder downloads, private messages, contacts, optional selected-folder sharing and stay-online mode.
- Pinned MIT Rust core with Android JNI libraries for four ABIs and local peer interoperability tests.
- Room-backed transfers/conversations and optional Keystore-protected credentials.
- User-selected download destination; completed files survive uninstalling Runout.

### Changed
- All music admission and playback is FLAC-only, with byte validation and non-destructive legacy migration.
- Soulseek sign-in gates both Soulseek and Internet Archive downloads; offline playback remains account-free.
- Nostr online UI and automatic relay publication are replaced; legacy local data are preserved.
- Internet Archive MP3/Ogg fallback is removed; unavailable/failed FLAC downloads consume no claim.

---

## [0.3.0] - 2026-10-19

### Added
- **The Glass Drive RG-1 Turntable:** Complete redesign inspired by classic direct-drive DJ decks. Features a frosted liquid glass plinth with dynamic refraction shaders (Android 13+), 4-row strobe platter (180 dots/row), S-shaped chrome tonearm with counterweight, anti-skate dial, and exact law-of-cosines tracking geometry.
- **Picture Discs:** Albums with cover artwork are now pressed as picture discs with album art rotating underneath grooved vinyl shaders and smooth track gap bands.
- **Dot-Matrix Pixel Screen:** Plinth-integrated 8-bit display driven by the Silkscreen pixel font. Real-time scrolling track marquee, timer, RPM, and cue readouts. Full TalkBack screen reader support.
- **Serverless Nostr Mesh:** User accounts based on local 256-bit private keys (`nsec1...`). Public profiles and shelves published as kind 33760 events on public relays (`relay.damus.io`, `nos.lol`, `relay.primal.net`).
- **Tag Prefix Search:** Fast username search across public relays with 4-character key collision prevention (`@username·XXXX`).
- **SHA-256 Checksummed Packages:** Every audio file in a `.vinyl` bundle is verified byte-for-byte on arrival.
- **Deck Controls:** Start/Stop toggle, 33/45 RPM switches, ±8% tactile pitch fader with center detent, and track skip controls (`◀◀` / `▶▶`).

### Changed
- Replaced system metadata retrievers with native binary tag parsing for FLAC, MP3, and WAV.
- Expanded Robolectric test coverage to 12 test suites and 106 unit tests.

---

## [0.2.0] - 2026-10-12

### Added
- **Custom 32-Bit Float AudioSink:** Dedicated audio processing pipeline in front of ExoPlayer preserving high-resolution audio up to 24-bit/192 kHz.
- **Sound Panel:** 10-band graphic equalizer with ISO center frequencies, automatic headroom pre-attenuation, AutoEq headphone profile import (`ParametricEQ.txt`), ReplayGain, and look-ahead limiter at −0.3 dBFS.
- **Pure Mode:** Bit-perfect passthrough disabling all DSP filters. Android 14+ USB DAC bit-perfect output support.
- **Pressing Plant:** Custom record creator with 6 generative sleeve patterns, 6 vinyl formulations (Black, Solid Colour, Clear, Splatter, Marble, Picture Disc), and up to 40 tracks.
- **Friday Drops:** Deterministic weekly release of 10 Creative Commons netlabel albums from Internet Archive with a 3-album weekly claim quota.
- **Serverless Sharing:** Portable `.vinyl` file distribution via Android system share sheet (Quick Share, Telegram, WhatsApp, Drive).
- **Portrait Layout:** Responsive dual-mode UI supporting vertical stacking (turntable on top, crate below).

### Fixed
- Fixed folder scanning bug where Android's storage provider failed to return files for nested directories.

---

## [0.1.0] - 2026-10-01

### Added
- Initial prototype listening room.
- Storage Access Framework folder selection.
- 3D perspective vinyl crate.
- Basic spinning turntable with tonearm drag-and-drop.
- Real-time synthesised vinyl SFX (needle drop, surface crackle, runout groove).
- MediaSession background audio playback service.
