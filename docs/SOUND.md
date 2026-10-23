# Sonic Sound Architecture & DSP Pipeline

Sonic was built from day one for listeners who demand audiophile transparency. Rather than routing audio through default Android mixers that truncate sample bit depths or force 48 kHz resampling, Sonic implements a dedicated **32-bit floating-point DSP pipeline**.

---

## Signal Processing Flow

```
FLAC Audio File
                 │
                 ▼
     Native Audio Track Decoder (Media3)
                 │
                 ▼
       Sonic DSP Float Sink (`DspAudioSink.kt`)
  ┌─────────────────────────────────────────────────────────────┐
  │  1. Float Normalization (16/24/32-bit PCM ──► 32-bit Float) │
  │                              │                              │
  │  2. ReplayGain Leveling (Track / Album Mode)                │
  │                              │                              │
  │  3. Automatic Headroom Attenuation (-Max Boost dB)          │
  │                              │                              │
  │  4. 10-Band Biquad Graphic EQ (or AutoEq Parametric Engine) │
  │                              │                              │
  │  5. Warmth Analog Cartridge Curve (+1.5dB / -2.0dB)         │
  │                              │                              │
  │  6. 1.5ms Look-Ahead Peak Limiter (-0.3 dBFS Ceiling)       │
  └──────────────────────────────┬──────────────────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
       [Standard Mode]                     [Pure Mode]
                 │                               │
                 ▼                               ▼
       32-Bit Float AudioTrack        Bit-Perfect USB DAC Direct
       (At File's Sample Rate)        (Android 14+ Bypass Mixer)
```

---

## 1. Floating-Point Conversion

Every incoming buffer is converted into native 32-bit IEEE 754 floating-point values $[-1.0, 1.0]$:
- **16-bit PCM:** Sample values $[-32768, 32767]$ divided by $32768.0$.
- **24-bit PCM:** 3-byte packed integers $[-8388608, 8388607]$ divided by $8388608.0$.
- **32-bit PCM:** Integer samples divided by $2147483648.0$.

All internal filter states, delay lines, and coefficients are maintained in single-precision floating point, preserving subtlest signal details and eliminating quantization noise.

---

## 2. Dynamic Automatic Headroom

A frequent pitfall in software equalizers is digital clipping: if a user boosts a frequency band by +6 dB on a normalized track, the signal exceeds digital 0 dBFS, resulting in harsh clipping distortion.

Sonic solves this automatically:
$$\text{Headroom Attenuation (dB)} = -\max\left(0.0, \max_{i \in [1, 10]} \text{gain}_i\right)$$

If an EQ band is boosted by +4.5 dB, Sonic automatically drops the digital preamp level by −4.5 dB prior to filtering. The shape of your EQ curve is preserved with zero clipping.

---

## 3. The 10-Band Graphic Equalizer

The equalizer consists of 10 second-order IIR biquad peaking filters centered on ISO standard frequencies:
**31 Hz, 62 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz, 16 kHz**

### Biquad Peaking Filter Equations
For a center frequency $f_0$, sample rate $f_s$, gain $A = 10^{\text{gaindB}/40}$, and bandwidth $Q = 1.414$:
$$\omega_0 = 2\pi \frac{f_0}{f_s}, \quad \alpha = \frac{\sin(\omega_0)}{2Q}$$
$$b_0 = 1 + \alpha A, \quad b_1 = -2\cos(\omega_0), \quad b_2 = 1 - \alpha A$$
$$a_0 = 1 + \frac{\alpha}{A}, \quad a_1 = -2\cos(\omega_0), \quad a_2 = 1 - \frac{\alpha}{A}$$

Coefficients are calculated dynamically whenever the track sample rate changes, ensuring that frequency response curves remain identical at 44.1 kHz, 96 kHz, and 192 kHz.

### Presets
- **Flat:** Zero gain across all bands.
- **Warm Vinyl:** Subtle mid-bass lift and soft high-frequency rolloff.
- **Bass:** Enhanced sub-bass below 125 Hz.
- **Vocal:** Mid-range elevation between 1 kHz and 4 kHz.
- **Bright:** High-frequency air boost at 8 kHz and 16 kHz.
- **Late Night:** Dynamic range suppression with gentle bass reduction.

---

## 4. AutoEq Headphone Correction Import

Sonic features built-in support for the open-source **AutoEq** database (`autoeq.app`):
1. Users export their headphone model's `ParametricEQ.txt`.
2. Sonic's parser extracts:
   - Fixed preamp gain (e.g. `Preamp: -6.4 dB`)
   - Filter types: `PK` (Peaking), `LS` (Low Shelf), `HS` (High Shelf)
   - Filter parameters: Frequency ($f_c$), Gain (dB), and Quality factor ($Q$).
3. The DSP engine synthesizes arbitrary multi-stage biquad cascades matching the target headphone's Harman target correction curve.

---

## 5. Pure Mode & Bit-Perfect Output

For dedicated listening sessions with high-end external DACs, **Pure Mode** bypasses all DSP stages:
- **No Equalizer:** All filter passes bypassed.
- **No ReplayGain:** Raw amplitude preserved.
- **No Limiter:** Signal passes untouched.
- **No Speed / Pitch Alterations:** Platter speed locked bit-identically to source sample rate.
- **Bit-Perfect AudioTrack (Android 14+):** Requests `AudioTrack.Builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)` combined with exclusive mixer bypass flags when an external USB DAC is detected.
