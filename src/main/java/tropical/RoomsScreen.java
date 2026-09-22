package tropical;

import javafx.animation.AnimationTimer;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import tropical.engine.*;

/**
 * Zelda-style screen-transition gameplay: a grid of rooms, camera fixed
 * per room, edge-crossing triggers a transition to the adjacent room.
 * This is the architecture Kinger's design (Scratch project) is built
 * around — distinct from the scrolling GameplayScreen.
 */
public class RoomsScreen extends Screen {
    // 2x render scale: canvas 1600x960, room logic at 800x480.
    static final double S = 2.0;
    static final int VIEW_W = 800, VIEW_H = 480;
    static final int CANVAS_W = (int)(VIEW_W * S), CANVAS_H = (int)(VIEW_H * S);

    final RoomWorld world;
    final tropical.engine.ScreenManager screens;  // engine room-transition manager
    final World phys;
    final Physics.Body player;
    final Combat combat;
    final PlayerInventory inventory;

    final Canvas canvas;
    final GraphicsContext gc;
    final javafx.scene.layout.StackPane root;

    AnimationTimer timer;
    double accumulator = 0;
    long lastPulse = -1;
    boolean left, right, up, down;
    int facing = 1;  // 1=right, -1=left (persists — render + future weapons)

    public RoomsScreen(ScreenManager manager, int levelNum) {
        super(manager);
        world = new RoomWorld(5, 5, 2000L + levelNum);  // 25 rooms — theme changes every 10 along the path
        phys = new World();
        combat = new Combat();
        inventory = new PlayerInventory();

        // player starts center of the start room (top-down: no floor
        // strip to stand on — center is open corridor)
        player = new Physics.Body(RoomWorld.ROOM_W / 2 - 16, RoomWorld.ROOM_H / 2 - 16, 32, 32);
        player.oneway = true;
        phys.addBody(player);

        screens = new tropical.engine.ScreenManager(player);
        for (int r = 0; r < world.rows; r++)
            for (int c = 0; c < world.cols; c++)
                screens.add(world.grid[r][c]);
        screens.start(world.startRoomId);
        // load the start room's geometry + content
        loadRoom(world.startRoomId);

        canvas = new Canvas(CANVAS_W, CANVAS_H);
        gc = canvas.getGraphicsContext2D();
        root = new javafx.scene.layout.StackPane(canvas);
        root.getStyleClass().add("screen-bg");
    }

    /** Load a room's geometry + content into the live world. Called on
     *  start and after every room transition. */
    void loadRoom(String roomId) {
        phys.clearRoom();
        phys.addBody(player);

        Room room = screens.getRoom(roomId);
        phys.tiles.addAll(room.getTiles());
        phys.oneways.addAll(room.getOneways());

        // Content from the generator (enemies/pickups/doors). Pickups
        // that were already collected stay collected (active=false is
        // stored in the generator's list — the same objects reload).
        for (double[] f : world.roomEnemies.getOrDefault(roomId, java.util.List.of())) {
            Enemy e = new Enemy(f[0], f[1], 28, 28);
            e.body.noGravity = true;
            e.dir = (int) f[2];
            phys.addEnemy(e);
        }
        for (Pickup p : world.roomPickups.getOrDefault(roomId, java.util.List.of())) {
            if (p.active) phys.addPickup(p);
        }
        for (Door d : world.roomDoors.getOrDefault(roomId, java.util.List.of())) {
            if (d.isSolid()) {
                phys.doors.add(d);
                phys.tiles.add(d.aabb());
            }
        }
        currentTheme = world.themeFor(roomId);
    }

    RoomWorld.Theme currentTheme = RoomWorld.Theme.JUNGLE;

    @Override public Parent getRoot() { return root; }

