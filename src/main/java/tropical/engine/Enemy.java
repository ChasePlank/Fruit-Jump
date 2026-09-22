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
    boolean hitWall = false;
    boolean atLedge = false;

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
                int tries = 0;
                do {
                    switch (tdr.nextInt(4)) {
                        case 0 -> { hdx = 1; hdy = 0; }
                        case 1 -> { hdx = -1; hdy = 0; }
                        case 2 -> { hdx = 0; hdy = 1; }
                        default -> { hdx = 0; hdy = -1; }
                    }
                } while (hdx == -oldX && hdy == -oldY && ++tries < 4);
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
