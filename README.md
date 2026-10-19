<div align="center">

# ◈ RUNOUT

### *A vinyl listening room and record-collecting app for Android*

**Your own albums become records you pull from a crate, drop on a glass turntable, and play in bit-perfect hi-res audio — with serverless sharing and zero running costs.**

[![Platform](https://img.shields.io/badge/Platform-Android%209%2B%20(API%2028--36)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Audio-Media3%20ExoPlayer%201.9.4-FF6F00?style=flat-square)](https://developer.android.com/media/media3)
[![Nostr](https://img.shields.io/badge/Network-Nostr%20Relay%20Mesh-9B51E0?style=flat-square)](https://nostr.com)
[![Tests](https://img.shields.io/badge/Tests-106%20Passing%20(12%20Suites)-brightgreen?style=flat-square)](docs/BUILDING.md)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

<br/>

[**Download APK v0.3.0 (26.9 MB)**](release/Runout-0.3.0.apk) • [**Release Notes**](RELEASE.md) • [**Source (.zip)**](release/Runout-0.3.0-source.zip) • [**Handbook**](docs/HANDBOOK.md) • [**Architecture**](docs/ARCHITECTURE.md)

<br/>

```text
    ┌────────────────────────────────────────────────────────────────────────┐
    │  [RUNOUT · your room]                               3:42 PM · 14 LPs   │
    │                                                                        │
    │       ╭─────────────────────╮             ┌──────┐ ┌──────┐ ┌──────┐   │
    │      ╱   . . . . . . . . .   ╲            │      │ │      │ │      │   │
    │     │  .  ╭─────────────╮  .  │           │      │ │  ★   │ │      │   │
    │     │ .  │ PICTURE DISC │  . │   ======►  │      │ │ FRONT│ │      │   │
    │     │ .  │  ALBUM ART   │  . │            │      │ │ SLEEV│ │      │   │
    │     │  .  ╰─────────────╯  .  │           │      │ │      │ │      │   │
    │      ╲   . . . . . . . . .   ╱            └──────┘ └──────┘ └──────┘   │
    │       ╰─────────────────────╯             [  CRATE · 3D PERSPECTIVE ]  │
    │        [ GLASS DRIVE RG-1 ]                                            │
    │                                                                        │
    │   [A1 TRACK · ARTIST]                      [DROPS]  [FRIENDS]  [SOUND] │
    │   01:45  33 RPM  +0.0%                     [PRESSING PLANT]            │
    └────────────────────────────────────────────────────────────────────────┘
```

</div>

---

## The Pitch

### In One Breath
> **A free vinyl app:** your own albums become records you pull from a crate, drop on a glass turntable and play, lossless, and share with friends, with no server behind it.

### In 30 Seconds
Streaming turned music weightless and endless. Runout brings back the tactile ritual of vinyl records. Pick the folder where your audio files live: each album is pressed into a physical record on your shelf, with its real cover printed on the disc. Flick through your crate, slide a record out, drop it on a see-through glass turntable, and lower the needle. 

It plays up to **24-bit/192 kHz** through a custom 32-bit float audio engine with studio EQ, AutoEq headphone correction, and bit-perfect USB output. Every Friday, ten free Creative Commons albums drop. Swap records with friends phone-to-phone or search profiles across public Nostr relays. There is no company server: everything lives on your phone, and the app costs nothing to run.

---

## ⚡ What Makes Runout Different

| Principle | Streaming Apps | Runout |
| :--- | :--- | :--- |
| **Ownership** | Rented access; tracks vanish when licenses expire | Offline from your own storage. What you own stays yours. |
| **The Ritual** | Infinite algorithmic queues | One record on the platter. Sides end. You flip them. |
| **Audio Path** | Lossy AAC/Opus, resampled by Android mixer | 32-bit float DSP sink; bit-perfect USB DAC output on Android 14+. |
| **Backend** | Corporate servers tracking listening behavior | **Zero company servers.** Identity and search run on public Nostr relays. |
| **Sharing** | Web URLs to streaming paywalls | Self-contained `.vinyl` archives verified byte-for-byte with SHA-256. |

---

## 🎧 The Listening Room & Turntable

```
                                      [Tonearm Base]
                                      (Height Collar)
                                      (Anti-Skate Dial)
                                            \
       ┌────────────────────────┐            \ [Chrome S-Arm]
       │   STROBE PLATTER RIM   │             \ (Law of Cosines)
       │  4 Rows · 180 Dots/Row │              \
       │  ....................  │               [Headshell]
       │  :   PICTURE DISC   :  │                    \
       │  :  (Cover on Vinyl):  │                     ▼
       │  :         ☉        :  │                (Diamond Stylus)
       │  :..................:  │
       └────────────────────────┘
       
       [DOT-MATRIX PIXEL DISPLAY]         [TACTILE PITCH FADER]
       ┌────────────────────────┐         ┌──┐ -8%
       │ A2 GLASS HOURS · INES  │         │  │
       │ 01:23   33 RPM   +0.0% │         ├──┤ [Green Zero LED]
       │ [Silkscreen Pixel Font]│         │  │
       └────────────────────────┘         └──┘ +8%
```

- **Glass Drive RG-1 Deck:** Modeled after classic direct-drive DJ decks. Frosted liquid glass plinth with real-time optical refraction shaders (Kyant Backdrop) on Android 13+.
- **Picture Discs:** Albums with artwork are pressed as picture discs: full-color cover turns under grooved vinyl with a stationary lighting sheen.
- **True Tonearm Tracking Geometry:** Solved with the **Law of Cosines** so the stylus traces an exact physical arc across track gaps from lead-in to runout groove.
- **Integrated Pixel Screen:** An 8-bit dot-matrix display (Silkscreen OFL font) scrolls track titles at 1 dot/90 ms with time, RPM, and cue readouts.
- **Physical Gestures:**
  - Drag the needle anywhere to seek; tap to drop at lead-in.
  - Hold the spinning disc to brake and wind down the music; release to spin up.
  - Flip side when Side A ends; double-tap pitch fader to reset to zero detent.
  - Real-time synthesised needle-drop thumps, surface crackle, and runout loops.

---

## 🔊 Pro Sound Architecture

Runout routes audio through a **custom 32-bit float audio sink** (`DspAudioSink.kt`) placed directly in front of Android's `AudioTrack`. Unlike standard players, high-resolution audio (up to 24-bit/192 kHz) is never folded down to 16-bit.

```
Incoming Audio (FLAC, WAV, MP3, ALAC)
                 │
                 ▼
       32-Bit Float Normalization
                 │
                 ▼
       ReplayGain (Album / Track Mode)
                 │
                 ▼
       Automatic Headroom Preamp (Prevents EQ Clipping)
                 │
                 ▼
       10-Band Biquad Graphic EQ (ISO Bands 31Hz - 16kHz)
       OR AutoEq Parametric Engine (Imports ParametricEQ.txt)
                 │
                 ▼
       Warmth Analog Shelf (+1.5 dB @ 110Hz / -2 dB @ 9kHz)
                 │
                 ▼
       1.5ms Look-Ahead Peak Limiter (-0.3 dBFS Ceiling)
                 │
                 ▼
       Pure Mode / Bit-Perfect Output (Android 14+ USB DAC)
```

---

## 🌐 Serverless Nostr Mesh & Identity

Runout has **no accounts database and no central server**. Identity and social features are powered by public **Nostr relays**:

```
Phone A (@abhi·7F3A)                      Public Nostr Relays
┌────────────────────────┐              ┌──────────────────────┐
│ Generates secp256k1 key│              │   relay.damus.io     │
│ Signs Kind 33760 Event ├─────────────►│   nos.lol            │
│ (Profile + 600 albums) │              │   relay.primal.net   │
└────────────────────────┘              └──────────┬───────────┘
                                                   │
Phone B (Search "abhi")                            │
┌────────────────────────┐                         │
│ Query prefix tag: abhi ├─────────────────────────┘
│ Validates BIP-340 sig  │
│ Renders Friend's shelf │
└────────────────────────┘
```

- **Cryptographic Keys:** Local 256-bit private key (`nsec1...`). Back up your key to move to a new phone.
- **BIP-340 Schnorr Signatures:** In-tree pure-Kotlin implementation verified against Bitcoin test vectors.
- **Kind 33760 Profiles:** Replaceable signed events holding your username, display name, color theme, and shelf catalog (up to 600 albums).
- **Tag Prefix Search:** Relays index username character prefixes, enabling instant $O(1)$ search queries without a database.
- **Collision-Resistant Handles:** Unique 4-character public key suffix (`@abhi·7F3A`) prevents name-squatting without centralized registration.

---

## 📦 The `.vinyl` Package Format

Music travels between phones as a single, self-contained **`.vinyl` archive**:

```text
record.vinyl (ZIP Container)
├── manifest.json       # Metadata, credits, tracklist, license, side splits
├── sleeve.jpg          # High-resolution square cover art
├── checksums.json      # SHA-256 digest of every audio file
└── audio/              # Original, untouched audio bitstreams
    ├── 01_track.flac
    └── 02_track.flac
```

- **Rights-Safe Sharing:**
  - **Audio travels** for personal original pressings (*"I made this music"*) and Creative Commons releases.
  - **Cards travel** for commercial albums: friends receive the 3D sleeve, vinyl design, notes, and tracklist without copyrighted audio.
- **SHA-256 Verification:** Every incoming audio file is validated against its cryptographic checksum. Corrupted files are refused.
- **Hardened:** Zip-slip path traversal defenses, path sanitization, and a 4.0 GB size ceiling.

---

## 🏭 Pressing Plant & Friday Drops

### The Pressing Plant
- Assemble up to **40 tracks** from your library into balanced ~22-minute vinyl sides.
- Procedural sleeve generator with 6 geometric styles (*Orbit, Stripes, Bloom, Grid, Sunset, Type*) or custom photos.
- 6 vinyl formulations: *Black, Solid Colour, Clear Translucent, Splatter, Marble, and Picture Disc*.
- Honest audio labeling (`FLAC · 24-bit · 96 kHz`, `LOSSLESS MIX`, or `MIXED`).

### Friday Drops
- **10 free albums drop every Friday** from Internet Archive's netlabel collection.
- **Deterministic Derivation:** The calendar week number seeds a deterministic PRNG. Every phone on Earth derives the exact same 10 albums without a server.
- **Claim Allowance:** Claim up to **3 albums per week** with direct FLAC downloads.

---

## 🗺️ Project Structure

```text
.
├── app/                              # Android Application Module
│   ├── src/main/java/app/runout/
│   │   ├── account/                  # BIP-340 Schnorr, NIP-19 Bech32, Nostr relays
│   │   ├── drops/                    # Friday Drops deterministic engine & IA client
│   │   ├── library/                  # SAF scanner, FLAC/MP3/WAV tag readers, covers
│   │   ├── model/                    # Album, Track, VinylStyle, AudioQuality, Sides
│   │   ├── net/                      # Polite HTTP client with custom User-Agent
│   │   ├── playback/                 # MediaSessionService, Deck physics, synthesised SFX
│   │   ├── pressing/                 # Pressing Plant & generative sleeve patterns
│   │   ├── sharing/                  # .vinyl archive pack/unpack & SHA-256 checks
│   │   ├── social/                   # Friends list, card parser, shelf sync
│   │   ├── sound/                    # 32-bit float DSP sink, 10-band EQ, AutoEq, Pure
│   │   └── ui/                       # Compose listening room, turntable, crate, panels
│   └── src/test/                     # 12 Robolectric test suites (106 unit tests)
├── docs/                             # Comprehensive Documentation Hub
│   ├── HANDBOOK.md                   # Complete, unabridged project handbook
│   ├── ARCHITECTURE.md               # Technical architecture & subsystem design
│   ├── TURNTABLE.md                  # Glass Drive RG-1 anatomy & tonearm math
│   ├── SOUND.md                      # 32-bit float DSP engine & filter equations
│   ├── SHARING-AND-NOSTR.md          # Serverless Nostr identity & relay protocol
│   ├── RECORD-FORMAT.md              # .vinyl package format specification
│   ├── PRESSING-AND-DROPS.md         # Pressing Plant & Friday Drops algorithm
│   ├── BUILDING.md                   # Build prerequisites, tests, and CI guide
│   └── Runout-Project-Handbook.txt   # Original project handbook text file
├── release/                          # Standalone Release Deliverables
│   ├── Runout-0.3.0.apk              # Signed sideloadable APK (26.9 MB)
│   ├── SHA256SUMS.txt                # Checksum verification file
│   ├── RELEASE_NOTES.md              # Release highlights & changelog
│   └── README.md                     # Installation & sideloading instructions
├── .github/
│   ├── workflows/ci.yml              # GitHub Actions CI (Robolectric & Lint)
│   ├── workflows/release.yml         # Tag-driven release APK packaging
│   └── ISSUE_TEMPLATE/               # Bug & feature report templates
├── CHANGELOG.md                      # Keep a Changelog version history
├── CONTRIBUTING.md                   # Contributor guide & commit conventions
├── LICENSE                           # MIT License + third-party notices
└── build.gradle.kts                  # Root Gradle configuration
```

---

## 📖 Documentation Hub

| Document | Description |
| :--- | :--- |
| [**Project Handbook**](docs/HANDBOOK.md) | Complete guide covering philosophy, walkthrough, features, and limits. |
| [**Architecture**](docs/ARCHITECTURE.md) | Deep dive into the DSP float sink, SAF storage, and state machines. |
| [**Turntable Mechanics**](docs/TURNTABLE.md) | Tonearm geometry (Law of Cosines), strobe platter, and pixel display. |
| [**Sound Engine**](docs/SOUND.md) | 32-bit float pipeline, 10-band biquad EQ, AutoEq, and bit-perfect USB. |
| [**Nostr & Identity**](docs/SHARING-AND-NOSTR.md) | BIP-340 Schnorr signatures, relay events, and prefix search. |
| [**`.vinyl` Specification**](docs/RECORD-FORMAT.md) | Package format, manifest schema, SHA-256 checks, and rights rules. |
| [**Pressing & Drops**](docs/PRESSING-AND-DROPS.md) | Generative sleeve designs, vinyl formulations, and weekly drop math. |
| [**Building & Testing**](docs/BUILDING.md) | Gradle build targets, Robolectric test suites, and live relay checks. |

---

## 🛠️ Build from Source

### Prerequisites
- **JDK 17+** (OpenJDK 17 or 21 recommended)
- **Android SDK** with Platform 36 (`compileSdk = 36`, `minSdk = 28`)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/Abhiroop1122/runout.git
cd runout

# Build Debug APK
./gradlew assembleDebug

# Build Prototype Release APK (pre-signed with debug key)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Run the complete Robolectric test suite (106 tests across 12 suites)
./gradlew testDebugUnitTest

# Run live integration check against public Nostr relays
./gradlew testDebugUnitTest -Prunout.live=true
```

---

## 📊 Quality & Test Suites

Runout maintains a strict testing standard with **12 test suites, 106 unit tests, and 0 Android Lint errors**:

```text
✓ FolderScanTest         SAF document provider walking, hidden file filtering
✓ TagReaderTest          Native FLAC/MP3/WAV binary header parsing & cover art
✓ RecordModelTest        Vinyl side partition algorithm, audio quality attribution
✓ DspTest                Biquad filter responses at 44.1/96/192kHz, headroom, Pure mode
✓ AudioOutputTest        32-bit float audio sink buffer conversion, bit-identical passthrough
✓ TurntableDeckTest      Tonearm law-of-cosines tracking arc, RPM speed, pixel screen text
✓ SharingTest            .vinyl zip extraction, SHA-256 integrity, zip-slip defense
✓ AccountsTest           BIP-340 Schnorr test vectors, NIP-19 Bech32, mock relay WebSockets
✓ FriendsTest            Friend shelf synchronization, mutual record detection
✓ PressingPlantTest      Generative sleeve patterns, track quotas, mixed-source labeling
✓ FridayDropsTest        Deterministic week calculation, Internet Archive API parsing
✓ ListeningRoomUiTest    Responsive Compose UI rendering in portrait & landscape
```

---

## 🗺️ Roadmap

- [x] **v0.1.0:** Listening room, SAF folder scan, 3D crate, spinning turntable, synthesized SFX.
- [x] **v0.2.0:** 32-bit float audio sink, 10-band EQ, AutoEq import, Pure mode, Pressing Plant, Friday Drops.
- [x] **v0.3.0:** Glass Drive RG-1 deck, picture discs, dot-matrix pixel screen, Nostr mesh accounts, SHA-256 packages.
- [ ] **Direct WebRTC Transfers:** Browser/phone direct p2p transfers with Nostr relay handshakes.
- [ ] **QR Code Friend Swap:** Scan personal QR code in person to swap friend cards offline.
- [ ] **Headphone-Specific Profiles:** Auto-switching EQ curves based on connected Bluetooth/USB device.
- [ ] **Google Play Store Release:** Production signing, Play Protect verification, rights review.

---

## 📜 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for full details.

- **Inter Font:** SIL Open Font License 1.1 (`app/src/main/assets/licenses/Inter-OFL.txt`)
- **Silkscreen Pixel Font:** SIL Open Font License 1.1 (`app/src/main/assets/licenses/Silkscreen-OFL.txt`)
- **AndroidX Media3 & Compose:** Apache License 2.0
- **Kyant Backdrop:** Apache License 2.0
- **OkHttp:** Apache License 2.0

---

<div align="center">
Built with 🎧 for the love of vinyl records by <a href="https://github.com/Abhiroop1122">Abhiroop</a>
</div>
