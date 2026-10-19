# Runout Architecture & Internal Systems

Runout is engineered with an emphasis on **audio fidelity**, **serverless decentralization**, and **tactile visual design**. This document outlines the technical design of its core subsystems.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              JETPACK COMPOSE UI                             │
│  ListeningRoom ────► Turntable (Canvas) ────► Crate (3D Perspective)        │
│       │                      │                         │                    │
│       ▼                      ▼                         ▼                    │
│  Panels (Glass)       Pixel Screen Canvas        Record Art (Picture Disc)  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            PLAYBACK & DECK ENGINE                           │
│  Deck.kt ◄─────────────────────────► PlaybackService (MediaSession)         │
│  (Needle tracking, RPM, hold, flip)   │                                     │
│                                       ▼                                     │
│                             AndroidX Media3 ExoPlayer                       │
│                                       │                                     │
│                                       ▼                                     │
│                             Runout DSP Float Sink                           │
│                             ├─ ReplayGain                                   │
│                             ├─ Automatic Headroom Preamp                    │
│                             ├─ 10-Band Biquad EQ (or AutoEq)                │
│                             ├─ Warmth Analog Shelf                          │
│                             └─ 1.5ms Look-Ahead Peak Limiter                │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             DECENTRALIZED DATA                              │
│  LibraryRepository (SAF Scan)       Nostr Relay Client (Damus, Nos, Primal) │
│  TagReader (FLAC/MP3/WAV)           BIP-340 Schnorr / NIP-19 Bech32 Keys    │
│  ShareRepository (.vinyl zip)       Friday Drops Client (Internet Archive)  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Custom 32-Bit Float DSP AudioSink

### The Challenge
Standard Android audio players (including standard Media3 / ExoPlayer setups) fold audio down to 16-bit integer PCM when software audio effects or equalizers are attached. This truncates 24-bit high-resolution audio files (such as 24-bit/96kHz or 24-bit/192kHz FLAC) and limits dynamic headroom.

### Runout's Solution: `DspAudioSink.kt`
Runout implements a custom `AudioSink` wrapper positioned directly in front of Android's `AudioTrack`:
1. **Dynamic Conversion:** Incoming audio buffers (whether 16-bit PCM, 24-bit packed PCM, 32-bit integer PCM, or float) are converted into standardized 32-bit floating-point arrays (`FloatArray`).
2. **Float DSP Processing Chain:**
   - **ReplayGain:** Applies gain adjustment derived from Vorbis/ID3 tags without clipping.
   - **Automatic Headroom Calculation:** Pre-attenuates the master signal by $\max(0, \max(\text{EQ Boosts}))$, ensuring additive EQ curves cannot exceed digital full scale (0 dBFS).
   - **10-Band Biquad Filter Bank:** Peaking filters with Q=1.414 calculated dynamically for the track's sample rate (44.1 kHz, 48 kHz, 96 kHz, 192 kHz).
   - **Warmth Analog Emulation:** A subtle +1.5 dB low-shelf boost at 110 Hz combined with a -2 dB high-shelf rolloff above 9 kHz.
   - **Look-Ahead Limiter:** A 1.5 ms circular buffer with fast attack and smooth exponential decay holding peak output at −0.3 dBFS.
3. **Pure Mode Passthrough:** When Pure Mode is active, all DSP blocks are bypassed. The float or PCM buffer passes directly to `AudioTrack`.
4. **Bit-Perfect DAC Output:** On Android 14+ (API 34+), Runout queries `AudioTrack` and the connected USB audio device for bit-perfect output mode, bypassing the Android system mixer entirely.

---

## 2. Audio File Ingestion & Scoped Storage

### Zero Broad Storage Permissions
To ensure user privacy and compatibility with modern Android security standards, Runout requires **no device-wide storage permissions** (`READ_MEDIA_AUDIO` or `MANAGE_EXTERNAL_STORAGE`).

Instead, Runout operates via the **Storage Access Framework (SAF)**:
- The user selects an album folder using `ACTION_OPEN_DOCUMENT_TREE`.
- The app persists URI read permissions via `contentResolver.takePersistableUriPermission()`.
- `LibraryScanner.kt` traverses the document tree up to 8 levels deep, ignoring hidden directories (starting with `.`).

### Native Streaming Tag Reader (`TagReader.kt`)
Android's built-in `MediaMetadataRetriever` frequently fails on specialized FLAC block layouts or variable bitrate MP3s. Runout embeds a pure-Kotlin binary tag parser:
- **FLAC:** Reads `fLaC` stream marker, extracts `STREAMINFO` block (sample rate, bit depth, channel count, sample count), parses `VORBIS_COMMENT` blocks for tags and ReplayGain metadata, and extracts `PICTURE` blocks.
- **MP3:** Reads ID3v2.2, ID3v2.3, and ID3v2.4 headers, unpacks unsynchronized APIC frames, and measures duration from Xing, Info, or VBRI headers.
- **WAV:** Traverses RIFF chunks, parses `fmt ` descriptors, and extracts `LIST` INFO chunks.

---

## 3. Serverless Social & Identity Mesh (Nostr)

Runout operates without centralized user databases, authentication servers, or backend APIs.

```
Phone A (User @abhi·7F3A)                  Public Nostr Relays
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

### Identity Specification
- **Keys:** 256-bit private key generated on device using `SecureRandom`. Encoded as `nsec1...` and `npub1...` via Bech32 (NIP-19).
- **Signatures:** Pure-Kotlin implementation of **BIP-340 Schnorr signatures** over the `secp256k1` elliptic curve. Verified against all official Bitcoin test vectors.
- **Event Kind 33760:** An addressable, replaceable event storing user display metadata and catalog JSON.
- **Tag Prefix Search:** Relays index events with tag arrays: `["n", "a"]`, `["n", "ab"]`, `["n", "abh"]`, `["n", "abhi"]`. This enables instant prefix queries across public relays.

---

## 4. Division of Vinyl Sides

Real vinyl records have physical capacity limits (~22 minutes per side). Runout translates digital album tracklists into authentic physical vinyl structures via `RecordModel.kt`:
1. Total album duration is calculated.
2. Albums under ~26 minutes become a single-sided LP (Side A).
3. Albums between 26 and 48 minutes are divided into Side A and Side B using a partition algorithm that balances side durations without ever splitting an individual track.
4. Albums over 48 minutes are pressed as Double LPs (Sides A, B, C, D).

---

## 5. Visual Pipeline & Shaders

- **Canvas-Drawn Turntable:** The plinth, rotating platter, vinyl disc, tonearm assembly, strobe dots, and pitch fader are rendered on a hardware-accelerated Compose `Canvas`.
- **Kyant Backdrop:** Utilizes Android 13+ RenderEffect shaders to render frosted liquid glass with true optical refraction and light dispersion over dynamic album art.
- **Silkscreen Pixel Font:** An open-license 8-bit dot-matrix font rendered pixel-to-dot to emulate hardware LED/VFD displays with horizontal marquee text scrolling.
