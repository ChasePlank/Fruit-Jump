package tropical.engine;

/**
 * Visibility - line of sight and remembered terrain for the top-down dungeon.
 *
 * Answers three questions per tile, which is exactly what a 2.5D dungeon needs
 * to draw a room that lights up as you walk it:
 *
 *   isVisible(c, r)   can the eye see this tile right now
 *   lightAt(c, r)     how brightly, 1.0 at the eye falling to 0 at the light's edge
 *   isExplored(c, r)  has it ever been seen (terrain you remember in the dark)
 *
 * Algorithm: recursive shadowcasting, eight octants. It is the right tool here
 * because it produces true geometric shadows - a torch behind a pillar casts a
 * wedge, not a soft blob - and it costs one pass per octant rather than one ray
 * per tile. The alternative (cast N rays, mark whatever they hit) is simpler to
 * write and worse to look at: it leaves gaps between rays that read as holes in
 * the dark, and its cost scales with how smooth you want it.
 *
 * Deliberately not included: a smoothing pass, coloured light, or per-light
 * intensity. One eye, one radius, one falloff curve - the shape most dungeons
 * need. Multiple lights would mean accumulating into a second buffer.
 */
public class Visibility {

    /** How brightly terrain you remember but cannot see renders. */
    public static final double MEMORY_LIGHT = 0.22;

    /**
     * Floor for a tile the eye CAN see. Without it the falloff reaches exactly 0
     * at the rim, so a visible tile and an unexplored one both draw as pure
     * black - the light radius would end in a ring of tiles that are lit in the
     * data and invisible on screen. Found by asking what the rim tile's
     * displayLight was, not by looking at it.
     */
    public static final double MIN_LIGHT = 0.08;

    private final int cols, rows;
    private final boolean[] walls;
    private final boolean[] visible;
    private final boolean[] explored;
    private final double[] light;

    private int eyeC = -1, eyeR = -1;

