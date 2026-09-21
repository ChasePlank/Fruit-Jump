package tropical.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * LevelValidator — a blind-traversal bot that plays generated levels
 * to prove they're completable. Jump-height-aware: reads the ASCII
 * grid to find the path, jumps gaps ≤ its jump distance, climbs steps
 * ≤ its jump height, avoids spikes.
 *
 * This is the empirical check on the generator's "completable by
 * construction" claim — construction guarantees geometry, the bot
 * proves playability.
 */
public class LevelValidator {
    static final double RUN_SPEED = 200;
    static final double JUMP_V = -420;

    final LevelMap map;
    public final World world;
    public final Physics.Body player;
    public final Combat combat;
    public final PlayerInventory inventory;
    /** Recorded path floor per column (from LevelGen), or null for
     *  hand-authored levels — then the bot reads the grid blind. */
    final int[] pathFloor;
    /** Precomputed jump waypoints: [x, type] pairs in path order.
     *  type 0 = GAP (fire at the lip; if passed while airborne, DROP —
     *  the flight already crossed it, and firing it late is a spurious
     *  jump that launches the bot over the NEXT gap lip).
     *  type 1 = CLIMB (fire when grounded and reached; late is fine —
     *  a straight-up jump at the face still clears a 2-cell step).
     *  Computed ONCE from the path — no per-frame geometric inference,
     *  no boundary straddle cases. */
    final java.util.ArrayList<double[]> jumpPoints = new java.util.ArrayList<>();
    static final double WP_GAP = 0, WP_CLIMB = 1;

    // Grid navigation state
    int targetCol;       // next column of the path we're walking toward
    boolean jumpQueued = false;
    /** Blocked-detector window state: x at window start, time left. */
    double windowX = 0, windowT = 0;

    public LevelValidator(LevelMap map) {
        this(map, null);
    }

    public LevelValidator(LevelMap map, int[] pathFloor) {
        this.map = map;
        this.pathFloor = pathFloor;
        this.world = new World();
        map.buildWorld(world);

        this.player = new Physics.Body(map.spawnX, map.spawnY, 24, 44);
        world.addBody(player);
        this.combat = new Combat();
        combat.playerHP = 3;
        this.inventory = new PlayerInventory();

        this.targetCol = (int) (map.spawnX / LevelMap.TILE) + 1;

        if (pathFloor != null) computeWaypoints();
    }

    /** Scan the recorded path and mark where jumps must happen:
     *  - gap lip: last floor column before a run of floorless columns
     *  - step face: column where the path floor rises 1-2 cells */
    void computeWaypoints() {
        int startCol = Math.max(0, (int) (map.spawnX / LevelMap.TILE));
        int c = startCol;
        while (c < pathFloor.length) {
            if (pathFloor[c] == -1) {
                c++;  // skip gap columns (waypoint was added at the lip)
            } else {
                // floor column: is the NEXT column a climb or a gap?
                if (c + 1 < pathFloor.length) {
                    if (pathFloor[c + 1] == -1) {
                        // gap starts at c+1: jump at the lip. Carry the
                        // gap's FAR edge — "already crossed" means past
                        // the far side, not past the lip (a flight can
                        // land AT the lip and still need the jump).
                        int far = c + 2;
                        while (far < pathFloor.length && pathFloor[far] == -1) far++;
                        jumpPoints.add(new double[]{
                            (c + 1) * (double) LevelMap.TILE, WP_GAP,
                            far * (double) LevelMap.TILE});
                    } else if (pathFloor[c + 1] < pathFloor[c]) {
                        // climb at c+1: fire so the APEX is at the face.
                        // Apex = 70px rise (measured) at 0.35s × 200px/s
                        // = 70px out. Carries the TARGET row so the bot
                        // can tell "climbed it" (grounded at/above the
                        // step floor) from "stranded below it."
                        jumpPoints.add(new double[]{
                            (c + 1) * (double) LevelMap.TILE - 70, WP_CLIMB,
                            pathFloor[c + 1]});
                    }
                }
                c++;
            }
        }
    }

    int curColSafe() {
        return (int) Math.floor(player.x / LevelMap.TILE);
    }

