package tropical.engine;

/**
 * A fish that lives in the water and bites.
 *
 * <p>Kinger's ask, verbatim: "a new enemy, piranha, like bats, but does hurt instead of stun, and spawn in
 * groups". The bat is the model and the differences are the point:
 *
 * <ul>
 *   <li><b>It hurts, it does not stun.</b> A bat knocks you down and you get up; a piranha takes a heart. That
 *       makes it the only water threat, and it means water stops being the safe landing a missed jump used to
 *       get - which is the whole reason water was rare enough to be harmless before.
 *   <li><b>It only moves when YOU are in the water.</b> A fish out of water is not a threat and should not
 *       pretend to be one, so it idles at its pool and chases only when the player is swimming. That is also
 *       what makes the surface a decision rather than a wall: get out and it loses you.
 *   <li><b>Slower than the player's swim.</b> 95 against a swim that crosses a pool in a moment - it cannot
 *       catch you in open water, only corner you, which is what a group is for.
 *   <li><b>In groups.</b> Placed three to five at once in one pool, so the threat is the pool, not the fish.
 * </ul>
 */
public class Piranha {
    public enum State { IDLE, CHASE, BITE, RECOVER }

    static int nextId = 0;
    public final int id = nextId++;

    public final Physics.Body body;
    public State state = State.IDLE;

    /** Slower than the player swims. It corners; it does not run anyone down. */
    public static final double CHASE_SPEED = 95;
    public static final double IDLE_SPEED = 28;

    public static final double AGGRO_RANGE = 220;   // how far it notices a swimmer
    public static final double BITE_RANGE = 24;     // body to body
    public static final double BITE_TIME = 0.25;    // committed lunge
    public static final double RECOVER_TIME = 1.1;  // punish window after a miss
    /** After a bite it will not re-engage for this long. Without it a group is a stun-lock with extra steps. */
    public static final double REAGGRO_DELAY = 2.5;
    public static final double HIT_COOLDOWN = 0.6;

    // NO DAMAGE CONSTANT HERE. This had `DAMAGE = 1.0` with a docstring saying "one heart, the same as a spike",
    // and nothing read it: the bite is a SIGNAL and the screen turns it into damage through Combat, which owns
    // the amount. So the constant was a second, unread copy of a number that lives in one place - the same class
    // as the other dead knobs the engine audit found (Boss.maxHP's twin, Hookshot.HOOK_SPEED).
    //
    // What made it worth removing rather than wiring: the mutation sweep caught `DAMAGE = 0.0` NOWHERE, because
    // a constant nothing reads cannot change any behaviour. A dead knob that also looks load-bearing is worse
    // than an obvious one.

    double timer = 0;
    double hitCooldown = 0;
    double aggroLock = 0;
    double lungeVX, lungeVY;
    final java.util.Random rng;

    public Piranha(double x, double y, long seed) {
        this.body = new Physics.Body(x, y, 22, 16);
        this.body.noGravity = true;          // it swims
        this.rng = new java.util.Random(seed);
    }

    /**
     * One step.
     *
     * @return true if it bit the player this frame
     */
    public boolean update(double dt, Physics.Body player, WaterSystem water) {
        if (hitCooldown > 0) hitCooldown -= dt;
        if (aggroLock > 0) aggroLock -= dt;
        timer += dt;

        // It only cares about a player who is IN the water. On the bank it is scenery, and that is deliberate:
        // the surface has to be a way out, or a flooded level is a wall rather than a level.
        boolean playerWet = water != null && water.swimming(player);
        double dx = player.x - body.x, dy = player.y - body.y;
        double dist = Math.hypot(dx, dy);

        switch (state) {
            case IDLE -> {
                if (playerWet && dist < AGGRO_RANGE && aggroLock <= 0) state = State.CHASE;
                else drift();
            }
            case CHASE -> {
                if (!playerWet || dist > AGGRO_RANGE * 1.4) { state = State.IDLE; break; }
                if (dist < BITE_RANGE) { state = State.BITE; timer = 0; lunge(dx, dy, dist); break; }
                if (dist > 1) {
                    body.vx = dx / dist * CHASE_SPEED;
                    body.vy = dy / dist * CHASE_SPEED;
                }
            }
            case BITE -> {
                if (timer > BITE_TIME) { state = State.RECOVER; timer = 0; break; }
                body.vx = lungeVX;
                body.vy = lungeVY;
                if (dist < BITE_RANGE && hitCooldown <= 0) {
                    hitCooldown = HIT_COOLDOWN;
                    aggroLock = REAGGRO_DELAY;
                    state = State.RECOVER;
                    timer = 0;
                    return true;
                }
            }
            case RECOVER -> {
                body.vx *= 0.9;
                body.vy *= 0.9;
                if (timer > RECOVER_TIME) { state = State.IDLE; timer = 0; }
            }
        }
        return false;
    }

    private void lunge(double dx, double dy, double dist) {
        if (dist < 0.001) { lungeVX = 0; lungeVY = 0; return; }
        lungeVX = dx / dist * CHASE_SPEED * 2.2;
        lungeVY = dy / dist * CHASE_SPEED * 2.2;
    }

    private void drift() {
        if (rng.nextDouble() < 0.02) {
            body.vx = (rng.nextDouble() - 0.5) * 2 * IDLE_SPEED;
            body.vy = (rng.nextDouble() - 0.5) * 2 * IDLE_SPEED;
        }
    }

    public void flee() {
        aggroLock = REAGGRO_DELAY;
        state = State.IDLE;
    }
}