    public Visibility(int cols, int rows) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        this.walls = new boolean[this.cols * this.rows];
        this.visible = new boolean[this.cols * this.rows];
        this.explored = new boolean[this.cols * this.rows];
        this.light = new double[this.cols * this.rows];
    }

    public int cols() { return cols; }
    public int rows() { return rows; }

    public boolean inBounds(int c, int r) {
        return c >= 0 && c < cols && r >= 0 && r < rows;
    }

    public void setWall(int c, int r, boolean wall) {
        if (inBounds(c, r)) walls[r * cols + c] = wall;
    }

    /** Out of bounds is not a wall: the map edge should not cast a shadow. */
    public boolean isWall(int c, int r) {
        return inBounds(c, r) && walls[r * cols + c];
    }

    public void wallRow(int r, String pattern) {
        for (int c = 0; c < cols && c < pattern.length(); c++) {
            setWall(c, r, pattern.charAt(c) == '#');
        }
    }

    /**
     * Recompute what the eye sees. Radius is in tiles.
     *
     * Everything visible this pass is also marked explored, and exploration is
     * never cleared by a move - only by {@link #forget()}. That asymmetry is the
     * whole point: sight is transient, memory is not.
     */
    public void compute(int eyeC, int eyeR, double radiusTiles) {
        java.util.Arrays.fill(visible, false);
        java.util.Arrays.fill(light, 0.0);
        this.eyeC = eyeC;
        this.eyeR = eyeR;

        if (!inBounds(eyeC, eyeR) || radiusTiles <= 0) {
            if (inBounds(eyeC, eyeR)) {
                visible[eyeR * cols + eyeC] = true;
                explored[eyeR * cols + eyeC] = true;
                light[eyeR * cols + eyeC] = 1.0;
            }
            return;
        }

        visible[eyeR * cols + eyeC] = true;
        explored[eyeR * cols + eyeC] = true;
        light[eyeR * cols + eyeC] = 1.0;

        cast(eyeC, eyeR, radiusTiles, 1, 1.0, 0.0,  1,  0,  0,  1);
        cast(eyeC, eyeR, radiusTiles, 1, 1.0, 0.0,  0,  1,  1,  0);
        cast(eyeC, eyeR, radiusTiles, 1, 1.0, 0.0,  0, -1,  1,  0);
        cast(eyeC, eyeR, radiusTiles, 1, 1.0, 0.0, -1,  0,  0,  1);
        cast(eyeC, eyeR, radiusTiles, 1, 1.0, 0.0, -1,  0,  0, -1);
        cast(eyeC, eyeR, radiusTiles, 1, 1.0, 0.0,  0, -1, -1,  0);
        cast(eyeC, eyeR, radiusTiles, 1, 1.0, 0.0,  0,  1, -1,  0);
        cast(eyeC, eyeR, radiusTiles, 1, 1.0, 0.0,  1,  0,  0, -1);
    }

    /**
     * One octant of the shadowcast. Slopes are in tile-corner space; xx/xy/yx/yy
     * transform this octant's coordinates back to map space.
     */
    private void cast(int ox, int oy, double radius, int row, double start, double end,
                      int xx, int xy, int yx, int yy) {
        if (start < end) return;
        double newStart = start;
        int maxRow = (int) Math.ceil(radius);

        for (int i = row; i <= maxRow; i++) {
            boolean blocked = false;
            for (int dx = -i, dy = -i; dx <= 0; dx++) {
                double lSlope = (dx - 0.5) / (dy + 0.5);
                double rSlope = (dx + 0.5) / (dy - 0.5);
                if (start < rSlope) continue;
                if (end > lSlope) break;

                int X = ox + dx * xx + dy * xy;
                int Y = oy + dx * yx + dy * yy;
                double dist2 = (double) dx * dx + (double) dy * dy;

                if (dist2 <= radius * radius && inBounds(X, Y)) {
                    int idx = Y * cols + X;
                    visible[idx] = true;
                    explored[idx] = true;
                    light[idx] = MIN_LIGHT + (1 - MIN_LIGHT) * falloff(Math.sqrt(dist2), radius);
                }

                if (blocked) {
                    if (isWall(X, Y)) {
                        newStart = rSlope;
                        continue;
                    }
                    blocked = false;
                    start = newStart;
                } else if (isWall(X, Y) && i < radius) {
                    blocked = true;
                    cast(ox, oy, radius, i + 1, start, lSlope, xx, xy, yx, yy);
                    newStart = rSlope;
                }
            }
            if (blocked) break;
        }
    }

    /** Torch curve: full at the eye, gone at the rim, smooth in between. */
    public static double falloff(double distance, double radius) {
        if (radius <= 0) return distance == 0 ? 1.0 : 0.0;
        double t = 1.0 - distance / radius;
        if (t <= 0) return 0.0;
        return t * t * (3 - 2 * t);          // smoothstep, no hard band at the rim
    }

    public boolean isVisible(int c, int r) {
        return inBounds(c, r) && visible[r * cols + c];
    }

    public boolean isExplored(int c, int r) {
        return inBounds(c, r) && explored[r * cols + c];
    }

    /** Brightness of a visible tile: 0 when it cannot be seen. */
    public double lightAt(int c, int r) {
        return inBounds(c, r) ? light[r * cols + c] : 0.0;
    }

    /**
     * What the renderer should draw with: the real light where the eye has line
     * of sight, MEMORY_LIGHT where it is only remembered, 0 for the unknown.
     */
    public double displayLight(int c, int r) {
        if (!inBounds(c, r)) return 0.0;
        int idx = r * cols + c;
        if (visible[idx]) return light[idx];
        if (explored[idx]) return MEMORY_LIGHT;
        return 0.0;
    }

    public int visibleCount() {
        int n = 0;
        for (boolean b : visible) if (b) n++;
        return n;
    }

    public int exploredCount() {
        int n = 0;
        for (boolean b : explored) if (b) n++;
        return n;
    }

    public double exploredFraction() {
        return (double) exploredCount() / (cols * rows);
    }

    /** Forget the map: the unexplored-dungeon state. */
    public void forget() {
        java.util.Arrays.fill(explored, false);
        java.util.Arrays.fill(visible, false);
        java.util.Arrays.fill(light, 0.0);
        eyeC = eyeR = -1;
    }

    /** Eye tile of the last compute, or (-1,-1) before the first one. */
    public int eyeCol() { return eyeC; }
    public int eyeRow() { return eyeR; }

    /**
     * Plain Bresenham line of sight, exposed for systems that want a one-off
     * answer (an enemy deciding whether it can see the player) without running
     * a whole shadowcast. A shadowcast is for LIGHTING; this is for questions.
     */
    public boolean lineOfSight(int c0, int r0, int c1, int r1) {
        int dx = Math.abs(c1 - c0), dy = Math.abs(r1 - r0);
        int sx = c0 < c1 ? 1 : -1, sy = r0 < r1 ? 1 : -1;
        int err = dx - dy;
        int c = c0, r = r0;
        while (true) {
            if (!(c == c0 && r == r0) && isWall(c, r)) return false;
            if (c == c1 && r == r1) return true;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; c += sx; }
            if (e2 < dx) { err += dx; r += sy; }
        }
    }
}
