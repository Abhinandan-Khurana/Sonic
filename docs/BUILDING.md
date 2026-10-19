# Building & Testing Runout

This guide outlines how to build, test, and package **Runout** from source.

---

## 1. Prerequisites

Ensure your development environment meets the following requirements:
- **Java Development Kit (JDK):** Version 17 or higher (tested on OpenJDK 17 & 21).
- **Android SDK:**
  - `compileSdk`: 36 (Android 16)
  - `minSdk`: 28 (Android 9.0 Pie)
  - `targetSdk`: 36
- **Build Tools:** Android SDK Platform-Tools 36+
- **Gradle:** 8.14 (managed via the included `gradlew` wrapper)
- **Kotlin:** 2.3.21 with Compose Compiler Plugin

---

## 2. Quick Start Commands

### Clone Repository
```bash
git clone https://github.com/Abhiroop1122/runout.git
cd runout
```

### Build Debug APK
```bash
./gradlew assembleDebug
# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

### Build Prototype Release APK
```bash
./gradlew assembleRelease
# Output APK location:
# app/build/outputs/apk/release/app-release.apk
```

> **Note on Signing:** Prototype release builds are pre-configured to sign with the debug key so they can be directly sideloaded onto any test phone without keystore setup.

---

## 3. Running the Test Suite

Runout features a comprehensive test suite of **12 suites and 106 unit tests** executed via **Robolectric** (the actual Android 15 framework running on the host JVM):

```bash
./gradlew testDebugUnitTest
```

### Test Suite Map

| Test Suite | File Location | Key Verifications |
| :--- | :--- | :--- |
| **`FolderScanTest`** | `app/src/test/java/app/runout/FolderScanTest.kt` | Tests folder scanning via simulated Storage Access Framework content providers. |
| **`TagReaderTest`** | `app/src/test/java/app/runout/TagReaderTest.kt` | Tests native parsing of FLAC, MP3, and WAV headers, streaminfo, and embedded art. |
| **`RecordModelTest`**| `app/src/test/java/app/runout/RecordModelTest.kt` | Validates vinyl side division, double LP rules, and quality attribution. |
| **`DspTest`** | `app/src/test/java/app/runout/DspTest.kt` | Measures biquad filter frequency responses at 44.1, 96, and 192 kHz, headroom math, and Pure mode bit identity. |
| **`AudioOutputTest`**| `app/src/test/java/app/runout/AudioOutputTest.kt` | Tests 16-bit and 24-bit PCM passthrough through the float audio sink end-to-end. |
| **`TurntableDeckTest`**| `app/src/test/java/app/runout/TurntableDeckTest.kt` | Validates tonearm law-of-cosines geometry, RPM math, strobe frequencies, and pixel screen text rendering. |
| **`SharingTest`** | `app/src/test/java/app/runout/SharingTest.kt` | Tests `.vinyl` zip creation, SHA-256 integrity verification, and zip-slip path traversal rejection. |
| **`AccountsTest`** | `app/src/test/java/app/runout/AccountsTest.kt` | Runs BIP-340 test vectors, tests local WebSocket mock relays, and validates NIP-19 key encoding. |
| **`FriendsTest`** | `app/src/test/java/app/runout/FriendsTest.kt` | Validates friend shelf merging, mutual record detection, and card imports. |
| **`PressingPlantTest`**| `app/src/test/java/app/runout/PressingPlantTest.kt` | Validates sleeve pattern generators, track quotas, and mixed-quality attribution. |
| **`FridayDropsTest`**| `app/src/test/java/app/runout/FridayDropsTest.kt` | Tests deterministic week calculations against recorded Internet Archive API responses. |
| **`ListeningRoomUiTest`**| `app/src/test/java/app/runout/ListeningRoomUiTest.kt` | Tests Compose UI layout rendering in both portrait and landscape orientations. |

---

## 4. Testing Against Real Public Relays (`LiveRelayCheck`)

To verify live WebSocket connectivity against public Nostr relays without mocking:

```bash
./gradlew testDebugUnitTest -Prunout.live=true
```

This publishes a temporary, throwaway test event to `relay.damus.io`, `nos.lol`, and `relay.primal.net` (configured to auto-expire after 10 minutes) and queries it back by username prefix.

---

## 5. Visual Screenshot Testing (Roborazzi)

Runout uses **Roborazzi** to capture and verify exact UI renders without requiring a physical device:
```bash
./gradlew recordRoborazziDebug
# Screenshots are saved to:
# app/build/outputs/roborazzi/
```

To compare against baseline reference screenshots:
```bash
./gradlew verifyRoborazziDebug
```

---

## 6. Android Lint Checks

Run the static code analyzer:
```bash
./gradlew lintDebug
```
*Current baseline status: 0 errors.*
