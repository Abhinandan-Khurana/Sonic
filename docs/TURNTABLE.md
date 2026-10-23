# Glass Drive RG-1: Turntable Design & Mechanics

The centerpiece of **Sonic** is the **Glass Drive RG-1**, an homage to the legendary direct-drive DJ turntables of the vinyl era, reimagined through frosted liquid glass and modern Android graphics shaders.

---

## Turntable Anatomy

```
┌────────────────────────────────────────────────────────────────────────┐
│  [45 Adaptor]                                      [Tonearm Base]      │
│     ( ○ )                                           (◎ Collar)         │
│                                                     (Ribbed Weight)    │
│            ┌───────────────────────┐                    \              │
│            │   STROBE PLATTER      │                     \ [Chrome S]  │
│            │  ...................  │                      \            │
│            │  :   PICTURE DISC  :  │                       \           │
│            │  :  (Album Art)    :  │                        [Headshell]│
│            │  :        ☉        :  │                             \     │
│            │  :     SPINDLE     :  │                             ▼     │
│            │  :.................:  │                       (Stylus)    │
│            └───────────────────────┘                                   │
│                                                                        │
│  [DOT-MATRIX SCREEN]                          [PITCH FADER ±8%]        │
│  ┌─────────────────────────┐                  ┌──┐ +                   │
│  │ A1 TRACK · ARTIST       │                  │  │                     │
│  │ 02:45   33 RPM   +0.0%  │                  ├──┤ [Zero LED]          │
│  └─────────────────────────┘                  │  │                     │
│                                               └──┘ -                   │
│  [START/STOP]   [33 RPM]   [45 RPM]   [◀◀]   [▶▶]                      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Tonearm Geometry & The Law of Cosines

In a physical turntable, the stylus is attached to a pivoted arm and traces an arc across the spinning record surface. To deliver authentic playback visuals and accurate seek positions, Sonic models this exact mechanical behavior in `Deck.kt`.

### Geometric Model
Let:
- $P = (x_p, y_p)$ be the fixed coordinate of the tonearm pivot bearing.
- $S = (x_s, y_s)$ be the spindle (center of the platter).
- $L$ be the effective tonearm length (distance from pivot to diamond stylus tip).
- $D$ be the pivot-to-spindle distance.
- $r$ be the radial distance of the stylus from the center spindle.

Given the current fractional progress $t \in [0, 1]$ of the vinyl side:
$$r(t) = R_{\text{outer}} - t \cdot (R_{\text{outer}} - R_{\text{inner}})$$

By applying the **Law of Cosines** on the triangle formed by Pivot, Spindle, and Stylus:
$$r^2 = L^2 + D^2 - 2LD \cos(\theta)$$
$$\cos(\theta) = \frac{L^2 + D^2 - r^2}{2LD}$$
$$\theta = \arccos\left(\frac{L^2 + D^2 - r^2}{2LD}\right)$$

This calculation produces an exact tracking angle $\theta$. As the side progresses, the virtual chrome S-arm sweeps gracefully inward from the outer lead-in lip toward the inner runout spiral.

---

## 2. Platter Strobe Dots

The platter rim features 4 precision strobe bands, each composed of 180 circular markers:
- **Band 1:** Calibrated for 33⅓ RPM at 50 Hz illumination.
- **Band 2:** Calibrated for 45 RPM at 50 Hz illumination.
- **Band 3:** Calibrated for 33⅓ RPM at 60 Hz illumination.
- **Band 4:** Calibrated for 45 RPM at 60 Hz illumination.

During playback, the strobe markings rotate in real time at the platter's angular velocity:
$$\omega = \text{RPM} \times \frac{2\pi}{60}$$

The deck always runs at recorded speed, so the matching strobe band visually synchronizes, producing the optical illusion of standing stationary.

---

## 3. Dot-Matrix Pixel Screen

Built into the frosted glass plinth below the platter is a retro-futuristic 8-bit dot-matrix screen:
- **Typography:** Driven by the **Silkscreen** pixel font (OFL 1.1).
- **Dot Grid:** Rendered at a native 1-to-1 dot pitch. Characters are rasterized directly onto a matrix of mint-green phosphor dots against dark smoked glass.
- **Marquee Engine:**
  - When playing, long track titles scroll horizontally at 1 dot per 90 milliseconds.
  - Pauses scrolling when the deck is stopped or cueing to conserve battery.
- **Status Readouts:**
  - Line 1: `A1 TRACK TITLE · ARTIST`
  - Line 2: `MM:SS   33 RPM   +1.2%` (or states: `CUE A1`, `STOP`, `SIDE A END`, `NO RECORD`).

---

## 4. Gestures & Tactile Mechanics

| Gesture | Action | Audio & Physical Reaction |
| :--- | :--- | :--- |
| **Tap tonearm on rest** | Start playback | Arm lifts, swings to lead-in groove, drops needle with subtle needle-drop thump. |
| **Drag tonearm** | Manual needle drop | Tone arm follows finger; lowers to specific groove on release. |
| **Hold spinning record** | Hand brake | Simulates physical platter friction; audio slows and pitches down to a full stop. |
| **Release held record** | Platter release | Audio accelerates smoothly back to standard operating speed. |
| **Tap record (needle up)**| Flip side | Animates 3D disc flip from Side A to Side B (or Side C/D). |
| **Fader Drag / Double Tap**| Pitch adjustment | Drags between −8% and +8%; double-tap resets directly to 0.0% detent. |
| **◀◀ / ▶▶ keys** | Track skip | Jumps between track boundary bands; triggers side flip at end of side. |

---

## 5. Synthesised Turntable Sound Effects (`VinylSfx.kt`)

Rather than relying on static audio sample files, Sonic's mechanical sound effects are **mathematically synthesised in real-time** via audio generators:
1. **Needle Drop:** A low-frequency impulse (40 Hz damped sinusoidal burst) accompanied by a brief micro-crackle simulating stylus tip impact.
2. **Surface Noise & Crackle:** Continuous algorithmic Brownian noise mixed with sparse, high-amplitude Poisson-distributed clicks.
3. **Runout Thump:** A periodic 1.8 Hz low-frequency thud reproducing the physical jolt of the stylus resting in the concentric runout groove.
4. **Pure Mode Behavior:** All synthesised sound effects are automatically muted when Pure Mode is engaged, guaranteeing an unadulterated audio path.
