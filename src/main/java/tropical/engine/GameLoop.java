package tropical.engine;

/**
 * Fixed timestep game loop for deterministic physics.
 *
 * The classic problem: physics tied to frame rate breaks.
 * Solution: accumulate time, step physics at fixed intervals.
 * This ensures identical behavior at 30fps, 60fps, or 144fps.
 *
 * The loop:
 *   - Accumulate elapsed time
 *   - While accumulator >= dt, step physics
 *   - Render with interpolation between states
 */
public class GameLoop {
    public static final double DT = 1.0 / 60.0;  // 60Hz physics
    static final double MAX_FRAME = 0.25; // spiral of death protection
    
    final World world;
    double accumulator = 0;
    double gameTime = 0;
    
    public GameLoop(World world) {
        this.world = world;
    }
    
    /** Run for a fixed duration (for headless testing). */
    public void run(double duration) {
        double t = 0;
        while (t < duration) {
            double frameTime = DT; // pretend perfect frame timing for headless
            frameTime = Math.min(frameTime, MAX_FRAME);
            
            accumulator += frameTime;
            while (accumulator >= DT) {
                world.update(DT);
                accumulator -= DT;
                gameTime += DT;
            }
            t += frameTime;
        }
    }
    
    /** For real rendering: interpolation = accumulator / DT. */
    public double alpha() {
        return accumulator / DT;
    }
}
