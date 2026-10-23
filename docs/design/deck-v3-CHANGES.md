# Runout RG-1 v3

v3 reworks Hermes' v2 deck (see `deck-v2-CHANGES.md`) in Blender, with the scripts in `scripts/deck-v3`. All three colourways (white + teal, mono white, dark) come from the same build, so they stay identical in shape.

## What changed

- **Layout.** The control module now sits to the right of the tonearm. The deck is wider and shallower: 5.145 × 3.10 units, where v2 was 4.15 × 2.68. Platter, tonearm and record are v2's parts, moved together, so the arm's geometry is unchanged.
- **Keys.** Flush TE-style keys fill a dark tray edge to edge, with 0.018 gaps between them:
  - prev and next, side by side at the back;
  - a large START/STOP below them;
  - a wide eject at the front.
  
  Each key is printed with a large icon in the middle and a lowercase word in its top-left corner. The status lamp and the word "playing" sit above the tray.
- **Red LED windows.** A front band holds two windows on one line, each behind smoked red acrylic in a dark bezel:
  - a long title window (2.75 × 0.344) under the platter;
  - a time window (1.6 × 0.344) under the keys.
  
  Both windows share one 1024×256 texture: the title takes the top half, and the time takes the bottom half from u 0 to 0.581. The app draws the title in the pixel font, and the track code and time as 7-segment digits with faint unlit segments.
- **Labels.** One printed-label atlas per colourway (2048×1240, Inter), laid out top-down over the deck. "·", "-" and "⅓" now render correctly.
- **Chassis.** Rebuilt as bevelled modules with a seam, screws, feet and a channel for the LED strip. The baked ORM from v2 is gone, because its UVs no longer fit; roughness is now a constant per material. The contact shadow is regenerated for the new footprint.
- **LED strip.** Runs the full width along the front, with u running left to right.

## Not changed

- The record: `runout_record.glb`.
- Node names, and the convention that each key's origin sits at the top of its cap.
- The material names the app looks up: `Screen_Mat`, `LED_Strip_Mat`, `LED_Status_Mat` and `Prints_Mat`. `Screen_Time` is a new node; it uses `Screen_Mat`.

## Rebuilding

The build needs Blender 5.2 with its MCP extension. The working folder (`RUNOUT_DECK_V3`, default `~/Downloads/runout_turntable_v3`) holds the input in `v2-original/` (Hermes' `.blend` files) and receives the output.

1. For each theme (`white_teal`, `mono_white`, `dark`), run `atlas.py`, then `build.py`, inside Blender. Set `SCRIPTS` and `THEME` before running them, as shown in each file's header.
2. Run `python3 -I scripts/prepare-deck-glb.py <folder>/runout_turntable_white_teal.glb`.
3. Copy any changed numbers from `deck-v3-spec.json` into `ui/deck/DeckModel.kt`.
