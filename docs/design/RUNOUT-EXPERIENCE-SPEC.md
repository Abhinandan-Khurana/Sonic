# Runout — push the concept further

Working design specification. Proposal only; companion to draft v1 and subsequent reviewed v2.

## The product sentence

**Pick a record. Open its sleeve. Lower the needle. Stay for a side.**

The design fails if the same result could be achieved by replacing a generic player thumbnail with a spinning disc. Physical actions must change meaningful playback state, and the software must remain usable without dexterity, sound effects or animation.

## 1. The recommendation: the Runout Console

A walnut-edged, warm enamel record player with black grooves, a real label-sized center, a machined rim, a cue lever and a small amber/green equipment display. Around it: paper sleeves, collection tabs, catalog typography and sparse pixel/ASCII labels. Not neon synthwave, fake DOS, a Spotify grid with wood texture, or wall-to-wall distressed grunge.

Keep the hardware continuous across screens: the current record remains on the deck while browsing. Only one record can be mounted. Browsing a sleeve does not unload or pause it. One pending record is enough for the first release; do not add an autoplay queue disguised as a stack.

The 1950s reference defines deliberate album listening, not historically exact electronics. Pitch faders and pixel displays are a deliberate later-era blend. Artwork should not pretend to be an authentic commercial turntable or original vinyl pressing.

### Material hierarchy

1. **Physical:** deck, sleeve, vinyl, inner paper. Depth, occlusion, contact shadow and controlled highlights. Most realism belongs here.
2. **Printed:** library labels, liner notes, transfer manifests. Flat cream paper, ink, rules, tabs, one stamped state marker.
3. **Instrument:** status, RPM, side code, byte counts. Monospace/pixel accents, never long all-capital filenames or tiny errors.

Black vinyl is a candidate default for new visual presentations, independent of source quality. Existing user-chosen colored/clear/picture pressings must retain their meaning and choice. Do not silently rewrite saved disc styles. High resolution is metadata, not a reason a record must look like translucent candy.

## 2. The home composition

Primary surface: **Operate**. The deck is the hero object, not a marketing hero.

- A compact brand/status rail; two labeled global actions: Downloads and Settings.
- A large top-down deck with a slight physical edge, sized for the available width. No giant decorative dashboard numbers.
- A meaningful status sentence below the instrument: `Needle lifted · Side A at A2`, `Runout groove · lift in a moment`, or `Ready to flip to Side B`.
- Sleeve rail with album, artist, real source quality and `Open sleeve`.
- When stopped at side end, one clear `Flip to Side B` action. On last side, `Play this side again` and `Put away`.
- No duplicate transport bar on Home. Elsewhere, compact `On the turntable` strip reports the same authoritative state.
- Compact screens get a larger accessible control tray instead of tiny physical labels. Landscape puts sleeve/back cover next to the deck. It must not shrink both into illegibility.

Power, motor and cue must not all be unexplained synonyms for Play. Recommended MVP: an actual **Motor** control and **Lift / Lower cue**; the main **Start listening** macro performs the sequence for first-time users. Decorative power hardware is either labeled as a lamp or removed. A separate software power subsystem is unnecessary.

## 3. Signature interaction: opening the sleeve

The current app already pulls a disc and supports dragging to the platter. Enhance it, do not replace it with a fullscreen video.

### Choreography

1. Selected sleeve advances from the crate, retaining its position and cover image.
2. Sleeve back is available by an explicit control; front/back does not start music.
3. `Take record out` moves the inner sleeve first, then exposes the disc edge. A changing shadow and occlusion sell separation better than particles or lens effects.
4. The record can be held by its edge with direct manipulation, or moved with `Place on platter`.
5. Near the spindle, a subtle alignment marker appears. Release snaps the center, not the finger, to the spindle; a broad forgiving target replaces precision dragging.
6. The sleeve rests beside the deck; it does not vanish into a navigation transition. Its back becomes the track/credits drawer.
7. Cancelling returns the object to the previous safe state. Rotation, navigation or an interrupted drag cannot accidentally load/play/delete anything.

