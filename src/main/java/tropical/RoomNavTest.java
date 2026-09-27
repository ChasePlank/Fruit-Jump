package tropical;
import tropical.engine.*;

/**
 * RoomNavTest - the bridge between a pixel room and tile navigation, tested on
 * the thing that actually makes it worth having: a door.
 *
 * A locked door is a wall that blocks movement AND sight; unlocking it opens both.
 * If the two systems are fed from the same mask that is automatic. If they are
 * built separately it is two chances to disagree, and the disagreement looks like
 * an enemy walking through a door it cannot see through.
 */
public class RoomNavTest {
    static int failures = 0;
    static void check(String n, boolean ok) { check(n, ok, ""); }
    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        System.out.println("RoomNav - room geometry to tile navigation");
        System.out.println("==========================================");

        int W = RoomWorld.ROOM_W, H = RoomWorld.ROOM_H;      // 800 x 480
        RoomNav nav = new RoomNav(W, H);
        check("bridge: an 800x480 room is 25x15 tiles at TILE=32",
            nav.cols == 25 && nav.rows == 15, nav.cols + "x" + nav.rows);

        // a room: border walls, an internal wall, and a door in the gap
        java.util.List<Physics.AABB> solids = new java.util.ArrayList<>();
        solids.add(new Physics.AABB(0, 0, W, 32));            // north
        solids.add(new Physics.AABB(0, H - 32, W, H));        // south
        solids.add(new Physics.AABB(0, 0, 32, H));            // west
        solids.add(new Physics.AABB(W - 32, 0, W, H));        // east
        for (int r = 32; r < H - 32; r += 32) {                // internal wall at x=384
            solids.add(new Physics.AABB(384, r, 416, Math.min(r + 32, H - 96)));
        }
        Physics.AABB door = new Physics.AABB(384, H - 96, 416, H - 32);
        solids.add(door);

        nav.rebuild(solids);
        check("bridge: the border is blocked", nav.blockedAt(4, 4) && nav.blockedAt(W - 4, 4));
        check("bridge: the middle of the west half is open", !nav.blockedAt(200, 240));
        check("bridge: the internal wall is blocked where it is solid",
            nav.blockedAt(400, 240), "x=400 y=240");
        check("bridge: the doorway is blocked while the door is locked",
            nav.blockedAt(400, H - 64), "x=400 y=" + (H - 64));

        int pc = nav.colOf(200), pr = nav.rowOf(240);
        int farC = nav.colOf(700), farR = nav.rowOf(240);

        nav.grid.flowTo(farC, farR);
        check("door: a locked door closes the route between the halves",
            nav.grid.flowCost(pc, pr) == -1,
            "cost=" + nav.grid.flowCost(pc, pr));

        // Sight is checked on a line that passes through the doorway itself
        // (row 13, the only opening), not on a line that hits the wall - and at a
        // radius that actually reaches, or the check passes on distance and
        // proves nothing about the door. Both mistakes were in the first version.
        int eyeC = nav.colOf(200), eyeR = nav.rowOf(416);
        int tgtC = nav.colOf(700), tgtR = nav.rowOf(416);
        nav.eye.compute(eyeC, eyeR, 20);
        check("door: a locked door blocks sight through the doorway",
            !nav.eye.isVisible(tgtC, tgtR));

        // unlock: the wall that stops being one changes both systems at once
        solids.remove(door);
        nav.rebuild(solids);

        nav.grid.flowTo(farC, farR);
        int cost = nav.grid.flowCost(pc, pr);
        check("unlocked: opening the door opens the route", cost > 0, "cost=" + cost);
        nav.eye.compute(eyeC, eyeR, 20);
        check("unlocked: and opens the sight line through it",
            nav.eye.isVisible(tgtC, tgtR));
        check("unlocked: the wall still blocks sight where it is still solid",
            !nav.eye.isVisible(farC, farR), "row 7 is wall, not doorway");

        // the route it finds should actually go through the doorway
        int[] path = nav.grid.flowPath(pc, pr);
        boolean throughDoor = false;
        for (int node : path) {
            if (nav.grid.col(node) == 12 && nav.grid.row(node) >= 12) throughDoor = true;
        }
        check("unlocked: the route passes through the open doorway", throughDoor,
            "col 12, row >= 12");

        // conversions
        check("bridge: world -> tile -> world round-trips",
            Math.abs(nav.centreX(nav.colOf(400)) - (400 / 32 * 32 + 16)) < 0.01
                && Math.abs(nav.centreY(nav.rowOf(240)) - (240 / 32 * 32 + 16)) < 0.01);
        check("bridge: a body outside the room still gets a tile",
            nav.colOf(-500) == 0 && nav.rowOf(99999) == nav.rows - 1);

        // cost of rebuilding a room-sized mask
        long t0 = System.nanoTime();
        for (int i = 0; i < 200; i++) nav.rebuild(solids);
        double ms = (System.nanoTime() - t0) / 1e6 / 200;
        check("bridge: rebuilding the mask is cheap enough to do on every room change",
            ms < 2.0, String.format("%.3f ms/rebuild", ms));

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
