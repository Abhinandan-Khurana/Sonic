> Historical v0.3 documentation: Soulseek replaces the online Nostr features in v0.4, and music is FLAC-only. See [current Soulseek behavior](SOULSEEK.md) and [build instructions](BUILDING.md).

# Sonic — Project Handbook

> **A vinyl listening room and record-collecting app for Android**  
> *The idea, how it works, what's built, what's next.*  
> **Version described:** 0.3.0 (October 2026)

---

## Table of Contents

1. [The Idea in One Breath](#1-the-idea-in-one-breath)
2. [Why It Exists, and the Rules It Lives By](#2-why-it-exists-and-the-rules-it-lives-by)
3. [What Using It Feels Like (A Walkthrough)](#3-what-using-it-feels-like-a-walkthrough)
4. [Every Feature, in Detail](#4-every-feature-in-detail)
   - [4.1 The Listening Room and the Glass Turntable](#41-the-listening-room-and-the-glass-turntable)
   - [4.2 Your Music Folder](#42-your-music-folder)
   - [4.3 Sound: Lossless, Hi-Res, EQ, Pure Mode](#43-sound-lossless-hi-res-eq-pure-mode)
   - [4.4 Accounts, Usernames, and Profiles (No Server)](#44-accounts-usernames-and-profiles-with-no-server)
   - [4.5 Friends](#45-friends)
   - [4.6 Sending Records to Friends](#46-sending-records-to-friends-and-300400-mb-albums)
   - [4.7 Pressing Plant: Make Your Own Vinyl](#47-pressing-plant-make-your-own-vinyl)
   - [4.8 Friday Drops](#48-friday-drops)
   - [4.9 The Collecting Game (Planned)](#49-the-collecting-game-planned)
5. [How It Works Under the Hood](#5-how-it-works-under-the-hood)
6. ["No Server": Exactly Where Everything Lives](#6-no-server-exactly-where-everything-lives)
7. [Tech Stack and Licences](#7-tech-stack-and-licences)
8. [Look and Feel](#8-look-and-feel)
9. [Testing and Quality](#9-testing-and-quality)
10. [Version History](#10-version-history)
11. [Honest Limits and Risks](#11-honest-limits-and-risks)
12. [What to Build Next](#12-what-to-build-next)
13. [Questions People Will Ask (and Answers)](#13-questions-people-will-ask-and-answers)
14. [Numbers Cheat Sheet](#14-numbers-cheat-sheet)
15. [Glossary](#15-glossary)
16. [Project Files, Installing, and Building](#16-project-files-installing-and-building)

---

## 1. The Idea in One Breath

### One Line
A free vinyl app: your own albums become records you pull from a crate, drop on a glass turntable and play, lossless, and share with friends, with no server behind it.

### 30 Seconds
Streaming made music endless and weightless. Sonic brings back the ritual of records. Pick the folder where your albums live and each one becomes a record on your shelf, with its real cover printed on the vinyl. You flick through a crate, pull a record out, drag it onto a see-through turntable and lower the needle. It plays in full quality, up to 24-bit/192 kHz, with a pro-grade EQ. Every Friday ten free Creative Commons albums drop, and you can claim three. You can find friends by username, see their shelves, and send records phone to phone. There's no company server: everything lives on the phones, and the little that's public sits on free community relays.

### 2 Minutes
- **A collecting app, not a streaming app:** You only play what you own: your folder, drops you claimed, records friends sent you, and records you pressed yourself.
- **The turntable is the interface:** Modelled on the classic DJ direct-drive deck: strobe-dotted platter, S-shaped tonearm, start/stop, track skip and a tiny pixel screen showing the track. Records play at the speed they were recorded at. Drop the needle anywhere to jump in the side. Hold the record to slow it down. Flip it when side A ends.
- **Sound is taken seriously:** Hi-res output in 32-bit float, a 10-band EQ, headphone correction profiles, loudness levelling, a limiter, and a Pure mode that sends the music untouched (bit-perfect to a USB DAC on Android 14+).
- **Serverless sharing:** A record travels as a single `.vinyl` file over Quick Share, Telegram, WhatsApp or Drive, checked byte for byte on arrival.
- **Free, no ads, and costs nothing to run.**

---

## 2. Why It Exists, and the Rules It Lives By

### The Problem
- Streaming apps are flat lists. Nothing feels owned, nothing is scarce, nothing is a ritual.
- People who care about sound often keep lossless files (FLAC) that most players flatten to 16-bit or don't present with any care.
- Music sharing between friends lost its warmth: no more passing a record across a room.

### Who It's For
- People who download or buy albums (Bandcamp, Qobuz, CD rips) and want a beautiful, serious player for them.
- Vinyl lovers who want the tactile feel of records on their phone.
- Friend groups who like swapping music and seeing each other's collections.
- Independent artists who want to "press" and give away their own music.

### The Design Rules
1. **Free, and no server to run or pay for:** A solo builder with no budget can keep it alive.
2. **A limited vinyl experience, not unlimited streaming:** One record on the platter. Sides end. You flip them. Nothing autoplays. Scarcity comes from time and choice, not money.
3. **Your music stays yours:** Playback is local and offline from your own folder.
4. **Lossless in, lossless out:** Files are never re-encoded. Labels say exactly what a file is (`FLAC · 24-bit · 96 kHz`), never guessed upwards.
5. **Rights-safe sharing:** Audio travels only for your own music and Creative Commons records. Everything else travels as a "card" (sleeve, vinyl design, track list). This is what keeps the app publishable on Google Play.
6. **The turntable is the interface:** Gestures you'd use on a real deck do the real thing.
7. **Honest about limits:** When something can't be done without a server, the app says so.

---

## 3. What Using It Feels Like (A Walkthrough)

### First Launch
1. **A white room:** Glass turntable on the left, an empty crate on the right, tiny labels in the corners (`SONIC · your room`, the time, `Choose a folder to begin`).
2. **Pick your folder:** Tap the empty sleeve and pick your music folder with Android's folder picker (SAF). No broad storage permission is needed; the app gets access to that one folder only.
3. **Crate populates:** Albums appear in the crate one by one as the folder is read. Each gets its cover: from inside the files, from a `cover.jpg` in the folder, or found online via MusicBrainz / Cover Art Archive.
4. **Empty folder reporting:** If a folder has no music, the empty sleeve states what it found instead (e.g. `found .m4p ×12`).

### Playing a Record
1. **Flick the crate sideways:** Sleeves stand in perspective; the one in the middle turns to face you and lifts.
2. **Pull the disc:** Tap the front sleeve. The record slides out (upwards in portrait).
3. **Load the platter:** Drag the disc onto the platter. It grows as it nears the spindle and snaps into place.
4. **Drop the needle:** Lower the tonearm onto the record (drag it, tap it, or press START/STOP). You hear the needle drop and authentic surface crackle, then the music. The record spins with the album cover printed on it.
5. **Pixel screen:** The pixel screen scrolls `A1 TRACK NAME · ARTIST` and shows playback time, or CUE when a song is waiting under the needle.
6. **Needle drop cueing:** Drop the needle anywhere on the record to jump to that point of the side. Gaps between tracks show as smooth bands in the grooves.
7. **Track skipping:** `◀◀` / `▶▶` skip tracks. At the end of a side, `▶▶` turns the record over.
8. **Platter hold:** Hold the record with a finger: the music winds down. Release: it spins back up.
9. **Cue from search:** Dig › "Put on the platter" loads the record and parks the needle at that song. Press START/STOP to play it.
10. **Side flip:** When side A ends, you hear the runout groove; tap the record to flip it to side B.

### The Four Panels
- **Drops:** This Friday's ten free albums; claim three.
- **Friends:** Your account and handle, search people by username, open their profiles and shelves.
- **Sound:** Pure mode, hi-res output, EQ, ReplayGain, limiter, warmth.

### A Record's Details
Long-press a sleeve or a pulled record to open its details: format, licence, the running order by side and the total running time. The share key in the corner sends the details as a picture; Android's share sheet opens (Quick Share, Telegram, WhatsApp, Drive). Under the details: put back, fetch missing tracks, and remove record (not for music from your folder).

---

## 4. Every Feature, in Detail

### 4.1 The Listening Room and the Glass Turntable

- **Layout `[BUILT]`:** Landscape (turntable left, crate right) and portrait (turntable top, crate bottom, panels below). Follows phone orientation.
- **The Turntable (Glass Drive RG-1) `[BUILT]`:**
  - Frosted glass plinth with optical refraction shaders (Android 13+).
  - Platter rim with 4 rows of 180 strobe dots.
  - S-shaped chrome tonearm with counterweight, anti-skate dial, and exact law-of-cosines tracking geometry.
  - Start/Stop and track skip controls. No speed keys, pitch fader or power knob: records always play at recorded speed.
- **The Pixel Screen `[BUILT]`:**
  - Dot-matrix screen built into the plinth with mint dots on dark smoked glass.
  - Driven by the Silkscreen pixel font at native 1-to-1 dot resolution.
  - Displays track code, title, artist, running time and cue states.
  - Fully accessible with Android TalkBack.
- **Picture Disc Records `[BUILT]`:**
  - Covers printed directly on vinyl under grooves with stationary lighting sheen.
  - Track gap bands reflecting true track lengths.
  - Dynamic vinyl styling (clear, tinted, splatter, marble).
- **Turntable SFX `[BUILT]`:** Real-time synthesised audio for needle drop, surface crackle, needle lift, and runout thumps.
- **Background Playback `[BUILT]`:** MediaSessionService with notification and lock screen controls.

### 4.2 Your Music Folder

- **Storage Access Framework `[BUILT]`:** Walks selected folder up to 8 levels deep.
- **Native Tag Reader `[BUILT]`:**
  - **FLAC:** Stream info, Vorbis comments, embedded picture art.
  - **MP3:** ID3v2.2/2.3/2.4, embedded APIC covers, Xing/VBRI headers.
  - **WAV:** Format chunk, LIST INFO metadata.
- **Cover Art Resolution Cascade `[BUILT]`:**
  1. Embedded audio file metadata.
  2. Local folder image (`cover.jpg`, `folder.jpg`, `artwork.png`).
  3. Online lookup via MusicBrainz + Cover Art Archive.
  4. Generative typographic pastel sleeve.
- **Honest Quality Readout `[BUILT]`:** Strict, non-interpolated labels (`FLAC · 24-bit · 96 kHz`, `MP3 · 320 kbps`).

### 4.3 Sound: Lossless, Hi-Res, EQ, Pure Mode

- **32-Bit Float DSP Sink `[BUILT]`:** Custom Media3 audio sink preserving full 32-bit floating-point precision up to 24-bit/192 kHz.
- **Processing Chain `[BUILT]`:**
  1. *ReplayGain:* Album, Track, or Off modes.
  2. *Automatic Headroom:* Preamp gain reduction preventing EQ boost clipping.
  3. *10-Band Graphic EQ:* Biquad peaking filters (31 Hz to 16 kHz, ±12 dB).
  4. *AutoEq Headphone Correction:* Direct import of `ParametricEQ.txt`.
  5. *Warmth Filter:* Gentle analog cartridge curve (+1.5 dB low shelf, -2 dB high shelf).
  6. *Look-ahead Limiter:* 1.5 ms buffer with −0.3 dBFS ceiling.
- **Pure Mode `[BUILT]`:** Bypasses all processing. On Android 14+, requests bit-perfect USB output.

### 4.4 Accounts, Usernames, and Profiles (No Server)

- **Cryptographic Identity `[BUILT]`:** Local secp256k1 BIP-340 Schnorr keypair. No passwords, no central auth database.
- **Nostr Relay Publishing `[BUILT]`:** Profiles published as kind-33760 addressable events across public relays (`relay.damus.io`, `nos.lol`, `relay.primal.net`).
- **Handles & Suffixes `[BUILT]`:** Handle includes 4 characters of public key hash (`@abhi·7F3A`) ensuring unique identity without centralized reservations.
- **Prefix Search `[BUILT]`:** Client-side prefix tag queries enabling rapid username discovery.
- **Key Backup & Recovery `[BUILT]`:** Export/import via standard NIP-19 `nsec1...` strings.

### 4.5 Friends

- **Adding Friends `[BUILT]`:** Via Nostr search or by swapping offline `.vinyl` Friend Cards.
- **Friend Shelves `[BUILT]`:** View friends' record collections with shared-ownership indicators.

### 4.6 Sending Records to Friends

- **The `.vinyl` Package `[BUILT]`:**
  ```text
  record.vinyl (ZIP container)
  ├── manifest.json       # Metadata, credits, tracklist, license
  ├── sleeve.jpg          # Album cover art
  ├── checksums.json      # SHA-256 hash of each audio file
  └── audio/              # Original audio files (unaltered)
  ```
- **Rights-Safe Sharing Rule `[BUILT]`:** Audio travels only for personal original pressings and CC albums. Commercial albums travel as metadata cards.
- **Integrity Verification `[BUILT]`:** Every audio byte checked against SHA-256 fingerprint; zip-slip and malformed archives rejected.

### 4.7 Pressing Plant (removed)

The Pressing Plant was removed in the UI redesign. Records pressed with it earlier stay in the crate, playable and removable.

### 4.8 Friday Drops

- **Weekly Drops `[BUILT]`:** 10 curated Creative Commons albums from the Internet Archive Netlabel collection.
- **Deterministic Selection `[BUILT]`:** Every phone derives the exact same 10 albums using the current calendar week number as a PRNG seed.
- **Claim Allowance `[BUILT]`:** Claim up to 3 albums per week with direct lossless downloads.

---

## 5. How It Works Under the Hood

```
YOUR ANDROID PHONE
┌────────────────────────────────────────────────────────────────────────┐
│  Music Folder ──SAF Scan──► Local Library (Shelf) ──┐                  │
│  Received .vinyl Packages ─────────────────────────┼──► Crate          │
│  Claimed Friday Drops ─────────────────────────────┤      │            │
│  Custom Pressings ─────────────────────────────────┘      ▼            │
│                                                   Turntable (Deck)     │
│                                                           │            │
│  ExoPlayer (Media3) ──► Sonic DSP Sink ──────────────────┼────────────► Speakers / DAC
│                         (32-bit float, EQ, Limiter, Pure) │            │
│                                                           │            │
│  Local Keychain (nsec)    Settings & EQ    Covers Cache   │            │
└───────────────────────────────────┬───────────────────────┼────────────┘
                                    │                       │
                                    ▼                       ▼
                         Public Nostr Relays         Quick Share / Apps
                         (damus, nos.lol, primal)    (Telegram, WhatsApp)
```

---

## 6. "No Server": Exactly Where Everything Lives

| Component | Storage Location | Operator / Cost |
| :--- | :--- | :--- |
| **Your Music Library** | Local phone storage | User / Free |
| **Audio Processing** | On-device 32-bit float engine | User / Free |
| **Cover Artwork** | MusicBrainz + Cover Art Archive (cached locally) | Community / Free |
| **Friday Drops** | Internet Archive CDN | Non-profit / Free |
| **User Profiles & Shelves** | 3 Public Nostr relays (signed JSON) | Decentralized / Free |
| **User Discovery** | Nostr relay tag search | Decentralized / Free |
| **Record Transfers** | Peer-to-peer / messaging share sheet | User / Free |

---

## 7. Tech Stack and Licences

- **Language & UI:** Kotlin 2.3, Jetpack Compose, Canvas drawing
- **Audio Engine:** AndroidX Media3 ExoPlayer 1.9.4 + Custom Float AudioSink (Apache 2.0)
- **Visual Shaders:** Kyant Backdrop 2.0.0-alpha03 (Apache 2.0)
- **Networking:** OkHttp 4.12.0 for WebSocket relay connections (Apache 2.0)
- **Typography:** Inter (SIL Open Font License 1.1), Silkscreen (OFL 1.1)
- **Cryptography:** BIP-340 Schnorr, secp256k1, SHA-256, Bech32 (Pure Kotlin)
- **Testing:** JUnit, Robolectric 4.16.1, Roborazzi 1.76.0, Compose UI Test

---

## 8. Testing and Quality

Sonic maintains a strict testing regime with **12 test suites, 106 tests, and 0 Android Lint errors**:

| Suite | Focus Area |
| :--- | :--- |
| `FolderScanTest` | SAF folder walking, tag extraction, hidden file filtering |
| `TagReaderTest` | Native FLAC, MP3, and WAV parsing without system decoders |
| `RecordModelTest` | Vinyl side division, audio quality tags, serialization |
| `DspTest` | Biquad filter frequency responses, headroom, Pure mode bit identity |
| `AudioOutputTest` | Float audio sink conversion, 24-bit/96kHz passthrough |
| `TurntableDeckTest` | Plinth geometry, tonearm tracking arc, pixel font rendering |
| `SharingTest` | `.vinyl` zip creation, SHA-256 verification, zip-slip defense |
| `AccountsTest` | BIP-340 Schnorr test vectors, Nostr relay WebSocket protocol |
| `FriendsTest` | Social graph, friend card parsing, mutual shelf overlap |
| `FridayDropsTest` | Deterministic week shuffling, Internet Archive API parsing |
| `ListeningRoomUiTest` | Compose UI layout in portrait and landscape orientations |

---

## 9. Numbers Cheat Sheet

- **Friday Drops:** 10 albums per week, 3 claimable, deterministic weekly seed
- **Side Durations:** ~22 minutes per side; double LPs split into Sides A/B/C/D
- **Turntable speed:** records always play at their recorded speed. The on-screen platter turns at half of 33⅓ rpm (one turn in 3.6 s), with a strobe that stands still at that speed.
- **Audio Precision:** Up to 24-bit/192 kHz, 32-bit floating-point processing
- **EQ Architecture:** 10 bands (31 Hz – 16 kHz), ±12 dB adjustment, AutoEq support
- **Safety Ceiling:** Look-ahead limiter holding peaks below −0.3 dBFS
- **Relays Connected:** `relay.damus.io`, `nos.lol`, `relay.primal.net`
- **Package Limits:** Max 4 GB per `.vinyl` package, SHA-256 per track

---

## 10. Glossary

- **AutoEq:** Free headphone-correction equalization profiles derived from laboratory measurements.
- **Bit-perfect:** Audio output where samples reach the DAC completely unaltered by Android mixer.
- **Card:** A `.vinyl` record bundle containing sleeve, vinyl artwork, and tracklist without audio files.
- **Nostr:** An open, censorship-resistant protocol for signed JSON notes stored across public relays.
- **nsec / npub:** Bech32-encoded private and public keys used in the Nostr protocol.
- **Picture Disc:** A vinyl record where full-color artwork is pressed beneath the audio grooves.
- **Pure Mode:** Signal path where EQ, ReplayGain, warmth, and pitch modifiers are completely bypassed.
- **Sonic Groove:** The spiral groove at the end of a record side where the tonearm rests once music ends.
- **Storage Access Framework (SAF):** Android's sandboxed document provider granting scoped access to a single user-chosen directory.
