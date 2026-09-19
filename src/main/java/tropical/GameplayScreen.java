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

    public GameplayScreen(ScreenManager manager, int levelNum) {
        super(manager);
        this.levelNum = levelNum;

        // Generate + build level
        LevelGen gen = new LevelGen(60, 14, 1000L + levelNum);
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

        canvas = new Canvas(VIEW_W, VIEW_H);
        gc = canvas.getGraphicsContext2D();

        root = new javafx.scene.layout.StackPane(canvas);
        root.getStyleClass().add("screen-bg");
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
        // Player input → velocity
        player.vx = 0;
        if (left) player.vx -= RUN_SPEED;
        if (right) player.vx += RUN_SPEED;

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

        // Spikes
        for (Physics.AABB sp : world.spikes) {
            if (sp.overlaps(player.aabb())) {
                combat.hurtPlayer(player, player.x + 1);
            }
        }

        // Death or fell out of world: respawn
        if (combat.playerDead() || player.y > 14 * 32 + 64) {
            combat.playerHP = 3;
            player.x = map.spawnX;
            player.y = map.spawnY;
            player.vx = 0;
            player.vy = 0;
        }

        // Exit reached: next level (replace — no way back)
        if (Math.abs(player.x - map.exitX) < 24 && Math.abs(player.y - map.exitY) < 40) {
            manager.replace(new GameplayScreen(manager, levelNum + 1));
            return;
        }

        // Camera follows (with look-ahead)
        camera.update(dt, player.x, player.y, player.vx);

        // Hookshot pull (engine handles the physics; update returns
        // false when the shot is done)
        hookshot.update(dt, world);
    }

    private void render() {
        // Sky
        gc.setFill(Color.web("#87CEEB"));
        gc.fillRect(0, 0, VIEW_W, VIEW_H);

        // Solid tiles
        gc.setFill(Color.web("#228B22"));
        for (Physics.AABB t : world.tiles) drawTile(t);
        // One-ways
        gc.setFill(Color.web("#DAA520"));
        for (Physics.AABB t : world.oneways) drawTile(t);
        // Spikes
        gc.setFill(Color.web("#A9A9A9"));
        for (Physics.AABB t : world.spikes) drawTile(t);
        // Doors
        gc.setFill(Color.web("#8B4513"));
        for (Door d : world.doors) drawTile(d.aabb());

        // Pickups
        for (Pickup p : world.pickups) {
            if (!p.active) continue;
            if (p.type == Pickup.Type.HEART) gc.setFill(Color.web("#FF4444"));
            else gc.setFill(Color.web("#FFD700"));
            gc.fillRect(camera.worldToScreenX(p.x - p.hw),
                        camera.worldToScreenY(p.y - p.hh),
                        p.hw * 2, p.hh * 2);
        }

        // Enemies
        for (Enemy e : world.enemies) {
            if (e.dead) continue;
            gc.setFill(Color.web("#8B0000"));
            gc.fillRect(camera.worldToScreenX(e.body.x - e.body.hw),
                        camera.worldToScreenY(e.body.y - e.body.hh),
                        e.body.hw * 2, e.body.hh * 2);
        }

        // Player (radioactive banana)
        gc.setFill(Color.web("#F5A623"));
        gc.fillRect(camera.worldToScreenX(player.x - player.hw),
                    camera.worldToScreenY(player.y - player.hh),
                    player.hw * 2, player.hh * 2);

        // Projectiles
        for (Projectile p : world.projectiles) {
            if (!p.active) continue;
            if (p.type == Projectile.Type.ARROW) {
                gc.setFill(Color.web("#DEB887"));
                gc.fillRect(camera.worldToScreenX(p.x - 6), camera.worldToScreenY(p.y - 2), 12, 4);
            } else {
                // Bomb: pulsing circle, red as fuse burns
                gc.setFill(p.timer < 0.4 ? Color.web("#FF4500") : Color.web("#2F4F4F"));
                double r = 6;
                gc.fillOval(camera.worldToScreenX(p.x - r), camera.worldToScreenY(p.y - r), r * 2, r * 2);
            }
        }

        // Hookshot line (player → anchor while active)
        if (hookshot.isActive()) {
            gc.setStroke(Color.web("#C0C0C0"));
            gc.setLineWidth(3);
            gc.strokeLine(camera.worldToScreenX(player.x), camera.worldToScreenY(player.y),
                          camera.worldToScreenX(hookshot.anchorX), camera.worldToScreenY(hookshot.anchorY));
        }

        renderHUD();
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
