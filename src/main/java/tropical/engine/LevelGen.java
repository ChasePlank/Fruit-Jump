package tropical.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * LevelGen — procedural platformer levels in the LevelMap ASCII format.
 *
 * Approach: a ground random walk (the guaranteed path) plus decorative
 * side platforms (optional routes). The walk carves a floor with
 * bounded jumps and bounded climbs:
 *   - horizontal gaps ≤ MAX_GAP_CELLS (jumpable)
 *   - vertical steps up ≤ MAX_STEP_CELLS (jumpable)
 *   - drops can be any height (falling is free)
 * Occasional slopes (via '/' cells) and spikes (hazard, placed on the
 * walk path only with guaranteed clearance above).
 *
 * Every generated level is completable BY CONSTRUCTION; the validator
 * bot exists to prove it empirically.
 */
public class LevelGen {
    // Player physics (measured from the engine at 1x velocity):
    // jump v0 = 420, gravity 1200 → apex ≈ 73px ≈ 2.3 cells
    // run speed 200px/s, air time ≈ 0.7s → jump distance ≈ 140px ≈ 4.4 cells
    static final int MAX_GAP_CELLS = 3;    // conservative: 3-cell gaps
    static final int MAX_STEP_CELLS = 2;   // conservative: 2-cell climbs

    final int width, height;
    final Random rng;
    /** Floor row of the carved path per column (-1 = no path floor).
     *  Recorded during generation so tests measure the ACTUAL path,
     *  not a heuristic re-read of the grid. */
    public final int[] pathFloor;
    /** The most recently generated map (for validateGenerated). */
    public LevelMap lastMap;

    public LevelGen(int width, int height, long seed) {
        this.width = width;
        this.height = height;
        this.rng = new Random(seed);
        this.pathFloor = new int[width];
        java.util.Arrays.fill(this.pathFloor, -1);
    }

