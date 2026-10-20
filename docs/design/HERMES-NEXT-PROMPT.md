# Prompt for Hermes (GPT-5.5): Runout Slice 0, "Stop the damage"

Paste everything below the line into Hermes.

---

You are continuing the Runout Android project (Kotlin/Compose, Media3 1.9.4, an MIT Rust/JNI Soulseek bridge). The repository is `https://github.com/Somchandra17/runout`, branch `main`. The local checkout per `Handsoff.md` is `/home/som/Documents/github/runout`. Run `git pull` first.

## Read first, in order

1. `Handsoff.md`: invariants, build and test commands, tool paths, credential rules.
2. `docs/design/RUNOUT-REDESIGN-DRAFT-v2.md`: §0, **§0.1** (the design decisions, so you know where the UI is heading), §2, §13, and **§14 Slice 0**.
3. `docs/design/RUNOUT-REDESIGN-REVIEW.md`: §0 (evidence labels) and **§16.1** (the Tier 0 register).
4. `docs/DEVELOPMENT.md` and `CONTRIBUTING.md`.

## Task

Implement **Slice 0 only**, as listed in v2 §14. Do not start Slice 1 or any visual redesign. The deck-only interaction and the "inked owner's manual" visuals in `docs/design/mockups/` belong to later slices, in `docs/design/HERMES-UI-PROMPT.md`.

UI touched by Slice 0 (confirmations, the cover-lookup setting row, notices) must use the existing components and stay minimal. Don't restyle anything. The findings, by area:

- **Record lists:** L-01, L-02
- **Crash:** L-16
- **ReplayGain safety:** L-07
- **`.vinyl` import:** L-08, L-17
- **Privacy and licence:** L-04, L-05, L-06, U-06
- **No retry war:** S-01, N-07
- **Native shutdown:** N-01
- **Friday Drops rollover:** S-08
- **Onboarding and drafts:** U-13, U-02, U-03, U-04

## Method: required for every finding

1. **Re-verify.** Re-read the cited lines. A ✓ in §16.1 means a reviewer confirmed the mechanism. Unmarked items are subagent-reported and may be wrong.
2. **Write a failing (red) test first**, and run it so you see it fail:
   - Kotlin: JVM/Robolectric tests under `app/src/test/java/app/runout/`, with the existing fixtures, `SoulseekTransport` fakes and Room/document-provider fixtures.
   - Rust: tests in `native/bridge`, run with `cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked`.
3. **Make the smallest fix** in the correct layer: Compose renders and requests; repositories hold policy; `DownloadStorage` handles platform storage; Rust handles protocol mechanics.
4. **Re-run the test** and see it pass.
5. **If a finding doesn't reproduce, don't fix it.** Record it as "not reproduced" with what you tried.

## Specific decisions already made: do not re-litigate

- **Online cover lookup is off by default.** Add a setting row under Collection & storage named "Find missing covers online", whose copy names MusicBrainz, the Cover Art Archive and the Internet Archive. Add a negative cache. Existing users get a one-time notice that it is now opt-in.
- **`.vinyl` export strips `source`, `from` and `notes`.** `Profile.u` must not be the Soulseek username. Import always sets `owned = false`.
- **ReplayGain:** finite values only, clamped to −24…+12 dB. Accept "dB", "DB" and "db" suffixes.
- **Never rewrite a record list on a failed read.** Keep unreadable records and show them as unavailable. Make a dated backup on every rewrite. A rescan whose root couldn't be listed keeps the previous shelf.
- **Backup:** exclude the folder-URI preferences (`library`, `soulseek` destination and shared folder) and the `soulseek-setup` onboarded flag in **both** `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`, **or** validate `persistedUriPermissions` at startup and re-prompt. Pick one approach and justify it in the commit message.
- **Poll loop:**
  - A handler exception must never become a session loss. Handle and log each event independently.
  - Contact re-watch uses `runCatching` per contact.
  - Validate incoming DM usernames before `addContact`.
  - On a transport error, check the native session-loss reason before classifying it. A displacement must stay DISPLACED and terminal: no automatic reconnect, no retry war.
- **Native `stop()`:** call `client.remove_download(user, path)` for every tracked transfer before `shutdown()`, so download threads exit and `.part` files are **kept** (only Cancelled discards them). Keep the change in `native/bridge`. If a vendor change is unavoidable, document it in `native/README.md` and `native/vendor/UPSTREAM`.
- **Friday Drops:** if the cached week index ≠ the current index, refresh and refuse. Count and record claims against a single index. The rules are unchanged: ten weekly albums, three successful claims, no general Archive search, and no claim used on failure.
- **Back on the first-run welcome screen** exits the app without calling `finishOnboarding()`.
- **The Pressing draft** clears only after a successful press, and asks before discarding a changed draft.
- **The message composer** clears only after the row is inserted. Validate UTF-8 bytes in the UI.
- **Remove record** asks for confirmation before deleting received or pressed audio.

## Invariants: must not break

- FLAC-only byte validation through `FlacPolicy`.
- Offline playback with no account.
- `hasAccount` ≠ `connected`.
- One connection owner, with generation checks.
- Messages are committed before they are acknowledged.
- Serialized publication; cancellation never deletes published music.
- SAF only, with no broad storage access.
- Account-scoped consent.
- Room schema migrations only; never destructive fallback. Slice 0 should need **no** schema change. If it does, stop and ask.
- No new permissions. In particular, no `BLUETOOTH_CONNECT`.
- Never log credentials, message bodies or query text.

## Verification: run these and report the real output

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain
cargo +1.91.0 test --manifest-path native/Cargo.toml --workspace --lib --locked
# Required because Slice 0 touches the JNI bridge:
./gradlew connectedDebugAndroidTest   # on the API 35 or 37 test AVD from Handsoff.md
```

- Lint must report zero errors.
- Do not claim a pass you did not see.
- If a check can't run, say exactly why.
- Do not use real Soulseek credentials. Do not sign in on two clients with one ID.

## Git and delivery

- Work on a new branch: `slice-0-stop-the-damage`.
- Make one focused commit per finding group, with subjects like `fix(library): never rewrite record lists on a failed read`.
- Do **not** add any Claude, GPT or AI attribution trailer to commits.
- Push the branch to `origin` (the user's fork). Do **not** open a PR, push to `upstream`, or merge to `main` without the user's explicit instruction.
- Do not change signing, releases, CI templates or the app version.

## Final report: write it, then stop

1. **Findings table:** ID → reproduced (yes/no) → test name → fix commit → status.
2. **Verification:** the exact commands and pass/fail counts.
3. **Deviations** from v2 §14 Slice 0, and why.
4. **Doc updates:** change v2 §14 and review §16.1 from "planned" to "fixed in `<sha>`" for each item you landed. Do not edit the other plan sections.
5. **Questions** for the user, only if a decision is genuinely theirs.