### Why it is more than animation

The record identity is not committed until valid local audio is ready and placement succeeds. A failed read returns a repair message on the sleeve. If another record is playing, inspect freely; only actual replacement requires confirmation. Cached sleeve images must not trigger file rescans.

### Accessibility equivalent

A screen-reader sequence `Open record → View tracks → Place on platter → Start listening` reaches the same state. Gesture instructions are optional and shown once. Reduced motion uses a short crossfade or immediate placement, never changes audio policy or prevents a cue action.

## 4. Signature interaction: a side as a continuous object

A side is a sequence of tracks in their original order, not a shuffled playlist. Its groove bands are a navigation map. A labeled `Track bands` drawer exposes A1/A2 and durations; `Cue here` positions the needle within the mounted record. No global song queue is created.

Strict album-only must not be misread as forcing users to finish a side or making an interruption punitive. Always allow pause/lift/stop, explicit cueing and ejection. Recommended policy: **album is the playback container; tracks remain addressable within it**. If the user instead wants disabling track skipping entirely, that is an explicit product decision, not an accessibility regression smuggled into artwork.

Source discipline:
- Computed side splits say `Runout side layout`.
- Original disc numbers are retained; avoid mixing disc 1 and 2 without disclosure.
- Long single tracks remain intact even beyond a nominal side duration.
- Unknown durations use a list and honest unknown marker, not fabricated grooves/progress.
- A peer directory is not proof of a complete album. Show downloaded/expected files when known; otherwise say `folder collection` or `completeness unknown`.
- Existing homemade pressings remain playable as records. Do not remove Pressing Plant merely because it can assemble a custom album.

## 5. Signature interaction: start and end you can hear

The existing fixed drop delay and one-shot end sound are not a complete ritual. The design must explain when the motor spins, when the stylus touches the groove, and when source audio starts.

### First side start

`Cued → Motor ramp → Arm above lead-in → Stylus contact → Lead-in bed → Program audio`.

Motor and arm can overlap in time. Source audio remains at its real beginning until the lead-in ends. Do not fade away the first musical transient merely to hide a timing problem. A short click-free transition is not permission to trim the track.

### Within a side

No new needle-drop sequence at every track boundary. Gapless adjacent tracks remain gapless where the decoder/files permit it. Existing source silence is retained. Continuous crackle, when enabled, stays consistent across track boundaries.

### Natural end

`Last source sample → Runout groove → Cue lift → Arm return → Motor stop → Awaiting flip`.

Finite runout is the default. The first release does not need an endless locked-groove mode, an antiskate simulator, motor servicing or a wear economy. End-of-side should be unmistakable, calm and stoppable.

### Three independent preferences, not a giant effects rack

- **Ritual length:** Short / Full / Off. Controls waiting/choreography around a side, not mid-side gaps.
- **Surface sound:** Off / Subtle / Custom level, with a brief audition that stops on exit.
- **Pure sound:** overrides all SFX, speed/pitch coloration and DSP per existing policy. Stored ritual preferences remain; UI says `Surface sound off while Pure is on`.

Mechanical cue sounds and a continuous surface bed must not be hidden behind the same ambiguous `Crackle on` switch. For MVP, group them under one clearly explained surface-sound setting with a detail screen; do not add ten knobs before the basic start/end sequence is reliable.

Haptics are optional; no haptic at every crackle pop. A cue lever detent, pitch zero and spindle placement are enough. Quiet listening must not be interrupted by loud celebratory sound.

## 6. State ownership: where the realism becomes trustworthy

Rendering must consume playback state, never invent it. State dimensions:

| Dimension | Example values | Owner |
|---|---|---|
| Mounted record | none / validating / record+side+generation | Playback coordinator |
| Motor | stopped / ramping / stable / braking | Playback coordinator clock |
| Stylus | rest / raised / moving / contact | Playback coordinator |
| Program | preparing / ready / playing / buffering / paused / ended / failed | Media3, reconciled by coordinator |
| Ritual | none / lead-in / runout / auto-lift | Playback coordinator |
| Interruption | user / focus / route / process / error | Explicit reason |
| Effect voices | active handles, phase/level, cancellation scope | Audio subsystem |

