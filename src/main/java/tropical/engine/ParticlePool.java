package tropical.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Object pool for particles: avoids allocation during gameplay.
 * 
 * Pre-allocates a fixed number of particles. When more are needed,
 * the pool grows. Inactive particles are reused.
 */
public class ParticlePool {
    private final List<Particle> particles = new ArrayList<>();
    private int nextIndex = 0;
    
    /**
     * Create a pool with initial capacity.
     */
    public ParticlePool(int initialCapacity) {
        for (int i = 0; i < initialCapacity; i++) {
            particles.add(new Particle());
        }
    }
    
    /**
     * Get a particle from the pool. Grows if necessary.
     */
    public Particle acquire() {
        // Try to find an inactive particle
        int start = nextIndex;
        do {
            Particle p = particles.get(nextIndex);
            if (!p.active) {
                nextIndex = (nextIndex + 1) % particles.size();
                return p;
            }
            nextIndex = (nextIndex + 1) % particles.size();
        } while (nextIndex != start);
        
        // All particles active, grow the pool
        Particle p = new Particle();
        particles.add(p);
        // THE CURSOR WRAPS; IT DOES NOT POINT PAST THE END.
        //
        // This was `nextIndex = particles.size()` - one past the last index - so the NEXT acquire called
        // particles.get(size) and threw. MEASURED 2026-10-12 with a pool of 2: the third acquire grew the pool to
        // 3, and the fourth threw "IndexOutOfBoundsException: Index 3 out of bounds for length 3".
        //
        // THE GAME'S POOL HOLDS 200 AND A SPLASH BURST IS AT MOST SIXTEEN, so it takes about thirteen splashes
        // inside a particle's 0.25-0.6s lifetime to reach - rare, and not rare enough to leave: World.update is
        // called from the JavaFX animation pulse with nothing catching, so when it does happen the FRAME LOOP dies
        // rather than the effect. No test in either edition named this class until this one.
        nextIndex = 0;
        return p;
    }
    
    /**
     * Get all particles (for iteration).
     */
    public List<Particle> getAll() {
        return particles;
    }
    
    /**
     * Count active particles.
     */
    public int activeCount() {
        int count = 0;
        for (Particle p : particles) {
            if (p.active) count++;
        }
        return count;
    }
    
    /**
     * Count total pool size.
     */
    public int poolSize() {
        return particles.size();
    }
}
