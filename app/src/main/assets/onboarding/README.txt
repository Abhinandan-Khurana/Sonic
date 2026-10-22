Onboarding pictures, loaded at runtime (ui/OnboardingArt.kt, rememberOnboardingPictures).
Each name may be .webp (preferred), .png or .jpg. A missing file is fine: the screen keeps its
drawn layout.

Step pictures (square, white background, 1024 x 1024), shown between the big step number and the copy:
  step-02   02 · offline (bring your own records)
  step-03   03 · where should downloads go?
  step-04   04 · share a folder?

Sign-in collage covers (square, 1024 x 1024), 3 x 2 in this order, left to right, top row first:
  cover-1   night drive, purple
  cover-2   teal
  cover-3   cream
  cover-4   blue
  cover-5   charcoal
  cover-6   pink
Any cover that's missing falls back to the drawn sleeve in its place.

Intro (the first screen: a sleeve slides in, its record flies to the deck):
  intro-sleeve   the sleeve, square, edge to edge (no background), 1024 x 1024
  intro-disc     the record from straight above, black vinyl with a printed paper label, on a
                 TRANSPARENT background (PNG), the record touching all four edges, 1024 x 1024.
                 Its label is 39 % of the record's diameter, centred (Intro.kt LABEL; at least 35 %
                 so it covers the deck's own): Runout lays that same label over the deck's once the
                 record lands.
Missing either, the intro uses its built-in sleeve and disc.

Dark theme (Settings › Flags › theme, or the phone's dark mode). Each picture may have a "-dark"
twin, used in the dark theme; missing, the light one shows:
  step-02-dark … step-05-dark   the step pictures redrawn on true black (#000000), same
                                composition, square 1024 x 1024
  deck-empty-dark, deck-loaded-dark, deck-playing-dark, deck-arm-dark
                                the dark RG-1 rendered from Blender exactly as the white / teal
                                res/drawable-nodpi/onboarding_deck_* (same camera, size and
                                transparent background), so the intro still lands on the 3D deck
