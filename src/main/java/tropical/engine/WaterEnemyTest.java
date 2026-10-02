package tropical.engine;


/**
 * WaterEnemyTest - what water does to an ENEMY.
 *
 * WaterSystem applies to every body, not just the player, and nothing ever checked
 * what that means for something that swims without wanting to. Three questions:
 *
 *   1. does it float, or does it sink and stick to the pool floor?
 *   2. does it keep moving, or does the drag make it hang there?
 *   3. can it get out - and if it cannot, does it at least look like it is trying
 *      rather than grinding against a wall forever?
 *
 * Answers are measurements, not assumptions: the point is to find out whether the
 * player-only verification was hiding something.
 */
public class WaterEnemyTest {
    static int failures = 0;
    static final double DT = GameLoop.DT;

    static void check(String n, boolean ok) { check(n, ok, ""); }
    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    /** A level with a 4-deep pool in a walled box, and dry ground either side. */
    static final String POOL = String.join("\n",
        " ".repeat(0) + "########################",
        "#                      #",
        "#                      #",
        "#####          #########",
        "#####~~~~~~~~~~#########",
        "#####~~~~~~~~~~#########",
        "#####~~~~~~~~~~#########",
        "#####~~~~~~~~~~#########",
        "########################");

    static World world() {
        LevelMap map = LevelMap.parse(POOL);
        World w = new World();
        map.buildWorld(w);
        // A player body, because the engine only runs enemy AI when it can find one
        // (it looks for a body flagged oneway). Without it the enemies float with no
        // brain at all: the first version of this test measured 0px travelled and
        // looked like a water bug, when the enemies simply were not thinking.
        Physics.Body player = new Physics.Body(80, 90, 24, 44);
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
        System.out.println("Water and enemies");
        System.out.println("=================");

        // --- an enemy dropped into the middle of the pool -------------------
        World w = world();
        Enemy e = new Enemy(384, 100, 28, 28);
        w.addEnemy(e);
        check("setup: the level has water and an enemy in it",
            w.water.water() != null && !w.water.water().isEmpty());

        double minFeet = 1e9, maxFeet = -1e9;
        double travelled = 0;
        double lastX = e.body.x;
        boolean everSubmerged = false;
        for (int i = 0; i < 60 * 8; i++) {
            w.update(DT);
            minFeet = Math.min(minFeet, e.body.y + e.body.hh);
            maxFeet = Math.max(maxFeet, e.body.y + e.body.hh);
            travelled += Math.abs(e.body.x - lastX);
            lastX = e.body.x;
            if (e.body.submersion > 0.5) everSubmerged = true;
        }
        // AVERAGED, not sampled. A body at the surface bobs, so an instantaneous
        // submersion can catch it mid-overshoot at 1.0 and look like it sank - the
        // first version of this check failed for exactly that reason.
        double sum = 0;
        int n = 0;
        for (int i = 0; i < 120; i++) { w.update(DT); sum += w.water.submersion(e.body); n++; }
        double sub = sum / n;

        check("float: the enemy does not sink to the floor and stay there",
            sub > 0.4, String.format("mean submersion over 2s = %.2f", sub));
        check("float: it is genuinely in the water", everSubmerged);
        check("float: it floats around the buoyancy line, not at the bottom",
            Math.abs(sub - 1.0 / WaterSystem.BUOYANCY) < 0.25,
            String.format("mean sub=%.2f equilibrium=%.2f, feet ranged %.0f..%.0f (bobbing)",
                sub, 1.0 / WaterSystem.BUOYANCY, minFeet, maxFeet));
        check("move: it keeps swimming rather than hanging still",
            travelled > 40, String.format("travelled %.0fpx in 8s", travelled));

        // --- does it get out? ----------------------------------------------
        // An enemy has no jump, so it cannot. What matters is whether the situation
        // is stable and legible rather than a body grinding into geometry forever.
        double xAtStart = e.body.x;
        for (int i = 0; i < 60 * 4; i++) w.update(DT);
        check("escape: without a jump it stays in the pool (expected, not a bug)",
            w.water.submersion(e.body) > 0.3 && Math.abs(e.body.x - xAtStart) < 400,
            String.format("x moved %.0fpx in 4 more seconds", Math.abs(e.body.x - xAtStart)));

        // --- an enemy on dry land beside the pool is unaffected -------------
        World dry = world();
        Enemy land = new Enemy(80, 90, 28, 28);
        dry.addEnemy(land);
        for (int i = 0; i < 60 * 3; i++) dry.update(DT);
        check("dry: an enemy on land is dry and grounded",
            !land.body.inWater && land.body.submersion == 0 && land.body.grounded,
            String.format("inWater=%b sub=%.2f grounded=%b",
                land.body.inWater, land.body.submersion, land.body.grounded));

        // --- an enemy that walks off the edge into the pool ------------------
        World walk = world();
        Enemy drop = new Enemy(300, 140, 28, 28);
        drop.dir = 1;
        walk.addEnemy(drop);
        boolean enteredWater = false;
        for (int i = 0; i < 60 * 6; i++) {
            walk.update(DT);
            if (drop.body.submersion > 0.3) enteredWater = true;
        }
        check("entry: an enemy that patrols off the edge ends up in the water",
            enteredWater, "it walked in rather than turning at the lip");
        check("entry: and it is floating there, not sunk",
            walk.water.submersion(drop.body) > 0.3,
            String.format("sub=%.2f", walk.water.submersion(drop.body)));

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        return failures;
    }
}
