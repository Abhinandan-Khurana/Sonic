> Historical v0.3 documentation: Soulseek replaces the online Nostr features in v0.4, and music is FLAC-only. See [current Soulseek behavior](SOULSEEK.md) and [build instructions](BUILDING.md).

# Friday Drops (and the retired Pressing Plant)

Two defining features of **Sonic** are the **Pressing Plant** (which lets you master and design your own bespoke vinyl releases) and **Friday Drops** (a serverless weekly distribution of curated legal Creative Commons music).

---

## Part 1: The Pressing Plant (removed)

The Pressing Plant was removed in the UI redesign. Records pressed with it earlier stay playable and removable; nothing new can be pressed.

## Part 2: Friday Drops

Every Friday at midnight UTC, **ten new Creative Commons albums** drop into the listening room. Every user around the world sees the exact same ten albums—**without any central server coordinating the list**.

### The Deterministic Week Algorithm
Instead of polling a central database, every Sonic installation derives the week's 10 drop albums mathematically:
1. The app computes the current calendar week index:
   $$\text{weekNumber} = \left\lfloor \frac{\text{currentTimeMs}}{1000 \times 60 \times 60 \times 24 \times 7} \right\rfloor$$
2. The `weekNumber` selects one of 20 indexed pages from Internet Archive's curated Netlabel collection (thousands of legal, freely shareable independent releases).
3. The catalog list is shuffled using a Pseudo-Random Number Generator (PRNG) seeded with `weekNumber`.
4. Because the algorithm and seed are identical across all devices, every phone produces the exact same ranked list of ten albums.

### Claim Mechanics
- **Allowance:** Users can claim **3 albums** per week.
- **Lossless Ingestion:** When an album is claimed, Sonic downloads the original lossless FLAC files directly from Internet Archive's global CDN (`archive.org/download/...`). If FLAC is unavailable, it gracefully falls back to high-bitrate MP3 or Ogg.
- **Full Legal Attribution:** The Creative Commons license (e.g. `CC BY-NC-SA 4.0`) is baked into the record's manifest, empowering users to share the album with friends safely.
- **Claim Reset:** The 3-claim quota resets automatically every Friday at 00:00 UTC.
