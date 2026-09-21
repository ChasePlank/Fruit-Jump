package tropical.engine;

/**
 * Kinematic moving platform: moves on a path, carries the player.
 * 
 * Kinematic = moved by code, not forces. The platform ignores gravity and
 * collisions with tiles; it follows a fixed path. The player collides with
 * it as if it were solid geometry.
 * 
 * The carry mechanic: when the player is standing on the platform, the
 * platform's velocity is added to the player's movement each frame.
 * This is how real platformers do it — the player doesn't "stick" to
 * the platform, they just inherit its velocity while grounded on it.
 */
public class MovingPlatform {
    double x, y;              // current position (center)
    double w, h;              // size
    double vx, vy;            // current velocity
    double pathTimer;         // time along path
    double speed;             // path traversal speed
    
    // Path types
    public enum PathType { HORIZONTAL, VERTICAL, CIRCULAR }
    final PathType type;
    
    // Path parameters
    final double x0, y0;      // path origin (center)
    final double amplitude;   // horizontal/vertical: distance from origin; circular: radius
    final double period;      // seconds for full cycle
    
    Physics.Body standingPlayer = null; // player currently riding
    
    public MovingPlatform(PathType type, double x0, double y0, double w, double h,
                          double amplitude, double period) {
        this.type = type;
        this.x0 = x0;
        this.y0 = y0;
        this.w = w;
        this.h = h;
        this.amplitude = amplitude;
        this.period = period;
        this.x = x0;
        this.y = y0;
        this.pathTimer = 0;
    }
    
    /** Get the platform's AABB. */
    public Physics.AABB aabb() {
        return new Physics.AABB(x - w/2, y - h/2, x + w/2, y + h/2);
    }
    
    /** Check if a body is standing on this platform (within tolerance). */
    public boolean isCarrying(Physics.Body b) {
        Physics.AABB a = aabb();
        // Player's bottom must be at/near platform's top
        boolean verticallyAligned = Math.abs((b.y + b.hh) - a.y0) < 4.0;
        // Horizontal overlap
        boolean horizontallyAligned = b.x + b.hw > a.x0 && b.x - b.hw < a.x1;
        return verticallyAligned && horizontallyAligned;
    }
    
    /** Advance the platform along its path. Returns (vx, vy) delta this frame. */
    public double[] update(double dt) {
        double prevX = x;
        double prevY = y;
        
        pathTimer += dt;
        double phase = (pathTimer / period) * 2 * Math.PI;
        
        switch (type) {
            case HORIZONTAL:
                x = x0 + Math.sin(phase) * amplitude;
                y = y0;
                break;
            case VERTICAL:
                x = x0;
                y = y0 + Math.sin(phase) * amplitude;
                break;
            case CIRCULAR:
                x = x0 + Math.cos(phase) * amplitude;
                y = y0 + Math.sin(phase) * amplitude;
                break;
        }
        
        vx = (x - prevX) / dt;
        vy = (y - prevY) / dt;
        
        return new double[]{vx, vy};
    }
}
