# Runout — listening-room redesign, draft v1

Status: proposal for review, not implemented. Date: 2026-10-20.
Baseline inspected: `d56ceaa` on `main`; application implementation `64e1222`.

## 1. Brief and non-negotiables

Make Runout feel like owning records and operating a turntable, not a streaming app with a rotating picture. The user authorizes a full UI remap, asks for a 1950s album-listening ritual, more convincing sleeve opening, meaningful start/end crackle, speed control, a coherent retro + ASCII + pixel aesthetic, a bug audit, and a draft revised after a GPT-6 Astra Medium subagent review. This deliverable is planning only; do not silently ship a redesign.

Preserve Kotlin/Compose, Media3, the MIT Rust/JNI Soulseek boundary, FLAC byte validation, offline/no-account local listening, retained files, account-scoped consent/history, authenticated download gates, Friday Drops rules, original signing identity and the existing record/side model. No PR, publish, login, public message or network-displacement experiment is part of this planning pass.

Album-first means the record is the unit of selection and playback. No shuffle feed, standalone song queue, automatic next album or endless autoplay. Song search can still locate a groove in its parent album; the proposed primary action becomes **View record**, not **Play song**. Do not silently delete existing intra-record needle cueing or accessible track navigation. Exact policy is a decision before implementation.

The historical reference is the ritual, not a museum reconstruction. 33⅓/45 and pitch controls coexist with pixel/ASCII instrumentation by intent. Do not add fake 78 rpm or a DJ scratch engine to suggest authenticity.

## 2. Evidence and limits

Read: handoff, architecture/developer/contribution docs; current navigation, setup, discovery, transfers/account/messages, controls, deck, SFX, audio service, models and related test sources. Visually inspected the user's download screenshot and committed `docs/images/home-0.4.1.png`. Current device inventory reports Pixel_6 / emulator-5554 with `app.runout.dev` installed. No app interactions, login, state resets, new APK, test run or audio audition occurred in this pass. Installed APK equivalence to checkout is not established.

Evidence labels below:
- **Observed:** visible in supplied screenshot or committed reference image.
- **Source-confirmed:** exact implementation path exists; not necessarily reproduced at runtime.
- **Hypothesis:** needs a red-capable regression or device scenario.
- **Product gap:** missing or inconsistent experience, not necessarily a software fault.

Do not call the another-client warning itself a defect: Soulseek's one-session behavior is expected. Never fix it by aggressive automatic reconnection.

## 3. Current experience map

```text
First use
  Welcome → Offline → choose local FLAC folder / skip → Home
          → Soulseek → download folder → optional sharing
                     → app-only/background → Home
Main shell
  Home     → Sound panel, Crackle toggle, turntable + physical crate
  Library  → text album cards → Put on the platter; folder/rescan; Press a record
  Discover → Find music: local + Soulseek → song play or folder preview/download
           → Friday Drops: ten weekly Archive albums / three successful claims
  Messages → contacts → conversation → composer
Global
  ↓ active-transfer count → Downloads full-screen overlay
  account status → Account overlay / setup
  mini player → Home + Play/Pause
```

Settings are scattered: sound in Home; library folder in Library; download/share folders, background and sign-out under Account; no unified settings destination. Main shell embeds a legacy room with its own panel/message layer. Library selection loads immediately; the home crate uses a separate sleeve pull/drag ritual. Same album intent has inconsistent paths.

## 4. Audit register

