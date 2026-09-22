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

        // 2x render scale: window up to 1600x1200, but NEVER taller than
        // the user's screen (playtest: window was "set up higher than my
        // screen and i cant resize"). Fit to the screen's work area,
        // keep the 4:3 aspect, and allow resizing — the menus are
        // layout-driven now (StackPane container resizes children), so
        // any window size works.
        javafx.geometry.Rectangle2D workArea =
                javafx.stage.Screen.getPrimary().getVisualBounds();
        double winW = Math.min(1600, workArea.getWidth());
        double winH = Math.min(1200, workArea.getHeight());
        // keep 4:3 within the fitted bounds
        if (winH / winW > 1200.0 / 1600.0) winH = winW * 1200.0 / 1600.0;

        Scene scene = new Scene(root, winW, winH, Color.BLACK);
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
        stage.setResizable(true);
        stage.setMinWidth(640);
        stage.setMinHeight(480);
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
