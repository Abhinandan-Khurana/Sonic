# Runout listening-room redesign: review of draft v1 and the experience spec

Status: design review, planning only. Date: 2026-10-20.
Reviews: [`RUNOUT-REDESIGN-DRAFT-v1.md`](RUNOUT-REDESIGN-DRAFT-v1.md) (v1) and [`RUNOUT-EXPERIENCE-SPEC.md`](RUNOUT-EXPERIENCE-SPEC.md) (spec).
Successor: [`RUNOUT-REDESIGN-DRAFT-v2.md`](RUNOUT-REDESIGN-DRAFT-v2.md).
Baseline: HEAD `126290c`. The app code is unchanged since `64e1222`: `git diff --name-only 64e1222 HEAD` lists only `Handsoff.md`, `docs/README.md` and the two design drafts.

This review stands in for the GPT-6 Astra review, which stopped at a usage limit and never produced output. I wrote it after rechecking every cited source location in the current checkout.

**Second pass (same day).** Six read-only subagent audits ran in parallel: playback/sound, library/records/sharing, Soulseek/Drops/Discovery, UI/onboarding/accessibility, the native bridge, and an adversarial Media3 review of v2. The plan reviewer checked its Media3 claims against the 1.9.4 tag source. Their findings, with the lead reviewer's spot checks, are in **§16**. The playback-state corrections they made to §4–§5 are applied in place and marked *(corrected in pass 2)*. Section 2 now opens with a **Tier 0** of data-loss, safety, privacy and retry-war items, which outrank the listening-room work.

## 0. Method, evidence labels and limits

I read the handoff, README, architecture, developer and contributing docs, both drafts, and the Kotlin sources they cite. That covers `playback/`, `sound/`, `ui/` (shell, room, turntable, crate, controls, panels, discovery, Soulseek panels), `soulseek/` (repository, connection manager, models, database, storage), `library/FlacPolicy.kt`, `drops/Drops.kt` claim rules and `MainActivity.kt`. I also read the Rust bridge's `download` command and the vendored `PartFile` resume logic.

Each finding carries one of these labels:

- **Observed.** I saw it in an artifact during this session. The only visual artifact I inspected is the committed `docs/images/home-0.4.1.png`.
- **Reported.** It comes from the user's brief. That includes the Downloads screenshot, which was described to me but not available in this session. v1 labelled the screenshot items "Observed". I have downgraded them, because I could not see the image.
- **Source-confirmed.** The exact code path exists at the cited lines. I traced it but did not run it.
- **Hypothesis.** It is plausible from the source but needs a device, a fake-clock test or a listening check.

Not done in this session: no Gradle or Cargo build, no unit, instrumentation or Argent run, no emulator or device interaction, no audio audition and no network use. No test results are claimed here. The settled DSP question (32-bit integer PCM converted to float in `DspAudioSink`) is not reopened. It appears below only where it affects user-facing copy.

---

## 1. Verdict

v1 and the spec point the right way: album as container, sleeve ritual, finite runout, one coordinator for state, and honest Downloads recovery. They are not yet buildable, for four reasons.

1. **They skip the bugs that make realism impossible.** Several source-confirmed deck defects (sections 3 and 4) would make any new art lie about what is playing. Examples: external pause leaves the platter spinning; resuming after a platter hold plays at 0.25× speed; a lift during the needle settle leaves the player at volume 0; and a hidden tap is the only way to flip a record on Home. v1 found some of these. The spec assumes a coordinator will absorb them and does not list them.
2. **They never decide the album policy.** Both documents defer "album-first versus album-only". The current source lets search start a song immediately (`Deck.playTrack`). "Next" on the last band of a side flips the record and autoplays the next side (`Deck.nextTrack`), and wraps from the last side back to Side A. Section 6 decides this.
3. **The art direction is decoration, not mechanism.** The walnut/enamel Console restyles a direct-drive DJ deck (S-arm, ±8% fader, prev/next keys, start/stop key, "GLASS DRIVE RG-1"; Observed in the committed image). A 1950s ritual needs a 1950s mechanism model, where each control maps to exactly one deck command. Section 7 challenges this.
4. **Some v1 findings are wrong or mis-labelled.** These are corrected in section 2.

---

## 2. Top required changes, in priority order

These must be resolved before implementation starts.

**Tier 0: stop the damage.** These come first and are mostly small, local fixes. Details are in §16.

- **0a. A temporary read failure permanently rewrites record lists (L-01).** Startup validation drops unreadable pressings and received records, then rewrites the JSON. The backup is made only once, so a later rewrite has none. A rescan without folder access also wipes the whole shelf (L-02).
- **0b. Crash and safety.**
  - A title whose hash is `Int.MIN_VALUE` crashes rendering (L-16).
  - ReplayGain tags are unclamped and `NaN` is accepted, so a hostile or corrupt tag can produce extreme levels or silence (L-07).
  - An oversized `.vinyl` manifest can run the app out of memory (L-08).
- **0c. Privacy and licence.**
  - Online cover lookup is on by default with no consent; it sends artist and title to MusicBrainz and the Cover Art Archive (L-04).
  - Shared `.vinyl` files leak the Soulseek peer and the sender's username (L-05).
  - A received record keeps the sender's `owned` flag, so the receiver can re-share the audio (L-06).
  - Backup restores folder URIs and the onboarded flag, but not the SAF grants those URIs depend on (U-06).
- **0d. No retry war.**
  - Any event-handler exception becomes a non-terminal session loss. One invalid contact name can make every login loop roughly once a second (S-01).
  - Native download threads survive `close`/`shutdown`. After displacement, downloads keep writing to staging, and a later resume can double-write the same `.part` (N-01).
- **0e. Friday Drops policy.** A Friday rollover lets claims exceed 3 per week and charges the wrong week (S-08).

**Tier 1: listening truth.** Items 1–6 below block any visual work.

