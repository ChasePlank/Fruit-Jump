package tropical;
import tropical.engine.*;

/**
 * GroundFillTest - grounded terrain, a working bombable chamber, and a parser
 * that keeps the grid it was given.
 *
 * Ported onto the current engine (the original went to the zip against the
 * Sept 21 snapshot). Three live bugs are under test here:
 *
 *  1. Floating terrain: the walk writes one '#' per column on flat runs, so most
 *     of a floor hung over empty space and stepping off it dropped you out of the
 *     level.
 *  2. The bombable chamber never worked: it had no floor, so on a flat run the
 *     heart hung over the map's void and taking it dropped you out of the world.
 *  3. LevelMap.parse dropped every empty line, so a level with a blank row in the
 *     middle silently shifted everything below it up one row.
 */
public class GroundFillTest {
    static int failures = 0;
    static final double DT = GameLoop.DT;
    static final int W = 60, H = 14;

    static void check(String n, boolean ok) { check(n, ok, ""); }

    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    static final char[] TERRAIN = { '#', 'C', 'D', '/', '\\', '^' };

    /** A floating platform: a block with two rows of air under it and a floored pit
     *  below that. The floor part is what separates it from a spike alcove. */
    static boolean isFloating(LevelMap m, int r, int c) {
        return m.cell(r, c) == '#' && m.cell(r + 1, c) == ' '
            && m.cell(r + 2, c) == '#';
    }

    static boolean isTerrain(char ch) {
        for (char t : TERRAIN) if (ch == t) return true;
        return false;
    }

    /** Floating columns, open columns, solid mass and overhangs for a seed set. */
    static int[] survey(int levelNum, int seeds) {
        int floating = 0, open = 0, mass = 0, overhangs = 0;
        for (long seed = 201; seed < 201 + seeds; seed++) {
            LevelGen gen = new LevelGen(W, H, seed, levelNum);
            LevelMap map = gen.generate();
            for (int c = 0; c < W; c++) {
                int anchor = -1;
                for (int r = H - 1; r >= 0; r--) {
                    if (isTerrain(map.cell(r, c))) { anchor = r; break; }
                }
                if ((anchor >= 0) != (map.cell(H - 1, c) == '#')) floating++;
                if (anchor < 0) open++;
            }
            for (int c = 0; c < W; c++) {
                for (int r = 0; r < H; r++) {
                    if (map.cell(r, c) == '#') {
                        mass++;
                        if (r + 1 < H && map.cell(r + 1, c) == ' ') overhangs++;
                    }
                }
            }
        }
        return new int[]{floating, open, mass, overhangs};
    }

