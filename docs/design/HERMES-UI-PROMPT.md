# Prompts for Hermes (GPT-5.5): the listening room, slices 1–5

Use these **after Slice 0** (`HERMES-NEXT-PROMPT.md`) has landed and the user has accepted it. Paste one slice prompt at a time. Each prompt below the shared preamble is complete on its own: paste the preamble first, then the slice block.

---

## Shared preamble: paste this first every time

You are continuing the Runout Android project (Kotlin/Compose, Media3 1.9.4, an MIT Rust/JNI Soulseek bridge). The repository is `https://github.com/Somchandra17/runout`. Pull first, then branch from the latest accepted work.

**Read before coding:**

1. `Handsoff.md`: invariants, build and test commands, credential rules.
2. `docs/design/RUNOUT-REDESIGN-DRAFT-v2.md`:
   - **§0.1 first.** It is authoritative where it conflicts with later sections.
   - Then §2 (album-only policy), §3 (deck parts and gestures), §4 (state machine, Media3 attribution, system integration), §5 (B1–B3), §6 (Home), §9, §11 (accessibility), §13 (data), and the §14 slice you were given.
3. `docs/design/RUNOUT-REDESIGN-REVIEW.md` §16: the bug register with IDs.
4. **The mockups** in `docs/design/mockups/`, especially the `home-*` states, `home-landscape-playing-day`, `gesture-01…09`, `component-deck`, `component-coverflow` and `signal-path-*`. The editable source is `docs/design/pen/runout-listening-room.pen` (pen.dev); you may only be able to view the WebP exports.

**Product rules (non-negotiable):**

- **The record is the unit.** Album-only playback. Search only cues a band, with the needle up. No queue, shuffle or autoplay of the next side or record. Next and previous never leave the side.
- **The deck is the controller.** There are no play, pause, skip or flip buttons on Home. You play it by hand:
  1. Tap a cover and the disc pops up.
  2. Drag the disc to the spindle; it snaps on.
  3. Flip the motor switch.
  4. Drag the arm onto the record, or push the cue lever down.
  5. Drag the arm across the bands to skip.
  6. Raise the cue to pause, or hold the record to brake.
  7. Swipe the record up to turn it over.
  8. Drag it back to its sleeve to put it away.
- **Home never scrolls** in portrait or landscape.
- **Accessibility floors:**
  - Every gesture has a TalkBack custom action.
  - Settings has an optional "Show deck controls" strip, off by default.
  - Hit areas are at least 48 dp.
  - Every pixel readout has a real-text status twin.
  - Reduced motion changes visuals only, never audio policy.
- **Look:** "inked owner's manual", using the final tokens in v2 §0.1.
  - Calm, integrated surfaces and soft pills.
  - No punk or terminal styling, no boxed trays, no glass, no walnut.
- **Preserve these invariants:** FLAC-only byte validation, offline playback with no account, `hasAccount` ≠ `connected`, one connection owner, commit-before-ack, SAF only, Room migrations only, no new permissions (in particular no `BLUETOOTH_CONNECT`), MIT native boundary, and the Friday Drops rules.

**Method:**

- For bugs: re-verify the cited lines, write a failing test and see it fail, make the smallest fix in the right layer, then see the test pass.
- For features: write the state or ViewModel tests first, then the UI.
- Migrate test tags and Argent flows in the same slice that changes them (v2 §14 table).
- Never claim a check passed unless you ran it and saw it pass.

**Verify:**

- `./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain` must pass with zero lint errors.
- Run `./gradlew connectedDebugAndroidTest` when playback, JNI or credentials change.
- Run the relevant Argent flows on the API 35 or 37 AVD.

**Git:**