1. **Derive deck visuals from program state, not from `needleDown`.** Platter speed, the Start/Stop state description, crackle and the status line must come from one reconciled state. Today the platter target is `needleDown && !held` (`Turntable.kt:219`), and the TalkBack state is `"Playing"` whenever `needleDown && !held` (`Turntable.kt:395`). An external pause leaves both claiming playback.
2. **Fix the speed and volume leaks in `Deck` before adding ritual.** These include `holdPlatter` leaving playback at `MIN_SPEED` (0.25×), and a cancelled `rampJob` leaving player volume at 0 or partial. See section 3, R-02 and R-03.
3. **Gate every surface and mechanical voice behind Pure and the surface-sound preference, including voices already playing.** `lift()` and `runout()` ignore both Pure and the crackle preference (`Deck.kt:113,233,323`; `VinylSfx.kt:31–35,56–65`).
4. **Make Flip a visible, labelled and accessible control on Home.** Remove the hidden quick-tap flip, and make side end offer "Flip to Side B", not "Play". Side end currently shows **Play** in the strip (Observed: `SIDE A END` with a Play button in `home-0.4.1.png`). That Play replays the same side (`Deck.startStop` → `needleDrop(0f)`).
5. **Decide the album policy (section 6).** v2 adopts enforced album-only playback with in-record cueing. Search may only cue a band inside its parent record. Next and previous stop at side edges. Nothing crosses a side or album without the listener acting.
6. **Move record validation off the main thread and stop failing silently.** `Deck.load` runs `FlacPolicy.filterAlbum` synchronously (an SAF query plus a stream open per track) from UI callbacks (`MainActivity.kt:142`, `ListeningRoom.kt:369`). It then returns without any message if every track fails (`Deck.kt:169`).
7. **Downloads recovery (reported screenshot).** Show one recovery notice with **Reconnect here** inside Downloads. After a reconnect, offer one explicit "Resume N interrupted" that leaves user-paused rows alone. Count paused-for-attention rows in the header badge. Make Cancel truthful about the private partial bytes it keeps (R-12).
8. **Replace the DJ-deck model with a single coherent mechanism (section 7).** Use a motor switch, a cue lever, a 33/45 selector and a fine-speed vernier with a strobe. Remove the prev/next keys and the decorative power knob.
9. **Rewrite the Pure copy.** "Nothing touches the signal" (`Panels.kt` Pure toggle) is untrue today. The needle-drop volume ramp runs even in Pure (`Deck.kt:216–226`), and processing still passes through the float sink. Use "No processing: no EQ, ReplayGain, surface sound or speed change."
10. **Have one notice owner.** Two toasts render the same `state.message` with 4.5 s and 7 s timers (`ListeningRoom.kt:472–497`, `RunoutScreen.kt:127–130`).

---

## 3. Source corrections to v1 and the spec

| v1/spec item | Claim | Recheck | Label | Correction |
|---|---|---|---|---|
| UX-01 | The displacement warning repeats "globally and per file"; Resume is disabled; there is no Reconnect | `pauseOwner` writes the session reason into **every** active row's `error` (`SoulseekRepository.kt:166,416`; `SoulseekDatabase.kt:57`). Downloads also shows `NetworkNote` (`SoulseekPanels.kt:165`). The **global** banner (`RunoutScreen.kt:71–76`) shows only the repository `_error`, which displacement does not set. Resume is `enabled = signedIn`, and `signedIn == connected` (`SoulseekModels.kt:20`; `SoulseekPanels.kt:181`). The Transfers panel has no `ConnectionNotice`. | Source-confirmed; screenshot Reported | The repetition is panel note plus every row, not global. The header pill reads **"Account !"** for the `ERROR` state (`RunoutScreen.kt:139`). It never says "Paused". |
| UX-02 | Dismiss clears `_error`, but the session error persists | `NetworkNote` uses `soulseekError ?: session.error` (`SoulseekPanels.kt:41`). Dismiss calls `clearError()`. | Source-confirmed | Correct. Mark session status as non-dismissable status, not as an error with a Dismiss button. |
| UX-03 | Archive Resume/Retry calls the repository resume, which rejects it | `MainActivity.resumeDownload` (`MainActivity.kt:360–370`) sends Archive rows to `claimDrop` for the current week. Outside the current week it shows a message. The repository `check` (`SoulseekRepository.kt:246`) is only a backstop. | Source-confirmed | **v1 is wrong about the call path.** The real defect is the label and expectation: a generic "Resume" silently re-runs a full Friday Drop claim, or refuses because the week changed. Label it "Claim again from Friday Drops". Whether `DropsRepository.claim` reuses partial Archive files is unverified (Hypothesis). |
| UX-04 | Filenames render as an uppercase 9.5sp `SectionTitle` | `SoulseekPanels.kt:168`; `Controls.kt:170–176` | Source-confirmed | Correct. |
| UX-05 | Non-lazy transfer list; badge hides paused rows | `SoulseekPanels.kt:167`; `RunoutScreen.kt:68` counts only QUEUED, DOWNLOADING, PUBLISHING and VALIDATING | Source-confirmed | Correct. With every row paused (the reported screenshot) the badge reads **↓ 0**. Observed in the committed image as `↓ 0` with no transfers. |
| UX-06 | Pure does not gate lift/runout; one-shots are untracked | `Deck.kt:113,233,323`; `VinylSfx.kt:31–35,56–65` | Source-confirmed | Extended: the **crackle-off preference** does not gate `needleDrop`, `lift` or `runout` either. "Crackle off" only stops the bed. `onPureChanged(true)` (`Deck.kt:358–362`) also leaves `rampJob` running, so the volume ramp still completes. |
| UX-07 | Runout one-shot plays while the state lifts immediately | `Deck.kt:111–115` | Source-confirmed | Correct. Also: `runout()` is a fixed 3.9 s, two-turn buffer at 33⅓ (`VinylSfx.kt:162–173`). It ignores 45 rpm and pitch. |
| UX-08 | Fixed 420 ms settle, fixed crackle loop | `Deck.kt:221–226`; `VinylSfx.kt:22,43,175–181` | Source-confirmed; audibility Hypothesis | Correct. The bed plays at `0.35 × 0.5 = 0.175` track volume, with pop gain 0.11 and 9 pops/s. Hearing it as "does not do much" is the user's report, which I did not audition. The program's first ~200 ms are also attenuated by an 8-step volume ramp (`Deck.kt:223–226`), which v1 and the spec both forbid. |
| UX-09 | Hypothesis: the platter follows the needle, not playback | `Turntable.kt:219`; `Deck.kt:104–108` updates only `isPlaying` | **Source-confirmed path**, runtime not reproduced | Upgraded. The path is concrete, and section 4 adds the double-press consequence (R-01). |
| UX-10 | Power knob is decorative; deck targets are small | `Turntable.kt:481–490` (lamp lit when any album is loaded); `122–128` geometry | Source-confirmed; sizes computed | Computed from geometry and the committed image (plinth about 313 dp wide, so h ≈ 244 dp): 33/45 keys ≈ **24 × 10 dp**, prev/next ≈ **22 × 11 dp**, Start/Stop ≈ **33 × 26 dp**. These are estimates, not device measurements. |
| UX-11 | Settings are scattered | Confirmed across `RunoutScreen.kt:80–83,115`, `SoulseekAccountPanel`, Library | Source-confirmed | Correct. |
| UX-12 | Two message layers | `ListeningRoom.kt:472–497` (4.5 s), `RunoutScreen.kt:127–130` (7 s) | Source-confirmed | Correct. Both call `dismissMessage()`, so the 4.5 s timer clears both early. |
| UX-13 | Pure/hi-res copy over-promises | Pure copy "Nothing touches the signal…" | Source-confirmed copy | Correct. See required change 9. The DSP sample-format question is settled and not reopened. |
| UX-14 | "All sources" excludes Archive | `Discovery.kt:10` | Source-confirmed | Correct. Rename to "Device + Soulseek". Do not add Archive search. |
| UX-15 | Hypothesis: synchronous validation on load | `Deck.kt:169` → `FlacPolicy.kt:54–62` (query plus stream per track), called from `MainActivity.kt:142` and `ListeningRoom.kt:369` on the main thread. `PlaybackService.isAllowed` re-reads every item (`PlaybackService.kt:73–75`) in session callbacks. | **Main-thread I/O source-confirmed**; stall size Hypothesis | Upgraded, and the validation is **duplicated**. |
| Spec §2 | "Decorative power hardware is either labelled as a lamp or removed" | The lamp means "a record is loaded" | Source-confirmed | Remove it. The motor switch replaces it (v2). |
| Spec §4 | "Tracks remain addressable" | `nextTrack` crosses sides and wraps from the last side to A (`Deck.kt:298–303`) | Source-confirmed | Constrain this to within the side (section 6). |
| Spec §7 Downloads | "Existing v1 rows have only state + free-text error" | `pause()` writes `error = null` (`SoulseekRepository.kt:241`). Interruptions write a non-null reason (`:101,166`), and enqueue failure writes a non-null message (`:226`). | Source-confirmed | Without parsing any strings, `PAUSED ∧ error == null` means paused by the user and `PAUSED ∧ error != null` means paused by the system. This is a usable interim signal, though a typed column is still better (section 12). |

