package tropical;

import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprite cache: builds all WritableImages once at startup.
 * Sprites are char-grid pixel art defined in Sprite.java.
 *
 * Two variants per sprite:
 *   - native (1x grid resolution) — used by headless tests
 *   - 2x — pre-scaled with nearest-neighbor for the high-res canvas.
 *     At render time the 2x image is drawn at 2x its logical size, so
 *     it lands 1:1 on physical pixels — JavaFX never resamples it
 *     (JavaFX 17 has no smoothing toggle; pre-scaling is the crisp path).
 */
public class Sprites {
    /** The boss, at grid size and at SCALE: see Sprite.BOSS for why 64 divides its box exactly. */
    static final WritableImage boss, boss2x;

    static final WritableImage banana, enemy, heart, key, spike,
            door, crack, exit, bomb, bombFlash, arrow;

    // Left-facing variants (mirrored char grids) for facing-direction
    // rendering (playtest: "able to look both directions")
    static final WritableImage bananaL, enemyL;

    // Player: the climber, replacing the banana as the playable body.
    static final WritableImage player, playerL, player2x, playerL2x;


    // Flying enemy.
    static final WritableImage bat, bat2x;
    static final WritableImage piranha, piranha2x;

    // World pickup (was the heart).
    static final WritableImage snack, snack2x;

    // Safe-room reward.
    static final WritableImage jar, jar2x;

    // Enemies: spider and snake replace the monkey.
    static final WritableImage spider, spiderL, spider2x, spiderL2x;
    static final WritableImage snake, snakeL, snake2x, snakeL2x;
    /** The snake's resting pose. Shown until it is actually chasing. */
    static final WritableImage snakeCoil, snakeCoilL, snakeCoil2x, snakeCoilL2x;

    // 2x variants for the high-res canvas
    static final WritableImage banana2x, enemy2x, heart2x, key2x, spike2x,
            door2x, crack2x, exit2x, bomb2x, bombFlash2x, arrow2x;
    static final WritableImage bananaL2x, enemyL2x;

    static final int SCALE = 2;

