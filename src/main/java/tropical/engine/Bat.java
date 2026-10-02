package tropical.engine;

/**
 * Bat - a flying pursuer that cannot hurt you, only knock you down.
 *
 * Design intent (Kinger, Sept 29): the bat FOLLOWS, and the follow is
 * abusable ON PURPOSE. The player can lead it away from a gap, or bait its
 * dive and then move. That makes the bat positioning play - the player uses
 * it - rather than just another timed jump. Bats CAN engage over gaps; there
 * is no placement restriction, and the reason that is fair is that gaps are
 * climbable spike pits, so a mid-air knockover is a setback and not a death.
 *
 * THE CONSTANT THAT MAKES THE LURE EXIST
 * PURSUE_SPEED must stay BELOW the player's run speed (200). A bat that can
 * outrun the player is a chase, not a tool - you cannot lead something that
 * simply catches you. Flying is what makes it threatening at that speed: it
 * ignores terrain, so it does not need to be fast to be dangerous.
 *
 * THE FAIR PART
 * Once a swoop starts the dive vector is FIXED - it cannot steer. So a
 * committed dive can be baited into empty space, and a missed dive costs it
 * a recovery window before it can pursue again. That is the punish.
 *
 * The knockover does not remove the bat; it makes it disengage. That retreat
 * is load-bearing twice over: it stops a non-damaging stun becoming a
 * stun-lock, and it is what creates the cycle the player re-lures.
 */
public class Bat {
    public enum State { WANDER, PURSUE, SWOOP, RETREAT }

    static int nextId = 0;
    public final int id = nextId++;

    public final Physics.Body body;
    public State state = State.WANDER;

    /** Player run speed is 200. Keep pursue below it or the lure dies. */
    public static final double WANDER_SPEED = 55;
    public static final double PURSUE_SPEED = 130;
    public static final double SWOOP_SPEED = 330;
    public static final double RETREAT_SPEED = 215;

    public static final double AGGRO_RANGE = 260;
    public static final double SWOOP_RANGE = 150;
    public static final double SWOOP_TIME = 0.5;      // committed dive
    public static final double SWOOP_RECOVER = 0.85;  // punish window on a miss
    public static final double RETREAT_TIME = 1.5;
    /** After a hit the bat will not re-engage for this long. Without it the
     *  retreat is cosmetic: the bat comes straight back and the player is
     *  flat ~1s out of every ~1.75s, which is a stun-lock in practice even
     *  though every individual stun ends correctly. Found by test, not by
     *  reading. */
    public static final double REAGGRO_DELAY = 3.5;
    public static final double STUN_SECONDS = 1.0;    // player flat on the ground
    static final double HIT_COOLDOWN = 0.5;           // no instant re-hit

    double timer = 0;        // state timer
    double recover = 0;      // cannot swoop again until this runs out
    double hitCooldown = 0;
    double swoopVX, swoopVY;
    double aggroLock = 0;   // cannot re-engage until this runs out
    double wanderAngle;
    final java.util.Random rng;

    public Bat(double x, double y, long seed) {
        this.body = new Physics.Body(x, y, 20, 16);
        this.body.noGravity = true;          // it flies
        this.rng = new java.util.Random(seed);
        this.wanderAngle = rng.nextDouble() * Math.PI * 2;
    }

    /**
     * Break off and leave. Called on EVERY bat the moment any one of them
     * knocks the player down.
     *
     * With three bats in a level the old behaviour was a relay: each took its
     * turn stunning the player, so a cluster of bats meant a stun-lock that got
     * longer the more of them there were (playtest, Sept 29). One knockdown
     * now clears the whole swarm, and they only come back after their own
     * re-aggro delays.
     */
    public void flee() {
        state = State.RETREAT;
        timer = RETREAT_TIME;
        recover = 0;
        // Set the re-aggro lock here, not only when the retreat ends. A bat
        // that is updated AFTER flee() in the same frame (the loop runs
        // backwards) can leave RETREAT immediately if it is already far away -
        // harmless on its own, but without the lock it could re-engage the
        // moment it drifted back. The lock is what actually guarantees the
        // player gets their second.
        aggroLock = REAGGRO_DELAY;
    }

