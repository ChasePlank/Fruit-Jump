package tropical;

import tropical.engine.*;
import javafx.animation.AnimationTimer;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import java.util.ArrayList;

/**
 * Gameplay screen: runs the headless platformer engine inside JavaFX.
 *
 * The engine stays display-agnostic — this screen is the VIEW layer:
 * - AnimationTimer fires per display pulse; an accumulator converts
 *   that to fixed 1/60 engine steps (the engine never sees variable dt)
 * - Canvas renders the world each frame (rect debug view; sprites later)
 * - Key events set player velocity
 * - HUD overlays hearts/keys/level
 * - ESC pushes the pause overlay (gameplay frozen underneath)
 */
public class GameplayScreen extends Screen {
    // Tuned constants (match the validator's verified values)
    private static final double RUN_SPEED = 200;
    private static final double JUMP_V = -420;
    // High-res: window and canvas are 2x the engine's logical 800x600.
    // The engine (physics, world coords) is untouched — only the VIEW
    // scales. Nearest-neighbor smoothing keeps pixel art crisp at 2x.
    private static final double SCALE = 2.0;
    private static final int VIEW_W = 800, VIEW_H = 600;
    private static final int CANVAS_W = (int) (VIEW_W * SCALE), CANVAS_H = (int) (VIEW_H * SCALE);

    // Engine state
    private final World world;
    private final Combat combat;
    private final Physics.Body player;
    private final PlayerInventory inventory;
    private final LevelMap map;

    /**
     * Floating tutorial words, or null on a generated level.
     *
     * <p>TUTORIAL MODE. The engine has had eight hand-built levels since aside grew them, and the release has
     * never been able to reach them - this screen always generated a level, so Tutorial.java sat here unused.
     * That is the same half-built shape as the water, the bats and the splash, one layer up: not a feature
     * nobody drew, a feature nobody could enter.
     */
    private final Sprites.Look look;
    private final AudioSystem audio;
    private final Sound sound;
    private final java.util.List<Tutorial.Sign> signs;
    /** True while playing the tutorial: hand-built levels, no autosave, ends at 8. */
    private final boolean tutorial;
    private final Camera camera;
    private final int levelNum;

    // View
    private final Canvas canvas;
    private final GraphicsContext gc;
    private final javafx.scene.layout.StackPane root;

    // Loop
    private AnimationTimer timer;
    private double accumulator = 0;
    private long lastPulse = -1;

    // Input state (held keys)
    private boolean left, right;
    // Held while the key is down: UP/W is jump on land and the swim-up stroke in water,
    // DOWN/S dives. The water system reads one of these every frame.
    private boolean up, down;

    // Facing direction (1=right, -1=left). Persists after keys release —
    // weapons fire where you're looking (playtest suggestion).
    private int facing = 1;

    // Weapons
    private final Hookshot hookshot;

    // Save/continue
    private static final String SAVE_FILE = System.getProperty("user.home")
            + "/.tropical-punch-autosave.txt";
    private double playTime = 0;

    public GameplayScreen(ScreenManager manager, int levelNum) {
        this(manager, levelNum, null);
    }

    /** Tutorial mode: the eight hand-built levels rather than the generator. */
    public GameplayScreen(ScreenManager manager, int levelNum, boolean tutorial) {
        this(manager, levelNum, null, tutorial);
    }

    /**
     * Full constructor. `resume` = a loaded GameState to restore into the
     * freshly generated level (autosave continuation), or null for a
     * fresh run.
     */
    public GameplayScreen(ScreenManager manager, int levelNum, SaveSystem.GameState resume) {
        this(manager, levelNum, resume, false);
    }