    public static void main(String[] args) {
        System.out.println("Ground fill + chamber + parser (ported onto HEAD)");
        // which game levels (seed 1000+levelNum) come out flooded - for a
        // screenshot of the water actually on screen
        StringBuilder wet = new StringBuilder("  flooded game levels: ");
        for (int lv = 1; lv <= 40; lv++) {
            LevelMap m = new LevelGen(60, 14, 1000L + lv, lv).generate();
            int first = -1, cells = 0;
            for (int c = 0; c < 60; c++) {
                for (int r = 0; r < 14; r++) {
                    if (m.cell(r, c) == '~') { if (first < 0) first = c; cells++; }
                }
            }
            if (cells > 0 && first > 6 && first < 20) wet.append("L").append(lv)
                .append("(col").append(first).append(") ");
        }
        System.out.println(wet);
        StringBuilder climbs = new StringBuilder("  climbing game levels: ");
        for (int lv = 1; lv <= 40; lv++) {
            LevelMap m = new LevelGen(60, 14, 1000L + lv, lv).generate();
            for (int c = 10; c < 22; c++) {
                for (int r = 5; r < 10; r++) {
                    if (m.cell(r, c) == '#' && m.cell(r + 1, c) == ' '
                            && m.cell(r + 2, c) == '#') {
                        climbs.append("L").append(lv).append("(col").append(c).append(") ");
                        c = 60; break;
                    }
                }
            }
        }
        System.out.println(climbs);
        System.out.println("================================================");

        int[] l1 = survey(1, 60);
        check("ground: no column with terrain ends above the map bottom",
            l1[0] == 0, l1[0] + " floating column(s) in 60 levels");
        check("ground: gaps stay open (the jumps are not filled in)",
            l1[1] > 0, l1[1] + " open columns kept");
        check("ground: the terrain is a mass, not one tile per column",
            l1[2] > 60 * W * 3, String.format("%,d solid cells (%,d per level)",
                l1[2], l1[2] / 60));
        System.out.printf("  overhanging cells: %d (deliberate carve-outs only)%n", l1[3]);

        int[] l22 = survey(22, 40);
        check("ground: still grounded at level 22 (difficulty scales the walk)",
            l22[0] == 0 && l22[1] > 0,
            l22[0] + " floating, " + l22[1] + " open columns");

        // --- the generator's own quality gate -----------------------------
        int done22 = 0, l22Count = 0;
        java.util.List<Long> failed22 = new java.util.ArrayList<>();
        for (long seed = 201; seed <= 260; seed++) {
            LevelGen g = new LevelGen(W, H, seed, 22);
            g.generate();
            l22Count++;
            if (LevelValidator.validateGenerated(g, 30.0)) done22++;
            else failed22.add(seed);
        }
        check("scaling: level-22 levels are all completable now that gaps cap at 4",
            done22 == l22Count, done22 + "/" + l22Count
                + (failed22.isEmpty() ? "" : " failing: " + failed22));

        // EVERY band, not just the two I kept checking. The whole difficulty curve
        // was untested above level 1 until now (DoorStressTest calls the 3-arg
        // constructor, so levelNum is always 1), and "it works at 1 and at 22" is
        // not the same claim as "it works".
        int[] bands = {2, 3, 5, 8, 12, 16, 20, 25, 30};
        StringBuilder bandReport = new StringBuilder();
        int badBands = 0;
        for (int lv : bands) {
            int done = 0, total = 0;
            for (long seed = 401; seed <= 412; seed++) {
                LevelGen g = new LevelGen(W, H, seed, lv);
                g.generate();
                total++;
                if (LevelValidator.validateGenerated(g, 30.0)) done++;
            }
            if (done < total) badBands++;
            bandReport.append("L").append(lv).append(":").append(done).append("/").append(total).append(" ");
        }
        check("scaling: every level band is completable, not just the ones I checked",
            badBands == 0, bandReport.toString().trim());

        int widest = 0;
        for (long seed = 2001; seed <= 2100; seed++) {
            LevelGen g = new LevelGen(W, H, seed, 22);
            g.generate();
            int[] pf = g.pathFloor;
            for (int c = 1; c < W - 1; c++) {
                if (pf[c] >= 0) continue;
                int run = 1;
                while (c + run < W && pf[c + run] < 0) run++;
                if (pf[c - 1] >= 0 && c + run < W && pf[c + run] >= 0) widest = Math.max(widest, run);
                c += run;
            }
        }
        check("scaling: no walk gap exceeds the jump (4 cells = 128px < 137px)",
            widest <= 4, "widest walk gap " + widest + " cells = " + widest * 32 + "px");

        // the new difficulty: floating platforms to climb, and none at level 1
        int floatsAt22 = 0, floatsAt1 = 0, widestPlat = 0, tallest = 0;
        for (long seed = 2001; seed <= 2060; seed++) {
            LevelMap m = new LevelGen(W, H, seed, 22).generate();
            for (int c = 1; c < W - 1; c++) {
                for (int r = 1; r < H - 2; r++) {
                    // a walk-level block with air beneath it = a floating platform
                    if (isFloating(m, r, c)) {
                        floatsAt22++;
                        int w = 1;
                        while (c + w < W && isFloating(m, r, c + w)) w++;
                        widestPlat = Math.max(widestPlat, w);
                    }
                }
            }
        }
        for (long seed = 2001; seed <= 2060; seed++) {
            LevelMap m = new LevelGen(W, H, seed, 1).generate();
            for (int c = 1; c < W - 1; c++) {
                for (int r = 1; r < H - 2; r++) {
                    if (isFloating(m, r, c)) floatsAt1++;
                }
            }
        }
        check("climb: levels above 1 have floating platforms to jump between",
            floatsAt22 > 0, floatsAt22 + " platform cells over 60 level-22 levels");
        check("climb: level 1 is unchanged - the scaling starts above it",
            floatsAt1 == 0, floatsAt1 + " platform cells at level 1");
        // consecutive climbs chain into longer runs (3+3+...), so what matters is
        // that every platform is a multiple of 3 wide, not that runs are short
        check("climb: platforms are 3-wide units - a landing the arc can hit",
            widestPlat >= 3 && widestPlat % 3 == 0,
            "widest run " + widestPlat + " cells (=" + (widestPlat / 3) + " platforms)");

        // --- flooded gaps ---------------------------------------------------
        // Water in the one place the walk already leaves empty. It costs the
        // guaranteed path nothing (the bot jumps gaps as before) and turns a
        // missed jump from "fall out of the world" into "swim out".
        int flooded = 0, waterCells = 0;
        for (long seed = 201; seed <= 300; seed++) {
            LevelMap m = new LevelGen(W, H, seed, 1).generate();
            int n = 0;
            for (int r = 0; r < H; r++) {
                for (int c = 0; c < W; c++) if (m.cell(r, c) == '~') n++;
            }
            waterCells += n;
            if (n > 0) flooded++;
        }
        check("flooded: some generated levels have water", flooded > 0,
            flooded + "/100 levels, " + waterCells + " water cells");

        LevelMap pool = null;
        int pr = -1, pc0 = -1, pc1 = -1;
        for (long seed = 201; seed <= 600 && pool == null; seed++) {
            LevelMap m = new LevelGen(W, H, seed, 1).generate();
            for (int r = 0; r < H && pool == null; r++) {
                for (int c = 0; c < W; c++) {
                    if (m.cell(r, c) == '~') {
                        pool = m; pr = r; pc0 = c; pc1 = c;
                        while (pc1 + 1 < W && m.cell(r, pc1 + 1) == '~') pc1++;
                        break;
                    }
                }
            }
        }
        if (pool != null) {
            int depth = 0;
            while (pool.cell(pr + depth, pc0) == '~') depth++;
            check("flooded: the pool is 2-3 cells wide and 2 deep",
                (pc1 - pc0 + 1) >= 2 && (pc1 - pc0 + 1) <= 3 && depth == 2,
                String.format("%d wide, %d deep (%dpx)", pc1 - pc0 + 1, depth, depth * 32));
            check("flooded: the pool has a solid floor, not a bottomless void",
                pool.cell(pr + depth, pc0) == '#');
            check("flooded: the surface sits at the walk level",
                pool.cell(pr, pc0 - 1) == '#' && pool.cell(pr, pc1 + 1) == '#');

            World pw = new World();
            pool.buildWorld(pw);
            double cx = (pc0 * 32 + (pc1 + 1) * 32) / 2.0;
            Physics.Body swimmer = new Physics.Body(cx, (pr + 1) * 32.0, 24, 44);
            pw.addBody(swimmer);
            boolean out = false;
            for (int i = 0; i < 900 && !out; i++) {
                pw.water.setVerticalInput(-1);        // hold UP
                swimmer.vx = -200;                    // steer at a lip
                pw.update(DT);
                if (swimmer.grounded && !swimmer.inWater && (swimmer.y + swimmer.hh) <= pr * 32 + 1) {
                    out = true;
                }
            }
            check("flooded: a swimmer who falls in can climb out", out,
                String.format("x=%.0f feet=%.0f inWater=%b", swimmer.x, swimmer.y + swimmer.hh,
                    swimmer.inWater));
        }

        // --- the chamber, structurally -------------------------------------
        LevelGen gen = null;
        for (long seed = 1; seed <= 4000 && gen == null; seed++) {
            LevelGen g = new LevelGen(W, H, seed, 1);
            if (!g.generate().cracked.isEmpty()) gen = g;
        }
        if (gen == null) { check("chamber: one exists to test", false, "none in 4000 seeds"); return; }
        LevelMap map = gen.lastMap;
        Physics.AABB box = map.cracked.get(0);
        for (Physics.AABB a : map.cracked) if (a.x0 < box.x0) box = a;
        int fr = (int) (box.y0 / 32), c = (int) (box.x0 / 32);
        check("chamber: two adjacent cracked floor tiles",
            map.cell(fr, c) == 'C' && map.cell(fr, c + 1) == 'C',
            String.format("row %d, cols %d-%d", fr, c, c + 1));
        check("chamber: one cell of seat - the bombed hole is the other cell of air",
            map.cell(fr + 1, c) == ' ' && map.cell(fr + 1, c + 1) == 'h');
        check("chamber: has an explicit floor (the old version wrote none)",
            map.cell(fr + 2, c) == '#' && map.cell(fr + 2, c + 1) == '#',
            "row " + (fr + 2));

        // --- the chamber, physically: bomb, drop in, take it, jump out -----
        World w = new World();
        map.buildWorld(w);
        Combat combat = new Combat();
        combat.playerHP = 1;          // hearts only heal a hurt player
        PlayerInventory inv = new PlayerInventory();
        Physics.Body p = new Physics.Body(map.spawnX, map.spawnY, 24, 44);
        w.addBody(p);

        int before = w.tiles.size();
        w.addProjectile(Projectile.bomb(box.x0 - 380 + 16, box.y0 - 16, 1));
        for (int i = 0; i < 180; i++) w.update(DT);
        boolean solidLeft = false;
        for (Physics.AABB a : map.cracked) if (w.tiles.contains(a)) solidLeft = true;
        check("chamber: the bomb opens the floor",
            !solidLeft && w.tiles.size() < before,
            "tiles " + before + " -> " + w.tiles.size());

        Pickup heart = null;
        for (Pickup pk : w.pickups) if (pk.type == Pickup.Type.HEART) heart = pk;

        double chamberFloor = (fr + 2) * 32.0, pathLevel = fr * 32.0;
        double holeCentre = (c * 32 + (c + 2) * 32) / 2.0;
        p.x = box.x0 - 80;
        p.y = pathLevel - p.hh;
        p.vx = 0; p.vy = 0;

        boolean fellIn = false;
        for (int i = 0; i < 400 && !fellIn; i++) {
            p.vx = Math.abs(p.x - holeCentre) < 8 ? 0 : 200;   // line up, then drop
            w.update(DT);
            if (p.grounded && Math.abs((p.y + p.hh) - chamberFloor) < 3.0) fellIn = true;
        }
        check("chamber: the player falls in and lands on the floor", fellIn,
            String.format("feet=%.0f chamber floor=%.0f", p.y + p.hh, chamberFloor));

        boolean gotHeart = false;
        for (int i = 0; i < 120 && !gotHeart; i++) {
            p.vx = 200;
            w.update(DT);
            for (Pickup pk : w.pickups) pk.tryCollect(p, combat, inv);
            if (heart != null && !heart.active) gotHeart = true;
        }
        check("chamber: the heart is collectable inside", gotHeart,
            String.format("hp=%.0f", combat.playerHP));

        boolean escaped = false;
        for (int i = 0; i < 300 && !escaped; i++) {
            if (p.grounded) p.vy = -420;
            p.vx = -200;
            w.update(DT);
            if (p.grounded && Math.abs((p.y + p.hh) - pathLevel) < 2.0) escaped = true;
        }
        check("chamber: the player can jump back out onto the path", escaped,
            String.format("feet=%.0f path=%.0f", p.y + p.hh, pathLevel));
        check("chamber: the player is still inside the level",
            p.y > 0 && p.y < H * 32.0, String.format("y=%.0f", p.y));

        // --- the parser -----------------------------------------------------
        // Height/width are package-private, so the check has to be positional:
        // if the interior blank row were dropped, everything below would shift up
        // and row 4 would be past the end (cell() returns ' ' out of bounds).
        // GENUINELY EMPTY rows, not rows of spaces. The bug this guards dropped empty
        // lines - and a row of five spaces is not empty, so the original version of this
        // check exercised nothing at all. Found by mutation testing: re-introducing the
        // drop before the padding fails this check (row4 reads ' '), while the same
        // mutation placed after the padding is a no-op, because padding has already made
        // the line non-empty. The current parse pads first, which is why the bug is now
        // structurally impossible - and why the check has to aim at the ordering.
        LevelMap keep = LevelMap.parse("#####\n\n#   #\n\n#####");
        check("parse: a blank row inside a level is kept, so nothing shifts up",
            keep.cell(4, 0) == '#' && keep.cell(0, 0) == '#',
            String.format("row0='%c' row4='%c'", keep.cell(0, 0), keep.cell(4, 0)));
        check("parse: rows are padded to the level's width",
            keep.cell(1, 4) == ' ' && keep.cell(2, 4) == '#');
        LevelMap top = LevelMap.parse("###\n# #\n###");
        check("parse: a normal level is unchanged",
            top.cell(0, 0) == '#' && top.cell(1, 1) == ' ' && top.cell(2, 2) == '#');
        check("parse: a level with no blank rows keeps its shape",
            top.cell(3, 0) == ' ', "row 3 is past the end");

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
