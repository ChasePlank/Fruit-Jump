package tropical;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Tropical Punch - a 2D platformer
 *
 * Entry point. JavaFX lifecycle:
 * - init() runs before the window appears (load resources here)
 * - start(Stage) is the main entry — build the scene and show
 * - stop() runs on close (save state, cleanup)
 */
public class Main extends Application {
    private ScreenManager screens;

    @Override
    public void init() {
        // Preload resources if needed
    }

    @Override
    public void start(Stage stage) {
        screens = new ScreenManager();

        StackPane root = new StackPane();
        root.getChildren().add(screens.getContainer());

        // 2x render scale: 1600x1200 window, engine logic at 800x600.
        // (Canvas is 1600x1200; all gameplay screens scale up.)
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        // Keyboard: menus and gameplay share the scene key handlers.
        // Gameplay needs KEY_RELEASED for held-key movement. The release
        // must route to ANY screen with held-key input — routing only to
        // GameplayScreen meant releasing a key during a RoomsScreen room
        // transition left RoomsScreen's `left` stuck true: the player
        // auto-walked into the wall with no way to counter (playtest bug).
        scene.setOnKeyPressed(e -> screens.handleKey(e));
        scene.setOnKeyReleased(e -> {
            Screen top = screens.peek();
            if (top instanceof GameplayScreen g) g.handleKeyReleased(e);
            else if (top instanceof RoomsScreen r) r.handleKeyReleased(e);
        });

        stage.setTitle("Tropical Punch");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        // Start at title screen
        screens.push(new TitleScreen(screens));
    }

    @Override
    public void stop() {
        // Cleanup
    }

    public static void main(String[] args) {
        launch(args);
    }
}
