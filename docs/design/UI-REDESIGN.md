# Runout UI redesign: interview record and checklist

This document records a screen-by-screen interview with the owner, held on 2026-10-21 against Runout 0.4.6-dev.1 on a Pixel 6 emulator. It is the agreed scope for the redesign on branch `ui-redesign`.

How to use this checklist:

- **Owner's words** are quoted as they were given in the interview, typos included.
- Every agreed change has an ID, such as `ON-3`, and a checkbox. Tick an item only after it is implemented and has been checked on the emulator or a device. Add the commit hash next to it.
- Implement exactly this. Do not add a redesign beyond it, and do not reinterpret it. Anything not listed keeps its current look and behaviour.
- **Landscape:** keep it working, but this pass does not redesign it (`GL-2`).

Status key: `[ ]` not started · `[~]` in progress · `[x]` done and verified.

---

## 1. Onboarding

### 1.1 Welcome
> "Need intro like vector animation which shows the vision for this app a small but pretty one … There should be app name. Remove Drop the needle. Connect soulseek should be selected as 1st and better cards which will respect the design more flat and less info and cluttred."

Intro story picked: **"Needle drop"**. Format: **"Compose code"**.

- [ ] **ON-1** Intro vector animation, about 3 s:
  - a sleeve slides in;
  - the disc slides out and lands on a platter;
  - the tonearm swings in and the needle drops;
  - the grooves glow TE orange;
  - "RUNOUT" types out in dot-matrix.
  It is built as self-contained Compose code (no new dependency) and can be skipped.
- [ ] **ON-2** Show the app name on the welcome. The home screen still shows no app name.
- [ ] **ON-3** Remove "Drop the needle."
- [ ] **ON-4** Connect Soulseek comes first. Listen offline comes second.
- [ ] **ON-5** Flatter, cleaner choices with less text and less clutter.

### 1.2 Offline folder step ("Bring your own records")
> "keep as it is"

- [ ] **ON-6** Keep the content. It only moves into the full-screen onboarding layout (`ON-10`, `ON-11`).

### 1.3 Login
> "remap the whole login flow like after that vector intro drop into login UI like spotify: Following the design lang ofc. And if there is no option to give Create an ID so Just Welcome then Souldseek ID a small info button if the user presses that then it will tell about the login process and sign up proceess of soulsleek client its like there is no sign up page and if they dont have soulseek account just try entring the id if prompts wrong its already taken try with another one! and Rememeber my sign-in should not be optional its an app so we can just keep them sign in."

The owner attached a screenshot of Spotify's welcome screen as the reference. Placement of "Listen offline": **"Small text link"**.

- [ ] **ON-7** After the intro, show a Spotify-style login in Runout's design language (light glass and blue accent, NOT Spotify's dark/green). Borrow only the structure:
  - hero artwork at the top: a collage of records and sleeves;
  - the app mark;
  - one big headline;
  - stacked full-width controls.
- [ ] **ON-8** Remove the "Sign in / Create an ID" tabs. The screen shows a welcome, then a "Soulseek ID" field with a small ⓘ, then the password and Connect.
  - The ⓘ explains that Soulseek has no sign-up page.
  - To make an account, enter the ID you want and it is created on first login.
  - If the app says the password is wrong, that ID is already taken: try another one.
- [ ] **ON-9** Remove the "Remember my sign-in" toggle. Users always stay signed in, with credentials in Android Keystore as today.
- [ ] **ON-9b** "Listen offline" becomes a small, quiet text link at the bottom.

### 1.4 Download folder step ("02 / Keep your records")
> "Bring all the things near to bottom so that its easier for one hand use and there should not be continue button there is no need of that after choosing the download fodler it should go to the next step. and also its better when the onboarding section have the full screen or seperate screen."

- [ ] **ON-10** The whole onboarding becomes its own full screen (intro, login and steps). It is no longer a glass sheet over the room.
- [ ] **ON-11** Content and actions sit near the bottom of the screen, for one-handed use.
- [ ] **ON-12** No Continue button. Picking the folder moves on automatically. Keep a quiet way to choose later.

### 1.5 Share step ("03 / Give something back")
> "after the later step it should be just prompted as in button and with a caution logo and descriptin as the folder is getting shared. and a small skip button,"

- [ ] **ON-13** Show a simple prompt:
  - one "Share a folder" button with a caution icon;
  - a short description saying the chosen folder becomes public to Soulseek users;
  - a small Skip button.
  Remove the toggle and "Not now — continue".

### 1.6 Stay-online step ("04 / Choose your rhythm")
> "those cards are good but i will prefer insted of selecting a button inside a card just change the color of the card which user will be selecting and remove that continue button."

- [ ] **ON-14** Keep the two cards. Remove the in-card buttons ("Selected" / "Use background mode"). The whole card is the tap target, and the selected card shows by its colour.
- [ ] **ON-15** No Continue button. Tapping a card selects it and moves on.

