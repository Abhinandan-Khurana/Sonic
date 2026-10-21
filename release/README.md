# Runout downloads

Releases are published in this repository: the current release on the
[releases page](https://github.com/Abhiroop1122/runout/releases), and every development build
kept as a pre-release beside it.

| Kind | Tag | Assets |
| :--- | :--- | :--- |
| **Release** | `v0.5.1` | `Runout-0.5.1.apk`, `Runout-0.5.1.apk.sha256`, `Runout-0.5.1-VERIFICATION.md` |
| **Development pre-releases** | `v0.4.0-dev.1` … `v0.6.0-dev.1` | `Runout-<version>-development.apk`, its `.sha256`, and a verification report |

This folder keeps the checksum and verification report for the **newest build**, which may be a
development pre-release ahead of the current release (`0.6.0-dev.1` here, while `0.5.1` is the
release). It also keeps the [release notes](RELEASE_NOTES.md) for the next build. Every published
version's files stay attached to its own GitHub release. APK binaries are release assets, never
files in Git.

The universal APK supports Android 9 or newer and all four Android ABIs. It installs as **Runout
Dev** (`app.runout.dev`) alongside the signed `app.runout` build from
[0.3.0](https://github.com/Abhiroop1122/runout/releases/tag/v0.3.0), with separate private state.

Download the APK and its `.sha256` file into one directory, then verify:

```bash
sha256sum -c Runout-0.5.1.apk.sha256
```

On Windows, use `Get-FileHash Runout-0.5.1.apk -Algorithm SHA256` and compare with the checksum
file. Open the APK on your Android device and allow installation from your browser or file manager
if prompted.

These builds are debug-signed and use the experimental Soulseek client version 177/1. An update
installs over an existing Runout Dev only when it is signed with the same certificate; the
certificate changed between 0.4.x and 0.5.x.

See [RELEASE.md](../RELEASE.md) for how a release is built, verified and published.
