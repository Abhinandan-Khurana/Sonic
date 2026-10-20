# Runout listening-room redesign, draft v2

Status: proposal for decision, not implemented. Date: 2026-10-20.
Baseline: HEAD `126290c`; the application code is unchanged since `64e1222`.
Keeps, for comparison: [draft v1](RUNOUT-REDESIGN-DRAFT-v1.md) and the [experience spec](RUNOUT-EXPERIENCE-SPEC.md).
Evidence and corrections: [review](RUNOUT-REDESIGN-REVIEW.md). Finding IDs (`R-xx`, `G-xx`, `UX-xx`, and the second-pass `P/L/S/N/U-xx`) refer to that document.

**Pass 2.** This revision adds three things:

- An adversarial Media3 1.9.4 review of this plan (the `V-xx` items, applied in place).
- A second-pass bug audit, which led to a new **Slice 0** before any listening-room work.
- The user's request for a **signal path** view, Bluetooth compatibility and an honest DAC guide (§9.5).

## 0. The product sentence, the failure test, and three floors

**Pick a record. Open its sleeve. Lower the needle. Stay for a side.**

**Failure test.** If a spinning disc on a generic player would do the same job, the design has failed. Every physical step in this draft must change real playback state:

| Step | State it changes |
|---|---|
| Opening the sleeve | Inspects a record without disturbing the mounted one |
| Placing the record | Validates the record and mounts it |
| Lowering the needle | Starts the motor and lead-in, holding the program at sample 0 |
| Lead-in crackle | Lasts as long as the groove, and longer if the player isn't ready |
| Runout | Is a deck phase with a revolution count, ending in auto-lift |
| Flip | Is the only way to reach the other side |
| Speed | Changes playback rate, except in Pure |

**Three floors.** The design must stay fully usable:

- **without dexterity**: every action is reachable from labelled ≥48 dp controls and TalkBack actions;
- **without sound effects**: every phase has a text state;
- **without animation**: reduced motion swaps choreography for crossfades and never changes the audio policy.

---

## 1. What changed from v1 and the spec, and why

| Topic | v1 / spec | v2 | Why |
|---|---|---|---|
| Album policy | Undecided ("album-first", "decide before implementation") | **Enforced album-only playback, album-first discovery** (§2.1) | The user's vision is "only play albums". Today the source still autoplays from search (R-08) and crosses sides on Next (R-07). |
| Deck model | A direct-drive DJ deck restyled in walnut and enamel | A **1950s transcription-deck mechanism** on a flat printed plate: motor switch, cue lever, 33/45 selector, fine-speed vernier with strobe. The DJ elements go: S-arm, ±8% fader, prev/next keys, Start/Stop key, power knob. (§3, §10) | Realism belongs on the moving parts and their timing, not on wood grain. Each control maps to one command. |
| Controls | Motor + Cue + a "Start listening" macro | Two controls. **Lowering the cue with the motor off starts the motor first.** | Removes the third, synonymous button. |
| First build slice | "Restore trust" (Downloads recovery and copy) | **Deck truth** before any art, with Downloads recovery as a parallel slice; pass 2 puts **Slice 0** in front of both (§14) | Source-confirmed deck defects (R-01–R-06) would make any new visuals lie. |
| Lead-in | Timing targets of 2–4 s | Measured in **groove revolutions** at the effective RPM. Holds the program at sample 0 and **extends until the player is ready**. Volume ramp removed. | It becomes physically consistent, and stops attenuating the first transient (R-04). |
| Runout | "Finite, 2–4 s" | **n locked-groove revolutions** at the effective RPM, then lift, arm return and motor stop | It follows 33/45 and fine speed (UX-07). |
| Flip | "Explicit Flip to Side B" | Flip is a **visible tray action and a physical turnover**. The hidden tap-to-flip is removed. | R-05, R-06. |
| Search | "View record" as the primary action | **Open record at A3** / **Place and cue A3**, with the needle up. "Play song" and "Play album" are removed. | Search may only locate a groove. |
| Settings | v1: six sections plus an inventory. Spec: four groups plus About. | The spec's shape, behind **one header entrance**. The Home "Sound" and "Crackle" pills are removed. | One small entrance. |
| ASCII / pixel | Accents | **Instruments and labels only**. Every pixel readout has a real-text twin. | The brief, plus accessibility. |
| Downloads | Recovery copy, grouping, Active/Attention/Completed | Same, plus **Resume N interrupted** (excludes user-paused rows by `error == null`), **truthful Cancel** that discards private partial bytes, and an attention badge | Reported screenshot, R-12, G-06. |
| Black vinyl default | Candidate default | Not silent. A Display choice: "by quality / always black". | Avoids restyling existing records (`Models.kt:107–118`). |
| v1 UX-03 | "Archive Resume calls the wrong method" | Corrected: the routing exists (`MainActivity.kt:360–370`). Only the label changes. | Recheck. |
| Evidence | Screenshot items labelled Observed | Labelled **Reported** | The screenshot wasn't available to the reviewer. |
| Build order (pass 2) | Deck truth first | **Slice 0, "Stop the damage"**, first: record-list data loss, a crash, the ReplayGain clamp, privacy and licence leaks, the reconnect loop, native download threads, the Drops rollover | Silent loss of pressings and a retry war outrank listening polish (review §16.1). |
| Command attribution (pass 2) | "Expected playWhenReady" | A deck **connection hint** plus `getControllerForCurrentRequest()` in a service-side `ForwardingSimpleBasePlayer` (§4.3) | Notification and deck commands both arrive as `USER_REQUEST` (V-01). |
| Transient focus (pass 2) | Treated like a pause, so the arm rises | **Suppression**: needle down, Media3 auto-resumes | Lifting would cancel Media3's resume after a call (V-02). |
| Ritual audio (pass 2) | Separate `AudioTrack` voices | **Target**: surface sound mixed into `DspAudioSink`, with lead-in and runout as silent program periods inside the player. **Interim**: tracked voices. | Focus, foreground service, wake lock, noisy events and timing then come from Media3 (V-04, V-05). |
| External Play (pass 2) | "Short cue ≤0.8 s" | **Immediate.** At side start the lead-in is skipped, and the app says so. | Background foreground-service start rules, AVRCP and headset double-press (V-09). |
| Pure (pass 2) | "Rate 1.0" | Rate 1.0 **without overwriting** the saved 33/45 and fine speed | Today `onPureChanged` overwrites them (V-13). |
| Signal path (pass 2) | — | **Record → Runout → Android → Output**, each stage tagged with *how we know*, plus Bluetooth handling and a DAC guide that states Android's limits (§9.5) | User request; honesty constraints in review §17. |
| Downloads reason (pass 2) | Interim `error == null` rule | **Room v2 `pauseReason` moves into slice 1b** | The interim rule misclassifies some rows (V-17). |

Unchanged from v1 and the spec: Kotlin/Compose, Media3, the MIT Rust/JNI Soulseek boundary, FLAC byte validation, account-free offline listening, retained files, authenticated download gates, Friday Drops rules (Archive stays there), the record/side model (`SideSplitter`), and four destinations plus global Downloads.

---

## 2. Decisions

### 2.1 Album policy

**Playback is album-only:**

- Audio plays only from a **mounted record side**.
- Bands stay addressable *within* the mounted side: cue a band, next or previous band, or drag the arm. This is required for accessibility, and it is how a real record is used.
- Next and previous **stop at the side edges**. Reaching another side takes a **Flip**. Reaching another record takes a **placement**.
- There is no queue, shuffle, autoplay of the next side or album, or radio.
- The final side ends with **Play this side again** and **Put away**.

**Discovery is album-first.** Song results are ways to find a record. A local song hit opens its record at that band. A remote song hit opens the peer's folder preview.

**System media controls obey the same rules.** Play and pause map to lowering and raising the cue. Next and previous are clamped to the side. The notification never flips or changes records.

### 2.2 Visual direction

**Direction D, "Printed hi-fi"** (§10). It is recommended over v1's B (walnut/enamel Console). B stays as a fallback if the user explicitly wants furniture realism. Direction C (pixel record shop) stays out.

### 2.3 Settings

One labelled **Settings** entrance in the header. Its first page has five rows (§9.6). Nothing about configuration lives on the Home deck except the **Pure** lamp, which is state, not a setting.

---

## 3. The mechanism: what is on the deck, and what each part does

Model: a 1950s-style transcription turntable. It has a heavy platter that takes a moment to reach speed, a damped cue lever, a straight or gently offset arm with an arm rest, a speed selector, and a fine-speed vernier read against a strobe. The deck is not modelled on any real brand or product, and carries no commercial branding.

