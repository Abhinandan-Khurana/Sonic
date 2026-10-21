# Runout RG-1 turntable v2

## Delivered

- `runout_turntable_white_teal.glb` + `.blend`
- `runout_turntable_dark.glb` + `.blend`
- `runout_turntable_mono_white.glb` + `.blend`
- Matching `*_lod1.glb` assets
- `runout_record.glb` + `.blend`
- `deck-spec.json`, real mesh-derived glTF coordinates and material references
- `previews/`: top, 25-degree app view, lit-LED view at 1600 px; three-quarter and record-on-deck heroes at 2400 px, for all three themes
- `studio_env/studio.hdr`: neutral 1024x512 analytic Radiance studio environment
- `validation/`: Khronos validator reports, Chromium/Three.js viewer reports/screenshots, independent geometry/UV/parity checks
- `build/` and `textures/`: reproducible scripts and source textures

## Removed / preserved

Removed the six mixer knobs, both mixer faders, three large turntable knobs, old round/rectangular deck buttons. No pitch fader, speed switch, power knob or record was baked into the deck GLBs.

Preserved the original footprint/coordinate frame, original feet/I/O geometry, and the source curved arm tube, counterweight and headshell. Rebuilt the matching chassis shells with 0.015-unit soft bevels, inset screen and LED grooves, simple fasteners/seam trim, platter/mat/spindle and static bearing/rest. Added a small decorative anti-skate dial. The source .blend files were corrupt; the intact white-teal GLB supplied the retained source meshes. Original files are unchanged.

Geometry was built once. The three full GLBs have byte-identical decoded geometry/UV data and identical node hierarchy, origins, rotations/scales; only material definitions and colour textures differ. All export rotations/scales are applied. The root is identity at the world origin, glTF Y up, +Z front.

## Runtime parts

Exact exported nodes: `Modular_Turntable_Root`, `Turntable_Platter`, `Tonearm_Arm`, `Tonearm_Base`, `Key_StartStop`, `Key_Prev`, `Key_Next`, `Key_Eject`, `LED_Status`, `LED_Strip`, `Screen_Display`, `Screen_Glass`, `Prints`, `Shadow_Plane`, plus three `Chassis_*` static meshes.

- Platter origin is its rotation axis at mat-top Y = **0.5700000**; radius approximately **1.19**. Felt support is flat through radius 1.15, excluding the physical spindle. Spindle top Y = **0.6250000**, clearance **0.055** above the mat.
- Arm origin is the vertical bearing pivot. It is parked outside the platter, on the repositioned rest. Stylus lowest point at rest is Y approximately **0.643**; lowering 0.055 brings it to the record face at **0.588**. Pivot tests produced zero origin drift.
- Key origins are cap-top centres; nominal travel **0.002**. Each key includes its skirt. Cap glyph decal patches are merged into the moving keys so they cannot float above a pressed cap. The other printed labels/outline remain one `Prints` atlas object. This is a deliberate practical exception to placing *all* ink in a static `Prints` node.
- Screen is 1.0 x 0.25, with a 256x64 #101418 base-colour placeholder, near-black LCD appearance and greenish-white emission factor. Glass is BLEND alpha 0.80. App swaps Screen_Mat's base-colour texture.
- Orange Start/Stop is #E07A4F on every theme. White-teal secondary caps are BLEND alpha 0.82; grey caps on the other themes are opaque.
- LED_Status_Mat and LED_Strip_Mat are separate emissive materials. Strip UV U runs left-to-right, V across. The strip is 0.04 deep, 0.01 high, inset at the front. Default LED emission strength is 1; render variants show on/off states.

## Textures and mobile shading

A real Cycles ambient-occlusion bake was made against the stationary chassis/trim/underside/base, packed into a shared 1024-square ORM atlas. AO was quantised to 16 levels to meet file size; plastic roughness has subtle low-amplitude microvariation. Metal is satin, not chrome: maximum metallic factor 0.28, lit-material effective roughness stays inside 0.25–0.55. The unlit shadow material's irrelevant exporter roughness is 0.9.

