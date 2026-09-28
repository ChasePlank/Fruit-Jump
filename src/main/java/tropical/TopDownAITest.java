package tropical;
import tropical.engine.*;

/**
 * TopDownAITest - the top-down enemy AI with eyes.
 *
 * The behaviour under test is the one the fog of war makes possible: an enemy
 * that cannot see the player does not chase them, even when they are close. The
 * old AI chased anything within a radius, through walls, which made a dungeon
 * with lighting pointless - you could hide in the dark and still be followed.
 */
public class TopDownAITest {
    static int failures = 0;
    static void check(String n, boolean ok) { check(n, ok, ""); }
    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    static final int W = RoomWorld.ROOM_W, H = RoomWorld.ROOM_H;
    static final double DT = 1.0 / 60.0;

    static java.util.List<Physics.AABB> geometry() {
        java.util.List<Physics.AABB> tiles = new java.util.ArrayList<>();
        tiles.add(new Physics.AABB(0, 0, W, 32));
        tiles.add(new Physics.AABB(0, H - 32, W, H));
        tiles.add(new Physics.AABB(0, 0, 32, H));
        tiles.add(new Physics.AABB(W - 32, 0, W, H));
        // a partial internal wall: blocks sight at mid height, open above and below
        tiles.add(new Physics.AABB(384, 140, 416, 340));
        return tiles;
    }

    /** Live world with a player, one top-down enemy, and a RoomNav. */
    static World world(Physics.Body[] playerOut, Enemy[] enemyOut) {
        World w = new World();
        w.tiles.addAll(geometry());
        Physics.Body p = new Physics.Body(450, 240, 24, 44);
        p.noGravity = true;
        p.oneway = true;                       // how World finds the player body
        w.addBody(p);
        Enemy e = new Enemy(250, 240, 28, 28);
        e.topDown = true;
        e.body.noGravity = true;
        w.addEnemy(e);
        RoomNav nav = new RoomNav(W, H);
        nav.rebuild(w.tiles);
        w.nav = nav;
        playerOut[0] = p;
        enemyOut[0] = e;
        return w;
    }

    static double p2x = 450, p2y = 240;

