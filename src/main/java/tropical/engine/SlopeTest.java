package tropical.engine;

/**
 * A ramp holds you while you walk down it, and a steep one slides you.
 *
 * <p><b>MOVED HERE FROM tropical.engine.SelfTest, WHICH THE RELEASE DOES NOT HAVE.</b> The release's mutation list
 * reported "slopes: no snap onto a ramp from a ledge" as NOT CAUGHT - and the behaviour was checked, in a class only
 * one edition can run. That is the fifth check today in that position (the arrow, the boss's volley, the boss's
 * armour, the knockback and the stomp, and this), and the fix is always the same: a class with a main.
 *
 * <p>Slopes had NO checks at all until the sweep found three of their four constants unconstrained. The fixture is
 * measured against the LINE rather than against the snap constant, so a snap of zero fails it.
 */
public class SlopeTest {
    static int failures = 0;
    static int passes = 0;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (ok) passes++; else failures++;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = 0;
        passes = 0;

        slopesHoldYouAndSlideYou();

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }

    /**
     * A ramp holds you while you walk down it, and a steep one slides you.
     *
     * <p><b>SLOPES HAD NO CHECKS AT ALL, and the sweep found three of their four constants unconstrained</b> -
     * `SLOPE_SNAP_DOWN` (14px), `SLOPE_SLIDE_ACC` (900), and `SLOPE_SNAP_UP`, which turns out to be a different
     * finding entirely (see the note on it in World). Walking up and down ramps is a whole mechanic, in the level
     * generator and in the tutorial, and nothing was measuring it.
     *
     * <p><b>MEASURED AGAINST THE LINE, COMPUTED HERE FROM THE ENDPOINTS.</b> The distance from the player's feet to
     * the slope surface is the thing being claimed - "the surface holds you" - and it is measured in pixels rather
     * than against the snap constant, so a snap of zero fails it. On the shipped numbers a walk down the whole ramp
     * stays within 3.6px of the surface.
     */
    static void slopesHoldYouAndSlideYou() {
        // --- a gentle ramp, walked down -------------------------------------
        double x0 = 200, y0 = 240, x1 = 400, y1 = 300;      // descends to the right, gentle
        World w = new World();
        w.addSlope(x0, y0, x1, y1);
        Physics.Body walker = new Physics.Body(x0 + 20, slopeY(x0 + 20, x0, y0, x1, y1) - 22, 24, 44);
        w.addBody(walker);
        double worst = 0;
        for (int i = 0; i < 90; i++) {
            walker.vx = 120;                                 // walking right, downhill
            w.update(GameLoop.DT);
            double feet = walker.y + walker.hh;
            if (walker.x > x0 + 30 && walker.x < x1 - 30) {
                worst = Math.max(worst, Math.abs(feet - slopeY(walker.x, x0, y0, x1, y1)));
            }
        }
        verdict("slopes: walking down a ramp keeps the feet on it - worst gap "
                + String.format("%.1fpx", worst) + " over the whole walk", worst < 10);

        // --- AND THE SEAM, which is where the snap-down actually lives -------
        // MEASURED FIRST, AND THE MEASUREMENT MOVED THE CHECK. Walking a ramp at 120px/s is held by the PENETRATION
        // branch - gravity puts the feet a little below the surface every frame and it snaps them back - so
        // SLOPE_SNAP_DOWN set to zero changed that walk by three tenths of a pixel and the check could not see it.
        // The gap-snap fires when the feet are ABOVE the surface, and the case that produces that is stepping off a
        // flat edge onto a ramp that starts slightly lower. Measured on this fixture: 3.6px and never airborne with
        // the snap, 13.0px and 46 airborne frames without it. That is the difference between walking onto a ramp
        // and bouncing down it, and it is what the constant's comment is about.
        World seam = new World();
        seam.tiles.add(new Physics.AABB(60, 240, 200, 300));   // a flat ledge, surface at y=240
        seam.addSlope(200, 248, 400, 308);                     // and a ramp starting 8px below its edge
        Physics.Body stepper = new Physics.Body(120, 240 - 22, 24, 44);
        seam.addBody(stepper);
        double seamWorst = 0;
        int airborne = 0;
        for (int i = 0; i < 150; i++) {
            stepper.vx = 200;                                   // the speed the player actually runs
            seam.update(GameLoop.DT);
            if (stepper.x > 205 && stepper.x < 395) {
                double gap = (stepper.y + stepper.hh) - slopeY(stepper.x, 200, 248, 400, 308);
                seamWorst = Math.max(seamWorst, Math.abs(gap));
                if (!stepper.grounded) airborne++;
            }
        }
        verdict("slopes: stepping off a ledge onto a ramp keeps the feet on it - worst gap "
                + String.format("%.1fpx", seamWorst) + ", airborne " + airborne + " frames",
                seamWorst < 8 && airborne == 0);

        // --- and a STEEP one, stood on --------------------------------------
        double sx0 = 200, sy0 = 200, sx1 = 300, sy1 = 420;   // ratio 2.2, past STEEP_RATIO
        World w2 = new World();
        w2.addSlope(sx0, sy0, sx1, sy1);
        Physics.Body slider = new Physics.Body(250, slopeY(250, sx0, sy0, sx1, sy1) - 22, 24, 44);
        w2.addBody(slider);
        w2.update(GameLoop.DT);
        verdict("slopes: a body stood on a steep slope is not moving at first (" + (int) slider.vx + "px/s)",
                Math.abs(slider.vx) < 20);
        for (int i = 0; i < 30; i++) w2.update(GameLoop.DT);   // half a second of nothing but the slope
        verdict("slopes: and half a second later the slope has slid it downhill (" + (int) slider.vx
                + "px/s, downhill is +x here)", slider.vx > 60);
    }
    /** The slope's surface height at x, from its endpoints. The test's own arithmetic, not the engine's. */
    static double slopeY(double x, double x0, double y0, double x1, double y1) {
        return y0 + (y1 - y0) * (x - x0) / (x1 - x0);
    }
}