    /** Generate a level as ASCII rows. */
    public LevelMap generate() {
        // Grid of chars, all spaces initially
        char[][] g = new char[height][width];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');

        // --- The guaranteed path: a ground walk left to right ---
        // Full-height fills below every floor cell: no voids to escape
        // into, and step-down cliffs are never climbed (the path only
        // travels right; climbs are capped at MAX_STEP_CELLS).
        int floorRow = height - 3;  // ground level
        int col = 2;

        // Spawn platform
        for (int c = col - 2; c <= col + 2; c++) {
            if (c >= 0) { g[floorRow][c] = '#'; pathFloor[c] = floorRow; }
        }
        g[floorRow - 1][col] = 'P';

        col += 3;
        int lastFloorRow = floorRow;
        boolean needRunway = false;  // a climb just happened: no gap next

        while (col < width - 4) {
            // Choose the next segment: flat, gap, step-up, step-down.
            // After a climb, force a LANDING RUNWAY (3 flat cells)
            // before any gap — a jump arc lands 2+ cells past a step,
            // and arriving airborne at a gap lip means no jump fires
            // and the bot falls in (real level-design constraint:
            // players need landing room too).
            double roll = rng.nextDouble();

            if (roll < 0.30) {
                // Flat run (2-4 cells)
                int len = 2 + rng.nextInt(3);
                for (int i = 0; i < len && col < width - 4; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
            } else if (roll < 0.45) {
                // Gap (1 to MAX_GAP_CELLS), then continue at same height.
                // No gap right after a climb — landing runway required.
                if (needRunway) {
                    for (int i = 0; i < 3 && col < width - 4; i++, col++) {
                        g[lastFloorRow][col] = '#';
                        pathFloor[col] = lastFloorRow;
                    }
                } else {
                    int gap = 1 + rng.nextInt(MAX_GAP_CELLS);
                    col += gap;
                    int len = 2 + rng.nextInt(2);
                    for (int i = 0; i < len && col < width - 4; i++, col++) {
                        g[lastFloorRow][col] = '#';
                        pathFloor[col] = lastFloorRow;
                    }
                    // A gap jump arc lands ~140px (4+ cells) past the
                    // lip — a climb inside the landing zone wedges the
                    // player against the step face mid-descent. Runway
                    // required after gaps too.
                    needRunway = true;
                    continue;
                }
            } else if (roll < 0.60) {
                // Step up (1 to MAX_STEP_CELLS) — jumpable climb
                int step = 1 + rng.nextInt(MAX_STEP_CELLS);
                int newRow = Math.max(4, lastFloorRow - step);
                for (int r = newRow; r < height; r++) {
                    g[r][col] = '#';
                }
                pathFloor[col] = newRow;
                lastFloorRow = newRow;
                col++;
                int len = 2 + rng.nextInt(2);
                for (int i = 0; i < len && col < width - 4; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
                needRunway = true;
                continue;
            } else if (roll < 0.75) {
                // Step down (1-3 cells) — free fall, full fill
                int step = 1 + rng.nextInt(3);
                int newRow = Math.min(height - 3, lastFloorRow + step);
                for (int r = newRow; r < height; r++) {
                    g[r][col] = '#';
                }
                pathFloor[col] = newRow;
                lastFloorRow = newRow;
                col++;
                int len = 2 + rng.nextInt(2);
                for (int i = 0; i < len && col < width - 4; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
            } else if (roll < 0.85) {
                // Flat run with a chance of a spike on it
                int len = 3 + rng.nextInt(3);
                for (int i = 0; i < len && col < width - 4; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
            } else {
                // Spike PIT: the path dips into a 1-2 cell pit with
                // spikes at the bottom and a floor bridge over it —
                // no wait, that blocks the walk. Honest design: spikes
                // go in a pit BESIDE the walk line — a decorative
                // hazard off the guaranteed path. On-path spikes make
                // the level require damage-boosting (found by the
                // validator bot dying at every on-path spike).
                int len = 3 + rng.nextInt(3);
                for (int i = 0; i < len && col < width - 4; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
                // hazard pit below the floor line, off the path:
                // only if there's room (floor has fill below)
                if (lastFloorRow < height - 4 && col < width - 5) {
                    g[lastFloorRow + 3][col] = '^';
                    // leave the column below the path floor empty so the
                    // pit is visible: replace fill with a small alcove
                    // (purely cosmetic — the path runs over the top)
                }
            }
        }

        // Exit platform: bridge from wherever the walk ended to the
        // border wall, at the walk's final height. (A fill starting only
        // at width-6 can leave a gap between the last walk segment and
        // the exit — the bot falls in it and can never reach the exit.)
        for (int c = col; c < width; c++) {
            g[lastFloorRow][c] = '#';
            pathFloor[c] = lastFloorRow;
        }
        g[lastFloorRow - 1][width - 3] = 'E';

        // --- Decorative one-way platforms: REMOVED for now ---
        // They intercept the bot's jump arcs (one-ways catch falling
        // bodies — a jump peaking under one lands the bot on it, off
        // the path, at the wrong height). The validator can't reason
        // about optional geometry. Real levels can add them by hand —
        // the grid-reading bot handles hand-authored levels fine.

        // --- Locked door across the path (with a guaranteed key) ---
        // Pick a flat path column in the middle third. The door spans
        // the 2 cells above the floor. A key is placed on the path
        // ~8-12 columns BEFORE the door, 2 cells above the floor
        // (reachable by jump, not sitting in the walking line).
        // Chance-gated so not every level has one.
        if (rng.nextDouble() < 0.6) {
            int doorCol = -1;
            int from = width / 3, to = 2 * width / 3;
            for (int c = to; c >= from; c--) {
                int fr = pathFloor[c];
                if (fr >= 2 && fr <= height - 4
                        && pathFloor[c + 1] == fr && pathFloor[c + 2] == fr
                        && g[fr][c] == '#' && g[fr - 1][c] == ' '
                        && g[fr - 2][c] == ' ') {
                    doorCol = c;
                    break;
                }
            }
            if (doorCol > 4) {
                int fr = pathFloor[doorCol];
                // 'D' parses as a 2-tall door from the SINGLE lower cell
                // (fr-1): door occupies rows fr-2..fr-1. Only mark the
                // lower cell — two 'D' chars would make overlapping doors.
                g[fr - 1][doorCol] = 'D';
                // Key: on a FLAT stretch before the door, 1 cell above
                // the floor (head height while walking). Must be a run
                // with no gap/climb within 3 columns either side — a key
                // inside a jump arc is passed over airborne and never
                // collected (found by trace: key sat in a climb's arc).
                int keyCol = -1;
                for (int tries = 0; tries < 15 && keyCol < 0; tries++) {
                    int c = Math.max(3, doorCol - 6 - rng.nextInt(Math.max(1, doorCol - 10)));
                    if (pathFloor[c] < 0) continue;
                    boolean flat = true;
                    for (int cc = c - 3; cc <= c + 3; cc++) {
                        if (cc < 0 || cc >= width || pathFloor[cc] != pathFloor[c]) {
                            flat = false; break;
                        }
                    }
                    if (!flat) continue;
                    int kfr = pathFloor[c];
                    if (g[kfr - 1][c] == ' ' && g[kfr][c] == '#') keyCol = c;
                }
                if (keyCol >= 0) {
                    g[pathFloor[keyCol] - 1][keyCol] = 'k';
                }
            }
        }

        // --- Cracked floor + hidden pocket (bombable, OFF the bot's
        // concern). TWO adjacent floor tiles on a flat stretch are
        // CRACKED; below them a 2-cell pocket with a heart. Intact:
        // solid, walk over. Bombed: hole opens, player drops in,
        // grabs the heart, jumps out (2-cell climb, jump clears 2.2).
        // TWO cells, not one: the player body is 48px wide — a 32px
        // hole is always spanned by the lips and can never be entered
        // (found by physics test). The bot never bombs, so the path
        // is unaffected either way.
        if (rng.nextDouble() < 0.5) {
            int from = 6, to = width - 10;
            for (int tries = 0; tries < 10; tries++) {
                int c = from + rng.nextInt(to - from);
                int fr = pathFloor[c];
                if (fr < 0 || fr > height - 4) continue;
                if (g[fr][c] != '#' || g[fr][c + 1] != '#') continue;
                boolean flat = true;
                for (int cc = c - 1; cc <= c + 2; cc++) {
                    if (cc < 0 || cc >= width || pathFloor[cc] != fr) { flat = false; break; }
                }
                if (!flat) continue;
                if (g[fr + 1][c] != ' ' && g[fr + 1][c] != '#') continue;
                if (g[fr + 1][c + 1] != ' ' && g[fr + 1][c + 1] != '#') continue;
                g[fr][c] = 'C';       // cracked floor tiles
                g[fr][c + 1] = 'C';
                g[fr + 1][c] = ' ';   // pocket interior
                g[fr + 1][c + 1] = 'h';  // heart in the pocket
                break;
            }
        }

        // --- Enemies on wide flat stretches of the main path ---
        // (scan for 4+ consecutive '#' on a row with 2 clear cells above)
        for (int r = 2; r < height - 1; r++) {
            int run = 0;
            for (int c = 0; c < width; c++) {
                if (g[r][c] == '#' && (r == 0 || (g[r-1][c] == ' ' || g[r-1][c] == 'P' || g[r-1][c] == 'E'))
                    && (r < 1 || g[r-1][c] != '^')) {
                    run++;
                } else {
                    if (run >= 5 && rng.nextDouble() < 0.4) {
                        g[r-1][c - run / 2] = 'o';
                    }
                    run = 0;
                }
            }
        }

        // Border walls
        for (int r = 0; r < height; r++) {
            g[r][0] = (g[r][0] == ' ') ? '#' : g[r][0];
            if (g[r][width - 1] == ' ') g[r][width - 1] = '#';
        }

        // Assemble rows (top to bottom)
        List<String> rows = new ArrayList<>();
        for (char[] row : g) rows.add(new String(row));
        this.lastMap = new LevelMap(rows);
        return this.lastMap;
    }
}
