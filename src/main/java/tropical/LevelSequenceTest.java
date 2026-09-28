package tropical;
import tropical.engine.*;

/**
 * LevelSequenceTest - the levels the GAME actually builds.
 *
 * Every other scaling check uses synthetic seeds. The game uses 1000 + levelNum
 * and now passes levelNum through, so this is the sequence a player walks: if a
 * level in it is not completable, it is not a test finding, it is a wall in the
 * shipped game.
 */
public class LevelSequenceTest {
    static int failures = 0;
    static void check(String n, boolean ok) { check(n, ok, ""); }

    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        System.out.println("The game's own level sequence (seed 1000+levelNum, levelNum passed)");
        System.out.println("==================================================================");
        int bad = 0;
        StringBuilder fails = new StringBuilder();
        int[] featureTotals = new int[3];
        for (int lv = 1; lv <= 40; lv++) {
            LevelGen g = new LevelGen(60, 14, 1000L + lv, lv);
            LevelMap m = g.generate();
            if (!LevelValidator.validateGenerated(g, 30.0)) {
                bad++;
                fails.append("L").append(lv).append(" ");
            }
            featureTotals[0] += m.enemies.size();
            featureTotals[1] += m.doors.size();
            if (hasWater(m)) featureTotals[2]++;
        }
        check("sequence: every level a player walks is completable", bad == 0,
            (40 - bad) + "/40" + (bad == 0 ? "" : "  failing: " + fails));
        System.out.printf("  over 40 levels: %d enemies, %d doors, %d levels with water%n",
            featureTotals[0], featureTotals[1], featureTotals[2]);

        // difficulty has to actually rise, or the scaling is inert again
        int early = 0, late = 0;
        for (int lv = 1; lv <= 10; lv++) early += new LevelGen(60, 14, 1000L + lv, lv).generate().enemies.size();
        for (int lv = 31; lv <= 40; lv++) late += new LevelGen(60, 14, 1000L + lv, lv).generate().enemies.size();
        check("sequence: enemies increase with the level number", late > early,
            String.format("levels 1-10: %d, levels 31-40: %d", early, late));

        int earlyPlat = 0, latePlat = 0;
        for (int lv = 1; lv <= 10; lv++) earlyPlat += platforms(new LevelGen(60, 14, 1000L + lv, lv).generate());
        for (int lv = 31; lv <= 40; lv++) latePlat += platforms(new LevelGen(60, 14, 1000L + lv, lv).generate());
        check("sequence: platform climbs increase with the level number", latePlat > earlyPlat,
            String.format("levels 1-10: %d cells, levels 31-40: %d", earlyPlat, latePlat));
        // The seam the game actually uses. This is the invariant that had no test when it broke:
        // the game built its levels through the 3-arg constructor, so the level number never
        // reached the generator and nothing in play scaled. Asserted here against the feature
        // counts the generator produces, not against the constructor call.
        // A DIFFERENTIAL over the same seeds: both sides build the same levels, and the only
        // difference is whether the level number reaches the generator. The first version of
        // this compared level 1-10 against 31-40 through forLevel alone and passed even with
        // the level number dropped - because the seed (1000 + levelNum) still varied, and a
        // per-level enemy count is a random roll, so the sums differed anyway. Measurement
        // without a control measured nothing.
        int throughFactory = 0, throughOldCall = 0;
        for (int lv = 21; lv <= 60; lv++) {
            throughFactory += LevelGen.forLevel(lv).generate().enemies.size();
            throughOldCall += new LevelGen(60, 14, 1000L + lv).generate().enemies.size();
        }
        check("wiring: forLevel scales difficulty, the 3-arg call it replaced does not",
            throughFactory > throughOldCall,
            String.format("same 40 seeds: %d enemies with the level number, %d without",
                throughFactory, throughOldCall));

        check("sequence: level 1 has no climbs (its walk is untouched)", 
            platforms(new LevelGen(60, 14, 1001L, 1).generate()) == 0);

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static boolean hasWater(LevelMap m) {
        for (int r = 0; r < 14; r++) for (int c = 0; c < 60; c++) if (m.cell(r, c) == '~') return true;
        return false;
    }

    static int platforms(LevelMap m) {
        int n = 0;
        for (int r = 5; r < 12; r++)
            for (int c = 1; c < 59; c++)
                if (m.cell(r, c) == '#' && m.cell(r + 1, c) == ' ' && m.cell(r + 2, c) == '#') n++;
        return n;
    }
}
