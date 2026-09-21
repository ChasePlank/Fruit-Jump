package tropical;

import javafx.scene.image.WritableImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprite cache: builds all WritableImages once at startup.
 * Sprites are char-grid pixel art defined in Sprite.java.
 */
public class Sprites {
    static final WritableImage banana, enemy, heart, key, spike,
            door, crack, exit, bomb, bombFlash, arrow;

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

        // Bomb flash: same grid, red-shifted palette (fuse nearly spent)
        Map<Character, String> flashPal = new HashMap<>(pal);
        flashPal.put('M', "#E23B2E");
        bombFlash = Sprite.build(Sprite.BOMB, flashPal);
    }
}
