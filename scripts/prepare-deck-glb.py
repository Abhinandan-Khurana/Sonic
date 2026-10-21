#!/usr/bin/env python3
"""Prepares the 3D turntable for the app.

    python3 scripts/prepare-deck-glb.py <runout_turntable_*.glb> [<runout_record.glb>]

Writes app/src/main/assets/deck/turntable.glb (and record.glb when given). The source is the Runout
RG-1 v3 model (runout_turntable_white_teal.glb and its siblings, built in Blender by
scripts/deck-v3 from Hermes' v2), whose node names, origins and measurements are described in
docs/design/deck-v3-spec.json.

Two changes are made:

- The screen (Screen_Mat) and the LED strip (LED_Strip_Mat) get an emissive texture, the same
  placeholder their base colour uses. Without it the ubershader has no emissive map to swap, and
  the screen's glow would wash flat over its text. The app replaces that texture at runtime with
  the screen's text and the strip's progress.
- The printed labels (Prints_Mat) become unlit (see below).

Geometry and every other material are copied byte for byte.
"""
import json
import shutil
import struct
import sys

DECK_OUT = "app/src/main/assets/deck/turntable.glb"
RECORD_OUT = "app/src/main/assets/deck/record.glb"
DRAWN = ("Screen_Mat", "LED_Strip_Mat")


def read(path):
    data = open(path, "rb").read()
    magic, version, _ = struct.unpack_from("<4sII", data, 0)
    if magic != b"glTF" or version != 2:
        sys.exit(f"{path}: not a glTF 2 binary")
    json_len = struct.unpack_from("<I", data, 12)[0]
    return json.loads(data[20:20 + json_len]), data[20 + json_len:]


def write(path, gltf, rest):
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * (-len(js) % 4)
    body = struct.pack("<I4s", len(js), b"JSON") + js + rest
    with open(path, "wb") as f:
        f.write(struct.pack("<4sII", b"glTF", 2, 12 + len(body)) + body)
    print(f"{path}: {12 + len(body)} bytes")


def main(deck, record=None):
    gltf, rest = read(deck)
    materials = {m["name"]: m for m in gltf.get("materials", [])}
    screen = materials.get("Screen_Mat")
    if screen is None or "baseColorTexture" not in screen.get("pbrMetallicRoughness", {}):
        sys.exit("Screen_Mat with a base colour texture not found: is this the RG-1 model?")
    placeholder = screen["pbrMetallicRoughness"]["baseColorTexture"]["index"]
    for name in DRAWN:
        m = materials.get(name)
        if m is None:
            sys.exit(f"{name} not found")
        m["emissiveTexture"] = {"index": placeholder}
        print(f"{name}: emissive texture slot added")
    # Printed ink: unlit. Lit, Filament's transparent blending keeps the specular sheen over the
    # whole decal quad, even where the ink is fully transparent, and every label sits in a grey box.
    prints = materials.get("Prints_Mat")
    if prints is not None:
        prints.setdefault("extensions", {})["KHR_materials_unlit"] = {}
        used = gltf.setdefault("extensionsUsed", [])
        if "KHR_materials_unlit" not in used:
            used.append("KHR_materials_unlit")
        print("Prints_Mat: unlit")
    write(DECK_OUT, gltf, rest)
    if record:
        shutil.copyfile(record, RECORD_OUT)
        print(f"{RECORD_OUT}: copied")


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    main(*sys.argv[1:])
