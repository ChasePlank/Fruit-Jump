package tropical.engine;

/**
 * Three things the piranha's comments claim and nothing was checking.
 *
 * <p><b>WHY THIS EXISTS.</b> `tools/tautologies.py` reported that `IDLE_SPEED`, `RECOVER_TIME` and `HIT_COOLDOWN`
 * could each be switched off with nothing in ~9976 checks noticing. The class doc makes claims about all three - a
 * fish that idles at its pool and only moves when you are in the water, a punish window after a miss, and a bite
 * that costs it time - and none of the claims had a check.
 *
 * <p><b>THE FIXTURE IS WaterEnemyTest'S POOL, reused rather than reinvented</b>: a 4-deep pool in a walled box with
 * dry ground either side. Same reason the blast test calls the pocket test's shape - two fixtures for one level
 * layout is two things to keep in step.
 *
 * <p><b>AND ONE FINDING THAT IS NOT A CHECK.</b> `HIT_COOLDOWN` is real - setting it to 1000 took the bite count in
 * ten seconds from four to one - but at its shipped 0.6 it cannot do anything, because re-engagement is gated by
 * `REAGGRO_DELAY` (2.5s) and any cooldown shorter than that has already expired by the time the fish comes back.
 * That is why zero is invisible: it is inert at this value, not untested. Left in place and reported rather than
 * deleted, because removing a guard is a design decision - but worth knowing, since the class already removed one
 * knob that looked load-bearing and did nothing.
 */
public class PiranhaTest {
    static int failures = 0;
    static int passes = 0;
    static final double DT = GameLoop.DT;

    static void check(String n, boolean ok) {
        System.out.printf("%s  %s%n", ok ? "PASS" : "FAIL", n);
        if (ok) passes++; else failures++;
    }

    static final String POOL = String.join("\n",
        "########################",
        "#                      #",
        "#                      #",
        "#####          #########",
        "#####~~~~~~~~~~#########",
        "#####~~~~~~~~~~#########",
        "#####~~~~~~~~~~#########",
        "#####~~~~~~~~~~#########",
        "########################");

    /**
     * WHERE "IN THE WATER" IS DECIDED, and it is not a rectangle drawn here.
     *
     * <p>The first version of this test asserted the fish's CENTRE stayed inside the water's pixel box, and it failed
     * on two of three seeds: the fish's centre reaches about y=131, three pixels above the surface line at y=128. What
     * it is actually doing is drifting at the surface with its body straddling the waterline, which is what a fish
     * does - the centre is a point, the fish is 16px tall, and submersion is what matters. So the assertion asks the
     * engine's own question, `water.inWater(body)`, rather than a rectangle this test invented. A test that fails on
     * a true statement about a fish teaches the wrong thing twice: once when it fails, and once when somebody
     * loosens it.
     */