| ID | Priority | Evidence | Finding and source | Required check before fixing |
|---|---|---|---|---|
| UX-01 | P1 | Observed + source-confirmed | Downloads repeats one displacement warning globally and per file, Resume is disabled, and no Reconnect exists in this panel. `SoulseekPanels.kt:162–188`; `RunoutScreen.kt:117`. | Compose a displaced-account fixture with partial transfers; assert one actionable recovery notice and retained byte counts. |
| UX-02 | P1 | Source-confirmed | Dismiss clears repository `_error`, but `NetworkNote` falls back to `session.error`; a persistent session error can immediately remain visible. `SoulseekPanels.kt:40–43`; `SoulseekRepository.kt:123`. | Fixture with both error fields; dismiss; distinguish collapsed persistent status from removable transient errors. |
| UX-03 | P1 | Source-confirmed | Generic Resume/Retry is offered for Archive rows, but repository `resume` rejects non-Soulseek and directs users back to Friday Drops. `SoulseekPanels.kt:180–184`; repository `243–251`. | Failed Archive row and click path; assert provider-specific action, allowance preserved. |
| UX-04 | P2 | Observed + source-confirmed | Filenames render as uppercase 9.5sp section labels, expanding feature credits into tall blocks; no album/job grouping or compact details. `Controls.kt:170–174`. | Long filenames, missing tags, mixed folders, text scaling. |
| UX-05 | P2 | Source-confirmed | Transfer list composes all rows in a scrolling Column, not a lazy list. Mini badge counts active states but hides paused attention. `SoulseekPanels.kt:167`; `RunoutScreen.kt:68,174`. | Large fixture, stable IDs, scrolling performance and paused-only badge. |
| UX-06 | P1 | Source-confirmed | Pure mode suppresses drop/crackle starts but `liftNeedle`, side change and ended listener call lift/runout SFX unconditionally; already-playing one-shots are untracked. `Deck.kt:110–114,230–235,323,358–365`; `VinylSfx.kt:56–64`. | Injectable SFX spy: Pure before/during drop, lift, side end, flip; no effect starts or remains audible. |
| UX-07 | P1 | Source-confirmed + hypothesis | Ended event starts a 3.9s runout one-shot but immediately lifts arm/state; visuals wind down while noise continues. No runout state. `Deck.kt:110–114`; `VinylSfx.kt:162–172`. | Fake-clock side end and render state; on-device capture/audio audition. |
| UX-08 | P2 | Source-confirmed + user report | Start waits 420ms and ramps volume; crackle is a fixed-seed four-second loop at a fixed hidden level. No distinct lead-in bed/ritual control. `Deck.kt:209–226`; `VinylSfx.kt:22,37–45,175–180`. | Record quiet/loud fixture playback with effects on/off and levels; avoid inventing audibility conclusions. |
| UX-09 | P1 | Hypothesis, source basis | Platter target derives from needle/held, not actual playback; MediaSession commands operate underlying player directly. External pause can leave visual spinning state or startStop semantics inconsistent. `Turntable.kt:219`; `Deck.kt:104–107,287–294`; `PlaybackService.kt:49`. | Notification/headset pause/resume, focus loss, buffering; assert state/audio/visual agreement. |
| UX-10 | P2 | Source-confirmed | Power knob is drawn, not wired as an actual control; pitch/needle gestures have limited semantic actions; deck hitboxes scale with artwork rather than a 48dp minimum. `Turntable.kt:393–453,481–490`. General Pill controls already have 48dp minima. | Measure actual deck targets at compact width; TalkBack full record flow; avoid falsely calling all buttons too small. |
| UX-11 | P2 | Product gap | Settings lack one predictable home; setup is reused to recover/change account and folder choices are fragmented. `RunoutScreen.kt`; `SoulseekAccountPanel`. | Task-based navigation: change crackle, repair folder, reconnect, notification denied, cancel setup. |
| UX-12 | P2 | Source-confirmed | Embedded RoomScreen and outer RunoutScreen both show `state.message` with separate expiry jobs. `ListeningRoom.kt:472–497`; `RunoutScreen.kt:127–129`. | One message on Home versus Library, assert one visible notice and one dismissal owner. |
| UX-13 | P1 | Source-confirmed copy risk | Pure/hi-res UI promises more than route negotiation can prove; float conversion is not full 32-bit integer bit preservation. `Panels.kt:94–104`; `DspAudioSink.kt`; `SoundEngine.kt`. | Source/output/route copy tests and hardware qualification; no bit-perfect badge based solely on a preference request. |
| UX-14 | P2 | Product gap, source-confirmed | All sources excludes Archive; Archive is Friday Drops only. `Discovery.kt:10`; `DiscoveryRepository.kt`. | Truthful labels now; generic Archive integration remains separate scope and allowance decision. |
| UX-15 | P1 | Hypothesis, source basis | Album FLAC validation runs synchronously in `Deck.load` reached by UI actions; SAF reads may stall input/animation. `Deck.kt:168–170`; `MainActivity.kt:142`. | Slow provider fixture / StrictMode trace; preserve revalidation but move blocking work behind cancellable loading state. |

## 5. Information architecture proposal

Home is primarily **Operate**. Crate/Discover are **Explore**. Transfers are **Operate/Monitor**. Settings are **Configure**. Do not apply the same rounded-card composition to every screen.

