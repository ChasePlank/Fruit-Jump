package tropical.engine;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * WaterSystem - everything water does to a body, and everything the game
 * needs to know about it.
 *
 * Split from {@link Water} on purpose: Water is the level's geometry (pure
 * queries, freely shareable), this is per-body state and per-frame response.
 *
 * The frame order matters and mirrors World.step:
 *
 *   applyMedium(b, dt)   BEFORE gravity. Returns true when water has already
 *                        accounted for vertical motion (swimming), so gravity
 *                        must NOT be added on top - buoyancy and gravity in
 *                        the same frame would double-count and make swimming
 *                        feel either leaden or floaty depending on depth.
 *   ...body moves and collides...
 *   postStep(b, dt)      AFTER the move. Recomputes state at the new position,
 *                        fires the splash on a surface crossing, and runs the
 *                        breath meter. Splash detection lives here rather than
 *                        in applyMedium because the crossing happens DURING
 *                        the move: the pre-move position is dry, the post-move
 *                        position is wet, and only the comparison of the two
 *                        says "that was an entry".
 *
 * Bodies are keyed by identity (Physics.Body has no equals), so a body that
 * is removed simply drops out of the map.
 */
public class WaterSystem {

    // --- tuning ------------------------------------------------------------

    /** Water must be at least this deep to swim in at all (1.5 tiles). */
    public static final double SWIM_DEPTH = 48.0;

    /** Submersion at which a body in deep water starts to swim. */
    public static final double WADE_SUB = 0.35;

    /**
     * Submersion at which a swimming body stops swimming. Lower than WADE_SUB
     * on purpose: the two thresholds make the state hysteretic.
     *
     * A single threshold flip-flops at the surface, and the flip-flop has a
     * consequence that is easy to miss. A swimmer holding UP only strokes while
     * "swimming"; as they rise, submersion drops through the threshold, the
     * stroke stops mid-breach, gravity reasserts, and they fall back. Their
     * peak height lands a few pixels short of the surface, every time, forever
     * - a body bobbing at the water line that can never get out under its own
     * power. Hysteresis keeps the stroke alive until the body is genuinely out.
     */
    public static final double WADE_EXIT = 0.12;

    /**
     * Buoyancy as a multiple of gravity at full submersion. Above 1.0, so a
     * fully sunk body is pushed up; the equilibrium is 1/BUOYANCY (~0.74),
     * where head and shoulders sit above the surface. The body finds the
     * surface on its own - no "float to the top" special case to get wrong.
     */
    public static final double BUOYANCY = 1.35;

    public static final double DRAG_X = 2.5;        // per second, swimming
    public static final double DRAG_Y = 4.0;
    public static final double ENTRY_DRAG_Y = 8.0;  // heavier on entry - see below
    public static final double ENTRY_TIME = 0.4;    // seconds of entry drag
    public static final double WADE_DRAG = 5.0;     // shallow water resists walking
    public static final double STROKE_ACC = 950.0;  // holding up, px/s^2
    public static final double DIVE_ACC = 700.0;    // holding down
    public static final double SWIM_UP_MAX = 165.0; // px/s - slower than running
    public static final double SINK_MAX = 140.0;
    public static final double SWIM_ACCEL = 620.0;  // how fast vx chases the steer target
    public static final double SWIM_SPEED = 0.60;   // x run speed while swimming
    public static final double WADE_SPEED = 0.72;
    public static final double BREATH_SECONDS = 12.0;
    public static final double BREATH_REFILL = 3.0; // x realtime at the surface
    public static final double DROWN_INTERVAL = 1.0;
    public static final double SPLASH_MIN_V = 120.0;

    /** Breach hop: a jump taken while floating at the surface. */
    public static final double BREACH_JUMP = 1.25;
    /** A jump taken while fully under water is a paddle, not a leap. */
    public static final double UNDERWATER_JUMP = 0.45;

    // --- state -------------------------------------------------------------

    private Water water;
    private int verticalInput = 0;   // -1 up, +1 down, 0 neutral

    private final Map<Physics.Body, State> states = new IdentityHashMap<>();

    private boolean splashPending;
    private double splashX, splashY, splashSpeed;
    private int splashEvents;   // lifetime count - survives consumeSplash, for tests/stats

    static final class State {
        double air = BREATH_SECONDS;
        double drownTimer = 0;
        int pendingDrown = 0;
        double entryTimer = 0;   // seconds of extra entry drag remaining
        boolean wasInWater = false;
        boolean deep = false;
        boolean swim = false;
        boolean wasSwim = false;   // hysteresis for the swim/wade threshold
    }

    private State state(Physics.Body b) {
        return states.computeIfAbsent(b, k -> new State());
    }

    // --- configuration -----------------------------------------------------

    public void setWater(Water w) {
        this.water = w;
    }

    public Water water() { return water; }

    /** Vertical swim input, written by the game each frame. */
    public void setVerticalInput(int dir) {
        this.verticalInput = (dir < 0) ? -1 : (dir > 0 ? 1 : 0);
    }

    public int verticalInput() { return verticalInput; }

