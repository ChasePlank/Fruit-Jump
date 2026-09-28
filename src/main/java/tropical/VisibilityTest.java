package tropical;
import tropical.engine.*;

/**
 * VisibilityTest - line of sight, light falloff, and dungeon memory.
 *
 * The first check is the important one: an open room must be fully lit inside the
 * radius. Every octant bug worth making shows up there - a transposed transform
 * leaves a wedge dark, a wrong slope sign hides a quadrant - and none of those
 * are visible in a shadow test, because a shadow test passes just as happily
 * when the light does not reach at all.
 */
public class VisibilityTest {
    static int failures = 0;

    static void check(String n, boolean ok) { check(n, ok, ""); }

    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    static Visibility open(int cols, int rows) {
        return new Visibility(cols, rows);      // no walls
    }

    /** A room with walls on every edge. */
    static Visibility room(int cols, int rows) {
        Visibility v = new Visibility(cols, rows);
        for (int c = 0; c < cols; c++) { v.setWall(c, 0, true); v.setWall(c, rows - 1, true); }
        for (int r = 0; r < rows; r++) { v.setWall(0, r, true); v.setWall(cols - 1, r, true); }
        return v;
    }


    /**
     * The narrow form of this suite's old trap, and the mirror image of it.
     *
     * A tile asserted NOT visible for a reason - a pillar, a wall, a sealed box - must also
     * be IN RANGE. Otherwise the check passes because the light never reached it at all,
     * and the occlusion it claims to test is doing nothing. That is a false green, and this
     * suite produced exactly that once with a radius that did not reach its target.
     *
     * (The visible-direction version of the trap is self-guarding: a radius too small makes
     * the tile dark, and "it is visible" fails loudly.)
     */
    static void fixtureInRange(String label, Visibility v, int cx, int cy, int tx, int ty, int radius) {
        double d = Math.hypot(tx - cx, ty - cy);
        check("fixture: " + label + " is inside the radius, so occlusion is what hides it",
            d <= radius, String.format("distance %.1f vs radius %d", d, radius));
    }

