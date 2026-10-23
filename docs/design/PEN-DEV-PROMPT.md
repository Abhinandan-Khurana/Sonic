# Prompt for pen.dev (Opus 5.5 with the image model): Runout TE enhancement pass

You're working in `docs/design/runout-te.pen`. It's the Teenage Engineering-style redesign of Runout, an Android vinyl player for FLAC albums. Its controls are a 3D turntable (the RG-1 deck), a crate of record sleeves you flick through, and records you pull out and drop onto the platter.

The owner has already reviewed every screen in an interview. Those decisions are final and recorded in `docs/design/TE-INTERVIEW.md`. **Read it first. Don't undo anything it lists.**

Your job is an enhancement pass in four parts:
1. **Image-model explorations** where imagery would genuinely help.
2. **Two new interactions:** putting a record back, and an occupied-platter error.
3. **Contextual help icons.**
4. **A consistency sweep across all TE screens.**

## Ground rules

- **Work on the TE frames only.** These are the top-level frames named `TE · …`, to the right of the old 0.5.1 frames. Never edit the old frames; they're the reference. Never edit `TE · Foundations` tokens without saying so.
- **Explorations go next to the approved screens, not over them.** Put every image-model idea in new top-level frames named `Exploration · <screen> · <idea>`. Place them below the TE section with `FindEmptySpace`, and keep `placeholder: true` while working. The owner will pick favourites; approved screens change only for the items in parts 2–4 below.
- **Design system (keep exactly):**
  - **Font:** all text uses the `$te-font` variable. It's currently "Array", loaded from the document's fonts list. Never hard-code another family.
  - **Colour:** canvas `#FFFFFF`; ink `#000000`; greys `#4D4D4D`, `#767676`, `#B2B2B2`, `#CCCCCC`, `#E5E5E5`, `#F5F5F5`.
  - **Accents (signals only):**
    - orange `#F05A24` = playing, now spinning;
    - blue `#0071BB` = links and info;
    - green `#006837` = online;
    - amber `#FAB413` = warnings;
    - red `#B81D13` = errors and destructive actions;
    - deck LED red `#FF2E14`.
  - **Spacing:** the 11-px grid, 22 / 33 / 66; screen side padding 22.
  - **Shape:** corner radius 0 everywhere. No drop shadows; 1-px hairlines (`#CCCCCC`).
  - **Components:** use the existing components (`TE Key`, `TE Key / Outline`, `TE Key / Signal`, `TE Link`, `TE Tag`, `TE Tag / Mono`, `TE Switch / On|Off`, `TE Status`, `TE Field`, `TE Row`, `TE Toggle Row`, `TE Screen Head`, `TE Nav Cell`, `TE Nav Bar`). Don't build look-alikes.
  - **Scrims:** every panel and dialog sits on one grey scrim, `#767676` at 60 %.
- **Don't change these (owner decisions):**
  - The deck is the real Blender render, `images/rg1-v3-*.png`. Never redraw, regenerate or restyle it.
  - Home has a plain white canvas behind the deck. No black band.
  - The top bar has no RUNOUT wordmark and no crackle switch or 24/96 chip; online is just a green dot.
  - Crate sleeves are copied from the old "Home · portrait" design. The vinyl in hand is `images/vinyl-in-hand-night-drive.png`.
  - The track wheel under the caption is side-numbered (A1, A2, B1…); the focused song is sharp and the others are blurred.
  - Dig results have no album art (two compact lines).
  - The download key is black.
  - The settings rows sit just above the faded RUNOUT watermark.
  - Crackle lives in Settings › Sound.
  - The crate is scaled to 85 % and sits 78 px below the deck caption. That headroom keeps the vinyl that rises out of the sleeve from ever overlapping the deck. Keep it on every Home state, and don't push the crate back up.
- **Image model rules:**
  - Use `Generate("ai", frameId, prompt)` on a frame to give it an image fill. Never put readable text, logos or the RUNOUT wordmark inside a generated image; real text stays as text layers in `$te-font`.
  - No real album covers or real artists' likenesses. "Moon Safari / Air" is a real album, so use abstract artwork for it.
  - Keep generated imagery in one family: Teenage Engineering catalog photography. Studio light, seamless white or very light grey sweep, crisp industrial objects, soft natural contact shadows inside the photo (the UI itself stays flat), restrained colour with one accent at most.
  - For album art, aim for flat graphic or abstract art that would print well on a sleeve. Square, no text.
  - After generating, check every frame with `TakeScreenshot`. If an image fights the white UI or the accents, regenerate it with a tighter prompt rather than adding UI effects.

## Part 1: image-model explorations (2–3 options each)

Place these as `Exploration · …` frames, each a copy of the approved TE screen with only the image slots changed:

1. **Onboarding · Sign in: the cover collage** (the 3×2 grid at the top). Generate six fictional album covers as one coherent "crate":
   - Night Drive (synthwave dusk, violet to rose, a low sun);
   - Static Bloom (teal with an orange bloom);
   - Paper Moons (cream paper with a black disc);
   - Summer Tape (pink and red);
   - Blue Hour (blue with an amber sun);
   - After Hours (charcoal and grey).
   
   Flat, print-like, no text. Option B: the same six albums as photographed physical sleeves, slightly worn, shot top-down on white.
2. **Onboarding · Intro storyboard (3 s)**, with five keyframes. Generate photographic keyframe references for:
   - a sleeve sliding in;
   - the disc sliding out of the sleeve;
   - the disc landing on a white turntable platter;
   - the tonearm dropping;
   - a light sweep across the grooves.
   
   These are motion references only. Leave the real deck render in the final screens.