    static {
        Map<Character, String> pal = Sprite.PAL();

        boss   = Sprite.build(Sprite.BOSS, Sprite.bossPal());
        boss2x = Sprite.buildScaled(Sprite.BOSS, Sprite.bossPal(),
                Sprite.BOSS[0].length() * SCALE, Sprite.BOSS.length * SCALE);

        banana = Sprite.build(Sprite.BANANA, pal);
        enemy  = Sprite.build(Sprite.ENEMY, pal);
        heart  = Sprite.build(Sprite.HEART, pal);
        key    = Sprite.build(Sprite.KEY, pal);
        spike  = Sprite.build(Sprite.SPIKE, pal);
        door   = Sprite.build(Sprite.DOOR, pal);
        crack  = Sprite.build(Sprite.CRACK, pal);
        exit   = Sprite.build(Sprite.EXIT, pal);
        bomb   = Sprite.build(Sprite.BOMB, pal);
        arrow  = Sprite.build(Sprite.ARROW, pal);

        // Mirrored (left-facing) variants
        bananaL = Sprite.build(Sprite.flipX(Sprite.BANANA), pal);
        enemyL  = Sprite.build(Sprite.flipX(Sprite.ENEMY), pal);

        player  = Sprite.build(Sprite.PLAYER, pal);
        playerL = Sprite.build(Sprite.flipX(Sprite.PLAYER), pal);

        bat     = Sprite.build(Sprite.BAT, pal);
        snack   = Sprite.build(Sprite.SNACK, pal);
        jar     = Sprite.build(Sprite.JAR, pal);
        spider  = Sprite.build(Sprite.SPIDER, pal);
        spiderL = Sprite.build(Sprite.flipX(Sprite.SPIDER), pal);
        snake   = Sprite.build(Sprite.SNAKE, pal);
        snakeL  = Sprite.build(Sprite.flipX(Sprite.SNAKE), pal);
        snakeCoil  = Sprite.build(Sprite.SNAKE_COIL, pal);
        snakeCoilL = Sprite.build(Sprite.flipX(Sprite.SNAKE_COIL), pal);

        // Bomb flash: same grid, red-shifted palette (fuse nearly spent)
        Map<Character, String> flashPal = new HashMap<>(pal);
        flashPal.put('M', "#E23B2E");
        bombFlash = Sprite.build(Sprite.BOMB, flashPal);

        // 2x pre-scaled variants (nearest-neighbor, crisp pixel grid)
        banana2x = Sprite.buildScaled(Sprite.BANANA, pal, Sprite.BANANA[0].length() * SCALE, Sprite.BANANA.length * SCALE);
        enemy2x  = Sprite.buildScaled(Sprite.ENEMY, pal, Sprite.ENEMY[0].length() * SCALE, Sprite.ENEMY.length * SCALE);
        heart2x  = Sprite.buildScaled(Sprite.HEART, pal, Sprite.HEART[0].length() * SCALE, Sprite.HEART.length * SCALE);
        key2x    = Sprite.buildScaled(Sprite.KEY, pal, Sprite.KEY[0].length() * SCALE, Sprite.KEY.length * SCALE);
        spike2x  = Sprite.buildScaled(Sprite.SPIKE, pal, Sprite.SPIKE[0].length() * SCALE, Sprite.SPIKE.length * SCALE);
        door2x   = Sprite.buildScaled(Sprite.DOOR, pal, Sprite.DOOR[0].length() * SCALE, Sprite.DOOR.length * SCALE);
        crack2x  = Sprite.buildScaled(Sprite.CRACK, pal, Sprite.CRACK[0].length() * SCALE, Sprite.CRACK.length * SCALE);
        exit2x   = Sprite.buildScaled(Sprite.EXIT, pal, Sprite.EXIT[0].length() * SCALE, Sprite.EXIT.length * SCALE);
        bomb2x   = Sprite.buildScaled(Sprite.BOMB, pal, Sprite.BOMB[0].length() * SCALE, Sprite.BOMB.length * SCALE);
        arrow2x  = Sprite.buildScaled(Sprite.ARROW, pal, Sprite.ARROW[0].length() * SCALE, Sprite.ARROW.length * SCALE);
        bombFlash2x = Sprite.buildScaled(Sprite.BOMB, flashPal, Sprite.BOMB[0].length() * SCALE, Sprite.BOMB.length * SCALE);

        bananaL2x = Sprite.buildScaled(Sprite.flipX(Sprite.BANANA), pal, Sprite.BANANA[0].length() * SCALE, Sprite.BANANA.length * SCALE);
        enemyL2x  = Sprite.buildScaled(Sprite.flipX(Sprite.ENEMY), pal, Sprite.ENEMY[0].length() * SCALE, Sprite.ENEMY.length * SCALE);

        player2x  = Sprite.buildScaled(Sprite.PLAYER, pal, Sprite.PLAYER[0].length() * SCALE, Sprite.PLAYER.length * SCALE);
        playerL2x = Sprite.buildScaled(Sprite.flipX(Sprite.PLAYER), pal, Sprite.PLAYER[0].length() * SCALE, Sprite.PLAYER.length * SCALE);

        bat2x     = Sprite.buildScaled(Sprite.BAT, pal, Sprite.BAT[0].length() * SCALE, Sprite.BAT.length * SCALE);
        piranha   = Sprite.build(Sprite.PIRANHA, pal);
        piranha2x = Sprite.buildScaled(Sprite.PIRANHA, pal, Sprite.PIRANHA[0].length() * SCALE, Sprite.PIRANHA.length * SCALE);
        snack2x   = Sprite.buildScaled(Sprite.SNACK, pal, Sprite.SNACK[0].length() * SCALE, Sprite.SNACK.length * SCALE);
        jar2x     = Sprite.buildScaled(Sprite.JAR, pal, Sprite.JAR[0].length() * SCALE, Sprite.JAR.length * SCALE);
        spider2x  = Sprite.buildScaled(Sprite.SPIDER, pal, Sprite.SPIDER[0].length() * SCALE, Sprite.SPIDER.length * SCALE);
        spiderL2x = Sprite.buildScaled(Sprite.flipX(Sprite.SPIDER), pal, Sprite.SPIDER[0].length() * SCALE, Sprite.SPIDER.length * SCALE);
        snake2x   = Sprite.buildScaled(Sprite.SNAKE, pal, Sprite.SNAKE[0].length() * SCALE, Sprite.SNAKE.length * SCALE);
        snakeL2x  = Sprite.buildScaled(Sprite.flipX(Sprite.SNAKE), pal, Sprite.SNAKE[0].length() * SCALE, Sprite.SNAKE.length * SCALE);
        snakeCoil2x  = Sprite.buildScaled(Sprite.SNAKE_COIL, pal, Sprite.SNAKE_COIL[0].length() * SCALE, Sprite.SNAKE_COIL.length * SCALE);
        snakeCoilL2x = Sprite.buildScaled(Sprite.flipX(Sprite.SNAKE_COIL), pal, Sprite.SNAKE_COIL[0].length() * SCALE, Sprite.SNAKE_COIL.length * SCALE);
    }