```text
Persistent navigation: HOME | CRATE | DISCOVER | MESSAGES
Global labeled actions: DOWNLOADS [active + attention] | SETTINGS
Compact Soulseek indicator opens connection details (never blocks local playback)

HOME / LISTENING ROOM
  current sleeve + large deck → ritual / cue lever / motor / speed / flip
  Track bands & sleeve notes drawer; accessible control panel
CRATE
  cover grid or tactile sleeves → record detail → sleeve back / side map
  → Take record out → Place on platter → Home
  Import / choose folder / rescan / Pressing Plant under collection tools
DISCOVER
  albums-first results; songs are ways to find an album
  source tabs: On device | Soulseek; explicit Friday Drops entry
  preview full peer folder → choose FLAC files / confirm record download
MESSAGES
  conversations → conversation; always-readable local history
  connection-aware composer; never call accepted sends delivered/read
DOWNLOADS
  Active | Needs attention | Completed
  album/folder jobs → expandable tracks → details
SETTINGS
  Listening & ritual
  Sound & output
  Library & storage
  Soulseek & sharing
  Appearance & accessibility
  About, privacy & diagnostics
```

Keep familiar functional labels alongside themed headings: `Downloads / incoming records`, not an obscure metaphor-only menu. Four main destinations are enough. Downloads/settings do not need two more bottom tabs. On narrow layouts move secondary global actions into an explicitly labeled menu without hiding connection recovery.

## 6. Screen design and critical flows

### Home: one physical object, one coherent status

```text
RUNOUT                         Downloads  Settings
OFFLINE LISTENING · network optional
┌────────────────────────────────────────────┐
│ walnut edge / enamel faceplate             │
│     black grooved record      tonearm      │
│       printed label             cue lever  │
│                                  pitch    │
│ [MOTOR] [33⅓] [45]     [LIFT / LOWER]       │
│ A1 · Opening       SIDE A       +0.0%       │
└────────────────────────────────────────────┘
Needle settling…           [Skip ritual]
Night Drive — Vela Park     [Sleeve / tracks]
[Flip to B] when side ends  [Put record away]
HOME       CRATE       DISCOVER       MESSAGES
```

Illustrative wireframe, not measured final geometry. Portrait: deck dominates; current sleeve becomes a small rail/detail drawer instead of competing for half the height. Landscape: deck left, sleeve/side list right. At large text sizes: readable control panel rather than shrinking labels further. Hide duplicate mini transport on Home; keep a truthful status strip away from Home.

Primary Start performs motor spin-up + cue-lower as a convenient macro. Expose independent motor/cue controls for deliberate operation; drag is an optional alternate, never required. Touch controls need visible affordances, semantic actions and non-overlapping 48dp targets. A decorative part is not styled as an interactive switch.

### Selecting and opening a record

Crate → sleeve front → inspect back/credits/side tracks → **Take out record** → inner paper sleeve slides → record edge emerges → drag OR **Place on platter** → spindle alignment/contact shadow → selected side cued. No autoplay from merely loading. Changing an actively playing record asks **Replace current record?** and cancels prior transitions only after confirmation. Returning it reverses the ritual; never deletes files.

Record detail shows real metadata and source. If sides are computed, label them **Runout side layout**, not original pressing sides. Multi-disc files retain disc boundaries where possible; never split a track. Do not invent original artwork, edition, license, disc count or complete-album certainty. Incomplete downloads show `record incomplete`, not a full sealed album.

### Listening through a side

Loaded → Start → platter accelerates → arm moves above lead-in → stylus settles → audible lead-in texture → track audio begins without trimming its opening → continuous tracks within side → runout texture → arm lifts/returns → explicit **Flip to Side B**. Final side ends with **Play again / Put away**, not next album. Side end does not become “Play” with an unexplained reset.

Draft timing targets, adjustable after audition: motor rise 0.8–1.4s, cue movement 0.6–1.0s, lead-in 2–4s, runout 2–4s then automatic lift. Some phases overlap naturally. Provide Short/Full/Off ritual; Skip ritual acts immediately. Reduced motion changes visuals, not the user's audio preference; disabling crackle must not force animation off. No ritual between every song on one side.

### Downloads recovery (screenshot)

One warning above album jobs:
> Connection paused: this Soulseek ID connected in another client. Your partial downloads are kept. Reconnecting here may disconnect that client.
> **Reconnect here** · **Keep listening offline**

Use `another client`, not necessarily `another device`; another app on the same phone can cause this. Keep account identity. After explicit reconnect succeeds, offer **Resume paused downloads** (not automatic duplicate enqueue); preserve manual pauses. Per-file status becomes `Waiting for your connection` with concise provider/peer errors only when distinct. While recovering, show one status and prevent repeat requests.

Group by persisted job or exact source + peer + full folder, never title alone. Keep first-class file details for long names, destination, bytes, queue, error and retry. Completed item → **View in crate** / **Open destination**. Cancelling explains what partial data is discarded and never removes already-published music. Partial album progress uses known totals, not an unweighted average; unknown totals remain explicit. Archive recovery is **Return to Friday Drop** until real resumable support exists. No new weekly allowance behavior.

