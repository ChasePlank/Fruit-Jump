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

    /**
     * The longest frame the simulation will believe in, in seconds.
     *
     * <p><b>PUBLIC BECAUSE THE GAME LAYER HAD ITS OWN COPY OF THE NUMBER.</b> The spiral-of-death protection in the
     * shipping loop lives in GameplayScreen: `accumulator += Math.min(dt, 0.25)` - a hard-coded 0.25, with no
     * comment. This constant, which explains itself, was used only by {@link #run}, where the frame time is always
     * DT and the clamp can therefore never fire. So the documented copy was dead in its own path and the live copy
     * was an unexplained number in another file, which is where a drift between them would start.
     *
     * <p>The screen uses this constant now, so the two cannot disagree, and {@link #advance} gives the engine a path
     * where the clamp is reachable and therefore testable - it was unreachable from any suite before.
     */
    public static final double MAX_FRAME = 0.25; // spiral of death protection
    
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
            advance(frameTime);
            t += frameTime;
        }
    }

    /**
     * Advance by one frame of real time, clamped, and return how many physics steps ran.
     *
     * <p>This is the whole loop, factored out so the clamp is reachable: a five-second hitch must advance the world
     * by a quarter of a second rather than by three hundred steps, which is what the spiral of death is. Before this
     * existed, MAX_FRAME could only be exercised by the game layer's own copy of the same number.
     */
    public int advance(double frameTime) {
        double clamped = Math.min(frameTime, MAX_FRAME);
        accumulator += clamped;
        int steps = 0;
        while (accumulator >= DT) {
            world.update(DT);
            accumulator -= DT;
            gameTime += DT;
            steps++;
        }
        return steps;
    }
    
    /** For real rendering: interpolation = accumulator / DT. */
    public double alpha() {
        return accumulator / DT;
    }
}
