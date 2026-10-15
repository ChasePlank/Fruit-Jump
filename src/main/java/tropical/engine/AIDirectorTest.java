package tropical.engine;

/**
 * What the AI Director actually does, pinned before anybody decides how much it should steer.
 *
 * <p><b>THIS CLASS EXISTED WITH NO TEST AT ALL, and that is why this file is here rather than a wiring change.</b>
 * The README describes the director as the one item in the "built but not wired" list whose last step is "a design
 * decision about how much it steers, not just a wire" - so wiring it is not mine to do. But the director's behaviour
 * is fully specified in its own code, and NOTHING PINNED ANY OF IT: it is the only engine class with a state machine
 * and a documented invariant and no suite.
 *
 * <p>THE INVARIANT WORTH THE FILE. `update()`'s comment says, in as many words:
 * <i>"Decay must be time-based: frustration *= exp(-k * dt). (A per-tick constant like 0.95 decays 0.95^60 ≈ 0.05
 * per second at 60fps - events vanish before they can accumulate.)"</i> That is a claim about the code, written
 * beside the code, and until now NOTHING CHECKED IT. `decayDoesNotDependOnTheStepSize` below is that claim as a
 * test: the same second advanced in one step and in sixty must leave the same frustration. A per-tick constant
 * fails it by a factor of fifteen, which is the bug the comment says was avoided.
 *
 * <p>AND THE NUMBERS ARE PRINTED, because they are the read somebody needs to make the steering decision: over a
 * sixty-second run the director's spawn multiplier stays between 0.39 and 1.40 - MEASURED, not guessed: this file
 * printed 0.20 to 1.50 before it was run, and 0.39 to 1.40 is what the run says. That range is what "how much it steers"
 * means in practice.
 */
public class AIDirectorTest {
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

        itStartsQuietInBuild();
        buildPeakRelaxIsEightFiveFour();
        decayDoesNotDependOnTheStepSize();
        frustrationOpensAMercyWindowAndItCloses();
        theMultiplierStaysInsideItsClamp();
        theTwoHelpersUseTheMultiplier();

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }

    /** A fresh director is in BUILD with no intensity, whatever the initial field happens to say. */
    static void itStartsQuietInBuild() {
        AIDirector d = new AIDirector();
        verdict("a new director is in BUILD", d.getState() == AIDirector.State.BUILD);
        verdict("and has pushed nothing yet (intensity 0)", d.getIntensity() == 0.0);
        verdict("and is not holding a mercy window", !d.isMercyActive());
    }

    /**
     * The state machine's timing: BUILD 8s, PEAK 5s, RELAX 4s, then back to BUILD. dt is a quarter of a second
     * because that is exact in binary, so the boundaries are not floating-point luck.
     */
    static void buildPeakRelaxIsEightFiveFour() {
        AIDirector d = new AIDirector();
        step(d, 0.25, 31);   // 7.75s
        verdict("still BUILD at 7.75s", d.getState() == AIDirector.State.BUILD);
        step(d, 0.25, 1);    // 8.00s
        verdict("PEAK at 8.00s", d.getState() == AIDirector.State.PEAK);
        step(d, 0.25, 19);   // 4.75s into PEAK
        verdict("still PEAK at 4.75s into it", d.getState() == AIDirector.State.PEAK);
        step(d, 0.25, 1);    // 5.00s
        verdict("RELAX at 5.00s into PEAK", d.getState() == AIDirector.State.RELAX);
        step(d, 0.25, 15);   // 3.75s into RELAX
        verdict("still RELAX at 3.75s into it", d.getState() == AIDirector.State.RELAX);
        step(d, 0.25, 1);    // 4.00s
        verdict("BUILD again at 4.00s into RELAX", d.getState() == AIDirector.State.BUILD);
    }

    /**
     * THE INVARIANT ITS OWN COMMENT CLAIMS: one second of decay is one second of decay, however it is sliced.
     *
     * <p>A per-tick constant would put these two an order of magnitude apart, which is exactly what the comment
     * beside the code says was avoided and what nothing was checking.
     */
    static void decayDoesNotDependOnTheStepSize() {
        AIDirector one = new AIDirector();
        one.onPlayerDamaged(1.0);
        one.update(0.0);              // prime: bank the event, advance no time
        one.update(1.0);              // then one second in a single step

        AIDirector sixty = new AIDirector();
        sixty.onPlayerDamaged(1.0);
        sixty.update(0.0);
        for (int i = 0; i < 60; i++) sixty.update(1.0 / 60.0);   // the same second, in sixty steps

        double a = one.getFrustration();
        double b = sixty.getFrustration();
        verdict(String.format("one second of decay is the same in 1 step and in 60 (%.6f vs %.6f)", a, b),
                Math.abs(a - b) < 1e-9);
        verdict("and that second actually decayed it (it is below what was banked)",
                a < 0.3 && a > 0.15);
    }

    /** Frustration over the threshold opens the mercy window; three seconds later it closes and BUILD resumes. */
    static void frustrationOpensAMercyWindowAndItCloses() {
        AIDirector d = new AIDirector();
        for (int i = 0; i < 3; i++) d.onPlayerDamaged(1.0);   // 0.9 banked, over the 0.7 threshold
        d.update(0.0);
        verdict("frustration past the threshold opens RECOVER", d.getState() == AIDirector.State.RECOVER);
        verdict("and says a mercy window is active", d.isMercyActive());
        verdict("with the full three seconds on the timer", Math.abs(d.getMercyTimer() - 3.0) < 1e-9);
        step(d, 0.25, 11);   // 2.75s
        verdict("still holding at 2.75s", d.isMercyActive());
        step(d, 0.25, 1);    // 3.00s
        verdict("released at 3.00s", !d.isMercyActive());
        verdict("and back to BUILD", d.getState() == AIDirector.State.BUILD);
    }

    /** The multiplier is clamped, and a long run says where it actually sits - which is the steering question. */
    static void theMultiplierStaysInsideItsClamp() {
        AIDirector d = new AIDirector();
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = 0; i < 240; i++) {          // sixty seconds at a quarter-second step
            d.update(0.25);
            lo = Math.min(lo, d.getSpawnMultiplier());
            hi = Math.max(hi, d.getSpawnMultiplier());
        }
        verdict("never below the 0.2 floor", lo >= 0.2 - 1e-9);
        verdict("never above the 2.0 ceiling", hi <= 2.0 + 1e-9);
        System.out.printf("      over a 60s run the multiplier ranges %.2f to %.2f%n", lo, hi);
        verdict("and it does move - the range is not a constant", hi - lo > 0.2);
    }

    /** The two helpers are the multiplier, applied - and in RECOVER the floor is what a caller gets. */
    static void theTwoHelpersUseTheMultiplier() {
        AIDirector d = new AIDirector();
        for (int i = 0; i < 3; i++) d.onPlayerDamaged(1.0);
        d.update(0.0);
        verdict("in RECOVER the multiplier is the floor, 0.2", Math.abs(d.getSpawnMultiplier() - 0.2) < 1e-9);
        verdict("so a base chance of 1.0 becomes 0.2", Math.abs(d.modifySpawnChance(1.0) - 0.2) < 1e-9);
        verdict("and a base count of 5 becomes 1", d.modifyEnemyCount(5) == 1);
    }

    static void step(AIDirector d, double dt, int times) {
        for (int i = 0; i < times; i++) d.update(dt);
    }
}
