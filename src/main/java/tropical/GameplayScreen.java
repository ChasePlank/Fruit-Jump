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
    private static final int VIEW_W = 800, VIEW_H = 600;

    // Engine state
    private final World world;
    private final Combat combat;
    private final Physics.Body player;
    private final PlayerInventory inventory;
    private final LevelMap map;
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

    // Weapons
    private final Hookshot hookshot;

    // Save/continue
    private static final String SAVE_FILE = System.getProperty("user.home")
            + "/.tropical-punch-autosave.txt";
    private double playTime = 0;

    public GameplayScreen(ScreenManager manager, int levelNum) {
        this(manager, levelNum, null);
    }

    /**
     * Full constructor. `resume` = a loaded GameState to restore into the
     * freshly generated level (autosave continuation), or null for a
     * fresh run.
     */
    public GameplayScreen(ScreenManager manager, int levelNum, SaveSystem.GameState resume) {
        super(manager);
        this.levelNum = levelNum;

        // Generate + build level (deterministic seed: same levelNum
        // always makes the same level — saves reference the level number)
        LevelGen gen = new LevelGen(60, 14, 1000L + levelNum, levelNum);
        map = gen.generate();
        world = new World();
        map.buildWorld(world);
        combat = new Combat();
        world.setAudio(new AudioSystem());  // headless: logs only
        inventory = new PlayerInventory();

        // Player
        player = new Physics.Body(map.spawnX, map.spawnY, 24, 44);
        player.oneway = true;
        world.addBody(player);

        // Enemies from map
        for (double[] e : map.enemies) {
            world.addEnemy(new Enemy(e[0], e[1], 24, 24));
        }

        // Camera: room = full level (60*32 x 14*32)
        camera = new Camera(VIEW_W, VIEW_H);
        camera.setRoom(60 * 32, 14 * 32);

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

        canvas = new Canvas(VIEW_W, VIEW_H);
        gc = canvas.getGraphicsContext2D();

        root = new javafx.scene.layout.StackPane(canvas);
        root.getStyleClass().add("screen-bg");
    }

    /** Snapshot current state and write the autosave file. */
    private void autosave() {
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
        boolean pulling = hookshot.update(dt, world);
        
        // Player input → velocity (skipped while hookshot pulls)
        if (!pulling) {
            player.vx = 0;
            if (left) player.vx -= RUN_SPEED;
            if (right) player.vx += RUN_SPEED;
        }

        // Engine step
        world.update(dt);
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
        if (combat.playerDead() || player.y > 14 * 32 + 64) {
            manager.replace(new GameOverScreen(manager, levelNum, playTime));
            return;
        }

        // Exit reached: autosave (next level's checkpoint), then next
        // level (replace — no way back). The save stores the NEW level's
        // spawn state (fresh position, carried HP/keys) — Continue
        // resumes at the next level's start, which is the checkpoint.
        if (Math.abs(player.x - map.exitX) < 24 && Math.abs(player.y - map.exitY) < 40) {
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
            manager.replace(new GameplayScreen(manager, nextLevel));
            return;
        }

        playTime += dt;

        // Camera follows (with look-ahead)
        camera.update(dt, player.x, player.y, player.vx);
    }

    private void render() {
        // Sky
        gc.setFill(Color.web("#87CEEB"));
        gc.fillRect(0, 0, VIEW_W, VIEW_H);

        // Solid tiles: grass-topped dirt (rect base + grass strip)
        for (Physics.AABB t : world.tiles) drawGroundTile(t);
        // Cracked tiles: crack overlay on top of ground
        for (Physics.AABB t : world.cracked) drawCrackedTile(t);
        // One-ways: wooden platform
        for (Physics.AABB t : world.oneways) {
            gc.setFill(Color.web("#8B5A2B"));
            drawTile(t);
            gc.setFill(Color.web("#DAA520"));
            double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
            gc.fillRect(sx, sy, t.x1 - t.x0, 4);
        }
        // Spikes: sprite
        for (Physics.AABB t : world.spikes) {
            double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
            if (sx > VIEW_W || sx + (t.x1 - t.x0) < 0) continue;
            gc.drawImage(Sprites.spike, sx, sy, 32, 32);
        }
        // Doors: sprite (3 tiles tall, from fr-3 to floor)
        for (Door d : world.doors) {
            if (d.isSolid()) {
                double sx = camera.worldToScreenX(d.aabb().x0), sy = camera.worldToScreenY(d.aabb().y0);
                if (sx > VIEW_W || sx + 32 < 0) continue;
                gc.drawImage(Sprites.door, sx, sy, 32, 32 * 4);
            }
        }

        // Pickups: sprites
        for (Pickup p : world.pickups) {
            if (!p.active) continue;
            double sx = camera.worldToScreenX(p.x - 8), sy = camera.worldToScreenY(p.y - 8);
            if (p.type == Pickup.Type.HEART) gc.drawImage(Sprites.heart, sx, sy, 16, 16);
            else gc.drawImage(Sprites.key, sx, sy, 16, 16);
        }

        // Enemies: sprite
        for (Enemy e : world.enemies) {
            if (e.dead) continue;
            double sx = camera.worldToScreenX(e.body.x - e.body.hw),
                    sy = camera.worldToScreenY(e.body.y - e.body.hh);
            gc.drawImage(Sprites.enemy, sx, sy, e.body.hw * 2, e.body.hh * 2);
        }

        // Exit flag
        double ex = camera.worldToScreenX(map.exitX - 12), ey = camera.worldToScreenY(map.exitY - 20);
        if (ex > -32 && ex < VIEW_W) gc.drawImage(Sprites.exit, ex, ey, 16, 24);

        // Player: radioactive banana sprite (48x44 body → 24x22 sprite scaled)
        gc.drawImage(Sprites.banana,
                camera.worldToScreenX(player.x - player.hw),
                camera.worldToScreenY(player.y - player.hh),
                player.hw * 2, player.hh * 2);

        // Projectiles
        for (Projectile p : world.projectiles) {
            if (!p.active) continue;
            if (p.type == Projectile.Type.ARROW) {
                gc.drawImage(Sprites.arrow, camera.worldToScreenX(p.x - 6), camera.worldToScreenY(p.y - 2), 12, 4);
            } else {
                // Bomb: red flash as fuse burns
                gc.drawImage(p.timer < 0.4 ? Sprites.bombFlash : Sprites.bomb,
                        camera.worldToScreenX(p.x - 6), camera.worldToScreenY(p.y - 6), 12, 12);
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
            gc.setLineWidth(3);
            gc.strokeLine(camera.worldToScreenX(player.x), camera.worldToScreenY(player.y),
                          camera.worldToScreenX(tipX), camera.worldToScreenY(tipY));
            // Hook claw at the tip
            gc.setFill(Color.web("#C0C0C0"));
            double ax = camera.worldToScreenX(tipX), ay = camera.worldToScreenY(tipY);
            gc.fillOval(ax - 4, ay - 4, 8, 8);
        }

        renderHUD();
    }

    private void drawGroundTile(Physics.AABB t) {
        double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
        double w = t.x1 - t.x0, h = t.y1 - t.y0;
        if (sx > VIEW_W || sy > VIEW_H || sx + w < 0 || sy + h < 0) return;
        // Dirt base
        gc.setFill(Color.web("#8B5A2B"));
        gc.fillRect(sx, sy, w, h);
        // Grass top ONLY if nothing solid directly above (a stacked
        // column of tiles shouldn't have grass bands mid-pillar —
        // visible in the first screenshot as stripes on every segment)
        gc.setFill(Color.web("#228B22"));
        boolean above = false;
        for (Physics.AABB o : world.tiles) {
            if (o.x0 == t.x0 && o.y1 == t.y0) { above = true; break; }
        }
        if (!above) gc.fillRect(sx, sy, w, Math.min(8, h));
    }

    private void drawCrackedTile(Physics.AABB t) {
        double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
        if (sx > VIEW_W || sx + 32 < 0) return;
        gc.drawImage(Sprites.crack, sx, sy, 32, 32);
    }

    private void drawTile(Physics.AABB t) {
        double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
        double w = t.x1 - t.x0, h = t.y1 - t.y0;
        // Cull off-screen
        if (sx > VIEW_W || sy > VIEW_H || sx + w < 0 || sy + h < 0) return;
        gc.fillRect(sx, sy, w, h);
    }

    private void renderHUD() {
        gc.setFont(Font.font("Arial", 20));
        // Hearts
        gc.setFill(Color.RED);
        for (int i = 0; i < (int) combat.playerHP; i++) {
            gc.fillText("<3", 20 + i * 34, 32);
        }
        // Keys
        gc.setFill(Color.GOLD);
        gc.fillText("Key x" + inventory.keys, 20, 60);
        // Level
        gc.setFill(Color.WHITE);
        gc.fillText("Level " + levelNum, VIEW_W - 100, 32);
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case LEFT, A -> { left = true; e.consume(); }
            case RIGHT, D -> { right = true; e.consume(); }
            case SPACE, UP, W -> {
                if (player.grounded) player.vy = JUMP_V;
                e.consume();
            }
            case X -> {
                // Hookshot: fires toward facing direction (last horizontal
                // input; up if holding jump). Instant raycast at fire time.
                double dx = (right ? 1 : 0) - (left ? 1 : 0);
                double dy = 0;
                if (e.isShiftDown()) dy = -1;  // Shift+X = fire upward
                if (dx == 0 && dy == 0) dx = 1;  // default: face right
                hookshot.fire(dx, dy, world);
                e.consume();
            }
            case F -> {
                // Arrow: fast projectile in facing direction
                int dir = (left && !right) ? -1 : 1;
                world.addProjectile(Projectile.arrow(player.x, player.y - 10, dir));
                e.consume();
            }
            case G -> {
                // Bomb: thrown arc in facing direction
                int dir = (left && !right) ? -1 : 1;
                world.addProjectile(Projectile.bomb(player.x, player.y - 10, dir));
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
        }
    }
}
