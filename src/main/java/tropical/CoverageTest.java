package tropical;

import tropical.engine.LevelGen;
import tropical.engine.LevelMap;

/**
 * Two questions, and they are different questions.
 *
 * <p><b>One: does a generated run contain the game?</b> This project has found the same fault four times - the
 * mechanic exists, is wired, is drawn, and never appears in a run: the boss, the moving platforms, the one-way
 * planks, and (in the sibling repository) an ending that was never written at all. Each was found by hand, one
 * mechanic at a time, days apart. This asks it of every mechanic at once.
 *
 * <p><b>Two: does the tutorial teach what the run contains?</b> And this is the direction that is easy to leave
 * out, because the first question answers "yes" quite happily while a player meets something no level ever
 * introduced. That is exactly what happened here on 9 October: the generator began placing bosses every tenth
 * level and this tutorial had never shown one. The first check would not have noticed.
 *
 * <p>Exits 1 on a fault, 0 otherwise - the gate's rule is that the exit code decides and the text only explains.
 */
public class CoverageTest {

    /** The mechanics a level can contain, in the order {@link #mechanicsIn} fills its flags. */
    static final String[] MECHANICS = {
        "water", "piranhas", "spikes", "cracked floor", "one-way planks",
        "pickups (snack/jar/coin)", "bats", "snakes", "doors or keys",
        "moving platforms", "bosses",
    };

    /**
     * Which of {@link #MECHANICS} this map contains.
     *
     * <p><b>THE CELL SYMBOLS ARE LevelMap's PARSER'S AND ARE NOT GUESSED.</b> A measurement written earlier in the
     * sibling repository used 'P' for a piranha - that is the PLAYER SPAWN - and 'B' for a bat, which is 'b', and
     * duly reported forty levels out of forty containing piranhas and none containing bats. Both numbers were
     * artefacts of the mapping. Read from the switch that decides what a character means.
     */
    static boolean[] mechanicsIn(LevelMap m) {
        boolean[] hit = new boolean[MECHANICS.length];
        for (int r = 0; r < m.heightCells(); r++) {
            for (int c = 0; c < m.widthCells(); c++) {
                switch (m.cell(r, c)) {
                    case '~': case '>': case '<': case 'V': case 'A': hit[0] = true; break;
                    case 'f': hit[1] = true; break;
                    case '^': hit[2] = true; break;
                    case 'C': hit[3] = true; break;
                    case '=': hit[4] = true; break;
                    case 'h': case 'j': case 'o': hit[5] = true; break;
                    case 'b': hit[6] = true; break;
                    case 's': hit[7] = true; break;
                    case 'D': case 'k': hit[8] = true; break;
                    default: break;
                }
            }
        }
        if (!m.movers.isEmpty()) hit[9] = true;
        if (m.hasBoss()) hit[10] = true;
        return hit;
    }

    public static void main(String[] args) {
        int fails = 0;

        // --- the run -------------------------------------------------------------
        // 120 levels rather than 40: the thinnest mechanics are genuinely rare - spikes land on 5 of 120 and planks
        // on 6 - and a check that passes by a margin of one fails when a seed changes. 120 generate in under a
        // tenth of a second, so the width is free.
        final int LEVELS = 120;
        int[] inRun = new int[MECHANICS.length];
        for (int level = 1; level <= LEVELS; level++) {
            boolean[] hit = mechanicsIn(new LevelGen(60, 20, 1000L + level, level).generate());
            for (int i = 0; i < MECHANICS.length; i++) if (hit[i]) inRun[i]++;
        }
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < MECHANICS.length; i++) {
            if (i > 0) run.append(", ");
            run.append(MECHANICS[i]).append(' ').append(inRun[i]);
            if (inRun[i] == 0) fails++;
        }
        System.out.println("  in a generated run (" + LEVELS + " levels): " + run);
        if (fails > 0) System.out.println("  FAIL: " + fails + " mechanic(s) never appear in a run");

        // --- and the teaching ----------------------------------------------------
        int[] inTutorial = new int[MECHANICS.length];
        for (int level = 1; level <= Tutorial.LAST; level++) {
            boolean[] hit = mechanicsIn(Tutorial.map(level));
            for (int i = 0; i < MECHANICS.length; i++) if (hit[i]) inTutorial[i]++;
        }
        StringBuilder untaught = new StringBuilder();
        int untaughtCount = 0;
        for (int i = 0; i < MECHANICS.length; i++) {
            if (inTutorial[i] == 0) {
                if (untaughtCount++ > 0) untaught.append(", ");
                untaught.append(MECHANICS[i]);
            }
        }
        System.out.println("  taught by the tutorial (" + Tutorial.LAST + " levels): "
                + (untaughtCount == 0 ? "every one" : "MISSING " + untaught));
        if (untaughtCount > 0) {
            System.out.println("  FAIL: the run contains " + untaughtCount + " thing(s) no level introduces");
            fails += untaughtCount;
        }

        System.out.println(fails == 0
                ? "SUCCESS: the run contains the game, and the tutorial teaches what the run contains"
                : "FAILURE: " + fails + " coverage fault(s)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