    public GameplayScreen(ScreenManager manager, int levelNum, SaveSystem.GameState resume, boolean tutorial) {
        super(manager);
        this.levelNum = levelNum;
        this.tutorial = tutorial;

        // Generate + build level (deterministic seed: same levelNum
        // always makes the same level — saves reference the level number)
        //
        // Tutorial levels are HAND-BUILT and not generated: they have to teach one thing each, in an order,
        // and a generator cannot be asked for that. Tutorial.map returns the level, Tutorial.signs the words
        // that float in it.
        if (tutorial) {
            map = Tutorial.map(levelNum);
            signs = Tutorial.signs(levelNum);
        } else {
            LevelGen gen = new LevelGen(60, 14, 1000L + levelNum, levelNum);
            map = gen.generate();
            signs = null;
        }
        world = new World();
        map.buildWorld(world);
        combat = new Combat();
        // The engine posts cue names; Sound plays them. Kept here rather than inside World because the
        // engine must stay JavaFX-free - it runs headless in the suites and on machines with no sound device.
        audio = new AudioSystem();
        world.setAudio(audio);
        sound = Sound.load(".");
        look = Sprites.buildLook(CharacterConfig.load());
        inventory = new PlayerInventory();

        // Player
        player = new Physics.Body(map.spawnX, map.spawnY, 24, 44);
        player.oneway = true;
        world.addBody(player);

        // Enemies from map
        for (double[] e : map.enemies) {
            world.addEnemy(new Enemy(e[0], e[1], 24, 24));
        }

        // Camera: room = the full level, whatever size it is. Viewport is the
        // PHYSICAL canvas size — worldToScreen returns physical pixels,
        // so all draw calls (sprites at 2x, tiles at 2x) land 1:1 on
        // screen with no resampling.
        camera = new Camera(CANVAS_W, CANVAS_H);
        camera.setRoom(60 * 32, map.heightCells() * 32);

        // Weapons
        hookshot = new Hookshot(player);

        // Restore from autosave if provided (level number must match —
        // the seed determines the level; a save from a different level
        // can't be applied to this one)
        if (resume != null && resume.levelNum == levelNum) {
            new SaveSystem().applyState(resume, player, combat, inventory, world);
            // -1 = checkpoint spawn marker: restore stats but spawn at
            // the level's start position, not a mid-level coordinate.
            if (resume.playerX < 0) {
                player.x = map.spawnX;
                player.y = map.spawnY;
                player.vx = 0;
                player.vy = 0;
            }
        }

        canvas = new Canvas(CANVAS_W, CANVAS_H);
        gc = canvas.getGraphicsContext2D();

        root = new javafx.scene.layout.StackPane(canvas);
        root.getStyleClass().add("screen-bg");

        // Fit the fixed-size canvas into the (now resizable) window:
        // uniform scale, centered, letterboxed. Without this the
        // 1600x1200 canvas overflows a smaller window and clips the
        // HUD (playtest: "stuck unable to see certain stats").
        javafx.scene.layout.StackPane.setMargin(canvas, null);
        canvas.setManaged(false);
        fitToWindow(root, canvas);
    }

    /** Bind a fixed-size canvas to its parent's size: scale to fit
     *  (preserve aspect), center. Re-evaluated on every resize. */
    static void fitToWindow(javafx.scene.layout.StackPane parent, Canvas canvas) {
        parent.widthProperty().addListener((obs, o, n) -> fitCanvas(parent, canvas));
        parent.heightProperty().addListener((obs, o, n) -> fitCanvas(parent, canvas));
        fitCanvas(parent, canvas);
    }

    static void fitCanvas(javafx.scene.layout.StackPane parent, Canvas canvas) {
        double pw = parent.getWidth(), ph = parent.getHeight();
        if (pw <= 0 || ph <= 0) return;
        // Scale pivots at the NODE CENTRE, not the top-left, so scaling already keeps the centre in place.
        // The translate therefore only has to centre the UNSCALED canvas - centring the scaled one as well
        // double-counts the shift and pushes the image off-centre.
        //
        // THIS WAS LIVE HERE AND INVISIBLE. The only canvas screen in this repository sized its canvas to the
        // window's maximum, so the scale was always exactly 1 and the pivot did not matter. The first screen
        // with a different canvas size - the customiser, at 1280x720 in a 1600x1200 window - came out with its
        // left edge cut off: "HE CLIMBER" instead of "THE CLIMBER". aside's copy has had this fix and this
        // comment for days; the release's did not.
        double s = Math.min(pw / canvas.getWidth(), ph / canvas.getHeight());
        canvas.setScaleX(s);
        canvas.setScaleY(s);
        canvas.setTranslateX((pw - canvas.getWidth()) / 2);
        canvas.setTranslateY((ph - canvas.getHeight()) / 2);
    }

    /** Snapshot current state and write the autosave file. */
    private void autosave() {
        // A tutorial run must never overwrite the save of a real run.
        if (tutorial) return;
        SaveSystem.GameState state = SaveSystem.GameState.snapshot(
            player, combat, inventory, world, "level" + levelNum, playTime);
        state.levelNum = levelNum;
        try {
            new SaveSystem().save(state, SAVE_FILE);
        } catch (java.io.IOException ex) {
            System.err.println("autosave failed: " + ex.getMessage());
        }
    }

