package tropical;

import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;

/**
 * Pixel-art sprites defined as character grids + palette maps.
 * Hand-authored: pixel art at 32px tile scale needs an explicit pixel
 * grid — a downscaled render loses the grid that makes pixel art read.
 * Each sprite is a String[] (rows) of palette chars; ' ' = transparent.
 */
public class Sprite {
    /** Build a WritableImage from a char grid + palette. */
    public static WritableImage build(String[] rows, java.util.Map<Character, String> palette) {
        return buildScaled(rows, palette, rows[0].length(), rows.length);
    }

    /**
     * Build a sprite at an arbitrary destination size using nearest-
     * neighbor sampling from the char grid. Each destination pixel maps
     * to one grid cell — the pixel grid stays crisp at any scale.
     *
     * Used to pre-scale sprites to their exact PHYSICAL draw size (2x
     * the logical size): at render time the image lands 1:1 on screen,
     * so JavaFX's bilinear pipeline never resamples the art. (JavaFX 17
     * has no smoothing toggle on GraphicsContext — pre-scaling is the
     * only way to keep pixel art crisp.)
     */
    public static WritableImage buildScaled(String[] rows, java.util.Map<Character, String> palette,
                                            int dstW, int dstH) {
        int srcH = rows.length;
        int srcW = rows[0].length();
        WritableImage img = new WritableImage(dstW, dstH);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < dstH; y++) {
            int sy = y * srcH / dstH;
            String row = rows[sy];
            for (int x = 0; x < dstW; x++) {
                int sx = x * srcW / dstW;
                char c = row.charAt(sx);
                if (c == ' ') continue;
                String hex = palette.get(c);
                if (hex == null) continue;
                int argb = 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
                pw.setArgb(x, y, argb);
            }
        }
        return img;
    }

    // ---- Shared palette ----
    static java.util.Map<Character, String> PAL() {
        java.util.Map<Character, String> p = new java.util.HashMap<>();
        p.put('Y', "#F5D442"); // banana yellow
        p.put('D', "#B8860B"); // banana dark / outline
        p.put('G', "#5CBF3E"); // radioactive green (glow spots)
        p.put('W', "#FFFFFF");
        p.put('K', "#1A1A1A"); // near-black outline
        p.put('B', "#8B5A2B"); // brown (crate, stems, monkey fur)
        p.put('T', "#D2A679"); // tan (monkey face)
        p.put('S', "#787878"); // stone gray
        p.put('L', "#A8A8A8"); // stone light
        p.put('R', "#E23B2E"); // red (heart, bomb flash)
        p.put('O', "#FF8C00"); // orange (spike, key gold)
        p.put('N', "#4A90D9"); // blue (door)
        p.put('C', "#7EC8E3"); // cyan (cracked lines)
        p.put('M', "#2F4F4F"); // dark slate (bomb body)
        return p;
    }

    /** Horizontally mirror a char grid (for left-facing sprite variants). */
    public static String[] flipX(String[] rows) {
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++)
            out[i] = new StringBuilder(rows[i]).reverse().toString();
        return out;
    }

    // ---- Player: radioactive banana character, facing right, 12x22 ----
    // A banana standing on end (classic crescent), with the face placed
    // on the RIGHT half so the left/right flip is clearly visible (the
    // old sprite's features were centered — it "faced the camera" and
    // the flip did nothing, playtest feedback). Brown stem on top.
    static String[] BANANA = {
        "      BB      ",
        "     KBBK     ",
        "    KYYYYK    ",
        "   KYYYYYYK   ",
        "   KYYYYYYYK  ",
        "  KYYWYWYYYK  ",
        "  KYYWYWYYYK  ",
        "  KYYYYYYYYYK ",
        "  KYYYYYKKYYK ",
        "  KYYYYKYYYK  ",
        "  KYYYYYYYYYK ",
        "  KYYYYYYYYYK ",
        "  KGGYYYYGGK  ",
        "  KYYGYYGYYK  ",
        "  KYYYYYYYYYK ",
        "   KYYYYYYYK  ",
        "   KYYYYYYYK  ",
        "    KYYYYYK   ",
        "    KYYYYYK   ",
        "     KYYYK    ",
        "     KYYYK    ",
        "      KKK     ",
    };

    // ---- Enemy: banana-eating monkey, 16x16 ----
    // (playtest: "enemies seem to be rocks — monkeys or apes would
    // fit better, things that eat bananas")
    static String[] ENEMY = {
        "  KKKK    KKKK  ",
        " KBBBBKKKKBBBBK ",
        " KBBTTTTTTTTBBK ",
        " KBTTTTTTTTTTBK ",
        " KBTKKTTTTKKTBK ",
        " KBTKKTTTTKKTBK ",
        " KBTTTTTTTTTTBK ",
        " KBTTTTKKTTTTBK ",
        " KBTTTTTTTTTTBK ",
        " KBBKKKKKKKKBBK ",
        "  KBBTTTTTTBBK  ",
        "   KBBBBBBBBK   ",
        "   KBKBBBBKBK   ",
        "   KBKBBBBKBK   ",
        "    KK KBK KK   ",
        "       KKK      ",
    };

    // ---- Heart pickup, 8x8 ----
    static String[] HEART = {
        " KK KK ",
        "KRRKRRK",
        "KRRRRRK",
        "KRRRRRK",
        " KRRRK ",
        "  KRK  ",
        "   K   ",
        "       ",
    };

    // ---- Key, 8x8 ----
    static String[] KEY = {
        "  KKKK  ",
        " KOOOOK ",
        " KO  OK ",
        " KOOOOK ",
        "  KKOKK ",
        "    KO  ",
        "    KO  ",
        "    KK  ",
    };

    // ---- Spike (drawn per-tile), 8x8 ----
    static String[] SPIKE = {
        "K K K K ",
        "OLOLOLOL",
        "OLOLOLOL",
        "OLOLOLOL",
        "OLOLOLOL",
        "OLOLOLOL",
        "SSSSSSSS",
        "        ",
    };

    // ---- Door (2 tiles tall = 64px; sprite 16x32, scaled 2x) ----
    static String[] DOOR = {
        "KKKKKKKKKKKKKKKK",
        "KNNNNNNNNNNNNNNK",
        "KNCCNNNNNNNNCCNK",
        "KNCCNNNNNNNNCCNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNOONNNNNK",
        "KNNNNNNNOONNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KKKKKKKKKKKKKKKK",
    };

    // ---- Cracked tile overlay, 8x8 (drawn over stone) ----
    static String[] CRACK = {
        "C      C",
        " CC    C",
        "  CCCC C",
        " C CCCC ",
        "C  CC CC",
        " C CC C ",
        "CC C  C ",
        " C    CC",
    };

    // ---- Exit flag, 8x12 ----
    static String[] EXIT = {
        "KOOOOOOK",
        "KOOOOOOK",
        "KOOOOOOK",
        "KOOOOOOK",
        "KOOOOOOK",
        "KKKKKKKK",
        "   KK   ",
        "   KK   ",
        "   KK   ",
        "   KK   ",
        "   KK   ",
        "   KK   ",
    };

    // ---- Bomb, 6x6 ----
    static String[] BOMB = {
        "  KK  ",
        " KOOK ",
        "KMMMMK",
        "KMMMMK",
        "KMMMMK",
        " KKKK ",
    };

    // ---- Arrow, 8x3 ----
    static String[] ARROW = {
        "      KK",
        "BBBBBBOK",
        "      KK",
    };
}
