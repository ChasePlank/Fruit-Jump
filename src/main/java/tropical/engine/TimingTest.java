package tropical.engine;

/**
 * Two things measured in time: how long a frame the world will believe in, and how long a dead enemy is drawn.
 *
 * <p><b>MOVED HERE FROM tropical.engine.SelfTest, WHICH THE RELEASE DOES NOT HAVE.</b> The release's mutation list
 * reported "loop: the frame clamp stops the world" and "enemy: a death with no fade" as NOT CAUGHT - and both were
 * checked, in a class only one edition can run. Ninth and tenth instances of the same gap.
 *
 * <p>The clamp is the one that matters: `GameLoop.MAX_FRAME` is the spiral-of-death protection, and its check was
 * written after the sweep found the constant could be zeroed with nothing noticing.
 */
public class TimingTest {
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

        aHugeFrameIsClamped();
        theDeathFadeIsVisible();
        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }

    /**
     * A five-second hitch advances the world by a quarter of a second, not by five.
     *
     * <p><b>FOUND BY tools/tautologies.py:</b> `GameLoop.MAX_FRAME` could be set to anything with nothing noticing -
     * and the reason was structural rather than a missing assertion. The constant was used only by `run()`, where
     * the frame time is always DT and the clamp can never fire. The clamp that actually runs in the game was a
     * hard-coded `Math.min(dt, 0.25)` in GameplayScreen, carrying the same number with none of the explanation. So
     * the documented copy was unreachable and the live copy was unexplained.
     *
     * <p><b>THE FIX WAS TWO THINGS, and this check is the second half of it.</b> The screen uses the named constant
     * now, so the two cannot disagree; and `GameLoop.advance(frameTime)` factors out the loop so the clamp is
     * reachable from a test at all. There was nothing to assert before, which is why nothing did.
     *
     * <p>BOTH BOUNDS, on the physics rather than only on a step count, because the failure this protects against is
     * a body moving too far in one frame: clamped, a quarter second of free fall is about 37px and fifteen steps;
     * unclamped, five seconds is fifteen thousand pixels and three hundred steps. And zero - a clamp of nothing -
     * stops the world instead, which the lower bound catches.
     */


    static void aHugeFrameIsClamped() {
        World w = new World();
        Physics.Body p = new Physics.Body(200, 100, 24, 44);
        w.addBody(p);
        GameLoop loop = new GameLoop(w);
        double y0 = p.y;
        int steps = loop.advance(5.0);          // a five-second hitch
        double fell = p.y - y0;
        verdict("a five-second hitch advances the world a quarter second, not five (" + steps + " steps, "
                        + (int) fell + "px of fall)",
                steps >= 1 && steps <= 20 && fell > 5 && fell < 120);

        int one = loop.advance(GameLoop.DT);
        verdict("and an ordinary frame is one step (" + one + ")", one == 1);
    }
    /**
     * The death fade is a moment rather than a frame.
     *
     * <p>The second thing the mutation harness found unverifiable: setting Enemy's DEATH_SECONDS to 0.0 - a death
     * with no fade at all, which is what the game looked like before the fade was written - was caught by nothing.
     */
    static void theDeathFadeIsVisible() {
        Enemy e = new Enemy(0, 0, 24, 24, Enemy.KIND_SPIDER);
        verdict("a live enemy is at full fade", e.deathFade() == 1.0);
        e.dead = true;
        verdict("and on the frame it dies it is still visible, which zero DEATH_SECONDS would break",
                e.deathFade() > 0.0);
        for (int i = 0; i < 240; i++) e.updateAI(1.0 / 60, 0, 0);
        verdict("and it is gone four seconds later", e.deathFade() == 0.0);
    }
}