| Plate part | Gesture (optional) | Command | State effect | Equivalent in the ≥48 dp control tray / TalkBack |
|---|---|---|---|---|
| **Motor switch** (ON/OFF) | Tap | `motor(on)` | Spin-up or brake. In vinyl mode the brake slows playback rate while the needle is down; in Pure it pauses. | "Motor on/off" toggle, with state "Running · 33" or "Stopped" |
| **Speed selector** 33 / 45 | Tap a position | `setSpeed(RPM33 \| RPM45)` | Rate change through the motor ramp. Locked to 33 in Pure. | Two-option segmented control |
| **Fine speed vernier** with strobe window | Rotate (drag); double-tap returns to zero | `setFine(-8..+8 %)`, 0.1 steps, detent at 0 | Rate change. The strobe "locks" at 0. Disabled in Pure, with the reason shown. | Slider with `setProgress`, plus a "Zero" action |
| **Cue lever** (UP/DOWN) | Tap or flick | `lowerCue()` / `raiseCue()` | Descent or rise. **Lowering with the motor off starts the motor first.** | The primary tray button: "Lower needle" / "Lift needle" |
| **Tonearm** | Drag (optional) | `parkArm(fraction)`, snapping to bands; release over the record with the cue down drops there | With the cue up, it only moves the arm. With the cue down, a drag means lift, move and lower. | "Bands" sheet with **Cue A1…A5**; arm actions "Cue next band", "Cue previous band", "Lower here" |
| **Record** | Long-press means finger brake (optional, vinyl mode only, never in Pure) | `hold()` / `release()` | **Momentary**: the program slows to a pause, then ramps back on release. At the end of the hold the rate is reset to the effective speed, so an external Play is never 0.25×. | **Motor off, then Motor on** (two steps, latched rather than momentary) |
| (hand action, not on the plate) | — | `flip()` | Lift if needed, turn the record over, cue the next side with the needle up | Tray: "Flip to Side B" when awaiting a flip; otherwise inside the record menu |
| (hand action) | — | `putAway()` | Lift and stop, then the record returns to its sleeve | Tray: "Put away" |

Removed from the plate: Start/Stop key, prev/next keys, ±8% DJ fader, decorative power knob and lamp, anti-skate dial decoration, 45-adapter well.

The **Pure lamp** is a small printed indicator, "PURE", lit when Pure is on. Tapping it opens Settings › Listening.

---

## 4. The listening state machine

### 4.1 Dimensions

These are owned by the `Deck` coordinator in `AppGraph`. The service runs in-process. Rendering only reads them.

| Dimension | Values |
|---|---|
| Mount | `EMPTY`, `PLACING(album, gen)`, `MOUNTED(album, sides, sideIndex, gen)`, `REMOVING` |
| Motor | `OFF`, `SPINNING_UP`, `AT_SPEED`, `BRAKING`; target factor = speed × (1 + fine/100), or 1.0 in Pure |
| Arm | `REST`, `PARKED(fraction)`, `DESCENDING`, `IN_GROOVE(zone)`, `RISING`, `RETURNING`; zone is `LEAD_IN`, `PROGRAM(band)` or `RUNOUT` |
| Program (reconciled with Media3) | `IDLE`, `PREPARING`, `READY`, `PLAYING`, `SUSPENDED(CALL)`, `PAUSED(reason)`, `ENDED`, `FAILED(reason)`. Reason is `USER`, `MOTOR`, `EXTERNAL`, `FOCUS` (permanent loss) or `NOISY`. `SUSPENDED` comes from `onPlaybackSuppressionReasonChanged` (transient focus loss); `playWhenReady` stays true and Media3 resumes on its own. |
| Phase (derived) | `CUED`, `LEAD_IN(revolutions left)`, `PROGRAM`, `NEEDLE_UP`, `RUNOUT(k of n)`, `AWAITING_FLIP`, `FINISHED` |
| Surface voices | Tracked handles with level and envelope. Every voice is gated by Pure, the surface preference, focus and noisy events. |

Rules:

- **Visuals derive only from these dimensions.** The platter spins when the motor is not `OFF`, not because `needleDown` is true. The status sentence never says "Playing" unless the program is `PLAYING`.
- **One monotonic coordinator clock and a generation token.** Every delayed step checks the generation, so a stale lead-in, runout or ramp can never act on a replaced record.
- **Player volume stays at 1.0.** There is no volume ramp anywhere (fixes R-03 and R-04). Speed effects outside Pure are the only rate changes.

### 4.2 Transitions

```text
EMPTY ──Place(album)──▶ PLACING ──valid (IO, gen ok)──▶ CUED(side A, arm REST, motor OFF)
                          └──invalid──▶ EMPTY + "Couldn't read this record: <repair>" (record back in sleeve)

CUED/NEEDLE_UP ──lowerCue──▶ [motor OFF → SPINNING_UP] ──▶ DESCENDING
   at side start:     ──▶ LEAD_IN(n revs, program held at 0)
                          ──(revs done)──▶ play(); the bed continues until onIsPlayingChanged(true) for this gen
                          (status "Lead-in · waiting for the record" until isPlaying)       // V-06
   mid-side (resume): ──▶ PROGRAM after contact (no lead-in; one contact sound outside Pure)

PROGRAM ──raiseCue──▶ NEEDLE_UP(paused at t)            ──lowerCue──▶ DESCENDING → PROGRAM
PROGRAM ──motor OFF──▶ BRAKING → PAUSED(MOTOR)           ──motor ON──▶ SPINNING_UP → PROGRAM
PROGRAM ──external pause / permanent focus loss / noisy──▶ PAUSED(EXTERNAL|FOCUS|NOISY), arm RISING
PROGRAM ──transient focus loss──▶ SUSPENDED(CALL): needle stays down, motor runs, voices stop,
         no deck command; Media3 auto-resumes ──▶ PROGRAM                                   // V-02
NEEDLE_UP/PAUSED ──external play──▶ IMMEDIATE: rate = effective factor, needle lowers at t,
         one contact sound outside Pure; from PAUSED(MOTOR) the motor is on at once        // V-09
CUED (side start) ──external play──▶ PROGRAM with no lead-in; status "Started from headset · lead-in skipped"
RUNOUT/AWAITING_FLIP/FINISHED ──external play──▶ unavailable (Play hidden for non-deck controllers) // V-03
PROGRAM ──STATE_ENDED (last band)──▶ RUNOUT(n revs) → RISING → RETURNING → motor OFF
         └─▶ AWAITING_FLIP (more sides) | FINISHED (last side)

AWAITING_FLIP ──flip──▶ (turnover) → CUED(next side)          // never auto-lowers
FINISHED ──playSideAgain──▶ CUED(same side) | ──putAway──▶ REMOVING → EMPTY

MOUNTED(any, needle down) ──Place(other)──▶ confirm "Replace Night Drive?" ─yes─▶ RISING → REMOVING → PLACING(other)
Pure ON (any) ──▶ stop all voices within one buffer; rate 1.0; LEAD_IN/RUNOUT keep their timing, silently
Process death ──▶ restore CUED(album, side, position) after off-main URI checks; never sound
```

Next and previous band: inside `PROGRAM`, the cue rises, the arm parks at the band boundary, and the cue lowers with a contact sound. There is no lead-in. **At the side edges the action is disabled**, with the label "Last band on Side A".

### 4.3 Interruption contract

This replaces spec §6.

| Event | Program | Voices | Visual | Resume |
|---|---|---|---|---|
| Lift cue | Pause at t | Bed fades **out** in 120 ms; lift sound outside Pure, if surface is on | Arm rises, motor keeps running | Lower → contact → program at t |
| Motor off | Rate brake (vinyl) or pause (Pure) | Bed follows rate, then stops | Platter decelerates | Motor on → spin-up → program |
| Headphone unplug | Pause immediately (Media3 noisy) | **Stop all immediately**, including tails | Arm rises, status "Paused: headphones disconnected" | User action only |
| Transient focus loss | `SUSPENDED(CALL)`; `playWhenReady` stays true | Stop | Needle stays down, motor running, "Paused for a call" | Media3 resumes automatically. The deck issues **no** command (a lift would cancel the auto-resume). |
| Permanent focus loss | `PAUSED(FOCUS)` | Stop | Arm rises | User action |
| Notification or headset pause | `PAUSED(EXTERNAL)` | Same as a lift | Same as a lift | **One** press of Lower or Play |
| Notification or headset play | Immediate lower at t (no lead-in, no cue delay) | One contact sound outside Pure | Arm lowers | — |
| Notification or headset play in RUNOUT, AWAITING_FLIP or FINISHED | Unavailable to external controllers | — | — | Flip or Play-side-again in the app |
| Replace or put away | Stop old gen after confirmation | Cancel all old-gen voices and timers | Record returns to its sleeve | New record `CUED` |
| Pure on | Rate 1.0, position kept | Stop all, including pending | Pure lamp lit; speed controls disabled with the reason | No stale ramp may reinstate rate or noise |
| Hi-res toggle (player rebuild) | `PlaybackService.rebuildPlayer` carries the item and position | Unchanged | Unchanged | Coordinator re-reads state after the swap |
| Decoder or storage error | `FAILED(reason)` | Cancel | Arm lifts, record stays mounted | "Repair folder access" / "Try again" |
| Process death | Nothing plays | No live handles restored | `CUED` at the saved position | User lowers the needle |

