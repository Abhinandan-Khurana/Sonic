# Contributing to Runout

Start with the [README](README.md), [build guide](docs/BUILDING.md), [architecture](docs/ARCHITECTURE.md), and [developer notes](docs/DEVELOPMENT.md). The current integration is Runout 0.5.1 development; older Nostr guides describe v0.3.

## Product boundaries

- Local FLAC playback works without an account or network connection. Soulseek sign-in gates downloads from both providers, online search, messages and sharing. Local metadata search works offline.
- Music is FLAC-only at every entry point. Validate bytes through `FlacPolicy`; an extension or peer-reported codec alone is insufficient. Artwork, `.vinyl` containers and synthesized turntable effects are separate assets.
- Use Android's folder picker and persisted URI grants. Never request broad storage access or assume a document URI is a filesystem path. Completed downloads belong in the user's selected folder.
- Sharing and staying online require explicit opt-in per account. A share-folder grant for one account is not consent for another. Preserve account identity during temporary disconnection and use the process lifecycle connection owner. Keep Soulseek credentials out of logs, backups, crash reports and Internet Archive requests.
- Preserve the turntable controls, audio quality reporting and Pure mode. Output fidelity depends on the device and audio route; avoid unconditional bit-perfect claims.
- Runout has no hosted application backend. Room is an on-device database; Soulseek uses its existing central server and peer network, and Archive uses HTTPS.
- Soulseek downloads do not confer ownership or a redistribution license. Keep the existing original-music/Creative Commons checks for `.vinyl` audio bundles.

## Making a change

1. Build with the pinned prerequisites in [BUILDING](docs/BUILDING.md). Debug builds install as **Runout Dev** alongside the original app.
2. Keep Compose rendering, repository decisions, document storage and native wire handling in their existing layers. Prefer a focused change over a new framework.
3. Add regression coverage for behavior changes, particularly account isolation, message acknowledgement, transfer interruption and storage failures. Documentation-only changes do not require new tests.
4. Run the checks relevant to the change:

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked
   # Required when changing JNI, native packaging, playback or Android credential behavior:
   ./gradlew connectedDebugAndroidTest
   ```

5. Review the generated reports. Lint must have zero errors; existing warnings are not evidence that a new warning is acceptable. Explain any skipped or unavailable check.
6. For navigation/login/search changes, run the [Argent flows](.argent/README.md), check keyboard and folder-picker transitions, and exercise large local/remote result sets on an emulator. Use incremental results and stable list identities; do not query storage from UI composition.
7. Update user-facing documentation for behavior changes. Update `native/README.md`, provenance and license notices when touching vendored code or dependencies. Commit exported Room schemas with database changes.

Use clear commit subjects such as `fix(downloads): preserve progress after folder permission loss`. Keep generated build directories, SDK paths, credentials and signing keys out of commits. New APKs belong in GitHub Releases, rather than Git history.

## Reports and review

For playback issues, include Android version, device, sample rate/bit depth, Pure/DSP setting and output route. For transfer issues, include provider, visible state, destination provider and whether background mode was enabled. Redact usernames where appropriate; never include passwords, private messages or account files.

Ordinary development and CI use fixtures and local Soulseek peers. Public-network sign-in, account creation and messages require a deliberate manual test with an account you control. Do not send unsolicited messages to test a feature.

Describe the problem, final behavior, validation and limitations in a proposed contribution. A release should identify the exact source commit and signing variant; follow [RELEASE.md](RELEASE.md).

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
