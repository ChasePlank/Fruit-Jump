# Holdfast

*a climber, a sunset, and a way home*

A 2D action-adventure platformer in Java 17 + JavaFX. Screen-transition based, with hookshot, bombs, bow, and
procedurally generated levels.

> **The repository is still called `Fruit-Jump` and the jar is still `tropical-punch.jar`.** Those are Kinger's to
> rename and have deliberately not been. The **game** has been called Holdfast since 4 October 2026.

## The story

The valley flooded. Not all at once: the water came up over years, the way it does, and everyone left. **You did
not, and then you did** — and by then the road out was under sixty feet of water, so the only direction that was
still dry was up.

**So the way home is above you**, which is the wrong direction for home to be and the only one left.

**And the sunset is a clock, not a decoration.** You climb while there is light. The horizon never moves past
dusk because that is the rule rather than an art choice — and the game-over screen shows the same sky with the
rock empty, because that is what has happened.

The full premise, and the one question it raises about the gameplay sky, is in **[HOLDFAST.md](HOLDFAST.md)**. That question is answered by looking rather than arguing: **[docs/sky-comparison.png](docs/sky-comparison.png)** is the same level rendered twice, and [the note beside it](docs/sky-comparison.md) says what the picture shows — including the two things the question itself did not mention.

This is the Java engine + playable game. The climber has no face, so hair and pack are the identity:
**The Climber** in the menu picks them, and the game draws what you chose.

## Run

```bash
java -jar tropical-punch.jar
```

Requires Java 17+ with JavaFX 17+ on the module path (the shaded jar bundles JavaFX for Linux; on Windows/Mac
use the source build below). The jar carries its own sound files as well as JavaFX, so it is playable on its own
with nothing beside it.

**Controls:**
- Arrow keys / WASD — move
- Space / W — jump, and swim upward in water
- Down / S — dive (water only)
- X — hookshot (Shift+X fires upward)
- F — arrow
- G — bomb
- ESC — pause

## Build from source

```bash
mvn package
java -jar target/tropical-punch-1.0.0.jar
```

## What's in it

- **A boss, every tenth level.** `Boss` is 368 lines of multi-phase AI, telegraphed attacks and weak-point
  windows: a hunched, horned brute that only takes damage while the mark on its
  back is lit, and that throws charges when you keep your distance. It is placed by the generator on the
  tenth level of a run, seeded from the level so the fight is the same on every attempt.

A complete platformer engine, built and tested layer by layer:

- **Physics** — fixed timestep (1/60), swept AABB collision (slab method), iterative multi-collision resolution, slopes (45°/30° walkable, 63°+ slides)
- **Movement** — run, jump, one-way platforms
- **Water** — flooded gaps, buoyancy, a breath meter, a breach hop out of a pool, and a splash on entry. **A quarter of levels roll FLOODED** (5 of the first 40 come out wet enough to read as one): the walk itself under water in runs with dry ground between them, so the level is a different shape rather than a wetter version of the same one
- **Piranhas** — a water enemy in groups of three to five. Unlike a bat, it takes a **heart** rather than knocking you down. It moves only when you are in the water, so getting out loses it, and it is slower than you swim, so it corners you rather than running you down
- **Bats** — they follow you, and a knock-down clears the swarm rather than pinning you
- **Combat** — contact damage, stomp (positional check), knockback, i-frames
- **Weapons** — hookshot (instant raycast, pull physics), arrows (fast, light gravity), bombs (thrown arc, 1.2s fuse, blast radius, destroy cracked terrain)
- **Enemies** — PATROL/CHASE AI with hysteresis, wall/ledge sensors
- **Items** — hearts, keys, locked doors, cracked tiles (bombable). Heals get **rarer as levels go on** (0.55 falling to a floor of 0.12), because a late heal is worth more than an early one
- **Level generation** — procedural ground walk with guaranteed traversability: gaps from 3 cells widening to a hard cap of 4 as levels go on, climbs ≤2 cells, landing runways in both directions, spike pits off the path, locked doors with keys on flat stretches, bombable pockets hiding hearts
- **Level validation** — a bot plays every generated level through the real physics engine before it ships; 100/100 fresh seeds pass
- **Camera** — smooth follow, look-ahead, room clamping
- **Particles** — burst/stream/trail emitters, object pooling
- **Audio** — event-driven cues, posted by the engine and played by `Sound`. Twelve sound effects, generated
  from source rather than committed as opaque assets, and bundled in the jar. **The generator lives in the engine
  repository** (`aside/tools/fruitjump-audio.py`), not here — this line used to say `tools/fruitjump-audio.py`,
  which sends a reader of THIS repository looking for a file that is not in it.
- **Save/Load** — player, room, and global state, entity-ID tracking
- **The tutorial** — 10 hand-built levels, and the only place the game explains itself

