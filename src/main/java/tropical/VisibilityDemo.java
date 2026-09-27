package tropical;

import tropical.engine.Visibility;

/**
 * VisibilityDemo - walks a small dungeon and prints what the light reveals, so
 * the fog of war can be judged without a window.
 *
 * The explorer is greedy: prefer an adjacent unexplored tile, otherwise the
 * least-visited one. It knows nothing about the map it has not seen, exactly
 * like a player, and the frames show the difference between what is lit now and
 * what is only remembered.
 */
public class VisibilityDemo {
    static final int COLS = 42, ROWS = 14;
    static final double RADIUS = 5.5;

    static boolean[][] solid = new boolean[ROWS][COLS];

    static void carveRect(int c0, int r0, int c1, int r1) {
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                if (r >= 0 && r < ROWS && c >= 0 && c < COLS) solid[r][c] = false;
    }

    /** One-tile-wide L corridor between two points, horizontal leg first. */
    static void carveCorridor(int c0, int r0, int c1, int r1) {
        for (int c = Math.min(c0, c1); c <= Math.max(c0, c1); c++) solid[r0][c] = false;
        for (int r = Math.min(r0, r1); r <= Math.max(r0, r1); r++) solid[r][c1] = false;
    }

    static void print(Visibility v, int ex, int ey, String label) {
        System.out.println("  " + label);
        for (int r = 0; r < ROWS; r++) {
            StringBuilder line = new StringBuilder("  ");
            for (int c = 0; c < COLS; c++) {
                char ch;
                if (c == ex && r == ey) ch = '@';
                else if (!v.isExplored(c, r)) ch = ' ';
                else if (v.isVisible(c, r)) ch = solid[r][c] ? '#' : '.';
                else ch = solid[r][c] ? 'x' : ',';
                line.append(ch);
            }
            System.out.println(line);
        }
    }

    public static void main(String[] args) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) solid[r][c] = true;

        carveRect(2, 2, 11, 6);        // west room
        carveRect(17, 3, 27, 9);       // middle room
        carveRect(31, 2, 39, 7);       // east room
        carveCorridor(11, 4, 17, 6);   // west -> middle
        carveCorridor(27, 6, 31, 4);   // middle -> east
        carveRect(20, 3, 22, 5);       // a pillar block inside the middle room

        Visibility v = new Visibility(COLS, ROWS);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) v.setWall(c, r, solid[r][c]);

        int[][] lastVisit = new int[ROWS][COLS];
        for (int[] row : lastVisit) java.util.Arrays.fill(row, -1);
        int[] dc = {1, -1, 0, 0}, dr = {0, 0, 1, -1};
        int ex = 4, ey = 4;
        lastVisit[ey][ex] = 0;
        v.compute(ex, ey, RADIUS);
        print(v, ex, ey, "start: one room lit, everything else unknown");

        int step = 0;
        while (step < 1200) {
            int bx = -1, by = -1;
            double best = -1e9;
            for (int i = 0; i < 4; i++) {
                int nx = ex + dc[i], ny = ey + dr[i];
                if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS || solid[ny][nx]) continue;
                // Prefer unexplored; among equals, prefer the one visited
                // longest ago. A plain visit COUNT ping-pongs between two tiles
                // forever (each move makes the tile you just left attractive
                // again) and the map stayed half-dark - which is exactly the
                // stall the first run of this demo showed.
                double score = (v.isExplored(nx, ny) ? 0 : 1000)
                             + (step - lastVisit[ny][nx]) * 0.1;
                if (score > best) { best = score; bx = nx; by = ny; }
            }
            if (bx < 0) break;
            ex = bx; ey = by;
            lastVisit[ey][ex] = step;
            v.compute(ex, ey, RADIUS);
            step++;
        }

        print(v, ex, ey, "after " + step + " steps: lit where the eye is, dim where memory is");
        // Two numbers, because the first one alone reads as a failure. Rock
        // beyond the reach of any eye is never explored and never should be, so
        // the honest metric is how much of the WALKABLE map was found.
        int walkable = 0, walkableSeen = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (solid[r][c]) continue;
                walkable++;
                if (v.isExplored(c, r)) walkableSeen++;
            }
        }
        System.out.printf("  explored %d/%d walkable tiles (%.0f%%), %d tiles lit right now%n",
            walkableSeen, walkable, 100.0 * walkableSeen / walkable, v.visibleCount());
        System.out.printf("  explored %.0f%% of the whole grid (the rest is rock no eye can reach)%n",
            v.exploredFraction() * 100);
        System.out.println("  legend:  @ eye   . lit floor   # lit wall   , remembered floor"
            + "   x remembered wall   (blank) unknown");
    }
}
