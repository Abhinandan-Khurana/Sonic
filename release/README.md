# Runout downloads

Development builds are published as [GitHub pre-releases](https://github.com/Abhiroop1122/runout/releases) in this repository. Each release has the universal APK, its `.sha256` file and a verification report attached. This folder keeps only the latest checksum and report, and the [release notes](RELEASE_NOTES.md) for the next build.

The universal APK supports Android 9 or newer and all four Android ABIs. It installs as **Runout Dev** (`app.runout.dev`) alongside the original app, with separate private state.

Download the APK and its `.sha256` file into one directory, then verify:

```bash
sha256sum -c Runout-<version>-development.apk.sha256
```

On Windows, use `Get-FileHash Runout-<version>-development.apk -Algorithm SHA256` and compare with the checksum file. Open the APK on your Android device and allow installation from your browser or file manager if prompted.

These are debug-signed development builds using experimental Soulseek 177/1. An update installs over an existing Runout Dev only when it is signed with the same certificate.

The original 0.3.0 APK, source bundle and checksums are on the [upstream v0.3.0 release](https://github.com/Abhiroop1122/runout/releases/tag/v0.3.0).
