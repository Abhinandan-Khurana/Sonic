# Runout TE redesign: screen interview (pen.dev)

Design file: `docs/design/runout-te.pen`. The TE frames are the ones named "TE · …", to the right of the 0.5.1 frames.

This checklist records the owner's answers screen by screen. Each item is checked off once it is built and verified on the emulator.

Rules that apply to every screen (decided 2026-10-22):
- [ ] Dot-matrix font everywhere; TE spacing 11 / 22 / 33 / 66; zero radius; no shadows; hairlines.
  - Font: the owner switched it from Silkscreen to **Array** (Indian Type Foundry, via Fontshare; regular / semibold / bold, plus Wide).
  - In pen.dev the `te-font` variable is "Array", loaded from `docs/design/fonts/array/` (gitignored, not redistributed).
  - Before shipping Array in the app, check its licence (`fonts/array/LICENSE.txt`, FFL) allows embedding in an APK.
- [ ] Keep the current UX:
  - crate coverflow (front sleeve with turned sleeves beside it);
  - tap pulls the vinyl up, then drag it onto the platter;
  - a dashed drop outline;
  - bottom tabs, with sheets that close back to the room.
- [ ] The vinyl is the hero, with a subtle sheen and a faint floor reflection ("not much").

## 1. Home (TE · Home)

Deck backdrop:
- [ ] Remove the black background behind the deck. "the black color is making it seperat from the whole design." The owner's first idea was "something glassy which will disslove with the current theame", but the final word was: "Just keep it plain white."
- [ ] Keep the "RG-1" and "Side A" caption under the deck.

Top bar:
- [ ] Remove the "RUNOUT" wordmark.
- [ ] Remove the word "online": "Only keep a green dot for online, and the rest is fine." Keep the downloads count and the record count.
- [ ] Remove crackle and its switch.
- [ ] Remove the 24/96 bit-rate chip, the "phone speaker" text, and the orange square and rectangle.

Crate:
- [ ] Keep it as designed: front sleeve with turned sleeves beside it, and a faint floor reflection.

Vinyl in hand:
- [ ] The 2D vinyl you pull out reverts to the earlier build's drawn record. It is not the new photo-like face.
  - In the design: `images/vinyl-in-hand-night-drive.png`, captured from the build.
- [ ] Sleeve covers in the crate use the older design ("Home · portrait"):
  - the teal turned sleeve on the left;
  - the cream turned sleeve with orange and black art on the right;
  - the far-right sliver;
  - the old Night Drive front cover.
  
  TE tag, sleeve label and faint reflection on top. (Owner, 2026-10-22: "check the older design… Home · portrait".)

Track list under the caption (in the empty space):
- [ ] Show the album's songs there, songs only. The list stays in that place and scrolls.
- [ ] About four names show at a time; scroll to see the rest.
- [ ] Number songs by side, as on the plate (sides A, B, C, D…): A1, A2, B1, B2, B3…
- [ ] A blur layer: only the highlighted song is sharp, and the others in view are blurred. "Only the song that I'm seeing should be visible there."
- [ ] Keep the "now spinning" line. The owner didn't ask to remove it.

## 2. Home · landscape
- [ ] Mirror portrait on the right half:
  - the crate with the old-design sleeves and the reflection;
  - the caption;
  - the scrollable track wheel;
  - the "now spinning" line.

## 3. Home · empty (no folder)
- [ ] "Remove the empty slot and pull 01 Yoshil [your shelf] down. Also remove that text 01 yourself [01 your shelf]."
  - Delete the three empty slot outlines.
  - Delete the "01 your shelf" label.
  - Move the title, line and key down toward the bottom (thumb reach).

