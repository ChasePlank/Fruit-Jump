package tropical;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The Tutorial menu entry actually opens the tutorial.
 *
 * <p>It is wired - menu entry, hand-built levels instead of the generator, no autosave, and it ends back at the
 * menu - and NOTHING TESTED IT. The menu test checks the label is in the list and nothing more, so the wiring
 * could be removed and every suite would still pass. That is the same shape as the tutorial being unreachable
 * in the first place: present, and no one checking it could be reached.
 *
 * <p>Three things, and the third is the one that would have caught the bug that made every tutorial level end
 * in GAME OVER on its first frame: the level is HAND-BUILT, so it is 20 rows, where a generated one is 14. A
 * screen that still hardcoded 14 anywhere would fail this.
 */
public class TutorialTest extends Application {
    private ScreenManager screens;
    private int step = 0;
    private int failures = 0;

    private void check(String name, boolean ok) {
        System.out.printf("%-52s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    /**
     * NO TWO SIGNS MAY OVERLAP. This is a static check with no screen involved, so it runs for all nine levels.
     *
     * <p>It exists because two signs on tutorial 6 WERE overlapping and neither could be read: "JAR - a life
     * FOREVER, and a full refill" is 41 characters, wrapSign wraps it to a 34-character line, and at the 12px a
     * character this is drawn in that spans roughly 408px from x=704 - ending near 1112. The KEY sign started at
     * 960, inside it. wrapSign keeps a sign ON THE SCREEN and the clamp keeps it NEAR its object, and neither
     * knows about the sign next to it.
     *
     * <p>12px a character is the same figure the renderer's own clamp comment uses, so this measures what the
     * player sees rather than a guess. Two signs collide when their horizontal spans overlap AND their rows do -
     * a sign wraps onto its own second row 26px down, which is why a three-line sign can reach a sign below it.
     */
    private void checkSignsDoNotOverlap() {
        // MEASURED WITH THE REAL FONT, not estimated. The first version of this check assumed 12px a character -
        // the figure the renderer's own clamp comment quotes - and reported THREE collisions that are not there,
        // including two signs on tutorial 3 that have a clear gap between them on screen. An estimate that is a
        // shade over the renderer's metric is a shade under someone else's, and a check built on a guess reports
        // its own error as a fault in the game.
        //
        // So: the same font the renderer draws with, the same wrapSign it wraps with, and JavaFX's own layout
        // bounds for the width. The only thing still assumed is the 26px line height, which is in the draw loop.
        final Font FONT = Font.font("Arial", 22);
        final double LINE_H = 26;
        int checked = 0, collisions = 0;

        java.util.List<Tutorial.Sign> all = new java.util.ArrayList<>();
        // Tutorial.LAST, not a literal 9. The tutorial grew a level once already and a hardcoded bound is how
        // a new level goes untested - which is the same shape as the README's tutorial count, which was wrong.
        for (int level = 1; level <= Tutorial.LAST; level++) {
            java.util.List<Tutorial.Sign> signs = Tutorial.signs(level);
            for (int i = 0; i < signs.size(); i++) {
                for (int j = i + 1; j < signs.size(); j++) {
                    Tutorial.Sign a = signs.get(i), b = signs.get(j);
                    boolean overlapY = false;
                    {
                        double[] ra = spanOf(a, FONT, LINE_H);
                        double[] rb = spanOf(b, FONT, LINE_H);
                        overlapY = ra[2] < rb[3] && rb[2] < ra[3];
                    }
                    checked++;
                    if (!overlapY) continue;
                    // EVERY CAMERA POSITION, not just the one where a sign sits at its own x. The renderer clamps
                    // a sign to the right edge of the VIEW, so which signs collide depends on where the camera is:
                    // a sign far to the right is pulled flush-right as the view approaches it, and can land on
                    // top of one that is already there. Modelling a single camera reported no overlap on tutorial
                    // 4 while the render showed the hookshot sign running into the spiders sign.
                    boolean hit = false;
                    for (double cam = 0; cam <= 1920 - 1280 && !hit; cam += 16) {
                        double[] ra = screenSpan(a, cam, FONT, LINE_H);
                        double[] rb = screenSpan(b, cam, FONT, LINE_H);
                        if (ra[0] < rb[1] && rb[0] < ra[1]) hit = true;
                    }
                    if (hit) {
                        collisions++;
                        System.out.printf("  tutorial %d: \"%s\" overlaps \"%s\"%n", level, shorten(a.text()), shorten(b.text()));
                    }
                }
            }
        }
        check("no two tutorial signs overlap (" + checked + " pair(s) checked)", collisions == 0);

        // AND THAT THE TUTORIAL TEACHES EVERY CONTROL THE README DOCUMENTS.
        //
        // This is the check that would have caught the hookshot. The README leads with it - "with hookshot, bombs,
        // bow" - and documents it as a control, and NONE of the nine levels mentioned it; the tutorial taught
        // movement, jumping, spiders, bombs, arrows, bats, pickups, spikes and water, and never the game's own
        // signature mechanic. The collision check above cannot see that: it compares signs to each other, and a
        // mechanic with no sign at all has nothing to collide with.
        //
        // THE LIST COMES FROM THE README, not from here, so it cannot drift from what a player is told. Pause is
        // excluded on purpose: it is a menu control, not something a level can teach.
        java.util.List<String> missing = new java.util.ArrayList<>();
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of("README.md"));
            boolean inControls = false;
            for (String line : lines) {
                if (line.startsWith("**Controls:**")) { inControls = true; continue; }
                if (inControls && line.startsWith("## ")) break;
                if (!inControls || !line.startsWith("- ")) continue;
                int dash = line.indexOf('\u2014');
                if (dash < 0) continue;
                String action = line.substring(dash + 1).trim().split("[ ,]")[0].toLowerCase();
                if (action.equals("pause")) continue;
                boolean taught = false;
                for (int level = 1; level <= Tutorial.LAST && !taught; level++) {
                    for (Tutorial.Sign sg : Tutorial.signs(level)) {
                        if (sg.text().toLowerCase().contains(action)) { taught = true; break; }
                    }
                }
                if (!taught) missing.add(action);
            }
        } catch (Exception e) {
            check("the README's control list can be read", false);
        }
        for (String m : missing) System.out.println("  NO SIGN TEACHES: " + m);
        check("every control the README documents is taught by a sign (" + missing.size() + " missing)",
                missing.isEmpty());

        // AND THAT THE TUTORIAL ACTUALLY HAS WATER YOU CAN SWIM IN.
        //
        // The sign on level 9 says "SPACE swims up. DOWN / S dives", and a sign saying that above water you cannot
        // swim in is the same fault as a sign promising a rule the code does not enforce. MEASURED, because the
        // first version of this was reasoned from a screenshot and got it wrong: the climber floats at an
        // equilibrium with most of the body under water, so a still frame reads as standing on the surface.
        //
        // What separates the two waters is not submersion - both are about 0.73 - it is `deep`, which is the
        // water's SHAPE. A flooded walk is one row and is not deep; a pool is two rows with a floor and is.
        tropical.engine.World w = new tropical.engine.World();
        Tutorial.map(9).buildWorld(w);
        tropical.engine.Physics.Body swimmer =
                new tropical.engine.Physics.Body(47 * 32 + 16, 17 * 32, 24, 44);
        w.addBody(swimmer);
        for (int i = 0; i < 180; i++) w.update(1.0 / 60);
        boolean swims = w.water.swimming(swimmer);
        System.out.printf("  level 9 pool: submersion=%.3f swimming=%b deep=%b%n",
                w.water.submersion(swimmer), swims, w.water.deep(swimmer));
        check("level 9 has water the climber can swim in", swims);
    }