    static World world(Physics.Body player) {
        LevelMap map = LevelMap.parse(POOL);
        World w = new World();
        map.buildWorld(w);
        // The engine only runs enemy AI when it can find a body flagged `oneway`.
        player.oneway = true;
        w.addBody(player);
        return w;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** Run the suite and return the number of failures, so a gate can fold it in. */
    public static int runAll() {
        failures = 0;
        passes = 0;
        idlesWhenYouAreDry();
        aBiteCostsItTime();
        aMissBuysYouAPunishWindow();
        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }

    /**
     * It drifts at its pool when the player is on the bank, and never chases.
     *
     * <p>Path length rather than net displacement: the drift is a random walk, so where it ends up is noise and how
     * far it travelled is the signal. Measured, not guessed - three seeds of ten seconds each, and the numbers are
     * printed so a failure says which seed and how far.
     */
    static void idlesWhenYouAreDry() {
        for (long seed : new long[]{5L, 6L, 7L}) {
            Physics.Body dryPlayer = new Physics.Body(80, 90, 24, 44);
            World w = world(dryPlayer);
            Piranha p = new Piranha(384, 200, seed);
            w.addPiranha(p);
            w.update(DT);

            // HORIZONTAL TRAVEL, and the reason is a measurement rather than a preference. The first version
            // measured total path, and it could not see IDLE_SPEED switched off, because most of that path is
            // VERTICAL: the fish starts deep and the water lifts it to the surface (measured: 384,200 -> 395,132,
            // sixty-eight pixels up and eleven across). Buoyancy is not the drift. What the drift does is push it
            // sideways at a random walking pace, so sideways is what to count.
            double pathX = 0, lastX = p.body.x;
            boolean chased = false, stayedWet = true;
            for (int i = 0; i < 60 * 10; i++) {
                w.update(DT);
                pathX += Math.abs(p.body.x - lastX);
                lastX = p.body.x;
                if (p.state != Piranha.State.IDLE) chased = true;
                if (!w.water.inWater(p.body)) stayedWet = false;
            }
            check("piranha (seed " + seed + "): with the player dry it drifts SIDEWAYS - "
                    + (int) pathX + "px of sideways travel in ten seconds", pathX > 20);
            check("piranha (seed " + seed + "): and stays in the water it lives in for the whole ten seconds "
                    + "(ending at " + (int) p.body.x + "," + (int) p.body.y + ")", stayedWet);
            check("piranha (seed " + seed + "): and never takes an interest in a player on the bank", !chased);
        }
    }

    /**
     * A bite costs it time, so a group cannot stun-lock you.
     *
     * <p>That is `REAGGRO_DELAY`'s stated purpose, and the cadence is the observable: bites spaced by seconds rather
     * than frames. Gaps are measured between bites and asserted against a fixed floor in seconds - with the delay
     * set to zero the fish bites on almost every frame, which is the stun-lock the comment is about.
     */
    static void aBiteCostsItTime() {
        Physics.Body swimmer = new Physics.Body(384, 216, 24, 44);
        World w = world(swimmer);
        Piranha p = new Piranha(384, 200, 5L);
        w.addPiranha(p);
        w.update(DT);

        double firstBite = -1, reengagedAt = -1;
        for (int i = 0; i < 60 * 10; i++) {
            double t = i * DT;
            w.piranhaBit = false;
            w.update(DT);
            // THE FIRST BITE AND THE FIRST RE-ENGAGEMENT, and then stop counting. The first version kept watching
            // every bite and recorded the earliest chase against the latest bite, which produced a negative delay
            // ("refuses to re-engage for -5.63s") - a measurement that was arithmetic rather than behaviour.
            if (w.piranhaBit && firstBite < 0) firstBite = t;
            else if (firstBite > 0 && reengagedAt < 0 && p.state == Piranha.State.CHASE) reengagedAt = t;
        }
        check("piranha: it bites a swimmer standing on top of it (first bite at "
                + String.format("%.2fs", firstBite) + ")", firstBite > 0);
        // THE DELAY UNTIL IT COMES BACK, not the gap between bites. The gap measures the whole round trip - the
        // lunge, the overshoot, the swim back at 95px/s - and it stayed above the threshold with the delay set to
        // zero, because swimming back takes time whatever the lock says. What the constant controls is how long
        // the fish refuses to re-engage, which is the thing the comment says exists to stop a stun-lock.
        double refused = reengagedAt - firstBite;
        check("piranha: and then refuses to re-engage for " + String.format("%.2fs", refused)
                + " (it bites again, but not immediately)", refused > 1.5);
    }

    /**
     * A lunge that cannot land leaves it open.
     *
     * <p>`RECOVER_TIME` is documented as "punish window after a miss", and a miss has to be arranged: this lunge
     * cannot land because the fish's own bite cooldown is still running, which is the only way to produce one on
     * purpose and is set directly because that field is internal to this package. Bounded at both ends - a window
     * of zero frames is not a window, and a fish stuck in RECOVER forever is a different bug.
     */
    static void aMissBuysYouAPunishWindow() {
        Physics.Body swimmer = new Physics.Body(384, 216, 24, 44);
        World w = world(swimmer);
        Piranha p = new Piranha(384, 200, 5L);
        p.hitCooldown = 99;      // the fixture: this lunge cannot land
        w.addPiranha(p);
        w.update(DT);

        Piranha.State prev = p.state;
        double start = -1, end = -1;
        for (int i = 0; i < 60 * 6 && end < 0; i++) {
            double t = i * DT;
            w.update(DT);
            if (p.state != prev) {
                if (p.state == Piranha.State.RECOVER) start = t;
                if (prev == Piranha.State.RECOVER) end = t;
                prev = p.state;
            }
        }
        double window = end - start;
        check("piranha: a lunge that cannot land leaves it recovering for "
                + String.format("%.2fs", window) + ", rather than instantly", window > 0.4);
        check("piranha: and it comes back out of that window rather than staying in it", end > 0 && window < 4.0);
    }
}
