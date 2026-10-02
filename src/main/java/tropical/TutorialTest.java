package tropical;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The Tutorial menu entry actually opens the tutorial.
 *
 * <p>It is wired - menu entry, hand-built levels instead of the generator, no autosave, ends at 8 back to the
 * menu - and NOTHING TESTED IT. The menu test checks the label is in the list and nothing more, so the wiring
 * could be removed and every suite would still pass. That is the same shape as the tutorial being unreachable
 * in the first place: present, and no one checking it could be reached.
 *
 * <p>Three things, and the third is the one that would have caught the bug that made every tutorial level end
 * in GAME OVER on its first frame: the level is HAND-BUILT, so it is 20 rows, where a generated one is 14. A
 * screen that still hardcoded 14 anywhere would fail this.
 */
public class TutorialTest extends Application {
    private ScreenManager screens;
    private int step = 0;
    private int failures = 0;

    private void check(String name, boolean ok) {
        System.out.printf("%-52s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    @Override public void start(Stage stage) {
        screens = new ScreenManager();
        StackPane root = new StackPane();
        root.getChildren().add(screens.getContainer());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
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
                        check("the menu offers Tutorial", menu.indexOf("Tutorial") >= 0);
                        int to = menu.indexOf("Tutorial");
                        for (int i = 0; i < to; i++) screens.handleKey(key(KeyCode.DOWN));
                        screens.handleKey(key(KeyCode.ENTER));
                    }
                    case 1 -> {
                        check("Tutorial opens a gameplay screen", screens.peek() instanceof GameplayScreen);
                        if (!(screens.peek() instanceof GameplayScreen g)) { finish(); return; }
                        check("and it is the tutorial, not a generated run", g.isTutorial());
                        check("starting at tutorial 1", g.levelNumber() == 1);
                        check("with a hand-built level: 20 rows, where a generated one is 14",
                                g.mapHeightCells() == Tutorial.H);
                        check("and the level has a floor the player is standing on",
                                g.playerIsSupported());
                    }
                    default -> { }
                }
            } catch (Exception ex) {
                check("step " + step + " exception: " + ex.getMessage(), false);
                finish();
                return;
            }
            step++;
            if (step > 1) finish(); else step();
        });
        wait.play();
    }

    private javafx.scene.input.KeyEvent key(KeyCode code) {
        return new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false);
    }

    private void finish() {
        System.out.println(failures == 0 ? "SUCCESS: the tutorial is reachable and is the tutorial"
                                         : "FAILURE: " + failures + " check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) { launch(args); }
}