    public static void main(String[] args) {
        System.out.println("Visibility - line of sight and dungeon memory");
        System.out.println("============================================");

        // --- 1. open room: the octant smoke test ---------------------------
        Visibility v = open(31, 31);
        v.compute(15, 15, 10);
        int missed = 0, tooFar = 0;
        for (int r = 0; r < 31; r++) {
            for (int c = 0; c < 31; c++) {
                double d = Math.hypot(c - 15, r - 15);
                boolean vis = v.isVisible(c, r);
                if (d <= 9.5 && !vis) missed++;
                if (d > 10.5 && vis) tooFar++;
            }
        }
        check("open room: everything inside the radius is lit", missed == 0,
            missed + " tile(s) dark inside the radius");
        check("open room: nothing outside the radius is lit", tooFar == 0,
            tooFar + " tile(s) lit beyond the radius");
        check("open room: the eye tile is lit", v.lightAt(15, 15) == 1.0);

        // --- 2. a wall pillar casts a shadow -------------------------------
        v = open(31, 31);
        v.setWall(10, 15, true);
        v.compute(5, 15, 12);
        check("pillar: the tile in front of the wall is visible", v.isVisible(9, 15));
        fixtureInRange("the tile directly behind the pillar", v, 5, 15, 11, 15, 12);
        fixtureInRange("the tile two tiles behind it", v, 5, 15, 13, 15, 12);
        check("pillar: the tiles directly behind the wall are not",
            !v.isVisible(11, 15) && !v.isVisible(13, 15),
            String.format("(11,15)=%b (13,15)=%b", v.isVisible(11, 15), v.isVisible(13, 15)));
        check("pillar: well off the shadow line stays visible", v.isVisible(11, 9),
            "(11,9) is 6 tiles above the shadow");

        // --- 3. a wall with a gap: see through the gap only ---------------
        v = open(41, 21);
        for (int r = 0; r < 21; r++) if (r != 10) v.setWall(20, r, true);
        v.compute(5, 10, 25);
        check("gap: the tile beyond the gap is visible", v.isVisible(25, 10));
        fixtureInRange("the tile behind the solid wall", v, 5, 10, 25, 4, 25);
        check("gap: the tile beyond the solid wall is not", !v.isVisible(25, 4),
            String.format("(25,4) behind wall row 4"));

        // --- 4. a sealed box ---------------------------------------------
        v = new Visibility(21, 21);
        for (int c = 8; c <= 14; c++) { v.setWall(c, 8, true); v.setWall(c, 12, true); }
        for (int r = 8; r <= 12; r++) { v.setWall(8, r, true); v.setWall(14, r, true); }
        v.compute(11, 10, 15);
        check("box: the interior is visible", v.isVisible(10, 10) && v.isVisible(13, 10));
        fixtureInRange("the box's far corner", v, 11, 10, 6, 6, 15);
        fixtureInRange("the box's other far corner", v, 11, 10, 16, 16, 15);
        fixtureInRange("the tile above the box", v, 11, 10, 11, 5, 15);
        check("box: nothing outside a sealed box is visible",
            !v.isVisible(6, 6) && !v.isVisible(16, 16) && !v.isVisible(11, 5),
            String.format("(6,6)=%b (16,16)=%b (11,5)=%b",
                v.isVisible(6, 6), v.isVisible(16, 16), v.isVisible(11, 5)));

        // --- 5. light falls off, and never to zero while visible ----------
        v = open(31, 31);
        v.compute(15, 15, 8);
        double near = v.lightAt(16, 15), mid = v.lightAt(19, 15);
        check("falloff: light decreases with distance", near > mid && mid > 0,
            String.format("d1=%.2f d4=%.2f", near, mid));
        boolean rimLit = true;
        for (int i = 0; i < 31; i++) {
            for (int j = 0; j < 31; j++) {
                if (v.isVisible(i, j) && v.lightAt(i, j) <= 0) rimLit = false;
            }
        }
        check("falloff: a visible tile is never pitch black (rim floor)", rimLit,
            "MIN_LIGHT=" + Visibility.MIN_LIGHT);

        // --- 6. memory ----------------------------------------------------
        v = room(41, 21);
        v.compute(4, 10, 4);
        int firstExplored = v.exploredCount();
        check("memory: exploring marks tiles", firstExplored > 0, firstExplored + " tiles");
        check("memory: displayLight is full light where seen", v.displayLight(4, 10) == 1.0);

        v.compute(30, 10, 4);
        check("memory: explored only grows", v.exploredCount() > firstExplored,
            firstExplored + " -> " + v.exploredCount());
        check("memory: the old room is no longer visible", !v.isVisible(4, 10));
        // A relationship, not an equality with the constant. The first version compared
        // displayLight to MEMORY_LIGHT - the constant that defines it - so setting
        // MEMORY_LIGHT to 1.0 (remembered terrain as bright as live sight, which is the whole
        // point of the fog) satisfied it. Mutation found that; it is independent now:
        // remembered must be dimmer than visible, and still not black.
        check("memory: remembered terrain is dimmer than sight, and not black",
            v.displayLight(4, 10) < v.displayLight(30, 10) && v.displayLight(4, 10) > 0.0,
            String.format("remembered=%.2f visible=%.2f",
                v.displayLight(4, 10), v.displayLight(30, 10)));
        check("memory: never-seen tiles stay black", v.displayLight(4, 2) == 0.0,
            String.format("%.2f", v.displayLight(4, 2)));

        v.forget();
        check("memory: forget clears it", v.exploredCount() == 0 && v.displayLight(4, 10) == 0.0);

        // --- 7. edge cases -------------------------------------------------
        v = open(9, 9);
        v.compute(4, 4, 0);
        check("edge: radius 0 lights only the eye tile",
            v.visibleCount() == 1 && v.isVisible(4, 4));
        v.compute(-5, -5, 6);
        check("edge: an out-of-bounds eye lights nothing", v.visibleCount() == 0);
        v.compute(0, 0, 6);
        check("edge: a corner eye does not crash and sees its neighbours",
            v.isVisible(0, 0) && v.isVisible(1, 1));
        new Visibility(1, 1).compute(0, 0, 3);

        // --- 8. lineOfSight helper ----------------------------------------
        v = open(21, 21);
        check("lineOfSight: open ground is clear", v.lineOfSight(2, 10, 18, 10));
        v.setWall(10, 10, true);
        check("lineOfSight: a wall in the way blocks", !v.lineOfSight(2, 10, 18, 10));
        check("lineOfSight: the same wall does not block a clear lane",
            v.lineOfSight(2, 4, 18, 4));

        // --- 9. cost ------------------------------------------------------
        v = room(60, 60);
        for (int c = 4; c < 56; c += 7) for (int r = 4; r < 56; r += 7) v.setWall(c, r, true);
        long t0 = System.nanoTime();
        int iters = 1000;
        for (int i = 0; i < iters; i++) v.compute(30, 30, 12);
        double ms = (System.nanoTime() - t0) / 1e6 / iters;
        check("cost: a 60x60 grid with radius 12 stays under 1ms per frame",
            ms < 1.0, String.format("%.3f ms/frame", ms));
        System.out.printf("  lit tiles at radius 12: %d%n", v.visibleCount());

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