### MediaSession command attribution (target: slice 2)

All of the following was verified against Media3 1.9.4 (V-01, V-03, V-07, V-12).

- **Why a flag can't work.** A pause from the notification and a pause from the deck both reach the Deck's controller as `PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST`. Since 1.2.0, notification and Bluetooth buttons go through the media-notification controller, which carries **Runout's own package name**. Neither the reason nor the package name can tell them apart.
- **Hint the Deck's controller.** It is built with `setConnectionHints(bundleOf("runout.deck" to true))`.
- **Wrap the player in the service.** `PlaybackService` wraps ExoPlayer in `RunoutSessionPlayer : ForwardingSimpleBasePlayer`.
- **Classify requests in the wrapper.** It reads `session.controllerForCurrentRequest`. Requests from controllers **without** the hint are `EXTERNAL`. They are forwarded in-process to `Deck.onExternal(PLAY | PAUSE | SEEK_TO_ITEM)`, and the Deck then issues its own hinted command.
  - `handleSetPlayWhenReady` returns a future the Deck completes when its play lands, so the masked UI shows the change at once, with no flicker.
  - Hinted commands pass straight through, so there is no loop.
- **Classify changes from inside ExoPlayer** by reason (`AUDIO_FOCUS_LOSS`, `AUDIO_BECOMING_NOISY`) or by suppression (`TRANSIENT_AUDIO_FOCUS_LOSS`).
- **Hide Play at the side end.** In RUNOUT, AWAITING_FLIP and FINISHED, the wrapper removes `COMMAND_PLAY_PAUSE` from the available commands for non-deck controllers. Otherwise Media3's play-at-`ENDED` handling seeks to the default position and replays the last band (V-03).
- **Next and previous need no change.** `COMMAND_SEEK_TO_NEXT` is already hidden at the last band, because the playlist holds one side. If internal items are ever appended (§5 B1 target), the wrapper must keep hiding Next. Notification Next within a side is an EXTERNAL seek: the arm jumps, with no cue choreography. Previous at A1 restarts A1.
- **Swap players inside the wrapper.** `rebuildPlayer` calls `wrapper.swap(next)`, never `session.player = next`, which would drop the wrapper. The swap emits `DISCONTINUITY_REASON_INTERNAL`, so the Deck ignores ENDED and READY transitions within one generation tick of a swap; otherwise a swap during the runout would fire a second runout.
- **Rate.** The rate is normalised only for EXTERNAL plays. Re-applying it on every `play()` would break the deck's own platter release (0.25× → ramp) and the motor spin-up.
- **Slice 1 interim.** R-02 is fixed in `Deck` itself (§14). If an interim attribution flag is needed before slice 2, set it synchronously *before* each controller call, because the deck's own commands echo back synchronously through masking.

### 4.4 System integration (pass 2, V-04, V-10, V-22)

- **Media3 manages focus, the wake lock and the foreground service only while `playWhenReady` is true.** Focus is requested on play and kept while paused. The wake lock is held in READY or BUFFERING with `playWhenReady`. The foreground service starts after a period of user engagement and then holds for 10 minutes after a pause or end.
- **So a lead-in run on separate voices before the first `play()` holds no audio focus.** Another app's music plays under the bed, then stops when the program starts. The runout after a played side *is* covered by the 10-minute hold.
- **This is why the target puts the ritual inside the player (§5 B1).** With the interim voices, the first lead-in runs app-foreground only and has a hard cap (the "waiting for the record" extension stops at 10 s with an error state). An external Play never waits for a lead-in.
- **`onTaskRemoved`.** Treat LEAD_IN and RUNOUT as ongoing playback. Otherwise call `pauseAllPlayersAndStopSelf()` and cancel the Deck generation, so a timer cannot call `play()` on a stopped service.
- **Restore after process death is visual only (`CUED`).** The controller connects and prepares on Place or Lower, because the deck connects lazily so that a browsed app holds no session. Playback resumption (a `MediaButtonReceiver` and `onPlaybackResumption`) is out of scope.
- **Lock down the exported session.** In `onConnect`, accept the own package, system UI and known media controllers. Restrict `setMediaItems` from non-deck controllers so another app cannot replace the side (P-16).

---

## 5. Three bold interactions

Each one changes playback state, is feasible with Kotlin/Compose and Media3 1.9.4, and has a dexterity-free, sound-free and motion-free equivalent.

### B1. Audible edges: lead-in and runout as deck phases

**What it does.** Lowering at the side start runs a real lead-in: the stylus lands in the outer lead-in band, the arm visibly crosses it, and a surface bed plays for *n* revolutions at the effective RPM. The program waits at sample 0. When the revolutions are done, `play()` is called and music starts at **full volume** from sample 0. The bed keeps turning until the player actually reports `isPlaying` ("Lead-in · waiting for the record"), so a slow start is heard as groove, not silence (V-06). Readiness becomes something you can see and hear, not a spinner.

At the end, `STATE_ENDED` enters `RUNOUT`: *n* locked-groove revolutions (a soft tick plus thump once per revolution, its period derived from RPM), then the cue lifts, the arm returns, the motor stops, and the deck shows **Flip to Side B**.

| Speed | Seconds per revolution | Short | Full |
|---|---|---|---|
| 33⅓ | 1.80 s | lead-in 1 rev ≈ 1.8 s; runout 2 rev ≈ 3.6 s | lead-in 2 rev ≈ 3.6 s; runout 4 rev ≈ 7.2 s |
| 45 | 1.33 s | lead-in 1 rev ≈ 1.3 s; runout 2 rev ≈ 2.7 s | lead-in 2 rev ≈ 2.7 s; runout 4 rev ≈ 5.3 s |

These numbers assume fine speed at 0, which scales them. Ritual **Off** means zero revolutions, so the phases pass immediately and the text still says what happened. All timings are proposals to tune in a listening session.

**State it changes.** Program start time, phase, arm zone, motor stop, the side-end action.

**Feasibility.** There are two stages (V-04, V-05, V-06, V-11).

- **Interim (slice 2).**
  - `Deck` runs a phase scheduler on the coordinator clock.
  - A new `SurfaceVoices` replaces fire-and-forget `playOnce`: tracked streaming `AudioTrack` voices, a procedural bed with pop clustering tied to RPM, and a cancellable envelope.
  - Hard stop is `pause()` + `flush()` + `release()`, not `stop()`, which plays out what is already buffered.
  - Voices get their own `ACTION_AUDIO_BECOMING_NOISY` receiver, so a tail never leaks to the speaker.
  - When the lead-in revolutions are done, call `play()`. The bed continues until `onIsPlayingChanged(true)` for the current generation. Don't gate `play()` on a READY value seen through the controller; it can be stale while a seek is masked.
  - Limit: the first lead-in holds no audio focus (§4.4).
- **Target (slice 3).**
  - Mix the bed, contact and runout tick into `DspAudioSink.process()`, after the `DspChain` and before the delegate. Skip it entirely when `pure`; the existing bypass keeps Pure untouched.
  - Lead-in and runout become **silent program periods inside the player**: internal `runout:` MediaItems resolved by a custom `MediaSource.Factory` in `PlayerFactory`, and hidden from controllers by `RunoutSessionPlayer`.
  - With `playWhenReady` true through the ritual, Media3 owns focus, the foreground service, the wake lock, noisy handling and routing.
  - Timing becomes sample-accurate, and revolutions scale with 33/45 and fine speed for free, because silence plays in media time × rate.
  - `STATE_ENDED` then means "runout done".
- **Costs of the target, stated honestly.**
  - Media3's `SilenceMediaSource` is fixed at 44.1 kHz / 16-bit / stereo. A **format-matched silence source** (about 150 LOC) is required; otherwise a 24/96 side reconfigures the sink at both edges, causing a gap or click and flipping the USB DAC rate.
  - The index and position mapping (`Side.positionOf`, band codes, Next availability) must ignore the internal items.
  - `onSetMediaItems` accepts `runout:` only from the service itself.
  - A band boundary that isn't ready means BUFFERING, which means silence and no bed.
  - Separate `AudioTrack` voices remain only for lift and motor one-shots while the program is paused.
- `DeckGeometry` reserves outer and inner bands as the lead-in and runout zones; `alphaForFraction` maps the program to the middle band.

