# Holdfast 1.16 — a maintenance release: the game is unchanged

**Download `holdfast-1.16-windows-x64.zip` (Windows) or `holdfast-1.16-linux-x64.tar.gz` (Linux).** Unzip or extract,
then run `Holdfast.bat` or `Holdfast.sh`. **You do not need Java installed** — the runtime and the JavaFX modules are
inside both.

The plain jar is attached too — **`tropical-punch.jar`** — for anyone who already has Java 17 and JavaFX. (It is
still named after the working title. Renaming it is a decision for Kinger, not for a release.)

## Nothing you can see has changed, and that is the honest headline

**If you already have 1.15, there is no reason to download this.** The game plays the same. What changed is what is
inside the jar beside the game, and the repository around it:

- **The AI Director now has a test, and it did not have one.** `AIDirector` is the pacing layer — BUILD, PEAK,
  RELAX, RECOVER, an intensity signal, a mercy window — and it was the only engine class with a state machine and a
  claim written beside it and nothing checking either. The claim is the interesting part: *"Decay must be
  time-based: `frustration *= exp(-k * dt)`. A per-tick constant like 0.95 decays 0.95^60 ≈ 0.05 per second at
  60fps — events vanish before they can accumulate."* That sentence is now a test, and the test fails by exactly the
  factor the sentence names when the wrong decay is put back: **0.285000 against 0.013821**.
  **The director is still not wired into the game, and that is deliberate** — how much it should steer is a design
  decision rather than a wire, and the test exists so that decision can be made against measured behaviour: over a
  sixty-second run its spawn multiplier stays between **0.39 and 1.40**.
- **Three build artefacts were tracked in this repository**: a 36 KB SWT bridge nothing uses, a pom the shade plugin
  rewrites on every build, and a JavaFX warning log from 23 September. All three removed, and the ignore rules
  widened — this repository already knew the class of fault, because two crash logs had been committed once.
- **The jar is rebuilt from source**, because a jar built from older source is a silent bug: the fix is in the
  repository and not in the thing people download. `check-jar-current` compares 117 classes and 103 resources
  against a fresh build and now agrees.

## What is verified, and what is not

Two editions of this game exist — the engine inside the aside library and this standalone repository — and they are
aligned **by their self-tests reporting the same numbers rather than by diffing text**: 5 engine files identical,
11 screen markers agreeing, both self-tests at **28 passed**, and both gates green (**39 checks** here, 44 suites in
the library).

**Nothing here can tell you whether the game is fun.** Reachability, counts and reproducibility are checkable; the
feel of a jump is not, and no test in this repository claims otherwise.
