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
 * Save/continue integration test:
 * 1. New Game → force the player to the exit (teleport via reflection)
 *    → engine autosteps → autosave file written with levelNum=2
 * 2. Verify the file's contents
 * 3. Quit to menu, Continue → GameplayScreen loads level 2 from the save
 */
public class SaveLoadTest extends Application {
    private ScreenManager screens;
    private int step = 0;
    private int failures = 0;
    private final String savePath = System.getProperty("user.home")
            + "/.tropical-punch-autosave.txt";

    @Override
    public void start(Stage stage) {
        // clear any stale save
        new java.io.File(savePath).delete();

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
        System.out.printf("%-52s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private GameplayScreen game() {
        Screen top = screens.peek();
        return top instanceof GameplayScreen g ? g : null;
    }

    private Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private void nextStep() {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(400));
        wait.setOnFinished(e -> {
            step++;
            Robot robot = new Robot();
            try {
                switch (step) {
                    case 1 -> {
                        robot.keyType(KeyCode.ENTER);  // New Game
                    }
                    case 2 -> {
                        check("in gameplay (level 1)", game() != null);
                        // teleport player to the exit: next engine step
                        // triggers the transition + autosave
                        GameplayScreen g = game();
                        var player = (tropical.engine.Physics.Body) field(g, "player");
                        var map = (tropical.engine.LevelMap) field(g, "map");
                        player.x = map.exitX;
                        player.y = map.exitY;
                    }
                    case 3 -> {
                        // should have transitioned to level 2
                        GameplayScreen g = game();
                        check("transitioned to level 2", g != null && (int) field(g, "levelNum") == 2);
                        // check the save file
                        java.io.File save = new java.io.File(savePath);
                        check("autosave file exists", save.exists());
                        if (save.exists()) {
                            String content = new String(java.nio.file.Files.readAllBytes(save.toPath()));
                            check("save has level=2", content.contains("level=2"));
                        }
                        // back to menu via pause → quit
                        robot.keyType(KeyCode.ESCAPE);
                    }
                    case 4 -> {
                        check("paused", screens.peek() instanceof PauseScreen);
                        // navigate to Quit to Menu (2nd button)
                        robot.keyType(KeyCode.DOWN);
                    }
                    case 5 -> {
                        robot.keyType(KeyCode.ENTER);
                    }
                    case 6 -> {
                        check("back at main menu", screens.peek() instanceof MainMenu);
                        // navigate to Continue (2nd button)
                        robot.keyType(KeyCode.DOWN);
                    }
                    case 7 -> {
                        robot.keyType(KeyCode.ENTER);
                    }
                    case 8 -> {
                        GameplayScreen g = game();
                        check("Continue → gameplay", g != null);
                        check("Continue loaded level 2", g != null && (int) field(g, "levelNum") == 2);
                        finish();
                    }
                    default -> finish();
                }
            } catch (Exception ex) {
                check("step " + step + " exception: " + ex, false);
                finish();
                return;
            }
            if (step < 8) nextStep();
        });
        wait.play();
    }

    private void finish() {
        System.out.println(failures == 0
                ? "SUCCESS: autosave → continue loop works"
                : "FAILURE: " + failures + " check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) { launch(args); }
}
