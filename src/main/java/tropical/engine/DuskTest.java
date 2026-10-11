package tropical.engine;

/**
 * The sun goes down over the run, and the run ends when it has.
 *
 * <p><b>FOUND BY A MUTATION NOTHING NOTICED.</b> `LevelGen.FINAL_LEVEL` from 40 to 2 - a run of two levels instead
 * of forty - came back NOT CAUGHT from every suite, and the run's length is load-bearing: the code's own comment
 * says "AND THIS IS THE WAY HOME. The run has a length and its end is the dusk - the sun the whole climb has been
 * sinking towards." Nothing asserted that the sun is anywhere near down when the game decides you are home.
 *
 * <p><b>RELATIONAL, NOT A CONSTANT.</b> Comparing `LEVELS_TO_DUSK` to `FINAL_LEVEL` would be a tautology - one is
 * defined as the other - and would catch nothing. What is asserted is the SHAPE of the descent: it starts in
 * daylight, it arrives at night, and it gets there by sinking rather than by jumping.
 */
public class DuskTest {
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

        double first = tropical.GameplayScreen.duskFor(1);
        verdict("the first level is full daylight (" + first + ")", first == 0.0);

        double last = tropical.GameplayScreen.duskFor(tropical.engine.LevelGen.FINAL_LEVEL);
        verdict("and the level that ends the run is essentially night (" + String.format("%.3f", last)
                + " at level " + tropical.engine.LevelGen.FINAL_LEVEL + ")", last >= 0.95);

        double before = tropical.GameplayScreen.duskFor(tropical.engine.LevelGen.FINAL_LEVEL - 1);
        verdict("and it got there by sinking, not by jumping (" + String.format("%.3f", before) + " the level before)",
                before < last);

        // The descent has to be monotonic, or a level could go backwards into daylight.
        boolean sinks = true;
        for (int n = 2; n <= tropical.engine.LevelGen.FINAL_LEVEL; n++) {
            if (tropical.GameplayScreen.duskFor(n) < tropical.GameplayScreen.duskFor(n - 1)) sinks = false;
        }
        verdict("and it never rises again", sinks);

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
