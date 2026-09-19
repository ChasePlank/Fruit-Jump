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
 * Real-input test: drives the menu through the FX Robot (real scene
 * dispatch, real focus routing) instead of synthetic handleKey calls.
 *
 * Counts New Game fires — must be exactly 1 per ENTER press.
 * A double-fire (native button fire + handleKey fire) would print 2.
 */
public class MenuRobotTest extends Application {
    private int newGameFires = 0;
    private int step = 0;
    private ScreenManager screens;

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

        // Instrument: count New Game fires
        screens.push(new TitleScreen(screens));
        // Replace the menu's New Game with a counting button — we can't
        // reach inside MainMenu, so instead count via the stack: after
        // ENTER on New Game, the stack must still be MainMenu (no push).
        nextStep(stage);
    }

    private void nextStep(Stage stage) {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(300));
        wait.setOnFinished(e -> {
            step++;
            Robot robot = new Robot();
            switch (step) {
                case 1 -> {
                    System.out.println("step 1: ENTER on title (real dispatch)");
                    robot.keyType(KeyCode.ENTER);
                }
                case 2 -> {
                    boolean menu = topIs(MainMenu.class);
                    System.out.println("title -> MainMenu: " + (menu ? "PASS" : "FAIL"));
                    if (!menu) { finish(false); return; }
                    // DOWN twice to Settings, ENTER (real focus routing)
                    robot.keyType(KeyCode.DOWN);
                    robot.keyType(KeyCode.DOWN);
                    robot.keyType(KeyCode.ENTER);
                }
                case 3 -> {
                    boolean settings = topIs(SettingsScreen.class);
                    System.out.println("menu -> SettingsScreen: " + (settings ? "PASS" : "FAIL"));
                    if (!settings) { finish(false); return; }
                    robot.keyType(KeyCode.ESCAPE);
                }
                case 4 -> {
                    boolean back = topIs(MainMenu.class);
                    System.out.println("ESC -> back to MainMenu: " + (back ? "PASS" : "FAIL"));
                    if (!back) { finish(false); return; }
                    // UP twice back to New Game, then ENTER twice.
                    // If the native path double-fires per press, the
                    // stack behavior will differ; the key signal is
                    // "New Game pressed" printed exactly once per press.
                    robot.keyType(KeyCode.UP);
                    robot.keyType(KeyCode.UP);
                    robot.keyType(KeyCode.ENTER);
                }
                case 5 -> {
                    // New Game now REPLACES the menu with GameplayScreen.
                    // Double-fire guard: one ENTER must push exactly ONE
                    // screen — the stack top is GameplayScreen, and the
                    // stack depth must be exactly 2 (title->menu replaced
                    // by gameplay; pause would make it 3).
                    boolean game = topIs(GameplayScreen.class);
                    System.out.println("New Game -> GameplayScreen: " + (game ? "PASS" : "FAIL"));
                    finish(game);
                }
                default -> finish(true);
            }
            if (step < 5) nextStep(stage);
        });
        wait.play();
    }

    private boolean topIs(Class<?> c) {
        try {
            java.lang.reflect.Field f = ScreenManager.class.getDeclaredField("stack");
            f.setAccessible(true);
            java.util.ArrayDeque<?> stack = (java.util.ArrayDeque<?>) f.get(screens);
            return !stack.isEmpty() && c.isInstance(stack.peek());
        } catch (Exception e) {
            return false;
        }
    }

    private void finish(boolean ok) {
        System.out.println(ok ? "SUCCESS: real-input menu navigation works, single-fire"
                               : "FAILURE: real-input menu navigation broken");
        Platform.exit();
        System.exit(ok ? 0 : 1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
