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
 * Death, and the two doors out of it: ENTER retries the level you died on, ESC goes to the menu.
 *
 * <p><b>NAMED BY NO TEST IN EITHER EDITION BEFORE THIS.</b> `GameOverScreen` is constructed from exactly one place -
 * `GameplayScreen`'s death branch - and nothing had ever driven it. It is the screen a platformer shows MOST, and
 * its behaviour is the fix for a reported bug: dying used to reset the health bar and there was no way back to the
 * level you were on.
 *
 * <p><b>AND IT CANNOT BE CHECKED HEADLESS.</b> The decision lives in `handleKey`, which needs a `ScreenManager` and
 * a display - the same reason the engine's checkpoint bug hid behind a screen until the six statements were
 * extracted. This edition's answer to screen behaviour is a Robot test, so this is one: it dies on a specific
 * level, and then asks both doors where they go.
 *
 * <p>THE LEVEL IS DELIBERATELY NOT 1. A retry that restarts the run from level 1 is the bug this screen exists to
 * prevent, and a test that dies on level 1 cannot tell the difference.
 */
public class GameOverRobotTest extends Application {
    private ScreenManager screens;
    private int step = 0;
    private int failures = 0;
    private static final int DIED_ON = 7;

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

        // Straight into level 7 - no menu, so the only thing under test is the death path.
        screens.push(new GameplayScreen(screens, DIED_ON));
        nextStep();
    }

    private void check(String name, boolean ok) {
        System.out.printf("%-52s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private int levelOf(GameplayScreen g) {
        try {
            java.lang.reflect.Field f = GameplayScreen.class.getDeclaredField("levelNum");
            f.setAccessible(true);
            return (int) f.get(g);
        } catch (Exception e) { return -1; }
    }

    /** Kill the player the way the game does, so the screen's own death branch runs. */
    private void killThePlayer() {
        try {
            Screen top = screens.peek();
            java.lang.reflect.Field f = GameplayScreen.class.getDeclaredField("combat");
            f.setAccessible(true);
            tropical.engine.Combat c = (tropical.engine.Combat) f.get(top);
            c.playerHP = 0;
        } catch (Exception e) {
            check("the player could be killed for the test: " + e, false);
        }
    }

    private void nextStep() {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(400));
        wait.setOnFinished(e -> {
            step++;
            Robot robot = new Robot();
            switch (step) {
                case 1 -> {
                    check("the run starts on the level we chose (" + levelOf((GameplayScreen) screens.peek()) + ")",
                            levelOf((GameplayScreen) screens.peek()) == DIED_ON);
                    killThePlayer();
                }
                case 2 -> {
                    check("losing the last heart reaches GAME OVER", screens.peek() instanceof GameOverScreen);
                    robot.keyType(KeyCode.ENTER);
                }
                case 3 -> {
                    Screen top = screens.peek();
                    check("ENTER retries into gameplay", top instanceof GameplayScreen);
                    int lvl = top instanceof GameplayScreen g ? levelOf(g) : -1;
                    check("AND RETRIES THE LEVEL YOU DIED ON, not level 1 (" + lvl + ")", lvl == DIED_ON);
                    killThePlayer();
                }
                case 4 -> {
                    check("dying again reaches GAME OVER again", screens.peek() instanceof GameOverScreen);
                    robot.keyType(KeyCode.ESCAPE);
                }
                case 5 -> {
                    check("ESC leaves for the main menu, and does not leave a blank screen",
                            screens.peek() instanceof MainMenu);
                    finish();
                }
                default -> finish();
            }
            if (step < 5) nextStep();
        });
        wait.play();
    }

    private void finish() {
        System.out.println(failures == 0
                ? "SUCCESS: death offers both doors, and the retry is the level you died on"
                : "FAILURE: " + failures + " check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