    @Override
    public Parent getRoot() {
        return root;
    }

    // --- read-only accessors, for the tests ---------------------------------
    // The tutorial wiring is easy to remove by accident and nothing could see it happen: the menu test checks
    // the label is in the list, and no test opened the screen.

    /** True while playing the hand-built tutorial levels. */
    boolean isTutorial() { return tutorial; }

    int levelNumber() { return levelNum; }

    int mapHeightCells() { return map.heightCells(); }

    /** Is the player standing on something? The tutorial levels are 20 rows with the floor on row 17, so a
     *  screen that still assumed a 14-row level would have killed them on the first frame. */
    boolean playerIsSupported() {
        for (Physics.AABB t : world.tiles) {
            if (t.overlaps(player.aabb())) return true;
        }
        return player.grounded;
    }

    @Override
    public void enter() {
        if (timer == null) {
            timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    // now is in nanoseconds (display pulse).
                    // First pulse just records the timestamp.
                    if (lastPulse < 0) { lastPulse = now; return; }

                    double frameTime = (now - lastPulse) / 1e9;
                    lastPulse = now;
                    frameTime = Math.min(frameTime, 0.25);  // spiral-of-death cap

                    accumulator += frameTime;
                    while (accumulator >= GameLoop.DT) {
                        engineUpdate(GameLoop.DT);
                        accumulator -= GameLoop.DT;
                    }
                    render();
                }
            };
        }
        // Fresh accumulator — paused time is not simulated
        accumulator = 0;
        lastPulse = -1;
        timer.start();
    }

    @Override
    public void pause() {
        // Pause overlay pushed on top: stop the timer, freeze the world.
        if (timer != null) timer.stop();
    }

    @Override
    public void resume() {
        // Unpause: reset pulse tracking so the paused interval is not
        // simulated as one giant frame.
        accumulator = 0;
        lastPulse = -1;
        if (timer != null) timer.start();
    }

    @Override
    public void exit() {
        if (timer != null) timer.stop();
    }

    private void engineUpdate(double dt) {
        // Hookshot first: if it's pulling, it OWNS player velocity —
        // don't zero it with input, and let the physics step consume it.
        // (The old order — input zeroing, then world.update, then
        // hookshot.update — meant the pull velocity set at the end of
        // frame N was wiped before frame N+1's physics. The line drew
        // but the player never moved.)
        // A piranha bit this frame. The engine says what happened; the screen decides what it costs, because
        // Combat (hearts, i-frames, the hurt sound) lives here and not in World. This is the half the sync
        // script cannot carry - see its "SCREENS" warning, which is written from the three times a feature
        // arrived in this repository with nothing to draw or drive it.
        if (world.piranhaBit) combat.hurtPlayer(player, world.piranhaBitFromX);

        boolean pulling = hookshot.update(dt, world);
        
        // Player input → velocity (skipped while hookshot pulls)
        if (!pulling) {
            double desired = 0;
            if (left) desired -= RUN_SPEED;
            if (right) desired += RUN_SPEED;
            // Vertical: the water system reads this every frame and applies it only while the
            // body is actually in a pool.
            world.water.setVerticalInput(up ? -1 : (down ? 1 : 0));
            // Horizontal. On land this is the assignment it always was. In water it goes through
            // the water system, so a body steers instead of snapping and wading is a multiplier.
            player.vx = world.water.swimming(player)
                ? world.water.steerVx(player, desired, dt)
                : desired * world.water.speedMultiplier(player);
            // Facing persists after keys release — weapons fire where
            // you're LOOKING, not where you're holding (playtest:
            // "bombs default right, shift that to where youre facing")
            if (left && !right) facing = -1;
            else if (right && !left) facing = 1;
        }

        // Engine step
        world.update(dt);
        sound.drain(audio);
        combat.update(dt);

        // Enemy contact (stomp or hurt — Combat decides)
        for (Enemy e : new ArrayList<>(world.enemies)) {
            combat.processContact(player, e);
        }

        // Pickups (tryCollect applies HP/keys + audio itself)
        for (Pickup p : new ArrayList<>(world.pickups)) {
            p.tryCollect(player, combat, inventory);
        }

        // Doors (try to unlock if player has key and touches door)
        for (Door d : world.doors) {
            if (d.tryUnlock(player, inventory, combat)) {
                world.unlockDoor(d);
            }
        }

        // Spikes
        for (Physics.AABB sp : world.spikes) {
            if (sp.overlaps(player.aabb())) {
                combat.hurtPlayer(player, player.x + 1);
            }
        }

        // Death or fell out of world: game over
        // The level's REAL height, not 14.
        //
        // This was `player.y > 14 * 32 + 64`, and 14 is the height the generator happens to make. The tutorial
        // levels are hand-built at 20 rows with their floor on row 17, so the player spawned at y=528, the
        // bound was y>512, and every tutorial level ended in GAME OVER on its first frame. It is the same bug
        // as the `y > 500` projectile cull that used to live in Projectile: a bound hardcoded to one level
        // size, which is correct until the level size changes and then silently kills you.
        if (combat.playerDead() || player.y > map.heightCells() * 32 + 64) {
            manager.replace(new GameOverScreen(manager, levelNum, playTime));
            return;
        }

        // Exit reached: autosave (next level's checkpoint), then next
        // level (replace — no way back). The save stores the NEW level's
        // spawn state (fresh position, carried HP/keys) — Continue
        // resumes at the next level's start, which is the checkpoint.
        if (Math.abs(player.x - map.exitX) < 24 && Math.abs(player.y - map.exitY) < 40) {
            if (tutorial && Tutorial.endsTheTutorial(levelNum)) {
                // The tutorial is done - back to the menu, not on to level 9 of a generated run.
                manager.replace(new MainMenu(manager));
                return;
            }
            int nextLevel = levelNum + 1;
            SaveSystem.GameState checkpoint = new SaveSystem.GameState();
            checkpoint.levelNum = nextLevel;
            checkpoint.playerX = -1; checkpoint.playerY = -1;  // -1 = spawn
            checkpoint.playerHP = (int) combat.playerHP;
            checkpoint.keys = inventory.keys;
            checkpoint.playTime = playTime;
            try {
                new SaveSystem().save(checkpoint, SAVE_FILE);
            } catch (java.io.IOException ex) {
                System.err.println("checkpoint save failed: " + ex.getMessage());
            }
            // Carry HP/keys/playTime into the next level. The checkpoint
            // (playerX=-1) restores stats but spawns at the level start.
            // Previously the next level got a FRESH Combat — full HP
            // every level (playtest: "lives still reset each level").
            manager.replace(new GameplayScreen(manager, nextLevel, checkpoint, tutorial));
            return;
        }

        playTime += dt;

        // Camera follows (with look-ahead)
        camera.update(dt, player.x, player.y, player.vx);
    }

    private void render() {
        // All rendering is in PHYSICAL pixels (canvas 1600x1200).
        // worldToScreen returns physical coords; sprites are pre-
        // scaled 2x and drawn at 2x logical size — 1:1, no resampling.
        final double S = SCALE;

        // Sky
        gc.setFill(Color.web("#87CEEB"));
        gc.fillRect(0, 0, CANVAS_W, CANVAS_H);

        // Water: AFTER the sky (before it, the sky paints over the pool) and BEFORE the terrain, so
        // a pool reads as water in a pit with the tiles as its walls. The engine has generated
        // flooded gaps since this update and the screens could not show them, which made a pool an
        // invisible hole - and, because nothing here sent vertical input or allowed a jump out of
        // water, one a player could not leave. Both are fixed in this file.
        Water waterField = world.water.water();
        if (waterField != null && !waterField.isEmpty()) {
            for (double[] r : waterField.rects) {
                double sx = camera.worldToScreenX(r[0]), sy = camera.worldToScreenY(r[1]);
                double w = r[2] - r[0], h = r[3] - r[1];
                if (sx > CANVAS_W || sy > CANVAS_H || sx + w < 0 || sy + h < 0) continue;
                gc.setFill(Color.web("#2E86C1", 0.55));
                gc.fillRect(sx, sy, w, h);
                gc.setFill(Color.web("#7FD4F0", 0.85));   // surface line
                gc.fillRect(sx, sy, w, 3);
            }
        }

        // Facing sprite: mirrored variant when facing left
        // (playtest: "able to look both directions")
        javafx.scene.image.Image playerSprite =
                facing < 0 ? look.left2x : look.right2x;

        // Solid tiles: grass-topped dirt (rect base + grass strip)
        // Two things happen here that are not obvious, and neither was true of this file until it was carried
        // across from aside:
        //
        // 1. Door AABBs live in world.tiles because PHYSICS needs them solid, but they are not terrain.
        //    Drawing them painted the door's invisible anti-jump wall as a floating dirt block hovering a tile
        //    above every door.
        //
        // 2. The grass test ("is anything solid directly above me") was a nested loop over world.tiles -
        //    O(tiles^2). With the terrain now a solid mass instead of a one-cell strip that is a few hundred
        //    thousand comparisons per frame on a Celeron. One hash set per frame makes it O(tiles).
        java.util.HashSet<Long> solidCells = new java.util.HashSet<>();
        for (Physics.AABB t : world.tiles) {
            if (!isDoorBox(t)) solidCells.add(cellKey(t.x0, t.y0));
        }
        for (Physics.AABB t : world.tiles) {
            if (isDoorBox(t)) continue;
            drawGroundTile(t, solidCells);
        }
        // Cracked tiles: crack overlay on top of ground
        for (Physics.AABB t : world.cracked) drawCrackedTile(t);
        // One-ways: wooden platform
        for (Physics.AABB t : world.oneways) {
            double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
            double w = (t.x1 - t.x0) * S, h = (t.y1 - t.y0) * S;
            if (sx > CANVAS_W || sy > CANVAS_H || sx + w < 0 || sy + h < 0) continue;
            gc.setFill(Color.web("#8B5A2B"));
            gc.fillRect(sx, sy, w, h);
            gc.setFill(Color.web("#DAA520"));
            gc.fillRect(sx, sy, w, 8);
        }
        // Spikes: sprite (32 logical → 64 physical)
        for (Physics.AABB t : world.spikes) {
            double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
            if (sx > CANVAS_W || sx + 64 < 0) continue;
            gc.drawImage(Sprites.spike2x, sx, sy, 32 * S, 32 * S);
        }
        // Doors: visible sprite is 2 tiles (64px) sitting on the floor;
        // the collision wall above it is intentionally invisible.
        for (Door d : world.doors) {
            if (d.isSolid()) {
                double sx = camera.worldToScreenX(d.aabb().x0);
                double sy = camera.worldToScreenY(d.aabb().y1 - d.visibleH);
                if (sx > CANVAS_W || sx + 64 < 0) continue;
                gc.drawImage(Sprites.door2x, sx, sy, 32 * S, d.visibleH * S);
            }
        }

        // Pickups: sprites at 2x
        for (Pickup p : world.pickups) {
            if (!p.active) continue;
            double sx = camera.worldToScreenX(p.x - 8), sy = camera.worldToScreenY(p.y - 8);
            // SNACK, not heart. The engine re-themed this pickup in its "sunset look, the climber, the bat"
            // rework - sprite and sign together - and the TUTORIAL SIGN travelled here while the sprite did not.
            // So this release has been drawing a red heart under a sign that says "SNACK - one life back", and
            // the two have disagreed in plain sight on tutorial 6 since 1 October.
            //
            // Found by comparing every line of this file against the engine's copy and reading the ones that
            // nearly matched: this was one word different in an otherwise identical line.
            if (p.type == Pickup.Type.HEART) gc.drawImage(Sprites.snack2x, sx, sy, 16 * S, 16 * S);
            // THE JAR HAD NO BRANCH HERE, so it fell into the key fallback and WAS DRAWN AS A KEY. The safe-room
            // reward - "a life FOREVER, and a full refill", as the tutorial's own sign says - looked exactly like
            // a key, and the key sprite next to it looked the same too. Sprites.jar2x was built in this file's
            // own Sprites class and never drawn by anything.
            //
            // Found the same way as the snack: reading the near-matching lines, then asking what the OTHER lines
            // were doing. The tutorial-6 render showed two identical orange shapes where a jar and a key should
            // have been, and that was the jar.
            else if (p.type == Pickup.Type.JAR) gc.drawImage(Sprites.jar2x, sx, sy, 16 * S, 16 * S);
            else gc.drawImage(Sprites.key2x, sx, sy, 16 * S, 16 * S);
        }

        // Enemies: sprite at 2x
        for (Enemy e : world.enemies) {
            if (e.dead) continue;
            double sx = camera.worldToScreenX(e.body.x - e.body.hw),
                    sy = camera.worldToScreenY(e.body.y - e.body.hh);
            gc.drawImage(Sprites.enemy2x, sx, sy, e.body.hw * 2 * S, e.body.hh * 2 * S);
        }

        // Bats. The engine has placed them, flown them and stunned the player with them since the
        // engine came over from aside, and NOTHING DREW THEM - an invisible enemy that knocks you
        // flat is worse than no enemy at all, because there is no way to learn it is there. Found by
        // looking at a level rather than by a test: the level-15 screenshot had three bats in it
        // according to the generator and an empty sky on screen.
        // Piranhas. Drawn rather than sprited - there is no piranha art in either copy, and a fish that exists
        // in the engine and is not drawn is the exact bug this repository has fixed three times (bats, water,
        // splash). A body, a tail, an eye and a tooth line reads as a fish at this size.
        // Piranhas, drawn from a pixel grid like every other creature here. Was canvas ovals while there was no
        // piranha art; Sprite.PIRANHA is a hand-drawn grid in the same style as the bat and the banana.
        for (Piranha f : world.piranhas) {
            double pw = Sprite.PIRANHA[0].length() * S, ph = Sprite.PIRANHA.length * S;
            double px = camera.worldToScreenX(f.body.x) - pw / 2;
            double py = camera.worldToScreenY(f.body.y) - ph / 2;
            if (px > CANVAS_W || px + pw < 0) continue;
            gc.drawImage(f.state == Piranha.State.BITE ? Sprites.piranha2x : Sprites.piranha, px, py, pw, ph);
        }


        for (Bat bat : world.bats) {
            double bx = camera.worldToScreenX(bat.body.x - bat.body.hw),
                   by = camera.worldToScreenY(bat.body.y - bat.body.hh);
            gc.drawImage(Sprites.bat2x, bx, by, bat.body.hw * 2 * S, bat.body.hh * 2 * S);
        }

        // Particles, on top. The engine spawns a droplet burst on every surface crossing and nothing
        // has ever drawn one - the same shape as the bats and the water, and the third instance of it
        // in this update. Squares rather than circles: at 2-5 physical pixels they are the same
        // handful of pixels and the rect is one call.
        for (Particle p : world.particles().getAll()) {
            if (!p.isActive()) continue;
            double px = camera.worldToScreenX(p.px()) - p.psize() * S / 2;
            double py = camera.worldToScreenY(p.py()) - p.psize() * S / 2;
            double size = p.psize() * S;
            if (px > CANVAS_W || py > CANVAS_H || px + size < 0 || py + size < 0) continue;
            gc.setFill(new Color(p.pr(), p.pg(), p.pb(), p.palpha()));
            gc.fillRect(px, py, size, size);
        }

        // Exit portal. Sized and anchored FROM THE SPRITE, not from numbers written for the sprite it used to
        // be: the grid is 22 wide, and this drew it at 16 - squashed by a third - with an anchor two pixels
        // off. The old comment said "16x24 logical" and had been wrong since the art changed.
        //
        // Bottom-aligned on the platform under the 'E' cell: exitY is the cell centre, so the floor the portal
        // stands on is one half-cell below it.
        double exitW = Sprite.EXIT[0].length(), exitH = Sprite.EXIT.length;
        double ex = camera.worldToScreenX(map.exitX - exitW / 2);
        double ey = camera.worldToScreenY(map.exitY + 16 - exitH);
        if (ex > -exitW * S && ex < CANVAS_W) {
            gc.drawImage(Sprites.exit2x, ex, ey, exitW * S, exitH * S);
        }

        // Player: the climber, in whatever colours CharacterConfig says. Drawn at its
        // OWN aspect, centered on the body — the 14x22 grid stretched
        // into the 24x44 body box read as a pencil (playtest).
        //
        // The sprite comes from `look` rather than a fixed BANANA grid, because hair and pack ARE this
        // character's identity — it has no face — and the customiser screen would be decoration if the game
        // did not read the config back. The geometry is unchanged: this screen centres on the body where
        // aside's bottom-aligns on the feet, and that is a separate difference, not one to fold in here.
        {
            double ph = player.hh * 2 * S;
            double pw = ph * (look.gridW / (double) look.gridH);
            double px = camera.worldToScreenX(player.x) - pw / 2;
            double py = camera.worldToScreenY(player.y) - ph / 2;
            gc.drawImage(playerSprite, px, py, pw, ph);
        }

        // Projectiles
        for (Projectile p : world.projectiles) {
            if (!p.active) continue;
            if (p.type == Projectile.Type.ARROW) {
                gc.drawImage(Sprites.arrow2x, camera.worldToScreenX(p.x - 6), camera.worldToScreenY(p.y - 2), 12 * S, 4 * S);
            } else {
                // Bomb: red flash as fuse burns
                gc.drawImage(p.timer < 0.4 ? Sprites.bombFlash2x : Sprites.bomb2x,
                        camera.worldToScreenX(p.x - 6), camera.worldToScreenY(p.y - 6), 12 * S, 12 * S);
            }
        }

        // Hookshot line (player → hook tip while active). Pulling draws
        // to the anchor; retracting (missed shot) draws to the hook tip
        // — the old code always drew to anchorX/anchorY, which is stale
        // on a miss, so the line pointed at nothing off-screen.
        if (hookshot.isActive()) {
            double tipX = hookshot.isPulling() ? hookshot.anchorX : hookshot.hookX;
            double tipY = hookshot.isPulling() ? hookshot.anchorY : hookshot.hookY;
            gc.setStroke(Color.web("#C0C0C0"));
            gc.setLineWidth(3 * S);
            gc.strokeLine(camera.worldToScreenX(player.x), camera.worldToScreenY(player.y),
                          camera.worldToScreenX(tipX), camera.worldToScreenY(tipY));
            // Hook claw at the tip
            gc.setFill(Color.web("#C0C0C0"));
            double ax = camera.worldToScreenX(tipX), ay = camera.worldToScreenY(tipY);
            gc.fillOval(ax - 4 * S, ay - 4 * S, 8 * S, 8 * S);
        }

        // Tutorial words, floating where they belong in the world rather than parked at the top of the
        // screen - the whole point of a sign is that it is next to the thing it is about.
        if (signs != null) {
            gc.setFont(Font.font("Arial", 22));
            for (Tutorial.Sign sign : signs) {
                double sx = camera.worldToScreenX(sign.x());
                double sy = camera.worldToScreenY(sign.y());
                java.util.List<String> lines = wrapSign(sign.text());
                double widest = 0;
                for (String line : lines) widest = Math.max(widest, line.length());
                if (sx > CANVAS_W || sx + widest * 14 < 0) continue;
                // CLAMPED, because wrapping alone was not enough. Tutorial 9's PIRANHA sign is anchored so far
                // right that even a 34-character line ran off - "they come in groups. get out an". A sign that
                // is wrapped but still past the edge is a sign half-read.
                sx = Math.min(sx, CANVAS_W - widest * 12 - 8);
                for (int i = 0; i < lines.size(); i++) {
                    double ly = sy + i * 26;
                    gc.setFill(Color.web("#1a1a2e"));
                    gc.fillText(lines.get(i), sx + 2, ly + 2);
                    gc.setFill(Color.WHITE);
                    gc.fillText(lines.get(i), sx, ly);
                }
            }
        }

        renderHUD();
    }

    /** Grid-cell key for the solid-cell set. Cell-aligned coords only. */
    private static long cellKey(double x, double y) {
        return ((long) (x / 32) << 20) ^ (long) (y / 32);
    }

    /** Is this AABB a door's collision box rather than terrain? */
    private boolean isDoorBox(Physics.AABB t) {
        for (Door d : world.doors) {
            if (d.aabb() == t) return true;
        }
        return false;
    }

    private void drawGroundTile(Physics.AABB t, java.util.HashSet<Long> solidCells) {
        final double S = SCALE;
        double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
        double w = (t.x1 - t.x0) * S, h = (t.y1 - t.y0) * S;
        if (sx > CANVAS_W || sy > CANVAS_H || sx + w < 0 || sy + h < 0) return;
        // Dirt base
        gc.setFill(Color.web("#8B5A2B"));
        gc.fillRect(sx, sy, w, h);
        // Grass top ONLY if nothing solid directly above (a stacked
        // column of tiles shouldn't have grass bands mid-pillar —
        // visible in the first screenshot as stripes on every segment)
        gc.setFill(Color.web("#228B22"));
        boolean above = solidCells.contains(cellKey(t.x0, t.y0 - 32));
        if (!above) gc.fillRect(sx, sy, w, Math.min(16, h));
    }

    private void drawCrackedTile(Physics.AABB t) {
        final double S = SCALE;
        double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
        if (sx > CANVAS_W || sx + 64 < 0) return;
        gc.drawImage(Sprites.crack2x, sx, sy, 32 * S, 32 * S);
    }

    private void renderHUD() {
        gc.setFont(Font.font("Arial", 20));
        // Reset transform for HUD: text should render at native res,
        // not scaled (scaled text is blurry and mispositioned).
        gc.setTransform(1, 0, 0, 1, 0, 0);
        // Hearts
        gc.setFill(Color.RED);
        for (int i = 0; i < (int) combat.playerHP; i++) {
            gc.fillText("<3", 40 + i * 68, 64);
        }
        // Keys
        gc.setFill(Color.GOLD);
        gc.fillText("Key x" + inventory.keys, 40, 120);
        // Level
        gc.setFill(Color.WHITE);
        gc.fillText(tutorial ? "Tutorial " + levelNum + " / " + Tutorial.LAST : "Level " + levelNum,
                CANVAS_W - 260, 64);
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case LEFT, A -> { left = true; e.consume(); }
            case RIGHT, D -> { right = true; e.consume(); }
            case DOWN, S -> { down = true; e.consume(); }
            case SPACE, UP, W -> {
                // THE LINE THAT WAS A SOFT-LOCK. This was `if (player.grounded)`, and a body
                // floating in a pool is not grounded - so a player who walked into a flooded gap
                // could never jump, never touch the bottom, and never leave. The pool is 64px deep
                // with the surface level with the walk, so buoyancy holds the body at the surface
                // and the ledge is one cell up and unreachable. Measured before the fix: x=276
                // feet=135 inWater=true grounded=false, after six seconds of holding right and
                // pressing jump every frame. STUCK.
                //
                // jumpV answers for the water: a breach hop at the surface (how a pool is
                // escaped), a paddle when fully under, and the plain jump when dry.
                up = true;
                if (player.grounded || world.water.swimming(player)) {
                    player.vy = world.water.jumpV(player, JUMP_V);
                }
                e.consume();
            }
            case X -> {
                // Hookshot: X while pulling = cancel (player agency —
                // a pull must never hold the player hostage).
                if (hookshot.isPulling()) {
                    hookshot.release();
                } else {
                    double dx = facing;
                    double dy = 0;
                    if (e.isShiftDown()) dy = -1;  // Shift+X = fire upward
                    hookshot.fire(dx, dy, world);
                }
                e.consume();
            }
            case F -> {
                // Arrow: fast projectile in facing direction
                // From the middle of the body, not above it. player.y is the CENTRE of the physics box, so -10
                // fired the arrow from the chest upward and it sailed over anything level with you. Reported from
                // play: "the arrows shoot from above the player so if you're level with the enemy it'll fly over."
                //
                // PORTED FROM aside, 6 October 2026. This fix landed in the engine on 1 October and never reached
                // the release - the two copies of this screen diverged and the release kept the bug for five days.
                // Found by comparing the two by hand after check-fnaf-match.sh showed the FNAF pair was already
                // identical: the same class of drift, in the same repository family, with nothing looking for it.
                world.addProjectile(Projectile.arrow(player.x, player.y, facing));
                e.consume();
            }
            case G -> {
                // Bomb: thrown arc in facing direction
                world.addProjectile(Projectile.bomb(player.x, player.y - 10, facing));
                e.consume();
            }
            case ESCAPE -> {
                manager.push(new PauseScreen(manager, this));
                e.consume();
            }
        }
    }

    /** Key release — Main routes KEY_RELEASED here (held-key movement). */
    public void handleKeyReleased(KeyEvent e) {
        switch (e.getCode()) {
            case LEFT, A -> left = false;
            case RIGHT, D -> right = false;
            case SPACE, UP, W -> up = false;
            case DOWN, S -> down = false;
        }
    }
    /**
     * A SIGN THAT RUNS OFF THE EDGE IS WRAPPED, NOT MOVED.
     *
     * <p>Found by looking at tutorial 9: its two long signs are anchored near the right-hand end of the level and
     * the text ran past the edge - "unlike a bat, this one takes a H". The longest signs are 46 to 48 characters,
     * about 550 pixels, so anything anchored past x=730 lost its end.
     *
     * <p>A sign points at something in the world, so moving it would move it away from the thing it is talking
     * about. Making it taller keeps it where it belongs.
     */
    static java.util.List<String> wrapSign(String text) {
        final int WIDTH = 34;
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text.length() <= WIDTH) { out.add(text); return out; }
        String rest = text;
        while (rest.length() > WIDTH) {
            int cut = rest.lastIndexOf(' ', WIDTH);
            if (cut <= 0) cut = WIDTH;
            out.add(rest.substring(0, cut).stripTrailing());
            rest = rest.substring(cut).stripLeading();
        }
        if (!rest.isEmpty()) out.add(rest);
        return out;
    }

}
