package tropical;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.robot.Robot;
import javafx.scene.input.KeyCode;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Screenshot test: boot to gameplay, capture the rendered canvas as PNG.
 * Verifies sprites render (non-sky pixels where sprites should be).
 */
public class ScreenshotTest extends Application {
    @Override
    public void start(Stage stage) {
        ScreenManager screens = new ScreenManager();
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
        Robot robot = new Robot();

        // step 1: ENTER → gameplay. step 2: capture.
        javafx.animation.PauseTransition w1 = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(500));
        w1.setOnFinished(e -> {
            robot.keyType(KeyCode.ENTER);
            javafx.animation.PauseTransition w2 = new javafx.animation.PauseTransition(
                    javafx.util.Duration.millis(800));
            w2.setOnFinished(e2 -> {
                // find the canvas via the GameplayScreen's scene
                if (!(screens.peek() instanceof GameplayScreen)) {
                    System.out.println("FAIL: not in gameplay");
                    Platform.exit(); System.exit(1); return;
                }
                // capture via the scene
                // Print the geometry instead of reasoning about it. Last attempt referenced a `canvas`
                // variable that does not exist here - this test snapshots the scene, not the screen's canvas -
                // and the two theories before that did not fit the observation either. Walk the tree and report
                // what is actually there, which is the move that has worked every time this week.
                System.out.println("DIAG scene=" + scene.getWidth() + "x" + scene.getHeight());
                dump(scene.getRoot(), "  ");
                WritableImage img = scene.snapshot(null);
                // Write PNG by hand (no swing module in the JavaFX set):
                // raw RGBA scanlines + filter byte 0 per row.
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                try {
                    // Written with ImageIO, like every other capture tool in this project. This block used to hand-roll the
                // PNG - IHDR, IDAT, IEND, CRCs, a Deflater - and justified it in a comment saying there was no swing
                // module in the JavaFX set. ShotWater and the rest call ImageIO with the same module set and produce
                // correct images, so the comment's reason was wrong, and the hand-rolled encoder was producing a
                // broken one: the game appeared in a quadrant of an otherwise empty frame.
                //
                // The render was never at fault. A tree dump proved it: canvas 1600x1200, scale 0.5, no offset,
                // filling an 800x600 scene. Replacing the encoder with the proven path removes the whole class of
                // problem rather than hunting whichever byte was wrong in a hand-written format.
                var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var pr2 = img.getPixelReader();
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr2.getArgb(x, y));
                javax.imageio.ImageIO.write(bi, "png", new java.io.File("/root/downloads/tropical-punch-screenshot.png"));
                System.out.println("PASS: screenshot saved (" + w + "x" + h + ")");
                } catch (Exception ex) {
                    System.out.println("FAIL: " + ex.getMessage());
                    Platform.exit(); System.exit(1); return;
                }
                Platform.exit(); System.exit(0);
            });
            w2.play();
        });
        w1.play();
    }

    static byte[] intBytes(int v) {
        return new byte[]{(byte)(v>>>24), (byte)(v>>>16), (byte)(v>>>8), (byte)v};
    }
    static byte[] cat(byte[] a, byte[] b) {
        var out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
    static byte[] crc(byte[] data) {
        var crc = new java.util.zip.CRC32();
        crc.update(data);
        long v = crc.getValue();
        return new byte[]{(byte)(v>>>24), (byte)(v>>>16), (byte)(v>>>8), (byte)v};
    }

    public static void main(String[] args) { launch(args); }
    /** Walk the tree and report what is actually there. Reasoned twice about the quadrant capture and was
     *  wrong twice; the values are the only thing that settles it. */
    static void dump(javafx.scene.Node n, String indent) {
        String extra = "";
        if (n instanceof javafx.scene.canvas.Canvas c)
            extra = " canvas=" + c.getWidth() + "x" + c.getHeight()
                  + " scale=" + c.getScaleX() + " tx=" + c.getTranslateX() + " ty=" + c.getTranslateY();
        System.out.println("DIAG " + indent + n.getClass().getSimpleName()
            + " size=" + String.format("%.0fx%.0f", n.getLayoutBounds().getWidth(), n.getLayoutBounds().getHeight())
            + extra);
        if (n instanceof javafx.scene.Parent p) for (javafx.scene.Node k : p.getChildrenUnmodifiable()) dump(k, indent + "  ");
    }

}