## 6–14. Onboarding (owner's spec, verbatim)
- [ ] ON‑1: an intro of about 3 s that tells the needle-drop story: a sleeve arrives, the disc slides out and lands on a platter, the tonearm swings in and drops, the grooves glow, and RUNOUT types out in dot-matrix. It's skippable by tap, and skipped entirely when system animations are off.
- [ ] ON‑2: the app name shows during onboarding. The room still shows no app name. The wordmark stays a drawn graphic with contentDescription = "Runout", not a Text node; RunoutFlowTest asserts there's no "RUNOUT" text in the room.
- [ ] ON‑4, ON‑9b: Soulseek sign-in comes first. "Skip, listen offline" is a small quiet link at the bottom (a test asserts it sits in the bottom 30 %).
- [ ] ON‑8: no "Sign in / Create an ID" tabs and no sign-up page. Signing up is signing in with a new ID. The ⓘ explains it with the current copy (a test matches "Soulseek has no sign-up page").
- [ ] ON‑9: no "Remember my sign-in" toggle. Always call actions.signInSoulseek(id, password, true). Passwords never go into saved instance state.
- [ ] ON‑10, ON‑11: onboarding is its own full screen, with content and actions gathered near the bottom for one-handed use.
- [ ] ON‑12, ON‑15: no Continue buttons on the auto-advancing steps. Picking the download folder advances. Tapping a rhythm card selects it and advances.
- [ ] ON‑13: the share step has one button with a caution icon, copy saying the folder becomes public, and a small Skip.
- [ ] ON‑16: no summary screen. A greeting pops in, then you drop into the room.
- [ ] FU‑1: with the keyboard up, nothing slides under the titles. The hero gives way gracefully.

From the pen.dev review:
- [ ] Sign in: keep as shown (cover collage, fields, grey connect key, quiet offline link).
- [ ] Soulseek ID help panel: a lighter scrim (white or grey, not dark) to match the white theme.
- [ ] Intro and Welcome use the white canvas, like Home. (Claude's call, following the owner's "black separates it from the design"; owner to confirm.)

Onboarding steps 02–05 and Welcome:
- [ ] Poster style: number only. Keep the giant dotted step number; drop the pictogram tile and its "fig." caption.
- [ ] Progress: squares only. Drop the "02 / 05" text and keep the five squares.
- [ ] Welcome: "WELCOME IN, <Soulseek ID>." when signed in, "WELCOME IN, LISTENER." when offline.
- [ ] Welcome shows the deck: the RG-1 appears above the greeting, so the drop into the room continues from it.

## 15–19. Dig
- [ ] Header: keep it. "02 / dig", "close ×", big DIG title, underlined search field with a square black search key, and the two-cell "on this device | soulseek" switch.
- [ ] Soulseek results: "Remove the album art thing completely and redesign that segment only for space efficiency and a non-cluttered view."
  - Done in the design: two compact lines per album, with no art.
  - Line 1: index, title, size.
  - Line 2: artist, format (orange text when 24-bit), green peers square and count.
  - Hairlines between albums.
- [ ] Folder preview: the download key is black, not orange. Orange stays for "playing".
- [ ] Filters panel: keep it as shown.

## 20–21. Drops
- [ ] Shelf: keep it as shown (week, claims left with three squares, 2-column catalog tiles).
- [ ] Preview: keep it as shown (big art, licence tag, claim key, 30 s previews).

## 22–25. Friends
- [ ] Chats list: keep it as shown (square initial avatars with status squares, chat rows, unread square, "+ new" key).
- [ ] Chat thread: bigger record cards. Shared records are full-width cards with bigger art (110 px).
- [ ] New chat and Send a record panels: "Make it gray." The scrim behind them is grey, not dark.

## 26–28. Settings list, Sound, Audio quality
- [ ] Settings list: "Bring all the rows down above the watermark. Don't remove the watermark." The rows sit at the bottom, just above the big faded RUNOUT and the build line.
- [ ] Signal chain (track → runout → output), now that the home chip is gone: both of these.
  - At the top of Settings › Sound.
  - As the Audio quality panel, opened by a long-press on the deck's LED window.
- [ ] Sound page: keep it as shown.

## 29–33. Library, About, Soulseek, Downloads
- [ ] Library: move crackle to Sound. "The deck" section with the crackle switch leaves Library and becomes section 05 in Settings › Sound. Everything else in Library stays.
- [ ] About: "move Runout 0.6 versioning to the bottom, and also move Record Soul's Leak Drops Fonts [records / soulseek / drops / fonts / libraries] and all these rows to the bottom. Keep Runout at the bottom, with the rows above it."
- [ ] Soulseek settings: keep them as shown.
- [ ] Downloads, and the background-usage-off state: keep them as shown.