    @Override
    public void enter() {
        if (timer == null) {
            timer = new AnimationTimer() {
                @Override public void handle(long now) {
                    if (lastPulse < 0) lastPulse = now;
                    double frame = (now - lastPulse) / 1e9;
                    lastPulse = now;
                    accumulator += Math.min(frame, 0.25);
                    double dt = GameLoop.DT;
                    while (accumulator >= dt) {
                        update(dt);
                        accumulator -= dt;
                    }
                    render();
                }
            };
        }
        lastPulse = -1;
        timer.start();
    }

    @Override public void exit() { if (timer != null) timer.stop(); }

    void update(double dt) {
        // TOP-DOWN (2.5D) movement — RPG dungeon feel, original-Zelda
        // style (playtest: "less of a platformer, more like an rpg
        // dungeon"). No gravity, no jump: the player walks in 4
        // directions and obstacles are walked AROUND, not jumped over.
        player.noGravity = true;
        player.vx = (right ? 1 : 0) * 220 - (left ? 1 : 0) * 220;
        player.vy = (down ? 1 : 0) * 220 - (up ? 1 : 0) * 220;
        if (left && !right) facing = -1;
        else if (right && !left) facing = 1;

        String roomBefore = screens.currentRoomId();
        phys.update(dt);
        screens.update(dt, phys);  // edge-crossing detection + transitions

        // Room changed (transition completed): load the new room's
        // content. Note: transitions take 0.3s; the ID changes when the
        // transition COMPLETES.
        if (!screens.currentRoomId().equals(roomBefore)) {
            loadRoom(screens.currentRoomId());
        }

        // Enemies: World.update already runs updateAI (chase logic uses
        // the player body it finds). Top-down needs noGravity on enemy
        // bodies — set at load. senseWall feeding is handled by World
        // via its own sensors (side-view ones work fine horizontally).

        // Pickups
        for (Pickup p : new java.util.ArrayList<>(phys.pickups)) {
            p.tryCollect(player, combat, inventory);
        }

        // Doors: unlock with a key when touching
        for (Door d : new java.util.ArrayList<>(phys.doors)) {
            if (d.tryUnlock(player, inventory, combat)) {
                phys.tiles.remove(d.aabb());
                phys.doors.remove(d);
            }
        }

        // Enemy contact damage (no stomping in top-down)
        for (Enemy e : phys.enemies) {
            if (!e.dead && e.overlaps(player)) {
                combat.hurtPlayer(player, e.body.x);
            }
        }

        // Death: respawn in the start room with full HP (prototype —
        // softer than the platformer's game-over)
        if (combat.playerDead()) {
            combat.playerHP = 3;
            player.x = RoomWorld.ROOM_W / 2;
            player.y = RoomWorld.ROOM_H / 2;
            player.vx = 0; player.vy = 0;
            screens.start(world.startRoomId);
            loadRoom(world.startRoomId);
        }

        // out of the world safety net (shouldn't happen — rooms are walled)
        if (player.y > RoomWorld.ROOM_H + 200 || player.y < -200) {
            player.x = RoomWorld.ROOM_W / 2;
            player.y = RoomWorld.ROOM_H / 2;
            player.vx = 0; player.vy = 0;
        }
    }