**Without sound:** status text plus a revolution counter in the instrument window ("RUNOUT 2/4"). **Without motion:** the arm jumps between zones and the counter still advances. **In Pure:** silent phases with the same timing.

**Tests.** Fake clock, fake player and fake voices:

- the program `play()` happens only after *n* revolutions, and the bed stops only after `isPlaying` for this generation;
- the first program call is at volume 1.0;
- a runout at 45 rpm takes the scaled duration;
- Pure mid-runout stops the voice within one buffer;
- replacing the record mid-lead-in leaves no stale `play()`.

### B2. The band map under the arm

**What it does.** The grooves the deck already draws from `Side.trackBoundaries()` become the side's navigation map. With the cue **up**, dragging the arm across the record **snaps to band gaps**, with a haptic tick at each gap. A paper tag follows the headshell: "A3 · Paper Moon · 4:12". Lowering the cue drops there.

The sleeve back, the same object as the record detail, lists the bands as **Cue A3** rows. Search lands here: "Open record at A3" opens the sleeve with A3 marked, and "Place and cue A3" mounts the record with the arm parked over A3 and the needle up.

**State it changes.** The arm's parked fraction and the cue target. This is the only within-record navigation, and it never leaves the side.

**Feasibility.** `DeckGeometry.alphaForPointer` and `fractionForAlpha` exist. Add a snap within ±1.5° of a boundary and `HapticFeedbackConstants.CLOCK_TICK`. The "Bands" tray sheet and the arm's custom semantics actions call the same `parkArm(band)`.

**Without dexterity:** the Bands sheet and TalkBack actions "Cue next band", "Cue previous band", "Lower here". **Without sound:** nothing is lost. **Without motion:** the arm moves instantly.

**Tests:**

- `parkArm(band k)` sets the fraction to the boundary;
- next or previous at the side edges is a no-op with a message;
- a search cue leaves `needleDown = false`;
- no code path calls `play()` from search.

### B3. One record, two hands: sleeve, spindle and turnover

**What it does.**

1. **Open sleeve.** From Crate, Discover or a finished download, the front sleeve advances. **Back** shows bands and credits. **Take out** slides the inner paper sleeve, then exposes the disc edge.
2. **Place.** Drag the disc toward the spindle, or tap **Place on platter**. Near the spindle, a contact shadow tightens and an alignment ring appears. Release snaps the **centre** to the spindle.
3. **Commit.** During the 400–600 ms placing animation the record is validated **off the main thread**. Only a successful validation commits `MOUNTED`. On failure the disc returns to the sleeve with "Couldn't read 2 of 9 files · Repair folder access".
4. **Replace.** If another record's needle is down, a confirmation appears first. The old record lifts and slides back into **its** sleeve before the new one seats.
5. **Flip.** It is the same physical object. The record lifts (shadow grows, scale +3%), turns over on its Y axis, the label art changes to the other side's printed label at 90° ("SIDE B" plus a band list), and it reseats. The new side is `CUED`. **The needle stays up.**

**State it changes.** Mount, generation, side, and the validation outcome.

**Feasibility.**

- `ListeningRoom.kt`: the pulled-disc path already exists. Add a `PLACING` state, an `IO` validation job with a generation check, and a sleeve-return animation.
- Flip: `graphicsLayer { rotationY }` on the existing `drawRecord` canvas.
- `Deck.load` becomes asynchronous. Today it is synchronous on the main thread (UX-15).

**Without dexterity:** "Open record → Place on platter → Lower needle" are all labelled buttons. Flip and Put away are tray buttons. **Without motion:** crossfades of 150 ms or less with identical state steps. **Without sound:** nothing is lost.

**Tests:**

- placement commits only after validation;
- a failed validation leaves the previous record mounted and shows a message;
- replace requires confirmation when the needle is down;
- flip never sets `needleDown`;
- rotation or navigation during placement neither mounts nor plays.

---

## 6. Home composition

Portrait, compact width (illustrative, not measured):

```text
RUNOUT                       [↓ 1 needs you]  [Settings]
┌────────────────────────────────────────────────────┐
│  printed plate · enamel token · ink legends         │
│      ○ PURE        (( record + label ))   arm ╲     │
│  MOTOR [ON]          spindle ·              rest ●  │
│  33 ● 45 ○                                  CUE ▲▼  │
│  FINE ◐ +0.0%   ┌──────────────┐                    │
│                 │33⅓ ▪LOCK  A2 │  ← pixel window    │
│                 │RUNOUT 2/4    │    (instrument)    │
│                 └──────────────┘                    │
└────────────────────────────────────────────────────┘
Side A · A2 Paper Moon · 2:14 · 33 rpm      ← real text status
[ Lift needle ] [ Motor ● ] [ 33 | 45 ] [ Bands ]   ← ≥48 dp tray
NIGHT DRIVE — Vela Park       FLAC 24/96   [Sleeve]
HOME      CRATE      DISCOVER      MESSAGES
```

- **The deck dominates.** The perspective crate moves off Home to the **Crate** tab, with a "Dig" view. Home shows only the **mounted sleeve rail**, or "Choose a record → Crate".
- **No mini-player on Home.** Elsewhere, an "On the turntable" strip shows the same reconciled status sentence and a single context action: Lower, Lift or **Flip to Side B**. It never shows "Play" at side end.
- **The tray changes with the phase:**
  - `CUED`: **Lower needle**
  - `PROGRAM`: **Lift needle**
  - `AWAITING_FLIP`: **Flip to Side B** (primary), plus Put away
  - `FINISHED`: **Play this side again**, **Put away**
  - Motor, speed and Bands are always present.
- **Landscape:** the deck is on the left, the sleeve and bands are on the right, and the tray runs under the deck.
- **Large text** (font scale ≥1.3): the tray wraps into two rows, and the plate legends fade to decoration because the tray carries the labels. Status text never truncates. It wraps to two lines.
- **Night theme:** the plate becomes a dark enamel token, the ink becomes cream, and the window stays amber or green on black (§10).

---

## 7. Choreography timings

All values are proposals to tune on a device.

| Movement | Full | Short | Reduced motion |
|---|---|---|---|
| Sleeve advance from crate | 280 ms | 200 ms | crossfade 120 ms |
| Inner sleeve, then disc edge | 360 + 240 ms | 240 ms combined | instant |
| Placement snap and validation window | 400–600 ms; extends to the validation result | same | crossfade |
| Motor spin-up to speed (heavy platter) | τ ≈ 0.5 s, about 1.2 s to 95% | τ ≈ 0.3 s | platter shown static with an "AT SPEED" readout |
| Cue descent | 0.8 s damped | 0.4 s | instant |
| Lead-in | 2 revolutions | 1 revolution | same audio timing; arm jumps |
| Runout | 4 revolutions | 2 revolutions | same; counter only |
| Arm return and motor brake | 0.9 s, brake τ ≈ 0.6 s | 0.5 s | instant |
| Flip turnover | 520 ms | 360 ms | crossfade 150 ms |

Ritual **Off** zeroes the lead-in and runout revolutions and the cue time. The sleeve and turnover choreography still play unless reduced motion is on, because they are visual and cost no listening time.

---

## 8. Sound design

Surface sound is independent of the music format. FLAC-only applies to music. Synthesised effects are not imported files and never widen admission.

- **One owner.** `SurfaceVoices` with voices for the **bed**, **contact** (needle lands), **lift**, **runout tick**, and **motor** (very low, optional, off by default). Every voice is cancellable and level-controlled. It obeys Pure, the surface preference, focus and noisy events, and the coordinator generation.
- **Bed.** Procedural and long-period: hiss plus clustered pops, with the cluster period tied to the revolution so pops repeat per turn, as real dust does. No four-second fixed-seed loop (`VinylSfx.kt:175–181`). Levels:
  - **Off.**
  - **Subtle**: the current effective level, which is `0.175` of track volume.
  - **Present**: about +6 dB over Subtle, with bounded peaks.
  - **Custom**: a slider with a 5 s audition that stops when you leave the screen.
- **Contact, lift and runout** follow the surface preference too. "Surface sound off" means **no** effect voices. Today only the bed obeys it (UX-06).
- **Pure:** no voices, rate 1.0, no volume ramp, no DSP. The UI says "Surface sound off while Pure is on". Saved preferences are kept, **including the saved 33/45 and fine speed**. Pure forces the *effective* factor to 1.0 and never writes `speed` or `pitch`; turning Pure off restores them, without a ramp (V-13). Turning Pure on cancels any running hold or release ramp (P-03).
- **ReplayGain safety:** gains must be finite and are clamped to −24…+12 dB, with a peak guard from `REPLAYGAIN_*_PEAK` when present (L-07, P-11). Gain changes apply at the stream boundary and update the chain in place, without resetting the limiter or filter state (P-01, P-02).
- **Gapless:** no ritual at band boundaries within a side. Media3 playlist adjacency stays untouched, and the bed continues across tracks.

