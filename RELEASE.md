# Release v0.3.0 — The Glass Turntable & Serverless Mesh

**Runout 0.3.0** is the prototype release introducing the **Glass Drive RG-1** direct-drive turntable, picture discs, an integrated 8-bit dot-matrix pixel screen, decentralized user profiles over public Nostr relays, and SHA-256 verified `.vinyl` package transfers.

---

## 📥 Downloads

| Deliverable | Description | Format | Size | Checksum (SHA-256) |
| :--- | :--- | :---: | :---: | :--- |
| [**`Runout-0.3.0.apk`**](release/Runout-0.3.0.apk) | Standalone debug-signed Android app | `.apk` | 26.9 MB | `4655cf36f067b3b5eebf0b3cb8b4cd6c1f41d464aef59d99cf155f3adb4eb5c2` |
| [**`Runout-0.3.0-source.zip`**](release/Runout-0.3.0-source.zip) | Complete source code, tests, and assets | `.zip` | 1.7 MB | `3d74b07230216d9d8fb2b8cc28d9b13e605ca3a6d12b77860ffa78ccbebd8b70` |
| [**`SHA256SUMS.txt`**](release/SHA256SUMS.txt) | Checksum verification signatures | `.txt` | 158 B | *See file for signatures* |

> 🌐 **GitHub Releases:** You can also download all release assets directly from the [GitHub Releases Tag v0.3.0](https://github.com/Abhiroop1122/runout/releases/tag/v0.3.0).

---

## 📱 Installation Guide (Sideloading)

1. **Download the APK:**
   - Tap [**`Runout-0.3.0.apk`**](release/Runout-0.3.0.apk) or grab it from the [Releases page](https://github.com/Abhiroop1122/runout/releases/tag/v0.3.0).
2. **Transfer to your Phone:**
   - Send via Quick Share, USB cable, Google Drive, or download directly in your mobile browser.
3. **Install the Package:**
   - Open the `.apk` file from your device Downloads folder.
   - If prompted by Android, grant permission to *"Install unknown apps"* for your file manager or browser.
4. **Permissions Model:**
   - Runout **never** asks for broad device storage access (`MANAGE_EXTERNAL_STORAGE`).
   - When launching for the first time, simply tap the empty sleeve to pick your music folder using Android's sandboxed Storage Access Framework (SAF).

---

## ✨ Highlights in This Release

### 1. The Glass Drive RG-1 Turntable
- Direct-drive plinth crafted from frosted liquid glass with real optical refraction shaders (Kyant Backdrop) on Android 13+.
- Platter rim with 4 rows of 180 rotating strobe dots.
- S-shaped chrome tonearm with counterweight, anti-skate dial, and exact law-of-cosines tracking geometry.
- Tactile pitch fader with ±8% adjustment, center zero detent LED, and double-tap reset.
- Synthesised mechanical audio: needle drops, surface crackle loops, and runout groove thumps.

### 2. Picture Discs
- Every album with cover artwork is pressed as a picture disc: cover art rotates smoothly under the grooved vinyl texture with a stationary lighting sheen.
- Dynamically rendered smooth groove bands reflecting actual track positions and durations.

### 3. Dot-Matrix Pixel Screen
- Mint-green dots on dark smoked glass driven by the Silkscreen typography engine.
- Real-time scrolling track metadata, playback timer, RPM speed indicator, and cue readouts.
- TalkBack screen reader accessibility integration.

### 4. Decentralized Identity via Nostr Relays
- Completely serverless account creation with local 256-bit private keys (`nsec1...`).
- Public profiles and shelf catalogs published as signed kind-33760 events across public Nostr relays (`relay.damus.io`, `nos.lol`, `relay.primal.net`).
- Prefix search powered by relay tag indexing with collision prevention via 4-character key suffixes (`@abhi·7F3A`).

### 5. Checksummed `.vinyl` Record Transfers
- Every audio file within a `.vinyl` bundle is indexed with its SHA-256 fingerprint.
- Hostile path traversals ("zip-slip") and corrupted audio files are detected and rejected on arrival.

---

## 🔒 Verification & Integrity

To verify the integrity of the release files after downloading:

### macOS / Linux:
```bash
shasum -a 256 -c release/SHA256SUMS.txt
```

### Windows (PowerShell):
```powershell
Get-FileHash release/Runout-0.3.0.apk -Algorithm SHA256
```
Expected output:
```text
4655cf36f067b3b5eebf0b3cb8b4cd6c1f41d464aef59d99cf155f3adb4eb5c2
```

---

## 🛠️ Build Information

- **Built with:** Android SDK 36 (Android 16), Kotlin 2.3.21, Jetpack Compose, Media3 1.9.4, OkHttp 4.12.0
- **Test Status:** 12 suites, 106 unit tests passing (Robolectric JVM Android 15 framework). 0 Android Lint errors.
- **Signing:** Debug-signed prototype configuration for direct sideloading.