### Settings inventory

| Section | Contents | Ownership / safeguards |
|---|---|---|
| Listening & ritual | Full/Short/Off; lead-in/out; crackle level and audition; auto-lift; haptics; cue help | Device settings. Saved preferences distinct from effective values under Pure. |
| Sound & output | Pure, source vs configured output vs verified route, hi-res request, ReplayGain, EQ, AutoEq, warmth, limiter | Existing sound engine retained; truthful capability labels. |
| Library & storage | Music folder, rescan/report, download destination, permission repair, imported records, cache controls | SAF only; cache clearing cannot delete music or staged transfers unintentionally. |
| Soulseek & sharing | Account state/reconnect/disconnect/logout, remembered credentials, share folder/consent, background consent/permission | Exact-account scope. Show effective paused state separately from preference. |
| Appearance & accessibility | Theme, reduce texture, system/reduced motion, accessible deck controls, text/system scaling | No pixel font for long prose/messages/errors. Respect system accessibility. |
| About & diagnostics | App/build version, licenses, privacy/protocol warning, sanitized diagnostics export, tour | No credential/message dumps, no invented support server. |

Settings back returns to the originating screen and preserves query/scroll/record state. Folder picker cancellation retains the old valid grant. Failed replacement remains repairable without discarding the library. Logout affects network/account data visibility, not offline music.

## 7. Three art directions, not three palette swaps

**A — Restored hi-fi (conservative).** Keep existing turntable geometry, replace frosted glass/pastel light with satin metal, walnut edge, black rubber and paper sleeves. Familiar layouts, fastest implementation; less distinctive beyond Home.

**B — Walnut console × print-shop terminal (recommended).** Tactile deck and record dominate Home; supporting screens look like record-shop catalog slips and equipment service panels. Cream paper, soot ink, walnut, oxidized green and amber. Pixel lettering for equipment legends and short status readouts; monospace/ASCII separators for catalog metadata; readable existing Inter for body text. Harder edges, thin rules, stamped side identifiers, restrained physical shadows. One coherent material family, not full-screen fake terminal or glass cards.

**C — Pixel record shop (divergent).** Isometric/pixel room with crate furniture and equipment. Strong identity, but more artwork, heavier motion, poorer discoverability at small sizes and extra accessibility work. Prototype only if B feels too restrained; not the initial implementation.

ASCII accents: `[ SIDE A ]`, `A1 / A2`, `--- LINER NOTES ---`, record matrix/catalog labels. Decoration is hidden from accessibility trees. Do not make icons unreadable or encode live status only in glyphs. No fake CRT flicker, glitch transitions, neon gradients or constant marquee.

Draft palette for B: paper `#F2E6CE`, ink `#24231F`, walnut `#5A3A28`, enamel `#DDD7C7`, signal green `#345A45`, amber `#BC6C25`, error `#9A3429`. These are candidates, not contrast-approved tokens. Texture never sits behind small live text at high contrast. Preserve album art colors; do not recolor user covers to match the theme.

## 8. Playback architecture proposal

Replace scattered playback booleans/timers with an explicit transition owner, not a rewrite of networking/library. Conceptual state:

```text
Empty → LoadingRecord → Cued → MotorStarting → CueLowering → LeadIn → PlayingSide
PlayingSide ↔ NeedleRaised / MotorStopped / HandHeld
PlayingSide → Runout → AutoLift → AwaitingFlip → Flipping → Cued(next side)
Final side → Runout → AutoLift → RecordFinished
Any interruptible phase → Cancelled / Paused / Error (with recoverable context)
```

Motor velocity, arm pose, stylus contact, record identity, selected side and media readiness are explicit state dimensions. Statechart must define legal combinations, not accumulate an enum for every combination. Use one monotonic transition clock and generation token; animations derive from it. Service/Deck controller is authoritative; composable lifetime does not own playback or effect tails.

Handle cancellation on replace/eject, lift, side change, app process death, focus loss, phone call, route unplug, headphone command and Pure toggle. MediaSession controls must enter the same semantic command path. Reject stale callbacks from old records/controller generations. Restore cued/paused safely after process death; never spontaneously start sound. Keep selected record/side/position only with necessary permission checks and stable IDs.

SFX design: separate mechanical events, surface bed, lead-in and runout envelopes; make all voices cancellable, level-controlled and lifecycle/focus-aware. Pure is a hard gate for every voice, including sounds already playing. Use long/procedural non-obvious texture rather than a repeating short pop pattern. Drive runout periodicity from actual RPM. Include off, subtle and adjustable intensity, with safe bounded peaks and explicit audition; do not normalize noise to full-scale. Preserve existing FLAC source bytes. Synthetic effects are not music-format exceptions to broaden imports.