---

## 9. Supporting screens: same world, quieter materials

### 9.1 Crate

- **Default:** a lazy cover grid. Readable title and artist, plus sort by artist, title, year or recently added.
- **Dig:** the existing perspective crate, kept as a toggle.
- **Record detail = the sleeve.** Front, back (bands per side, durations, and "Runout side layout · computed from track lengths", because `SideSplitter` always computes sides), source, quality, provenance and licence.
- **Record actions:** Place on platter, Place and cue a band, Send (existing `.vinyl` sharing), Remove (non-folder sources only).
- **Crate tools:** Music folder, Rescan, Pressing Plant.
- **Partial Soulseek records** read "Partial record · 1 of 12 files" when membership is known from a preview, otherwise "completeness unknown".

### 9.2 Discover: the record-shop counter

- **Source chips:** **Device + Soulseek**, **On this device**, **Soulseek**. "All sources" is renamed because it never included Archive.
- **Friday Drops** stays a separate shelf chip. Archive behaviour is unchanged: ten weekly albums, three successful claims, and no general Archive search.
- **Local results:** albums first. A song hit reads "Paper Moon · in Night Drive · A3", with **Open record at A3** as the primary action and **Place and cue A3** as secondary. "Play song" and "Play album" are removed.
- **Remote results:** folders first. A song hit's action is **Preview folder**. Inside the preview, **Download record** is primary, **Choose files** is secondary, and the existing completeness caveat is kept.

### 9.3 Downloads: incoming records

Pinned **recovery card** above the tabs. Copy for the reported state:

> **Connection paused**
> This Soulseek ID connected in another client. Your partial files are kept here. Reconnecting may disconnect that client.
> **[Reconnect here]** · Keep listening offline

After an explicit, successful reconnect:

> **5 downloads were interrupted.** [Resume 5] · Leave paused

- **Resume N** includes Soulseek rows for the current account whose `pauseReason` is `SESSION` or `INTERRUPTED` (Room v2, slice 1b, §13). Rows the listener paused (`USER`) are excluded. For legacy rows whose reason is null, fall back to `PAUSED ∧ error != null`; this tests for null and never parses message strings.
- **Nothing resumes automatically.**
- **Rows** are grouped by account, provider, peer and full folder. They show readable sentence-case file names, not uppercase `SectionTitle`. For example:
  - "Waiting for your connection · 53.3 of 93.7 MB kept"
  - "Waiting for your connection · nothing downloaded yet"
- **Cancel** on a row with kept bytes asks "Discard 53.3 MB downloaded so far?" and then actually deletes the private staging data (R-12). Already-published music is never touched.
- **Archive rows:** "Claim again from Friday Drops". Outside the current week the action is disabled, with the reason given up front.
- **Tabs:** Active / Needs you / Saved. The header badge counts *needs you* (interrupted or failed) as well as active rows. It never reads "↓ 0" while five rows wait.
- **Lists are lazy**, with stable keys.

### 9.4 Messages

- A lazy list with date separators and readable prose (never the pixel font).
- States: pending, accepted ("sent" means accepted by the transport, never "delivered"), and failed with **Retry**.
- History is readable offline. The composer explains that a connection is needed to send, and the draft is kept.

### 9.5 Signal path, Bluetooth and the DAC guide (pass 2, user request)

**What the user asked for:** a view like Amazon Music's "Audio quality" (track → device → output, with bit depth and rate at each step), to confirm what bitrate they are actually getting. Also Bluetooth compatibility, and a recommendation to use a wired DAC that is honest about Android's limits.

**Runout's rule:** every stage states **how we know**:

- **measured**: read from the file's FLAC STREAMINFO;
- **set by Runout**: the format Runout hands to Android;
- **reported by Android**: device or mixer information the platform exposes;
- **not reported**: Android doesn't tell apps.

Runout never prints a rate or bit depth that Android didn't report. The Amazon-style line "Built-in speaker · 24-bit / 44.1 kHz" is exactly what this rule forbids, unless Android reports it.

**Entry points:**

- Tap the **quality stamp** on the record label or the instrument window (for example "24/96").
- Or go to Settings › Listening › **Signal path**.

It opens a sheet in Direction D style: a printed "signal chain" card with four stamped stages joined by a rule, plus real-text lines.

```text
SIGNAL PATH                                   [?]
① RECORD      FLAC · 24-bit · 96 kHz · stereo            measured
              ~2,950 kbps average file · 4,608 kbps PCM
② RUNOUT      32-bit float · 96 kHz · EQ off · ReplayGain −3.1 dB   set by Runout
              (Pure: "no processing in Runout")
③ ANDROID     Mixed by Android · rate not reported            reported / not reported
              (USB bit-perfect: "Bit-perfect · 24-bit · 96 kHz" only when
               the active mixer attributes match encoding, rate and channels)
④ OUTPUT      Bluetooth · "WH-1000XM4" · wireless codec not reported   reported
              Re-encoded lossy for wireless. Hi-res detail is reduced.
──────────────────────────────────────────────
Get the most from FLAC → [Wired DAC guide]
```

**Data sources.** All are public APIs; no new permission is needed. Verify each against API levels 28–36 and Media3 1.9.4 when implementing.

- **① Record.** `AudioInfo` from FLAC STREAMINFO: bits, rate, channels. Average file bitrate is `bitrateKbps`, which already exists but is never shown for lossless. PCM rate is bits × rate × channels.
  - Fix `AudioQuality.label` (or add a `detailLabel`) so FLAC shows its bitrate.
  - Label the bitrate "average", because FLAC is variable.
- **② Runout.** What `DspAudioSink.configure` handed to the delegate: float or 16-bit, sample rate, and whether processing is active. Use `onAudioTrackInitialized(AudioTrackConfig)` from `AnalyticsListener` where available, falling back to the sink's own configure values. Truth fixes:
  - Under Pure with hi-res output **off**, the stage says "16-bit (hi-res output is off)". It never says "untouched" (P-09).
  - Reset this stage on eject, stop and service destroy, so stale "bit-perfect" lines disappear (P-10).
- **③ Android.**
  - USB: `getPreferredMixerAttributes(attrs, device)` (Android 14+). Report **bit-perfect** only when the active attributes' behaviour is `MIXER_BEHAVIOR_BIT_PERFECT` **and** their encoding, rate and channel mask match ② (P-09). Otherwise say "Mixed by Android". Android doesn't report the mixer rate to apps in general, so say "rate not reported", not "48 kHz".
  - Direct or offload playback (`AudioManager.getDirectProfilesForAttributes`, API 33+) can be listed as "Device can play this format directly". That describes capability, not current state.
- **④ Output.**
  - The device type and product name come from `AudioManager.getAudioDevicesForAttributes` (API 33+), or from `getDevices(GET_DEVICES_OUTPUTS)` with a priority heuristic (USB > wired > Bluetooth A2DP/LE > speaker) on API 28–32, labelled "likely".
  - Register an `AudioDeviceCallback` so the card updates live when headphones or a DAC are connected or removed.
  - **Do not request `BLUETOOTH_CONNECT`.** The Bluetooth codec isn't available to normal apps, so the card says "wireless codec not reported".
  - `AudioDeviceInfo.getProductName()` gives the device name without a Bluetooth permission. Verify this on API 31+, and fall back to "Bluetooth headphones" if it isn't available.

**Bluetooth compatibility requirements.** These are engineering requirements and acceptance criteria.

- **Routing is automatic.** Never pin a device with `setPreferredAudioDevice`, unless a future explicit "Prefer USB DAC" setting asks for it.
- **Disconnect.** Media3's noisy handling pauses the program. The deck enters `PAUSED(NOISY)` with "Paused: Bluetooth disconnected", and surface voices stop immediately (interim voices have their own noisy receiver, V-11).
- **AVRCP.** Media keys, cars and watches go through the session wrapper (§4.3). Play, pause and in-side next/previous work; nothing crosses sides or records.
- **Metadata.**
  - Album title stays the plain album title; the side goes in the subtitle or extras (P-15).
  - Set the album artist.
  - Artwork must be reachable by the system UI: serve it through a `content://` provider URI rather than a `file://` URI into private storage. Verify the current `Uri.fromFile` behaviour on device (`Deck.kt:427`).