---

## 4. New source findings: critical bugs versus missing flows

### 4.1 Critical bugs: wrong audio, wrong state or a lost listener decision

| ID | Finding | Evidence | Label | Consequence |
|---|---|---|---|---|
| R-01 | **External pause, then in-app Start, needs two presses.** A notification, headset or focus pause leaves `needleDown = true` and `held = false`. `startStop()` then runs `holdPlatter()` (`Deck.kt:287–295`), which ramps to a pause that is already in effect. The strip label stays "Play" (`RunoutScreen.kt:162`). The second press calls `releasePlatter()`. | `Deck.kt:104–108,287–295` | Source-confirmed; not reproduced | The first tap appears dead, and the platter keeps spinning throughout. |
| R-02 | **Resume after a platter hold plays at 0.25×.** `holdPlatter` ramps `playbackParameters` to `MIN_SPEED` and pauses. Only `releasePlatter` restores the speed. A notification or headset Play calls `player.play()` directly, at 0.25×, and `onIsPlayingChanged(true)` restarts crackle. | `Deck.kt:240–256`; `PlaybackService.kt:49` (no command interception) | Source-confirmed; not reproduced | Audible wrong-speed playback from system controls. |
| R-03 | **Volume can stay at 0 or partial.** `dropAt` sets `volume = 0` and ramps it up after 420 ms. A `liftNeedle`, `holdPlatter` or `load` within about 620 ms cancels the job; `flip` and `eject` do the same through `liftNeedle`. Nothing restores the volume. `releasePlatter` never touches volume, and the next external Play is silent or quiet. *(Corrected in pass 2: `jumpTo` is not a trigger, because it ends in `dropAt`, which restarts the ramp. The next in-app drop restores volume; an external Play or a platter release does not.)* | `Deck.kt:213–227,232,244` | Source-confirmed; not reproduced | Silent "playing" state. |
| R-04 | **Pure still alters the opening.** The 0→1 volume ramp over the first ~200 ms runs whether or not Pure is on. | `Deck.kt:216–226` | Source-confirmed | It contradicts the Pure promise and attenuates the first transient. |
| R-05 | **The only way to flip on Home is a hidden gesture.** The production shell renders `RoomScreen(showChrome = false)` (`RunoutScreen.kt:84`), so the hint "tap the record to flip it" (`ListeningRoom.kt:591`) never appears. The record has no semantics. Flip is reachable only through a <170 ms tap on the record (`Turntable.kt:346–351`). | as cited | Source-confirmed; Observed (no Flip in `home-0.4.1.png`) | TalkBack users cannot flip on Home. A stray tap on a stopped record flips it and resets the position. |
| R-06 | **Side end offers "Play", which replays the same side.** `startStop` with `sideFinished` calls `needleDrop(0f)` on the current side. | `Deck.kt:291`; Observed `SIDE A END` + Play | Source-confirmed | It defeats the "stay for a side, then flip" ritual. |
| R-07 | **Next crosses sides and wraps.** On the last band, `nextTrack` cues the next side and drops the needle, so it autoplays. On the last side it wraps to Side A. | `Deck.kt:298–303,317–329` | Source-confirmed | Album-only violation. This is effectively an endless loop by key press. |
| R-08 | **Search plays immediately.** "Play song" or "Play album" mounts the record and drops the needle at once. | `DiscoveryScreen.kt:61–65` → `Deck.playTrack` (`Deck.kt:178–186`) | Source-confirmed | It conflicts with "search only locates a groove". |
| R-09 | **Replacing a playing record needs no confirmation.** A drag onto the platter or "Put on the platter" calls `load()`, which pauses and replaces immediately. | `Deck.kt:168–175`; `ListeningRoom.kt:366–370` | Source-confirmed | The listener can lose a side mid-play by accident. |
| R-10 | **A failed load is silent.** If no track validates (for example after a revoked SAF grant), `load` returns. The dragged disc animates onto the platter, then disappears, with no message. | `Deck.kt:169`; `ListeningRoom.kt:366–370` | Source-confirmed; not reproduced | A dead-end ritual. |
| R-11 | **Focus loss and noisy events desync the visuals.** On unplug and on *permanent* focus loss, Media3 sets `playWhenReady = false`. The deck keeps `needleDown`, so the platter spins and the arm stays down. *Transient* focus loss is a playback **suppression** (`playWhenReady` stays true and Media3 resumes on its own): the desync is temporary, but pressing Start during it calls `holdPlatter` and turns a transient pause into a permanent hold. One-shots already queued (runout 3.9 s) keep playing on their own `AudioTrack`. *(Corrected in pass 2; Media3 1.9.4 `AudioFocusManager` L384–391.)* | `PlayerFactory.kt:49`; `VinylSfx.kt:56–65` | Source-confirmed path; the speaker-after-unplug case is a Hypothesis | Same class as R-01, plus a possible effect tail on the speaker. |
| R-12 | **Cancel keeps private partial bytes after displacement.** Displacement clears `activeTransfers` (`SoulseekRepository.kt:161`). Cancel then skips the native `cancel` (`:256`) and never deletes `noBackupFilesDir/soulseek-staging/<id>`. The vendored `PartFile` keeps `.part` files for resume (`download_peer.rs` `PartFile::open`/`finish`). A later **Retry** resumes from those bytes. | as cited | Source-confirmed; not reproduced | "Cancel" does not discard, and the staging space is never reclaimed. |

### 4.2 Missing flows: product gaps, not faults

| ID | Gap | Evidence | Label |
|---|---|---|---|
| G-01 | No persistence for the mounted record, side or position. Process death returns an empty deck. | No `sideIndex` or `positionMs` writes outside `Deck`/`Turntable`. Deck prefs store only `crackle` (`Deck.kt:96–98`). | Source-confirmed |
| G-02 | No accessible tonearm, band cueing or flip. Pitch has a state description but no adjust action. | `Turntable.kt:404–431`, and no semantics on the arm or record | Source-confirmed |
| G-03 | The pulled disc can only be placed by dragging. The accessible alternative is the Library card, a different path to the same intent. | `ListeningRoom.kt:348–381`; `RunoutScreen.kt:203` | Source-confirmed |
| G-04 | No lead-in or runout states. No ritual-length preference. One fixed crackle level. | `Deck.kt`, `VinylSfx.kt` | Source-confirmed |
| G-05 | No dark theme. A listening room at night is a primary use. | No `isSystemInDarkTheme` in `app/runout` | Source-confirmed |
| G-06 | No "Resume all interrupted" after a reconnect. Five paused rows take five taps, while Resume stays disabled until the reconnect. | `SoulseekPanels.kt:180–183` | Source-confirmed; Reported screenshot |
| G-07 | Single-file Soulseek downloads become one-track "records" with no completeness marker. | `publishSoulseek` (`SoulseekRepository.kt:503–526`) builds an album from whatever has arrived | Source-confirmed |
| G-08 | Messages are a non-lazy, unbounded `forEach` inside a scroll. | `SoulseekPanels.kt:231` | Source-confirmed |
| G-09 | No visible status sentence on Home. The pixel screen is the only status, and the screen reader gets that same text. | `RunoutScreen.kt:84`, `Turntable.kt:404–409` | Source-confirmed |

