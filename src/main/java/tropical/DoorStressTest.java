package tropical;

import tropical.engine.LevelGen;
import tropical.engine.LevelValidator;
import tropical.engine.LevelMap;

/**
 * Stress test for the door/key/cracked generator features.
 * 100 fresh seeds through the validator bot (which now collects
 * keys and unlocks doors). Also counts feature placement.
 */
public class DoorStressTest {
    public static void main(String[] args) {
        int completed = 0;
        int doorLevels = 0, crackedLevels = 0;
        java.util.List<Long> failed = new java.util.ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (long seed = 201; seed <= 300; seed++) {
            LevelGen gen = new LevelGen(60, 14, seed);
            LevelMap map = gen.generate();
            if (!map.doors.isEmpty()) doorLevels++;
            if (!map.cracked.isEmpty()) crackedLevels++;
            if (LevelValidator.validateGenerated(gen, 30.0)) completed++;
            else failed.add(seed);
        }
        System.out.printf("Stress: %d/100 completed in %dms%n",
            completed, System.currentTimeMillis() - t0);
        System.out.printf("Feature placement: doors=%d levels, cracked=%d levels%n",
            doorLevels, crackedLevels);
        if (!failed.isEmpty()) System.out.println("Failed: " + failed);
        System.exit(failed.isEmpty() ? 0 : 1);
    }
}
