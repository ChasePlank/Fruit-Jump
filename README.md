# Fruit jump

A 2D action-adventure platformer in Java 17 + JavaFX — a radioactive banana on a floating island. Screen-transition based with hookshot, bombs, bow, and procedurally generated levels.

This is the Java engine + playable game.

## Run

```bash
java -jar tropical-punch.jar
```

Requires Java 17+ with JavaFX 17+ on the module path (the shaded jar bundles JavaFX for Linux; on Windows/Mac use the source build below).
release should let users play it immediately. Bound to be overhauled later

**Controls:**
- Arrow keys / WASD — move
- Space / W — jump
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

A complete platformer engine, built and tested layer by layer:

- **Physics** — fixed timestep (1/60), swept AABB collision (slab method), iterative multi-collision resolution, slopes (45°/30° walkable, 63°+ slides)
- **Movement** — run, jump, one-way platforms, moving platforms with carry
- **Combat** — contact damage, stomp (positional check), knockback, i-frames
- **Weapons** — hookshot (instant raycast, pull physics), arrows (fast, light gravity), bombs (thrown arc, 1.2s fuse, blast radius, destroy cracked terrain)
- **Enemies** — PATROL/CHASE AI with hysteresis, wall/ledge sensors
- **Items** — hearts, keys, locked doors, cracked tiles (bombable)
- **Level generation** — procedural ground walk with guaranteed traversability: gaps ≤3 cells, climbs ≤2 cells, landing runways in both directions, spike pits off the path, locked doors with keys on flat stretches, bombable pockets hiding hearts
- **Level validation** — a bot plays every generated level through the real physics engine before it ships; 100/100 fresh seeds pass
- **Camera** — smooth follow, look-ahead, room clamping, parallax
- **Particles** — burst/stream/trail emitters, object pooling
- **Audio** — event-driven (SFX triggers, music states); headless backend attaches later with zero gameplay changes
- **AI Director** — L4D-style pacing (BUILD/PEAK/RELAX/RECOVER), intensity signal, mercy window
- **Save/Load** — player, room, and global state, entity-ID tracking
- **Boss fights** — multi-phase AI, telegraphed attacks, weak-point windows, wall-stun
- **Multiplayer foundation** — authoritative TCP server, client prediction + reconciliation, lag compensation (server rewind), entity interpolation

## Testing philosophy

Every system was built headless-first and verified by bots playing the game through the real engine — including a validator bot that walks generated levels blind (it found real bugs in all three layers: engine, bot, and generator). The menu and gameplay screens are verified by JavaFX robot tests under xvfb.

Notable bugs the bots found:
- **Phantom floor**: the collision sweep's zero-velocity axis case made a falling body collide with tiles at any horizontal distance — masked for 22 test suites because every test moved in both axes
- **Double-fire**: JavaFX buttons fire natively on ENTER when focused + the button's own listener + the screen's key handler = three fire paths for one keypress
- **The 48px player can't enter a 32px hole** — pockets need two cracked tiles

## Architecture

```
src/main/java/tropical/        — the game (screens, menus, tests)
src/main/java/tropical/engine/ — the engine (25 classes, display-agnostic)
src/main/resources/style.css   — UI styling
```

The engine never knows JavaFX exists — same property that let the validator bot and netcode tests drive it headless. GameplayScreen is the view; the engine is the model.

## Credits

Original concept and art direction: Kinger (_Rubix_King2)
Engine and Java port: Roxanne 