    void render() {
        // All drawing in physical pixels: room coords * S.
        // Floor: theme color (biome — changes every 10 rooms)
        gc.setFill(Color.web(currentTheme.floorColor));
        gc.fillRect(0, 0, CANVAS_W, CANVAS_H);
        // room tiles (fixed camera: world coords = screen coords, scaled)
        for (Physics.AABB t : phys.tiles) drawGround(t);
        for (Physics.AABB o : phys.oneways) {
            gc.setFill(Color.web("#DEB887"));
            gc.fillRect(o.x0 * S, o.y0 * S, (o.x1 - o.x0) * S, (o.y1 - o.y0) * S);
        }

        // Doors (locked): blue sprite filling the doorway
        for (Door d : phys.doors) {
            double sx = d.aabb().x0 * S, sy = d.aabb().y0 * S;
            gc.setFill(Color.web("#4A90D9"));
            gc.fillRect(sx, sy, (d.aabb().x1 - d.aabb().x0) * S, (d.aabb().y1 - d.aabb().y0) * S);
            gc.setStroke(Color.web("#1A1A1A"));
            gc.setLineWidth(2);
            gc.strokeRect(sx, sy, (d.aabb().x1 - d.aabb().x0) * S, (d.aabb().y1 - d.aabb().y0) * S);
        }

        // Pickups: coins (gold circles), keys, hearts
        for (Pickup p : phys.pickups) {
            if (!p.active) continue;
            if (p.type == Pickup.Type.COIN) {
                gc.setFill(Color.web("#FFD700"));
                gc.fillOval((p.x - 8) * S, (p.y - 8) * S, 16 * S, 16 * S);
                gc.setStroke(Color.web("#B8860B"));
                gc.setLineWidth(1.5);
                gc.strokeOval((p.x - 8) * S, (p.y - 8) * S, 16 * S, 16 * S);
            } else if (p.type == Pickup.Type.KEY) {
                gc.drawImage(Sprites.key2x, (p.x - 8) * S, (p.y - 8) * S, 16 * S, 16 * S);
            } else {
                gc.drawImage(Sprites.heart2x, (p.x - 8) * S, (p.y - 8) * S, 16 * S, 16 * S);
            }
        }

        // Enemies: monkey sprites, facing their patrol/chase direction
        for (Enemy e : phys.enemies) {
            if (e.dead) continue;
            double eh = 28 * S;
            double ew = eh;  // square body
            gc.drawImage(e.dir < 0 ? Sprites.enemyL2x : Sprites.enemy2x,
                    (e.body.x - 14) * S, (e.body.y - 14) * S, ew, eh);
        }

        // player (facing-aware; sprite drawn at its OWN aspect, centered
        // on the body — the body box is square 32x32 but the sprite grid
        // is 14x22; stretching to the box made it look like a pencil)
        double ph = player.hh * 2 * S;                 // physical height = body height
        double pw = ph * (Sprite.BANANA[0].length() / (double) Sprite.BANANA.length);  // sprite aspect
        gc.drawImage(facing < 0 ? Sprites.bananaL2x : Sprites.banana2x,
                player.x * S - pw / 2, player.y * S - ph / 2, pw, ph);

        // HUD: room id + coins + keys + HP
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Arial", 24));
        gc.fillText("Room " + screens.currentRoomId(), CANVAS_W - 160, 40);
        gc.fillText("Coins: " + inventory.coins, 20, 40);
        gc.fillText("Keys: " + inventory.keys, 20, 72);
        gc.fillText("HP: " + (int) combat.playerHP, 20, 104);
    }

    void drawGround(Physics.AABB t) {
        // TOP-DOWN: walls/blocks in theme block color, top-edge highlight
        // for depth. No grass tops (side-view concept).
        gc.setFill(Color.web(currentTheme.blockColor));
        gc.fillRect(t.x0 * S, t.y0 * S, (t.x1 - t.x0) * S, (t.y1 - t.y0) * S);
        gc.setFill(Color.web(currentTheme.blockColor).brighter());
        gc.fillRect(t.x0 * S, t.y0 * S, (t.x1 - t.x0) * S, 4 * S);
    }

    @Override
    public void handleKey(javafx.scene.input.KeyEvent e) {
        if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.A) left = true;
        else if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.D) right = true;
        else if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.W) up = true;
        else if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.S) down = true;
        else if (e.getCode() == KeyCode.ESCAPE) manager.replace(new MainMenu(manager));  // RoomsScreen replaced the menu on entry — pop would empty the stack (white screen, playtest bug)
    }

    // keyReleased is wired at the scene level (Main calls the top screen
    // directly) — not a Screen override.
    public void handleKeyReleased(javafx.scene.input.KeyEvent e) {
        if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.A) left = false;
        else if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.D) right = false;
        else if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.W) up = false;
        else if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.S) down = false;
    }
}
