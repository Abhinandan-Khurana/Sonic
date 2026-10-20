# Slice 0 verification report

Date: 2026-10-21, Asia/Kolkata.
Branch: `slice-0-stop-the-damage`.
Base: `532419b0d07bbc848a6c8bbea2e5727091d9da90`.
Application baseline: `64e12227fa42490f2f90a0b1f57e17abc017d352`.
No pull request was opened and `main` was not modified.

## Environment

```text
JAVA_HOME=/home/som/.local/share/runout-build/jdk/jdk-21.0.12.1+1
ANDROID_HOME=/home/som/Android/Sdk
ANDROID_NDK_HOME=/home/som/.local/share/runout-build/android-ndk-r27d
CARGO=/home/som/.cargo/bin/cargo
RUSTUP=/home/som/.cargo/bin/rustup
Android device: emulator-5556, AVD Runout_API_35, API 35
```

## Verification commands and actual results

| Command | Result |
|---|---|
| `./gradlew testDebugUnitTest lintDebug --console=plain` | `BUILD SUCCESSFUL in 25s`. Parsed JUnit XML: 215 tests, 214 passed, 0 failures, 0 errors, 1 skipped. Parsed lint XML: 0 errors, 60 warnings, 0 information. |
| `./gradlew assembleDebug --console=plain` | Reached `assembleDebug`; native task was `UP-TO-DATE`. The combined invocation then failed only because no emulator was attached. |
| `cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked` | Exit 0. `test result: ok. 5 passed` and `test result: ok. 329 passed`. |
| `ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest --console=plain` | `BUILD SUCCESSFUL in 11s`. Parsed device XML: 5 tests, 4 passed, 1 skipped, 0 failures. |

The skipped tests are the existing opt-in public-relay JVM test and `SoulseekLiveNetworkTest.publicNetworkSessionAndSearch`. No live Soulseek login was performed.

The first combined Gradle invocation failed with `No connected devices!` because `emulator-5556` had stopped. The emulator was restarted, and the connected task above is the successful rerun. A non-fatal `Failed to start Emulator console for 5556` appeared before the tests ran; it did not fail the task.

## Findings

| ID | Reproduced | Outcome | Verification |
|---|---|---|---|
| L-01 | Yes | Fixed: unavailable records and tracks are retained; every existing-file rewrite gets a dated backup. | RED 4 failed; GREEN 4 passed. |
| L-02 | Yes | Fixed: failed or partial scans roll back; a genuinely empty root still clears. | RED 2 failed, then 1 partial-regression failed; GREEN 4 focused tests passed. |
| L-16 | Yes | Fixed: minimum integer hash uses `Math.floorMod`. | RED 1 failed with `ArrayIndexOutOfBoundsException`; GREEN 1 passed. |
| L-07 | Yes | Fixed: non-finite gains rejected; finite gains clamped to −24…+12 dB; `dB`, `DB`, and `db` accepted. | RED 2 failed; GREEN focused suites passed. |
| L-04 | Yes | Fixed: lookup is off for new and existing installs, opt-in, with a seven-day negative cache and one-time notice. | RED 2 privacy failures and 2 cache-query failures; GREEN 6 focused tests passed. |
| U-06 | Yes | Fixed by excluding the SAF/setup preference files from both backup rules, so a restored cache cannot bypass setup. | RED 3 failed; GREEN 4 passed. |
| L-08 | Yes | Fixed: metadata reads are bounded and abandoned staging is swept without following symlinks. | RED reproduced across corrected suites; final GREEN 7 passed. |
| L-17 | Yes | Fixed: every imported audio entry requires a checksum. | RED 3 failed; GREEN 6 passed. |
| L-05 | Yes, with one citation correction | Fixed at the export boundary. `RunoutApp.kt:91` overwrote the display name, not JSON `u`; both the actual overwrite and exported legacy username are now prevented. | RED 3 failed; GREEN 3 passed. |
| L-06 | Yes | Fixed: import forces `owned = false`; licence-based sharing remains. | RED 4 failed; GREEN 4 passed. |
| S-01 | Yes | Fixed: one handler failure cannot kill polling, skip contacts, or become session loss. | RED exit 1; GREEN with connection and repository tests. |
| N-07 | Yes | Fixed: native terminal displacement is preserved before transport-failure classification. | RED before isolation; GREEN `transport failure checks native displacement before scheduling retry`. |
| N-01 | Yes | Fixed: tracked downloads are removed before shutdown, preserving `.part` files. No vendor change. | RED Cargo exit 101; GREEN full lib suite, including the paused worker test. |
| S-08 | Yes | Fixed: a stale Friday selection is refused before download, and its claim is charged only to the admitted week. | RED 2 failed; GREEN full Friday suite. |
| U-13 | Yes | Fixed: welcome Back exits without completing onboarding. | RED 1 failed; GREEN 2 passed. |
| U-02 | Yes | Fixed: Pressing drafts survive pending and failed saves and require discard confirmation. | RED reproduced; GREEN 5 passed. |
| U-03 | Yes | Fixed: the composer clears only after durable insertion and validates UTF-8 size. | RED 1 failed; GREEN 15 repository tests, then GREEN 5 UI tests. |
| U-04 | Yes | Fixed: removal requires confirmation; Cancel and Back retain audio. | RED reproduced; GREEN 3 passed. |

No assigned finding was left unfixed. No finding outside this Slice 0 list was changed.

## Product decisions recorded

- Cover lookup is off for both new and existing installs, with an explicit opt-in and a one-time existing-user notice.
- Backup exclusion, rather than startup-only grant revalidation, is the U-06 approach.
- Visual decisions—including plate versus walnut, ritual length, runout length, crackle mixing, and the signal-path name—remain planning decisions and were not implemented.

## Limits

- No physical ARM device, production signing, live Soulseek session, or public message/login test was run.
- The historical lint baseline remains 60 warnings; this integration introduced no lint error.
- Native ABI packaging was current (`buildSoulseekNative UP-TO-DATE`) during the successful debug assembly path. It was not rebuilt unnecessarily after the verified parent native build.
