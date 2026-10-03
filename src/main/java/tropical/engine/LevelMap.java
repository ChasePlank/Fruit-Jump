package tropical.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * LevelMap — ASCII text grid → World geometry.
 *
 * The data-driven level format: levels are readable, editable text.
 * Characters:
 *   #  solid tile
 *   /  slope rising to the right (45°)
 *   \  slope falling to the right (45°)
 *   =  one-way platform (jump through from below)
 *   ^  spikes (contact damage zone)
 *   P  player spawn
 *   E  exit
 *   o  enemy spawn
 *   k  key pickup
 *   h  heart pickup
 *   D  locked door
 *   (space)  empty
 *
 * Grid is 32px per cell. Rows are listed top to bottom.
 */
public class LevelMap {
    static final int TILE = 32;

    final List<String> rows;
    final int width, height;   // in cells
    public final double spawnX, spawnY;
    public final double exitX, exitY;

    final List<Physics.AABB> solidTiles = new ArrayList<>();
    final List<Physics.AABB> onewayTiles = new ArrayList<>();
    final List<Slope> slopes = new ArrayList<>();
    final List<Physics.AABB> spikes = new ArrayList<>();
    public final List<Physics.AABB> cracked = new ArrayList<>();  // bombable
    public final List<double[]> enemies = new ArrayList<>();   // {x, y}
    public final List<double[]> bats = new ArrayList<>();      // {x, y}
    public final List<double[]> piranhas = new ArrayList<>();  // {x, y}, always in groups
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Door> doors = new ArrayList<>();
    /** Water cells as {row, col, dir}: dir 0 still, 1 right, 2 left, 3 down, 4 up. Water is not solid and
     *  never enters collision - WaterSystem applies it as forces. Ported from the Fruit-Jump release. */
    final List<int[]> waterCells = new ArrayList<>();

    LevelMap(List<String> rows) {
        this.rows = rows;
        this.height = rows.size();
        this.width = rows.stream().mapToInt(String::length).max().orElse(0);

        double sx = 0, sy = 0, ex = 0, ey = 0;

        for (int r = 0; r < height; r++) {
            String row = rows.get(r);
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                double x = c * TILE, y = r * TILE;

                switch (ch) {
                    case '~': waterCells.add(new int[]{r, c, 0}); break;
                    case '>': waterCells.add(new int[]{r, c, 1}); break;
                    case '<': waterCells.add(new int[]{r, c, 2}); break;
                    case 'V': waterCells.add(new int[]{r, c, 3}); break;
                    case 'A': waterCells.add(new int[]{r, c, 4}); break;
                    case '#':
                        solidTiles.add(new Physics.AABB(x, y, x + TILE, y + TILE));
                        break;
                    case '=':
                        onewayTiles.add(new Physics.AABB(x, y, x + TILE, y + 4));
                        break;
                    case '/':
                        // rising to the right: surface from bottom-left to top-right
                        slopes.add(new Slope(x, y + TILE, x + TILE, y));
                        break;
                    case '\\':
                        // falling to the right: top-left to bottom-right
                        slopes.add(new Slope(x, y, x + TILE, y + TILE));
                        break;
                    case '^':
                        // spikes sit on the floor below them; damage zone is
                        // the lower half of the cell
                        spikes.add(new Physics.AABB(x, y + TILE / 2, x + TILE, y + TILE));
                        break;
                    case 'P':
                        sx = x + TILE / 2; sy = y + TILE / 2;
                        break;
                    case 'E':
                        ex = x + TILE / 2; ey = y + TILE / 2;
                        break;
                    case 'o':
                        // spider
                        enemies.add(new double[]{x + TILE / 2, y + TILE / 2,
                                                 Enemy.KIND_SPIDER});
                        break;
                    case 's':
                        // snake
                        enemies.add(new double[]{x + TILE / 2, y + TILE / 2,
                                                 Enemy.KIND_SNAKE});
                        break;
                    case 'b':
                        // Flying enemy anchor. Bats are free to engage over
                        // gaps - gaps are climbable spike pits, so a mid-air
                        // knockover is a setback, not a death.
                        bats.add(new double[]{x + TILE / 2, y + TILE / 2});
                        break;
                    case 'f':
                        // Piranha anchor. Placed in GROUPS by the generator, because one fish is a nuisance and
                        // four are a pool you have to think about crossing.
                        piranhas.add(new double[]{x + TILE / 2, y + TILE / 2});
                        break;
                    case 'k':
                        pickups.add(Pickup.key(x + TILE / 2, y + TILE / 2));
                        break;
                    case 'h':
                        pickups.add(Pickup.heart(x + TILE / 2, y + TILE / 2));
                        break;
                    case 'j':
                        // Safe-room reward: raises the lives cap and refills.
                        pickups.add(Pickup.jar(x + TILE / 2, y + TILE / 2));
                        break;
                    case 'D':
                        // Door: VISIBLE sprite is 2 tiles (64px) sitting
                        // on the floor top (fr). The collision AABB is
                        // taller — 3 cells of invisible wall above the
                        // door (top at fr-3, bottom at fr) so it can't be
                        // jumped over (apex ~73px). Bug history: first
                        // box ran to y+2*TILE (dipped into floor, sprite
                        // stretched down); the "fix" set bottom to y
                        // (top of the 'D' cell) — one tile ABOVE the
                        // floor, leaving a 32px gap enemies walked
                        // through (playtest round 3).
                        {
                            Door d = new Door(x, y - TILE * 2, x + TILE, y + TILE);
                            d.visibleH = TILE * 2;
                            doors.add(d);
                        }
                        break;
                    case 'C':
                        // Cracked tile: solid until bombed. ONE AABB
                        // object in both lists — the explosion removes
                        // it from tiles by identity (AABB has no
                        // equals), so two same-coord objects would
                        // leave the solid twin behind (found by test).
                        {
                            Physics.AABB box = new Physics.AABB(x, y, x + TILE, y + TILE);
                            solidTiles.add(box);
                            cracked.add(box);
                        }
                        break;
                }
            }
        }

