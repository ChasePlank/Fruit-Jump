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
    static final int VIEW_W = 800, VIEW_H = 480;

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
    boolean left, right;

    public RoomsScreen(ScreenManager manager, int levelNum) {
        super(manager);
        world = new RoomWorld(3, 3, 2000L + levelNum);
        phys = new World();
        combat = new Combat();
        inventory = new PlayerInventory();

        // player starts center-bottom of the start room
        player = new Physics.Body(RoomWorld.ROOM_W / 2 - 12, RoomWorld.ROOM_H - 120, 24, 44);
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

        canvas = new Canvas(VIEW_W, VIEW_H);
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
        player.vx = left ? -220 : right ? 220 : 0;
        phys.update(dt);
        screens.update(dt, phys);  // edge-crossing detection + transitions

        // fell out of the world (shouldn't happen — rooms are walled,
        // but a pit in the bottom wall could leak): reset to room center
        if (player.y > RoomWorld.ROOM_H + 200) {
            player.x = RoomWorld.ROOM_W / 2;
            player.y = RoomWorld.ROOM_H / 2;
            player.vx = 0; player.vy = 0;
        }
    }

    void render() {
        // sky
        gc.setFill(Color.web("#87CEEB"));
        gc.fillRect(0, 0, VIEW_W, VIEW_H);
        // room tiles (fixed camera: world coords = screen coords)
        // (current room tiles already in phys.tiles; draw those)
        for (Physics.AABB t : phys.tiles) drawGround(t);
        for (Physics.AABB o : phys.oneways) {
            gc.setFill(Color.web("#DEB887"));
            gc.fillRect(o.x0, o.y0, o.x1 - o.x0, o.y1 - o.y0);
        }
        // player
        gc.drawImage(Sprites.banana, player.x - player.hw, player.y - player.hh);
        // HUD: current room id
        gc.setFill(Color.WHITE);
        gc.fillText("Room " + screens.currentRoomId(), VIEW_W - 100, 20);
    }

    void drawGround(Physics.AABB t) {
        gc.setFill(Color.web("#8B5A2B"));
        gc.fillRect(t.x0, t.y0, t.x1 - t.x0, t.y1 - t.y0);
        boolean above = false;
        for (Physics.AABB o : phys.tiles) if (o.x0 == t.x0 && o.y1 == t.y0) { above = true; break; }
        if (!above) {
            gc.setFill(Color.web("#228B22"));
            gc.fillRect(t.x0, t.y0, t.x1 - t.x0, Math.min(8, t.y1 - t.y0));
        }
    }

    @Override
    public void handleKey(javafx.scene.input.KeyEvent e) {
        if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.A) left = true;
        else if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.D) right = true;
        else if (e.getCode() == KeyCode.SPACE) { if (player.grounded) player.vy = -420; }
        else if (e.getCode() == KeyCode.ESCAPE) manager.pop();  // back to menu (RoomsScreen is standalone for now)
    }

    // keyReleased is wired at the scene level (Main calls the top screen
    // directly) — not a Screen override.
    public void handleKeyReleased(javafx.scene.input.KeyEvent e) {
        if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.A) left = false;
        else if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.D) right = false;
    }
}
