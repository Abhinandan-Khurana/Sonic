# Changelog

All notable changes to **Runout** are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

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