3. **Onboarding steps 02–05**: one small supporting image per step, placed above the copy, beside the giant step number, never replacing it:
   - 02 a crate of FLAC records on a shelf;
   - 03 a folder or drawer of records;
   - 04 two people's crates connected (sharing);
   - 05 day versus night listening (stay online).
   
   Keep each image under about 140 px tall so the bottom-anchored copy and actions don't move.
4. **Onboarding · Welcome**: an alternative where a soft, photographic studio light pools on the white floor under the deck. Generate it as a background frame behind the deck render, not as a new deck.
5. **Home · empty (no folder)**: a calm still life of an empty record crate or a single blank white sleeve, above "CHOOSE YOUR MUSIC FOLDER".
6. **Drops · shelf / preview**: abstract cover art for the four drops ("Another Day, Another Way", "Curses From Past Times", "Brick City Love Song", "Riding Alone For Thousands Of Miles"). These are Creative Commons albums, so it's fine for the art to be abstract.
7. **Friends · chat**: abstract cover art for the shared-record cards.
8. **Empty and edge states with no image yet**: a small spot image for each.
   - "no search results" (Dig);
   - "no chats yet" (Friends);
   - "downloads idle" (Downloads);
   - "repair found nothing wrong" (Library).

## Part 2: two new interactions (approved behaviour, design them in the TE screens)

**A. Putting a record back**

Today there's an animation that puts a disc onto the platter, but none that brings it back. Design `TE · Home · putting a record back` as a sequence of states, plus a storyboard frame like the intro one. The return path is the pull path played in reverse:

1. **Trigger:** the eject key on the deck, "put back" in the record menu, or dragging the disc off the platter.
2. **The deck winds down first:** the platter slows to a stop, the tonearm lifts and swings back to its rest, and the LED shows "EJECT".
3. **The disc lifts off the platter**, flies back to the crate, and its own sleeve comes to the front of the coverflow.
4. **The disc slides down into the sleeve**, which settles with a small bump. The sleeve label goes back to normal; the platter is empty and the LED shows "DROP A RECORD".

Show the in-between states as frames, with the motion described in short captions (timing about 0.8–1.2 s; haptic tick when it lands in the sleeve).

**B. The platter is occupied**

If a record is already on the platter and the user pulls a different record and drops it on the platter, the drop is refused. Design `TE · Home · platter occupied`:
- The dragged disc bounces back to its sleeve; it doesn't land.
- An error shows with the copy **"First, remove the current record from the platter."**
- Style: the error is a toast above the tabs, a black bar with a **red** square (red = error) and an inline **"eject"** action link that runs the put-back from part A.
- While dragging over the occupied platter, the dashed drop ring shows red instead of black, so the refusal is visible before letting go.

## Part 3: contextual help icons

- **Build the component first.** Create one reusable `TE Help` component: a 16×16 square with a 1-px ink outline and a centred "?" in `$te-font` 10 px. Tapping it opens a help panel; reuse the "Soulseek ID help" panel pattern (grey scrim, white panel with a 2-px ink top edge, numbered points, "got it").
- **Where to add it:** next to each term below, with real, short help copy. Plain words, two or three numbered points at most.
  - Sound: pure, hi-res output, the signal chain verdict, EQ, ReplayGain (off / album / track), limiter, warmth.
  - Soulseek settings:
    - find missing covers online (it sends artist and title to MusicBrainz, the Cover Art Archive and the Internet Archive);
    - share selected FLAC folder;
    - stay online in the background;
    - the "not encrypted" warning.
  - Dig: the "free upload slots only" filter; what the peers number means.
  - Drops: claims (three a week, the same ten for everyone, and listening previews don't use a claim).
  - Downloads: "background usage is off".
  - Home, the first time: one help on the crate covering flick, pull up, drag onto the platter, tap the record to flip it, hold it to slow it, and long-press for the menu. Also the track wheel, and the long-press on the deck's LED that opens the signal chain.
- Don't add help where the label already says it all.

## Part 4: consistency sweep across all TE screens

Fix these directly in the TE screens, list every fix in a note frame `TE · Sweep notes`, and screenshot before and after for anything visual:
- Use the same `TE Screen Head` on every page: index line, "close ×", 44-px title, and padding [22, 22, 11, 22]. Back links read "← <parent>" at 12 px, always the first item in the body.
- Every screen keeps 22 px side padding and gaps on the 11 grid. No stray 8, 15 or 20 px gaps except inside components.
- Right-hand value columns (sizes, times, peer counts, "close ×") must not clip with Array's wider glyphs. Give those columns a fixed width, right-aligned, with about 12 px of right padding. Check every list for this.
- All panels: the same scrim, the same 2-px ink top edge, a close in the same place, and the primary key full width at the bottom.
- Keys: one height (padding 16 / 22), full width when they're the main action of a panel or step.
- Status squares: green = online, hollow = offline, orange = playing or active, amber = warning or paused, red = error. Make sure every status uses this mapping.
- The nav bar is identical everywhere, with the right cell active (dig / drops / friends / settings; none on Home).
- Text: no text node may fall back to another font. Check every text node uses `$te-font`.
- Landscape Home must stay in sync with portrait, including any new help icon and toast placement.

## When you're done

Run a layout check (nothing clipped or collapsed) on every TE and Exploration frame. Then reply with a short list:
- the Exploration frames you made, with one line each saying what to look at;
- the frames you changed for parts 2–4;
- anything you weren't sure about.

Don't delete or overwrite any TE frame to "start fresh".
