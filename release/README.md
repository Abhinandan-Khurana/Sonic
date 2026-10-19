# Runout downloads

## 0.4.1 development

[GitHub pre-release](https://github.com/Somchandra17/runout/releases/tag/v0.4.1-dev.1) · [Download APK](https://github.com/Somchandra17/runout/releases/download/v0.4.1-dev.1/Runout-0.4.1-development.apk) · [Release notes](RELEASE_NOTES.md) · [Verification report](Runout-0.4.1-VERIFICATION.md)

The universal APK supports Android 9 or newer and all four Android ABIs. It installs as **Runout Dev** (`app.runout.dev`) alongside the original app, with separate private state. New APKs are hosted as release assets to keep source history smaller.

Offline FLAC playback remains account-free. Soulseek provides FLAC search, downloads and private messages; Internet Archive Friday Drops remains available. Both download providers require Soulseek sign-in. Sharing and stay-online mode are opt-in.

Download the APK and its `.sha256` file into one directory, then verify:

```bash
sha256sum -c Runout-0.4.1-development.apk.sha256
```

On Windows, use `Get-FileHash Runout-0.4.1-development.apk -Algorithm SHA256` and compare with the checksum file. Open the APK on your Android device and allow installation from your browser/file manager if prompted.

This is a debug-signed development integration using experimental Soulseek 177/1. See the verification report for tested behavior and remaining production work. Updates to an existing app require the same signing certificate; keep your original installation when trying this build.

## Historical 0.3.0 assets

[APK](Runout-0.3.0.apk), [source bundle](Runout-0.3.0-source.zip), [checksums](SHA256SUMS.txt) and [original release notes](archive/v0.3.0.md) are retained as historical artifacts. They contain the prior Nostr and multi-format behavior and do not include the Soulseek integration.
