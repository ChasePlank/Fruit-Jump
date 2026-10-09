package tropical;

import tropical.engine.LevelMap;

import java.util.ArrayList;
import java.util.List;

/**
 * The first nine levels are hand-built tutorials, not generated ones.
 *
 * Each teaches exactly one thing and shows it rather than saying it, in the
 * order the player needs it: move, fight, blast, shoot, deal with the bat,
 * know your items, know your enemies, know the ground, and know the water.
 * Level 9 ends the run back at the menu.
 *
 * THIS HEADER SAID "eight" AND "Level 8 ends the run" LONG AFTER LEVEL 9 EXISTED. Water was added as level 9 and
 * the count and the list were not revisited, so the claim was false in the file that DEFINES `LAST` - and the
 * check written to catch exactly this class of staleness did not see it, because it greps for the phrase
 * "ends at <N>" and this file says neither "ends at" nor a digit. Two spellings of the same claim, one covered.
 * A comment naming a value is a claim; a check that matches one phrasing of it is a check with a blind spot.
 *
 * Built PROGRAMMATICALLY from a flat floor plus placed entities rather than as
 * hand-written ASCII grids. Flat rooms are exactly the shape you should
 * generate - I have been consistently wrong about hand-placed grids, and these
 * have to be right because they are the first thing anyone plays.
 */
public class Tutorial {
    public static final int LAST = 9;
    static final int W = 60, H = 20, FLOOR = 17;

    public static boolean isTutorial(int level) {
        return level >= 1 && level <= LAST;
    }

    /** The last tutorial level sends the player back to the menu instead of level 10. */
    public static boolean endsTheTutorial(int level) {
        return level == LAST;
    }

    /** Floating words the player walks into. Drawn at world coordinates. */
    public record Sign(double x, double y, String text) {}

    /** Place a character at a cell, ignoring anything out of bounds. */
    private static void put(char[][] g, int col, int row, char c) {
        if (row < 0 || row >= H || col < 0 || col >= W) return;
        g[row][col] = c;
    }

    private static void wall(char[][] g, int col, int top, int bottom) {
        for (int r = top; r <= bottom; r++) put(g, col, r, '#');
    }

