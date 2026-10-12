package tropical;

import tropical.engine.DoorStressTest;
import tropical.engine.LevelGen;
import tropical.engine.LevelMap;

/**
 * Are the levels the generator makes actually playable?
 *
 * <p><b>WRITTEN BECAUSE THIS EDITION REPORTED THE ANSWER AND NEVER ASSERTED IT.</b> `DoorStressTest` walks 100
 * fresh seeds through the validator bot and counts the levels it could not finish, plus door boxes whose geometry
 * has drifted - and its own comment says the box half was "PORTED FROM THE ENGINE'S SUITE ... this release's suite
 * did not have it". But here it is listed in the gate as a REPORT: it prints its numbers and never fails. The
 * engine repository asserts exactly this line - `check("doors: 100 generated levels are all completable by the
 * validator bot", DoorStressTest.runAll() == 0)` - so an uncompletable generated level would have been caught
 * there and shipped from here.
 *
 * <p><b>AND THE GAP WIDTH, WHICH NOTHING HERE ASKED ABOUT AT ALL.</b> The validator bot cannot see it: it falls in
 * an over-wide gap, lands on the spikes two cells down and climbs out, so the level still "completes".
 * COMPLETABLE AND PLAYABLE ARE DIFFERENT PROPERTIES, and only the second one is about walls. The engine checks
 * both, over 100 seeds at level 22; this is that check.
 *
 * <p>The jump reach is not a guess - it is in the generator's own header comment, measured from the engine: jump
 * v0 420, gravity 1200, so apex ~73px, and at 200px/s with ~0.7s of air the distance is ~137px. Four cells is
 * 128px, which clears it. Five is 160px, which does not.
 *
 * <p>Headless: no display, no window.
 */
public class LevelValidityTest {
    static int failures = 0;
    static int passes = 0;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (ok) passes++; else failures++;
    }

    public static void main(String[] args) { System.exit(runAll() == 0 ? 0 : 1); }

    public static int runAll() {
        failures = 0; passes = 0;

        // ---- one: the run is completable, and the doors are the shape they must be ----
        // runAll() returns unfinished levels PLUS door-box faults, and it prints the two separately, so a failure
        // here says which half to look at.
        int faults = DoorStressTest.runAll();
        verdict("100 generated levels are all completable by the validator bot, with every door box on the floor ("
                + faults + " fault(s))", faults == 0);

        // ---- two: and no gap is wider than the jump ----
        final int W = 60, H = 20, JUMPABLE_CELLS = 4;
        int widest = 0, tooWide = 0;
        String where = "";
        for (long seed = 2001; seed <= 2100; seed++) {
            LevelGen gen = new LevelGen(W, H, seed, 22);
            LevelMap m = gen.generate();
            boolean[] gapCol = new boolean[m.widthCells()];
            for (int r = 0; r < m.heightCells(); r++) {
                for (int c = 0; c < m.widthCells(); c++) {
                    char ch = m.cell(r, c);
                    if (ch == '^') gapCol[c] = true;
                    // A FLOODED GAP is a gap; a FLOODED WALK is not. Both are water at the walk's level with a solid
                    // floor under them, so the grid cannot tell them apart, and counting every '~' as a gap made a
                    // flooded crossing read as one 28-cell gap. The walk's own floor data is what distinguishes
                    // them, and only the generator has it.
                    else if (ch == '~' && c < gen.lastPathFloor.length && gen.lastPathFloor[c] < 0) gapCol[c] = true;
                }
            }
            for (int c = 0; c < m.widthCells(); c++) {
                if (!gapCol[c]) continue;
                int start = c;
                while (c < m.widthCells() && gapCol[c]) c++;
                int len = c - start;
                if (len > widest) widest = len;
                if (len > JUMPABLE_CELLS) {
                    tooWide++;
                    if (where.isEmpty()) where = "seed " + seed + " column " + start;
                }
            }
        }
        verdict("the widest gap over 100 level-22 seeds is " + widest + " cells, and jumpable is " + JUMPABLE_CELLS,
                widest > 0 && widest <= JUMPABLE_CELLS);
        verdict("and no generated gap is wider than the jump"
                + (tooWide == 0 ? "" : "  -- " + tooWide + " too wide, first: " + where), tooWide == 0);

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
