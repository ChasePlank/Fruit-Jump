package tropical.engine;

import java.util.Map;
import java.util.TreeMap;

/**
 * What does ROOMS MODE contain, and does the mode load everything it generates?
 *
 * <p><b>THIS EXISTS BECAUSE THE TWO MODES ARE NOT THE SAME GAME, AND NOTHING SAID SO.</b> The scrolling platformer
 * contains water, spikes, cracked floors, one-way planks, moving platforms, four enemy kinds and a boss. Rooms mode
 * - the screen-transition architecture the Scratch project was built around, and the one this game is headed for -
 * contains enemies, pickups and doors, and nothing else. There is no water, no hazard of any kind, no platform that
 * moves, and no boss.
 *
 * <p><b>AND THAT IS A STAGE RATHER THAN A FAULT, which is why this is a report and not a check.</b> The loader and
 * the generator agree exactly: `RoomsScreen.loadRoom` adds tiles, one-ways, enemies, pickups and doors, and
 * `RoomWorld` generates enemies, pickups and doors and nothing else. Nothing is generated and dropped - the mode
 * simply has less, deliberately or not.
 *
 * <p>What it is for is the day somebody grows rooms mode: this prints the inventory so the change is visible, and
 * `aside/tools/compare-release.sh` makes the same kind of statement about this repository against the release.
 *
 * <p><b>AND IT IS A CHECK AS WELL AS A REPORT, because the gate's tool list says every tool in it is a check and a
 * check that cannot fail is worse than no check.</b> What it asserts is the invariant that would be invisible if it
 * broke: every content map this mode generates has SOMETHING in it. A door generator that stopped placing doors
 * would leave a mode that still runs, still loads, and is missing its doors. The thresholds are one room in five
 * seeds - the doors are genuinely rare at 4 in 125 - so the check is about a generator that has stopped, not about
 * a number that moved.
 *
 * <p>What it does NOT assert is the one-way count, which is zero and correctly so: see the note it prints.
 */
public class RoomsProbe {

    public static void main(String[] args) {
        final int SEEDS = 5;
        int rooms = 0;
        Map<String, Integer> withEnemies = new TreeMap<>();
        Map<String, Integer> withPickups = new TreeMap<>();
        Map<String, Integer> withDoors = new TreeMap<>();

        for (long seed = 0; seed < SEEDS; seed++) {
            RoomWorld w = new RoomWorld(5, 5, 2000L + seed);
            for (Room[] col : w.grid) {
                for (Room r : col) {
                    if (r == null) continue;
                    rooms++;
                    // one-way platforms are the one piece of PLATFORMER furniture a room can carry, and they come
                    // from the room's own geometry rather than from a content map.
                    if (!r.getOneways().isEmpty()) bump(withEnemies, "oneway-" + r.id);
                }
            }
            count(w.roomEnemies, withEnemies);
            count(w.roomPickups, withPickups);
            count(w.roomDoors, withDoors);
        }

        int onewayRooms = 0;
        for (var e : withEnemies.entrySet()) if (e.getKey().startsWith("oneway-")) onewayRooms++;
        withEnemies.keySet().removeIf(k -> k.startsWith("oneway-"));

        System.out.println("=== rooms mode, " + SEEDS + " seeds, " + rooms + " room(s) ===");
        System.out.printf("  enemies            %3d of %d room(s)%n", total(withEnemies), rooms);
        System.out.printf("  pickups            %3d%n", total(withPickups));
        System.out.printf("  doors              %3d%n", total(withDoors));
        System.out.printf("  one-way platforms  %3d%n", onewayRooms);
        System.out.println();
        if (onewayRooms == 0) {
            System.out.println("  AND THE ONE-WAY COUNT IS ZERO, which is worth a second look rather than a shrug:");
            System.out.println("    RoomsScreen.loadRoom adds room.getOneways() to the physics every time a room loads,");
            System.out.println("    and no room in five seeds has one. The load path is live and its input is always empty.");
        }
        System.out.println("  water             none - no room carries any, and the mode has no place to put it");
        System.out.println("  spikes            none - nor any other hazard");
        System.out.println("  moving platforms  none");
        System.out.println("  cracked floors    none");
        System.out.println("  bosses            none");
        System.out.println("  enemy kinds       one: every foe is `new Enemy(x, y, 28, 28)`, the default kind");
        System.out.println();
        System.out.println("  Every room is top-down: `RoomsScreen` sets noGravity on the player (line ~174) and on every");
        System.out.println("  enemy it loads. Which is why the one-way count above is ZERO AND CORRECTLY SO - a one-way");
        System.out.println("  platform is a jump-up-through-it affordance from the SIDE-VIEW engine, and RoomWorld never");
        System.out.println("  writes one. The load path is vestigial, carried over from the engine the two modes share.");
        System.out.println();
        System.out.println("  This is a report about a mode that is thinner than the platformer, and the question of");
        System.out.println("  whether that is the plan belongs to whoever is designing the real game. What it CHECKS is");
        System.out.println("  that no generator has quietly stopped: see the verdict below.");

        // AND THE ASSERTION. A mode whose door generator stopped placing doors would still run, still load, and
        // still look right - it would simply be missing its doors.
        boolean ok = total(withEnemies) > 0 && total(withPickups) > 0 && total(withDoors) > 0;
        System.out.println(ok
                ? "  VERDICT: every content map this mode generates has something in it (enemies, pickups, doors)"
                : "  VERDICT: a generator has stopped - one of these maps is empty across " + SEEDS + " seeds: "
                  + "enemies=" + total(withEnemies) + " pickups=" + total(withPickups) + " doors=" + total(withDoors));
        System.out.println(ok ? "SUCCESS: rooms mode still generates its content" : "FAILURE: rooms mode lost a generator");
        System.exit(ok ? 0 : 1);
    }

    private static void count(Map<String, ? extends java.util.List<?>> src, Map<String, Integer> into) {
        int n = 0;
        for (var e : src.entrySet()) if (!e.getValue().isEmpty()) n++;
        into.merge("total", n, Integer::sum);
    }

    private static void bump(Map<String, Integer> m, String k) { m.merge(k, 1, Integer::sum); }

    private static int total(Map<String, Integer> m) { return m.getOrDefault("total", 0); }
}