    public static LevelMap map(int level) {
        char[][] g = new char[H][W];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');

        // Flat floor, no hazards. Everything on top of it is placed by hand.
        for (int c = 1; c < W - 1; c++) {
            for (int r = FLOOR; r < H; r++) g[r][c] = '#';
        }
        put(g, 2, FLOOR - 1, 'P');
        put(g, W - 3, FLOOR - 1, 'E');

        switch (level) {
            case 1 -> {
                // THE ONE-WAY PLANK, and it is here because of a fault this project has had four times, seen from
                // the other side: the generator started placing one-way platforms in the run, and the TUTORIAL had
                // never shown one - the game contained a movement affordance it never taught. The movement level is
                // where it belongs.
                //
                // TWO ROWS UP: 64px, inside the 73px jump apex, and the height is the whole lesson. You pass UP
                // THROUGH a one-way and land on top, which cannot be shown one row up, because a body standing on
                // the walk already occupies the row above it. The snack is the reason to go up. Optional, so the
                // jump-only bot walks past it.
                for (int c = 20; c <= 22; c++) put(g, c, FLOOR - 2, '=');
                put(g, 21, FLOOR - 3, 'h');
            }
            case 2 -> {
                // One spider to stomp.
                put(g, 22, FLOOR - 1, 'o');
            }
            case 3 -> {
                // A breakable wall, floor to ceiling, with a snake BEHIND it.
                // The snake dies in the blast, which is the point: it shows a
                // bomb kills enemies as well as walls.
                wall(g, 24, 6, FLOOR - 1);
                put(g, 24, FLOOR - 1, 'C');
                put(g, 24, FLOOR - 2, 'C');
                put(g, 24, FLOOR - 3, 'C');
                put(g, 27, FLOOR - 1, 's');
            }
            case 4 -> {
                // Target practice: two spiders at range, nothing in between.
                put(g, 30, FLOOR - 1, 'o');
                put(g, 40, FLOOR - 1, 'o');

                // AND A LEDGE THE HOOKSHOT IS THE ONLY WAY UP TO. The sign above teaches the control; this is the
                // level teaching the USE, which is what the sign could not do on its own - every other mechanic in
                // the tutorial is taught beside the thing it is for.
                //
                // SIX ROWS UP, and above the SIGN ROW rather than on it. The signs are drawn at y = (FLOOR - 4) *
                // 32, so a ledge at FLOOR - 4 would have the words inside the stone - and the sign-collision check
                // compares signs to signs, not signs to terrain, so it would not have said so.
                //
                // Six rows is 192px. The jump apex is about 73px, so it is well out of reach; the hookshot's range
                // is 400px, so it is well inside that.
                for (int c = 8; c <= 14; c++) put(g, c, FLOOR - 6, '#');
                put(g, 11, FLOOR - 7, 'h');     // a snack, so the reward is the reason to go up
            }
            case 5 -> {
                // Bats, and room to learn that one knockdown clears the swarm.
                put(g, 28, FLOOR - 5, 'b');
                put(g, 36, FLOOR - 6, 'b');
            }
            case 6 -> {
                // Every item the game currently has, spaced out to be read.
                put(g, 14, FLOOR - 1, 'h');   // snack
                put(g, 24, FLOOR - 1, 'j');   // heart in a jar
                put(g, 32, FLOOR - 1, 'k');   // key
                put(g, 42, FLOOR - 1, 'D');   // and the door the key opens
            }
            case 7 -> {
                // Every enemy, boxed and hovering, out of reach. A display
                // case, not a fight.
                box(g, 12, 8, 'o');
                box(g, 24, 7, 's');
                box(g, 36, 9, 'b');
            }
            case 8 -> {
                // The ground hazards, two tiles wide so they are legible, and set INTO the floor rather than on
                // top of it. They were at FLOOR - 1 with solid '#' directly below, so they rendered as spikes
                // sitting on the ground - while the sign two lines below them promises "a pit costs you a heart,
                // not the run - climb out". Reported from play as "the spike level had them above ground, not a
                // pit". Clearing the row above and putting the spike in the floor is what makes it a pit.
                for (int c = 18; c <= 19; c++) { g[FLOOR - 1][c] = ' '; g[FLOOR][c] = '^'; }
                for (int c = 30; c <= 31; c++) { g[FLOOR - 1][c] = ' '; g[FLOOR][c] = '^'; }
            }
            case 9 -> {
                // WATER. The tutorial taught the spider, the bomb, the arrow, the bat, the pickups and the
                // spikes, and then water became common and piranhas arrived - so a player met both for the
                // first time in a generated level with nothing to tell them what they were.
                //
                // The water is ONE ROW at FLOOR - 1: ankle-deep, on the walk's own floor, which is exactly how
                // a flooded level is built (see LevelGen). The floor is untouched, so walking through it is
                // walking. The piranhas sit at FLOOR - 2, at the surface, where you can see them coming.
                for (int c = 16; c <= 34; c++) g[FLOOR - 1][c] = '~';
                for (int c = 22; c <= 24; c++) g[FLOOR - 2][c] = 'f';   // a group, because they come in groups
                put(g, 40, FLOOR - 1, '~');                             // and one on its own, to show the difference

                // AND A POOL, because the water above is ankle-deep ON PURPOSE - it is how a flooded level is
                // built - and a player who only ever sees this one never meets the water they can SWIM in. The
                // game has two kinds: a flooded walk, one row, which you wade through; and a pool, two rows with
                // a floor under it, which you swim in and can dive in. The README documents "Space / W - jump,
                // and swim upward in water" and "Down / S - dive (water only)", and until now the tutorial taught
                // neither, because there was nothing deep enough to teach them in.
                for (int c = 44; c <= 50; c++) { g[FLOOR - 2][c] = '~'; g[FLOOR - 1][c] = '~'; }
            }
            default -> { }
        }

        for (int r = 0; r < H; r++) {
            g[r][0] = '#';
            g[r][W - 1] = '#';
        }

        StringBuilder sb = new StringBuilder();
        for (char[] row : g) sb.append(new String(row)).append('\n');
        return LevelMap.parse(sb.toString());
    }

    /** A sealed 3x3 display box holding one enemy, for level 7. */
    private static void box(char[][] g, int col, int row, char enemy) {
        for (int c = col - 1; c <= col + 1; c++) {
            put(g, c, row - 1, '#');
            put(g, c, row + 1, '#');
        }
        put(g, col - 1, row, '#');
        put(g, col + 1, row, '#');
        put(g, col, row, enemy);
    }

