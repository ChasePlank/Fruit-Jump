package tropical;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The climber's appearance, chosen in the customiser.
 *
 * ARCHITECTURE NOTE - why this is not a pile of sprite sheets:
 * hair length is a GRID variant (three head shapes), but hair COLOUR and
 * pack COLOUR are PALETTE swaps. The sprite builder maps palette characters
 * to hex colours, so recolouring is a map override at build time and costs
 * nothing. Pre-baking combinations would be 3 lengths x N hair x N pack
 * sheets, which is how this feature turns into a maintenance problem.
 *
 * The body colour is deliberately NOT customisable. It is pale cream because
 * it has to separate from the orange sky AND the near-black ground; letting a
 * player pick a body colour would let them pick an invisible climber.
 */
public class CharacterConfig {
    public static final String FILE =
            System.getProperty("user.home") + "/.climber-look.txt";

    /** 0 = short, 1 = medium, 2 = long. */
    public int hairLength = 1;
    /** Index into HAIR_COLORS. */
    public int hairColor = 0;
    /** Index into PACK_COLORS. */
    public int packColor = 0;

    // Swatches kept small and chosen to read on both the sunset sky and the
    // black ground. Every one of these has been checked as a silhouette
    // colour, not just picked because it looks nice in a list.
    public static final String[] HAIR_COLORS = {
        "#3A3F45",  // dark slate
        "#6B4A2F",  // brown
        "#8C2F39",  // dark red
        "#D9CFA5",  // pale sand (light option, still outlined)
        "#3F5D8C",  // denim blue
        "#5B3A6E",  // plum
    };
    public static final String[] HAIR_NAMES = {
        "slate", "brown", "auburn", "sand", "denim", "plum"
    };

    public static final String[] PACK_COLORS = {
        "#2FA8A0",  // teal
        "#C4452F",  // rust
        "#3E6FD0",  // blue
        "#7A9B2F",  // moss
        "#D8A02B",  // amber
        "#8A5AA8",  // violet
    };
    public static final String[] PACK_NAMES = {
        "teal", "rust", "blue", "moss", "amber", "violet"
    };

    public static final String[] HAIR_LENGTH_NAMES = {"short", "medium", "long"};

    public String hairHex() { return HAIR_COLORS[clamp(hairColor, HAIR_COLORS.length)]; }
    public String packHex() { return PACK_COLORS[clamp(packColor, PACK_COLORS.length)]; }

    private static int clamp(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }

    public void normalise() {
        hairLength = clamp(hairLength, 3);
        hairColor = clamp(hairColor, HAIR_COLORS.length);
        packColor = clamp(packColor, PACK_COLORS.length);
    }

    /** Load the saved look, or defaults if there is nothing readable. */
    public static CharacterConfig load() {
        CharacterConfig c = new CharacterConfig();
        try {
            File f = new File(FILE);
            if (!f.exists()) return c;
            List<String> lines = new ArrayList<>(Files.readAllLines(f.toPath()));
            for (String line : lines) {
                String[] kv = line.split("=", 2);
                if (kv.length != 2) continue;
                String v = kv[1].trim();
                switch (kv[0].trim()) {
                    case "hairLength" -> c.hairLength = Integer.parseInt(v);
                    case "hairColor" -> c.hairColor = Integer.parseInt(v);
                    case "packColor" -> c.packColor = Integer.parseInt(v);
                    default -> { }
                }
            }
        } catch (Exception ex) {
            // A broken look file must never stop the game starting.
            System.err.println("could not read the look file: " + ex.getMessage());
        }
        c.normalise();
        return c;
    }

    public void save() {
        normalise();
        try {
            Files.writeString(new File(FILE).toPath(),
                "hairLength=" + hairLength + "\n"
              + "hairColor=" + hairColor + "\n"
              + "packColor=" + packColor + "\n");
        } catch (IOException ex) {
            System.err.println("could not save the look: " + ex.getMessage());
        }
    }
}