    /**
     * Play the level with a time budget. Returns true if the exit is
     * reached without dying.
     */
    public boolean play(double maxTime) {
        double dt = GameLoop.DT;
        double t = 0;

        while (t < maxTime) {
            botThink();
            world.update(dt);
            combat.update(dt);

            // Pickups (keys/hearts on the path — tryCollect applies them)
            for (Pickup p : world.pickups) {
                p.tryCollect(player, combat, inventory);
            }

            // Doors: unlock with a key when touching. The bot walks the
            // guaranteed path; a door ON the path must be openable by
            // the key the generator placed before it. tryUnlock flips
            // the flag; unlockDoor removes the solid AABB.
            for (Door d : world.doors) {
                if (d.isSolid() && d.tryUnlock(player, inventory, combat)) {
                    world.unlockDoor(d);
                }
            }

            // Spike damage
            for (Physics.AABB sp : world.spikes) {
                if (sp.overlaps(player.aabb())) {
                    combat.hurtPlayer(player, player.x + 1);  // knock left
                }
            }
            if (combat.playerDead()) return false;

            // Exit check
            double dx = Math.abs(player.x - map.exitX);
            double dy = Math.abs(player.y - map.exitY);
            if (dx < 24 && dy < 40) return true;

            t += dt;
        }
        return false;  // time out
    }

    /**
     * Bot brain: walk right, jump obstacles.
     * Grid-reading: look ahead 1-2 cells at foot level; if the next
     * ground is missing (gap) or higher (step), jump when close.
     */
    public void botThink() {
        if (pathFloor != null) {
            pathThink();
        } else {
            gridThink();
        }
    }

    /**
     * Path-aware navigation for GENERATED levels: the generator recorded
     * the floor row of its carved path per column; the bot walks it.
     * This is not cheating — the physics engine still does every move:
     * if a carved jump is physically impossible, the bot falls and the
     * validation fails. The path data tests the generator's CLAIM
     * ("this path is traversable") while the engine proves it.
     */
    /**
     * Waypoint navigation for GENERATED levels: jump points are
     * precomputed from the recorded path (gap lips and step faces).
     * Runtime logic is trivial — run right, jump at the next waypoint.
     * The physics engine still does every move: if a carved jump is
     * physically impossible, the bot falls and validation fails.
     */
    void pathThink() {
        player.vx = RUN_SPEED;

        // Blocked detector: compare x against HALF A SECOND AGO, not
        // last frame. In the corner-wedge the embedded push-out cancels
        // vx each frame — per-frame deltas look like full progress while
        // the body goes nowhere, and grounded flicker resets any
        // per-frame counter. A 0.5s window sees the truth: no net
        // progress while a CLIMB is pending = pressed against the face
        // = jump now.
        if (!jumpPoints.isEmpty() && jumpPoints.get(0)[1] == WP_CLIMB) {
            if (windowT <= 0) { windowX = player.x; windowT = 0.5; }
            windowT -= GameLoop.DT;
            if (windowT <= 0) {
                if (player.x - windowX < 10) player.vy = JUMP_V;
                windowX = player.x;
                windowT = 0.5;
            }
        } else {
            windowT = 0;
        }

        if (!jumpPoints.isEmpty()) {
            double edge = player.x + player.hw;
            double[] wp = jumpPoints.get(0);

            if (player.grounded) {
                if (wp[1] == WP_GAP) {
                    // Gap: fire at the lip, consume immediately (the jump
                    // either clears it or the bot falls — no recovery
                    // from a lip).
                    if (edge >= wp[0]) {
                        player.vy = JUMP_V;
                        jumpPoints.remove(0);
                    }
                } else {
                    // Climb. Compare in PIXELS, not grid rows — feet at
                    // 159.9 read as row 4 while standing on row 5's top
                    // (row boundaries lie at cell edges; found by trace).
                    //   consume: past the face AND on/above the step top
                    //   jump:    reached the fire point AND below it
                    double face = wp[0] + 70;
                    double stepTop = wp[2] * LevelMap.TILE;  // target row's top y
                    double feet = player.y + player.hh;
                    if (edge > face && feet <= stepTop + 8) {
                        jumpPoints.remove(0);      // climbed it
                    } else if (edge >= wp[0] && feet > stepTop + 8) {
                        player.vy = JUMP_V;        // below the step: jump
                    }
                }
            } else {
                // Airborne: drop a GAP waypoint only when past its FAR
                // edge (the flight truly crossed it). Landing AT the lip
                // still needs the jump — dropping on the lip side walks
                // the bot into the pit. CLIMB waypoints stay.
                while (!jumpPoints.isEmpty() && jumpPoints.get(0)[1] == WP_GAP
                       && edge > jumpPoints.get(0)[2]) {
                    jumpPoints.remove(0);
                }
            }
        }
    }