    /** {x0, x1, y0, y1} for a sign, using the renderer's font and wrap. */
    private static double[] spanOf(Tutorial.Sign s, Font font, double lineH) {
        java.util.List<String> lines = GameplayScreen.wrapSign(s.text());
        double widest = 0;
        for (String line : lines) {
            javafx.scene.text.Text t = new javafx.scene.text.Text(line);
            t.setFont(font);
            widest = Math.max(widest, t.getLayoutBounds().getWidth());
        }
        // THE CLAMP, WHICH THIS DID NOT MODEL AND THE RENDERER DOES. GameplayScreen draws a sign at
        // min(sx, CANVAS_W - widest * 12 - 8) so that a sign anchored past the right edge is still readable -
        // and the check computed the span from s.x() alone. So the two disagreed, and the disagreement was
        // visible: on tutorial 4 the hookshot sign is anchored at column 44 and the clamp pulls it from screen
        // 1100 to 864, which is inside the spiders sign's span. The check said "no two tutorial signs overlap"
        // while the render showed them running together.
        //
        // The clamp is in SCREEN space and a sign is only clamped when it is partly on screen - the renderer
        // skips one entirely past the edge - so the span is expressed in screen coordinates for the same camera.
        // THE RENDERER'S `widest` IS A CHARACTER COUNT, NOT A WIDTH. GameplayScreen computes
        // `for (String line : lines) widest = Math.max(widest, line.length())` and then clamps to
        // CANVAS_W - widest * 12 - 8. My first version of this model multiplied the PIXEL width by 12, which
        // made the threshold enormous and clamped every sign on every level - it reported four overlaps that are
        // not there, including two on tutorial 6 that were fixed weeks ago. A model of a renderer has to use the
        // renderer's own arithmetic, not an equivalent-looking one.
        return new double[] { s.x(), s.x() + widest, s.y(), s.y() + lines.size() * lineH };
    }