### 1.7 Finish ("Ready to spin")
> "this feels way to static: It does not need to show what user have selected just greet and with a popping in animaiton drop to the home page"

- [ ] **ON-16** No summary card. Greet the user, then play a "pop in" animation into the home room.

---

## 2. Home (the room)

### 2.1 Deck look
Answers:
- direction: **"Teenage Engineering"**;
- accent: **"TE orange"**;
- details: **"Physical keys, Engraved labels & ticks"**;
- LCD: **"small font and small dot matrix but still readble"**;
- body: **"Glass + TE details"**;
- platter and arm: **"Keep strobe dots, Orange details on arm, Keep tonearm as is"**;
- keys: **"Keep layout"**.

- [x] **DK-1** TE orange (about `#FF5A1F`) is the deck's accent for keys, lights and highlights.
- [x] **DK-2** Keep the frosted-glass plinth and add TE details on top. It does NOT become a solid aluminium body.
- [x] **DK-3** Physical keys: visible depth, a shadow, a pressed state that travels down, and a small light.
- [x] **DK-4** Engraved labels and ticks: tiny printed labels by the keys, scale ticks, and model-plate details.
- [x] **DK-5** The pixel screen keeps its role, with a smaller font and a smaller dot matrix that is still readable.
- [x] **DK-6** Keep the strobe dots (restyle only).
- [x] **DK-7** Keep the S-shaped tonearm as is, and make the counterweight and/or headshell TE orange.
- [x] **DK-8** Key positions stay as today (START/STOP left, ◀◀ ▶▶ middle, LCD right).
- [x] **DK-9** Performance: cache the static deck layers, and spin the platter and record as their own layer instead of redrawing the whole deck every frame.
  - This was agreed in discussion: the deck stays code-drawn vector. Figma is used as the design reference, and Rive or Lottie only for the intro and transitions.

- [x] **DK-10** *(added after the interview)* "the disk spins way to fast keep it slow and real".
  - First pass: true 33⅓ rpm with real mains-strobe physics and a slower spin-up. The owner said it was still too fast.
  - Now: the picture turns at half speed (one turn every 3.6 s). The strobe is re-tuned to a 50 Hz flash, so the dots stand still at that speed.
  - The music still plays at its recorded speed.
- [x] **DK-11** *(added after phase-2 review)* "can we get some grassy finish over the buttons keeping the current texture". The deck keys get a clear glass coat over their existing colours: a glossy top, a diagonal reflection and a bright rim.
- [x] **HM-13** *(added after phase-2 review)* A weird shadow flashed between sleeves while flicking (the owner sent a screenshot). Sleeves no longer cast their own shadows. One soft floor shadow sits under the front slot and fades in only when a sleeve settles square.

### 2.2 Crate, caption and footer
Answers:
- crate: **"Keep coverflow, refine"**;
- caption: **"Shorter"**;
- footer: **"Icon tab bar"**;
- disc motion: **"Pull-out, Drag to platter"**;
- pull-out: **"Slide up + spin-up"**;
- drag: **"Shadow lift, Drop thud, Haptics"**;
- buttons beside a pulled disc: **"Long-press menu"**.

- [x] **HM-1** Keep the coverflow. Refine its perspective, depth and shadows.
- [x] **HM-2** Caption: title and artist only, with format/hi-res as a tiny tag. The licence moves into the record's details.
- [x] **HM-3** Replace the scrolling pill strip with an icon tab bar (icons and tiny labels) that always fits: Dig, Drops, Friends, Settings.
- [x] **HM-4** Pull-out: the disc slides up out of the sleeve with a slight overshoot and spins up a little.
- [x] **HM-5** Drag: the shadow lifts while the disc is held. The landing has a "thud" (a small squash, the shadow settling and a soft thud sound), and haptics fire on pickup and on landing.
- [x] **HM-6** Remove "Sleeve back" / "Send with audio" / "Remove" beside the pulled disc. Use a long-press menu on the sleeve or disc instead, with Put back, Send, Remove and Details.

### 2.3 Top bar
> "Move change folder to settings, Keep rescan but icon of refresh matching TE color, Keep crackle and keep online as in green and red dot and download number (grey if paused, green downlaoding, red if error) remove time, keep number of records"

- [x] **HM-7** "Change folder" moves to Settings › Library.
- [x] **HM-8** Rescan becomes a refresh icon in TE orange.
- [x] **HM-9** Keep the Crackle toggle.
- [x] **HM-10** Online status: a green dot when online, red when offline or on error.
- [x] **HM-11** Download count colour: grey when idle or paused, green while downloading, red on error.
- [x] **HM-12** Remove the clock. Keep the record count.

---

## 3. Dig (search)
> "Dig becomes its own full screen (like onboarding), not a sheet over the room. remove all sources keep on this device and soulseek as two options. remove the option of songs. it will only show albums need backend changes. It should only show folders. turn that filters and sort option to an icon on the bottom right howvering."

Results: **"One tap = preview, Fix ranking, Peer stats as icons, Cover art on cards"**.
Folder preview: **"Sleeve-style header, Clean track names, Drop the disclaimer, Peer info small"**.