---

## 5. Missing state transitions

The spec's coordinator dimensions are right but under-specified. These transitions have no defined outcome today, or a wrong one.

| From | Event | Today | Required |
|---|---|---|---|
| Needle down, playing | System pause (notification, headset, permanent focus loss, noisy) | `isPlaying = false`; needle, platter and arm stay "down" (R-01, R-11) | `Program = PAUSED(reason = external)`. Motor follows the policy (stays on, label "Paused from headset"). One press resumes. |
| Needle down, playing | Transient focus loss (call) | Temporary visual desync; Start during it causes a permanent hold | `SUSPENDED(CALL)`: needle stays down, motor keeps running, voices stop, no deck command is issued, and Media3 auto-resumes. *(Added in pass 2.)* |
| Side ended or finished | Notification or headset Play | Media3 `handlePlayButtonAction` seeks to the default position and plays: the band replays with the arm up (P-07) | Play is unavailable to external controllers in RUNOUT, AWAITING_FLIP and FINISHED. *(Added in pass 2.)* |
| Needle up | External Play | Program plays with the arm parked and the position frozen (P-07) | External Play lowers immediately, at the current position, and is shown as such. *(Added in pass 2.)* |
| Held (platter stopped) | System play | Plays at 0.25× (R-02) | Normalise speed through a ramp outside Pure, then clear `held`. |
| Settling (≤620 ms after a drop) | Lift, hold, flip, eject, load or jump | Volume stuck (R-03) | Every cancellation restores the volume, or the volume ramp is removed entirely (v2 removes it). |
| Playing the last band | Natural `STATE_ENDED` | One-shot plus an immediate lift (UX-07) | `RUNOUT(revolutions = n)` → `AUTO_LIFT` → `AWAITING_FLIP`, or `RECORD_FINISHED` on the last side. |
| Awaiting flip | Play / Start | Replays the same side (R-06) | Not offered. The primary action is "Flip to Side B". "Play this side again" is secondary. |
| Last band of a side | Next | Cross-side autoplay or wrap (R-07) | Disabled, announced as "Last band on Side A". |
| Any state | Load another record while the needle is down | Immediate replace (R-09) | A confirmation step, after which the old record visibly returns to its sleeve. |
| Placing | Validation fails | Silent (R-10) | The record returns to its sleeve with a repair note. |
| Any state | Pure on during a runout tail or ramp | The tail and ramp continue (UX-06) | All voices stop within one buffer. The ramp is cancelled with full volume. |
| Process death | Restart | Empty deck (G-01) | Restore `CUED(record, side, position)` after URI checks, with no sound. |
| Displaced (`ERROR`) | Reconnect succeeds | Rows stay PAUSED with the old reason; per-row Resume | One "Resume N interrupted" offer. User-paused rows (`error == null`) are excluded. Nothing is automatic. |
| Displaced | Cancel a paused row | `.part` kept; marked CANCELLED (R-12) | Confirm "Discard 53.3 MB kept for this file?", then delete staging. Or offer "Keep partial for later". |
| Archive row PAUSED | "Resume" | Re-claims silently, or refuses outside the week | Label it "Claim again from Friday Drops". Show the week caveat before the tap. |

---

## 6. Album-first or enforced album-only: the decision

**Recommendation: enforced album-only playback, album-first discovery.**

Playback rules:

- The only playable unit is a **mounted record side**. No code path plays audio without mounting its album.
- Within a mounted side, bands stay addressable. Use cue-to-band, next or previous within the side, and arm drag. Removing these would be an accessibility regression, not authenticity, so they stay.
- Next and previous **stop at the side edges**. Changing sides always requires a flip. Changing records always requires a deliberate placement.
- **No** queue, shuffle, autoplay of the next side, autoplay of the next album, or a "radio" of any kind.
- Natural side end leads to runout, then lift, then waiting. The final side ends with **Play this side again** and **Put away**.

Discovery rules:

- A song result's primary action is **"Open record at A3"**. It mounts the sleeve view with band A3 highlighted. **"Place and cue A3"** puts the record on the platter with the arm parked over A3 and the needle **up**. The listener lowers it.
- **Remove "Play song" and "Play album" from search** (R-08).
- Remote (Soulseek) song hits lead to the folder preview, which is where a record comes from. Single-file download stays inside the preview as "Choose files", and the resulting crate entry says "Partial record · 1 of 12 files" when membership is known, or "completeness unknown" otherwise (G-07).

MediaSession and system controls:

- Play and pause map to lowering and lifting the cue.
- Next and previous are clamped to the side. The player already holds only the current side's items (`Deck.cueSide`), so the natural behaviour is close. The UI-level cross-side `nextTrack` is what must change.
- The notification never flips or advances records.

Why "only" and not "first": the user's vision is "only play albums, just like in 1950s only vinyl". Album-first would still leave the single-song autoplay paths that exist today. Enforcing the record as the container is cheap in this codebase, since it is mostly deletions in `Deck.nextTrack` and `Deck.playTrack`. It also keeps track access, which accessibility needs.

---

## 7. Challenge to the walnut/enamel Runout Console

v1's Direction B and the spec's Runout Console have four problems.

1. **It is a material swap on the wrong machine.** The current deck is a direct-drive DJ turntable: S-shaped arm, ±8% pitch fader, prev/next keys, square Start/Stop, strobe dots and "GLASS DRIVE RG-1" (`Turntable.kt:100–105,471–495`; Observed). Wrapping it in walnut and enamel gives a 1970s DJ deck in 1950s furniture. "A spinning disc on a generic player would do the same job" applies directly.
2. **Realism is spent where the hand isn't.** Walnut grain, enamel and machined rims are case decoration. Believability comes from the moving parts and their timing: a heavy platter that takes time to reach speed, a cue that descends, an arm that crosses the lead-in and rides the runout, and a record that must be turned over.
3. **It fights the pixel/ASCII layer.** Furniture realism next to pixel readouts reads as theme salad. The spec's own hierarchy ("Physical / Printed / Instrument") is the better idea, but the Console puts physical realism on the case, the largest surface.
4. **Cost and scaling.** Textured wood and enamel need bitmap assets, a dark-mode variant and contrast work behind small labels. They also add GPU cost on top of an existing Backdrop lens/blur pipeline (`Turntable.kt:287–304`).

