package tropical.engine;

import java.util.List;

/**
 * RoomNav - the bridge between a room's pixel-space geometry and the tile-space
 * navigation systems (Visibility for eyes, PathGrid for legs).
 *
 * The top-down rooms are 800x480 screens described as a handful of big AABBs. The
 * navigation systems work on tiles and want to answer questions per tile, so this
 * is the adapter: one mask, built from whatever is solid RIGHT NOW, pushed into
 * both systems, plus the coordinate conversions neither of them should know about.
 *
 * Rebuilt rather than incrementally patched, and rebuilding is the point: a door
 * that unlocks is a wall that stops being one, which changes both what an enemy
 * can walk and what the player can see through. One call after the change keeps
 * the two views of the room from disagreeing.
 *
 * GRANULARITY: a tile is solid if any solid AABB overlaps it, so a wall thinner
 * than TILE blocks a whole tile. That is deliberate - it errs toward blocking,
 * which is safe for navigation - but it does mean a 16px wall narrows a corridor
 * by a tile. Drop TILE to 16 for finer rooms at 4x the tiles.
 */
public class RoomNav {
    public static final int TILE = 32;

    public final int cols, rows;
    public final PathGrid grid;
    public final Visibility eye;
    private final boolean[] solid;

    public RoomNav(int roomWidth, int roomHeight) {
        cols = Math.max(1, (roomWidth + TILE - 1) / TILE);
        rows = Math.max(1, (roomHeight + TILE - 1) / TILE);
        grid = new PathGrid(cols, rows);
        eye = new Visibility(cols, rows);
        solid = new boolean[cols * rows];
    }

    /** Rebuild the mask from the live solid geometry (walls plus locked doors). */
    public void rebuild(List<Physics.AABB> solids) {
        java.util.Arrays.fill(solid, false);
        for (Physics.AABB a : solids) mark(a);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                boolean b = solid[r * cols + c];
                grid.setBlocked(c, r, b);
                eye.setWall(c, r, b);
            }
        }
    }

    private void mark(Physics.AABB a) {
        int c0 = colOf(a.x0), c1 = colOf(Math.nextDown(a.x1));
        int r0 = rowOf(a.y0), r1 = rowOf(Math.nextDown(a.y1));
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) solid[r * cols + c] = true;
        }
    }

    // --- world <-> tile, clamped so a body outside the room still has a tile ---

    public int colOf(double x) { return clamp((int) Math.floor(x / TILE), cols); }
    public int rowOf(double y) { return clamp((int) Math.floor(y / TILE), rows); }
    private static int clamp(int v, int n) { return v < 0 ? 0 : (v >= n ? n - 1 : v); }

    public double centreX(int c) { return c * (double) TILE + TILE / 2.0; }
    public double centreY(int r) { return r * (double) TILE + TILE / 2.0; }

    public boolean blockedAt(double x, double y) {
        return solid[rowOf(y) * cols + colOf(x)];
    }

    /** What the renderer should draw at a world position, 0 = unknown. */
    public double lightAt(double x, double y) {
        return eye.displayLight(colOf(x), rowOf(y));
    }
}
