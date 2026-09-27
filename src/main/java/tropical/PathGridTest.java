package tropical;
import tropical.engine.*;

/**
 * PathGridTest - routes, optimality, and the two searches agreeing.
 *
 * The interesting check is not "does it find a path" - almost anything finds a
 * path. It is that A* and the flow field, which are different algorithms with
 * different data structures, produce the SAME cost for the same pair on hundreds
 * of layouts. When two independent searches agree on a number that a third
 * formula predicts exactly (the octile distance in open ground), the number is
 * probably right.
 */
public class PathGridTest {
    static int failures = 0;

    static void check(String n, boolean ok) { check(n, ok, ""); }

    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    static int octile(int dx, int dy) {
        int ddx = Math.abs(dx), ddy = Math.abs(dy);
        return PathGrid.COST_DIAGONAL * Math.min(ddx, ddy)
             + PathGrid.COST_STRAIGHT * Math.abs(ddx - ddy);
    }

    /** Every step adjacent, walkable, and no corner-cutting. */
    static boolean stepsAreLegal(PathGrid g, int[] path) {
        if (path == null || path.length == 0) return false;
        for (int i = 0; i < path.length; i++) {
            int c = g.col(path[i]), r = g.row(path[i]);
            if (g.isBlocked(c, r)) return false;
            if (i == 0) continue;
            int pc = g.col(path[i - 1]), pr = g.row(path[i - 1]);
            int dc = Math.abs(c - pc), dr = Math.abs(r - pr);
            if (dc > 1 || dr > 1 || (dc == 0 && dr == 0)) return false;
            if (dc == 1 && dr == 1 && (g.isBlocked(c, pr) || g.isBlocked(pc, r))) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println("PathGrid - A* and flow fields over a tile grid");
        System.out.println("=============================================");

        // --- 1. open ground: the cost is predictable, so check it ----------
        PathGrid g = new PathGrid(40, 40);
        int bad = 0;
        for (int dc = -8; dc <= 8; dc += 4) {
            for (int dr = -8; dr <= 8; dr += 4) {
                int c1 = 20 + dc, r1 = 20 + dr;
                int[] path = g.findPath(20, 20, c1, r1);
                if (path == null || g.lastCost() != octile(dc, dr) || !stepsAreLegal(g, path)) bad++;
            }
        }
        check("open ground: A* cost equals the octile distance for 25 offsets", bad == 0,
            bad + " wrong");
        int[] path = g.findPath(20, 20, 28, 26);
        check("open ground: the route starts at the start and ends at the goal",
            path != null && path[0] == g.index(20, 20)
                && path[path.length - 1] == g.index(28, 26));
        check("open ground: no step passes through a wall or cuts a corner",
            stepsAreLegal(g, path));

        // --- 2. around a wall ---------------------------------------------
        g = new PathGrid(30, 20);
        for (int r = 0; r < 15; r++) g.setBlocked(15, r, true);      // wall with a gap at the bottom
        int[] around = g.findPath(5, 5, 25, 5);
        check("wall: a route exists around the end of the wall", around != null);
        check("wall: the detour costs more than the straight line",
            g.lastCost() > octile(20, 0), String.format("%d > %d", g.lastCost(), octile(20, 0)));
        check("wall: the route is legal", stepsAreLegal(g, around));
        check("wall: the route actually crosses the gap column",
            crossesColumn(around, g, 15));

        // --- 3. sealed box -------------------------------------------------
        g = new PathGrid(21, 21);
        for (int c = 8; c <= 12; c++) { g.setBlocked(c, 8, true); g.setBlocked(c, 12, true); }
        for (int r = 8; r <= 12; r++) { g.setBlocked(8, r, true); g.setBlocked(12, r, true); }
        check("sealed: no route into a closed box", g.findPath(10, 10, 2, 2) == null);
        g.flowTo(2, 2);
        check("sealed: the flow field agrees the box is unreachable",
            g.flowCost(10, 10) == -1 && g.flowCost(2, 2) == 0);

        // --- 4. the corner rule -------------------------------------------
        g = new PathGrid(2, 2);
        g.setBlocked(1, 0, true);
        g.setBlocked(0, 1, true);
        check("corners: a diagonal squeeze between two walls is not a route",
            g.findPath(0, 0, 1, 1) == null, "(0,0) -> (1,1) through a diagonal gap");

        // --- 5. A* and the flow field must agree ---------------------------
        java.util.Random rng = new java.util.Random(20260927L);
        int agree = 0, disagree = 0, pairs = 0;
        for (int trial = 0; trial < 60; trial++) {
            PathGrid grid = new PathGrid(24, 24);
            for (int c = 0; c < 24; c++) {
                for (int r = 0; r < 24; r++) {
                    if (rng.nextDouble() < 0.24) grid.setBlocked(c, r, true);
                }
            }
            for (int k = 0; k < 8; k++) {
                int c0 = rng.nextInt(24), r0 = rng.nextInt(24);
                int c1 = rng.nextInt(24), r1 = rng.nextInt(24);
                if (grid.isBlocked(c0, r0) || grid.isBlocked(c1, r1)) continue;
                pairs++;
                int[] p = grid.findPath(c0, r0, c1, r1);
                int aCost = (p == null) ? -1 : grid.lastCost();
                grid.flowTo(c1, r1);
                int fCost = grid.flowCost(c0, r0);
                if (aCost == fCost) agree++; else disagree++;
            }
        }
        check("agreement: A* and the flow field return the same cost everywhere",
            disagree == 0, String.format("%d agree, %d disagree over %d pairs", agree, disagree, pairs));
        check("agreement: the sample is large enough to mean something", pairs > 250,
            pairs + " reachable pairs");

        // --- 6. walking the field ------------------------------------------
        PathGrid w = new PathGrid(40, 30);
        for (int r = 0; r < 30; r++) if (r % 7 != 3) w.setBlocked(12, r, true);
        w.flowTo(35, 25);
        int[] walked = w.flowPath(2, 2);
        int[] astar = w.findPath(2, 2, 35, 25);
        check("flow: the field's route is legal", stepsAreLegal(w, walked));
        check("flow: the field's route costs what A* costs",
            walked != null && astar != null && w.flowCost(2, 2) == w.lastCost(),
            String.format("flow %d, A* %d", w.flowCost(2, 2), w.lastCost()));
        check("flow: the route ends at the target",
            walked != null && walked[walked.length - 1] == w.index(35, 25));

        // --- 7. the map can change under it --------------------------------
        PathGrid d = new PathGrid(30, 12);
        for (int r = 1; r < 12; r++) d.setBlocked(14, r, true);   // wall, gap only at row 0
        int before = d.findPath(2, 5, 26, 5) == null ? -1 : d.lastCost();
        d.setBlocked(14, 0, true);                                // seal the gap
        int sealed = d.findPath(2, 5, 26, 5) == null ? -1 : d.lastCost();
        d.setBlocked(14, 0, false);                               // reopen it
        int after = d.findPath(2, 5, 26, 5) == null ? -1 : d.lastCost();
        check("dynamic: a wall that moves changes the route",
            before > 0 && sealed == -1 && after == before,
            String.format("before=%d sealed=%d after=%d", before, sealed, after));

        // --- 8. edge cases -------------------------------------------------
        g = new PathGrid(10, 10);
        check("edge: start equals goal is a zero-cost path",
            g.findPath(3, 3, 3, 3) != null && g.lastCost() == 0);
        check("edge: out of bounds is no path", g.findPath(-1, 0, 5, 5) == null);
        g.setBlocked(5, 5, true);
        check("edge: a blocked goal is no path", g.findPath(1, 1, 5, 5) == null);
        new PathGrid(1, 1).findPath(0, 0, 0, 0);

        // --- 9. cost -------------------------------------------------------
        PathGrid big = new PathGrid(60, 60);
        java.util.Random r2 = new java.util.Random(7L);
        for (int c = 0; c < 60; c++) {
            for (int r = 0; r < 60; r++) if (r2.nextDouble() < 0.2) big.setBlocked(c, r, true);
        }
        long t0 = System.nanoTime();
        int iters = 500;
        for (int i = 0; i < iters; i++) big.findPath(1, 1, 58, 58);
        double msPath = (System.nanoTime() - t0) / 1e6 / iters;

        t0 = System.nanoTime();
        int fields = 100;
        for (int i = 0; i < fields; i++) big.flowTo(30, 30);
        double msField = (System.nanoTime() - t0) / 1e6 / fields;

        big.flowTo(30, 30);
        t0 = System.nanoTime();
        int reads = 200000;
        int sink = 0;
        for (int i = 0; i < reads; i++) sink += big.flowNext(i % 60, (i / 60) % 60);
        double nsRead = (System.nanoTime() - t0) / (double) reads;
        check("cost: A* across a 60x60 maze stays under 1ms", msPath < 1.0,
            String.format("%.3f ms/path", msPath));
        check("cost: a whole-map flow field stays under 2ms", msField < 2.0,
            String.format("%.3f ms/field", msField));
        check("cost: reading a step from the field is a few nanoseconds", nsRead < 200,
            String.format("%.1f ns/read (sink=%d)", nsRead, sink));

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static boolean crossesColumn(int[] path, PathGrid g, int col) {
        if (path == null) return false;
        for (int node : path) if (g.col(node) == col) return true;
        return false;
    }
}