**Counter-proposal (v2 Direction D, "Printed hi-fi"):** model the deck on a **1950s transcription turntable mechanism**: idler-drive behaviour, a heavy platter, a speed selector, a fine-speed vernier with a strobe window, a cue/lift lever, a straight or gently curved arm with an arm rest, and a motor switch. Draw the **top plate flat**: one enamel colour token plus ink linework and printed legends, like an owner's manual plate. Reserve full rendering for **the record, its label, the arm and the platter rim**, the things that move and that the hand touches. Pixel and ASCII appear only in the **instrument window** and on catalogue strips.

This keeps "looks and feels like a real vinyl player" without walnut texture assets. It is cheap in Canvas, inverts cleanly for a night theme, and every control on the plate becomes a real command (v2 §3). Fine speed stays (the user requires speed control), now as a period-plausible vernier, not a DJ fader.

Walnut remains an optional **plinth edge** token (a 6–10 dp band), not the identity.

---

## 8. Critique of the spec's interaction model

- **Motor + Cue + "Start listening" macro means three controls for one intent.** v2 keeps two physical controls (Motor, Cue lever). It makes **lowering the cue with the motor off start the motor first** ("Motor starting… lowering"). This removes the third macro button and its synonyms problem.
- **Ritual length, surface sound and Pure as three independent preferences:** agreed. Add one rule: in Pure, lead-in and runout still exist as **states** with their visual timing (the ritual-length preference), but are silent. Pure is about sound, not choreography.
- **Sleeve choreography:** agreed, with one change. Validation runs **during** the placing animation, off the main thread, and the record commits only when it succeeds (R-10, UX-15).
- **"Current record stays mounted while browsing":** agreed. The global "On the turntable" strip must report the reconciled state (R-01).
- **Black vinyl as default:** disagree with a silent change. `Album.pressing` derives from quality when `disc == null` (`Models.kt:107–118`), so changing the default restyles every record without an explicit style. Keep it, and add a Display choice: "Pressing look: by quality / always black".
- **Downloads tabs Active/Attention/Completed:** keep them, but put the **recovery card above the tabs**, never inside one.
- **The Settings shape** is better in the spec (four groups plus About) than in v1 (six sections plus an inventory). v2 keeps the spec's shape and removes the Home **Sound** and **Crackle** pills (`RunoutScreen.kt:80–83`).
- **The prototype HTML board** mentioned in spec §9 does not exist in the repository. Do not cite it as evidence.

---

## 9. UX holes by flow

- **First use → first record:** Library "Put on the platter" (`RunoutScreen.kt:203`) and the Home crate pull/drag are two different rituals for one intent. Unify them as **Open sleeve → Place on platter**, the same sequence from Crate, Discover and download completion.
- **Listening:** there is no readable status sentence on Home (G-09). The pixel screen is the only status, and it truncates by scrolling.
- **Side end:** Play replays the side (R-06), and nothing guides the listener to flip (R-05).
- **Speed:** 33/45 keys of about 24 × 10 dp, a pitch fader with no accessible adjust, and Pure silently disables both with no explanation on the deck (`pitchEnabled = !pure` only dims it).
- **Downloads:** see sections 5 and 13.
- **Settings:** account, folders, sharing and background mode live inside an Account overlay that also re-enters setup. Sound and Crackle are Home pills. The library folder is in Library. These have no single entrance.
- **Messages:** an unbounded list, and Send is disabled while disconnected with no saved draft (the draft is kept per `rememberSaveable(peer)`, but there is no "will send when connected" path, which is correct: do not queue sends silently).
- **Errors:** `friendly(e)` surfaces raw exception messages in the global banner.

---

## 10. Accessibility

- **Deck hit targets** are below 48 dp (computed in section 3, UX-10). v2 moves every deck command into a **control tray** of ≥48 dp labelled buttons under the deck. Plate art becomes a mirror of state, never the only control.
- **Missing semantics:** tonearm, record, sleeve, pulled disc and the crate sleeves (`Crate.kt:168–174` has no role or label). The pitch has no `setProgress` (`Turntable.kt:410–431`).
- **The state description lies** while externally paused (`Turntable.kt:395`).
- **Text sizes:** `Room.Label` is 11sp and `SectionTitle` is 9.5sp (`Room.kt:26`; `Controls.kt:173`). Plate legends are canvas text at about 5–6sp, and the canvas doesn't respect font scale for live status. v2: all live status appears as real text outside the canvas.
- **Motion:** no reduced-motion handling exists. Required: respect the system animator duration scale. Placement and flip become crossfades with an unchanged audio policy.
- **Sound-free use:** lead-in and runout must have text states ("Lead-in · 3 s", "Runout groove · lifting") so a listener with effects off, or a deaf user, gets the same information.
- **Contrast:** the spec's numbers re-verify exactly (ink 12.72, green 6.30, error 5.86, amber 3.20 on `#F2E6CE`). v2 adds a darker amber `#8A4B12` (5.49:1) for small text, and a night palette (section 11 of v2).

## 11. Performance

- **Per-frame allocation** on the deck canvas while spinning: `DeckGeometry(size)` per draw (`Turntable.kt:362`), a 4 × 180 `Offset` list for the strobe (`:519–524`), and roughly `cols × 12` `drawRect` calls for the pixel screen (`:710–717`). Hypothesis: measurable jank on low-end devices. Profile with FrameMetrics or Macrobenchmark before and after.
- **Backdrop lens/blur** on the plinth (`:287–304`), every `GlassPill`, the panel sheets and the toast. Hypothesis: GPU cost. v2's flat plate removes the plinth lens.
- **Main-thread SAF I/O** on load and in session callbacks (UX-15), duplicated.
- A **200 ms position poll** runs for the life of the process (`Deck.kt:141–154`). It is cheap, but it should run only while a record is mounted.
- **Non-lazy lists** for transfers and messages (UX-05, G-08).
- Keep the documented 10k local / 5k remote search gates.

## 12. Migration and privacy

- **Deck persistence (new):** store `{albumId, sideIndex, positionMs, speed, finePitch}` in the existing `deck` SharedPreferences, or a small DataStore. Restore as `CUED`, never playing. Re-validate URIs off the main thread. Soulseek album ids are digests that include the peer username (`SoulseekPolicy.albumId`). They aren't readable, but exclude the deck file from backup to match the existing backup posture for Soulseek data.
- **Ritual preferences:** migrate the boolean `deck.crackle`. `true` becomes Surface "Subtle"; `false` becomes "Off". Ritual length defaults to Short for existing users and Full for new installs. Keep reading the old key for one version.
- **Transfer pause reason:** interim, derive from `error == null` (section 3). Long term, add Room v2 with a nullable `pauseReason` column (`USER`, `SESSION`, `INTERRUPTED`, `ERROR`), where null means legacy-unknown, plus an explicit `Migration(1,2)` and an exported schema. Never use destructive migration (DEVELOPMENT.md).
- **Downloads cancel:** deleting private staging is a data-loss decision. Confirm it, and state the byte count.
- **Pressing default:** no silent visual migration (section 8).
- **Copy:** use "another client", not "another device" (the reported alert says "device"). The source label says "another client" (`SoulseekModels.kt:8`). The native reason string may override it; this is unverified.
- **No new network calls, accounts or telemetry.** Archive stays in Friday Drops.

