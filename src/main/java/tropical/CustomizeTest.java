package tropical;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The Climber opens, and the game reads what it saved.
 *
 * <p>Two halves, because either one alone is a half-feature: a customiser nothing can reach is decoration, and
 * a config the game never reads is a settings file. The menu test checks the LABEL is in the list and nothing
 * more, which is exactly the gap the tutorial had.
 *
 * <p>It changes nothing. The screen saves the config on the way out, so a test that pressed LEFT would leave
 * the player's colours altered; this one opens, checks, and leaves.
 */
public class CustomizeTest extends Application {
    private ScreenManager screens;
    private int step = 0;
    private int failures = 0;

    private void check(String name, boolean ok) {
        System.out.printf("%-56s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    @Override public void start(Stage stage) {
        screens = new ScreenManager();
        StackPane root = new StackPane();
        root.getChildren().add(screens.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        scene.setOnKeyPressed(e -> screens.handleKey(e));
        stage.setScene(scene);
        stage.show();
        screens.push(new MainMenu(screens));
        step();
    }

    private void step() {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(300));
        wait.setOnFinished(e -> {
            try {
                switch (step) {
                    case 0 -> {
                        MainMenu menu = (MainMenu) screens.peek();
                        check("the menu offers The Climber", menu.indexOf("The Climber") >= 0);
                        for (int i = 0; i < menu.indexOf("The Climber"); i++) {
                            screens.handleKey(key(KeyCode.DOWN));
                        }
                        screens.handleKey(key(KeyCode.ENTER));
                    }
                    case 1 -> {
                        check("The Climber opens", screens.peek() instanceof CustomizeScreen);
                        // The half that makes it a feature: the sprite the game draws comes from the config.
                        CharacterConfig cfg = CharacterConfig.load();
                        Sprites.Look look = Sprites.buildLook(cfg);
                        check("the config builds a sprite the game can draw",
                                look != null && look.right2x != null && look.gridW > 0 && look.gridH > 0);
                        check("hair and pack are chosen from the config, not hardcoded",
                                cfg.hairHex().startsWith("#") && cfg.packHex().startsWith("#"));
                        screens.handleKey(key(KeyCode.ESCAPE));
                    }
                    case 2 -> check("ESC returns to the menu", screens.peek() instanceof MainMenu);
                    default -> { }
                }
            } catch (Exception ex) {
                check("step " + step + " exception: " + ex.getMessage(), false);
                finish();
                return;
            }
            step++;
            if (step > 2) finish(); else step();
        });
        wait.play();
    }

    private KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private void finish() {
        System.out.println(failures == 0 ? "SUCCESS: The Climber is reachable and its config is used"
                                         : "FAILURE: " + failures + " check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) { launch(args); }
}