    /** Forget a body (call if a body is permanently removed from the world). */
    public void forget(Physics.Body b) {
        states.remove(b);
    }

    // --- queries -----------------------------------------------------------

    private boolean active() {
        return water != null && !water.isEmpty();
    }

    public boolean inWater(Physics.Body b) { return b.inWater; }

    public double submersion(Physics.Body b) { return b.submersion; }

    public boolean swimming(Physics.Body b) {
        State s = states.get(b);
        return s != null && s.swim;
    }

    public boolean deep(Physics.Body b) {
        State s = states.get(b);
        return s != null && s.deep;
    }

    /** Air remaining, in seconds. */
    public double air(Physics.Body b) {
        State s = states.get(b);
        return (s == null) ? BREATH_SECONDS : s.air;
    }

    /** Air as 0..1, for a HUD meter. */
    public double airFraction(Physics.Body b) {
        return air(b) / BREATH_SECONDS;
    }

    /** Movement multiplier for the game to apply to its own speed target. */
    public double speedMultiplier(Physics.Body b) {
        State s = states.get(b);
        if (s == null || !b.inWater) return 1.0;
        if (s.swim) return SWIM_SPEED;
        return WADE_SPEED;
    }

    /**
     * Steer horizontal velocity while swimming: chase the desired velocity
     * instead of snapping to it, so the body carries momentum in and out of
     * the water. Call this instead of assigning body.vx while swimming.
     */
    public double steerVx(Physics.Body b, double desiredVx, double dt) {
        State s = states.get(b);
        if (s == null || !s.swim) return desiredVx;
        double target = desiredVx * SWIM_SPEED;
        double dv = target - b.vx;
        double maxStep = SWIM_ACCEL * dt;
        return b.vx + Math.max(-maxStep, Math.min(maxStep, dv));
    }

    /**
     * Jump velocity, adjusted for the water the body is in.
     *
     * Shallow water is ignored entirely - a puddle should never make jumping
     * feel different. Deep water gives three regimes: a breach hop at the
     * surface (how you climb out of a pool), a paddle when fully under (a
     * jump is not a way out of the deep), and nothing special in between.
     */
    public double jumpV(Physics.Body b, double baseJumpV) {
        State s = states.get(b);
        if (s == null || !b.inWater || !s.deep) return baseJumpV;
        if (b.submersion >= 0.90) return baseJumpV * UNDERWATER_JUMP;
        return baseJumpV * BREACH_JUMP;
    }

    /** Damage ticks owed to drowning; drains the counter. */
    public int drainDrownTicks(Physics.Body b) {
        State s = states.get(b);
        if (s == null) return 0;
        int n = s.pendingDrown;
        s.pendingDrown = 0;
        return n;
    }

    // --- splashes ----------------------------------------------------------

    public boolean consumeSplash() {
        boolean p = splashPending;
        splashPending = false;
        return p;
    }

    /** How many surface crossings have thrown a splash since this system was
     *  built. Not cleared by consumeSplash - the counter is the observable
     *  history, the flag is the one-frame event. */
    public int splashEvents() { return splashEvents; }

    public double splashX() { return splashX; }
    public double splashY() { return splashY; }
    public double splashSpeed() { return splashSpeed; }

    // --- per-frame ---------------------------------------------------------