The felt has a 1024 normal map, ring and spaced RUNOUT branding. All images are embedded PNG, maximum dimension 2048. Glass uses alpha blending, never transmission. No Draco, meshopt, external buffers, cameras or lights are exported. Tangents on the planar felt primitive are explicitly orthogonalized after export to avoid runtime tangent-generation warnings.

Shadow_Plane is a single quad at Y=-0.002, 15% larger than the deck. Its black-alpha texture comes from a real Cycles shadow-catcher render with the arm at rest; alpha is capped near 0.45 and tapered smoothly to zero at the texture borders. The requested 15%-oversize footprint only leaves roughly 0.2–0.3 units of margin, so the fade is clipped/tapered inside that margin rather than promising a full 0.4-unit fade beyond every side. It is unlit + BLEND. Keep it static.

## Record asset and placement — important

Separate record: radius approximately **1.13**, spindle hole radius **0.028**, **0.018** nominal thickness, +0.001 raised lead-in rim. **3,072 triangles**. Embedded 64-square placeholder, 2048-square concentric-groove tangent normal (scale 0.4), and packed metallic/roughness texture; metallic is zero, groove roughness ~0.4196, smooth-band roughness ~0.2784. Label and lead-in regions are smooth. Top planar UVs U follow X and V follow glTF Z.

The requested top-origin disc extends *below* its origin. Therefore its node must be placed at **[0.255, 0.588, 0]**, i.e. **matTopY + 0.018**, not directly at matTopY, to rest on rather than sink into the mat. The hero renders use this placement. Exact measurements and radial clearance are in deck-spec.json / record-spec.json.

## Budgets and validation

Each full deck: **12,944 triangles**, approximately **1.17 MB**; each LOD1: **10,260 triangles**, approximately **1.03 MB**. Exact byte sizes are in deck-spec.json and manifest.json. LOD1 preserves names, origins, screen/LED/print UVs, control positions and materials; it simplifies underside/arm/base/platter and embeds half-resolution texture copies.

All seven GLBs pass Khronos validation with **zero errors and zero warnings**. All seven load and render in a real Chromium/Three.js glTF viewer with zero load/console warnings/errors. Independent binary checks confirm variant geometry parity, full/LOD functional UV parity, budgets, names, identity rotation/scale and effective roughness limits. Additional measured UV tests confirm positive U/X and V/Z directions. Pivot rotations were exercised in the open Blender session, then restored.

The arm sweep/control clearance was checked with the conservative 2D convex hull of all exported arm vertices against each actual control hull, sampled at 1,001 angles from rest toward the platter centre. Every control remained separated; the closest is the front LED strip, with a minimum separating gap ~0.201 units. This is conservative sampled clearance, not a continuous-time collision proof. Visual top/app previews also show all controls unobstructed.

## Limitations / integration still required

- **Not tested inside Android Filament/Runout or on a phone.** Three.js/Khronos results do not claim a device-runtime test. Runtime texture replacement, touch mapping, opacity sorting and emissive animation still need application integration.
- Physical lettering is small at 380x245 dp; glyphs carry the main interaction meaning. Liberation Sans is used as the available Inter-like small-cap font, not the proprietary-looking exact Inter Medium source.
- `cmgen` is not installed, so no `studio_ibl.ktx` or `studio_skybox.ktx` was generated. The HDR is procedural/analytic, not a photographed environment. Main assets remain safe without it. With a proper IBL, a separate chrome tonearm variant could use metallic **0.9**, roughness **0.15**; these values are NOT applied to the delivered deck.
- Main native files are intentionally asset-only (no lights/cameras). Temporary render rigs live only in build work files/render scripts. The earlier comparison scene is preserved in `build/work-in-progress.blend`.

## Re-run checks

```sh
node build/validate_gltf.cjs
python3 build/validate_geometry.py
python3 build/measure_deck.py
node build/viewer_verify.cjs
```

The independent inspector now decodes metallic/roughness texture channels with Pillow rather than incorrectly treating a texture-multiplication factor of 1 as roughness 1.
