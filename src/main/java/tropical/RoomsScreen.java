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
        world = new RoomWorld(3, 3, 2000L + levelNum);
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
        // load the start room's geometry
        Room start = world.grid[world.rows / 2][0];
        phys.tiles.addAll(start.getTiles());
        phys.oneways.addAll(start.getOneways());

        canvas = new Canvas(CANVAS_W, CANVAS_H);
        gc = canvas.getGraphicsContext2D();
        root = new javafx.scene.layout.StackPane(canvas);
        root.getStyleClass().add("screen-bg");
    }

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
        phys.update(dt);
        screens.update(dt, phys);  // edge-crossing detection + transitions

        // out of the world safety net (shouldn't happen — rooms are walled)
        if (player.y > RoomWorld.ROOM_H + 200 || player.y < -200) {
            player.x = RoomWorld.ROOM_W / 2;
            player.y = RoomWorld.ROOM_H / 2;
            player.vx = 0; player.vy = 0;
        }
    }

    void render() {
        // All drawing in physical pixels: room coords * S.
        // sky
        gc.setFill(Color.web("#87CEEB"));
        gc.fillRect(0, 0, CANVAS_W, CANVAS_H);
        // room tiles (fixed camera: world coords = screen coords, scaled)
        for (Physics.AABB t : phys.tiles) drawGround(t);
        for (Physics.AABB o : phys.oneways) {
            gc.setFill(Color.web("#DEB887"));
            gc.fillRect(o.x0 * S, o.y0 * S, (o.x1 - o.x0) * S, (o.y1 - o.y0) * S);
        }
        // player (facing-aware; sprite drawn at its OWN aspect, centered
        // on the body — the body box is square 32x32 but the sprite grid
        // is 14x22; stretching to the box made it look like a pencil)
        double ph = player.hh * 2 * S;                 // physical height = body height
        double pw = ph * (Sprite.BANANA[0].length() / (double) Sprite.BANANA.length);  // sprite aspect
        gc.drawImage(facing < 0 ? Sprites.bananaL2x : Sprites.banana2x,
                player.x * S - pw / 2, player.y * S - ph / 2, pw, ph);
        // HUD: current room id
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Arial", 24));
        gc.fillText("Room " + screens.currentRoomId(), CANVAS_W - 160, 40);
    }

    void drawGround(Physics.AABB t) {
        // TOP-DOWN: walls/blocks are stone, no grass tops (side-view
        // concept). Slight highlight on the top edge for depth.
        gc.setFill(Color.web("#787878"));
        gc.fillRect(t.x0 * S, t.y0 * S, (t.x1 - t.x0) * S, (t.y1 - t.y0) * S);
        gc.setFill(Color.web("#A8A8A8"));
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
