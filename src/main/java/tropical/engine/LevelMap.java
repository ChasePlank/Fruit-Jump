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
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Door> doors = new ArrayList<>();

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
                        enemies.add(new double[]{x + TILE / 2, y + TILE / 2});
                        break;
                    case 'k':
                        pickups.add(Pickup.key(x + TILE / 2, y + TILE / 2));
                        break;
                    case 'h':
                        pickups.add(Pickup.heart(x + TILE / 2, y + TILE / 2));
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

    /** Parse from a text block (lines split on \n). */
    static LevelMap parse(String text) {
        List<String> rows = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.isEmpty()) rows.add(line);
        }
        return new LevelMap(rows);
    }

    /** Build the world: geometry into the given World. */
    public void buildWorld(World world) {
        for (Physics.AABB t : solidTiles) world.tiles.add(t);
        for (Physics.AABB t : onewayTiles) world.oneways.add(t);
        for (Slope s : slopes) world.slopes.add(s);
        for (Physics.AABB sp : spikes) world.spikes.add(sp);
        for (Physics.AABB c : cracked) world.cracked.add(c);
        for (Pickup p : pickups) world.addPickup(p);
        for (Door d : doors) {
            world.doors.add(d);
            world.tiles.add(d.aabb());
        }
    }

    /** The character at a cell, or ' ' if out of bounds. */
    public char cell(int r, int c) {
        if (r < 0 || r >= height || c < 0) return ' ';
        String row = rows.get(r);
        return (c < row.length()) ? row.charAt(c) : ' ';
    }
}
