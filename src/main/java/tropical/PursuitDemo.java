package tropical;

import tropical.engine.*;

/**
 * PursuitDemo - an enemy that chases what it can SEE, using last hour's
 * Visibility for eyes and PathGrid for legs.
 *
 * This is the integration proof for the two systems together, and it is a
 * behavioural test rather than a unit test: the assertions are "it catches the
 * player in the open" and "it loses the player behind a wall". Both are things
 * that can be true of each module separately and false of them together, which
 * is exactly why it is worth running.
 *
 * The enemy holds a last-known tile while it has line of sight, walks to it with
 * a flow field, and gives up after GIVE_UP frames of seeing nothing - the
 * cheapest enemy that reads as intelligent because it is honest about what it
 * knows.
 */
public class PursuitDemo {
    static final int COLS = 34, ROWS = 13;
    static final int TILES_PER_MOVE = 5;     // 12 tiles/sec at 60fps
    static final int SIGHT_TILES = 9;
    static final int GIVE_UP = 90;           // 1.5s of no sight before idling

    static boolean[][] solid = new boolean[ROWS][COLS];

    static void carve(int c0, int r0, int c1, int r1) {
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                if (r >= 0 && r < ROWS && c >= 0 && c < COLS) solid[r][c] = false;
    }

    static void corridor(int c0, int r0, int c1, int r1) {
        for (int c = Math.min(c0, c1); c <= Math.max(c0, c1); c++) solid[r0][c] = false;
        for (int r = Math.min(r0, r1); r <= Math.max(r0, r1); r++) solid[r][c1] = false;
    }

    public static void main(String[] args) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) solid[r][c] = true;

        carve(1, 1, 11, 6);          // west room
        carve(20, 4, 32, 11);        // east room, lower
        corridor(11, 5, 20, 8);      // the only link, and it bends
        carve(15, 1, 17, 4);         // pillar in the corridor mouth

        PathGrid grid = new PathGrid(COLS, ROWS);
        grid.fromAscii(toAscii());
        Visibility eye = new Visibility(COLS, ROWS);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) eye.setWall(c, r, solid[r][c]);

        // the player walks a circuit through both rooms; the enemy starts nearby
        int[][] circuit = {
            {3, 3}, {8, 3}, {10, 5}, {14, 5}, {17, 7}, {24, 8}, {30, 8},
            {30, 10}, {24, 10}, {17, 9}, {14, 5}, {10, 5}, {8, 3}, {3, 3}
        };
        int pc = 3, pr = 3, leg = 1;
        int ec = 5, er = 5;
        int target = -1;
        int giveUp = 0;
        int moves = 0, catches = 0, lostSight = 0, rebuilds = 0;
        int aimC = -1, aimR = -1, framesSinceRebuild = 999;
        boolean hadSight = false;
        int contiguity = 1;

        for (int frame = 0; frame < 4000; frame++) {
            // --- player follows the circuit, one tile at a time -------------
            if (frame % TILES_PER_MOVE == 0) {
                int tc = circuit[contiguity][0], tr = circuit[contiguity][1];
                if (pc < tc) pc++; else if (pc > tc) pc--;
                else if (pr < tr) pr++; else if (pr > tr) pr--;
                if (pc == tc && pr == tr) contiguity = (contiguity + 1) % circuit.length;
            }

            // --- enemy perception -----------------------------------------
            eye.compute(ec, er, SIGHT_TILES);
            boolean inRange = Math.hypot(pc - ec, pr - er) <= SIGHT_TILES;
            boolean canSee = inRange && eye.isVisible(pc, pr);
            if (canSee) {
                int here = grid.index(pc, pr);
                // Rebuild policy: the target moving ONE tile does not justify a
                // new search. The first run of this demo rebuilt per tile change
                // - 320 rebuilds for 296 moves, i.e. a search for every step -
                // and the enemy's route barely differs, because a field aimed a
                // tile behind the player still walks you at them. Rebuilding
                // only when the target is far from the last field's aim, or when
                // a few frames have passed, is the same chase for a fraction of
                // the work.
                if (target != here
                        && (rebuilds == 0 || framesSinceRebuild >= 20
                            || Math.hypot(pc - aimC, pr - aimR) >= 4)) {
                    target = here;
                    aimC = pc; aimR = pr;
                    framesSinceRebuild = 0;
                    grid.flowTo(pc, pr);          // one field for the whole pursuit
                    rebuilds++;
                } else if (target != here) {
                    target = here;
                }
                framesSinceRebuild++;
                giveUp = GIVE_UP;
                if (!hadSight) contiguity = contiguity;   // sight regained
            } else if (target >= 0 && giveUp > 0) {
                giveUp--;
                if (giveUp == 0) { lostSight++; target = -1; }
            }
            hadSight = canSee;

            // --- enemy moves along the field, one tile at a time ------------
            if (target >= 0 && frame % TILES_PER_MOVE == 0) {
                int next = grid.flowNext(ec, er);
                if (next >= 0) {
                    ec = grid.col(next);
                    er = grid.row(next);
                    moves++;
                }
            }

            if (ec == pc && er == pr) catches++;
            if (frame == 0) print(pc, pr, ec, er, "start: the enemy can see the player");
            if (frame == 700) print(pc, pr, ec, er, "mid: the player has taken the corridor");
        }
        print(pc, pr, ec, er, "end of the run");

        System.out.printf("  %d enemy moves, %d flow-field rebuilds, %d sight lost%n",
            moves, rebuilds, lostSight);
        System.out.println("  legend:  @ player   E enemy   # wall   . floor");

        boolean caught = catches > 0;
        boolean lost = lostSight > 0;
        System.out.println();
        System.out.println((caught ? "PASS" : "FAIL") + "  pursuit: the enemy reaches the player it can see");
        System.out.println((lost ? "PASS" : "FAIL") + "  pursuit: the enemy loses the player behind a wall and gives up");
        System.exit(caught && lost ? 0 : 1);
    }

    static String[] toAscii() {
        String[] out = new String[ROWS];
        for (int r = 0; r < ROWS; r++) {
            StringBuilder b = new StringBuilder();
            for (int c = 0; c < COLS; c++) b.append(solid[r][c] ? '#' : '.');
            out[r] = b.toString();
        }
        return out;
    }

    static void print(int pc, int pr, int ec, int er, String label) {
        System.out.println("  " + label);
        for (int r = 0; r < ROWS; r++) {
            StringBuilder line = new StringBuilder("  ");
            for (int c = 0; c < COLS; c++) {
                char ch = solid[r][c] ? '#' : '.';
                if (c == pc && r == pr) ch = '@';
                if (c == ec && r == er) ch = 'E';
                line.append(ch);
            }
            System.out.println(line);
        }
    }
}
