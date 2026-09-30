package tropical;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;

/**
 * ShotWater - run Rooms mode (top-down): the fog of war and the sight-gated enemies and
 * capture a frame once the player has walked into view of it.
 *
 * This exists because the game-side water wiring is the one part of the water
 * system that no unit test can cover: it compiles, and every line of its logic is
 * exercised headlessly, but "does water appear on screen" needs a screen. Level 3
 * is chosen from a scan of which seeds produce water near the start.
 */
public class ShotRooms extends Application {
    @Override
    public void start(Stage stage) {
        ScreenManager screens = new ScreenManager();
        StackPane root = new StackPane();
        root.getChildren().add(screens.getContainer());
        Scene scene = new Scene(root, 1600, 1200, Color.BLACK);   // the real window size
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        scene.setOnKeyPressed(e -> screens.handleKey(e));
        scene.setOnKeyReleased(e -> {
            Screen top = screens.peek();
            if (top instanceof GameplayScreen g) g.handleKeyReleased(e);
        });
        stage.setScene(scene);
        stage.show();

        // level and output path from system properties, so one tool captures any
        // feature: -Dlevel=N -Dshot=/root/downloads/rooms-shot.png
        final String shotPath = System.getProperty("shot", "/root/downloads/rooms-shot.png");
        screens.push(new RoomsScreen(screens, 1));
        Robot robot = new Robot();

        // no walking: the room itself is the subject
        
        after(1200, () -> {
            var img = scene.snapshot(null);
            try {
                writePng(img, shotPath);
                System.out.println("PASS: wrote " + shotPath + " ("
                    + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
            } catch (Exception ex) {
                System.out.println("FAIL: " + ex);
            }
            Platform.exit();
        });
        after(1600, () -> System.exit(0));
    }

    static void after(int ms, Runnable r) {
        var t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(ms));
        t.setOnFinished(e -> r.run());
        t.play();
    }

    static void writePng(javafx.scene.image.WritableImage img, String path) throws Exception {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var pr = img.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
        }
        javax.imageio.ImageIO.write(bi, "png", new java.io.File(path));
    }
}
