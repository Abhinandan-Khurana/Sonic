# Sonic downloads

Releases are published on this repository's
[releases page](https://github.com/Abhiroop1122/runout/releases). The newest stable release is
marked *Latest*.

| Kind | Tag | Assets |
| :--- | :--- | :--- |
| **Release** | `v<version>`, starting with `v1.0.0` | `Sonic-<version>.apk`, `Sonic-<version>.apk.sha256`, `Sonic-<version>-VERIFICATION.md` |
| **Development pre-release** (occasional) | `v<version>-dev.<n>` | `Sonic-<version>-development.apk`, its `.sha256`, and a verification report |

The APK supports Android 9 or newer on ARM64, ARMv7, x86-64 and x86. A release installs as
**Sonic.** (`app.sonic`); a development build installs as **Sonic. Dev** (`app.sonic.dev`) beside
it.

Download the APK and its `.sha256` file into one directory, then verify:

```bash
shasum -a 256 -c Sonic-1.0.0.apk.sha256    # macOS
sha256sum -c Sonic-1.0.0.apk.sha256        # Linux
```

On Windows, run `Get-FileHash Sonic-1.0.0.apk -Algorithm SHA256` and compare the result with the
checksum file. Then open the APK on your Android device and allow installation from your browser
or file manager if asked.

Releases are signed with the maintainer's debug key, so a new release installs over the previous
one. Sonic does not replace an older Runout install (`app.runout` or `app.runout.dev`); the two
apps keep separate private data. Music in your chosen download folder stays there if either app
is uninstalled.

## What this folder holds

- [Release notes](RELEASE_NOTES.md) for the current or next release.
- The checksum and verification report for each published version. APKs are release assets and
  are never committed.
- `Runout-0.6.0-*` and `Runout-0.7.0-*`: the checksums and verification reports of development
  builds from before the app was renamed. They are kept as history and describe those builds, not
  Sonic.

See [RELEASE.md](../RELEASE.md) for how a release is built, checked and published.
