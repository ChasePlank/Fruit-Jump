package tropical.engine;

import java.util.List;
import java.util.ArrayList;

/**
 * What a hit does to you, and what a stomp does to what you land on.
 *
 * <p><b>MOVED HERE FROM tropical.engine.SelfTest, WHICH THE RELEASE DOES NOT HAVE.</b> `tools/mutations.sh` in the
 * release reported "damage: the knockback pushes you IN" and "stomp: no bounce off an enemy" as NOT CAUGHT by any
 * suite - and both behaviours WERE checked, in a class that only one of the two editions can run. Four checks today
 * were in that position (the arrow, the boss's volley, the boss's armour, and these two), and the fix is always the
 * same: put them in a class with a main.
 *
 * <p>Both were found by tools/tautologies.py - the knockback could be set to zero, and the stomp bounce was invisible
 * until that sweep learned to see negative numbers.
 */
public class CombatTest {
    static int failures = 0;
    static int passes = 0;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (ok) passes++; else failures++;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = 0;
        passes = 0;

        aHitKnocksYouAway();
        stompingBouncesYouOff();

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }

    /**
     * A hit throws the player away from what hit them, and up.
     *
     * <p><b>FOUND BY tools/tautologies.py:</b> `Combat.KNOCKBACK_X` could be set to zero - a hit that takes a heart
     * and moves you nowhere - with nothing noticing. `damageIsMetered` covers how OFTEN a hit can land and never
     * looked at what a hit does to you.
     *
     * <p><b>BOTH DIRECTIONS, because "away from the source" is the claim and a sign error is the interesting bug:</b>
     * the wrong way round shoves the player INTO the spikes they were just hurt by, which is worse than no knockback
     * at all. Asserted in fixed px/s rather than against the constant, and the last check steps a real world so that
     * a velocity nobody applies still fails - the difference between a shove and a number written onto a body.
     */
    static void aHitKnocksYouAway() {
        Physics.Body fromRight = new Physics.Body(200, 100, 24, 44);
        Combat c1 = new Combat();
        c1.playerHP = 3;
        c1.hurtPlayer(fromRight, 400);          // the hazard is to the RIGHT of the player
        verdict("damage: a hit from the right throws the player left (" + (int) fromRight.vx + "px/s)",
                fromRight.vx < -50);
        verdict("damage: and pops them upward (" + (int) fromRight.vy + "px/s)", fromRight.vy < -50);

        Physics.Body fromLeft = new Physics.Body(200, 100, 24, 44);
        Combat c2 = new Combat();
        c2.playerHP = 3;
        c2.hurtPlayer(fromLeft, 0);             // and now to the LEFT
        verdict("damage: a hit from the left throws the player right (" + (int) fromLeft.vx + "px/s)",
                fromLeft.vx > 50);

        // AND IT IS MOVEMENT, NOT A VELOCITY NOBODY APPLIES.
        World w = new World();
        Physics.Body moved = new Physics.Body(200, 100, 24, 44);
        w.addBody(moved);
        Combat c3 = new Combat();
        c3.playerHP = 3;
        double x0 = moved.x, y0 = moved.y;
        c3.hurtPlayer(moved, 400);
        for (double t = 0; t < 0.15; t += GameLoop.DT) w.update(GameLoop.DT);
        verdict("damage: and the shove really moves them - " + (int) (x0 - moved.x) + "px left and "
                + (int) (y0 - moved.y) + "px up in a sixth of a second",
                (x0 - moved.x) > 10 && (y0 - moved.y) > 3);
    }

    /**
     * Stomping an enemy kills it and bounces you off it, and only from above.
     *
     * <p><b>FOUND BY tools/tautologies.py AFTER IT LEARNED TO SEE NEGATIVE NUMBERS.</b> `Combat.STOMP_BOUNCE` is
     * -400, and the sweep's pattern matched only unsigned values, so this constant had never been tested by anything.
     * Set it to zero and stomping stops bouncing you - which is the Mario mechanic the whole stomp path exists for.
     *
     * <p><b>AND THE POSITIONAL RULE, BOTH WAYS.</b> `isStomp` is a deliberate rule with a comment explaining why
     * velocity is not part of it: the first contact frame often arrives while the player is still rising, and
     * requiring a downward velocity would turn a legitimate stomp into a hurt. That rule is worth asserting in both
     * directions - feet above is a stomp, feet below is damage - because the failure mode of getting it wrong is a
     * player who cannot stomp anything.
     */
    static void stompingBouncesYouOff() {
        Physics.Body player = new Physics.Body(200, 100, 24, 44);
        Enemy spider = new Enemy(200, 130, 24, 24, Enemy.KIND_SPIDER);
        Combat c = new Combat();
        c.playerHP = 3;
        verdict("stomp: feet above the enemy is a stomp", c.isStomp(player, spider.body));
        boolean killed = c.processContact(player, spider);
        verdict("stomp: and it kills the enemy", killed && spider.dead);
        verdict("stomp: and throws the player back up off it (" + (int) player.vy + "px/s)", player.vy < -100);
        verdict("stomp: and costs no health (" + (int) c.playerHP + " hearts)", (int) c.playerHP == 3);

        // THE OTHER WAY ROUND: level with the enemy's centre is not a stomp, it is a collision.
        Physics.Body low = new Physics.Body(200, 140, 24, 44);
        Enemy other = new Enemy(200, 130, 24, 24, Enemy.KIND_SPIDER);
        Combat c2 = new Combat();
        c2.playerHP = 3;
        verdict("stomp: a player level with the enemy is NOT stomping it", !c2.isStomp(low, other.body));
        boolean killed2 = c2.processContact(low, other);
        verdict("stomp: and that contact hurts instead - " + (int) c2.playerHP + " hearts, enemy alive "
                + !other.dead, !killed2 && !other.dead && (int) c2.playerHP == 2);
    }
}