- [ ] **DG-1** Dig is its own full screen.
- [ ] **DG-2** Two sources only: On this device and Soulseek. Remove "All sources".
- [ ] **DG-3** Albums (folders) only. Remove the Songs option and the All/Songs/Albums row. This is a backend change: results are album/folder groups only.
- [ ] **DG-4** "Filters & sort" becomes a floating icon button at the bottom right.
- [ ] **DG-5** Result cards show cover art when the folder shares one.
- [ ] **DG-6** Tapping a result card opens the preview. Remove the "Preview album folder" / "Show tracks" buttons.
- [ ] **DG-7** Peer stats (slot, queue, speed) become tiny icons or colour.
- [ ] **DG-8** Fix ranking: exact album/artist matches first. Example: "moon safari" ranked "(2010) Active 2.0_ The BT Workout" above "Air - Moon Safari".
- [ ] **DG-9** Preview header in sleeve style: cover, title, artist and a spec tag (e.g. FLAC 24/192 · 1.7 GB).
- [ ] **DG-10** Clean track names: "01  La femme d'argent", not "Air - Moon Safari - 01 La femme d'argent.flac".
- [ ] **DG-11** Remove the "This is a peer's folder listing…" disclaimer.
- [ ] **DG-12** Peer info becomes small: the name plus a small Message icon.

---

## 4. Friday Drops
Answers: **"Record-shop shelf, Claim counter as TE, Tap = preview first"**. Drops stays a sheet.

- [ ] **DR-1** The 10 drops appear as big sleeves (a shelf or grid) instead of a text list.
- [ ] **DR-2** The claim counter becomes three small TE lights or tokens that switch off as you claim.
- [ ] **DR-3** Tapping a drop opens a preview (track list and a short listen), with Claim inside the preview.

## 5. Friends (Soulseek messages)
Answers: **"Full screen like Dig, Chat list first, Share records in chat, Friends list"**.

- [ ] **FR-1** Friends is its own full screen.
- [ ] **FR-2** Chat list first (initial, last message, unread dot). A floating "+" starts a chat by username.
- [ ] **FR-3** A friends list of saved usernames, with online/offline dots.
- [ ] **FR-4** Share records in chat: a record card the other person can tap to preview or download. This needs a wire-format design over Soulseek private messages.

## 6. Pressing Plant
> "remove this whole module."

- [~] **PP-1** *(code and tests removed; docs still to update)* Remove the tab, the screen, pressing creation, and their tests and docs.
- [x] **PP-2** Do not delete user data: records that were already pressed stay playable and removable.

## 7. Settings
Answers: **"TE hardware EQ, Shorter copy, Simple list layout"**. Settings stays a sheet.

- [ ] **ST-1** A plain list (Sound ›, Soulseek ›, Library ›, About ›) where each row opens its own page. This replaces the chip tabs.
- [ ] **ST-2** Shorter copy: one-line descriptions, with details behind an ⓘ.
- [ ] **ST-3** TE hardware EQ: orange fader caps, a tick scale, and presets as small keys.
- [ ] **ST-4** Library gains "Change folder" (see `HM-7`).

## 8. Downloads
Answers: **"Controls as icons, Shorter copy"**. Downloads stays a sheet.

- [ ] **DL-1** Pause all / Resume all / Turtle mode become small icon keys.
- [ ] **DL-2** Shorter copy: remove the two explanation lines and show a small empty state instead.

## 9. Global
Answers: **"Haptics everywhere, Landscape later"**. The owner did not pick "TE look everywhere".

- [~] **GL-1** *(done: deck keys, tabs, pills, toggles, menu rows, disc pickup/landing; still to do: the new onboarding and Dig screens)* Haptics on keys, toggles, tab switches, and disc pickup/landing.
- [ ] **GL-2** Portrait first. Landscape must keep working but is not redesigned now.
- [ ] **GL-3** TE styling stays limited to what is listed: the deck, the EQ, spec tags, claim tokens and the refresh icon. Do not restyle the rest of the app into TE hardware.

## 10. Bugs found during the interview
- [x] **BG-1** The record count says "1 records". It should say "1 record".
- [ ] **BG-2** Search results say "1 matching tracks". It should say "1 matching track".
- [ ] **BG-3** With the Dig sheet open, Back closed the whole sheet. Back should dismiss the keyboard first.
- [ ] **BG-4** During setup, the accessibility tree (uiautomator/TalkBack) kept the previous step's content after moving on. Verify, then fix.
- [ ] **BG-5** Soulseek searches for "Pink Floyd Animals" and "Radiohead" returned 0 FLAC results, while "moon safari" returned 273. This may be emulator NAT. Verify on a real phone.
- [ ] **BG-6** The setup step numbering looked like it jumped from 02 to 04. Check the step order and numbering.

---

Implementation decision (owner, 2026-10-21): Claude implements this directly on `ui-redesign`, instead of handing a prompt to Hermes.
