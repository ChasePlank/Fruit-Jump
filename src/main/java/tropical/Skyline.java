package tropical;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * The horizon the game happens on: a sky, a ridge, a rock, and - usually - a climber standing on it.
 *
 * <p><b>Why this is one class and not two drawings.</b> "A climber, a sunset, and a way home" is the game's own
 * sentence, and it belongs on more than one screen. The title screen got it first; the game-over screen is the
 * other place it lands, and it is the screen a player sees MOST - dying is what a platformer does. Two copies of
 * the same horizon would drift apart the first time either was touched, so the geometry lives here and the two
 * screens ask for a mood.
 *
 * <p>NO TEXT FADE HERE, unlike the Aside edition: this copy's screens are centred, so there is no left-hand
 * column of words to protect from a bright sky. The labels carry a drop shadow instead.
 *
 * <p>The moods are the two ends of the same day. {@link Mood#DUSK} is the title screen: warm, gold on the
 * horizon, the sun still up. {@link Mood#NIGHT} is the game-over screen: the sun is down and the sky has gone
 * cold and grey. Same place, later.
 */
public final class Skyline {

    private Skyline() { }

    public enum Mood {
        /** Warm, the sun still on the ridge. */
        DUSK,
        /** After the sun. Cold and grey, and no sun to draw. */
        NIGHT
    }

    public static void paint(GraphicsContext gc, double w, double h, Mood mood, boolean climber) {
        if (w <= 0 || h <= 0) return;
        double horizon = h * (mood == Mood.DUSK ? 0.72 : 0.70);

        Color[] sky = mood == Mood.DUSK
                ? new Color[]{Color.web("#0d1526"), Color.web("#1d3b5c"), Color.web("#7a5a54"),
                              Color.web("#c9723c"), Color.web("#f5a623")}
                : new Color[]{Color.web("#05080e"), Color.web("#0e1a2a"), Color.web("#1e2c3a"),
                              Color.web("#333d46"), Color.web("#4a4f52")};
        for (int y = 0; y < horizon; y++) {
            double t = y / horizon * (sky.length - 1);
            int i = Math.min((int) t, sky.length - 2);
            gc.setFill(sky[i].interpolate(sky[i + 1], t - i));
            gc.fillRect(0, y, w, 1);
        }

        if (mood == Mood.DUSK) {
            // The sun, going down behind the ridge.
            double sunX = w * 0.62, sunY = horizon - h * 0.026, r = h * 0.082;
            gc.setFill(Color.web("#f5a623", 0.20));
            gc.fillOval(sunX - r * 1.9, sunY - r * 1.9, r * 3.8, r * 3.8);
            gc.setFill(Color.web("#f5c46a", 0.35));
            gc.fillOval(sunX - r * 1.35, sunY - r * 1.35, r * 2.7, r * 2.7);
            gc.setFill(Color.web("#ffe0a3"));
            gc.fillOval(sunX - r, sunY - r, r * 2, r * 2);
        } else {
            // One star, low and faint, where the sun was. Not a sky full of them - this is a game-over screen and
            // it should be quiet.
            gc.setFill(Color.web("#c9d4e0", 0.35));
            gc.fillOval(w * 0.30, horizon - h * 0.22, 3, 3);
            gc.fillOval(w * 0.52, horizon - h * 0.30, 2, 2);
            gc.fillOval(w * 0.80, horizon - h * 0.16, 2, 2);
        }

        // The ridge, and the ground.
        Color rock = mood == Mood.DUSK ? Color.web("#2a1c16") : Color.web("#12161c");
        Color soil = mood == Mood.DUSK ? Color.web("#16100c") : Color.web("#0a0c10");
        Color deep = mood == Mood.DUSK ? Color.web("#0d0a09") : Color.web("#06080b");
        gc.setFill(rock);
        gc.fillPolygon(new double[]{0, w * 0.16, w * 0.34, w * 0.55, w * 0.77, w, w, 0},
                       new double[]{horizon - h * 0.055, horizon - h * 0.13, horizon - h * 0.04,
                                    horizon - h * 0.10, horizon - h * 0.03, horizon - h * 0.08, horizon, horizon}, 8);
        gc.setFill(soil);
        gc.fillRect(0, horizon, w, h - horizon);
        gc.setFill(deep);
        gc.fillRect(0, horizon + h * 0.018, w, h - horizon - h * 0.018);
        gc.setFill(rock);
        gc.fillRect(w * 0.655, horizon - h * 0.115, w * 0.115, h * 0.125);

        // And the climber, on the rock - or not, which is the whole difference between the two screens. The
        // sprite is the real one, from the same grid the game plays, so the character here is the character you
        // control. If the sprite changes, this changes with it.
        if (climber) {
            WritableImage img = Sprite.buildScaled(Sprite.PLAYER, Sprite.PAL(),
                    Sprite.PLAYER[0].length() * 4, Sprite.PLAYER.length * 4);
            gc.drawImage(img, w * 0.685, horizon - h * 0.115 - Sprite.PLAYER.length * 4);
        }

    }
}