## 13. Feasibility

| Need | Approach in the current stack | Risk |
|---|---|---|
| Reconciled state (R-01, R-02, R-11) | Keep `Deck` as the coordinator in `AppGraph` (the service runs in-process; the manifest has no `android:process`). Reconcile from `Player.Listener` (`onIsPlayingChanged`, `onPlayWhenReadyChanged` with reason, `onPlaybackParametersChanged`). Treat a pause the deck did not initiate as `PAUSED(external)`. Optionally add a `ForwardingPlayer` in `PlaybackService` that normalises speed on `play()`. | Low. Avoid command loops: the deck's own commands also pass through the session. |
| Lead-in that holds the program at sample 0 | Prepare on cue. On lowering: start the bed voice, keep the program paused at 0, then call `play()` at **full volume** when both the lead-in timer has elapsed and the player is `STATE_READY`. If the player isn't ready, extend the bed. | Low–medium. The bed's `AudioTrack` and ExoPlayer start are not sample-synchronous (tens of ms, Hypothesis). This is acceptable for surface noise. |
| Runout tied to RPM | Generate one revolution of locked-groove texture procedurally, at a period of `60 / (rpm × factor × fine)` seconds. Loop n times on a tracked streaming voice, then lift. Trigger on `STATE_ENDED` (after sink drain in Media3; Hypothesis on exact latency). | Low. |
| Cancellable, gated voices | Replace fire-and-forget `playOnce` with a `SurfaceVoices` owner that holds handles, uses `MODE_STREAM` with short envelopes, and obeys Pure, preference, focus and noisy events. Make it injectable for tests (an interface plus a fake). | Medium: it touches lifecycle, so it needs fake-clock tests. |
| Flip as a real object | `graphicsLayer { rotationY }` on the existing `drawRecord` canvas, with the label art switching at 90°. The state changes only at the end. | Low. |
| Band-snapping arm | Use the existing `DeckGeometry.alphaForPointer` and `fractionForAlpha` plus `Side.trackBoundaries()`. Snap within ±1.5° of a boundary. Haptic `CLOCK_TICK` on crossing. | Low. |
| Accessible deck | A tray of `Pill`s, plus custom semantics actions on the arm ("Cue next band", "Cue previous band", "Lower here"), plus `setProgress` on fine speed. | Low. |
| Off-main validation | `Deck.load` becomes `suspend`/async with `Dispatchers.IO`, a `PLACING` state and a generation check. `PlaybackService.isAllowed` stays as the trust boundary, but should move off the main thread too (`onSetMediaItems` already returns a `ListenableFuture`). | Medium: the future must be completed asynchronously. |
| Downloads recovery | UI plus repository additions (`resumeInterrupted(owner)`, `discardPartial(id)`), and a native `cancel`/`remove_download` where an entry exists. No new protocol command. | Low. |

The stack is Media3 1.9.4 (`app/build.gradle.kts:93–94`). None of the above needs a new framework, a backend or a native-protocol change.

## 14. Accounting for the Downloads screenshot (Reported)

**Reported state:** completed FLACs land in the crate and the music folder. A red alert says the ID is signed in on another device. One track is paused at 56% (53.3 of 93.7 MB). Four are paused at 0%. Every row has Resume and Cancel.

How the source explains it:

- **Displacement is terminal by design.** It sets `ERROR`, sets `desired = false`, turns off auto-connect and schedules no retry (`SoulseekConnectionManager.kt:152–163`). That is correct, and it means no retry war.
- **All five rows were active, so `pauseOwner` paused them with the session reason** (`SoulseekRepository.kt:166`). The 0% rows were most likely QUEUED in peer queues (Hypothesis).
- **Resume is disabled** until connected. **Cancel is enabled**, which is the dangerous combination given R-12.
- **The 53.3 MB survive.** `.part` sits in `soulseek-staging/<id>`. After a reconnect, Resume issues a fresh native `download` into the same directory, and `PartFile::open` appends when `on_disk < size`. This is Source-confirmed. Whether the peer honours the offset is not verified.

Design implications, carried into v2 §9:

- One pinned recovery card with **Reconnect here** and **Keep listening offline**.
- Rows read "Waiting for your connection · 53.3 MB kept".
- After a reconnect: a **Resume 5 interrupted downloads** button, with no automatic resume.
- Cancel confirms the discard and actually deletes the staging data.

## 15. Still unverified

- Every runtime behaviour in sections 4 and 5. None was reproduced here, and the next step is red-capable tests (v2 §14).
- Crackle audibility, lead-in feel, runout timing and loudness. These need a listening session on real headphones.
- The native reason text for displacement ("device" or "client").
- Peer acceptance of resume offsets, and whether Archive claims reuse partial files.
- GPU and frame cost of the current Backdrop deck.
- The behaviour of the 24-bit/hi-res path under Pure, beyond the settled float conversion. Out of scope.

---

## 16. Second-pass audit register

Six read-only subagents each audited one area and reported findings labelled SC (source-confirmed: the exact code path was traced) or Hyp (hypothesis). None of them edited, built or ran anything. The native auditor did not run `cargo test`: `rustup` lists stable, 1.85, 1.87 and 1.88, but not the pinned 1.91.0.

A **✓** in the last column means the lead reviewer re-read the cited lines and confirmed the mechanism. The other entries are the subagent's evidence as reported. They are plausible and specific, but must be re-verified red-first before fixing. IDs:

- `P` playback/sound
- `L` library/records/sharing
- `S` Soulseek/Drops/Discovery
- `N` native bridge
- `U` UI/onboarding/accessibility
- `V` plan review (applied to v2, not listed here)

### 16.1 Tier 0: data loss, crash, privacy, licence, retry war