- One branch per slice, named `slice-N-<name>`.
- Focused commits with no AI attribution trailers.
- Push the branch to `origin` (the user's fork). No PR, no upstream push, no merge to `main` without the user's instruction.
- Don't change signing, releases or CI.

**Report, then stop:**

1. What changed, with commit SHAs.
2. Tests added and the exact verification output.
3. Findings reproduced or not, by ID.
4. Deviations from v2 and why.
5. Screenshots of the changed screens (Argent), compared against the mockups.
6. Questions that only the user can decide.

---

## Slice 1: Deck truth and hand controls (no new art)

Implement v2 §14 **Slice 1**. Keep the existing deck drawing for now; this slice is behaviour, gestures and semantics.

1. **Reconciled program state** in `DeckState`:
   - Include `SUSPENDED(CALL)` and `PAUSED(reason)`.
   - The platter, status and TalkBack state derive from it (R-01, R-11, UX-09, V-02).
   - Handle `onPlayerError` (P-06).
2. **Rate and volume hygiene:**
   - No needle-drop volume ramp.
   - Reset the rate at the end of a hold and on every ramp cancel (R-02, R-03, R-04, V-08).
   - Pure never overwrites the saved speed (P-03, V-13).
   - Ramps track `effectiveSpeed()` (P-04).
3. **`SurfaceVoices` interface:**
   - Tracked voices, gated by Pure **and** surface-off.
   - Hard stop with pause/flush/release, plus a noisy receiver.
   - Buffers built off the main thread (UX-06, V-11, P-17).
4. **Album-only edges:**
   - Next and previous are clamped to the side.
   - `cueTrack` replaces `playTrack`.
   - Discover offers **Place and cue A3** only.
   - Keep measured durations and track the media index.
   - One validation path for the Deck and the service (R-07, R-08, P-05, P-08).
5. **Hand controls on the existing deck canvas:**
   - Drag the arm to lower or skip, snapping to band gaps.
   - Tap or flick the cue lever.
   - Tap the motor switch.
   - Swipe the record up to turn it over at the side end.
   - Drag the record off to put it away.
   - Remove the hidden quick-tap flip (R-05).
   - Side end never shows or does "Play" (R-06).
   - Add a TalkBack custom action for each gesture.
   - Add the optional "Show deck controls" strip, behind a Settings toggle that is off by default.
6. **Placement:** off the main thread (`PLACING` → `CUED`), with a failure message and a replace confirmation (R-09, R-10, UX-15).
7. **One notice owner** (UX-12).
8. **Migrations in this slice:**
   - Test tags per v2 §14.
   - Rewrite `.argent/flows/offline-song-search.yaml`: Place and cue → assert the needle is up → Lower (cue action) → assert the status twin contains "Opening".
   - Update the `TurntableDeckTest` cases that encode the rejected policy.

---

## Slice 1b: Downloads recovery (can run in parallel with Slice 1)

Implement v2 §14 **Slice 1b**:

- **Data and recovery:**
  - Room v2 `pauseReason` with `Migration(1, 2)` and a migration test.
  - A recovery card with **Reconnect here**.
  - "Resume N interrupted" after a reconnect, excluding user pauses. Nothing auto-resumes.
  - Cancel confirms "Discard 53.3 MB downloaded so far?", then deletes the staging data.
  - The attention badge.
- **Transfer fixes:** S-02/N-06, S-03/N-05, S-04/N-02, S-13/N-11, S-15.
- **Archive fixes:** S-05, S-06, S-07, S-14, S-17.
- **Album identity:** S-09, S-12.
- **Native fixes:** N-03, N-04 (a documented vendor delta), N-09.
- **Style:** Downloads uses the calm language from v2 §0.1 (soft pills, plain text rows, no boxed cards). It has not been mocked up, so follow §9.3 copy.

---

## Slice 2: Coordinator phases, session wrapper and persistence

Implement v2 §14 **Slice 2**:

- **Phase dimensions:** motor, arm and phase.
- **Session wrapper:** `RunoutSessionPlayer : ForwardingSimpleBasePlayer` with:
  - the deck connection hint and `getControllerForCurrentRequest()` attribution;
  - Play hidden at the side end for external controllers;
  - swap-in-place in `rebuildPlayer`;
  - an `onConnect` allow-list (P-16);
  - `onTaskRemoved` → `pauseAllPlayersAndStopSelf()` outside LEAD_IN and RUNOUT (V-22).
- **Ritual phases:** LEAD_IN and RUNOUT timed in revolutions, using interim voices. Call `play()` after the revolutions; the bed runs until `isPlaying`.
- **Persistence:** visual-only restore from `deck_mount`, excluded from both backup XMLs (G-01, V-10).
- **Tests:** fake-clock statechart tests for v2 §4.2 and §4.3, plus a Robolectric session test with hinted and unhinted controllers (V-21).

---

## Slice 3: Sound and signal truth

Implement v2 §14 **Slice 3**:

- **Ritual audio, final form:** surface sound mixed into `DspAudioSink`, with silent program periods from a format-matched silence source (§5 B1).
- **The bed:** procedural, tied to RPM, with level presets and an audition.
- **DSP fixes:** in-place chain updates, gain switching at the stream boundary, a smoothed limiter attack, and a ReplayGain peak guard (P-01, P-02, P-11).
- **Signal path screen** (§9.5, mockups `signal-path-*`): every stage tagged with how we know it. It never claims bit-perfect or hi-res on Bluetooth or the speaker.
- **Bluetooth requirements** and the **Wired DAC guide** (§9.5).
- **Output truth:** P-09, P-10, plus the metadata and artwork fixes in P-15.
- **Listening session:** record observations only (headphones, speaker, Bluetooth, USB DAC where available).

---

## Slice 4: The inked deck, the crate and the opening ritual (match the mockups)

Implement v2 §14 **Slice 4** to match `component-deck`, `component-coverflow`, the `home-*` states, `home-landscape-playing-day` and `gesture-01…09`:

- **The deck in Compose `Canvas`:**
  - Cream enamel plate, ink linework, screws, printed callouts.
  - A cross-hatch engraving `RuntimeShader` on API 33+, with a flat fallback; `docs/design/pen/shaders/hatch.glsl` is the reference to port to AGSL.
  - Knurled platter rim.
  - Glossy black record with the cover as its label, band gaps drawn from `Side.trackBoundaries()`.
  - Outlined chrome tonearm with a dark headshell.
  - Cue lever, fine knob with strobe, motor switch, 33/45 on the plate.
  - A real-text twin under the green VT323-style readout.
- **The crate coverflow on Home:** sleeves fanned with depth, and covers shown first.
  - Tap → the disc pops up out of the sleeve.
  - Drag → spindle snap, with validation during the settle.
  - "On the platter" tag on the mounted record's sleeve.
  - "Flick to dig ⟷" paging.
- **Turnover animation:** lift → rotate on Y → the label art switches at 90° → reseat. **Put away** slides the record back into its sleeve.
- **Home layout:** no-scroll in portrait (412×915) and landscape (915×412). At large font, the crate collapses to one row before anything scrolls.
- **Polish:** night theme, reduced motion via `ANIMATOR_DURATION_SCALE`, and no per-frame allocation (§12; measure jank with JankStats or Macrobenchmark on a physical device).
- **Demo art:** "Night Drive" and the pixel covers are design demo art only. Never bundle them as records.

---

## Slice 5: Supporting screens

Implement v2 §14 **Slice 5** in the same calm language:

- Record detail (sleeve front and back with Bands, Place / Place and cue).
- The Crate tab grid plus "Dig".
- Discover relabelled, with the cue flow.
- The Settings entrance (five rows, including the "Show deck controls" toggle and "Find missing covers online", off).
- Lazy Messages.
- Overlay pane semantics, keyboard insets and the landscape shell (U-01, U-07, U-10).
- A TalkBack and large-text pass.
- A development APK under the existing signing only, with no release or PR unless the user instructs it.
