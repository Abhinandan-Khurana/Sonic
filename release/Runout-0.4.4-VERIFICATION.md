# Runout 0.4.4 development verification

Verified on 2026-10-21 (Asia/Kolkata). Source branch: `slice-4-inked-home`.

## What this build contains

- Hand controls on the printed deck, black vinyl with the cover as the centre label, and a `record.vinyl` package written beside completed downloads.
- Archive downloads resume partial files. Cancel deletes unfinished staging. Corrupt partials are removed.

## Checks

- `./gradlew testDebugUnitTest lintDebug assembleDebug` — `BUILD SUCCESSFUL in 48s`.
- JVM/Compose XML: **222 tests, 221 passed, 1 historical opt-in skip, 0 failures**.
- Lint XML: **0 errors, 60 warnings**.
- APK: `app.runout.dev`, version 0.4.4, version code 8.
- No new public Soulseek login was run.