    public static void main(String[] args) {
        System.out.println("Top-down AI - sight-gated chase on a flow field");
        System.out.println("===============================================");

        Physics.Body[] po = new Physics.Body[1];
        Enemy[] eo = new Enemy[1];
        World w = world(po, eo);
        Physics.Body player = po[0];
        Enemy e = eo[0];
        RoomNav nav = w.nav;

        // --- 1. no sight, close by: no chase -------------------------------
        // This test only means anything if two things are true, and neither was checked
        // before: the wall is genuinely BETWEEN them, and the separation is inside the old
        // proximity aggro range - otherwise "it did not acquire" would be about distance
        // rather than about sight, which is the whole claim. (The comment here said 390px
        // for a while after the bodies were moved closer. Stale geometry in a comment is
        // the same false confidence as stale geometry in a coordinate.)
        int ec0 = nav.colOf(e.body.x), er0 = nav.rowOf(e.body.y);
        int pc0 = nav.colOf(player.x), pr0 = nav.rowOf(player.y);
        boolean wallBetween = false;
        for (Physics.AABB a : w.tiles) {
            if (a.y0 <= e.body.y && a.y1 >= e.body.y
                    && a.x0 > Math.min(e.body.x, player.x) && a.x1 < Math.max(e.body.x, player.x)) {
                wallBetween = true;
            }
        }
        check("fixture: the wall actually stands between them on their row", wallBetween,
            String.format("enemy x=%.0f player x=%.0f", e.body.x, player.x));
        check("fixture: the separation is inside the old 220px aggro range",
            Math.abs(player.x - e.body.x) < 220,
            String.format("%.0fpx apart - so sight, not distance, is the gate",
                Math.abs(player.x - e.body.x)));
        check("fixture: line of sight between them is blocked",
            !nav.eye.lineOfSight(ec0, er0, pc0, pr0));

        boolean everTargeted = false;
        boolean everInWall = false;
        for (int i = 0; i < 90; i++) {
            w.update(DT);
            if (e.hasTarget) everTargeted = true;
            if (nav.blockedAt(e.body.x, e.body.y)) everInWall = true;
        }
        check("blind: an enemy behind a wall never acquires a target",
            !everTargeted,
            String.format("player %.0fpx away, wall between, old aggro range was 220",
                Math.abs(player.x - e.body.x)));
        check("blind: it stays in PATROL", e.state == Enemy.AIState.PATROL || !e.hasTarget);
        check("blind: it does not walk into geometry", !everInWall);

        // --- 2. line of sight above the wall: chase and arrive -------------
        player.x = 640; player.y = 100;
        player.vx = 0; player.vy = 0;
        e.body.x = 250; e.body.y = 100;
        boolean acquired = false;
        double closest = 1e9;
        for (int i = 0; i < 420; i++) {
            w.update(DT);
            if (e.hasTarget) acquired = true;
            closest = Math.min(closest, Math.hypot(player.x - e.body.x, player.y - e.body.y));
            if (nav.blockedAt(e.body.x, e.body.y)) everInWall = true;
        }
        check("sighted: it acquires a target it can see", acquired);
        check("sighted: it closes on the player", closest < 60,
            String.format("closest %.0fpx", closest));
        check("sighted: it never walks into geometry while routing", !everInWall);

        // --- 3. losing sight: keep coming, then give up --------------------
        // Player teleports behind the wall; the enemy is near (640,100).
        player.x = 200; player.y = 400;
        double lostAt = -1;
        boolean stillComingImmediately = false;
        for (int i = 0; i < 240; i++) {
            w.update(DT);
            if (i == 5 && e.hasTarget) stillComingImmediately = true;
            if (!e.hasTarget && lostAt < 0) lostAt = i * DT;
        }
        check("memory: it keeps coming for a moment after losing sight",
            stillComingImmediately);
        check("memory: then it gives up and returns to patrol",
            !e.hasTarget && e.state == Enemy.AIState.PATROL,
            lostAt < 0 ? "never gave up" : String.format("gave up after %.2fs", lostAt));
        check("memory: giving up takes about the give-up window, not instantly",
            lostAt > 0.5, String.format("%.2fs", lostAt));

        // --- 4. no nav: the old behaviour is untouched ---------------------
        World plain = new World();
        plain.tiles.addAll(geometry());
        Physics.Body p2 = new Physics.Body(450, 240, 24, 44);
        p2.noGravity = true;
        p2.oneway = true;
        plain.addBody(p2);
        Enemy e2 = new Enemy(250, 240, 28, 28);
        e2.topDown = true;
        e2.body.noGravity = true;
        plain.addEnemy(e2);
        boolean chasedWithoutNav = false;
        for (int i = 0; i < 120; i++) {
            plain.update(DT);
            if (e2.state == Enemy.AIState.CHASE) chasedWithoutNav = true;
        }
        check("no nav: a top-down enemy with no navigation still uses the old chase",
            chasedWithoutNav, "straight-line chase through the wall, as before");

        // --- 5. the platformer is not affected -----------------------------
        World side = new World();
        side.tiles.add(new Physics.AABB(0, 400, 800, 440));
        Physics.Body p3 = new Physics.Body(350, 300, 24, 44);
        p3.oneway = true;
        side.addBody(p3);
        Enemy e3 = new Enemy(200, 378, 28, 28);   // side-view enemy, gravity on
        side.addEnemy(e3);
        for (int i = 0; i < 120; i++) side.update(DT);
        check("platformer: a side-view enemy still chases and still respects gravity",
            e3.state == Enemy.AIState.CHASE && e3.body.grounded,
            String.format("state=%s grounded=%b", e3.state, e3.body.grounded));

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