| ID | Finding | Evidence | Label | ✓ |
|---|---|---|---|---|
| L-01 | `readValidatedList` drops every album or track that fails validation *at that moment*, then rewrites the JSON. The `.before-flac` backup is made only once. This runs at startup for pressings, received records, Drops and the library. An unmounted card, a revoked grant or a deleted source record **permanently removes the user's pressings**. | `library/AlbumJson.kt:95–110`; `pressing/Pressing.kt:52`; `sharing/ShareRepository.kt:41` | SC | ✓ |
| L-02 | A rescan that cannot list the root still writes `found` (empty) to the shelf and to `library.json`. | `library/LibraryRepository.kt:93–99`; `LibraryScanner.kt:74,114–124` | SC | ✓ |
| L-16 | `abs(title.hashCode()) % 6` is negative for `Int.MIN_VALUE`, which throws `IndexOutOfBoundsException` during rendering. | `ui/RecordArt.kt:85` | SC | ✓ |
| L-07 | ReplayGain parses with `toFloatOrNull()`: NaN, ±Infinity and +60 are accepted and applied unclamped. | `library/TagReader.kt:487`; `sound/DspChain.kt:19`; `SoundEngine.kt:84–90` | SC; audible effect Hyp | ✓ |
| L-08 | The `.vinyl` manifest and checksums are read with `readBytes()` without a size limit; only `Exception` is caught, so an OOM escapes. `incoming-*` staging is never swept. | `sharing/VinylPackage.kt:134–135`; `ShareRepository.kt:93–99` | SC | |
| L-04 | **Privacy.** Online cover lookup defaults to true and is never configured. Artist and title go to musicbrainz.org and coverartarchive.org/archive.org after every scan, with no negative cache. | `LibraryRepository.kt:29,101–112`; `ArtworkRepository.kt:51–69` | SC | ✓ |
| L-05 | **Privacy.** `.vinyl` export keeps `source` and `from`; for Soulseek records, `from` is the peer username. The profile `u` is the Soulseek login. | `AlbumJson.kt:23,30`; `SoulseekRepository.kt:525`; `RunoutApp.kt:91` | SC | |
| L-06 | **Licence.** An imported record keeps the sender's `owned` flag, so `audioShareable` lets the receiver forward the audio. | `ShareRepository.kt:130–141`; `Models.kt:105` | SC | ✓ |
| U-06 | **Backup.** Only credentials, `account.xml` and `soulseek.db` are excluded. Folder URIs and the onboarded flag are restored without their SAF grants, so setup is skipped and every folder fails on a new phone. | `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | SC; restore effect Hyp | ✓ |
| S-01 | **Retry war.** Any handler exception in the poll loop becomes a non-terminal `PROTOCOL_ERROR`, which retries with backoff reset to 1 s. On "connected", `command("contact")` throws on the first invalid contact (the bridge rejects names that fail `valid_username`). Incoming DMs add the server-supplied sender **without validation**. One such DM gives a reconnect loop on every login. | `SoulseekConnectionManager.kt:116–131,147,165`; `SoulseekRepository.kt:413,458`; `native/bridge/src/lib.rs:287–290` | SC path; trigger Hyp | ✓ |
| N-01 | Native `stop()` clears the bridge's transfer map, but `Client::shutdown` cancels only uploads. Download threads keep reading into `.part` files, and a paused one sleeps forever. A resume in the next session can produce two writers on one `.part`. | `lib.rs:97–108`; `vendor/.../client/connection.rs:51–68`; `peer/download_peer.rs:532–555` | SC path; interleaving Hyp | ✓ |
| S-08 | `claim` checks against the *cached* week but counts and records claims under the *current* index. Across a Friday rollover, the old week's items can be claimed beyond 3. | `drops/Drops.kt:112–117,177–179,201` | SC | ✓ |
| U-13 | Back on the first-run welcome screen calls `onFinish()`, which permanently marks onboarding complete. | `ui/SetupScreen.kt:33`; `RunoutScreen.kt:60` | SC | ✓ |
| U-02 | The Pressing draft (up to 40 tracks plus a picture) is cleared right after `press()`, before the async result. It is also lost on Close or Back with no prompt. | `ui/Panels.kt:390–397`; `MainActivity.kt:372–379` | SC | ✓ |
| U-03 | The message text is cleared unconditionally; the 4,096-byte check throws before any FAILED row is saved. | `SoulseekPanels.kt:240–241`; `SoulseekRepository.kt:279–280` | SC | |
| U-04 | "Remove" on a received record deletes files with no confirmation or undo. | `ListeningRoom.kt:416`; `ShareRepository.kt:151–155` | SC | |

### 16.2 Tier 1: playback and listening truth (additions to §4)

| ID | Finding | Evidence | Label | ✓ |
|---|---|---|---|---|
| P-03 | Pure turned on during a hold or release ramp leaves a non-unity rate (1.35× or 0.25×) under Pure, because `onPureChanged` never cancels `rampJob`. | `Deck.kt:248–251,265–279,358–362` | SC | ✓ |
| P-07 | An external Play while the needle is up plays with the arm parked and the position frozen. At ENDED, Media3's `handlePlayButtonAction` seeks and replays without a runout. | `Deck.kt:106,111,145`; Media3 `Util.java` 1.9.4 L3876–3883 | SC + Media3 source | |
| P-06 | No `onPlayerError`: a revoked grant mid-side stops silently with the needle "down". | `Deck.kt:104–117` | SC | |
| P-08 | The Deck and the service validate differently: the service passes no fileName, so a null `DISPLAY_NAME` fails there but passes the Deck. The all-or-nothing rejection is never reported to the Deck. | `FlacPolicy.kt:42,50–51`; `PlaybackService.kt:63–64,73–75` | SC; outcome Hyp | |
| P-05 | `load` discards measured durations (`refreshMetadata = false`). `trackIndex` is derived from estimated sums, not `currentMediaItemIndex`. Drops estimates without setting `durationKnown = false`. | `Deck.kt:169`; `Models.kt:50–59`; `Drops.kt:147` | SC math | |
| P-01 | Each `DspChain` rebuild (on almost every track change, and every settings tick) resets the limiter delay line and the biquad state, losing about one lookahead of audio. | `SoundEngine.kt:64–80`; `DspAudioSink.kt:110–116`; `DspChain.kt:23–36,73–76` | SC path; audibility Hyp | |
| P-02 | ReplayGain switches on `onMediaItemTransition` (playback position), not at the stream boundary, so the gain changes mid-track. | `PlaybackService.kt:37–41` | Hyp | |
| P-09 | "Bit-perfect to USB DAC" is decided by sample rate only and ignores encoding and channels. With Pure on and hi-res off, 24-bit becomes 16-bit, yet the label says "untouched". | `SoundEngine.kt:38,110–111,152–155`; `PlayerFactory.kt:34` | SC | |
| P-10 | `OutputStatus` and the preferred mixer attributes are never reset on eject or destroy, so the stale "bit-perfect" label stays. | `SoundEngine.kt:70–71,104–111`; `PlaybackService.kt:108–116` | SC | |
| P-16 | The exported session accepts any controller, so another app can set media items to URIs that Runout can read. | `AndroidManifest.xml:89–96`; `PlaybackService.kt:49–65` | SC | |
| P-04, P-11–P-15, P-17 | Ramp target captured early; one-sample limiter attack; stale pending commands; controller never released; odd side counts; side in the album title (scrobblers); SFX synthesis on main. | See the playback audit | SC/Hyp | |

### 16.3 Downloads, Drops and native (additions to §5 and §14)

| ID | Finding | Evidence | Label | ✓ |
|---|---|---|---|---|
| S-02 / N-06 | A PAUSED row ignores native terminal events, so `activeTransfers` keeps the id. Resume sends `resume`, which the bridge treats as a no-op for a terminal transfer. The row is stuck in QUEUED and the service never stops. | `SoulseekRepository.kt:230,444–451`; `lib.rs:274` | SC | |
| S-03 / N-05 | Pausing a peer-*queued* download fails: `pause_by_file` only works from InProgress, and Kotlin writes PAUSED after the command. | `download_store.rs:158–179`; `SoulseekRepository.kt:240–241` | SC | |
| S-04 / N-02 | Uploads that silently leave the core never get a terminal event, so "queued" keeps `hasActiveTransfers` true. That keeps the foreground service alive, and on Android 15 the dataSync timeout force-disconnects the account. | `lib.rs:366–373`; `SoulseekRepository.kt:386,464–474` | SC | |
| S-05 | Archive progress launches a coroutine per 64 KB chunk with a read-modify-write that can overwrite the final state. The size is fixed at 1,000,000. | `SoulseekRepository.kt:569–580`; `MainActivity.kt:351` | SC | |
| S-06 | Pause or Cancel on an Archive row doesn't stop the blocking HTTP loop, so the download continues without the foreground service. | `net/Http.kt:43–65`; `Drops.kt:127–138` | SC | |
| S-07 | Every claimed Drop is stored twice: private staging plus the SAF copy. | `Drops.kt:124–137,173,190` | SC | |
| S-09 | A Soulseek album's title, artist, folder and cover are overwritten by each published track. Tag variance splits one record across folders. | `SoulseekRepository.kt:504–526` | SC | |
| S-10 | Every published track re-validates **every** Soulseek album over SAF. | `SoulseekRepository.kt:111–112,526` | SC; cost Hyp | |
| S-11 | A failed or cancelled publish leaves hidden `.runout-part` documents and empty album folders in the user's music folder. | `SoulseekRepository.kt:506–515,541–543` | SC | |
| S-12 | The `id.take(10)` filename prefix scrambles untagged play order and puts hash names into the user's folder. | `SoulseekRepository.kt:508,519,524` | SC | |
| S-13 / N-11 | The bridge reports `bytes: 0` for queued, failed and cancelled states, which wipes the visible "53.3 MB kept". | `lib.rs:352–360`; `SoulseekRepository.kt:445` | SC | |
| S-14 | A Soulseek blip cancels Archive claims, and a fully successful claim can still be reported as failed. | `SoulseekRepository.kt:160,578,587–589` | SC | |
| S-15 | A folder download aborts on the first failed `enqueue`, and the rest is silently skipped. | `SoulseekRepository.kt:211,226` | SC | |
| S-17 | Archive downloads never resume partial tracks: no Range header, and the temp file is truncated. This answers §15. | `net/Http.kt:47–49` | SC | |
| N-03 | The native `shares` op opens and validates up to 20k files while Kotlin holds the wire lock, so polling (DMs, progress, loss detection) stalls. It re-runs on every connect. | `lib.rs:293–311`; `SoulseekConnectionManager.kt:88–93` | SC | |
| N-04 | Every server login rejection (bad version, server full, ban) maps to AUTH_REJECTED ("wrong password") and turns auto-connect off. | `vendor/.../handlers.rs:381–407,496–501`; `lib.rs:156–157` | SC | |
| N-07 | A failed `ack` after loss misclassifies a displacement as PROTOCOL_ERROR, which auto-reconnects and kicks the other client. Overlaps S-01. | `SoulseekConnectionManager.kt:116–132`; `lib.rs:94,283–286` | Hyp | |
| N-08 / S-20, N-09 / S-16, N-10, N-12, N-13 | Replaced searches are never forgotten; the event-cap eviction never matches, so control events drop once full; browse races; non-FLAC renames are re-downloaded forever; bridge maps are never pruned. | See the native audit | SC/Hyp | |
| S-18, S-19 | Activity-owned claim and pending-download state is lost on recreation; Discovery filters per file before grouping. | `MainActivity.kt:49–50,102`; `Discovery.kt:96–121` | SC | |

### 16.4 UI and accessibility (additions to §9–§10)

| ID | Finding | Evidence | Label |
|---|---|---|---|
| U-01 | `RetroPage` and `FolderPreviewScreen` don't consume taps or set pane semantics, so taps and TalkBack reach the hidden controls underneath (`PanelSheet` does have the fix). | `RunoutScreen.kt:167–175`; `DiscoveryScreen.kt:134–153`; cf. `ListeningRoom.kt:539–540` | SC; target Hyp |
| U-05 | The Soulseek username field allows autocorrect and has no autofill hints; an autocorrected name **creates a new, unrecoverable ID**. | `Controls.kt:199–207`; `SetupScreen.kt:126` | SC |
| U-07 | The keyboard covers the Messages composer; Discover double-pads. | `RunoutScreen.kt:62,94`; `DiscoveryScreen.kt:30` | SC/Hyp |
| U-08 | `Pill(active)` has no `selected` semantics or focus indication; the unread marker is read as "bullet". | `Controls.kt:68–83`; `RunoutScreen.kt:105–106` | SC |
| U-09 | `EqFader`, the Pressing track checkboxes, the 14 dp "Remove" and `ColourDots` lack usable semantics or targets. | `Controls.kt:153–166,233–263`; `Panels.kt:328,358–373` | SC |
| U-10 | Landscape and large-font layouts leave roughly 100–150 dp for the deck; the Folder Preview download button can be pushed off-screen. | `RunoutScreen.kt:63–106`; `DiscoveryScreen.kt:136–152` | Hyp |
| U-11 | The AutoEq picker, image picker and share chooser don't call `onExternalFlow(true)`; unguarded `launch` calls can crash with the counter unbalanced. | `MainActivity.kt:140–194,321–329` | SC |
| U-12, U-14–U-18 | Picker follow-ups lost on recreation; `.vinyl` re-import from Recents plus overly broad zip/octet-stream filters; the notice ignores insets and isn't announced; no Back inside a conversation; clipped pill labels; switch track contrast 1.31:1. | See the UI audit | SC |
| U-19 | `ListeningRoomUiTest` mostly exercises `RoomScreen` chrome that production never shows (`showChrome = false`). | `ListeningRoomUiTest.kt`; `RunoutScreen.kt:84` | SC |

### 16.5 Test-suite findings

- `TurntableDeckTest` asserts policies v2 rejects: search autoplay (`:79–87`) and cross-side next (`:128–133`).
- It injects ExoPlayer directly, so no session, controller, wrapper or validation path is exercised (V-21).
- `AudioOutputTest.neutral()` disables the limiter, which hides P-01.
- The checked-in Argent flow `offline-song-search.yaml` taps **Play song** and asserts `deck:screen` contains "Opening". Slice 1 must rewrite it (lead-verified ✓).

## 17. Signal path, Bluetooth and DAC (new product gap G-10)

The user asked for an Amazon-Music-style "Audio quality" view, to confirm what is actually reaching their ears, plus Bluetooth compatibility and an honest wired-DAC recommendation.

Current source:

- Route awareness is absent. The only device query is a USB check for Pure (`SoundEngine.kt:143–146`).
- There is no `AudioDeviceCallback`, routed-device query, Bluetooth awareness or `onAudioTrackInitialized` reporting.
- The Sound panel shows `"24-bit · 96 kHz → 32-bit float"` with no output device.
- A FLAC file's average bitrate exists (`AudioQuality.bitrateKbps`), but `AudioQuality.label` shows bitrate only for lossy codecs (`Models.kt:46–55`), so it is never displayed.
- The "bit-perfect" label can be wrong (P-09, P-10).
- No Bluetooth permission is declared, and none should be added: `AudioDeviceInfo` is enough.

Constraint for the design: Android usually does not tell apps the real mixer rate or the active Bluetooth codec. A "Built-in speaker · 24-bit/44.1 kHz" claim, like the reference screenshot, would be unverifiable. v2 §9.5 specifies a signal path where every stage says **how we know**.

