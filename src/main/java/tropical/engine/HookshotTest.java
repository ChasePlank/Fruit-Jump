package tropical.engine;

/**
 * A hookshot pull always ends, whichever guard ends it.
 *
 * <p><b>MOVED HERE FROM tropical.engine.SelfTest, WHICH THE RELEASE DOES NOT HAVE.</b> The release's mutation list
 * reported "hookshot: the stall guard switched off" as NOT CAUGHT, and the behaviour was checked, in a class only one
 * edition can run. Thirteenth instance of that gap.
 *
 * <p>What it protects is a SOFT-LOCK: a pull that never ends because the player cannot move and nothing applies the
 * velocity the hook set. Both guards - the stall timer and the hard cap - end a pull, and the check deliberately does
 * not say which one fired, because naming it would pass while the other was broken.
 */
public class HookshotTest {
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

        aHookshotPullAlwaysEnds();

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }

    /**
     * A HOOKSHOT PULL ALWAYS ENDS, however unreachable the anchor.
     *
     * <p>Hookshot carries two failsafes and its comment says why: "found by playtest: firing at a ledge lip anchors
     * somewhere the pull can't reach - the player hangs against geometry with input ignored, unable to counter."
     * That is a SOFT-LOCK, the worst kind of bug this game can have, and the mutation sweep showed nothing was
     * protecting either guard: `MAX_PULL_TIME` and `STALL_TIME` both set to 999 left the gate green at 444
     * passed, 0 failed.
     *
     * <p>Two ways a pull can fail to finish, so two checks:
     *
     * <ul>
     *   <li>the player cannot move at all - the hook sets a velocity and nothing applies it - which is the STALL
     *       case;</li>
     *   <li>the player moves but the anchor is further away than MAX_PULL_TIME at PULL_SPEED allows (1.5s times
     *       500px/s is 750px), which is the HARD CAP case.</li>
     * </ul>
     *
     * <p>Both are "the pull ended", because that is the invariant the soft-lock violates, and neither asserts
     * WHICH guard fired - a test that named the guard would pass while the other one was broken.
     */
    static void aHookshotPullAlwaysEnds() {
        // A STALLED PULL RELEASES FAST, not merely eventually. Asserting only "it ended" does not catch a broken
        // stall guard, because the 1.5s cap ends it anyway - the first version of this passed with STALL_TIME set
        // to 999. What the stall guard is FOR is releasing in a third of a second rather than a second and a
        // half, so the assertion is about how long.
        double stalled = pullSeconds(false, 400);
        // AND THE LOWER BOUND IS THE POINT, which `stalled > 0` did not express: a stall guard that releases on the
        // FIRST frame is not a stall guard, it is a different weapon. Zero satisfied `> 0`, so the whole tolerance
        // could be removed and this check passed - found by tools/tautologies.py, which tests switching a constant
        // OFF as well as up and down. A tenth of a second is well inside any sane tolerance and far outside zero.
        verdict(String.format("a stalled hookshot pull releases in well under the cap (%.2fs, cap is 1.5s)", stalled),
                stalled > 0.1 && stalled < 1.0);

        // AND A PULL THAT IS STILL MAKING PROGRESS STILL ENDS. The anchor has to be further than the loop can
        // walk: 500px/s for six seconds is 3000px, and the first version used 2000, which the player simply
        // reached - so the pull ended by ARRIVAL and the cap was never exercised at all.
        double far = pullSeconds(true, 6000);
        verdict(String.format("a hookshot pull to an anchor beyond reach still ends (%.2fs)", far),
                far > 0 && far < 6.0);
    }

    /** Run a pull for up to six seconds; returns how long it took to end, or -1 if it never did. */
    static double pullSeconds(boolean stepWorld, double anchorX) {
        World w = new World();
        Physics.Body player = new Physics.Body(200, 100, 24, 44);
        w.addBody(player);
        w.playerBody = player;
        tropical.engine.Hookshot hook = new tropical.engine.Hookshot(player);
        hook.state = tropical.engine.Hookshot.State.PULLING;
        hook.anchorX = anchorX;
        hook.anchorY = 100;
        for (double t = 0; t < 6.0; t += GameLoop.DT) {
            if (stepWorld) w.update(GameLoop.DT);
            hook.update(GameLoop.DT, w);
            if (hook.state != tropical.engine.Hookshot.State.PULLING) return t;
        }
        return -1;
    }
}
