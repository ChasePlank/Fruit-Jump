package tropical.engine;

/**
 * Real-time enemy: patrols a platform, chases the player when close,
 * can be stomped (Mario-style) or deals contact damage.
 * 
 * AI states:
 *   PATROL — walk back and forth, turn at walls and ledges
 *   CHASE  — player within aggro range, walk toward them
 * 
 * The enemy is a physics body (gravity, collision) driven by AI velocity.
 */
public class Enemy {
    enum AIState { PATROL, CHASE }
    
    static int nextId = 0;
    public final int id;  // unique ID for save system
    
    public final Physics.Body body;
    AIState state = AIState.PATROL;
    
    double patrolSpeed = 60;
    double chaseSpeed = 110;
    public int dir = 1; // 1 = right, -1 = left

    // TOP-DOWN mode (rooms prototype): 4-direction movement. The
    // side-view AI only ever sets vx — in top-down rooms enemies slid
    // left/right and never tracked the player vertically (playtest:
    // "enemies can only move left and right, not up or down").
    public boolean topDown = false;
    public double hdx = 1, hdy = 0;  // heading (unit-ish vector)
    private final java.util.Random tdr = new java.util.Random();
    
    // Aggro
    double aggroRange = 220;    // horizontal distance to start chasing
    double deaggroRange = 320;  // hysteresis: don't stop chasing immediately
    
    // Stomp
    public boolean dead = false;
    double deadTimer = 0;
    boolean stompImmune = false;  // spiked/shielded: stomping hurts the player
    
    // Ledge/wall detection memory (set by World each frame)
    // PUBLIC LIKE `topDown`, because they are the World's report to this enemy for the frame - an interface
    // rather than an internal. A check cannot see a package-private field from another package, and the branch
    // that SETS these was the one thing the mutation sweep found unprotected.
    public boolean hitWall = false;
    public boolean atLedge = false;

    /** Wall sensor for top-down mode: solid tile just ahead in the
     *  current heading direction. (World.senseWall only probes
     *  horizontally — top-down enemies also run into north/south
     *  walls, which it can't see.) */
    public boolean senseWallTopDown(java.util.List<Physics.AABB> tiles) {
        double probe = 2.0;
        double ox = body.x + hdx * (body.hw + probe);
        double oy = body.y + hdy * (body.hh + probe);
        for (Physics.AABB t : tiles) {
            if (ox >= t.x0 && ox <= t.x1 && body.y - body.hh < t.y1 && body.y + body.hh > t.y0) return true;
            if (oy >= t.y0 && oy <= t.y1 && body.x - body.hw < t.x1 && body.x + body.hw > t.x0) return true;
        }
        return false;
    }
    
    public Enemy(double x, double y, double w, double h) {
        this.id = nextId++;
        body = new Physics.Body(x, y, w, h);
    }

    // Which creature this is. Art only for now: spiders and snakes share the
    // same patrol/chase behaviour. If they should differ mechanically, that is
    // a design call rather than something to invent here.
    public static final int KIND_SPIDER = 0, KIND_SNAKE = 1;
    public int kind = KIND_SPIDER;

    /**
     * Holds position until it aggros. A snake waits coiled rather than
     * patrolling - a snake wandering back and forth is not what a snake does,
     * and it also gives away the ambush (Kinger, Sept 29).
     */
    public boolean stationary = false;

    public Enemy(double x, double y, double w, double h, int kind) {
        this(x, y, w, h);
        this.kind = kind;
    }

    /** True while it is actively coming after the player. The snake rests
     *  coiled and only shows its pursuit pose when this is true. */
    public boolean isChasing() {
        return state == AIState.CHASE;
    }
    