- **Latency.** Bluetooth adds roughly 100–300 ms of output latency (Hypothesis; varies by device and codec). Program-driven visuals already follow the player position. The ritual must not use wall-clock timers for audible alignment: the interim lead-in visuals may lead the sound slightly (acceptable), and the target design (sink-mixed ritual) stays aligned with the program because it is in the same stream.
- **Hi-res over Bluetooth.** Playback works, but the card states that wireless re-encodes. No "Hi-Res" badge is ever tied to a Bluetooth route.
- **Absolute volume and volume keys** stay system-managed. No in-app volume.

**The wired DAC guide.** One screen from the signal-path sheet or Settings, plus a single dismissible tip. The tip appears at most once per route type, when a ≥24-bit or >48 kHz record plays over Bluetooth or the built-in speaker. No nagging, no product or affiliate links, no brand recommendations.

> **Get the most from FLAC**
> A wired USB DAC (a small USB-C adapter or desktop unit) skips wireless re-encoding.
> **What Android does, and what to expect:**
> • **Android usually mixes audio.** Most phones resample everything to a fixed rate (often 48 kHz) before output. Runout cannot prevent this on Bluetooth or the speaker.
> • **Bit-perfect needs Android 14 or later** and a phone and DAC that both support it. Runout shows "Bit-perfect" only when Android confirms the matching mode.
> • **In bit-perfect mode, volume may be fixed at full scale**, so use the DAC's or headphones' own volume, and **turn it down before you press play**. Other apps' sounds may be muted or behave differently while it is active.
> • **A DAC draws power from your phone** and needs USB-C (or an OTG adapter). Some phones limit USB audio formats.
> • **Bluetooth is always re-encoded.** Even high-bitrate codecs are lossy, and Android does not tell apps which codec is in use.
> • **The speaker** is fine for casual listening; it is also mixed and processed by the phone.
> Pure mode turns off Runout's own processing (EQ, ReplayGain, surface sound, speed). It cannot change what Android or Bluetooth do after Runout.

**Tests and acceptance:**

- With fake `AudioDeviceInfo` and mixer attributes, the card never shows "bit-perfect" unless the encoding, rate and channels match.
- A Bluetooth route always shows "codec not reported" and no hi-res badge.
- The route updates within one callback of a plug or unplug.
- Pure with hi-res off shows 16-bit.
- Every stage line has a "how we know" tag.
- FLAC shows its average bitrate.
- No `BLUETOOTH_CONNECT` appears in the merged manifest.

### 9.6 Settings: one entrance, five rows

| Row | First level | Nested |
|---|---|---|
| **Listening** | Ritual (Full / Short / Off), Surface sound (Off / Subtle / Present / Custom + audition), Pure, **Signal path** (§9.5) | Advanced sound: Hi-res output, ReplayGain, EQ / AutoEq, Warmth, Limiter; Wired DAC guide |
| **Collection & storage** | Music folder, Download folder, Rescan, Repair access | Scan report; **Find missing covers online**, off by default, naming the services: MusicBrainz, Cover Art Archive, Internet Archive (L-04) |
| **Soulseek** | Status with **Reconnect** / **Disconnect**, Sign out, Remember sign-in | Sharing folder and consent, Stay online in background, Protocol privacy note, Review setup |
| **Display & access** | Theme (System / Day / Night), Reduce motion (follow system / on), Reduce texture, Haptics | Pressing look (by quality / always black) |
| **About** | Version, licences | Sanitised diagnostics, replay the tour |

Settings rules:

- **Back** returns to the screen you came from, with its scroll position and query intact.
- **Cancelling a folder picker** keeps the previous valid grant.
- Account-scoped consent stays scoped to the exact account.

---

## 10. Visual system, Direction D: "Printed hi-fi"

Three material tiers, following the spec's hierarchy but moving realism onto the moving parts.

1. **Moving parts, rendered.** The record (grooves, sheen, label art from the cover), the platter rim, the arm and headshell, the cue lever and the strobe. These get depth, contact shadows and controlled highlights.
2. **The plate, printed.** A flat enamel colour token, ink linework and legends set like an owner's-manual plate. No wood or glass texture. An optional walnut **edge** band, 6–10 dp, is only a token.
3. **The instrument, pixel.** The window, in Silkscreen (OFL, already bundled), shows RPM, strobe lock, band code, time, and LEAD-IN / RUNOUT k/n. It always has a real-text twin underneath.

Supporting screens use paper and ink: ruled lists, catalogue strips in monospace (`CAT 0042 · 9 FLAC · 412.6 MB`), a few short ASCII headers (`── SIDE A ──`), and stamped side codes. Rounded glass cards, the Backdrop lens and pastel gradients are removed from the deck and panels.

Tokens. Contrast was calculated in this session, without alpha or texture.

| Token | Day | Night | Use |
|---|---|---|---|
| background / paper | `#F2E6CE` | `#1C1A17` | Screens |
| plate enamel | `#D9CDB4` | `#2A2620` | Deck top plate |
| ink | `#24231F` (12.72:1 on paper; 9.99:1 on plate) | `#E9DDC4` (12.90:1) | Text, legends |
| quiet | `#5E574B` (5.78:1) | `#C9BBA0` (9.18:1) | Metadata |
| signal green | `#345A45` (6.30:1) | — | Selected / OK |
| amber, small text | `#8A4B12` (5.49:1) | — | Warnings in body text |
| amber, decorative/large | `#BC6C25` (3.20:1) | — | Large stamps only |
| error | `#9A3429` (5.86:1) | `#E07A6A` (5.92:1) | Errors |
| primary action | `#7A2E22` with paper text (7.58:1) | same | One primary per screen |
| window | `#14110D` with amber `#FFB547` (10.71:1) or green `#9FD8B0` (11.58:1) | same | Instrument |

Type and imagery rules:

- **Type:** Inter for every sentence. Body 14–16sp, metadata 12–13sp, respecting system scale. Pixel type only for ≤3-word stamps and the window. ASCII decoration is hidden from TalkBack and is never the only carrier of status.
- **Album art** keeps its own colours. User-chosen pressings keep their styles.

### Assets: the "tell me if you want textures or SVG" answer

Slices 1–3 need **no assets**. For slice 4/5 polish I would ask you for these (or create originals), with an asset manifest recording creator, licence and dimensions:

1. a seamless paper-grain tile (512 px PNG, used at ≤8% alpha, off behind small text);
2. an inner-sleeve fibre tile (512 px);
3. about 12 original 16×16 pixel icons as SVG: motor, cue, download, attention, side, strobe, sleeve, pure, plus a few spare;
4. optionally, original mechanical recordings (cue thump, lift) to compare against the synthesised voices in a listening session.

No commercial deck branding and no scraped covers.

---

## 11. Accessibility contract

- **Every deck command has a ≥48 dp labelled tray control** with the same command path. Plate art is a mirror and an optional gesture surface. **No gesture-only task remains:** the hidden tap-flip is removed (R-05).
- **Semantics:**
  - record: "Night Drive, Side A, 5 bands, needle up at A2";
  - arm: custom actions for next band, previous band and lower here;
  - fine speed: `setProgress`;
  - status: a polite live region for phase changes;
  - crate sleeves: role and label.
- **The state description reflects the reconciled program state.** It never says "Playing" while paused (R-01).
- **Reduced motion** follows the system animator scale, plus an in-app override. It changes visuals only.
- **Sound-free:** every phase has text (§4). Haptics are optional, at cue detent, band snap, fine-speed zero and spindle seat.
- **Text:** no live status inside canvas text. Large-font layouts wrap. RTL keeps band codes LTR.
- **Contrast** per §10. Texture is off behind text below 18sp. The switch track must reach 3:1 against its surface; today it is 1.31:1 (U-18).
- **Fine speed** exposes `ProgressBarRangeInfo` with 31 steps (0.5% per TalkBack swipe), while dragging keeps 0.1% resolution (V-20).
- **The live region is the phase sentence only.** `RUNOUT k/n` and the running time are non-live nodes; otherwise TalkBack would chatter every 1.3–1.8 s.
- **Reduced motion** reads `Settings.Global.ANIMATOR_DURATION_SCALE` directly, because the platter's `withFrameNanos` loop doesn't honour the animator scale.
- **Overlays.** Every full-screen overlay (RetroPage, Folder Preview, Settings, the signal-path sheet) consumes taps and sets `paneTitle`, so content underneath is unreachable by touch or TalkBack (U-01).
- **Selection state.** Selected chips, tabs and destinations expose `selected`. Unread counts are a state description ("3 unread"), not a bullet character (U-08). The EQ faders get `setProgress`; pressing track pickers get `toggleable` with a checkbox role (U-09).
- **Sign-in fields.** The Soulseek username has autocorrect off, an ASCII keyboard and an autofill content type. The password gets a reveal toggle, and Done submits (U-05).

---

## 12. Performance budget

These are targets to measure, not claims.

