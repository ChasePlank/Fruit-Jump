package tropical;

import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;

/**
 * Pixel-art sprites defined as character grids + palette maps.
 * Hand-authored: pixel art at 32px tile scale needs an explicit pixel
 * grid — a downscaled render loses the grid that makes pixel art read.
 * Each sprite is a String[] (rows) of palette chars; ' ' = transparent.
 */
public class Sprite {
    /** Build a WritableImage from a char grid + palette. */
    public static WritableImage build(String[] rows, java.util.Map<Character, String> palette) {
        return buildScaled(rows, palette, rows[0].length(), rows.length);
    }

    /**
     * Build a sprite at an arbitrary destination size using nearest-
     * neighbor sampling from the char grid. Each destination pixel maps
     * to one grid cell — the pixel grid stays crisp at any scale.
     *
     * Used to pre-scale sprites to their exact PHYSICAL draw size (2x
     * the logical size): at render time the image lands 1:1 on screen,
     * so JavaFX's bilinear pipeline never resamples the art. (JavaFX 17
     * has no smoothing toggle on GraphicsContext — pre-scaling is the
     * only way to keep pixel art crisp.)
     */
    public static WritableImage buildScaled(String[] rows, java.util.Map<Character, String> palette,
                                            int dstW, int dstH) {
        int srcH = rows.length;
        int srcW = rows[0].length();
        WritableImage img = new WritableImage(dstW, dstH);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < dstH; y++) {
            int sy = y * srcH / dstH;
            String row = rows[sy];
            for (int x = 0; x < dstW; x++) {
                int sx = x * srcW / dstW;
                char c = row.charAt(sx);
                if (c == ' ') continue;
                String hex = palette.get(c);
                if (hex == null) continue;
                int argb = 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
                pw.setArgb(x, y, argb);
            }
        }
        return img;
    }

    // ---- Shared palette ----
    static java.util.Map<Character, String> PAL() {
        java.util.Map<Character, String> p = new java.util.HashMap<>();
        p.put('Y', "#F5D442"); // banana yellow
        p.put('D', "#B8860B"); // banana dark / outline
        p.put('G', "#5CBF3E"); // radioactive green (glow spots)
        p.put('W', "#FFFFFF");
        p.put('K', "#1A1A1A"); // near-black outline
        p.put('B', "#8B5A2B"); // brown (crate, stems, monkey fur)
        p.put('T', "#D2A679"); // tan (monkey face)
        p.put('S', "#787878"); // stone gray
        p.put('L', "#A8A8A8"); // stone light
        p.put('R', "#E23B2E"); // red (heart, bomb flash)
        p.put('O', "#FF8C00"); // orange (spike, key gold)
        p.put('N', "#4A90D9"); // blue (door)
        p.put('C', "#7EC8E3"); // cyan (cracked lines)
        p.put('M', "#2F4F4F"); // dark slate (bomb body)
        p.put('P', "#2FA8A0"); // climber's pack (teal)
        p.put('A', "#EDE6D6"); // climber body (pale cream)
        p.put('V', "#3A3F45"); // climber hair (dark slate)
        p.put('U', "#7A5230"); // climber boots (warm dark brown)
        return p;
    }

    // ---- Player: the climber, 16x22, facing RIGHT ----
    /**
     * The climber, replacing the banana.
     *
     * The backpack sits on the LEFT when facing right -- behind the
     * character -- so the SILHOUETTE carries facing direction.
     *
     * Why no face: the old character signalled direction with its face,
     * and that is fragile. The banana's features once drifted off centre
     * and it read as looking over its shoulder whichever way it moved.
     * Asymmetric mass cannot drift. It also leaves the player anonymous,
     * which is what makes the hair/pack customiser mean something rather
     * than being decoration.
     *
     * Why these colours: the sky is #E8763A and the ground is near-black,
     * so the body has to separate from BOTH. Pale cream reads light
     * against orange and bright against black, and the dark outline
     * carries it against the sky. A yellow body was rendered against the
     * sunset and failed -- it sank into it.
     */
    static String[] PLAYER = {
        "      KKKK      ",
        "     KVVVVK     ",
        "    KVVVVVVK    ",
        "    KVVVVVVK    ",
        "     KAAAAK     ",
        "     KAAAAK     ",
        "    KKAAAAKK    ",
        " KPPKAAAAAAK    ",
        " KPPKAAAAAAK    ",
        " KPPKAAAAAAK    ",
        " KPPKAAAAAAK    ",
        " KPPKAAAAAAK    ",
        " KPPKAAAAAAK    ",
        " KPPKAAAAAAK    ",
        " KPPKAAAAAAK    ",
        " KKKKAAAAAAK    ",
        "    KAAKKAAK    ",
        "    KAAKKAAK    ",
        "    KAAKKAAK    ",
        "    KAAKKAAK    ",
        "   KUUUUKKUUUUK ",
        "   KUUUUKKUUUUK ",
    };

    // Hair length = three HEAD shapes. The torso below is shared, so a body
    // tweak cannot drift out of sync with the hair variants.
    static String[] PLAYER_HEAD_SHORT = {
        "      KKKK      ",
        "     KVVVVK     ",
        "    KVAAAAVK    ",
        "    KAAAAAAK    ",
        "     KAAAAK     ",
        "     KAAAAK     ",
    };
    static String[] PLAYER_HEAD_MEDIUM = {
        "      KKKK      ",
        "     KVVVVK     ",
        "    KVVVVVVK    ",
        "    KVVVVVVK    ",
        "     KAAAAK     ",
        "     KAAAAK     ",
    };
    static String[] PLAYER_HEAD_LONG = {
        "      KKKK      ",
        "     KVVVVK     ",
        "    KVVVVVVK    ",
        "    KVVVVVVK    ",
        "    KVVVVVVK    ",
        "     KVAAVK     ",
    };


    /**
     * Rotate a char grid 90 degrees clockwise.
     *
     * The knocked-down pose is DERIVED from the standing grid this way rather
     * than hand-drawn, and that is the point: a prone body has to be as LONG as
     * the standing body is TALL. Hand-drawn into a 16x22 box it came out about
     * a quarter of the standing figure's size - "reminds me of a duck, seems
     * drastically smaller". A rotation cannot get that proportion wrong, and it
     * inherits the customiser's hair and pack colours for free.
     */
    public static String[] rotate90(String[] rows) {
        int h = rows.length, w = rows[0].length();
        String[] out = new String[w];
        for (int r = 0; r < w; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < h; c++) {
                sb.append(rows[h - 1 - c].charAt(r));
            }
            out[r] = sb.toString();
        }
        return out;
    }

    /** Full climber grid: the chosen head over PLAYER's shared torso. */
    public static String[] playerGrid(int hairLength) {
        String[] head = switch (hairLength) {
            case 0 -> PLAYER_HEAD_SHORT;
            case 2 -> PLAYER_HEAD_LONG;
            default -> PLAYER_HEAD_MEDIUM;
        };
        String[] out = new String[PLAYER.length];
        for (int i = 0; i < PLAYER.length; i++) {
            out[i] = (i < head.length) ? head[i] : PLAYER[i];
        }
        return out;
    }

    /**
     * Palette with the chosen hair and pack colours substituted in. This is
     * why colour costs nothing: the grid already marks which cells are hair
     * ('V') and pack ('P'), so recolouring is a map override rather than
     * another sprite sheet.
     */
    public static java.util.Map<Character, String> playerPal(
            String hairHex, String packHex) {
        java.util.Map<Character, String> p = new java.util.HashMap<>(PAL());
        p.put('V', hairHex);
        p.put('P', packHex);
        return p;
    }

    /** Horizontally mirror a char grid (for left-facing sprite variants). */
    public static String[] flipX(String[] rows) {
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++)
            out[i] = new StringBuilder(rows[i]).reverse().toString();
        return out;
    }

    // ---- Player: radioactive banana character, facing right, 14x22 ----
    // Face (eyes + mouth) is on the RIGHT half — the direction of
    // travel. Earlier version had features drifting left, so the base
    // sprite read as looking over its shoulder (playtest: "always
    // looking behind itself").
    static String[] BANANA = {
        "      BB      ",
        "     KBBK     ",
        "    KYYYYK    ",
        "   KYYYYYYK   ",
        "   KYYYYYYYK  ",
        "  KYYYYWYWYK  ",
        "  KYYYYWYWYK  ",
        "  KYYYYYYYYK  ",
        "  KYYYKKYYYK  ",
        "  KYYKYKYKYK  ",
        "  KYYYYYYYYK  ",
        "  KYYYYYYYYK  ",
        "  KGGYYYYGGK  ",
        "  KYYGYYGYYK  ",
        "  KYYYYYYYYK  ",
        "   KYYYYYYK   ",
        "   KYYYYYYK   ",
        "    KYYYYK    ",
        "    KYYYYK    ",
        "     KYYK     ",
        "     KYYK     ",
        "      KK      ",
    };

    // ---- Enemy: banana-eating monkey, 16x16 ----
    // (playtest: "enemies seem to be rocks — monkeys or apes would
    // fit better, things that eat bananas")
    static String[] ENEMY = {
        "  KKKK    KKKK  ",
        " KBBBBKKKKBBBBK ",
        " KBBTTTTTTTTBBK ",
        " KBTTTTTTTTTTBK ",
        " KBTKKTTTTKKTBK ",
        " KBTKKTTTTKKTBK ",
        " KBTTTTTTTTTTBK ",
        " KBTTTTKKTTTTBK ",
        " KBTTTTTTTTTTBK ",
        " KBBKKKKKKKKBBK ",
        "  KBBTTTTTTBBK  ",
        "   KBBBBBBBBK   ",
        "   KBKBBBBKBK   ",
        "   KBKBBBBKBK   ",
        "    KK KBK KK   ",
        "       KKK      ",
    };

    // ---- Heart pickup, 8x8 ----
    static String[] HEART = {
        " KK KK ",
        "KRRKRRK",
        "KRRRRRK",
        "KRRRRRK",
        " KRRRK ",
        "  KRK  ",
        "   K   ",
        "       ",
    };

    // ---- Key, 8x8 ----
    static String[] KEY = {
        "  KKKK  ",
        " KOOOOK ",
        " KO  OK ",
        " KOOOOK ",
        "  KKOKK ",
        "    KO  ",
        "    KO  ",
        "    KK  ",
    };

    // ---- Spike (drawn per-tile), 8x8 ----
    static String[] SPIKE = {
        "K K K K ",
        "OLOLOLOL",
        "OLOLOLOL",
        "OLOLOLOL",
        "OLOLOLOL",
        "OLOLOLOL",
        "SSSSSSSS",
        "        ",
    };

    // ---- Door (2 tiles tall = 64px; sprite 16x32, scaled 2x) ----
    static String[] DOOR = {
        "KKKKKKKKKKKKKKKK",
        "KNNNNNNNNNNNNNNK",
        "KNCCNNNNNNNNCCNK",
        "KNCCNNNNNNNNCCNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNOONNNNNK",
        "KNNNNNNNOONNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KNNNNNNNNNNNNNNK",
        "KKKKKKKKKKKKKKKK",
    };

    // ---- Cracked tile overlay, 8x8 (drawn over stone) ----
    static String[] CRACK = {
        "C      C",
        " CC    C",
        "  CCCC C",
        " C CCCC ",
        "C  CC CC",
        " C CC C ",
        "CC C  C ",
        " C    CC",
    };

    // ---- Exit portal, 22x32 (2 tiles tall: drawn 44x64 physical) ----
    // Was an 8x12 orange rectangle on a black pole: at 32x48 physical
    // pixels it read as a paintbrush, and nothing about a brush says
    // "this ends the level". A lit stone archway is a thing players
    // already read as a way out, and it fits what this level format
    // IS -- a screen-transition game, where every screen ends at a
    // passage.
    //
    // The green interior is just a lit portal. Do NOT write flavour
    // tying it to a "radioactive" theme: the radioactive premise belongs
    // to Tropical Punch, not to this game (Kinger, Sept 28). This game
    // is fully original and has no radioactive aspect anywhere in it.
    //
    // Sized to 2 tiles tall on purpose: at 40x60 the first version was
    // smaller than the player sprite, and a landmark smaller than the
    // thing standing next to it is not a landmark. The door is 2 tiles
    // tall, so the exit matches it.
    //
    // TODO(art): this is a readable placeholder, not the intended look.
    // The final portal design is an art-direction call.
    static String[] EXIT = {
        "   SSSSSSSSSSSSSSSS   ",
        "  SLLLLLLLLLLLLLLLLS  ",
        " SSLLLLLLLLLLLLLLLLSS ",
        " SSLLGGGGGGGGGGGGLLSS ",
        " SSLLGGGGGGGGGGGGLLSS ",
        " SSLLGGGGGGGGGGGGLLSS ",
        " SSLLGGGGGGGGGGGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGWWWWWWWWGGLLSS ",
        " SSLLGGGGGGGGGGGGLLSS ",
        " SSLLGGGGGGGGGGGGLLSS ",
        " SSLLGGGGGGGGGGGGLLSS ",
        " SSLLGGGGGGGGGGGGLLSS ",
        " SSSSSSSSSSSSSSSSSSSS ",
        " DDDDDDDDDDDDDDDDDDDD ",
    };

    // ---- Heart in a jar, 16x16 (the every-tenth-level reward) ----
    /**
     * A glass jar with a heart inside. Cyan glass with a dark outline so it
     * reads on both the sunset sky and the black ground; the heart is red so
     * the reward is unmistakably the health item.
     */
    static String[] JAR = {
        "                ",
        "     KKKKKK     ",
        "    KCCCCCCK    ",
        "   KCCCCCCCCK   ",
        "   KC      CK   ",
        "   KCRR  RRCK   ",
        "   KCRRRRRRCK   ",
        "   KC RRRR CK   ",
        "   KC  RR  CK   ",
        "   KC      CK   ",
        "   KC      CK   ",
        "   KC      CK   ",
        "   KC      CK   ",
        "   KCCCCCCCCK   ",
        "    KCCCCCCK    ",
        "     KKKKKK     ",
    };

    // ---- Enemies ----
    /**
     * Sprawled flat, 22x16 - hand-drawn, unlike the rotated placeholder.
     *
     * The rotation proved the SIZE had to be 22 long, and it proved the pose
     * could not be a rotation: rotating a 16-wide standing figure gives a
     * 16-THICK prone figure, which is far too deep for a body on the ground.
     * A sprawl is LONG AND THIN, so it needs its own drawing at 22x16 with the
     * figure only about ten rows deep. Arms are thrown out, which is what makes
     * it read as fallen rather than lying down tidily.
     */
    static String[] SPRAWL = {
        "                      ",
        "                      ",
        "                      ",
        "                      ",
        "          KKKKKK      ",
        "         KKPPPPKK     ",
        "        KKPPPPPPK KKK ",
        "        KPPPPPPPKVVVK ",
        "       KKPPPPPPKVVVVVK",
        "      KKAAAAAAKAAAAAK ",
        "     KAAAAAAAAKAAAAK  ",
        "    KAAAAAAAAAAAKAAKK ",
        "  KKKAAAAAAAAAAKKKK   ",
        "  KUUKAAAAAAAAK       ",
        "  KUUUKKKKKKK         ",
        "   KKK                ",
        "                      ",
    };

    /**
     * Up onto one knee, 16x22 - the in-between as the climber gets back up.
     * Same box as standing so the feet anchor does not move: the figure is the
     * standing one compressed downward with the legs folded under it.
     */
    static String[] KNEEL = {
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
        "      KKKK      ",
        "     KVVVVK     ",
        "    KVVVVVVK    ",
        "    KVVVVVVK    ",
        "     KAAAAK     ",
        "     KAAAAK     ",
        "    KKAAAAKK    ",
        " KPPKAAAAAAK    ",
        " KPPKAAAAAAK    ",
        " KPPKAAAAAAK    ",
        " KKKKAAAAAAK    ",
        "   KAAAAAAAAK   ",
        "   KAAAAAAAAKK  ",
        "   KUUUUUUUUK   ",
        "   KKKKKKKKKK   ",
        "                ",
        "                ",
    };

    /**
     * Spider, 24x20. Four legs a side as diagonal strokes, round body, red eye
     * cluster - the eyes are what stop a dark shape reading as a rock at this
     * size.
     */
    static String[] SPIDER = {
        "                        ",
        "                        ",
        "                        ",
        "                        ",
        "      KKKKKK            ",
        "     KMMMMMMK           ",
        "    KMMMMMMMMK          ",
        "   KMMMMMMMMMMK         ",
        "   KMMMMMMMMMMMKKKK     ",
        "  KMMMMMMMMMMMMMMMMK    ",
        "  KKKMKKMKKMKMMMRRMMK   ",
        "     K  K  K KKMMKKKK   ",
        "    K   K  K   KK       ",
        "    K  K   K   K K      ",
        "   K   K   K    K K     ",
        "   K   K    K   K K     ",
        "  K    K    K    K K    ",
        "  K   K     K    K  K   ",
        "      K     K           ",
        "                        ",
    };

    /**
     * Snake, 32x16 - deliberately WIDER than the spider box, because a snake
     * drawn tall in a square box is not a snake. Head raised at the left, then
     * the body coiling back right; the light tan BELLY is what defines it as a
     * tube rather than a silhouette. This is the PURSUIT pose - see SNAKE_COIL
     * for the resting one.
     */
    static String[] SNAKE = {
        "                                ",
        "                         KRRRR  ",
        "    KKK                 KMRRRRR ",
        "   KMMMKK              KMMRRRRRR",
        "  KMMMMMMK            KMMMMMMMMK",
        " KMMMMMMMMK          KMMMMMMMMMK",
        " KMMKKKKMMMK        KMMMMMMMMMK ",
        " KKK    KMMMK      KMMMKKKKKKK  ",
        " K       KMMMK    KMMMK         ",
        "          KMMMKKKKMMMK          ",
        "           KMMMMMMMMK           ",
        "            KMMMMMMK            ",
        "             KKMMMK             ",
        "               KKK              ",
        "                                ",
        "                                ",
    };

    /**
     * Snake at rest: coiled, head up. Coiled is both the natural resting read
     * and a far better silhouette than a long body standing still. The coil is
     * concentric loops - outer, middle, inner mass - with the head above it.
     */
    static String[] SNAKE_COIL = {
        "                        ",
        "                        ",
        "                        ",
        "                        ",
        "                        ",
        "                        ",
        "            MMMMMM      ",
        "          MMMMMMMMMM    ",
        "         MMMM   MMMMM   ",
        "         MM       MMM   ",
        "         MM    MM  MMM  ",
        "     MM  MMM   MM  MMM  ",
        "  MMMMMMM MMMMMM  MMM   ",
        " MMRRRMMMMM     MMMMM   ",
        "MMMMMMMMMMMMMMMMMMMM    ",
        " MMMMMMMMMMMMMMMMM      ",
        "  MMMMM    MMMM         ",
        "                        ",
        "                        ",
        "                        ",
    };
    // ---- Bomb, 6x6 ----
    // ---- Bat, 14x8 (the airborne pursuer) ----
    // ---- Snack, 16x16 (replaces the heart as the world pickup) ----

    /**
     * The pickup is a snack bar, not a heart. The HUD bar is unchanged and
     * still reads as health - this is the thing lying in the world, and it
     * reads as provisions, which is what pairs with the climber's pack.
     *
     * Rotated ~45 degrees clockwise from vertical (top tilts right), green
     * wrapper with the top quarter tan where the bar shows through. Green on
     * the dark chamber interior reads; the dark outline carries it against
     * the sunset sky.
     */
    static String[] SNACK = {
        "             KK ",
        "            KTTK",
        "           KTTTK",
        "          KTTGGK",
        "         KTTGGK ",
        "        KTGGGGK ",
        "       KTGGGGK  ",
        "      KTGGGGK   ",
        "     KTGGGGK    ",
        "    KTGGGGK     ",
        "   KTGGGGK      ",
        "  KTGGGGK       ",
        "  KGGGGK        ",
        "  KGGGK         ",
        "   KKK          ",
        "                ",
    };

    /**
     * Wings span the full grid, body in the middle, two claws below. Dark
     * slate deliberately: it reads against the sunset sky, which is where it
     * spends its time, rather than against the black ground.
     */
    static String[] BAT = {
        "KK          KK",
        " KKK      KKK ",
        "  KKMMMMMMKK  ",
        "  KMMMMMMMMK  ",
        "   KMMMMMMK   ",
        "   KMMKKMMK   ",
        "    K KK K    ",
        "              ",
    };

    static String[] BOMB = {
        "  KK  ",
        " KOOK ",
        "KMMMMK",
        "KMMMMK",
        "KMMMMK",
        " KKKK ",
    };

    // ---- Arrow, 8x3 ----
    static String[] ARROW = {
        "      KK",
        "BBBBBBOK",
        "      KK",
    };
}
