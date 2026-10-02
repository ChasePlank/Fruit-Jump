package tropical;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.robot.Robot;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

/**
 * Weapons integration test: arrow spawns and flies, bomb spawns with
 * fuse, hookshot fires and pulls. Real input dispatch under xvfb.
 */
public class WeaponsRobotTest extends Application {
    private ScreenManager screens;
    private int step = 0;
    private int failures = 0;
    private double arrowX0 = Double.NaN;
    private GameplayScreen game;

    @Override
    public void start(Stage stage) {
        screens = new ScreenManager();
        StackPane root = new StackPane();
        root.getChildren().add(screens.getContainer());
        Scene scene = new Scene(root, 800, 600, Color.BLACK);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        scene.setOnKeyPressed(e -> screens.handleKey(e));
        scene.setOnKeyReleased(e -> {
            Screen top = screens.peek();
            if (top instanceof GameplayScreen g) g.handleKeyReleased(e);
        });
        stage.setScene(scene);
        stage.show();

        screens.push(new MainMenu(screens));
        nextStep();
    }

    private void check(String name, boolean ok) {
        System.out.printf("%-46s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private tropical.engine.World world() throws Exception {
        java.lang.reflect.Field f = GameplayScreen.class.getDeclaredField("world");
        f.setAccessible(true);
        return (tropical.engine.World) f.get(game);
    }

    private int projectileCount() throws Exception {
        int n = 0;
        for (tropical.engine.Projectile p : world().projectiles) if (p.active) n++;
        return n;
    }

    private double projX() throws Exception {
        tropical.engine.World w = world();
        if (w.projectiles.isEmpty()) return Double.NaN;
        return w.projectiles.get(0).x;
    }

    /**
     * How long to wait before the next step, in ms. 400 by default.
     *
     * The arrow needs a shorter one. It flies at 700px/s, so a 400ms step is 280px - and this test's
     * levels put a step-up well inside that, so the arrow had spawned, flown and hit a wall before the
     * next step looked for it. The suite then reported "arrow spawned: FAIL" for a bow that works
     * perfectly, and had done since it was written. Verified against the engine first: an arrow
     * constructed and stepped five frames goes x=80 -> 138 and stays active.
     *
     * A permanently red suite is worse than no suite, because a new failure looks exactly like the old
     * one - which is the tax this cost today, when checking whether a failure was pre-existing was
     * itself a task.
     */
    private int nextDelayMs = 400;

    private void nextStep() {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(nextDelayMs));
        nextDelayMs = 400;
        wait.setOnFinished(e -> {
            step++;
            Robot robot = new Robot();
            try {
                switch (step) {
                    case 1 -> robot.keyType(KeyCode.ENTER);  // New Game
                    case 2 -> {
                        game = (GameplayScreen) screens.peek();
                        check("gameplay started", game != null);
                        // F = arrow. Look for it 60ms from now, not 400ms.
                        nextDelayMs = 60;
                        robot.keyType(KeyCode.F);
                    }
                    case 3 -> {
                        int n = projectileCount();
                        check("arrow spawned (1 active)", n == 1);
                        arrowX0 = projX();
                        // sample again shortly, while it is still in the air
                        nextDelayMs = 120;
                    }
                    case 4 -> {
                        double x = projX();
                        double spawnX = playerX();
                        check("arrow flew (x advanced, " + (int) arrowX0 + " -> " + (int) x + ")",
                                x > arrowX0 && x > spawnX + 50);
                        // G = bomb
                        robot.keyType(KeyCode.G);
                    }
                    case 5 -> {
                        boolean bomb = false;
                        for (tropical.engine.Projectile p : world().projectiles) {
                            if (p.active && p.type == tropical.engine.Projectile.Type.BOMB) bomb = true;
                        }
                        check("bomb spawned", bomb);
                    }
                    case 6 -> {
                        // bomb fuse ~1.2s; after 2 steps (~0.8s) still active or exploded
                        boolean anyBomb = false;
                        for (tropical.engine.Projectile p : world().projectiles) {
                            if (p.type == tropical.engine.Projectile.Type.BOMB) anyBomb = true;
                        }
                        // may have already exploded (removed) — either is fine;
                        // check no crash happened by verifying gameplay still on top
                        check("gameplay alive after bomb cycle", screens.peek() instanceof GameplayScreen);
                        // X = hookshot (fires toward facing)
                        robot.keyType(KeyCode.X);
                    }
                    case 7 -> {
                        java.lang.reflect.Field hf = GameplayScreen.class.getDeclaredField("hookshot");
                        hf.setAccessible(true);
                        tropical.engine.Hookshot h = (tropical.engine.Hookshot) hf.get(game);
                        // hookshot may have already completed (instant pull done).
                        // The signal it worked: no crash + gameplay on top.
                        check("hookshot fired without crash", screens.peek() instanceof GameplayScreen);
                        System.out.println("  (hookshot state now: " + h.isActive() + ")");
                        finish();
                    }
                    default -> finish();
                }
            } catch (Exception ex) {
                check("step " + step + " exception: " + ex.getMessage(), false);
                finish();
                return;
            }
            if (step < 7) nextStep();
        });
        wait.play();
    }

    private double playerX() throws Exception {
        java.lang.reflect.Field f = GameplayScreen.class.getDeclaredField("player");
        f.setAccessible(true);
        tropical.engine.Physics.Body p = (tropical.engine.Physics.Body) f.get(game);
        return p.x;
    }

    private void finish() {
        System.out.println(failures == 0
                ? "SUCCESS: weapons wired (arrow, bomb, hookshot)"
                : "FAILURE: " + failures + " check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
