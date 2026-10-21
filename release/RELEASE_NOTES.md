The record player is now a 3D turntable: the Runout RG-1, drawn live on the phone.

## What changed

- **A 3D deck:** a white turntable with teal glass and an anodised-orange START/STOP key, standing in the room on a soft shadow.
  - The platter and the record turn in 3D. Each record keeps its own look (black, coloured, splatter or picture vinyl, the cover as the label, the gaps between tracks), now on a grooved disc.
  - The tonearm swings out from its rest, follows the side and drops into the groove. Drag it to start anywhere; tap the record to flip it; hold it to slow it.
- **A control panel:** START/STOP, ◀◀ ▶▶ and a new **eject** key, which press down under your finger.
- **Lights and a screen on the deck:** an orange status lamp while a record plays, an LED strip along the front that fills as the side plays, and a small screen showing the track and time.
- **Library:** a scan you start no longer gets cancelled by the quiet refresh at startup (the shelf could stay empty).
- If the phone can't draw 3D, a flat deck stands in so the app still works.
- Startup rendering now requests a redraw even when Filament skips a frame, allowing the texture queue to drain.

## Install

Download **Runout-0.6.0-development.apk** and check it against the attached SHA-256 file. It installs as **Runout Dev** (`app.runout.dev`) and updates 0.5.x in place (same certificate). Coming from 0.4.x, uninstall it first.

The APK is about 22 MB larger than 0.5.1: it carries Filament's 3D libraries for the four CPU types.

## Not tested, and limits

- Tested on the Android emulator only, not on a phone. How smoothly the deck turns on older phones is unknown.
- The flying disc doesn't yet morph into the 3D one when you drop it, and flipping is an instant swap, not a 3D turn.
- Clear vinyl draws solid on the 3D deck.
- A few printed labels on the deck show a stray mark instead of "·" or "-" (a font problem in the model, fixed in the next model).
- On the emulator, the deck can take several seconds to appear. A graphics stall encountered during verification cleared after restarting the emulator; phone startup remains unverified.
- The remote discovery benchmark missed its 250 ms target (403–526 ms on the emulator).
- The native Soulseek library is reused from the earlier development build; this Mac has no Rust/NDK toolchain. Newer native-source changes are not included. See the verification report.
- This is a debug-signed development pre-release.
