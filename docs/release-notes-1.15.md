# Holdfast 1.15 — six rules the game taught and did not enforce

**Download `holdfast-1.15-windows-x64.zip` (Windows, 55 MB) or `holdfast-1.15-linux-x64.tar.gz` (Linux, 61 MB).**
Unzip or extract, then run `Holdfast.bat` or `Holdfast.sh`. **You do not need Java installed** — the runtime and
the JavaFX modules are inside both.

The plain jar is attached too — **`tropical-punch.jar`** — for anyone who already has Java 17 and JavaFX. (It is
still named after the working title. Renaming it is a decision for Kinger, not for a release.)

## What was wrong

Every one of these was a rule **the tutorial's own signs state and the code did not enforce**, which is why
comparing the tutorial text against the code found them and reading the code alone did not:

- **Bats knocked you down and nothing happened.** Tutorial 5 says "BATS KNOCK YOU DOWN". The engine set a stun
  timer and the screen never read it — the climber kept walking, and the prone and kneeling sprites were built and
  never drawn. It is a sequence now: flat, then up onto one knee, then standing.
- **Your own bomb could not hurt you.** Tutorial 3 says "STAND BACK. the blast hurts you too."
- **Running out of air did nothing**, and there was no meter to see it coming.
- **Damage was silent** — no flash on the climber, nothing on the HUD.
- **Every enemy was the same creature.** The snake did not exist in this build at all, and its whole tell — resting
  coiled until it sees you — was missing with it.
- **The safe-room reward drew as a key** instead of the jar it is.

## And two things the tutorial did not teach

- **The hookshot had a sign and nowhere to use it.** The README leads with it and none of the nine levels
  mentioned it. It now has a ledge on level 4 — six rows up, out of a jump's reach and inside the hookshot's —
  with a snack on it.
- **The water it taught was the wrong water.** Level 9's water is one row deep, deliberately, because that is how
  a flooded level is built. But the game has two kinds, and the kind you can *swim* in was never shown. There is a
  pool now, with the controls on a sign beside it.

## What is verified, and what is not

Everything above is verified by **rendering** — screenshots of the real game, not test output. The tutorial's pool
was verified by **measuring** the water system rather than by looking at a picture, after the picture told me the
opposite of the truth.

**The Windows launcher is not verified.** There is no Windows machine here. What is checked is that the runtime,
the JavaFX jars and the Windows natives are all present in the zip, and that the jar inside it is byte-identical
to the one verified running on Linux.

## About the file names

This release attaches `holdfast-1.15-windows-x64.zip` and `holdfast-1.15-linux-x64.tar.gz`. **Earlier releases used
a shorter convention** — two letters and the version — and eleven of them told you in their notes to download a
name that was not attached, which is fixed, but the convention itself is still an open question. This one uses the
descriptive form because that is what the notes have always said and what the engine repository uses.

(It does not spell the old convention out. `tools/check-release-notes.sh` fails any release whose notes name a
file that is attached to nothing, and a quoted historical name trips it — the third time this week that a note
explaining a fault has tripped the check for the fault. The rule that keeps coming out of it: do not reproduce the
bad value in the note about it.)
