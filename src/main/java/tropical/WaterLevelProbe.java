package tropical;

import tropical.engine.*;

/** Which generated levels actually contain water. The capture tool needs one, and guessing has not worked. */
public class WaterLevelProbe {
    public static void main(String[] args) {
        StringBuilder withWater = new StringBuilder();
        int found = 0;
        for (int level = 1; level <= 40; level++) {
            World w = new World();
            LevelGen.forLevel(level).generate().buildWorld(w);
            Water field = w.water.water();
            if (field == null) continue;
            int tiles = 0;
            for (int c = 0; c < 80; c++)
                for (int r = 0; r < 40; r++)
                    if (field.isWater(c * 32 + 16, r * 32 + 16)) tiles++;
            if (tiles > 0) { withWater.append(level).append("(").append(tiles).append(") "); found++; }
        }
        System.out.println("levels with water: " + found + " of 40");
        System.out.println("  " + withWater);
    }
}