    /** The sign's span in SCREEN coordinates for a given camera x, including the renderer's clamp. */
    private static double[] screenSpan(Tutorial.Sign s, double cam, Font font, double lineH) {
        java.util.List<String> lines = GameplayScreen.wrapSign(s.text());
        double widest = 0;
        for (String line : lines) {
            javafx.scene.text.Text t = new javafx.scene.text.Text(line);
            t.setFont(font);
            widest = Math.max(widest, t.getLayoutBounds().getWidth());
        }
        double sx = s.x() - cam;
        if (sx > 1280 || sx + widest < 0) return new double[] { 1e9, 1e9, s.y(), s.y() + lines.size() * lineH };
        sx = Math.min(sx, 1280 - widest - 8);
        return new double[] { sx, sx + widest, s.y(), s.y() + lines.size() * lineH };
    }

    private static String shorten(String t) {
        return t.length() > 30 ? t.substring(0, 30) + "..." : t;
    }

    @Override public void start(Stage stage) {
        checkSignsDoNotOverlap();
        screens = new ScreenManager();
        StackPane root = new StackPane();
        root.getChildren().add(screens.getContainer());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        scene.setOnKeyPressed(e -> screens.handleKey(e));
        stage.setScene(scene);
        stage.show();
        screens.push(new MainMenu(screens));
        step();
    }

    private void step() {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(300));
        wait.setOnFinished(e -> {
            try {
                switch (step) {
                    case 0 -> {
                        MainMenu menu = (MainMenu) screens.peek();
                        check("the menu offers Tutorial", menu.indexOf("Tutorial") >= 0);
                        int to = menu.indexOf("Tutorial");
                        for (int i = 0; i < to; i++) screens.handleKey(key(KeyCode.DOWN));
                        screens.handleKey(key(KeyCode.ENTER));
                    }
                    case 1 -> {
                        check("Tutorial opens a gameplay screen", screens.peek() instanceof GameplayScreen);
                        if (!(screens.peek() instanceof GameplayScreen g)) { finish(); return; }
                        check("and it is the tutorial, not a generated run", g.isTutorial());
                        check("starting at tutorial 1", g.levelNumber() == 1);
                        check("with a hand-built level: 20 rows, where a generated one is 14",
                                g.mapHeightCells() == Tutorial.H);
                        check("and the level has a floor the player is standing on",
                                g.playerIsSupported());
                    }
                    default -> { }
                }
            } catch (Exception ex) {
                check("step " + step + " exception: " + ex.getMessage(), false);
                finish();
                return;
            }
            step++;
            if (step > 1) finish(); else step();
        });
        wait.play();
    }

    private javafx.scene.input.KeyEvent key(KeyCode code) {
        return new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false);
    }

    private void finish() {
        System.out.println(failures == 0 ? "SUCCESS: the tutorial is reachable and is the tutorial"
                                         : "FAILURE: " + failures + " check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) { launch(args); }
}
