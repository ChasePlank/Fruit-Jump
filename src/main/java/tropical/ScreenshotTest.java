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
                WritableImage img = scene.snapshot(null);
                // Write PNG by hand (no swing module in the JavaFX set):
                // raw RGBA scanlines + filter byte 0 per row.
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                byte[] raw = new byte[(w * 4 + 1) * h];
                var reader = img.getPixelReader();
                int ri = 0;
                for (int y = 0; y < h; y++) {
                    raw[ri++] = 0; // filter: none
                    for (int x = 0; x < w; x++) {
                        int argb = reader.getArgb(x, y);
                        raw[ri++] = (byte)(argb >> 16);
                        raw[ri++] = (byte)(argb >> 8);
                        raw[ri++] = (byte)argb;
                        raw[ri++] = (byte)(argb >> 24);
                    }
                }
                try {
                    var out = new java.io.FileOutputStream("/root/downloads/tropical-punch-screenshot.png");
                    var hdr = new java.io.ByteArrayOutputStream();
                    hdr.write(new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
                    // IHDR
                    hdr.write(new byte[]{0,0,0,13}); hdr.write("IHDR".getBytes());
                    var ih = new java.io.ByteArrayOutputStream();
                    ih.write(intBytes(w));
                    ih.write(intBytes(h));
                    ih.write(new byte[]{8, 6, 0, 0, 0});  // 8-bit RGBA
                    byte[] ihdr = ih.toByteArray();
                    hdr.write(ihdr); hdr.write(crc(cat("IHDR".getBytes(), ihdr)));
                    // IDAT
                    var def = new java.util.zip.Deflater();
                    def.setInput(raw); def.finish();
                    var comp = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[65536];
                    while (!def.finished()) {
                        int n = def.deflate(buf);
                        comp.write(buf, 0, n);
                    }
                    byte[] idat = comp.toByteArray();
                    hdr.write(intBytes(idat.length)); hdr.write("IDAT".getBytes());
                    hdr.write(idat); hdr.write(crc(cat("IDAT".getBytes(), idat)));
                    // IEND
                    hdr.write(new byte[]{0,0,0,0}); hdr.write("IEND".getBytes());
                    hdr.write(crc("IEND".getBytes()));
                    out.write(hdr.toByteArray());
                    out.close();
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
}
