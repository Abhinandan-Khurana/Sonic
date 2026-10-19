# Runout 0.3.0 Release Notes

**Runout 0.3.0** is a major evolutionary release bringing a complete visual and mechanical overhaul of the turntable, picture discs with album artwork printed directly onto the vinyl, an 8-bit dot-matrix pixel screen, decentralized user profiles over public Nostr relays, and SHA-256 verified `.vinyl` package transfers.

---

### Highlights in 0.3.0

#### 1. The Glass Turntable ("Glass Drive RG-1")
- Rebuilt from the ground up inspired by the classic direct-drive DJ deck.
- Plinth crafted from frosted liquid glass with real optical refraction shaders (Kyant Backdrop) on Android 13+.
- Platter rim featuring 4 rows of 180 rotating strobe dots.
- S-shaped chrome tonearm with counterweight, anti-skate dial, and genuine tonearm arc geometry solved via the law of cosines.
- Tactile pitch fader with ±8% adjustment, center zero-detent indicator light, and double-tap reset.

#### 2. Picture Discs
- Every album with artwork is pressed as a picture disc: cover art rotates smoothly under the grooved vinyl texture with a stationary lighting sheen.
- Dynamically rendered smooth groove bands reflecting actual track positions and durations.

#### 3. Integrated Dot-Matrix Pixel Screen
- Mint-green dots on dark smoked glass driven by the Silkscreen typography engine.
- Real-time scrolling track metadata, playback timer, RPM speed indicator, and cue readouts.
- TalkBack screen reader accessibility integration.

#### 4. Decentralized Identity via Nostr Relays
- Completely serverless account creation with local 256-bit private keys (`nsec1...`).
- Public profiles and shelf catalogs published as signed kind-33760 events across public Nostr relays (`relay.damus.io`, `nos.lol`, `relay.primal.net`).
- Prefix search powered by relay tag indexing with collision prevention via 4-character key suffixes (`@abhi·7F3A`).

#### 5. Checksummed `.vinyl` Record Transfers
- Every audio file within a `.vinyl` bundle is indexed with its SHA-256 fingerprint.
- Hostile path traversals ("zip-slip") and corrupted audio files are detected and rejected on arrival.

---

### Verification
- **SHA-256:** `4655cf36f067b3b5eebf0b3cb8b4cd6c1f41d464aef59d99cf155f3adb4eb5c2`
- **File:** `Runout-0.3.0.apk` (26.9 MB)
