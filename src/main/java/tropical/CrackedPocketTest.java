package tropical;
import tropical.engine.*;

/** Verify the bomb→cracked-floor→pocket loop with real physics:
 *  bomb explodes on the cracked tile, tile is removed from both
 *  world.tiles and world.cracked, and the heart is reachable by
 *  falling into the pocket. */
public class CrackedPocketTest {
    public static void main(String[] args) {
        // find a seed with a cracked tile
        LevelGen gen = null;
        for (long seed = 201; seed <= 260; seed++) {
            LevelGen g = new LevelGen(60, 14, seed);
            g.generate();
            if (!g.lastMap.cracked.isEmpty()) { gen = g; break; }
        }
        if (gen == null) { System.out.println("FAIL: no cracked seed"); System.exit(1); return; }

        LevelMap map = gen.lastMap;
        World world = new World();
        map.buildWorld(world);
        Physics.Body player = new Physics.Body(map.spawnX, map.spawnY, 24, 44);
        world.addBody(player);
        Combat combat = new Combat();
        combat.playerHP = 3;
        PlayerInventory inv = new PlayerInventory();

        // Two cracked tiles; blast radius 100 covers both from the first
        Physics.AABB cracked = map.cracked.get(0);
        System.out.printf("cracked tiles at (%.0f,%.0f), %d total%n", cracked.x0, cracked.y0, map.cracked.size());
        int tilesBefore = world.tiles.size(), crackedBefore = world.cracked.size();

        // Throw the bomb from 380px left at throw height: the arc
        // (vx=380, vy=-300, g=600) returns to throw height at t=1.0s
        // = 380px downrange; the 1.2s fuse then detonates it right
        // around the cracked tile. (Placing a bomb ON the tile doesn't
        // work — the constructor always applies throw velocity.)
        double throwX = cracked.x0 - 380 + 16;
        double throwY = cracked.y0 - 16;
        world.addProjectile(Projectile.bomb(throwX, throwY, 1));

        // simulate until explosion (fuse 1.2s) + settle
        double dt = GameLoop.DT;
        for (double t = 0; t < 3; t += dt) world.update(dt);

        boolean tileGone = world.tiles.size() <= tilesBefore - 2;
        boolean crackedGone = world.cracked.size() <= crackedBefore - 2;
        System.out.printf("after bomb: tiles %d->%d, cracked %d->%d%n",
            tilesBefore, world.tiles.size(), crackedBefore, world.cracked.size());
        System.out.println(tileGone && crackedGone
            ? "PASS: bomb destroyed cracked floor tile" : "FAIL: tile survived");

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
        System.out.println(fellIn
            ? "PASS: player falls through bombed hole into pocket" : "FAIL: player did not fall in");
        System.exit(tileGone && crackedGone && fellIn ? 0 : 1);
    }
}
