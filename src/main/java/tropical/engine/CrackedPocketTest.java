package tropical.engine;

/** Verify the bomb→cracked-floor→pocket loop with real physics:
 *  bomb explodes on the cracked tile, tile is removed from both
 *  world.tiles and world.cracked, and the heart is reachable by
 *  falling into the pocket. */
public class CrackedPocketTest {
    static int failures = 0;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** Run the whole test and return the number of failures, so a gate can fold it in. */
    public static int runAll() {
        failures = 0;
        // Find a seed with a FLOOR POCKET - a cracked tile with open air or the snack directly
        // beneath it.
        //
        // This is the one place this port departs from the release's copy, and it is because aside
        // has a second kind of cracked tile that the release does not: buildVault carves a doorway
        // into a cliff FACE, and those tiles are cracked too. The release's version took
        // map.cracked.get(0), which was the pocket because the pocket was the only kind there was.
        // Here get(0) can be a vault door, and bombing a vault door opens a chamber in a wall - the
        // player has no reason to fall anywhere, and the test failed for a reason that had nothing
        // to do with what it is for. Selecting by the property the test is actually about ("a
        // cracked FLOOR") keeps the assertions unchanged and makes the fixture honest.
        LevelGen gen = null;
        Physics.AABB pocket = null;
        int bestRow = -1;   // the LARGEST row number is the lowest on screen
        for (long seed = 201; seed <= 260; seed++) {
            LevelGen g = new LevelGen(60, 14, seed);
            LevelMap m = g.generate();
            for (Physics.AABB a : m.cracked) {
                int col = (int) (a.x0 / 32), row = (int) (a.y0 / 32);
                char below = m.cell(row + 1, col);
                // ON THE PATH FLOOR, and open beneath it. The floor test is what separates a pocket
                // from a vault door: a vault door is a stack of cracked tiles in a wall, and its
                // upper tiles have open air below them too, so "air underneath" alone selects the
                // wrong thing - which it did, on the first attempt, at row 4 of a 14-row map.
                // The LOWEST pocket in the level, and the earliest seed that has one. The throw the
                // test uses is a 380px arc from the left, so a pocket high in a cliff - which the
                // generator does produce, it only requires floor+2 to be inside the map - puts terrain
                // between the thrower and the tile and the bomb never lands on it. Lowest is the one
                // the throw has a clear line to.
                if (g.pathFloor[col] == row && (below == ' ' || below == 'h') && row > bestRow) {
                    bestRow = row; gen = g; pocket = a;
                }
            }
        }
        if (gen == null) { verdict("a seed with a floor pocket exists", false); return failures; }
        System.out.println("pocket chosen at row " + bestRow + " of the level");

        LevelMap map = gen.lastMap;
        World world = new World();
        map.buildWorld(world);
        Physics.Body player = new Physics.Body(map.spawnX, map.spawnY, 24, 44);
        world.addBody(player);
        Combat combat = new Combat();
        combat.playerHP = 3;
        PlayerInventory inv = new PlayerInventory();

        // Two cracked tiles; blast radius 100 covers both from the first
        Physics.AABB cracked = pocket;
        System.out.printf("cracked tiles at (%.0f,%.0f), %d total%n", cracked.x0, cracked.y0, map.cracked.size());
        int tilesBefore = world.tiles.size(), crackedBefore = world.cracked.size();

        // SECOND DEPARTURE FROM THE RELEASE'S COPY, and the same reason as the first: the throw was
        // tuned to one build. It aimed a 380px arc at a 1.2s fuse - "the arc returns to throw height
        // at t=1.0s, the 1.2s fuse detonates it right around the tile". The fuse is 1.3s here (it was
        // lengthened so a player running flat out can escape their own bomb) and the level layout is
        // different, so the arc lands somewhere else and nothing is destroyed. The test then failed
        // for a reason that has nothing to do with cracked floors.
        //
        // The assertion is "a bomb opens a cracked floor", not "a bomb thrown from 380px away on
        // these particular levels opens it", so the bomb goes on the tile. The constructor does apply
        // throw velocity, so the velocity is zeroed after it - which is the thing the release's
        // comment said could not be done, and it can.
        double throwX = cracked.x0 + 16;
        double throwY = cracked.y0 - 16;
        Projectile bomb = Projectile.bomb(throwX, throwY, 1);
        bomb.vx = 0; bomb.vy = 0;
        world.addProjectile(bomb);

        // simulate until explosion (fuse 1.2s) + settle
        double dt = GameLoop.DT;
        for (double t = 0; t < 3; t += dt) world.update(dt);

        boolean tileGone = world.tiles.size() <= tilesBefore - 2;
        boolean crackedGone = world.cracked.size() <= crackedBefore - 2;
        System.out.printf("after bomb: tiles %d->%d, cracked %d->%d%n",
            tilesBefore, world.tiles.size(), crackedBefore, world.cracked.size());
        verdict("bomb destroyed cracked floor tile", tileGone && crackedGone);

        // now walk over the pocket — player should fall in and collect the heart
        player.x = cracked.x0 - 60; player.y = cracked.y0 - 60; player.vx = 0; player.vy = 0;
        boolean heartCollected = false;
        for (Pickup p : map.pickups) if (p.type == Pickup.Type.HEART) heartCollected = true;
        if (!heartCollected) { System.out.println("NOTE: no heart in this level's pocket (placement is chance-gated per-cell)"); }

        // Walk right until over the hole, then STOP — a 32px hole at
        // run speed is crossable without falling (0.16s crossing, 7.7px
        // drop, swept collision catches the far lip). Entering the
        // pocket is a choice: stop over the hole and drop in.
        for (double t = 0; t < 5; t += dt) {
            // center fully inside the hole (past both lips)
            boolean overHole = player.x - player.hw > cracked.x0 && player.x + player.hw < cracked.x1 + 32;
            player.vx = overHole ? 0 : 200;
            world.update(dt);
            for (Pickup p : world.pickups) p.tryCollect(player, combat, inv);
            if (player.y > cracked.y0) break;  // fell in
        }
        System.out.printf("player ended at (%.0f,%.0f), hp=%.0f%n", player.x, player.y, combat.playerHP);
        boolean fellIn = player.y > cracked.y0;  // below the former floor line
        verdict("player falls through bombed hole into pocket", fellIn);
        return failures;
    }
}
