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
    
    boolean active = false;
    
    /**
     * Initialize a particle from the pool.
     */
    public void init(double x, double y, double vx, double vy, 
                     double lifetime, double size) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.lifetime = lifetime;
        this.maxLifetime = lifetime;
        this.size = size;
        this.alpha = 1.0;
        this.active = true;
    }
    
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