        this.spawnX = sx; this.spawnY = sy;
        this.exitX = ex; this.exitY = ey;
    }

    /** Parse from a text block (lines split on \n). Public so hand-authored
     *  levels (the tutorial) can build a map without a generator. */
    public static LevelMap parse(String text) {
        // Ported from the release, where this was fixed on Sept 27 and never came back.
        //
        // The old version dropped empty lines (`if (!line.isEmpty())`), so a blank row anywhere in a
        // level shifted everything below it up by one. It also left a trailing '\r' on every row from
        // a CRLF file, which becomes a cell character and makes the row one wider than it is, and it
        // did not pad short rows.
        //
        // None of that is live today - Tutorial.map pads every row with spaces, so its rows are never
        // empty - but `parse` is public and documented as the way to hand-author a level, and a
        // hand-authored level is exactly where a blank row or a CRLF shows up. Fixing it costs
        // nothing and removes a trap from the one entry point a person is meant to use.
        String[] raw = text.split("\n", -1);
        // A trailing newline is a line terminator, not a line. `split(..., -1)` keeps the empty string after
        // it, and the padding below then turns it into a real row - so every level assembled by joining rows
        // with '\n' came out ONE ROW TALLER than it is. Tutorial.map builds a 20-row level and the map
        // reported 21. Interior blank rows are still kept; only the last one is dropped, and only when it is
        // empty, which is exactly the case a file ending in a newline produces.
        if (raw.length > 1 && raw[raw.length - 1].isEmpty()) {
            raw = java.util.Arrays.copyOf(raw, raw.length - 1);
        }
        for (int i = 0; i < raw.length; i++) {
            if (raw[i].endsWith("\r")) raw[i] = raw[i].substring(0, raw[i].length() - 1);
        }
        int width = 0;
        for (String line : raw) width = Math.max(width, line.length());
        List<String> rows = new ArrayList<>();
        for (String line : raw) {
            if (line.length() < width) line = line + " ".repeat(width - line.length());
            rows.add(line);
        }
        return new LevelMap(rows);
    }

    /** Build the world: geometry into the given World. */
    public void buildWorld(World world) {
        // Tell the world how big the level is, so projectiles cull against the
        // real extent instead of a constant that goes stale.
        world.setBounds(width * (double) TILE, height * (double) TILE);
        for (Physics.AABB t : solidTiles) world.tiles.add(t);
        for (Physics.AABB t : onewayTiles) world.oneways.add(t);
        for (Slope s : slopes) world.slopes.add(s);
        for (Physics.AABB sp : spikes) world.spikes.add(sp);
        for (Physics.AABB c : cracked) world.cracked.add(c);

        // Water: not solid and never in collision - WaterSystem applies it as forces. Set after the geometry so a
        // level with no water pays nothing, since the system is a no-op with no field.
        Water w = buildWater();
        if (!w.isEmpty()) world.setWater(w);
        for (Pickup p : pickups) world.addPickup(p);
        for (int i = 0; i < bats.size(); i++) {
            double[] b = bats.get(i);
            world.addBat(new Bat(b[0], b[1], (long) (b[0] * 31 + b[1] * 17 + i)));
        }
        for (int i = 0; i < piranhas.size(); i++) {
            double[] f = piranhas.get(i);
            world.addPiranha(new Piranha(f[0], f[1], (long) (f[0] * 13 + f[1] * 29 + i)));
        }
        for (Door d : doors) {
            world.doors.add(d);
            world.tiles.add(d.aabb());
        }
    }

    public static final double CURRENT_SPEED = 85.0;
    public static final double CURRENT_UP_SPEED = 70.0;

    public Water buildWater() {
        Water w = new Water(width, height);
        for (int[] wc : waterCells) w.set(wc[0], wc[1]);
        if (w.isEmpty()) return w;
        w.buildRects();
        for (int[] wc : waterCells) {
            int dir = wc[2];
            if (dir == 0) continue;
            double x0 = wc[1] * (double) TILE, y0 = wc[0] * (double) TILE;
            double vx = 0, vy = 0;
            switch (dir) {
                case 1: vx = CURRENT_SPEED; break;
                case 2: vx = -CURRENT_SPEED; break;
                case 3: vy = CURRENT_SPEED; break;
                case 4: vy = -CURRENT_UP_SPEED; break;
            }
            w.addCurrent(x0, y0, x0 + TILE, y0 + TILE, vx, vy);
        }
        return w;
    }

    /** The character at a cell, or ' ' if out of bounds. */
    public char cell(int r, int c) {
        if (r < 0 || r >= height || c < 0) return ' ';
        String row = rows.get(r);
        return (c < row.length()) ? row.charAt(c) : ' ';
    }

    /** Grid height in cells (the view needs it to size the letterbox). */
    public int heightCells() {
        return height;
    }

    /** Grid width in cells. */
    public int widthCells() {
        return width;
    }
}
