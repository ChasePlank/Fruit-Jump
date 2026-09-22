package tropical;

import javafx.scene.image.WritableImage;
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
    static final WritableImage banana, enemy, heart, key, spike,
            door, crack, exit, bomb, bombFlash, arrow;

    // Left-facing variants (mirrored char grids) for facing-direction
    // rendering (playtest: "able to look both directions")
    static final WritableImage bananaL, enemyL;

    // 2x variants for the high-res canvas
    static final WritableImage banana2x, enemy2x, heart2x, key2x, spike2x,
            door2x, crack2x, exit2x, bomb2x, bombFlash2x, arrow2x;
    static final WritableImage bananaL2x, enemyL2x;

    static final int SCALE = 2;

    static {
        Map<Character, String> pal = Sprite.PAL();

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
    }
}
