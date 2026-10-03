package tropical;

import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * The climber customiser.
 *
 * <p>Three rows and left/right to change them. The character has no face, so hair and pack ARE the identity -
 * which is what makes this screen worth having rather than decoration, and why the gameplay screen reads the
 * config back. The body colour is intentionally not editable: it is pale cream because it has to read against
 * both the sunset sky and the near-black ground, and a player-chosen body colour could make the climber
 * invisible.
 *
 * <p>Canvas-drawn like this engine's other screens, and fitted to the window the same way, because the window
 * is resizable and the layout is not.
 */
public class CustomizeScreen extends Screen {

    static final int W = 1280, H = 720;
    static final Font F_TITLE = Font.font("Arial", 44);
    static final Font F_ITEM = Font.font("Arial", 24);
    static final Font F_TINY = Font.font("Arial", 14);

    final CharacterConfig cfg;
    final Screen back;

    int row = 0;                 // 0 hair length, 1 hair colour, 2 pack colour
    static final int ROWS = 3;

    Sprites.Look look;
    WritableImage previewStand, previewDown;

    private final Canvas canvas;
    private final GraphicsContext gc;
    private final StackPane root;

    public CustomizeScreen(ScreenManager manager, CharacterConfig cfg, Screen back) {
        super(manager);
        this.cfg = cfg;
        this.back = back;

        canvas = new Canvas(W, H);
        gc = canvas.getGraphicsContext2D();
        root = new StackPane(canvas);
        root.setStyle("-fx-background-color: #000000;");
        canvas.setManaged(false);
        GameplayScreen.fitToWindow(root, canvas);

        rebuild();
    }

    private void rebuild() {
        look = Sprites.buildLook(cfg);
        int w = Sprite.PLAYER[0].length(), h = Sprite.PLAYER.length;
        previewStand = Sprites.scaleNearest(look.right2x, w * 7, h * 7);
        previewDown = Sprites.scaleNearest(look.downRight2x, w * 7, h * 7);
        draw();
    }

    // Option rows. The swatch strips live to the RIGHT of the labels - drawn over them the first time, which
    // hid the very text you were choosing.
    static final double ROW_Y0 = 250, ROW_DY = 62;
    // Swatches must clear the preview panel at x=900, or the panel paints over the last colour.
    static final double SWATCH_X = 600, SWATCH_DX = 44;

    @Override public Parent getRoot() { return root; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (e.getCode()) {
            case UP -> row = (row - 1 + ROWS) % ROWS;
            case DOWN -> row = (row + 1) % ROWS;
            case LEFT -> { step(-1); rebuild(); }
            case RIGHT -> { step(1); rebuild(); }
            case ESCAPE, ENTER -> { cfg.save(); manager.replace(back); }
            default -> { }
        }
        e.consume();
    }

    private void step(int d) {
        switch (row) {
            case 0 -> cfg.hairLength = wrap(cfg.hairLength + d, 3);
            case 1 -> cfg.hairColor = wrap(cfg.hairColor + d, CharacterConfig.HAIR_COLORS.length);
            case 2 -> cfg.packColor = wrap(cfg.packColor + d, CharacterConfig.PACK_COLORS.length);
            default -> { }
        }
        cfg.normalise();
    }

    private static int wrap(int v, int n) { return ((v % n) + n) % n; }

    void draw() {
        gc.setFill(Color.web("#1a1a2e"));
        gc.fillRect(0, 0, W, H);

        gc.setFill(Color.web("#f5a623"));
        gc.setFont(F_TITLE);
        gc.fillText("THE CLIMBER", 90, 120);

        gc.setFill(Color.web("#8a8aa0"));
        gc.setFont(F_TINY);
        gc.fillText("up/down pick an option    left/right change it    ENTER keeps it", 94, 150);

        double y = ROW_Y0;
        for (int i = 0; i < ROWS; i++) {
            boolean sel = i == row;
            gc.setFill(sel ? Color.web("#f5a623") : Color.web("#c9c9d6"));
            gc.setFont(F_ITEM);
            gc.fillText((sel ? ">  " : "   ") + label(i), 120, y);
            y += ROW_DY;
        }

        drawSwatchRow(1, CharacterConfig.HAIR_COLORS, cfg.hairColor, ROW_Y0 + ROW_DY);
        drawSwatchRow(2, CharacterConfig.PACK_COLORS, cfg.packColor, ROW_Y0 + ROW_DY * 2);

        // Why the body is not a choice. Stated on the screen rather than left as a mystery.
        gc.setFill(Color.web("#6e6e86"));
        gc.setFont(F_TINY);
        gc.fillText("the body stays cream - it has to read against both the sunset sky and the black ground",
                    120, H - 60);

        // Live preview, on black so you see it against the ground colour.
        gc.setFill(Color.web("#0d0a09"));
        gc.fillRect(900, 215, 330, 195);
        gc.setFill(Color.web("#8a8aa0"));
        gc.setFont(F_TINY);
        gc.fillText("standing", 935, 208);
        gc.fillText("after a bat", 1075, 208);
        gc.drawImage(previewStand, 930, 232);
        gc.drawImage(previewDown, 1075, 232);
    }

    private String label(int i) {
        return switch (i) {
            case 0 -> "hair length:  " + CharacterConfig.HAIR_LENGTH_NAMES[cfg.hairLength];
            case 1 -> "hair colour:  " + CharacterConfig.HAIR_NAMES[cfg.hairColor];
            default -> "pack colour:  " + CharacterConfig.PACK_NAMES[cfg.packColor];
        };
    }

    private void drawSwatchRow(int rowIndex, String[] colors, int chosen, double y) {
        boolean sel = row == rowIndex;
        for (int i = 0; i < colors.length; i++) {
            double x = SWATCH_X + i * SWATCH_DX;
            gc.setFill(Color.web(colors[i]));
            gc.fillRect(x, y - 26, 34, 30);
            if (i == chosen) {
                gc.setStroke(sel ? Color.WHITE : Color.web("#c9c9d6"));
                gc.setLineWidth(3);
                gc.strokeRect(x - 3, y - 29, 40, 36);
            }
        }
        if (!sel) return;
        gc.setStroke(Color.web("#f5a623"));
        gc.setLineWidth(2);
        gc.strokeRect(SWATCH_X - 6, y - 32, colors.length * SWATCH_DX + 10, 42);
    }
}
