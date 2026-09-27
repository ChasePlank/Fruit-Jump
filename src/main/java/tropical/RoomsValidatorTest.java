package tropical;
import tropical.engine.*;

/**
 * RoomsValidatorTest - a structural gate for the top-down room generator.
 *
 * The platformer's generator had a bug where a locked door could be placed with no
 * key (the door was written first and the key search was allowed to fail), which
 * makes a level unwinnable. The room generator places doors and keys the same way,
 * so it deserves the same gate - and unlike a level, a room graph can be checked
 * without playing it: keys are generic (any key opens any door) and doors sit on the
 * path exit, so the property is cumulative and exact.
 *
 *   Along the main path, the number of keys collectable SO FAR must never be fewer
 *   than the number of locked doors passed so far.
 *
 * A room with a door whose key was never placed fails that on the door's own step.
 */
public class RoomsValidatorTest {
    static int failures = 0;

    static void check(String n, boolean ok) { check(n, ok, ""); }
    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    static int[] parse(String id) {
        String[] parts = id.split(",");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    static int keysIn(RoomWorld w, String id) {
        int n = 0;
        for (Pickup p : w.roomPickups.getOrDefault(id, java.util.List.of())) {
            if (p.type == Pickup.Type.KEY) n++;
        }
        return n;
    }

    static int doorsIn(RoomWorld w, String id) {
        int n = 0;
        for (Door d : w.roomDoors.getOrDefault(id, java.util.List.of())) {
            if (d.isSolid()) n++;
        }
        return n;
    }

    public static void main(String[] args) {
        System.out.println("Room generator: keys before locks, along the path");
        System.out.println("================================================");

        int seeds = 120;
        int stranded = 0, doorLevels = 0, keyMismatch = 0, gapsInPath = 0;
        StringBuilder badSeeds = new StringBuilder();

        for (long seed = 1; seed <= seeds; seed++) {
            RoomWorld w = new RoomWorld(4, 3, seed);
            int keys = 0, doors = 0;
            boolean thisSeedBad = false;

            for (int i = 0; i < w.mainPath.size(); i++) {
                String id = w.mainPath.get(i);

                // a key for this door must already exist: keys are generic, so the
                // count is the property, not the identity
                keys += keysIn(w, id);
                int here = doorsIn(w, id);
                if (here > 0) doorLevels++;
                doors += here;
                if (doors > keys) {
                    stranded++;
                    thisSeedBad = true;
                    break;
                }

                // the generator's intent: the key sits in the room BEFORE the door
                if (here > 0 && i > 0) {
                    if (keysIn(w, w.mainPath.get(i - 1)) == 0) keyMismatch++;
                }

                // consecutive path rooms must be grid-adjacent, or the path is a
                // teleport the player cannot follow
                if (i > 0) {
                    int[] a = parse(w.mainPath.get(i - 1)), b = parse(id);
                    if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) != 1) gapsInPath++;
                }
            }
            if (thisSeedBad) badSeeds.append(seed).append(" ");
        }

        check("rooms: the path never asks for more keys than it has given",
            stranded == 0,
            stranded + "/" + seeds + " seeds stranded in a locked door"
                + (stranded == 0 ? "" : "  seeds: " + badSeeds));
        check("rooms: every door's key is in the room before it", keyMismatch == 0,
            keyMismatch + " door(s) without a key in the previous room");
        check("rooms: consecutive path rooms are adjacent in the grid", gapsInPath == 0,
            gapsInPath + " path step(s) that jump a cell");
        check("rooms: doors actually get placed (the gate is exercised)",
            doorLevels > 0, doorLevels + " doors across the path rooms of " + seeds + " seeds");

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