Technical spike before committing to audio implementation: compare tracked AudioTrack voices against a shared mixer outside Pure; document synchronization, route, gapless playback and sample-format tradeoffs. A shared mixer is not pre-approved. Pure bypass must not depend on this effect system, and 32-bit float must not be sold as lossless 32-bit integer output.

## 9. Delivery phases and gates

1. **Evidence + regressions:** red-capable fixtures for UX-01/02/03/06/09/12/15; screenshot/recording baseline; root cause confirmation. No broad visual rewrite to hide bugs.
2. **Recovery + navigation foundation:** shared status components, provider-aware transfer actions, Settings routes, one overlay/toast owner, lazy transfer/conversation lists, truthful audio/search copy.
3. **Record-first journeys:** common sleeve/record-detail entry from Home/Crate/search/download completion; incomplete-record handling; explicit flip/end; migration-safe preferences.
4. **Deck state/audio spike:** isolated fake-clock statechart tests and SFX gating; compare hardware/audio behavior before final visual polish.
5. **Material/motion implementation:** recommended art direction, sleeve depth/shadows, coherent controls and type, reduced-motion/accessibility equivalents.
6. **Hardening + development build:** JVM/Compose, lint, host native regression; packaged Android tests if relevant; Android API 35/37 flows, physical audio audition and hardware qualification. Development signing preserved. Release only after a separate implementation/release instruction.

## 10. Acceptance matrix

- Offline first use → FLAC folder → crate → sleeve → load → cue → play → flip → finish, with no account prompt or public network dependency.
- Search a song → open its album → cue its side; no standalone global queue. Album order retained, no cross-album auto advance.
- Every phase interrupted by eject/replacement/lift/Pure; fake clock proves no stale playback/effect start.
- Natural side end and explicit skip are distinct; single-track record, multi-disc, unknown duration and missing file are covered.
- Full/short/off rituals preserve first/last source samples and gapless track adjacency within a side; audible judgments require real listening.
- Pure on before/during any sound: all SFX off, speed 1x, no new effect allocation; do not claim hardware bit perfection without route verification.
- Headset/notification play/pause, route unplug, duck/focus loss, background and process recreation produce matching audio, deck and accessible state.
- Displacement → partial bytes retained → one recovery notice → explicit reconnect → explicit resume; no credential re-entry when retained session can reconnect, no retry war.
- Storage grant revoked, full destination, offline peer, queue, cancellation during validation/publication: truthful status and no lost completed music.
- Failed Archive retry never accidentally invokes Soulseek resume or consumes a weekly claim without successful publication.
- 320dp and 412dp portrait, landscape, maximum text size, TalkBack, reduced motion; no clipped critical actions, overlap or gesture-only task.
- Large crate, transfer list and message history bounded/lazy; keep documented 10k local/5k remote performance gates. Profile animations and audio on device, not just emulator.

Existing checks to extend: `TurntableDeckTest`, `RunoutFlowTest`, `ListeningRoomUiTest`, `AudioOutputTest`, `DspTest`, `SoulseekConnectionTest`, `SoulseekRepositoryTest`, `FridayDropsTest`, `DiscoveryTest`, `FlacPolicyTest`; packaged-JNI/device flows as documented. Historical release passes are not evidence for this plan's future changes.

## 11. Assets and next decisions

No external asset pack blocks a first prototype. Existing cover art, Silkscreen/Inter licenses, Compose Canvas/SVG shapes and procedural textures are sufficient. Optional upgrades: seamless walnut/paper grain, inner-sleeve fibers, original small pixel equipment icons, original/licensed needle/cue/motor recordings. Asset manifest needs creator, source, license, allowed redistribution and dimensions; do not scrape commercial deck branding or album covers.

Before implementation lock: art direction B versus A/C; whether song results allow explicit in-album cueing; full ritual default versus short; automatic lift after finite runout versus manual locked-groove mode (recommend finite auto-lift). No questions block this review draft; recommendations remain proposals.

Out of scope unless separately approved: unified generic Archive search/download policy, 32-bit bit-perfect engine, public rooms, new backend/framework, monetization, fake historical mechanisms, real-time scratching, automatic deletion of user music.

## 12. Subagent review contract

Review this v1 for coherent album-only intent, feasible Android audio ownership, edge cases, accessiblity, source-grounded findings and restrained implementation scope. Return concrete changes and prioritized disagreements, not praise. Recheck source references. Then write v2 retaining v1 for comparison; document what changed and why. Do not implement app code in a planning review.