    /** Advance the bat. Returns true if it connected with the player. */
    public boolean update(double dt, Physics.Body player) {
        if (hitCooldown > 0) hitCooldown -= dt;
        if (recover > 0) recover -= dt;
        if (aggroLock > 0) aggroLock -= dt;

        double dx = player.x - body.x;
        double dy = player.y - body.y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double inv = dist > 1e-6 ? 1.0 / dist : 0;
        boolean connected = false;

        switch (state) {
            case WANDER: {
                timer -= dt;
                if (timer <= 0) {
                    timer = 0.5 + rng.nextDouble() * 1.1;
                    wanderAngle += (rng.nextDouble() - 0.5) * 1.9;
                }
                body.vx = Math.cos(wanderAngle) * WANDER_SPEED;
                body.vy = Math.sin(wanderAngle) * WANDER_SPEED;
                if (dist < AGGRO_RANGE && aggroLock <= 0) {
                    state = State.PURSUE;
                }
                break;
            }
            case PURSUE: {
                body.vx = dx * inv * PURSUE_SPEED;
                body.vy = dy * inv * PURSUE_SPEED;
                // Give up if the player gets far away (hysteresis so it
                // does not flicker in and out of pursuit at the edge).
                if (dist > AGGRO_RANGE * 1.6) {
                    state = State.WANDER;
                    timer = 0;
                    break;
                }
                // Commit to a dive only from ABOVE (dy > 0 means the
                // player is lower on screen) and only when off cooldown.
                // A bat that has just been shrugged off cannot dive again,
                // which is the window the player earns by baiting it.
                if (dist < SWOOP_RANGE && dy > 0 && recover <= 0 && hitCooldown <= 0) {
                    state = State.SWOOP;
                    timer = SWOOP_TIME;
                    swoopVX = dx * inv * SWOOP_SPEED;
                    swoopVY = dy * inv * SWOOP_SPEED;
                }
                break;
            }
            case SWOOP: {
                // Fixed vector - no steering. This is the bait window.
                body.vx = swoopVX;
                body.vy = swoopVY;
                timer -= dt;
                if (hitCooldown <= 0 && body.aabb().overlaps(player.aabb())) {
                    connected = true;
                    hitCooldown = HIT_COOLDOWN;
                    // The knockover does not remove the bat; it makes it
                    // leave. Never allow it to re-engage before the player
                    // can move again, or a non-damaging stun becomes a
                    // stun-lock and the player never gets to play.
                    state = State.RETREAT;
                    timer = RETREAT_TIME;
                } else if (timer <= 0) {
                    state = State.PURSUE;
                    recover = SWOOP_RECOVER;
                }
                break;
            }
            case RETREAT: {
                timer -= dt;
                // Just past the range is enough (playtest: "level 2 bat flew
                // off screen, it doesnt need to fly that far, just a little
                // past the range"). Flying out of sight makes the bat
                // forgettable, and the point of the follow is that the player
                // can SEE it and plan around it.
                if (dist > AGGRO_RANGE * 1.15 || timer <= 0) {
                    body.vx = 0;
                    body.vy = 0;
                    state = State.WANDER;
                    timer = 0;
                    recover = 0;
                    aggroLock = REAGGRO_DELAY;
                } else {
                    // Fly UP and away, not along the line back from the
                    // player: retreating along that line drives the bat into
                    // the ground it just swooped at, where it stays in range
                    // and re-hits immediately.
                    double away = (dx >= 0) ? -1 : 1;
                    body.vx = away * RETREAT_SPEED;
                    body.vy = -RETREAT_SPEED * 0.85;
                }
                break;
            }
        }
        return connected;
    }
}