## In the engine, not in the game

These are written and tested, and **nothing in the game constructs them.** They are listed here rather than in
the section above because a reader should not have to run `grep` to find out whether a boss fight is coming.

Each one also says what wiring it would take, so the decision is a read rather than a project.

- **AI Director** — `AIDirector` is L4D-style pacing (BUILD/PEAK/RELAX/RECOVER) with an intensity signal and a
  mercy window. Nothing builds one, *and* nothing reads its output — `Combat` reports into it and no code asks it
  anything. **To wire:** `new AIDirector()`, `combat.setDirector(director)` (that hook exists), and
  `director.update(dt)` each frame — **and then something has to consult `getSpawnMultiplier()` and
  `isMercyActive()`**, which is the part that does not exist: the generator would have to ask it when placing
  enemies. So this one is a design decision about how much it steers, not just a wire.
## Not in this repository

**Two things this list used to claim, and now two things it can claim again.** The feature list said "moving
platforms with carry" and "parallax" while neither was in the game: `MovingPlatform` and `ParallaxLayer` were built
and nothing constructed or drew them. Both were wired on 2026-10-09: moving platforms are placed by the generator as a ferry over a gap every third level, and the parallax ridges are drawn as two layers hung on the terrain's own top edge. The paragraph
is kept rather than deleted because the shape of the mistake is worth keeping: **a feature list is read first and
the detail is read later, so the claim is the part that misleads** - the same shape as the multiplayer claim
underneath it, which is still a claim with nothing behind it.

**Multiplayer.** This README used to claim an authoritative TCP server, client prediction and reconciliation,
lag compensation and entity interpolation. **No file for any of it has ever been committed on any branch.** It is
not a feature that regressed or was removed; it is a claim that was written and never backed. If it was built, it
was built somewhere that was not saved.

## Running the checks

```bash
tools/run-suites.sh      # everything: the test classes, the README counts, and the jar
```

One command, and it is the only one this repository has ever had. Until it existed the test classes were
each run by hand and nothing ran them together — so nothing was the thing that failed when this release drifted
away from the engine it is built from. It did drift: over two days six bugs were found here that the engine had
already fixed, among them an enemy that did not exist and a player who could not be hurt by their own bomb.

The tools under `tools/` are each runnable on their own — `check-jar-current.sh` asks whether the committed jar
still matches the source, `readme-counts.sh` compares the counts in this file against the files, and
`self-test.sh` breaks each tool's subject on purpose and requires the tool to notice. `check-jar-reproducible.sh`
builds the jar twice and requires the same bytes, because a rebuild that changes them defeats the
artifact comparison the release routine relies on. `style-classes.sh`
asks whether every style class the code names is actually defined in the stylesheet — the bug that made two
lines of the game-over screen nearly unreadable on 2026-10-04, which the engine's tools could catch and
nothing ran here, which is the repository where it happened.

`check-release-notes.sh` is separate, because it needs the network: it asks whether every file the **release notes**
tell you to download is actually attached to a release. Run it before publishing.

It runs four kinds of check, because they fail differently. **The tests** drive the real JavaFX screens, so they
need a display. **The README** counts are compared against the files. And **the jar** — `java -jar
tropical-punch.jar` is what this file tells people to run, so a committed jar built from older source is a silent
distribution bug: the fix is in the repository and not in the thing people download. That check compiles the
source fresh and compares it class by class against the classes inside the committed jar.

And **the tools themselves**: every one of them is a check, and a check that cannot fail is worse than no check
because it is believed. `self-test.sh` breaks each tool's subject and requires it to complain.

## Testing philosophy

Every system was built headless-first and verified by bots playing the game through the real engine — including a validator bot that walks generated levels blind (it found real bugs in all three layers: engine, bot, and generator). The menu and gameplay screens are verified by JavaFX robot tests under xvfb.

Notable bugs the bots found:
- **Phantom floor**: the collision sweep's zero-velocity axis case made a falling body collide with tiles at any horizontal distance — masked for 22 test suites because every test moved in both axes
- **Double-fire**: JavaFX buttons fire natively on ENTER when focused + the button's own listener + the screen's key handler = three fire paths for one keypress
- **The 48px player can't enter a 32px hole** — pockets need two cracked tiles

## Architecture

```
src/main/java/tropical/        — the game (screens, menus, tests)
src/main/java/tropical/engine/ — the engine (31 classes, display-agnostic; plus four test files and a probe)
src/main/resources/style.css   — UI styling
```

The engine never knows JavaFX exists — the same property that lets the validator bot and the engine suites
drive it headless. GameplayScreen is the view; the engine is the model.

## Credits

Original concept and art direction: Kinger (_Rubix_King2)
Engine and Java port: Roxanne 
