package tropical.engine;

/**
 * What the bow does: the arrow's flight, its arc, and what it hits.
 *
 * <p><b>MOVED HERE FROM tropical.engine.SelfTest, WHICH THE RELEASE DOES NOT HAVE.</b> The release's mutation list
 * reported "weapons: arrows fly straight" as NOT CAUGHT - `ARROW_GRAVITY` set to zero, an arrow with no arc - and the
 * behaviour was checked, in a class only one edition can run. That is the eighth instance of this gap, and the fix is
 * always the same: a class with a main.
 */
public class ArrowTest {
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

        weaponsDoWhatTheySay();
        arrowsArc();
        arrowsHitWhatTheyPointAt();
        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }

    /**
     * The weapons do what their constants say, at the speed and on the schedule they claim.
     *
     * <p><b>Found by asking the gate what it does not cover.</b> `tools/mutate.sh` breaks one constant at a
     * time and reports whether a suite notices - and against this gate, `ARROW_SPEED = 700 -> 1`,
     * `BOMB_SPEED = 70 -> 700` and `FUSE_TIME = 1.3 -> 0.05` all came back NOT CAUGHT. The gate is strong on
     * generation, water, doors, the tutorial and audio, and it had nothing at all about the things the player
     * actually does. The release covers the weapons through its screen; aside, which is the source of truth,
     * covered them nowhere.
     *
     * <p>ABSOLUTE EXPECTATIONS, NOT THE CONSTANTS. Asserting `advanced < ARROW_SPEED * t` would read the same
     * constant the code uses and pass whatever it is - rule 32, which this session already learned once. These
     * are ranges in real units, so a speed of 1 or a fuse of 0.05 fails them.
     */
    static void weaponsDoWhatTheySay() {
        double dt = GameLoop.DT;

        // BARE WORLDS, no map. A projectile's own speed and fuse are what is under test, and a level in the
        // way would put terrain, culling and collisions into the measurement. Nothing here needs ground.
        World aw = new World();
        Projectile arrow = Projectile.arrow(200, 100, 1);
        aw.addProjectile(arrow);
        double arrowX0 = arrow.x;
        for (double t = 0; t < 0.1; t += dt) aw.update(dt);
        double flown = arrow.x - arrowX0;
        verdict("weapons: an arrow flies ~70px in a tenth of a second (" + (int) flown + "px)",
                arrow.active && flown > 50 && flown < 95);

        // A bomb is a short toss - roughly 70px/s - and its fuse is about a second and a third. The drift is
        // measured horizontally, so the throw arc does not matter; the fuse is a duration and is asserted as
        // one: armed at one second, gone by one and a half.
        World bw = new World();
        Projectile bomb = Projectile.bomb(200, 100, 1);
        bw.addProjectile(bomb);
        double bombX0 = bomb.x;
        for (double t = 0; t < 0.1; t += dt) bw.update(dt);
        double drifted = bomb.x - bombX0;
        verdict("weapons: a bomb drifts ~7px in a tenth of a second (" + (int) drifted + "px)",
                bomb.active && drifted > 3 && drifted < 12);
        for (double t = 0.1; t < 1.0; t += dt) bw.update(dt);
        verdict("weapons: the bomb is still armed one second in", bomb.active);
        for (double t = 0; t < 0.5; t += dt) bw.update(dt);
        verdict("weapons: the bomb has gone off by one and a half seconds", !bomb.active);
    }
    /**
     * An arrow arcs - it does not fly straight.
     *
     * <p><b>FOUND BY tools/tautologies.py SAYING "NOTHING NOTICES IT BEING SWITCHED OFF".</b> ARROW_GRAVITY could be
     * set to zero - every arrow flying dead straight - and no check in 649 noticed. The class doc says "Arrows:
     * fast, affected by gravity", and only the first half of that sentence was tested.
     */
    static void arrowsArc() {
        double dt = GameLoop.DT;
        World w = new World();
        Projectile arrow = Projectile.arrow(200, 100, 1);
        w.addProjectile(arrow);

        // TWO EQUAL WINDOWS, and the drop in each is the whole measurement. A body under gravity falls further in
        // the second window than the first, because a fall accelerates; one flying straight drops the same in both,
        // because a straight line has no curvature. RELATIONAL, so the number is never compared to the constant that
        // produced it - the tautology rule, which is what the sweep exists to enforce.
        double y0 = arrow.y;
        for (double t = 0; t < 0.25; t += dt) w.update(dt);
        double firstQuarter = arrow.y - y0;
        double yMid = arrow.y;
        for (double t = 0; t < 0.25; t += dt) w.update(dt);
        double secondQuarter = arrow.y - yMid;

        verdict("weapons: an arrow is still in flight half a second after leaving the bow, in open air", arrow.active);
        verdict("weapons: an arrow arcs - it falls further in the second quarter-second than the first ("
                        + (int) firstQuarter + "px then " + (int) secondQuarter + "px)",
                secondQuarter > firstQuarter + 5);
    }
    /**
     * AN ARROW KILLS WHAT IT FLIES INTO - both lists.
     *
     * <p>Two separate playtest reports, two separate pieces of code, and neither was protected. World says so in
     * its own comments: the arrow-enemy check "was missing entirely - arrows flew through enemies (playtest:
     * 'arrows fire, but do nothing')", and the arrow-bat check exists because "they were not in `enemies`, so an
     * arrow flew straight through one (playtest: the bat reads as invincible)".
     *
     * <p>The mutation sweep found both: disabling either collision left the gate green at 447 passed, 0 failed.
     * The existing weapon check measures that an arrow FLIES - seventy pixels in a tenth of a second - which is
     * a different question from whether it lands on anything.
     *
     * <p>They are checked separately because they ARE separate loops: a bat is not in `enemies`, and a fix to one
     * would leave the other broken.
     */
    static void arrowsHitWhatTheyPointAt() {
        // ON A FLOOR. An enemy in an empty World FALLS - the first version of this put one at y=100 and by the
        // time the arrow arrived it was twenty pixels lower, so the arrow sailed over it and the check failed
        // for a reason that had nothing to do with arrows. A bat does not fall, which is why that half passed.
        String flat = String.join("\n", "                        ", "                        ",
                "                        ", "########################");
        World w = new World();
        tropical.engine.LevelMap.parse(flat).buildWorld(w);
        // The floor's top is row 3, y=96, so the enemy's 22-tall box centres at 85 and the arrow flies at that
        // height. THREE ATTEMPTS TO GET THIS RIGHT, all by probing rather than reasoning: first an enemy in an
        // empty World, which FELL; then one at y=104, which snapped UP to the floor; then an arrow at y=106,
        // which was INSIDE the ground and died on the frame it spawned.
        Physics.Body player = new Physics.Body(80, 74, 24, 44);
        w.addBody(player);
        w.playerBody = player;
        tropical.engine.Enemy enemy = new tropical.engine.Enemy(270, 85, 22, 22);
        w.addEnemy(enemy);
        w.addProjectile(tropical.engine.Projectile.arrow(200, 85, 1));
        for (int i = 0; i < 60; i++) w.update(GameLoop.DT);
        verdict("weapons: an arrow kills the enemy it flies into", enemy.dead);

        World w2 = new World();
        Physics.Body p2 = new Physics.Body(80, 100, 24, 44);
        w2.addBody(p2);
        w2.playerBody = p2;
        w2.addBat(new tropical.engine.Bat(300, 100, 7L));
        w2.addProjectile(tropical.engine.Projectile.arrow(200, 100, 1));
        for (int i = 0; i < 60; i++) w2.update(GameLoop.DT);
        verdict("weapons: an arrow kills a bat too, which is a separate list from enemies", w2.bats.isEmpty());
    }
}