## 34–37. Record menu, details, remove, toast
- [ ] Record menu, Record details and Remove record?: keep all three as shown.
- [ ] Toast: keep it above the tabs. It may briefly cover the track wheel.

## All panels and dialogs
- [ ] One grey scrim (#767676 at 60 %) behind every panel and dialog: ID help, filters, new chat, send a record, audio quality, record menu, details and remove. This replaces the earlier per-screen "lighter" and "gray" answers.

## Added after the interview (owner, 2026-10-22)
- [ ] **Put-back animation:** today a disc can be put onto the platter, but nothing animates it back.
  - Trigger: eject, "put back", or dragging the disc off the platter.
  - The platter winds down, the tonearm returns to its rest, and the LED shows "EJECT".
  - The disc flies back to its sleeve, which comes to the front of the crate, and slides in.
- [ ] **Platter occupied:** a record is on the platter and the user drops a different one.
  - Refuse the drop; the dragged disc bounces back to its sleeve.
  - Show the error "First, remove the current record from the platter." The owner wrote "current index", meaning the current record.
  - The error comes with an "eject" action.
  - The drop ring turns red while hovering over an occupied platter.
- [ ] **Pulled vinyl must not overlap the deck:** the owner saw it overlapping on "Home · pulling a record" and asked to "shrink the others down a bit more down".
  - Done in the design on every portrait Home state:
    - the crate scaled to 85 % and moved 78 px down, leaving headroom for the rising vinyl;
    - the caption title at 18 px;
    - the track wheel at 4 visible songs.
- [ ] **Help icons:** a ⓘ / "?" help wherever a setting or gesture needs explaining. The full list is in `PEN-DEV-PROMPT.md`, part 3.
- [ ] **Layout sync:** a consistency sweep across all TE screens (headers, padding, right columns, panels, keys, status colours, nav, font). The full list is in `PEN-DEV-PROMPT.md`, part 4.
- [ ] **Image-model explorations:** handed to pen.dev's Opus 5.5 with `PEN-DEV-PROMPT.md`, part 1. The owner picks favourites.

## pen.dev enhancement pass (2026-10-22): results and follow-ups
pen.dev's Opus 5.5 ran `PEN-DEV-PROMPT.md`.

What it built:
- Parts 2–4:
  - the put-back states and storyboard;
  - "platter occupied" in portrait and landscape;
  - the TE Help component, 18 help panels, and the help icons;
  - the sweep, recorded in "TE · Sweep notes" and `sweep-shots/`.
- Part 1 is mostly unbuilt: it ran out of image generations. Only "Exploration · Sign in · A / B / C" exist.

Claude's fixes afterwards:
- [x] The LED readouts are now real Blender renders, not text laid over the render:
  - `rg1-v3-eject.png`: the record on the platter, the arm at rest, "EJECT";
  - `rg1-v3-eject-empty.png`: an empty platter with "EJECT";
  - `rg1-v3-empty.png`: "DROP A RECORD".
  
  These are swapped into the put-back states and the storyboard.
- [x] The "now spinning" line is restored on every portrait Home state (it had gone missing). The caption height now fits its content.
- [x] The crate sits at the same y (327) on every portrait Home state. The pull and drop states got Home's band and caption spacing.
- [x] "TE · Components" moved below "TE · Foundations" so they no longer overlap.

Owner's decisions on pen.dev's open questions:
- [ ] Status colours: keep pen.dev's changes. A completed download is ink, not green, and the "claimed" toast square is blue (info). Orange stays for playing only.
- [ ] Error copy is lowercase, like the rest of the UI: "first, remove the current record from the platter." (Done in the design.)
- [ ] Record menu, details and remove stay as centred cards, not bottom panels.
- [ ] Image-model explorations: skipped for now. Build without them and revisit later; the Sign in explorations A / B / C stay for reference only.
- [ ] Still open: the refused record in "platter occupied" needs its own in-hand disc. Only Night Drive's is captured. Capture one from the build once a second album with art is on the emulator.

## More owner asks (2026-10-22, after the pen.dev pass)
- [ ] **A proper flip animation** for side A → B and B → A. The storyboard frame is "TE · Home · flipping the record · storyboard (~0.9 s)", with five keyframes rendered from the real deck and record (`images/rg1-v3-flip-1…5.png`):
  1. Tap the record with the needle up; if it's playing, the arm returns to its rest first. LED: FLIP.
  2. The disc lifts off the spindle.
  3. It turns over around its left–right axis.
  4. It settles with side B up.
  5. It lands and cues: LED "CUE B1 · title", time 00:00, a thud and a haptic tick. The track wheel jumps to B1 and the top bar shows side B.

  The same motion runs back from B to A.
  - Flat fallback: the disc squashes to a line and opens on the other side.
  - Reduced motion: a 150 ms cross-fade of the label.
- [ ] **Remove the dark shadow from the album art.** The dark "ground" band at the bottom of the sleeve covers is gone from every front and mirrored sleeve in the design.
- [ ] **Stickers on the cover look like a brand, not dotted text:**
  - the hi-res sticker is a round orange sticker with a white edge, "HI-RES / 24/96" in heavy Inter (sans);
  - the sleeve label is a black tab, "ON THE PLATTER" in Inter bold capitals;
  - both are tilted a little, about −10° and +3° (owner: "good job with the stickers tilt it a bit").
  - This is the one place a rounded shape and a non-dot font are allowed: they're physical stickers on the sleeve.
- [ ] **Show which side is playing** in the top-left of Home (it was empty):
  - an orange square, "side a" in bold, then "a2 · 2 / 4";
  - "platter empty" or "no record" with a hollow square when nothing is on the platter;
  - landscape Home gets the same.

## 3D vinyl in hand (owner approved, 2026-10-22)
The goal is "perfect animation". The record is one 3D object from the moment it leaves the sleeve until it's on the platter and back. Sleeves stay 2D.
- [ ] A full-screen transparent 3D layer, active only while a record moves (pull, drag, drop, put back, flip) and paused otherwise. It shares the deck's Filament engine and material, so there's no second shader compile, and the disc is the same model as the record on the platter.
- [ ] The overlay camera is the deck camera extended to the whole screen (an off-centre frustum), so a disc over the platter projects onto exactly the deck's pixels. The landing swaps to the deck's record with nothing visible changing.
- [ ] In hand, the disc keeps a steady on-screen size, tilted toward you, catching real light and sheen. Approaching the platter it eases into the platter's angle and lands on the spindle.
- [ ] Coming out of the sleeve, the disc is clipped at the sleeve's top edge until it clears, so it still slides out from behind the cover.
- [ ] Fallback: no GPU, or the JVM tests, keep today's 2D drawn disc. Reduced motion uses the storyboards' short cross-fades.
- [x] **Spike first:** pull, drag, land and put back for one record, timed on the emulator, before the full build. Done 2026-10-22 on the Pixel 6 emulator, uncommitted on `deck-v3`:
  - The 3D disc was ready **40 ms** after appearing on each pull; the shared engine means no shader stall.
  - Frames: 113 drawn while pulling and dragging, 1 over 20 ms. A pull and put-back drew 86, with 0 over 20 ms.
  - Landing handover: the platter before and after the 3D disc hands over differs by **0.5 % RMSE** (anti-aliasing noise; same label, gaps and angle).
  - Put-back via the sleeve: the 3D disc slides down behind the sleeve.
  - Code:
    - `ui/deck/FilamentShared.kt`: one engine, the loader and the studio lights;
    - `ui/deck/DiscRenderer.kt`: the full-screen overlay with the deck camera's off-centre frustum;
    - `DeckModel.heldDiscTransform`;
    - `DeckLink` in `Turntable.kt`;
    - the wiring in `ListeningRoom.kt`.
  - A unit test checks the landing pose equals the platter pose at any angle, and that the held disc stays under the finger.
- [ ] Still to build after the spike:
  - eject from the platter, with the disc flying back to its sleeve (the storyboard);
  - the flip in 3D;
  - the "platter occupied" bounce-back;
  - a contact shadow under the held disc;
  - removing the spike's timing logs.

## 4–5. Home · pulling a record / dropping on the platter
- [ ] The dashed drop ring is in ink (black), not orange. Everything else stays as shown: the disc rising behind the sleeve, the sleeve labels and the "let go to drop" line.