    /**
     * AI update. The World reports hitWall/atLedge for this frame.
     * Player position used for chase logic.
     */
    public void updateAI(double dt, double playerX, double playerY) {
        if (dead) {
            deadTimer += dt;
            return;
        }

        double dx = playerX - body.x;
        double dy = playerY - body.y;
        double distX = Math.abs(dx);
        double distY = Math.abs(dy);

        if (topDown) {
            updateTopDown(dt, dx, dy, distX, distY);
            return;
        }

        // State transitions with hysteresis
        if (state == AIState.PATROL) {
            if (distX < aggroRange && distY < 120) {
                state = AIState.CHASE;
            }
        } else {
            if (distX > deaggroRange || distY > 200) {
                state = AIState.PATROL;
            }
        }

        switch (state) {
            case PATROL:
                // A stationary enemy (the coiled snake) does not patrol at all:
                // it waits. Staying put is the whole tell.
                if (stationary) {
                    body.vx = 0;
                    break;
                }
                // Turn at walls
                if (hitWall) {
                    dir = -dir;
                    hitWall = false;
                }
                // Turn at ledges (don't walk off)
                if (atLedge && body.grounded) {
                    dir = -dir;
                    atLedge = false;
                }
                body.vx = dir * patrolSpeed;
                break;

            case CHASE:
                dir = (dx > 0) ? 1 : -1;
                // Chase, but don't walk off ledges
                if (atLedge && body.grounded && dy > 0) {
                    body.vx = 0; // player below the ledge — don't dive off
                } else {
                    body.vx = dir * chaseSpeed;
                }
                if (hitWall) {
                    hitWall = false; // keep pushing against the wall
                }
                break;
        }
    }

    /** Top-down AI (rooms prototype): patrol in 4 directions, turn at
     *  walls, chase the player along both axes. */
    private void updateTopDown(double dt, double dx, double dy, double distX, double distY) {
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (state == AIState.PATROL) {
            if (dist < aggroRange) state = AIState.CHASE;
        } else {
            if (dist > deaggroRange) state = AIState.PATROL;
        }

        if (state == AIState.PATROL) {
            // Wall ahead: pick a new random heading (avoid oscillation:
            // never the exact reverse unless it's the only option)
            if (hitWall) {
                hitWall = false;
                double oldX = hdx, oldY = hdy;
                // PICK FROM THE ALLOWED SET, do not retry until you get one.
                //
                // PORTED FROM THE ENGINE, 6 October 2026. The version here rolled a d4 and re-rolled if it landed on
                // the heading it already had or the exact reverse. With two of the four faces excluded that is a
                // 1-in-16 chance of exhausting the retries and ACCEPTING THE SAME HEADING ANYWAY - so an enemy that
                // walked up into a ceiling could walk up into it again, stall for a frame, and try again. The
                // visible effect is small; the effect on the suite is not, because a check that reads the heading
                // to assert "a top-down enemy facing a ceiling turns" becomes FLAKY - it passed or failed depending
                // on an unseeded Random. Measured in the engine: six runs gave five 455/0 and one 454/1.
                //
                // Collecting the allowed headings first makes the turn certain, and the fallback only fires in a
                // corner where every direction is the one it came from.
                double[] dxs = {1, -1, 0, 0};
                double[] dys = {0, 0, 1, -1};
                int allowed = 0;
                for (int i = 0; i < 4; i++) {
                    if (dxs[i] == oldX && dys[i] == oldY) continue;      // same
                    if (dxs[i] == -oldX && dys[i] == -oldY) continue;    // reverse
                    allowed++;
                }
                int pick = tdr.nextInt(allowed > 0 ? allowed : 4);
                int seen = 0;
                for (int i = 0; i < 4; i++) {
                    if (allowed > 0) {
                        if (dxs[i] == oldX && dys[i] == oldY) continue;
                        if (dxs[i] == -oldX && dys[i] == -oldY) continue;
                    }
                    if (seen++ == pick) { hdx = dxs[i]; hdy = dys[i]; break; }
                }
            }
            body.vx = hdx * patrolSpeed;
            body.vy = hdy * patrolSpeed;
        } else {
            // CHASE: move toward the player on both axes
            if (dist > 1) {
                body.vx = (dx / dist) * chaseSpeed;
                body.vy = (dy / dist) * chaseSpeed;
            }
        }
        dir = body.vx >= 0 ? 1 : -1;  // sprite facing follows motion
    }
    
    /** Mark stomped. */
    public void stomp() {
        dead = true;
        body.vx = 0;
    }
    
    /** AABB overlap with another body. */
    public boolean overlaps(Physics.Body other) {
        return Math.abs(body.x - other.x) < (body.hw + other.hw)
            && Math.abs(body.y - other.y) < (body.hh + other.hh);
    }
}