Do not use the word `Playing` merely because needleDown is true. A rotating platter with a raised stylus is possible, but must say `Motor running · needle raised`, not falsely imply audible music.

### Required interruption contract

| Event | Program behavior | Ritual/effects | Resume |
|---|---|---|---|
| Lift cue | Pause at current source time | Cancel bed/tails after controlled stop; optional lift only outside Pure | Lower at saved groove; brief cue, not full sleeve ritual |
| Motor stop | Controlled brake only in vinyl mode; Pure pauses without pitch effect | Surface follows effective speed/contact and stops | Explicit start; no extra track advance |
| Headphone unplug | Pause immediately | Silence all voices immediately | User action only |
| Transient focus loss | Follow Media3 focus policy | Same focus loss applies to effects; never a rogue separate track | Only if policy allows and no later user pause |
| Replace/eject | Stop previous generation after confirmation | Cancel all old voices/timers | New record cued, no autoplay |
| Enable Pure | Preserve legitimate source position; normalize speed | Cancel every active/pending SFX voice | No later stale ramp may reinstate pitch/noise |
| Navigate away | Continue legitimate playback | Continue coordinator, not a composable timer | Re-enter same state |
| Process death | No spontaneous audio on recovery | No restored live effect handles | Restore safe cued/paused record after URI checks |
| Decoder/storage error | Stop affected program; explain exact repair | Cancel ritual and effects | Explicit repair/retry with same context |

MediaSession play/pause/skip is part of this contract; a second competing playback path is not acceptable. Test command mapping before adding the new renderer. Test any proposed service-owned coordinator against foreground-service startup restrictions; don't move initialization wholesale just because a diagram says 'service'.

## 7. The rest of the app belongs in the same world

### Crate: a collection, not engagement feed

Default cover-forward catalog with readable artist/title and alphabetical/year/recently added sort. A tactile browse view remains available, but not the sole way to reach the 500th record. Preserve scroll position and focused album after returning from sleeve inspection. Empty states show `Bring your first record` with a real FLAC folder action, not fake recommended albums.

### Discover: record shop counter

Albums/folders first; song terms lead to record context. Source labels tell the truth. Friday Drops is a small weekly shelf, visibly distinct from Soulseek search. A preview sheet is an inspection slip: full path, peer, FLAC count, sizes/quality as advertised, folder completeness caveat and explicit download action. No fabricated artwork or search result counts in live screens.

### Downloads: incoming records, not raw file spam

One receipt-like album/folder group, expandable into tracks. Three tabs: Active / Attention / Completed. Connection recovery is pinned above groups. It must remain visible when the list scrolls, but not overlay controls.

Model two axes: durable transfer state and current blocker. `PAUSED` alone loses whether the person paused it or the session disappeared. Existing v1 rows have only state+free-text error: unknown legacy reasons must remain unknown and must not be classified by matching arbitrary error strings.

A first UI slice can group legacy rows by exact owner/provider/peer/full folder and offer explicit per-file recovery. Persisted group intent, expected membership and typed pause reason require a later schema migration and tests; do not pretend these fields already exist.

Suggested stable group statuses: `Downloading 2 of 8 files`, `Waiting for connection`, `Waiting for peer`, `Finishing verified files`, `Partly saved`, `Saved to your crate`. Do not say `Album complete` unless expected membership is known. Duplicate attempts must not inflate totals.

Sample copy for the reported screen:
> **Connection paused**
> This ID connected in another Soulseek client. Your partial files are kept. Reconnecting here may disconnect that client.
> [Reconnect here] [Keep listening offline]

After connection: an explicit selection-based Resume action. Users who manually paused a file must not lose that decision. Archive rows use their actual supported recovery flow; no pretend universal resume.

### Messages: a private line, still a normal conversation

