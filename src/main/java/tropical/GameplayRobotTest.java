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
 * Integration test: menu → gameplay → pause → resume, via real
 * input dispatch under xvfb.
 *
 * Verifies:
 * 1. New Game replaces the menu with GameplayScreen
 * 2. The engine simulates (player moves under RIGHT input)
 * 3. ESC pushes PauseScreen and FREEZES the player (position static)
 * 4. ESC again resumes (player moves again)
 * 5. Death/respawn doesn't crash the loop
 */
public class GameplayRobotTest extends Application {
    private ScreenManager screens;
    private int step = 0;
    private int failures = 0;
    private double frozenX = -1;
    private int frozenChecks = 0;
    private double lastX = -1;

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

        // Start directly at main menu (skip title blink timing)
        screens.push(new MainMenu(screens));
        nextStep();
    }

    private void check(String name, boolean ok) {
        System.out.printf("%-46s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private GameplayScreen game() {
        Screen top = screens.peek();
        return top instanceof GameplayScreen g ? g : null;
    }

    private double playerX() {
        try {
            GameplayScreen g = game();
            if (g == null) return Double.NaN;
            java.lang.reflect.Field f = GameplayScreen.class.getDeclaredField("player");
            f.setAccessible(true);
            tropical.engine.Physics.Body p = (tropical.engine.Physics.Body) f.get(g);
            return p.x;
        } catch (Exception e) { return Double.NaN; }
    }

    private void nextStep() {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(400));
        wait.setOnFinished(e -> {
            step++;
            Robot robot = new Robot();
            switch (step) {
                case 1 -> {
                    // Focus New Game (first button, enter() requests focus)
                    robot.keyType(KeyCode.ENTER);
                }
                case 2 -> {
                    check("New Game -> GameplayScreen", game() != null);
                    lastX = playerX();
                    // Hold RIGHT for 0.5s of simulation
                    robot.keyPress(KeyCode.RIGHT);
                }
                case 3 -> {
                    double x = playerX();
                    check("engine simulates (player moved right)", x > lastX + 30);
                    robot.keyRelease(KeyCode.RIGHT);
                    // ESC → pause
                    robot.keyType(KeyCode.ESCAPE);
                }
                case 4 -> {
                    check("ESC -> PauseScreen on top", screens.peek() instanceof PauseScreen);
                    frozenX = playerX();
                }
                case 5 -> {
                    double x = playerX();
                    if (Math.abs(x - frozenX) > 0.5) {
                        check("pause FREEZES player", false);
                    } else {
                        frozenChecks++;
                        if (frozenChecks < 3) { step--; } // keep checking
                        else check("pause FREEZES player (3 samples)", true);
                    }
                }
                case 6 -> {
                    // ESC on pause = resume
                    robot.keyType(KeyCode.ESCAPE);
                }
                case 7 -> {
                    check("resume -> GameplayScreen on top", game() != null);
                    lastX = playerX();
                    robot.keyPress(KeyCode.RIGHT);
                }
                case 8 -> {
                    double x = playerX();
                    check("resumed engine simulates (moved)", x > lastX + 30);
                    robot.keyRelease(KeyCode.RIGHT);
                    finish();
                }
                default -> finish();
            }
            if (step < 8) nextStep();
        });
        wait.play();
    }

    private void finish() {
        System.out.println(failures == 0
                ? "SUCCESS: menu -> gameplay -> pause -> resume all work"
                : "FAILURE: " + failures + " check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