    /** The climber's sprites for one look. Built on change, never per frame. */
    public static final class Look {
        public final WritableImage right2x, left2x, downRight2x, downLeft2x;
        /** Getting back up: sprawled, then up onto one knee. */
        public final WritableImage kneelRight2x, kneelLeft2x;
        /** Grid sizes in engine pixels. Every pose is a different SHAPE, so the
         *  draw cannot use one set of dimensions for all of them. */
        public final int gridW, gridH, downGridW, downGridH, kneelGridW, kneelGridH;

        Look(String[] stand, String[] down, java.util.Map<Character, String> p) {
            this.gridW = stand[0].length();
            this.gridH = stand.length;
            this.downGridW = down[0].length();
            this.downGridH = down.length;
            this.kneelGridW = Sprite.KNEEL[0].length();
            this.kneelGridH = Sprite.KNEEL.length;
            this.right2x = scaleNearest(Sprite.build(stand, p), gridW * SCALE, gridH * SCALE);
            this.left2x  = scaleNearest(Sprite.build(Sprite.flipX(stand), p), gridW * SCALE, gridH * SCALE);
            this.downRight2x = scaleNearest(Sprite.build(down, p), downGridW * SCALE, downGridH * SCALE);
            this.downLeft2x  = scaleNearest(Sprite.build(Sprite.flipX(down), p), downGridW * SCALE, downGridH * SCALE);
            this.kneelRight2x = scaleNearest(Sprite.build(Sprite.KNEEL, p), kneelGridW * SCALE, kneelGridH * SCALE);
            this.kneelLeft2x  = scaleNearest(Sprite.build(Sprite.flipX(Sprite.KNEEL), p), kneelGridW * SCALE, kneelGridH * SCALE);
        }
    }

    /**
     * Nearest-neighbour rescale. JavaFX's Canvas has no smoothing toggle, so
     * any on-screen size that is not the image's own size must be pre-scaled
     * or the pixel art goes soft.
     */
    public static WritableImage scaleNearest(WritableImage src, int w, int h) {
        WritableImage out = new WritableImage(w, h);
        PixelReader rd = src.getPixelReader();
        PixelWriter pw = out.getPixelWriter();
        int sw = (int) src.getWidth(), sh = (int) src.getHeight();
        for (int y = 0; y < h; y++) {
            int sy = Math.min(sh - 1, y * sh / h);
            for (int x = 0; x < w; x++) {
                pw.setArgb(x, y, rd.getArgb(Math.min(sw - 1, x * sw / w), sy));
            }
        }
        return out;
    }

    /**
     * Build the climber for a given look. Hair length picks the head grid;
     * hair and pack colour are palette substitutions, so the whole feature
     * stays three grids deep instead of a combinatorial pile of sheets.
     */
    public static Look buildLook(CharacterConfig cfg) {
        cfg.normalise();
        java.util.Map<Character, String> p =
                Sprite.playerPal(cfg.hairHex(), cfg.packHex());
        String[] grid = Sprite.playerGrid(cfg.hairLength);
        // Sprawl and kneel are hand-drawn, but built with the SAME palette, so
        // they pick up the chosen hair and pack colours for free.
        return new Look(grid, Sprite.SPRAWL, p);
    }
}
