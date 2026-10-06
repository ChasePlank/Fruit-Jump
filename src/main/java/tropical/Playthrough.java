package tropical;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Playthrough - drive the real game with real key events and take pictures.
 *
 * <p><b>Why this exists.</b> On 2026-10-02 the engine was carried over from aside in one update, and every
 * suite in this repository passed before it and after it: DoorStress, Rooms, SaveLoad, MenuSmoke,
 * GameplayRobot, MenuRobot, and the ported water, pocket and enemy suites. Three features in that update were
 * broken in a way no suite could see, because no suite walks a player into a pool:
 *
 * <ul>
 *   <li><b>water</b> arrived with a field, no renderer, no vertical input and a grounded-only jump - so a
 *       player who walked into a flooded gap floated at the surface and could never leave. A soft-lock.</li>
 *   <li><b>bats</b> arrived flying, pursuing and <i>stunning</i> the player, and invisible.</li>
 *   <li><b>splash particles</b> arrived as droplets nothing drew.</li>
 * </ul>
 *
 * All three were found by looking at a level. This tool is that, made repeatable: it pushes a GameplayScreen,
 * holds RIGHT, taps jump on a timer, and writes a frame every few seconds, so a run can be read afterwards
 * instead of watched.
 *
 * <p><b>It loads the stylesheet</b>, which matters: the first version did not, and the game-over screen came
 * out with its text jammed against the left edge and its key hints nearly invisible. That was the harness, not
 * the game. An artifact produced by a test harness is evidence about the harness until you have run the real
 * thing - the same trap as this repository's ScreenshotTest, which built an 800x600 scene against a 1600x1200
 * canvas.
 *
 * <p>Run it through a display:
 * <pre>
 *   java --module-path $FX --add-modules $MODS -Dlevel=15 -Dshotdir=/tmp/play tropical.Playthrough
 * </pre>
 *
 * <p>Properties: {@code -Dlevel=N} (default 1), {@code -Dshotdir=DIR} (default /tmp/playthrough),
 * {@code -Dseconds=N} (default 30), {@code -Dkill=1} to zero the player's HP partway through so the run
 * reaches the death path - which a bot that plays well never reaches, and which is the screen a player sees
 * most often - {@code -Dtutorial=1} to play the hand-built tutorial levels instead of a generated one, and
 * {@code -DjumpEvery=N} (default 45 frames) because a bot that jumps every 0.75s falls into every pit and dies
 * before it can show you what comes after them.
 */
public class Playthrough extends Application {

    private ScreenManager screens;
    private Scene scene;
    private int shot = 0;
    private final String dir = System.getProperty("shotdir", "/tmp/playthrough");

    @Override public void start(Stage stage) {
        new java.io.File(dir).mkdirs();
        screens = new ScreenManager();
        StackPane root = new StackPane();
        root.getChildren().add(screens.getContainer());
        scene = new Scene(root, 1600, 1200, Color.BLACK);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        scene.setOnKeyPressed(e -> screens.handleKey(e));
        stage.setScene(scene);
        stage.show();

        int level = Integer.getInteger("level", 1);
        boolean tutorial = System.getProperty("tutorial") != null;
        int frames = Integer.getInteger("seconds", 30) * 60;
        boolean kill = System.getProperty("kill") != null;
        screens.push(tutorial ? new GameplayScreen(screens, level, true)
                              : new GameplayScreen(screens, level));
        System.out.println((tutorial ? "tutorial level " : "level ") + level + " for " + (frames / 60) + "s"
                + (kill ? ", killing the player at 5s to reach the death path" : ""));

        hold(KeyCode.RIGHT);

        new AnimationTimer() {
            long frame = 0;
            @Override public void handle(long now) {
                frame++;
                // jumpAfter lets a run walk into something before it is allowed to jump - which is how
                // you test the water: no jumping means the climber walks off the lip and into the pool,
                // and then jumping is the only way out.
                int jumpAfter = Integer.getInteger("jumpAfter", 0) * 60;
                // A bot that jumps every 0.75s falls into every pit and dies before it can show you what
                // comes after them - which is exactly what happened testing the tutorial's end: level 8 is
                // the spike lesson, and the run ended in GAME OVER every time. Jumping every 0.25s clears
                // them and the run finishes.
                int jumpEvery = Integer.getInteger("jumpEvery", 45);
                if (frame % jumpEvery == 0 && frame >= jumpAfter) tap(KeyCode.SPACE);
                // -DbombEvery=N taps G every N frames. The bot has never used bombs, which is why the tutorial
                // level that TEACHES the bomb has always been excluded from automated runs - and why nothing
                // noticed that the blast could not hurt the player it was thrown next to.
                int bombEvery = Integer.getInteger("bombEvery", 0);
                if (bombEvery > 0 && frame % bombEvery == 0) tap(KeyCode.G);
                if (kill && frame == 300) zeroHealth();
                // HOW OFTEN TO CAPTURE, settable. It was hardcoded at 180 frames - three seconds - which is fine
                // for watching a run but useless for anything SHORT: a damage flash lasts a third of a second, so
                // a three-second interval has roughly one chance in nine of catching one, and "I did not see it"
                // would then say nothing about whether it works.
                int captureEvery = Integer.getInteger("captureEvery", 180);
                if (captureEvery > 0 && frame % captureEvery == 0) capture("f" + frame);
                if (frame >= frames) {
                    capture("end");
                    System.out.println("  ended on " + screens.peek().getClass().getSimpleName());
                    stop();
                    Platform.exit();
                }
            }
        }.start();
    }

    private void zeroHealth() {
        try {
            var f = GameplayScreen.class.getDeclaredField("combat");
            f.setAccessible(true);
            Object combat = f.get(screens.peek());
            combat.getClass().getField("playerHP").setDouble(combat, 0.0);
            System.out.println("  playerHP set to 0");
        } catch (Exception ex) {
            System.out.println("  could not set HP: " + ex);
        }
    }

    private void hold(KeyCode c) {
        scene.getOnKeyPressed().handle(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", c, false, false, false, false));
    }

    private void tap(KeyCode c) {
        scene.getOnKeyPressed().handle(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", c, false, false, false, false));
        var top = screens.peek();
        if (top != null) top.handleKey(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", c, false, false, false, false));
    }

    private void capture(String tag) {
        try {
            var img = scene.snapshot(null);
            int w = (int) img.getWidth(), h = (int) img.getHeight();
            var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var pr = img.getPixelReader();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
            String path = dir + "/" + String.format("%02d", shot++) + "-" + tag + ".png";
            javax.imageio.ImageIO.write(bi, "png", new java.io.File(path));
            System.out.println("  " + path + "   screen=" + screens.peek().getClass().getSimpleName());
        } catch (Exception ex) {
            System.out.println("  capture failed: " + ex);
        }
    }

    public static void main(String[] args) { launch(args); }
}