Readable prose, date separators, stable scroll, pending/accepted/failed states, retry without duplicate sends. Never pixel-font the message body. Offline history should open without starting onboarding. Keeping an account is different from being connected; send requires connection but reading local history does not. Failed draft must remain recoverable rather than disappearing into an error banner. Pagination and account-switch isolation are correctness work, not decoration.

### Settings: one entrance, small first page

Four initial groups plus About:
1. Listening — ritual, surface sound, Pure and link to advanced sound.
2. Collection & storage — local folder, download folder, scans/permission repair.
3. Soulseek — session recovery, account-scoped sharing/background, privacy.
4. Display & accessibility — motion, texture and accessible deck controls.
5. About — version/licenses/sanitized diagnostics.

Advanced EQ/AutoEq stays nested, not mixed into the first settings screen. The visible effective setting must explain Pure overrides. Use real folder display names rather than raw document URIs where obtainable, with details available. No unrelated themes marketplace or cloud account layer.

## 8. Design-system rules

- Main body text target 14–16sp; metadata 12–13sp where practical; pixel labels are short and nonessential. Respect system scale. These are proposals to measure, not excuses to suppress font scaling.
- Touchable actions 48dp minimum with nonoverlapping bounds. Hardware art can be smaller, with equivalent controls in a legible tray.
- No blanket rounded cards. Use ruled lists, sleeve rectangles, tabbed sections and a single highlighted primary action.
- Motion has cause and consequence: heavier sleeve translation, lighter paper motion, rotation tied to motor, no random oscillation. Keep transforms independent of text layout.
- Texture cached/generated once, never per animation frame; reduce/dismiss it in accessibility settings. Don't read album bitmaps from disk each frame.
- ASCII embellishments never form the only name or status; hide decorative glyphs from TalkBack. Examples: `[SIDE A]`, `A1`, thin `+---+` separators, catalog marks.
- Visible pressed/focused/selected/error states; removing ripple cannot mean removing all action feedback.
- All state labels are localized text outside art. RTL and long metadata have a fallback layout.
- Candidate palette contrast, calculated against paper `#F2E6CE`: ink `#24231F` 12.72:1; green `#345A45` 6.30:1; error `#9A3429` 5.86:1; amber `#BC6C25` 3.20:1. Amber is decorative/large-text only on paper, not normal body copy. Darken it for small text. Actual alpha, texture and selected-state combinations still need checking.

## 9. Definition of a convincing prototype

The accompanying HTML board is a design study, not an Android build or audio simulator. It should show the recommended material language, a deck with a visible record/arm, sleeve/open/place/start/flip states, and source-informed connection recovery. Demo data is labeled. No actual Soulseek reconnection/download or real app preferences change.

Prototype acceptance: open locally without remote assets; navigate Home/Crate/Downloads/Settings; complete sleeve→placement→lead-in→playing→runout→flip; cancel a transition safely; reduced-motion behavior; no horizontal overflow at narrow mobile width; browser console clean. Sound is intentionally absent until Android audio spike/audition — don't synthesize browser noise and call it validated app crackle.

## 10. Build slices, not one giant redesign

**Slice 1 — restore trust:** actionable connection recovery, truthful dismiss semantics, provider-specific retry, single notices, honest quality/output labels. Validate exact failures before fixing them.

**Slice 2 — establish record context:** Crate detail/sleeve, unified entry points and Settings; keep old deck operational. Transfer UI grouping without invented completeness or unsafe bulk resume.

**Slice 3 — coordinate the deck:** typed state/commands, cancellable SFX, MediaSession parity, interruption tests. Ship no new animation tied to unverified timers.

**Slice 4 — physical ritual:** sleeve depth, spindle placement, lead-in/runout audio, explicit flip, controlled speed and accessible controls. Audition on real headphones/device.

**Slice 5 — finish the room:** supporting screens, performance, text-scale/TalkBack, screenshots, verified development APK.

Review gate: no production Kotlin edits from these documents alone. First choose the visual direction and album-container policy; then implement a vertical slice with red tests. No new production signing, release upload or PR as part of planning.
