package tropical.engine;

import java.util.List;

/**
 * Hookshot/grappling mechanic: fire ray, find anchor, pull player.
 * 
 * States:
 *   IDLE      - not active
 *   EXTENDING - hook traveling outward, looking for anchor
 *   PULLING   - attached to anchor, pulling player toward it
 *   RETRACTING - missed, returning to player
 * 
 * This is the Zelda-style hookshot: instant fire, direct pull, no swing physics.
 * Directly applicable to Tropical Punch's hookshot mechanic.
 */
public class Hookshot {
    public enum State { IDLE, PULLING, RETRACTING }
    
    static final double HOOK_SPEED = 1200;    // pixels/s (fast, almost instant)
    static final double PULL_SPEED = 500;    // pixels/s (controlled pull)
    static final double MAX_RANGE = 400;     // max hook distance
    static final double RELEASE_DIST = 40;   // auto-release near anchor
    static final double RETRACT_SPEED = 1500; // pixels/s (fast return)
    
    public State state = State.IDLE;
    public double hookX, hookY;         // current hook tip position
    public double dirX, dirY;           // fire direction (normalized)
    public double anchorX, anchorY;     // attached anchor point
    Physics.Body player;
    
    // Failsafes against a stuck pull (found by playtest: firing at a
    // ledge lip anchors somewhere the pull can't reach — the player
    // hangs against geometry with input ignored, unable to counter).
    double pullTimer = 0;              // total time spent pulling
    double stallTimer = 0;              // time with no progress toward anchor
    double lastDist = Double.MAX_VALUE; // distance to anchor last frame
    static final double MAX_PULL_TIME = 1.5;   // hard cap: release
    static final double STALL_TIME = 0.3;      // no progress for 0.3s: release
    
    public Hookshot(Physics.Body player) {
        this.player = player;
    }
    
    /** Fire hookshot in direction (dx, dy), raycasting against world tiles. */
    public void fire(double dx, double dy, World world) {
        if (state != State.IDLE) return;
        
        double len = Math.sqrt(dx*dx + dy*dy);
        if (len < 0.001) return;
        
        dirX = dx / len;
        dirY = dy / len;
        
        // Raycast to find anchor instantly
        double bestT = Double.MAX_VALUE;
        Physics.AABB bestTile = null;
        
        for (Physics.AABB tile : world.tiles) {
            Physics.Hit hit = Physics.raycastAABB(player.x, player.y, dirX * MAX_RANGE, dirY * MAX_RANGE, tile);
            if (hit != null && hit.time < bestT) {
                bestT = hit.time;
                bestTile = tile;
            }
        }
        
        if (bestTile != null) {
            // Found anchor — calculate exact hit point
            anchorX = player.x + dirX * MAX_RANGE * bestT;
            anchorY = player.y + dirY * MAX_RANGE * bestT;
            hookX = anchorX;
            hookY = anchorY;
            state = State.PULLING;
            player.noGravity = true;
            pullTimer = 0;
            stallTimer = 0;
            lastDist = Double.MAX_VALUE;
        } else {
            // No anchor — retract immediately
            hookX = player.x + dirX * MAX_RANGE;
            hookY = player.y + dirY * MAX_RANGE;
            state = State.RETRACTING;
        }
    }
    
    /** Manually release (cancel pull). */
    public void release() {
        if (state == State.PULLING) {
            // Retain momentum on release
            double dx = anchorX - player.x;
            double dy = anchorY - player.y;
            double dist = Math.sqrt(dx*dx + dy*dy);
            if (dist > 0.001) {
                player.vx = (dx / dist) * PULL_SPEED;
                player.vy = (dy / dist) * PULL_SPEED;
            }
        }
        state = State.IDLE;
        player.noGravity = false;
    }
    
    public boolean isActive() {
        return state != State.IDLE;
    }
    
    public boolean isPulling() {
        return state == State.PULLING;
    }
    
    /**
     * Update hookshot state.
     * Returns true if hookshot is controlling player movement (skip normal input).
     */
    public boolean update(double dt, World world) {
        switch (state) {
            case PULLING: {
                // Calculate direction to anchor
                double dx = anchorX - player.x;
                double dy = anchorY - player.y;
                double dist = Math.sqrt(dx*dx + dy*dy);
                
                // Failsafes: a pull that can't make progress (anchor
                // behind geometry, player wedged) must not hold the
                // player hostage. Hard time cap + stall detection.
                pullTimer += dt;
                if (pullTimer > MAX_PULL_TIME) {
                    release();
                    break;
                }
                if (dist > lastDist - 1.0) {
                    // No meaningful progress this frame
                    stallTimer += dt;
                    if (stallTimer > STALL_TIME) {
                        release();
                        break;
                    }
                } else {
                    stallTimer = 0;
                }
                lastDist = dist;
                
                if (dist < RELEASE_DIST) {
                    // Close enough — release with momentum
                    release();
                    break;
                }
                
                // Set velocity toward anchor
                player.vx = (dx / dist) * PULL_SPEED;
                player.vy = (dy / dist) * PULL_SPEED;
                
                // Keep noGravity flag set
                player.noGravity = true;
                
                return true; // signal: hookshot controls movement
            }
            
            case RETRACTING: {
                // Pull hook back to player
                double dx = player.x - hookX;
                double dy = player.y - hookY;
                double dist = Math.sqrt(dx*dx + dy*dy);
                
                if (dist < 20) {
                    state = State.IDLE;
                    break;
                }
                
                hookX += (dx / dist) * RETRACT_SPEED * dt;
                hookY += (dy / dist) * RETRACT_SPEED * dt;
                break;
            }
            
            default:
                break;
        }
        
        return false;
    }
    
    static boolean pointInAABB(double x, double y, Physics.AABB a) {
        return x >= a.x0 && x <= a.x1 && y >= a.y0 && y <= a.y1;
    }
}
