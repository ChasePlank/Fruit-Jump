package tropical.engine;


/**
 * Stress test for the door/key/cracked generator features.
 * 100 fresh seeds through the validator bot (which now collects
 * keys and unlocks doors). Also counts feature placement.
 */
public class DoorStressTest {

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** Run the stress and return the number of seeds whose level the bot could not finish. */
    public static int runAll() {
        int completed = 0;
        int doorLevels = 0, crackedLevels = 0, boxFaults = 0;
        java.util.List<Long> failed = new java.util.ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (long seed = 201; seed <= 300; seed++) {
            LevelGen gen = new LevelGen(60, 14, seed);
            LevelMap map = gen.generate();
            if (!map.doors.isEmpty()) doorLevels++;
            if (!map.cracked.isEmpty()) crackedLevels++;
            if (LevelValidator.validateGenerated(gen, 30.0)) completed++;
            else failed.add(seed);
            // THE DOOR'S COLLISION BOX HAS TO REACH THE FLOOR. LevelMap's own comment records the history:
            // "first box ran to y+2*TILE (dipped into floor, sprite stretched down); the 'fix' set bottom to y
            // (top of the 'D' cell) - one tile ABOVE the floor, leaving a 32px gap enemies walked through
            // (playtest round 3)". The mutation sweep found nothing protecting it: dropping the box back into the
            // floor, or dropping the sprite height to zero, both left the gate green at 453 passed, 0 failed.
            //
            // PORTED FROM THE ENGINE'S SUITE, 6 October 2026 - this release's suite did not have it, so the same
            // regression could have gone unnoticed here.
            for (Door d : map.doors) {
                if (d.visibleH <= 0) { boxFaults++; continue; }
                // THREE TILES TALL, not four. The box is y-2*TILE to y+TILE: two tiles of invisible wall above the
                // door so it cannot be jumped, and the door itself standing on the floor. Letting the bottom drop
                // one row further into the ground is the ORIGINAL bug and it is not visible from the bottom row
                // alone, because both rows are solid. The height is what tells them apart.
                if (Math.abs((d.y1 - d.y0) - 3 * 32) > 0.5) { boxFaults++; continue; }
                int row = (int) (d.y1 / 32), col = (int) (d.x0 / 32);
                if (row < 0 || row >= map.heightCells() || col < 0 || col >= map.widthCells()) continue;
                if (map.cell(row, col) != '#') boxFaults++;   // the box bottom must land on solid ground
            }
        }
        System.out.printf("Stress: %d/100 completed in %dms%n",
            completed, System.currentTimeMillis() - t0);
        System.out.printf("Feature placement: doors=%d levels, cracked=%d levels%n",
            doorLevels, crackedLevels);
        if (!failed.isEmpty()) System.out.println("Failed: " + failed);
        System.out.printf("Door boxes: %d with a bottom that does not reach the floor, or no sprite%n", boxFaults);

        // AND THE VALIDATOR'S CLIMB, WHICH I TRIED TO CHECK HERE AND COULD NOT.
        //
        // A real jump, one impulse from the ground, reaches 70.3 px - measured. A re-implementation of the
        // validator's climb loop reached 431.7 px, because it re-applied the jump every 0.5s with no grounded
        // check and a jump takes about 0.86s to apex. On that basis LevelValidator's climb branch now requires
        // player.grounded.
        //
        // BUT THE MEASUREMENT WAS OF MY OWN RE-IMPLEMENTATION, NOT OF THE VALIDATOR, and the check I wrote on top
        // of it cannot fail: a 2-cell wall passes and a 6-cell wall fails, and BOTH STILL DO with the grounded
        // guard removed. So the guard is kept because it is what a player does - jump from the ground - and NOT
        // because a check proves it fixes anything. The check is deleted rather than left in place, because a
        // check that cannot fail is worse than no check: it is believed.
        return failed.size() + boxFaults;
    }
}