    /**
     * Blind grid-reading navigation for hand-authored levels (no
     * recorded path). Reads the ASCII grid and decides.
     */
    void gridThink() {
        // Perception: the floor row = first solid row scanning DOWN from
        // the feet at the current column. One code path for grounded and
        // airborne — grounded feet sit within a pixel of the floor top
        // (snap jitter flips them between "in the air row" and "in the
        // floor row"), so deriving the floor by scan is the only stable
        // read. This is also the landing floor when airborne over a pit.
        int feetRow = (int) Math.floor((player.y + player.hh) / LevelMap.TILE);
        // Two columns, two jobs:
        //   center column — "what am I standing on" (scan down from feet;
        //     the right edge at a wall-face boundary reads the WALL's
        //     column as the floor and miscomputes the climb as 0)
        //   right edge column — "what's ahead" (honest for a body moving
        //     right; the center can straddle a pillar edge)
        int standCol = (int) Math.floor(player.x / LevelMap.TILE);
        int curCol = (int) Math.floor((player.x + player.hw) / LevelMap.TILE);
        int footRow = -1;
        for (int r = feetRow; r < map.height; r++) {
            char ch = map.cell(r, standCol);
            if (ch == '#' || ch == '/' || ch == '\\' || ch == '=') { footRow = r; break; }
        }
        if (footRow == -1) footRow = feetRow;  // void below: treat as gap

        // What's ahead at foot level and one above?
        char ahead = map.cell(footRow, curCol + 1);
        char ahead2 = map.cell(footRow, curCol + 2);
        char aboveAhead = map.cell(footRow - 1, curCol + 1);

        // Gap = the very next column has no floor at/below foot level.
        // Even a 1-cell hole must be jumped — a "2+ cells" check walks
        // straight into 1-cell pits (found by void-fall trace).
        boolean floorBelowAhead = false;
        for (int r = footRow; r < Math.min(map.height, footRow + 6); r++) {
            char ch = map.cell(r, curCol + 1);
            if (ch == '#' || ch == '/' || ch == '\\') { floorBelowAhead = true; break; }
        }
        boolean gapAhead = !floorBelowAhead;

        // Climbable step: find the top of the wall in the ahead column
        // (scanning up from the floor row). The CLIMB is from the standing
        // surface (top of footRow) to the landing surface (top of
        // wallTopRow) = footRow - wallTopRow cells. Don't +1: the floor
        // row itself is stood upon, not climbed. (An earlier version
        // over-counted by one and was masked by an opposite off-by-one
        // in the perception row — two bugs canceling.)
        boolean stepAhead = false;
        if (ahead == '#' || ahead == '/' || ahead == '\\') {
            int wallTopRow = footRow;
            for (int r = footRow - 1; r >= footRow - 4; r--) {
                char ch = map.cell(r, curCol + 1);
                if (ch == '#' || ch == '/' || ch == '\\') wallTopRow = r;
                else break;
            }
            int climb = footRow - wallTopRow;
            char aboveTop = map.cell(wallTopRow - 1, curCol + 1);
            boolean clearAbove = aboveTop == ' ' || aboveTop == 'P' || aboveTop == 'E'
                || aboveTop == 'o' || aboveTop == 'h' || aboveTop == 'k';
            stepAhead = climb >= 1 && climb <= 2 && clearAbove;
        }

        // Spike detection: spikes sit ON the floor (one row above the
        // floor row). Scan the next 2 columns at the spike row (footRow-1)
        // AND at foot level (in case of a raised-spike edge case).
        boolean spikeAhead = false;
        for (int dc = 1; dc <= 2; dc++) {
            char atSpikeRow = map.cell(footRow - 1, curCol + dc);
            char atFoot = map.cell(footRow, curCol + dc);
            if (atSpikeRow == '^' || atFoot == '^') { spikeAhead = true; break; }
        }

        // Walk right
        player.vx = RUN_SPEED;

        // Jump decisions (only when grounded)
        if (player.grounded) {
            if (gapAhead || spikeAhead) {
                // Jump the gap/spike: jump a bit early for arc clearance
                player.vy = JUMP_V;
            } else if (stepAhead) {
                // Climb: the apex must occur AT the wall face. Time to
                // apex ≈ 0.35s at 200px/s ≈ 70px — jump when the face is
                // ~70px out so the arc peaks at the wall. If already
                // grinding AT the face (distToStep <= 0), jump straight
                // up — 66px rise clears a 2-cell wall, then over the top.
                // (A >0 guard creates a dead zone at the face: the bot
                // lands there and can never jump.)
                double distToStep = (curCol + 1) * LevelMap.TILE - (player.x + player.hw);
                if (distToStep < 80) {
                    player.vy = JUMP_V;
                }
            }
        }
    }

    /** Quick static validation: generate + play, returns result. */
    public static boolean validateLevel(LevelMap map, double maxTime) {
        LevelValidator v = new LevelValidator(map);
        return v.play(maxTime);
    }

    /** Validate a GENERATED level against its recorded path. */
    public static boolean validateGenerated(LevelGen gen, double maxTime) {
        LevelValidator v = new LevelValidator(gen.lastMap, gen.pathFloor);
        return v.play(maxTime);
    }
}