    /** The floating words for a level. */
    public static List<Sign> signs(int level) {
        List<Sign> s = new ArrayList<>();
        double y = (FLOOR - 4) * 32.0;
        switch (level) {
            case 1 -> {
                s.add(new Sign(6 * 32, y, "MOVE   A / D   or   LEFT / RIGHT"));
                s.add(new Sign(30 * 32, y, "JUMP   SPACE"));
            }
            case 2 -> {
                s.add(new Sign(10 * 32, y, "THAT IS A SPIDER"));
                s.add(new Sign(20 * 32, y + 26, "JUMP ON ITS HEAD TO SQUASH IT"));
            }
            case 3 -> {
                // Beside the wall it names, not back at the spawn - the label
                // has to be on the thing it is labelling.
                s.add(new Sign(17 * 32, y, "CRACKED WALL"));
                s.add(new Sign(17 * 32, y + 26, "BOMB   G   -   it drops at your feet"));
                s.add(new Sign(29 * 32, y, "STAND BACK. the blast hurts you too"));
            }
            case 4 -> {
                // WAS JUST "ARROWS   F", the only sign in the tutorial that named a key without saying what it
                // does, when every other one follows "BOMB  G  -  it drops at your feet".
                s.add(new Sign(20 * 32, y, "ARROWS   F   -   hits what you face"));
                s.add(new Sign(32 * 32, y, "the spiders are out of reach. shoot them"));
                // AND THE HOOKSHOT, which the README leads with - "with hookshot, bombs, bow" - and which the
                // tutorial never mentioned in any of its nine levels. It fires at a wall and pulls you to it, so
                // it belongs on the level that is already about reaching what you cannot walk to.
                // TWO SIGNS, AND THE SECOND IS BELOW THE FIRST'S WRAP. The first is 37 characters and wraps to a
                // 34-character line, so its second line lands where a sign 26px below would start - which is the
                // same collision the JAR and KEY signs had on tutorial 6. TutorialTest caught it on the first run.
                // BACK AT 44, under the ledge. The signs are about thirteen tiles wide once wrapped, so there is no
                // room for a third one between the arrows sign at 12 and the spiders sign at 30 - 24 overlapped
                // both, which the collision check caught twice.
                s.add(new Sign(8 * 32, y, "HOOKSHOT   X   -   pulls you to a wall"));
                s.add(new Sign(8 * 32, y + 52, "Shift + X fires it upward"));
            }
            case 5 -> {
                s.add(new Sign(10 * 32, y, "BATS KNOCK YOU DOWN"));
                s.add(new Sign(10 * 32, y + 26, "they cannot hurt you - and they do not stay"));
            }
            case 6 -> {
                s.add(new Sign(12 * 32, y, "SNACK  -  one life back"));
                s.add(new Sign(22 * 32, y, "JAR  -  a life FOREVER, and a full refill"));
                // MOVED RIGHT, from 30 * 32. The JAR sign to its left is 41 characters and wraps to a 34-character
                // line, so it reaches past x = 1000. The KEY sign started at 960 and sat INSIDE it, and the two
                // overlaid so that neither could be read - visible on tutorial 6 since October.
                s.add(new Sign(38 * 32, y, "KEY  -  opens the door"));
            }
            case 7 -> {
                s.add(new Sign(20 * 32, y - 60, "WHAT IS OUT THERE"));
                // One name per creature, at the creature's own column. This was a single string with eight
                // literal spaces between the words, starting at column 10, so the names could not track boxes
                // at 12, 24 and 36 - reported from play as "the enemies level didn't space the names to match
                // the creature". Literal spaces cannot align to anything; positions can.
                s.add(new Sign(11 * 32, y - 34, "spider"));
                s.add(new Sign(23 * 32, y - 34, "snake"));
                s.add(new Sign(35 * 32, y - 34, "bat"));
            }
            case 8 -> {
                s.add(new Sign(10 * 32, y, "SPIKES"));
                s.add(new Sign(10 * 32, y + 26, "a pit costs you a heart, not the run - climb out"));
            }
            case 9 -> {
                s.add(new Sign(8 * 32, y, "WATER"));
                s.add(new Sign(8 * 32, y + 26, "you walk through it. it does not slow you down"));
                s.add(new Sign(20 * 32, y - 60, "PIRANHA"));
                s.add(new Sign(20 * 32, y - 34, "unlike a bat, this one takes a HEART"));
                // THE POOL'S OWN SIGN, beside the pool. The water above is wading; this is swimming, and the two
                // controls the README documents for it - swim up and dive - are only true here.
                s.add(new Sign(44 * 32, y - 60, "DEEP WATER"));
                s.add(new Sign(44 * 32, y - 34, "SPACE swims up.   DOWN / S dives"));
                // MOVED DOWN to y + 26, from y - 8. The sign above it is 36 characters, so it WRAPS ONTO A SECOND
                // LINE - and that line landed exactly where this one started. A sign that wraps occupies two
                // rows, and the next sign down has to clear both.
                s.add(new Sign(20 * 32, y + 26, "they come in groups. get out and they lose you"));
                s.add(new Sign(44 * 32, y, "that is everything. good luck"));
            }
            default -> { }
        }
        return s;
    }
}
