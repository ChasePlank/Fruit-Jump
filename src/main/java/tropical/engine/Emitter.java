package tropical.engine;

import java.util.function.DoubleSupplier;

/**
 * Particle emitter: spawns particles with configurable behavior.
 * 
 * Emitter types:
 *   BURST  - instant explosion of particles
 *   STREAM - continuous emission over time
 *   TRAIL  - follows an entity and emits behind it
 */
public class Emitter {
    enum Type { BURST, STREAM, TRAIL }
    
    Type type;
    double x, y;
    DoubleSupplier targetX, targetY;  // for TRAIL emitters
    
    double rate;              // particles per second (STREAM/TRAIL)
    double accumulator = 0;   // time since last emission
    
    int burstCount;           // particles per burst (BURST)
    
    // Particle parameters
    double speedMin, speedMax;
    double angleMin, angleMax;  // radians
    double lifetimeMin, lifetimeMax;
    double sizeMin, sizeMax;
    double gravity;
    
    boolean active = true;
    boolean oneShot = false;  // for BURST: deactivate after first burst
    
    public Emitter(Type type, double x, double y) {
        this.type = type;
        this.x = x;
        this.y = y;
    }
    
    /** Create a burst emitter (explosion). */
    public static Emitter burst(double x, double y, int count) {
        Emitter e = new Emitter(Type.BURST, x, y);
        e.burstCount = count;
        e.oneShot = true;
        return e;
    }
    
    /** Create a stream emitter (continuous). */
    public static Emitter stream(double x, double y, double rate) {
        Emitter e = new Emitter(Type.STREAM, x, y);
        e.rate = rate;
        return e;
    }
    
    /** Create a trail emitter (follows entity). */
    public static Emitter trail(DoubleSupplier targetX, DoubleSupplier targetY, double rate) {
        Emitter e = new Emitter(Type.TRAIL, 0, 0);
        e.targetX = targetX;
        e.targetY = targetY;
        e.rate = rate;
        return e;
    }
    
    /** Configure particle speed. */
    public Emitter speed(double min, double max) {
        this.speedMin = min;
        this.speedMax = max;
        return this;
    }
    
    /** Configure particle angle (radians). */
    public Emitter angle(double min, double max) {
        this.angleMin = min;
        this.angleMax = max;
        return this;
    }
    
    /** Configure particle lifetime. */
    public Emitter lifetime(double min, double max) {
        this.lifetimeMin = min;
        this.lifetimeMax = max;
        return this;
    }
    
    /** Configure particle size. */
    public Emitter size(double min, double max) {
        this.sizeMin = min;
        this.sizeMax = max;
        return this;
    }
    
    /** Configure gravity. */
    public Emitter gravity(double g) {
        this.gravity = g;
        return this;
    }
    
    /** Set position (for non-TRAIL emitters). */
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    /** Update and spawn particles. */
    public void update(double dt, ParticlePool pool) {
        if (!active) return;
        
        // Update position for TRAIL emitters
        if (type == Type.TRAIL && targetX != null) {
            x = targetX.getAsDouble();
            y = targetY.getAsDouble();
        }
        
        switch (type) {
            case BURST:
                if (burstCount > 0) {
                    for (int i = 0; i < burstCount; i++) {
                        spawnParticle(pool);
                    }
                    burstCount = 0;
                    if (oneShot) active = false;
                }
                break;
                
            case STREAM:
            case TRAIL:
                accumulator += dt;
                while (accumulator >= 1.0 / rate) {
                    spawnParticle(pool);
                    accumulator -= 1.0 / rate;
                }
                break;
        }
    }
    
    void spawnParticle(ParticlePool pool) {
        Particle p = pool.acquire();
        
        double speed = random(speedMin, speedMax);
        double angle = random(angleMin, angleMax);
        double vx = speed * Math.cos(angle);
        double vy = speed * Math.sin(angle);
        double life = random(lifetimeMin, lifetimeMax);
        double sz = random(sizeMin, sizeMax);
        
        p.init(x, y, vx, vy, life, sz);
    }
    
    double random(double min, double max) {
        return min + Math.random() * (max - min);
    }
}
