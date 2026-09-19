package tropical;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Headless smoke test for the menu system (run under xvfb).
 *
 * Drives the screen stack with synthetic key events and asserts
 * each transition by checking which screen class is on top.
 * Exits with code 0 on success, 1 on failure — no JUnit needed,
 * same pattern as the platformer test suite.
 */
public class MenuSmokeTest extends Application {
    private ScreenManager screens;
    private int failures = 0;
    private int step = 0;

    @Override
    public void start(Stage stage) {
        screens = new ScreenManager();
        StackPane root = new StackPane();
        root.getChildren().add(screens.getContainer());
        Scene scene = new Scene(root, 800, 600, Color.BLACK);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        scene.setOnKeyPressed(e -> screens.handleKey(e));
        stage.setScene(scene);
        stage.show();

        screens.push(new TitleScreen(screens));
        nextStep();
    }

    private void check(String name, boolean ok) {
        System.out.printf("%-40s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private void key(KeyCode code) {
        // Route through the scene's handler like real input
        screens.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
    }

    private void nextStep() {
        // Each step runs after a short delay so the FX thread can settle
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(200));
        wait.setOnFinished(e -> {
            step++;
            switch (step) {
                case 1 -> {
                    check("starts on TitleScreen", topIs(TitleScreen.class));
                    key(KeyCode.ENTER); // title -> main menu (replace)
                }
                case 2 -> {
                    check("ENTER: title -> MainMenu", topIs(MainMenu.class));
                    key(KeyCode.DOWN); // focus Continue
                    key(KeyCode.DOWN); // focus Settings
                    key(KeyCode.ENTER); // push settings
                }
                case 3 -> {
                    check("menu nav: push SettingsScreen", topIs(SettingsScreen.class));
                    key(KeyCode.ESCAPE); // pop settings
                }
                case 4 -> {
                    check("ESC: settings -> back to MainMenu", topIs(MainMenu.class));
                    key(KeyCode.UP); // back to Continue
                    key(KeyCode.UP); // back to New Game
                    key(KeyCode.ENTER); // New Game -> replaces with GameplayScreen
                }
                case 5 -> {
                    check("New Game -> GameplayScreen", topIs(GameplayScreen.class));
                    key(KeyCode.ESCAPE); // gameplay -> push PauseScreen
                }
                case 6 -> {
                    check("ESC in game -> PauseScreen", topIs(PauseScreen.class));
                    key(KeyCode.ESCAPE); // pause -> resume gameplay
                }
                case 7 -> {
                    check("ESC pause -> back to GameplayScreen", topIs(GameplayScreen.class));
                    finish();
                }
                default -> finish();
            }
            if (step < 7) nextStep();
        });
        wait.play();
    }

    private boolean topIs(Class<?> c) {
        // No public peek() on ScreenManager — infer from the container's
        // styleable root. Simpler: reflect the stack.
        try {
            java.lang.reflect.Field f = ScreenManager.class.getDeclaredField("stack");
            f.setAccessible(true);
            java.util.ArrayDeque<?> stack = (java.util.ArrayDeque<?>) f.get(screens);
            return !stack.isEmpty() && c.isInstance(stack.peek());
        } catch (Exception e) {
            return false;
        }
    }

    private void finish() {
        System.out.println(failures == 0
                ? "SUCCESS: menu navigation works"
                : "FAILURE: " + failures + " menu check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
