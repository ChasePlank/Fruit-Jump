package tropical.engine;

/**
 * JumpFeel - the input-forgiveness layer for the platformer's jump.
 *
 * The raw jump in GameplayScreen is one impulse: {@code if (grounded) vy = JUMP_V}.
 * That is a jump that only works if the player presses on exactly the right frame,
 * holds for exactly the right time, and never wants a small hop. Four standard
 * fixes, each a window instead of an instant:
 *
 *   COYOTE TIME      you may still jump for a moment after walking off a ledge
 *   JUMP BUFFER      a press just before landing fires on the landing frame
 *   VARIABLE HEIGHT  releasing early cuts the jump, so a tap is a short hop
 *   APEX HANG        gravity eases near the top, so the peak is readable
 *
 * THE CONTRACT THIS MODULE EXISTS TO KEEP: a full-hold jump must never get
 * WORSE. LevelGen's guarantees - a 2-cell step is climbable, a 3-cell gap is
 * jumpable - were measured against the stock 73px apex and ~140px arc, and
 * those numbers are baked into the generator and its validator. So the tuning
 * here only ever makes the apex higher and the arc longer, and
 * {@link #CALIBRATED_JUMP_V} is the measured launch velocity that restores the
 * stock apex exactly, for anyone who would rather keep the arc identical.
 *
 * Usage per frame, in GameplayScreen:
 * <pre>
 *   feel.update(dt, player.grounded, player.vy);
 *   if (feel.consumeJump(player.vy)) player.vy = JumpFeel.CALIBRATED_JUMP_V;
 *   player.vy = feel.cutVelocity(player.vy);
 *   player.gravityScale = feel.gravityScale(player.vy);
 * </pre>
 * with {@code feel.press()} on the jump key going down and {@code feel.release()}
 * on the way up.
 */
public class JumpFeel {

    /** Grace period after leaving the ground during which a jump still fires. */
    public static final double COYOTE_TIME = 0.10;      // 6 frames at 60Hz
    /** A press this long before landing still fires, on the landing frame. */
    public static final double JUMP_BUFFER = 0.12;      // 7 frames at 60Hz
    /** Velocity retained when the jump is released early. */
    public static final double CUT_MULTIPLIER = 0.45;
    /** Vertical speed under which a jump counts as "near its apex". */
    public static final double APEX_VY = 90.0;
    /** Gravity multiplier near the apex (1.0 = off). */
    public static final double APEX_GRAVITY = 0.55;

    /** The stock jump velocity (GameplayScreen.JUMP_V). */
    public static final double BASE_JUMP_V = -420.0;
    /**
     * Launch velocity that reproduces the stock apex WITH apex hang enabled.
     * Measured by JumpFeelTest, which bisects for it and fails if this number
     * drifts: apex hang raises the peak, so the launch has to come down to
     * keep the generator's clearance guarantees exactly as they were.
     */
    public static final double CALIBRATED_JUMP_V = -414.5;

    private double coyote = 0;
    private double buffer = 0;
    private boolean held = false;
    private boolean cutUsed = false;
    private boolean inJump = false;

    /**
     * Advance the timers. Call once per frame, before consumeJump.
     *
     * The vy argument is not decoration. On the launch frame the ground probe
     * still reports grounded - the collision from the previous frame is the
     * last thing the physics knows - so a module that treats "grounded" as "back
     * on the ground" cancels its own jump on the frame it starts. The result is
     * invisible in every test that only looks at whether the body left the
     * ground, and total in the ones that look at the arc: apex hang never
     * applies and release-to-cut never fires, because neither ever sees a jump.
     * Rising off the ground is not landing on it.
     */
    public void update(double dt, boolean grounded, double vy) {
        if (grounded && vy >= 0) {
            coyote = COYOTE_TIME;
            inJump = false;
        } else if (!grounded && coyote > 0) {
            coyote = Math.max(0, coyote - dt);
        }
        if (buffer > 0) buffer = Math.max(0, buffer - dt);
    }

    /** Jump key went down. */
    public void press() {
        buffer = JUMP_BUFFER;
        held = true;
        cutUsed = false;
    }

    /** Jump key came up. */
    public void release() {
        held = false;
    }

    /**
     * Should a jump fire this frame? Consumes the buffer, so it fires once.
     *
     * The vy gate is what stops coyote time from becoming a second jump off a
     * bounce: a body already flying upward (a stomp bounce, a knockback) is not
     * "walking off a ledge", and letting it jump would chain into unbounded
     * height.
     */
    public boolean consumeJump(double vy) {
        if (buffer > 0 && coyote > 0 && vy > -50) {
            buffer = 0;
            coyote = 0;
            inJump = true;
            cutUsed = false;
            return true;
        }
        return false;
    }

    /**
     * Release-to-cut: the first release while rising takes the jump's remaining
     * speed down to CUT_MULTIPLIER of it. Once only - repeated release events
     * (key bounce, focus loss) must not keep shaving the same jump.
     */
    public double cutVelocity(double vy) {
        if (inJump && !held && !cutUsed && vy < 0) {
            cutUsed = true;
            return vy * CUT_MULTIPLIER;
        }
        return vy;
    }

    /**
     * Gravity multiplier for this frame. Hang applies only to an actual jump
     * (inJump), so walking off a ledge and falling normally still feel like
     * falling - otherwise every small drop would float.
     */
    public double gravityScale(double vy) {
        if (!inJump) return 1.0;
        return (Math.abs(vy) < APEX_VY) ? APEX_GRAVITY : 1.0;
    }

    public boolean inJump() { return inJump; }
    public double coyoteLeft() { return coyote; }
    public double bufferLeft() { return buffer; }
}
