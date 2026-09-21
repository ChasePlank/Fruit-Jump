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
        nextIndex = particles.size();
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
