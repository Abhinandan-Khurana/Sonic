# Runout Release Deliverables

This directory contains standalone release builds and source distributions for **Runout**.

## Current Release: v0.3.0

| File | Description | Size | SHA-256 Checksum |
| :--- | :--- | :---: | :--- |
| [`Runout-0.3.0.apk`](Runout-0.3.0.apk) | Standalone debug-signed Android app | 26.9 MB | `4655cf36f067b3b5eebf0b3cb8b4cd6c1f41d464aef59d99cf155f3adb4eb5c2` |
| [`Runout-0.3.0-source.zip`](Runout-0.3.0-source.zip) | Complete source bundle, tests, and assets | 1.7 MB | `3d74b07230216d9d8fb2b8cc28d9b13e605ca3a6d12b77860ffa78ccbebd8b70` |
| [`SHA256SUMS.txt`](SHA256SUMS.txt) | Official cryptographic signatures | 158 B | *See file for signatures* |

---

## Verifying Integrity

Before installing, you can verify the integrity of the downloaded files:

### On macOS / Linux:
```bash
shasum -a 256 -c SHA256SUMS.txt
```

### On Windows (PowerShell):
```powershell
Get-FileHash Runout-0.3.0.apk -Algorithm SHA256
```

---

## Installation Guide (Sideloading)

1. **Transfer the APK to your Android device**:
   - Send via Quick Share, USB cable, Google Drive, Telegram saved messages, or download directly from GitHub Releases.
2. **Open the APK on your phone**:
   - Tap the `.apk` file from your Downloads or file manager.
3. **Allow Unknown Sources**:
   - If prompted, tap **Settings** and toggle **Allow from this source** for your browser or file manager.
4. **Complete Installation**:
   - Tap **Install**. It installs cleanly over earlier builds while preserving your folder selection, claimed drops, pressings, and account keys.

---

## Permissions & Privacy Model

Runout is built under strict privacy and serverless principles:

| Permission | Purpose | Why it's safe |
| :--- | :--- | :--- |
| **Storage Access Framework (SAF)** | Reading your chosen music folder | Runout **never** asks for broad device storage access (`MANAGE_EXTERNAL_STORAGE` or `READ_MEDIA_AUDIO`). You grant access to one specific folder only. |
| **Foreground Service (MediaSession)** | Background audio playback | Keeps your music spinning when the screen is off or when switching apps. |
| **Internet Access** | Nostr relays, Friday drops, cover art | Communicates directly with public Nostr relays, Cover Art Archive, and Internet Archive. No analytics, tracking, or proprietary telemetry. |
| **USB Host Access (Optional)** | Bit-perfect USB DAC playback | Allows Android 14+ to bypass the system mixer when a supported USB DAC is connected in Pure Mode. |
