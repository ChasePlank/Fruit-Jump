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
