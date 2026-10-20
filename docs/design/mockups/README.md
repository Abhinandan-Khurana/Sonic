# Runout listening room: mockups (design pass 3)

These are exports from the pen.dev source in [`../pen/runout-listening-room.pen`](../pen/runout-listening-room.pen). The decisions they illustrate are written down in [draft v2 §0.1](../RUNOUT-REDESIGN-DRAFT-v2.md). "Night Drive — Vela Park" and the pixel-art covers are fictional demo art.

## The vinyl is the controller: gesture storyboard

| | | |
|---|---|---|
| ![01](gesture-01-tap-the-cover.webp) | ![02](gesture-02-disc-pops-up.webp) | ![03](gesture-03-drag-onto-deck.webp) |
| ![04](gesture-04-start-motor.webp) | ![05](gesture-05-lower-needle.webp) | ![06](gesture-06-skip-ahead.webp) |
| ![07](gesture-07-pause-or-brake.webp) | ![08](gesture-08-turn-over.webp) | ![09](gesture-09-put-away.webp) |

## Home (no scroll), by deck state

| Empty | Cued | Lead-in | Playing |
|---|---|---|---|
| ![](home-empty-day.webp) | ![](home-cued-day.webp) | ![](home-lead-in-day.webp) | ![](home-playing-day.webp) |

| Paused (headset) | Paused (call) | Runout | Awaiting flip |
|---|---|---|---|
| ![](home-paused-headset-day.webp) | ![](home-paused-call-day.webp) | ![](home-runout-day.webp) | ![](home-awaiting-flip-day.webp) |

| Finished | Pure on | Playing (night) | Runout (night) |
|---|---|---|---|
| ![](home-finished-day.webp) | ![](home-pure-on-day.webp) | ![](home-playing-night.webp) | ![](home-runout-night.webp) |

Awaiting flip (night): ![](home-awaiting-flip-night.webp)

## Landscape room

![Landscape](home-landscape-playing-day.webp)

## Signal path

| USB DAC, bit-perfect (day) | Bluetooth (night) |
|---|---|
| ![](signal-path-usb-dac-day.webp) | ![](signal-path-bluetooth-night.webp) |

## Components and assets

The deck component, the crate coverflow, the generated concept art (the user chose "Concept · D printed plate"), and the fictional pixel-art covers:

![Deck](component-deck.webp) ![Coverflow](component-coverflow.webp)

![Assets](assets-generated.webp)

![Pixel covers](assets-pixel-covers.webp)

Not mocked up yet: Downloads recovery, Settings, record detail, Discover and Messages. Build those from v2 §9 copy, in the same calm language.
