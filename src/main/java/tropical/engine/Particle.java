package tropical.engine;

/**
 * Individual particle: position, velocity, lifetime, and visual properties.
 * 
 * Particles are pooled to avoid allocation during gameplay.
 * When a particle's lifetime expires, it returns to the pool.
 */
public class Particle {
    double x, y;
    double vx, vy;
    double lifetime;
    double maxLifetime;
    
    // Visual properties (headless test tracks these)
    double size;
    double alpha;

    // Colour, copied from the emitter that spawned it. It lives on the particle and
    // not on the emitter because a burst emitter deactivates itself the moment it
    // fires - by the time these droplets are on screen the thing that made them is
    // gone, and a particle that asked it what colour to be would have nothing to ask.
    double cr = 1.0, cg = 1.0, cb = 1.0;
    
    boolean active = false;
    
    /**
     * Initialize a particle from the pool.
     */
    public void init(double x, double y, double vx, double vy,
                     double lifetime, double size,
                     double cr, double cg, double cb) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.lifetime = lifetime;
        this.maxLifetime = lifetime;
        this.size = size;
        this.cr = cr;
        this.cg = cg;
        this.cb = cb;
        this.alpha = 1.0;
        this.active = true;
    }

    // --- read accessors, for the view -------------------------------------
    // The fields stay package-private; the renderer is in another package and asks.
    public boolean isActive() { return active; }
    public double px() { return x; }
    public double py() { return y; }
    public double psize() { return size; }
    public double palpha() { return alpha; }
    public double pr() { return cr; }
    public double pg() { return cg; }
    public double pb() { return cb; }
    
    /**
     * Update particle physics and lifetime.
     */
    public void update(double dt, double gravity) {
        if (!active) return;
        
        x += vx * dt;
        y += vy * dt;
        vy += gravity * dt;
        
        lifetime -= dt;
        
        // Fade out over lifetime
        alpha = Math.max(0, lifetime / maxLifetime);
        
        if (lifetime <= 0) {
            active = false;
        }
    }
    
    /** Return to pool. */
    public void deactivate() {
        active = false;
    }
}
