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
    static final int BASE_MAX_GAP_CELLS = 3;    // conservative: 3-cell gaps
    static final int BASE_MAX_STEP_CELLS = 2;   // conservative: 2-cell climbs
    
    final int width, height;
    final Random rng;
    final int levelNum;
    final int maxGapCells;
    final int maxStepCells;
    /** Columns whose walk surface is a FLOATING platform (see groundFill). */
    final boolean[] platformColumn;
    /** Chance a 2-3 cell gap is flooded. 0 disables flooded gaps. */
    static final double FLOODED_GAPS = 0.35;
    /**
     * Share of flat runs replaced by platform climbs, scaled by level. 0 turns
     * them off.
     *
     * OFF for now, and the reason is measured rather than cautious: the climb
     * geometry is implemented and the rises are adjacent (a rise combined with a
     * gap is unlandable - at the end of the arc the body is back at launch height,
     * so a platform one cell higher is a wall you hit sideways), but the validator
     * bot still fails ~30% of level-22 levels with them on. The bot jumps a gap
     * from the lip and lands ~4.3 cells later at ~0 height, so it cannot land on
     * anything raised. Teaching LevelValidator to fire a gap-and-rise jump earlier
     * (around its apex, 2 cells out) is the actual next step; until then this
     * stays 0 so the 100/100 gate keeps meaning something.
     */
    static final double PLATFORM_CLIMBS = 1.0;
    /** Floor row of the carved path per column (-1 = no path floor).
     *  Recorded during generation so tests measure the ACTUAL path,
     *  not a heuristic re-read of the grid. */
    public final int[] pathFloor;
    /** The most recently generated map (for validateGenerated). */
    public LevelMap lastMap;

    public LevelGen(int width, int height, long seed) {
        this(width, height, seed, 1);
    }
    
    public LevelGen(int width, int height, long seed, int levelNum) {
        this.width = width;
        this.height = height;
        this.rng = new Random(seed);
        this.levelNum = levelNum;
        this.pathFloor = new int[width];
        this.platformColumn = new boolean[width];
        java.util.Arrays.fill(this.pathFloor, -1);
        
        // Difficulty scaling: gaps grow by 1 every 5 levels (max 5)
        // Capped at 4. It used to reach 5 by level 21, and a 5-cell gap is 160px
        // against a 137px jump - an impossible gap, on any level from 21 on, which
        // is not difficulty, it is a wall. Difficulty comes from the platform
        // climbs below instead, which are hard and always passable.
        this.maxGapCells = Math.min(4, BASE_MAX_GAP_CELLS + (levelNum - 1) / 5);
        // Steps stay at 2 (harder to tune without breaking path)
        this.maxStepCells = BASE_MAX_STEP_CELLS;
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

            // Platform climbs arrive with the level number and take their share of
            // the flat runs, so level 1's walk is exactly what it always was.
            double climbShare = Math.min(0.20, 0.03 * (levelNum - 1)) * PLATFORM_CLIMBS;

            if (roll < 0.30 - climbShare) {
                // Flat run (2-4 cells)
                int len = 2 + rng.nextInt(3);
                for (int i = 0; i < len && col < width - 4; i++, col++) {
                    g[lastFloorRow][col] = '#';
                    pathFloor[col] = lastFloorRow;
                }
            } else if (roll < 0.30) {
                // PLATFORM CLIMB: a run of floating platforms, each 1-2 cells wide,
                // rising 1-2 cells, with a 1-3 cell gap between. This is the
                // difficulty axis now - vertical, and always passable (the same
                // climb and gap limits the walk already guarantees).
                //
                // The platforms are meant to hang in the air, so groundFill leaves
                // air under them, but it gives the pit a floor two rows down: a
                // missed jump drops you into a pit you can jump back out of, rather
                // than out of the level. Hard, not unfair.
                // Traced against the arc: at 3 cells of travel (96px, t=0.48s) the
                // body is still 64px = 2 cells up, so a 3-cell gap clears a 1-cell
                // rise comfortably and a 2-cell rise exactly. WIDER gaps with any
                // rise are impossible (at 4 cells the body is only 0.7 cells up),
                // and 1-cell hops are unhittable - the validator bot jumps at the
                // lip and lands ~4 cells past it, so its landings need room, and it
                // is the gate every level has to pass.
                //
                // Hence: 3-wide platforms, rising 1 cell, with a 3-cell hop between.
                // A real climb, inside the limits the walk already guarantees.
                // Geometry, traced against the arc, and it is narrower than it looks:
                //  - a rise combined with a gap is UNLANDABLE. At 4 cells of travel
                //    (the end of the arc) the body is back at launch height, so a
                //    platform one cell higher is a wall you hit sideways. A rise is
                //    only clearable up close, straight up at the face.
                //  - so the rises here are ADJACENT steps and the gaps sit between
                //    groups at the same height. The result still reads as floating
                //    platforms to jump between - it just respects the same limits the
                //    rest of the walk does, which is what keeps every level passable.
                int platforms = 2 + rng.nextInt(3);
                for (int k = 0; k < platforms && col < width - 4; k++) {
                    int newRow = Math.max(4, lastFloorRow - 1);
                    for (int i = 0; i < 3 && col < width - 4; i++, col++) {
                        g[newRow][col] = '#';
                        pathFloor[col] = newRow;
                        platformColumn[col] = true;
                    }
                    lastFloorRow = newRow;
                }
                col += 1 + rng.nextInt(2);               // the hop at this height
                needRunway = true;
                continue;
            } else if (roll < 0.45) {
                // Gap (1 to MAX_GAP_CELLS), then continue at same height.
                // No gap right after a climb — landing runway required.
                if (needRunway) {
                    for (int i = 0; i < 3 && col < width - 4; i++, col++) {
                        g[lastFloorRow][col] = '#';
                        pathFloor[col] = lastFloorRow;
                    }
                } else {
                    int gap = 1 + rng.nextInt(maxGapCells);
                    int gapStart = col;
                    col += gap;

                    // Some gaps are FLOODED. A gap is the one place the walk already
                    // leaves empty, so water costs the guaranteed path nothing: the
                    // bot jumps gaps exactly as before (same pathFloor, same jump
                    // waypoints), and a player who misses the jump lands in water
                    // instead of falling out of the level.
                    //
                    // TWO rows of water, not three: at the walk's default floor row
                    // (height-3), three rows plus a pool floor does not fit inside the
                    // map at all - the condition is never true and it silently floods
                    // nothing. A pool that looks reasonable and can never exist is
                    // worth checking by counting, not by reading.
                    //
                    // Escapable by construction: the surface sits at the path level
                    // and the pool is 64px deep, so the breach hop clears the lip.
                    // Deeper would be a trap. One-cell gaps are left alone - a 32px
                    // slot is not a place to swim.
                    if (gap >= 2 && rng.nextDouble() < FLOODED_GAPS
                            && lastFloorRow + 2 < height) {
                        for (int cc = gapStart; cc < col; cc++) {
                            g[lastFloorRow][cc] = '~';       // surface, level with
                            g[lastFloorRow + 1][cc] = '~';   // the walk
                            g[lastFloorRow + 2][cc] = '#';   // pool floor, so it is
                        }                                    // not bottomless
                    }

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
                int step = 1 + rng.nextInt(maxStepCells);
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
                    // The pit the comment above promises. It was described but never carved, so the spike sat
                    // inside solid fill and rendered as spikes sitting ON the ground rather than in a hole -
                    // reported from play as "the spike level had them above ground, not a pit". Clearing the two
                    // rows between the floor and the spike is what makes it a pit.
                    g[lastFloorRow + 1][col] = ' ';
                    g[lastFloorRow + 2][col] = ' ';
                }
            }
        }

        // Exit platform: bridge from wherever the walk ended to the
        // border wall, at the walk's final height. (A fill starting only
        // at width-6 can leave a gap between the last walk segment and
        // the exit — the bot falls in it and can never reach the exit.)
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
                // NOT placed yet - see below. A door without a key is an unwinnable
                // level, and the key search is allowed to fail.
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
                    g[fr - 1][doorCol] = 'D';   // only now: a door is placed ONLY once
                                                // its key exists. Placing the door first
                                                // and hoping left unwinnable levels
                                                // whenever the key search failed (found
                                                // by tracing a bot that walked 7 rows up
                                                // a platform climb and then pressed
                                                // against a locked door with keys=0).
                }
            }
        }

        // --- Cracked floor + hidden chamber is placed AFTER the enemy scan,
        // further down: the chamber floor is a long run of '#' with open space
        // above it, which is exactly what that scan looks for, and an enemy
        // sealed inside a bombable chamber is dead content.

        // --- Enemies on wide flat stretches of the main path ---
        // Frequency scales with level: 40% + 10%/level, capped 85%.
        // (Playtest: level 1 had ~1 enemy — nothing to fight.)
        double enemyChance = Math.min(0.85, 0.40 + 0.10 * (levelNum - 1));
        for (int r = 2; r < height - 1; r++) {
            int run = 0;
            for (int c = 0; c < width; c++) {
                if (g[r][c] == '#' && (r == 0 || (g[r-1][c] == ' ' || g[r-1][c] == 'P' || g[r-1][c] == 'E'))
                    && (r < 1 || g[r-1][c] != '^')) {
                    run++;
                } else {
                    // Skip enemy placement near the spawn — a run that
                    // starts at column 0 puts its midpoint enemy right
                    // on the player spawn (playtest: "enemy spawns right
                    // on you, instantly taking a life"). 6 cells ≈ the
                    // spawn platform plus a safe walking buffer.
                    int mid = c - run / 2;
                    if (run >= 5 && mid > 6 && rng.nextDouble() < enemyChance
                            && g[r - 1][mid] == ' ') {   // never overwrite a pickup
                        g[r-1][mid] = 'o';
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

        // --- Cracked floor + hidden chamber (bombable, OFF the bot's concern).
        // TWO adjacent floor tiles on a flat stretch are CRACKED; below them a
        // chamber with a heart. Intact: solid, walk over. Bombed: the hole opens,
        // the player drops in, takes the heart and jumps back out.
        //
        // TWO cells wide, not one: a 32px hole is spanned by the lips and can
        // never be entered. The bot never bombs, so the path is unaffected.
        //
        // WHY THE SEAT IS ONE CELL: the bombed hole is the SECOND cell of air.
        // A chamber H cells tall has its floor one row below that, so climbing
        // straight out is H+1 cells; the body is 44px and a cell is 32px, so H
        // must be >= 2 to fit inside at all - which makes the climb 3 cells =
        // 96px against a 73px jump. There is no H that works: the door has to be
        // the hole. Hence 1 cell of seat plus the open hole above it (64px of air,
        // the body fits with its head poking up) and a floor two rows down, which
        // is a 2-cell climb = 64px, inside the jump with the same margin the
        // walk's own step limit assumes.
        //
        // The old version cleared that one cell and wrote NO floor. On a flat run
        // the heart hung over the map's void, so taking it dropped the player out
        // of the world; where the column was already filled the cavity was 32px
        // and the body could not fit. Either way the feature never worked.
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
                boolean clear = true;
                for (int cc = c; cc <= c + 1 && clear; cc++) {
                    for (int rr = fr + 1; rr <= fr + 2; rr++) {
                        if (g[rr][cc] != ' ' && g[rr][cc] != '#') { clear = false; break; }
                    }
                }
                if (!clear) continue;
                g[fr][c] = 'C';          // cracked floor: the chamber's ceiling
                g[fr][c + 1] = 'C';      // AND its doorway
                g[fr + 1][c] = ' ';      // one cell of seat (the hole is the other)
                g[fr + 1][c + 1] = 'h';  // heart in the seat
                g[fr + 2][c] = '#';      // chamber floor, written explicitly: the
                g[fr + 2][c + 1] = '#';  // grounding pass fills below the LOWEST
                                         // block, so with no floor the heart would
                                         // be the lowest block and get buried
                break;
            }
        }

        // Border walls
        for (int r = 0; r < height; r++) {
            g[r][0] = (g[r][0] == ' ') ? '#' : g[r][0];
            if (g[r][width - 1] == ' ') g[r][width - 1] = '#';
        }

        // --- Ground the terrain (Kinger: "the blocks are floating") -----------
        // The walk writes ONE '#' per column on flat runs - only climbs and drops
        // fill their column - so most of a floor was a one-cell ledge hanging over
        // empty space. The path looked like it floated, and stepping off it
        // dropped the player out of the level. Copy the lowest block in each
        // column down to the bottom of the map. See groundFill() for why that is
        // safe and why gaps survive.
        groundFill(g);

        // Assemble rows (top to bottom)
        List<String> rows = new ArrayList<>();
        for (char[] row : g) rows.add(new String(row));
        this.lastMap = new LevelMap(rows);
        return this.lastMap;
    }

    /**
     * The generator for a given level number: the ONE place the game's construction
     * convention lives.
     *
     * The seed formula (1000 + levelNum) is what makes a level reproducible from its number;
     * passing levelNum through is what makes difficulty scale. Both were once wrong in the
     * game - the 3-arg constructor forces levelNum = 1 internally, so gap width, enemy count
     * and the platform climbs were inert in play, and nothing noticed because no test could
     * see the call site. A named factory in the engine can be asserted on from a headless
     * suite; a line inside a JavaFX screen cannot.
     */
    public static LevelGen forLevel(int levelNum) {
        return new LevelGen(60, 14, 1000L + levelNum, levelNum);
    }

    /** Terrain: what counts as ground for grounding purposes. Pickups, enemies,
     *  the spawn/exit markers and one-way platforms are deliberately absent. */
    static boolean isTerrain(char ch) {
        return ch == '#' || ch == 'C' || ch == 'D'
            || ch == '/' || ch == '\\' || ch == '^';
    }

    /**
     * Ground the terrain: copy the lowest block in each column down to the
     * bottom of the map.
     *
     * Four rules make it safe:
     *
     *  - The anchor is the LOWEST terrain cell, so nothing above it moves. The
     *    path surface, doors, keys, enemies and spikes are untouched by
     *    construction rather than by luck.
     *  - A column with no terrain is left alone. That is a gap, and gaps are the
     *    jumps - running per column grounds the cliffs either side of a gap
     *    without bridging it.
     *  - One-way platforms are not terrain: they exist to be jumped up through,
     *    and grounding one turns a platform into a pillar.
     *  - A cavity survives as long as it has a floor, because the floor is then
     *    the lowest cell and the fill starts below it. The chamber above builds
     *    its own floor for exactly this reason.
     *
     * Runs last, after enemies: a freshly filled mass offers the enemy scan brand
     * new long runs of '#' with open space above, deep inside what should be rock.
     */
    private void groundFill(char[][] g) {
        for (int c = 0; c < width; c++) {
            int anchor = -1;
            for (int r = height - 1; r >= 0; r--) {
                if (isTerrain(g[r][c])) { anchor = r; break; }
            }
            if (anchor < 0) continue;              // a gap - leave it open
            if (platformColumn[c]) {
                // A floating platform keeps one row of air beneath it, and the pit is
                // floored one row below that: a missed jump is a 2-cell climb back
                // out, the same limit the walk assumes everywhere else. (Two rows of
                // air would make it three cells to climb - a trap, the same counting
                // mistake the chamber had.)
                for (int r = 0; r < height; r++) {
                    if (g[r][c] == '#') { anchor = Math.min(height - 1, r + 1); break; }
                }
            }
            for (int r = anchor + 1; r < height; r++) {
                if (g[r][c] == ' ') g[r][c] = '#';
            }
        }
    }
}
