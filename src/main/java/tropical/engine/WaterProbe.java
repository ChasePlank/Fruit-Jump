package tropical.engine;

/**
 * Does the generator actually produce water - and is it the shape it was built to be?
 *
 * Counting, not reading, is how the release found that three rows of water plus a pool floor never fit in the
 * map and it silently flooded nothing. Counting is also how the upstream port found that every flooded gap in
 * every level was ONE row of water sitting on a row of spikes: the gap-pit pass wrote '^' over the water's
 * second row, and the pool floor under it. The cell count said 17 across 40 levels where two rows over four
 * pools is 34.
 *
 * So the count alone is not enough - it says how much water there is, not whether it is a pool. The shape check
 * below asserts what the flood pass claims to build: every water run is exactly two cells tall with a solid
 * floor under it, and no water cell has a spike in it. That is the check that would have caught the original
 * bug from the number alone, and it is the check that will catch it if the two passes ever disagree again.
 *
 * Not a copy of the release's probe: aside's LevelGen has no forLevel(int) factory, it takes constructor
 * arguments, so this is written against this API.
 *
 * It probes the GAME's geometry, and it says so in its own output. The first version used 60x14 - a height
 * copied from an older comment rather than from the level the game builds, which is 60x20 (GameplayScreen's
 * LEVEL_W/LEVEL_H). Here the answer happened to be the same either way, which is exactly what makes a
 * mismatched probe dangerous: it is one generation change away from reporting a number about a map nobody
 * plays, and nothing in the output would have said so. Printing the geometry is the cheap half of that fix.
 */
public class WaterProbe {

    /** Two rows of water, as the flood pass writes them. */
    private static final int POOL_ROWS = 2;

    public static void main(String[] args) {
        // GameplayScreen builds levels as new LevelGen(LEVEL_W, LEVEL_H, 1000L + levelNum, levelNum).
        int width = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        int height = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        int withWater = 0, total = 0, pools = 0, bad = 0;
        StringBuilder list = new StringBuilder();
        StringBuilder faults = new StringBuilder();

        for (int level = 1; level <= 40; level++) {
            LevelGen gen = new LevelGen(width, height, 1000L + level, level);
            LevelMap m = gen.generate();
            int n = 0;
            for (int r = 0; r < m.height; r++)
                for (int c = 0; c < m.width; c++)
                    if (m.cell(r, c) == '~') n++;
            if (n > 0) { withWater++; list.append(level).append("(").append(n).append(") "); }
            total += n;

            // Shape: read each column top to bottom and measure every water run.
            for (int c = 0; c < m.width; c++) {
                int r = 0;
                while (r < m.height) {
                    if (m.cell(r, c) != '~') { r++; continue; }
                    int start = r;
                    while (r < m.height && m.cell(r, c) == '~') r++;
                    int len = r - start;
                    pools++;
                    // A FLOODED WALK IS ONE ROW AND THAT IS CORRECT. It sits ankle-deep on the walk's own floor,
                    // so the ground under it is the floor the generator carved and the player crosses at walking
                    // height. A pool in a GAP is two rows, because there the water is the penalty for a missed
                    // jump. The two are told apart by the walk's floor data - the same distinction the gap check
                    // needed, for the same reason: once a level could be flooded end to end, "how deep is the
                    // water" stopped being one question.
                    boolean wade = len == 1
                            && c < gen.lastPathFloor.length
                            && gen.lastPathFloor[c] == start + 1
                            && m.cell(start + 1, c) == '#';
                    if (wade) continue;
                    if (len != POOL_ROWS) {
                        bad++;
                        faults.append("\n  level ").append(level).append(" column ").append(c)
                              .append(": water is ").append(len).append(" rows, not ").append(POOL_ROWS);
                    } else if (m.cell(start + len, c) != '#') {
                        bad++;
                        faults.append("\n  level ").append(level).append(" column ").append(c)
                              .append(": no floor under the pool, '").append(m.cell(start + len, c)).append("' instead");
                    }
                }
            }
            // Nothing in the water, and nothing water in the spikes: the two passes
            // must not overlap at all, in either direction.
            for (int r = 0; r < m.height; r++)
                for (int c = 0; c < m.width; c++)
                    if (m.cell(r, c) == '^' && r > 0 && m.cell(r - 1, c) == '~') {
                        bad++;
                        faults.append("\n  level ").append(level).append(" column ").append(c)
                              .append(": spikes directly under water at row ").append(r);
                    }
        }

        System.out.println("probing " + width + "x" + height
                + (args.length == 0 ? " (the game's level size)" : "  <-- NOT the game's 60x20"));
        System.out.println("levels with water: " + withWater + " of 40, " + total + " cells");
        System.out.println("  " + list);
        System.out.println("pools: " + pools + ", each expected to be " + POOL_ROWS
                + " rows with a floor under it (a flooded WALK is one row, ankle-deep, and is not a pool)");
        System.out.println(bad == 0
                ? "SHAPE OK: every pool is " + POOL_ROWS + " rows deep with a floor, none sitting on spikes"
                : "SHAPE FAILURES: " + bad + faults);
        if (bad != 0) System.exit(1);
    }
}
