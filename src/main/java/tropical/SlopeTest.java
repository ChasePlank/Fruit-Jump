package tropical;

import tropical.engine.*;

/**
 * SlopeTest - the three claims the slope pass makes about itself.
 *
 * The pass documents: walking up snaps feet to the surface; walking down snaps only when
 * already near it, so a downhill ramp does not launch you; and a body that jumps through a
 * slope from below must NOT be teleported onto it. That last claim is why the code guards
 * by VELOCITY rather than by a depth cap - the cap it replaced left an unread constant
 * behind.
 *
 * Geometry, taken from the engine's own definition rather than assumed: a '/' at cell
 * (c,r) builds Slope(c*32, (r+1)*32, (c+1)*32, r*32) - rising to the right. In this level
 * the slope is col 6 row 7, so its surface runs (192,256) -> (224,224). The cell beneath
 * the diagonal is EMPTY SPACE, but the pass still treats feet below the surface as
 * penetration, which is exactly why a fixture has to place a body deliberately: spawn it
 * under the diagonal and the surface owns it before you can test anything else.
 */
public class SlopeTest {
    static int failures = 0;
    static final double DT = GameLoop.DT;
    static final int TILE = 32;

    static void check(String n, boolean ok) { check(n, ok, ""); }
    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    /** Low floor cols 0-5 at row 8, slope at col 6 row 7, long high floor cols 7-17. Walls both ends. */
    static final String RAMP = String.join("\n",
        "                    ",
        "                    ",
        "                    ",
        "                    ",
        "                    ",
        "                    ",
        "                    ",
        "      /###########  ",
        "######              ",
        "####################");

    /** A slope over open space, with a base floor to catch what falls. */
    static final String OVERSLOPE = String.join("\n",
        "                    ",
        "                    ",
        "                    ",
        "                    ",
        "                    ",
        "                    ",
        "                    ",
        "     /              ",
        "                    ",
        "####################");

    /**
     * Which column the slope is in, read from the level string itself.
     *
     * Bodies are positioned from this rather than from a number typed by hand: a 20-column
     * string with one space too few silently moves a slope a whole tile, and the first
     * version of this test had exactly that - the ramp had no slope at all (so the walker
     * fell into the gap where it should have been) and the overslope's body stood a column
     * clear of it (so the "fast rise is not snapped" check passed while verifying nothing).
     * A fixture whose geometry is asserted cannot disagree with the test that uses it.
     */
    static int slopeCol(String level, int row) {
        return level.split("\n")[row].indexOf('/');
    }

    static World world(String level) {
        World w = new World();
        LevelMap.parse(level).buildWorld(w);
        return w;
    }

    static Physics.Body body(World w, double x, double y) {
        Physics.Body b = new Physics.Body(x, y, 24, 44);
        w.addBody(b);
        return b;
    }

    public static void main(String[] args) {
        System.out.println("Slopes - the three claims in the slope pass's own comment");
        System.out.println("=======================================================");

        // --- 1. walking up: feet snap to the surface, and it stays grounded --
        World ramp = world(RAMP);
        int rampCol = slopeCol(RAMP, 7);
        check("fixture: the ramp level actually contains a slope at row 7", rampCol > 0,
            "slope at column " + rampCol);
        Physics.Body walker = body(ramp, 120, 256 - 22);
        boolean airborne = false;
        for (int i = 0; i < 200; i++) {
            walker.vx = 200;
            ramp.update(DT);
            if (walker.x < 340 && !walker.grounded) airborne = true;
            if (walker.x > 340) break;
        }
        check("walking up: the body climbs onto the high floor",
            Math.abs((walker.y + walker.hh) - 224) < 6,
            String.format("feet=%.0f, high floor=224", walker.y + walker.hh));
        check("walking up: it never leaves the ground on the ramp", !airborne);

        // --- 2. jumping through from below: THE claim ----------------------
        // Spawn under the diagonal with real penetration (feet 270 vs surface ~238 at
        // this x), and set the upward velocity BEFORE the first update so the pass sees
        // a fast rise. A depth cap would snap it 32px onto the surface; the velocity
        // guard must refuse.
        World up = world(OVERSLOPE);
        Physics.Body jumper = body(up, slopeCol(OVERSLOPE, 7) * TILE + 18, 270 - 22);
        double y0 = jumper.y;
        jumper.vy = -420;                       // rising fast, before it is seen
        up.update(DT);
        double firstStep = Math.abs(jumper.y - y0);
        check("jumping through: a fast rise is NOT snapped onto the surface",
            firstStep < 12.0,
            String.format("first-frame move %.1fpx (a snap would be ~32px)", firstStep));

        // --- 2b. the contrast: same place, no upward velocity --------------
        World still = world(OVERSLOPE);
        Physics.Body resting = body(still, slopeCol(OVERSLOPE, 7) * TILE + 18, 270 - 22);
        double y1 = resting.y;
        resting.vy = 0;                          // not rising: the snap is allowed
        still.update(DT);
        double snapStep = Math.abs(resting.y - y1);
        check("jumping through: the same spot without upward speed IS snapped (that is the guard)",
            snapStep > 12.0,
            String.format("first-frame move %.1fpx", snapStep));

        // --- 3. free fall is not yanked onto a slope ------------------------
        World fall = world(OVERSLOPE);
        Physics.Body faller = body(fall, 96, 40);
        boolean yanked = false;
        double prev = faller.y;
        for (int i = 0; i < 60; i++) {
            fall.update(DT);
            if (faller.vy > 300 && Math.abs(faller.y - prev) < 1.0) yanked = true;
            prev = faller.y;
        }
        check("free fall: a fast faller is not snapped sideways onto a slope", !yanked);

        // --- 4. walking down: no launch, and it lands on the low floor ------
        World down = world(RAMP);
        Physics.Body rider = body(down, 300, 224 - 22);
        int airborneFrames = 0;
        for (int i = 0; i < 200; i++) {
            rider.vx = -200;
            down.update(DT);
            if (!rider.grounded && rider.x > 200 && rider.x < 240) airborneFrames++;
            if (rider.x < 150) break;
        }
        check("walking down: it does not launch off the ramp", airborneFrames < 6,
            airborneFrames + " airborne frames crossing the slope");
        check("walking down: it reaches the low floor",
            Math.abs((rider.y + rider.hh) - 256) < 8,
            String.format("feet=%.0f, low floor=256", rider.y + rider.hh));

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
