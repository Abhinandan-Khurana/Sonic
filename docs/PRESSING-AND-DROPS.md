# Pressing Plant & Friday Drops

Two defining features of **Runout** are the **Pressing Plant** (which lets you master and design your own bespoke vinyl releases) and **Friday Drops** (a serverless weekly distribution of curated legal Creative Commons music).

---

## Part 1: The Pressing Plant

The Pressing Plant allows any user or independent artist to create custom vinyl records directly on their phone.

### 1. Track Assembly & Timing
- Select up to **40 tracks** from your existing library.
- The pressing engine calculates total duration and splits tracks automatically into balanced vinyl sides (Sides A/B, or Sides A/B/C/D for double albums) respecting the physical ~22-minute side threshold.
- Re-order or remove tracks with live duration updates.

### 2. Algorithmic Sleeve Generator
Rather than requiring external graphic design software, Runout incorporates a procedural sleeve design engine with 6 geometric generative styles:
1. **Orbit:** Concentric gravitational planetary rings centered on the spindle.
2. **Stripes:** High-contrast modernist horizontal and diagonal rhythm bars.
3. **Bloom:** Multi-layered radial petal and floral geometry.
4. **Grid:** Strict Swiss-style architectural coordinate grids.
5. **Sunset:** Warm horizon gradient blocks.
6. **Type:** Bold minimalist typographic layouts.

*Custom Art Support:* Users can also import any square photograph or custom album artwork from their device.

### 3. Physical Vinyl Formulations
Pressings can be rendered with 6 distinct physical vinyl styles:
- **Black:** Classic micro-grooved black vinyl with authentic light reflection.
- **Solid Colour:** Opaque saturated pigment matching the artwork's dominant color.
- **Clear:** Translucent colored crystal vinyl through which the platter mat is visible.
- **Splatter:** Hand-poured vinyl base with dynamic high-velocity pigment droplets.
- **Marble:** Dual-tone organic liquid swirls.
- **Picture Disc:** Full-bleed album art printed across the disc beneath protective transparent vinyl grooves.

### 4. Honest Quality Attribution
When pressing mixtapes containing tracks of different formats, Runout computes an honest label:
- All identical lossless formats $\to$ exact rate (e.g. `FLAC · 24-bit · 48 kHz`).
- Different lossless rates $\to$ `LOSSLESS MIX`.
- Any lossy compression detected $\to$ `MIXED`.

---

## Part 2: Friday Drops

Every Friday at midnight UTC, **ten new Creative Commons albums** drop into the listening room. Every user around the world sees the exact same ten albums—**without any central server coordinating the list**.

### The Deterministic Week Algorithm
Instead of polling a central database, every Runout installation derives the week's 10 drop albums mathematically:
1. The app computes the current calendar week index:
   $$\text{weekNumber} = \left\lfloor \frac{\text{currentTimeMs}}{1000 \times 60 \times 60 \times 24 \times 7} \right\rfloor$$
2. The `weekNumber` selects one of 20 indexed pages from Internet Archive's curated Netlabel collection (thousands of legal, freely shareable independent releases).
3. The catalog list is shuffled using a Pseudo-Random Number Generator (PRNG) seeded with `weekNumber`.
4. Because the algorithm and seed are identical across all devices, every phone produces the exact same ranked list of ten albums.

### Claim Mechanics
- **Allowance:** Users can claim **3 albums** per week.
- **Lossless Ingestion:** When an album is claimed, Runout downloads the original lossless FLAC files directly from Internet Archive's global CDN (`archive.org/download/...`). If FLAC is unavailable, it gracefully falls back to high-bitrate MP3 or Ogg.
- **Full Legal Attribution:** The Creative Commons license (e.g. `CC BY-NC-SA 4.0`) is baked into the record's manifest, empowering users to share the album with friends safely.
- **Claim Reset:** The 3-claim quota resets automatically every Friday at 00:00 UTC.