- **The deck canvas allocates nothing per frame.** Strobe dots, grooves and legends move to `drawWithCache` or cached `Path`/`ImageBitmap`. The pixel window renders to an `ImageBitmap` only when its text changes. `DeckGeometry` is remembered by size.
- **No Backdrop blur or lens on the deck or panels.**
- **Jank:** under 1% janky frames over 30 s of spinning on a mid-range physical device (JankStats or Macrobenchmark). Compare before and after on the same device.
- **No main-thread SAF I/O** on placement or in session callbacks (UX-15).
- The position poll runs only while a record is mounted and the program is playing.
- **Lists:** Crate grid, Downloads and Messages are lazy with stable keys. The search gates of 10k local and 5k remote are unchanged.

---

## 13. Data, migration and privacy

- **Deck restore.** Persist `albumId, sideIndex, positionMs, speed, fine` in a **new `deck_mount` preferences file**. Exclude it in **both** `backup_rules.xml` and `data_extraction_rules.xml` (cloud backup and device transfer), because album ids can be digests of peer and folder. `deck.xml` stays backed up, since it holds `crackle` for the migration below. Restore is **visual only** (`CUED`), after off-main URI checks. The controller connects and prepares on Place or Lower, never at launch (V-10).
- **Backup and restore of folder grants (U-06).** Exclude the folder-URI preferences (`library`, `soulseek` destination and shared folder) and `soulseek-setup`'s onboarded flag from backup. Alternatively, check `persistedUriPermissions` at startup, clear URIs without a grant, and re-prompt. A restored phone must never skip setup into dead folders.
- **Never rewrite a record list on a failed read (L-01, L-02).** Unreadable records stay on disk and are shown as "unavailable · repair access". Every rewrite keeps a dated backup. A rescan that couldn't list its root keeps the previous shelf.
- **Preferences.** `deck.crackle = true` becomes Surface **Subtle**; `false` becomes **Off**. Ritual length defaults to **Short** for existing installs and **Full** for new installs (open decision). Read the old key for one version.
- **Room v2, in slice 1b (moved up in pass 2, V-17).** Add a nullable `pauseReason` (`USER`, `SESSION`, `INTERRUPTED`, `ERROR`; null means legacy-unknown) with an explicit `Migration(1, 2)`, an exported schema and a `room-testing` migration test. The schema directory and `exportSchema = true` already exist. The native transfer handler must preserve `pauseReason = USER` (today a PAUSED→PAUSED event can overwrite the error). "Resume N" filters on `provider == "Soulseek" ∧ owner == current ∧ pauseReason ∈ {SESSION, INTERRUPTED}`. The `error == null` rule is only a fallback for legacy rows with a null reason. Never use destructive fallback.
- **Privacy and licence in sharing (L-05, L-06).** `.vinyl` export strips `source`, `from` and `notes` unless the user opts in. The Soulseek username never goes into `Profile.u`. Import always sets `owned = false`.
- **Downloads.** Discarding staging is explicit and confirmed. Published music is never deleted by Cancel, migration or cache clearing.
- **Privacy.** No new network calls, accounts or telemetry. Credentials, message bodies and query text stay out of logs and diagnostics. Copy says "another client". Archive stays an independent HTTPS source under Friday Drops.

---

## 14. Build slices

### Slice 0, Stop the damage (pass 2: first build)

Small, local, red-first fixes. Each one prevents data loss, a crash, a privacy or licence leak, or a retry war. No UI redesign. The IDs are in review §16.1.

1. **Record lists:** never rewrite on a failed read; dated backups; a rescan that couldn't list its root keeps the shelf (L-01, L-02).
2. **Crash:** `Math.floorMod` in `fallbackColor` (L-16).
3. **ReplayGain safety:** finite values only, clamped to −24…+12 dB, lower-case "db" parsed (L-07).
4. **`.vinyl` import:** bounded manifest and checksum reads, catch `Throwable` with cleanup, a startup sweep of `incoming-*`, and checksums required when audio is present (L-08, L-17).
5. **Privacy and licence:**
   - Online cover lookup off by default, with a negative cache (L-04).
   - Export strips `source`, `from` and `notes`; no Soulseek username in the profile (L-05).
   - Import forces `owned = false` (L-06).
   - Backup excludes the folder URIs and the onboarded flag, or startup re-validates grants (U-06).
6. **No retry war:**
   - The poll loop handles each event independently and never turns a handler exception into a session loss.
   - Contact re-watch uses `runCatching`.
   - Incoming DM usernames are validated before `addContact`.
   - On a transport error, the native loss reason is checked before classifying it (S-01, N-07).
7. **Native shutdown:** `stop()` calls `remove_download` for every transfer before `shutdown()`, so download threads exit and `.part` files are kept (N-01).
8. **Friday Drops:** a claim refuses or refreshes when the cached week ≠ the current index, and counts and records against one index (S-08).
9. **Onboarding and drafts:**
   - Back at the first-run welcome screen exits without marking onboarding complete (U-13).
   - The Pressing draft clears only on success and asks before discarding (U-02).
   - The message draft clears only after the row is inserted (U-03).
   - Remove asks for confirmation (U-04).

Tests:

- **JVM:** an unreadable list is not rewritten; an empty rescan keeps the shelf; the `Int.MIN_VALUE` title renders; NaN, ±Infinity and +60 dB gains are clamped; an oversized manifest is rejected without OOM; imported `owned` is false; export has no `from`; one invalid contact does not disconnect.
- **Rust:** `stop` removes downloads, and the download thread exits within one wait cycle.
- **Drops:** a rollover test with a fake clock.

### Slice 1, Deck truth

No new art. It fixes what would make any new art lie.

1. **Reconciled program state.** Add `program`, `pauseReason` and `SUSPENDED(CALL)` to `DeckState`, derived from listener events (`onIsPlayingChanged`, `onPlayWhenReadyChanged` reason, `onPlaybackSuppressionReasonChanged`). Platter target, Start/Stop state description, crackle and status derive from it (R-01, R-11, UX-09, V-02). An interim own-command flag, if used, is set synchronously before each controller call (V-21). Add `onPlayerError` handling: lift, show a message, re-prepare on the next drop (P-06).
2. **Rate and volume hygiene.**
   - Remove the needle-drop volume ramp; play at 1.0 after the settle (R-03, R-04).
   - **At the end of `holdPlatter`, after `pause()`, reset `playbackParameters` to `effectiveSpeed()`.** Do the same in every `rampJob` cancellation path, so an external Play after a hold runs at the right rate without any session change (R-02, V-08).
   - `onPureChanged(true)` cancels `rampJob` and never overwrites the saved speed or fine (P-03, V-13).
   - Ramps recompute their target from `effectiveSpeed()` on every step (P-04).
3. **Gated voices.** Extract a `SurfaceVoices` interface behind `VinylSfx`. Track the one-shots. Pure **and** the surface-off preference gate contact, lift and runout. Stop all voices with `pause`/`flush`/`release` on Pure, noisy, focus and eject. Precompute buffers off the main thread (UX-06, V-11, P-17).
4. **Album-only edges.**
   - `nextTrack` and `previousTrack` are clamped to the side.
   - `playTrack` is replaced by `cueTrack` (needle up).
   - Discover's actions become **Place and cue A3** only. "Open record at A3" waits for the record detail in slice 4/5 (R-07, R-08, V-18).
   - `Deck.load` keeps measured durations (`refreshMetadata = true`) and tracks `currentMediaItemIndex` (P-05).
   - One validation path is shared by the Deck and the service (P-08).
5. **Side end.** `startStop` at `sideFinished` no longer replays. The strip and Home show **Flip to Side B**, or **Play this side again** / **Put away** on the last side (R-06).
6. **Visible Flip and Put away** in a minimal Home tray with semantics. Remove the quick-tap flip (R-05).
7. **Off-main placement.** `PLACING` state, IO validation, a failure message, and confirmation before replacing a record whose needle is down (R-09, R-10, UX-15).
8. **One notice owner.** Remove the `RoomScreen` toast (UX-12).

**Red-first tests.** These extend `TurntableDeckTest` with a fake player, `FakeSurfaceVoices` and `TestScope` time, plus `ListeningRoomUiTest`. No results are claimed until they run.

- External pause, then one `startStop`, plays.
- Hold, then external `play()`, gives rate = effective speed.
- A lift 300 ms after a drop leaves volume 1.0.
- Pure on during a runout stops the voice and allocates no new one.
- Surface off means no lift or runout voice.
- Next on the last band is a no-op.
- At side end, `startStop` does not seek to 0 on the same side.
- A search cue leaves the needle up.
- Load failure produces a message and keeps the previous record.
- Replace with the needle down requires confirmation.
- Flip is a visible, semantically labelled control.
- Exactly one message node is shown.

Also update the tests that encode the rejected policy: `TurntableDeckTest` "search starts the selected song…" and "next and previous … turn the record over".