    /**
     * Apply the medium's forces. Returns true if vertical motion was handled
     * (the caller must then skip gravity).
     */
    public boolean applyMedium(Physics.Body b, double dt) {
        if (!active()) {
            b.inWater = false;
            b.submersion = 0;
            return false;
        }

        double sub = water.submersion(b);
        double depth = water.depthUnder(b);
        boolean deep = depth >= SWIM_DEPTH;
        State s = state(b);
        // Hysteretic: enter swimming at WADE_SUB, stay until WADE_EXIT (see
        // WADE_EXIT - a single threshold stops the stroke mid-breach and the
        // body can never get out of the water under its own power).
        boolean swim = deep && (sub >= WADE_SUB || (s.wasSwim && sub > WADE_EXIT));
        s.swim = swim;

        b.submersion = sub;
        b.inWater = sub > 0;

        if (sub <= 0) return false;

        double[] cur = water.currentAt(b.x, b.y);

        if (swim) {

            // Net vertical force, in one term: buoyancy up minus weight down,
            // as a fraction of gravity. Zero at sub = 1/BUOYANCY (~0.74), and
            // restoring on both sides - shallower than that sinks, deeper
            // floats up - so the body finds the surface with no "float to the
            // top" special case and no depth test.
            //
            // The sign here was backwards on the first pass, and the failure
            // was instructive: anti-restoring buoyancy (up when shallow, down
            // when deep) does not look like a sign error in a screenshot. It
            // looks like a body that rises out of the water, sinks back,
            // hovers at the wading threshold and jitters there forever.
            b.vy += (1.0 - BUOYANCY * sub) * Physics.GRAVITY * dt;

            // Drag is applied to velocity RELATIVE TO THE WATER, not to
            // absolute velocity. That single change is what makes currents
            // work: a river's flow is a property of the water, and drag is
            // what couples the body to it. With absolute-velocity drag, a
            // current pulling at 85 px/s only moves a body at ~38 px/s,
            // because drag keeps dragging it back toward a standstill - the
            // water fights itself. Relative drag lets a current carry a body
            // at the current's own speed, and it gets there without a second
            // "current force" term competing with the first.
            double waterVx = (cur == null) ? 0 : cur[0];
            double waterVy = (cur == null) ? 0 : cur[1];

            // Exponential, so it is dt-correct: a per-tick multiplier
            // (v *= 0.95) is a different force at every frame rate (that
            // mistake made the director's intensity decay to 5% of its rate
            // at 60Hz).
            //
            // Scaled by submersion, because only the submerged part of a body
            // is pushing through water. That also makes BREACHING smooth: a
            // threshold would cut the drag off the instant the body left the
            // "swimming" band, so a jump out of a pool would lose most of its
            // horizontal momentum in the last few pixels, while fading it by
            // submersion lets the water release the body gradually.
            b.vx = waterVx + (b.vx - waterVx) * Math.exp(-DRAG_X * sub * dt);

            // Entry drag: a body arriving at the surface at 500+ px/s would
            // otherwise drive most of the way to the bottom of the pool
            // before steady drag arrested it (stopping distance is roughly
            // v/k). Heavier drag for the first fraction of a second reads as
            // water absorbing the impact, which is also what actually happens.
            double dragY = DRAG_Y + (ENTRY_DRAG_Y - DRAG_Y)
                           * Math.min(1.0, s.entryTimer / ENTRY_TIME);
            b.vy = waterVy + (b.vy - waterVy) * Math.exp(-dragY * sub * dt);

            // Stroke and dive are speed-limited by capping the velocity the
            // input itself is driving, rather than clamping body.vy. A hard
            // clamp on vy would eat a breach jump: the body leaves the water
            // at submersion ~0.74, well inside any "swimming" band, so the
            // clamp would cut -525 px/s down to -165 and the hop would barely
            // clear a lip. Capping only what the stroke adds leaves an
            // externally-set velocity (a jump, a hookshot, a knockback) intact.
            if (verticalInput < 0) {
                double want = b.vy - STROKE_ACC * dt;
                b.vy = Math.max(want, Math.min(b.vy, -SWIM_UP_MAX));
            } else if (verticalInput > 0) {
                double want = b.vy + DIVE_ACC * dt;
                b.vy = Math.min(want, Math.max(b.vy, SINK_MAX));
            }

            // Impact absorption: only during the entry window, which is the
            // only time a body reaches the sink cap (a dive's terminal
            // velocity under drag is ~70 px/s, well under it).
            if (b.vy > SINK_MAX && s.entryTimer > 0) b.vy = SINK_MAX;
            return true;
        }

        // Wading: normal gravity, water just resists. No buoyancy - a puddle
        // must not lift a body off its feet.
        double wadeVx = (cur == null) ? 0 : cur[0];
        b.vx = wadeVx + (b.vx - wadeVx) * Math.exp(-WADE_DRAG * sub * dt);
        return false;
    }

    /** Recompute state after movement: splashes and breath. */
    public void postStep(Physics.Body b, double dt) {
        if (!active()) return;

        State s = state(b);
        if (s.entryTimer > 0) s.entryTimer = Math.max(0, s.entryTimer - dt);

        double sub = water.submersion(b);
        double depth = water.depthUnder(b);
        double surfaceY = water.surfaceUnder(b);

        s.deep = depth >= SWIM_DEPTH;
        s.swim = s.deep && (sub >= WADE_SUB || (s.wasSwim && sub > WADE_EXIT));
        s.wasSwim = s.swim;

        boolean wasIn = s.wasInWater;
        boolean nowIn = sub > 0;

        b.submersion = sub;
        b.inWater = nowIn;

        // Surface crossing with speed: the splash. Both directions count -
        // a body breaching upward throws water just as an entry does.
        if (nowIn != wasIn && Math.abs(b.vy) >= SPLASH_MIN_V) {
            splashPending = true;
            splashEvents++;
            splashX = b.x;
            splashY = Double.isNaN(surfaceY) ? b.y : surfaceY;
            splashSpeed = Math.abs(b.vy);
        }
        if (nowIn && !wasIn) s.entryTimer = ENTRY_TIME;
        s.wasInWater = nowIn;

        // Breath: only the head matters. Measured at the head because a body
        // floating at equilibrium has most of its height under water and is
        // breathing fine - depleting air on SUBMERSION would drown a swimmer
        // who never went under.
        boolean headUnder = s.deep && !Double.isNaN(surfaceY)
                            && (b.y - b.hh) >= surfaceY - 1.0;

        if (headUnder) {
            s.air -= dt;
            if (s.air <= 0) {
                s.air = 0;
                s.drownTimer += dt;
                while (s.drownTimer >= DROWN_INTERVAL) {
                    s.drownTimer -= DROWN_INTERVAL;
                    s.pendingDrown++;
                }
            }
        } else {
            s.air = Math.min(BREATH_SECONDS, s.air + dt * BREATH_REFILL);
            s.drownTimer = 0;
        }
    }
}
