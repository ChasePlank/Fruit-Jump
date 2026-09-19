package tropical.engine;

/**
 * Physics components for 2D platformer.
 * 
 * All positions and velocities in world units (pixels or meters).
 * Gravity pulls down, Y increases upward.
 */
public class Physics {
    public static final double GRAVITY = 1200; // pixels/s^2
    
    // Entity physics state
    public static class Body {
        public int id = 0;            // entity ID for networking
        public double x, y;           // position (center)
        public double vx, vy;         // velocity
        public double hw, hh;         // half-width, half-height (AABB)
        public boolean grounded;      // touching ground this frame
        public boolean oneway;        // can jump through from below
        public boolean noGravity;     // skip gravity (hookshot pull, etc.)
        public boolean hitByExplosion; // damaged by bomb blast this frame
        
        public Body(double x, double y, double w, double h) {
            this.x = x; this.y = y;
            this.hw = w/2; this.hh = h/2;
        }
        
        public AABB aabb() { return new AABB(x - hw, y - hh, x + hw, y + hh); }
    }
    
    // Axis-aligned bounding box
    public static class AABB {
        public double x0, y0, x1, y1; // min corner, max corner
        
        public AABB(double x0, double y0, double x1, double y1) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        }
        
        public boolean overlaps(AABB o) {
            return x0 < o.x1 && x1 > o.x0 && y0 < o.y1 && y1 > o.y0;
        }
    }
    
    // Collision result from swept AABB
    public static class Hit {
        public double time;    // 0-1, fraction of frame
        public double nx, ny;  // normal (pointing from static to moving)
        double px, py;  // contact point
        
        Hit(double t, double nx, double ny, double px, double py) {
            this.time = t; this.nx = nx; this.ny = ny; this.px = px; this.py = py;
        }
    }
    
    /**
     * Swept AABB collision detection.
     * 
     * The problem: fast-moving objects tunnel through walls.
     * Solution: sweep the moving AABB from start to end, find first contact.
     * This gives us the exact time of collision within the frame.
     * 
     * Algorithm: expand the static AABB by the moving one's half-extents,
     * then raycast the moving center against the expanded box.
     * 
     * Also handles the embedded case (already overlapping).
     */
    public static Hit sweepAABB(Body moving, AABB staticBox, double dt) {
        // Velocity over this frame
        double dx = moving.vx * dt;
        double dy = moving.vy * dt;
        
        // Expand static box by moving's half-extents
        AABB e = new AABB(
            staticBox.x0 - moving.hw,
            staticBox.y0 - moving.hh,
            staticBox.x1 + moving.hw,
            staticBox.y1 + moving.hh
        );
        
        // DEBUG: show expanded box and player position
        // System.out.printf("    [SWEEP] player=(%.1f,%.1f) expanded=(%.1f,%.1f)-(%.1f,%.1f)%n",
        //     moving.x, moving.y, e.x0, e.y0, e.x1, e.y1);
        
        // First check if we're already embedded (fully inside expanded box)
        // Use STRICT inequality: being exactly on the boundary is NOT embedded
        boolean insideX = moving.x > e.x0 && moving.x < e.x1;
        boolean insideY = moving.y > e.y0 && moving.y < e.y1;
        
        if (insideX && insideY) {
            // Already inside — find minimal penetration axis and push out
            double ox0 = moving.x - e.x0;
            double ox1 = e.x1 - moving.x;
            double oy0 = moving.y - e.y0;
            double oy1 = e.y1 - moving.y;
            
            double min = Math.min(Math.min(ox0, ox1), Math.min(oy0, oy1));
            // Push out completely (not just by epsilon) and return time=0
            // This prevents infinite loops
            double nx, ny, px, py;
            if (min == ox0)      { nx = -1; ny = 0; px = e.x0; py = moving.y; }
            else if (min == ox1) { nx = 1;  ny = 0; px = e.x1; py = moving.y; }
            else if (min == oy0) { nx = 0; ny = -1; px = moving.x; py = e.y0; }
            else                 { nx = 0; ny = 1;  px = moving.x; py = e.y1; }

            // Separating: if velocity points AWAY from the push-out
            // direction, the body is already leaving — treat as no
            // collision. (Without this, a jump fired from a grounded
            // body resting ~0.3px into the floor gets its vy zeroed by
            // the embedded push-out, killing the jump. Found by the
            // level-validator trace: vy=-400 on the fire frame, vy=0
            // the next frame, forever.)
            double dot = moving.vx * nx + moving.vy * ny;
            if (dot > 0) return null;

            return new Hit(0, nx, ny, px, py);
        }
        
        // Not moving — no collision
        if (dx == 0 && dy == 0) return null;
        
        // Zero-velocity axis containment: the slab method's degenerate
        // case (dx==0 → tx0=0, tx1=1) means "inside the x slab for the
        // whole frame" — but that's only true if x actually lies within
        // [e.x0, e.x1]. Without this check, a body falling straight down
        // "collides" with any tile whose y-band it crosses, at ANY
        // horizontal distance — a phantom floor 960px away grounded a
        // falling player (found by the cracked-pocket test; masked for
        // 21 test suites because test bodies almost always had vx≠0).
        if (dx == 0 && (moving.x <= e.x0 || moving.x >= e.x1)) return null;
        if (dy == 0 && (moving.y <= e.y0 || moving.y >= e.y1)) return null;
        
        // Slab method: find entry time for each axis
        double tx0 = (dx != 0) ? (e.x0 - moving.x) / dx : (dx > 0 ? 0 : 1);
        double tx1 = (dx != 0) ? (e.x1 - moving.x) / dx : (dx > 0 ? 1 : 0);
        double ty0 = (dy != 0) ? (e.y0 - moving.y) / dy : (dy > 0 ? 0 : 1);
        double ty1 = (dy != 0) ? (e.y1 - moving.y) / dy : (dy > 0 ? 1 : 0);
        
        // Order: entry before exit
        if (tx0 > tx1) { double tmp = tx0; tx0 = tx1; tx1 = tmp; }
        if (ty0 > ty1) { double tmp = ty0; ty0 = ty1; ty1 = tmp; }
        
        // Latest entry, earliest exit
        double tEntry = Math.max(tx0, ty0);
        double tExit = Math.min(tx1, ty1);
        
        // No collision if exit before entry, or entry outside frame
        // tEntry <= 0 means we're already touching or inside — not a new collision
        // Use small epsilon to handle floating point precision
        double eps = 0.0001;
        if (tEntry > tExit || tExit < 0 || tEntry >= 1 || tEntry < eps) {
            return null;
        }
        
        double t = Math.max(0, tEntry);
        
        // Normal: which axis did we enter on?
        double nx, ny;
        if (tx0 > ty0) {
            nx = (dx < 0) ? 1 : -1;
            ny = 0;
        } else {
            nx = 0;
            ny = (dy < 0) ? 1 : -1;
        }
        
        return new Hit(t, nx, ny, 0, 0);
    }
    
    /**
     * Ray vs AABB intersection using slab method.
     * Returns hit time (0-1, fraction of ray length) and normal, or null if no hit.
     * 
     * rayX, rayY: ray endpoint relative to origin (ox, oy)
     * The hit time is t where contact = (ox, oy) + t * (rayX, rayY)
     * 
     * Used for line-of-sight, hookshot targeting, etc.
     */
    public static Hit raycastAABB(double ox, double oy, double rayX, double rayY, AABB box) {
        // Slab method: find entry/exit times for each axis
        double invDx = (rayX != 0) ? 1.0 / rayX : 1e20;
        double invDy = (rayY != 0) ? 1.0 / rayY : 1e20;
        
        double tx0 = (box.x0 - ox) * invDx;
        double tx1 = (box.x1 - ox) * invDx;
        double ty0 = (box.y0 - oy) * invDy;
        double ty1 = (box.y1 - oy) * invDy;
        
        // Order: entry before exit
        if (tx0 > tx1) { double tmp = tx0; tx0 = tx1; tx1 = tmp; }
        if (ty0 > ty1) { double tmp = ty0; ty0 = ty1; ty1 = tmp; }
        
        double tEntry = Math.max(tx0, ty0);
        double tExit = Math.min(tx1, ty1);
        
        // No hit if exit before entry, or entry outside ray
        if (tEntry > tExit || tExit < 0 || tEntry > 1) {
            return null;
        }
        
        // Clamp to [0, 1]
        double t = Math.max(0, tEntry);
        
        // Normal: which axis did we enter on?
        double nx, ny;
        if (tx0 > ty0) {
            nx = (rayX < 0) ? 1 : -1;
            ny = 0;
        } else {
            nx = 0;
            ny = (rayY < 0) ? 1 : -1;
        }
        
        double px = ox + rayX * t;
        double py = oy + rayY * t;
        
        return new Hit(t, nx, ny, px, py);
    }
}
