package tropical.engine;

/**
 * AI Director — L4D-style pacing system that watches player performance
 * and adjusts spawn intensity in real time. The "game feels alive" layer.
 *
 * States:
 *   BUILD   — intensity rising, spawns throttled to build tension
 *   PEAK    — max intensity, full spawns, the climax
 *   RELAX   — backing off, reduced spawns, player catches breath
 *   RECOVER — mercy window, minimal spawns, player recovers
 *
 * Key signals:
 *   intensity    — how hard the game is pushing right now (0-1)
 *   frustration  — EMA of player struggles (damage taken, near misses)
 *   performance  — how well the player is doing (kills, accuracy)
 *
 * The director adjusts spawn rates based on these signals, creating
 * dynamic pacing that adapts to player skill in real time.
 */
public class AIDirector {
    public enum State { BUILD, PEAK, RELAX, RECOVER }

    private State state = State.BUILD;
    private double stateTime = 0.0;

    // Intensity signal (0-1, how hard we're pushing)
    private double intensity = 0.0;
    private double targetIntensity = 0.3;

    // Frustration tracking (exponential decay, time-based)
    private double frustration = 0.0;
    private final double frustrationDecayK = 0.4;   // per second (half-life ~1.7s)
    private final double frustrationThreshold = 0.7;

    // Performance tracking (exponential decay, time-based)
    private double performance = 0.5;
    private final double performanceDecayK = 0.5;   // per second

    // Spawn throttling
    private double spawnMultiplier = 1.0;
    private double minSpawnMultiplier = 0.2;
    private double maxSpawnMultiplier = 2.0;

    // Mercy window
    private boolean mercyActive = false;
    private double mercyTimer = 0.0;
    private final double mercyDuration = 3.0;

    // State durations (how long to stay in each state)
    private final double buildDuration = 8.0;
    private final double peakDuration = 5.0;
    private final double relaxDuration = 4.0;
    private final double recoverDuration = 3.0;

    // Event accumulator for this tick
    private double damageTaken = 0.0;
    private double enemiesKilled = 0.0;
    private double nearMisses = 0.0;

    /**
     * Call this when the player takes damage.
     * @param amount HP lost (normalized 0-1)
     */
    public void onPlayerDamaged(double amount) {
        damageTaken += amount * 0.3;  // damage is significant frustration
    }

    /**
     * Call this when an enemy is killed.
     * @param threatLevel how dangerous the enemy was (0-1)
     */
    public void onEnemyKilled(double threatLevel) {
        enemiesKilled += threatLevel * 0.2;
    }

    /**
     * Call this when the player barely avoids damage (near miss).
     */
    public void onNearMiss() {
        nearMisses += 0.1;
    }

    /**
     * Update director state and intensity. Call once per tick.
     * @param dt Delta time in seconds
     */
    public void update(double dt) {
        // Update frustration: exponential decay toward 0, plus this tick's events.
        // Decay must be time-based: frustration *= exp(-k * dt).
        // (A per-tick constant like 0.95 decays 0.95^60 ≈ 0.05 per second
        // at 60fps — events vanish before they can accumulate.)
        double frustrationDelta = damageTaken + nearMisses * 0.5;
        frustration *= Math.exp(-frustrationDecayK * dt);
        frustration += frustrationDelta;
        frustration = Math.min(1.0, frustration);

        // Update performance: same time-based decay
        double performanceDelta = enemiesKilled;
        performance *= Math.exp(-performanceDecayK * dt);
        performance += performanceDelta;
        performance = Math.max(0.0, Math.min(1.0, performance));

        // Clear tick accumulators
        damageTaken = 0.0;
        enemiesKilled = 0.0;
        nearMisses = 0.0;

        // Check for mercy activation
        if (frustration > frustrationThreshold && state != State.RECOVER) {
            transitionTo(State.RECOVER);
            mercyActive = true;
            mercyTimer = mercyDuration;
        }

        // Update state timer
        stateTime += dt;

        // State machine
        switch (state) {
            case BUILD:
                targetIntensity = 0.3 + performance * 0.3;  // 0.3-0.6 based on performance
                if (stateTime >= buildDuration) {
                    transitionTo(State.PEAK);
                }
                break;

            case PEAK:
                targetIntensity = 0.8 + performance * 0.2;  // 0.8-1.0
                if (stateTime >= peakDuration) {
                    transitionTo(State.RELAX);
                }
                break;

            case RELAX:
                targetIntensity = 0.3 - frustration * 0.2;  // 0.1-0.3, lower if frustrated
                targetIntensity = Math.max(0.1, targetIntensity);
                if (stateTime >= relaxDuration) {
                    transitionTo(State.BUILD);
                }
                break;

            case RECOVER:
                targetIntensity = 0.1;  // minimal during mercy
                mercyTimer -= dt;
                if (mercyTimer <= 0) {
                    mercyActive = false;
                    transitionTo(State.BUILD);
                }
                break;
        }

        // Smooth intensity toward target
        double intensityRate = 0.5;  // per second
        if (intensity < targetIntensity) {
            intensity = Math.min(targetIntensity, intensity + intensityRate * dt);
        } else {
            intensity = Math.max(targetIntensity, intensity - intensityRate * dt);
        }

        // Compute spawn multiplier from intensity and state
        switch (state) {
            case BUILD:
                spawnMultiplier = 0.5 + intensity * 0.5;  // 0.5-1.0
                break;
            case PEAK:
                spawnMultiplier = 1.0 + intensity * 0.5;  // 1.0-1.5
                break;
            case RELAX:
                spawnMultiplier = 0.3 + intensity * 0.3;  // 0.3-0.6
                break;
            case RECOVER:
                spawnMultiplier = minSpawnMultiplier;  // 0.2
                break;
        }

        spawnMultiplier = Math.max(minSpawnMultiplier,
                                   Math.min(maxSpawnMultiplier, spawnMultiplier));
    }

    private void transitionTo(State newState) {
        state = newState;
        stateTime = 0.0;
    }

    // --- Getters ---

    public State getState() { return state; }
    public double getIntensity() { return intensity; }
    public double getFrustration() { return frustration; }
    public double getPerformance() { return performance; }
    public double getSpawnMultiplier() { return spawnMultiplier; }
    public boolean isMercyActive() { return mercyActive; }
    public double getMercyTimer() { return mercyTimer; }

    /**
     * Should we spawn an enemy right now?
     * @param baseChance Base spawn chance (0-1)
     * @return Modified spawn chance based on director state
     */
    public double modifySpawnChance(double baseChance) {
        return baseChance * spawnMultiplier;
    }

    /**
     * How many enemies should be in the active pool?
     * @param baseCount Base enemy count
     * @return Modified count based on intensity
     */
    public int modifyEnemyCount(int baseCount) {
        return (int) Math.ceil(baseCount * spawnMultiplier);
    }
}