**Test tags and flows to migrate in the same slice (V-19):**

| Removed or renamed | Replacement |
|---|---|
| `deck:startstop`, `deck:prev`, `deck:next` | Tray: `deck:lower` / `deck:lift`, `deck:motor`, `deck:bands` |
| `deck:pitch` | `deck:fine` |
| `deck:33`, `deck:45` | Keep, on the segmented control |
| `deck:screen` | Keep, on the **real-text status twin** |
| `mini-play` | `strip-action` |
| `play-local:*` | `cue-local:*` (slice 1); `open-record:*` (slice 4/5) |
| `open-sound`, `open-pressing` | `open-settings`; Pressing moves to the Crate tools (slice 5) |
| `destination:LIBRARY`, `screen:library` | `destination:CRATE`, `screen:crate` (slice 5) |
| `open-account` | Keep. Three Argent flows assert it contains "Online", so it stays on the Soulseek status element even after the Settings entrance replaces the account pill. |

Update the `ListeningRoomUiTest` action-log assertions (`startStop`, `next`, `prev`, `speed`). Retire the RoomScreen-chrome tests that production never shows (U-19), or point them at `RunoutScreen`.

**Rewrite `.argent/flows/offline-song-search.yaml`.** Today it taps "Play song" and asserts `deck:screen` contains "Opening". The new flow: tap **Place and cue**, assert the needle is up, tap **Lower needle**, then await the status twin containing "Opening".

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`, plus `connectedDebugAndroidTest` (the change touches playback), plus the rewritten Argent offline flow.

### Slice 1b, Downloads recovery (parallel, independent)

- **Room v2** `pauseReason` with `Migration(1, 2)`, an exported schema and a migration test (V-17).
- Recovery card with **Reconnect here** in Downloads; non-dismissable session status; per-row "waiting" copy; attention badge.
- `resumeInterrupted()` after reconnect, using `pauseReason`.
- Cancel discards staging, with confirmation; it also removes orphaned `.runout-part` documents, never touching published finals (S-11).
- Archive label "Claim again from Friday Drops"; lazy list; sentence-case names.
- **Transfer state machine fixes:**
  - A PAUSED row still records native terminal events and leaves `activeTransfers`.
  - Resume of a terminal native transfer falls back to `download` (S-02, N-06).
  - Pausing a peer-queued download works (S-03, N-05).
  - Upload rows get terminal events when the core drops them (S-04, N-02).
  - The bridge keeps the last known bytes, so "53.3 MB kept" stays true (S-13, N-11).
  - A folder enqueue reports "N of M queued" instead of aborting (S-15).
- **Archive:**
  - Progress throttled to about 2 Hz with a conditional SQL update and real sizes (S-05).
  - Cancel stops the HTTP loop (S-06).
  - Staged copies deleted after a verified publish (S-07).
  - A Soulseek blip no longer fails a claim that succeeded (S-14).
  - Range resume for partial tracks (S-17).
- **Soulseek album identity:** fixed on the first publish; no hash prefix on filenames (S-09, S-12).
- **Native follow-ups:**
  - Shares validation moves off the wire lock (N-03).
  - Login rejection reasons are carried through, as a documented vendor delta (N-04).
  - Event-cap eviction targets `search_batch` and never drops control events (N-09).

Tests:

- **`SoulseekRepositoryTest` with a fake transport:** displacement sets `pauseReason = SESSION`; no resume on `connected`; `resumeInterrupted` skips USER; Cancel removes `soulseek-staging/<id>` and never a published URI.
- **Room migration test.**
- **Bridge tests:** queued pause; terminal resume; upload removal event.

### Slice 2, Coordinator phases, session wrapper and persistence

- Motor, arm and phase dimensions.
- `RunoutSessionPlayer` (`ForwardingSimpleBasePlayer`) with the deck connection hint, `getControllerForCurrentRequest()` attribution, Play hidden at side end, and swap-in-place (§4.3).
- `onConnect` allow-list (P-16).
- `onTaskRemoved` → `pauseAllPlayersAndStopSelf()` outside LEAD_IN and RUNOUT (V-22).
- `LEAD_IN` and `RUNOUT` with interim voices, timed in revolutions. `play()` happens after the revolutions; the bed runs until `isPlaying`.
- Visual-only deck restore in `deck_mount` (G-01, V-10).
- Next and previous band by arm park.
- Fake-clock statechart tests for §4.2 and §4.3, plus a **Robolectric session test** with an in-process `MediaSession`, a hinted and an unhinted `MediaController`, and the wrapper (V-21).

### Slice 3, Sound and signal truth

- **Target ritual audio:** surface sound mixed into `DspAudioSink`, with lead-in and runout as silent program periods from a format-matched silence source (§5 B1).
- A procedural, RPM-tied bed; runout ticks; level presets and audition; preference migration.
- **DSP fixes:** in-place chain updates, gain switching at the stream boundary, a smoothed limiter attack (P-01, P-02, P-11).
- **The signal-path sheet, Bluetooth requirements and the wired DAC guide** (§9.5), including the truth fixes for bit-perfect and output status (P-09, P-10).
- **Metadata:** plain album title, album artist, artwork over `content://` (P-15).
- **A listening session** on real headphones, speaker, a Bluetooth headset and a USB DAC where available, recording levels, latency and impressions as observations only.

### Slice 4, Mechanism and plate (Direction D)

New plate drawing, control tray, band-snapping arm (B2), sleeve, place and turnover (B3), night theme, reduced motion, cached drawing (§12).

### Slice 5, Supporting screens and settings

Crate grid and record detail ("Open record at A3"), Discover relabel and the cue flow, the Settings entrance, lazy Messages, overlay pane semantics and keyboard insets (U-01, U-07), landscape shell (U-10), assets, TalkBack and large-text pass, a performance pass, and a development APK under the **existing** signing. No release or PR without a separate instruction.

---

## 15. Acceptance additions

These add to v1 §10.

- An external pause and resume from the notification or headset leaves audio, platter, arm, status and TalkBack state in agreement, and needs one press to resume in-app.
- No path produces playback at a rate other than the effective speed, or at volume below 1.0. The exceptions are BRAKING or SPINNING_UP with the needle down, and an explicit platter hold (V-14).
- A transient call suspends without lifting, and resumes on its own. Headset or notification Play at the side end does nothing audible.
- The signal-path sheet never shows a bit depth, rate or "bit-perfect" that Android didn't report. Bluetooth routes always say the codec is not reported.
- A Bluetooth disconnect pauses within one callback; no surface voice is ever heard on the speaker afterwards.
- No `BLUETOOTH_CONNECT` permission appears in the merged manifest.
- A restored backup on a new phone never skips setup into dead folder grants.
- Pressings and received records survive a launch with their storage unavailable.
- From search, no audio starts until the listener lowers the needle.
- At every side edge, next and previous do nothing audible. A side change happens only through Flip.
- Lead-in starts the program at sample 0 at full volume. Runout duration scales with 33/45 and fine speed. Pure makes both silent without changing state order.
- With surface sound off, no effect voice is ever created.
- In the displaced Downloads case: one notice, bytes kept and stated, an explicit reconnect, an explicit "Resume N" that excludes user-paused rows, and a Cancel that discards only after confirmation.
- At 320 dp and 412 dp portrait, in landscape, at font scale 2.0 with TalkBack, every deck command is reachable as a labelled ≥48 dp control.

## 16. Open decisions for the user

1. **Direction D (printed plate, realism on the moving parts) or v1's B (walnut and enamel furniture).** Recommendation: D.
2. **Ritual default for new installs: Full or Short.** Recommendation: Full for new installs, Short for upgrades.
3. **Runout length: 2 or 4 revolutions, and whether the motor stops automatically after the lift.** Recommendation: 4 revolutions on Full, 2 on Short; the motor stops.
4. **Keep the finger-brake long-press on the record?** Recommendation: yes, as an optional gesture; Motor off is the accessible equivalent.
5. **Assets in §10:** provide them, or let me draft originals in slice 4.
6. **Messages as a main tab, or behind a header entrance.** Recommendation: keep the tab for now; it is existing, shipped behaviour.
7. **Online cover lookup default** (pass 2). Recommendation: **off** for new and existing installs, with a one-time notice to existing users that it is now opt-in. It currently sends artist and album titles to third parties during "offline" listening.
8. **Ritual audio target** (pass 2). Mixing into the sink, with silent program periods, is recommended over separate voices. It costs about 150 LOC for a format-matched silence source, plus index mapping. Accept, or stay on the interim voices with the documented focus limits.
9. **The signal-path name in the UI** (pass 2). "Signal path" (recommended; it fits the hi-fi plate) or "Audio quality" (familiar from other apps).
