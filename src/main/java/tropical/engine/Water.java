package tropical.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Water - a level's water field: geometry and queries only, no per-body state.
 *
 * Water is never added to collision, so it can never fight the swept AABB
 * pass. Everything water does to a body is a FORCE, applied before movement
 * by {@link WaterSystem}. That separation is deliberate: a body resting in
 * water generates no collision (tEntry=0 is rejected by the sweep), so any
 * water behavior routed through collision-response would silently never fire
 * for a floating body - the same trap the moving-platform carry fell into.
 *
 * The field is a boolean mask over the level's tile grid rather than a list
 * of rectangles, which buys two things rects cannot give:
 *
 *   - a per-column surface: the top of the contiguous water run containing
 *     the query point, so two pools stacked in one column each report their
 *     own surface instead of both reporting the upper one;
 *   - a real depth per column, which is what separates WADING from SWIMMING.
 *     Depth is the honest signal: a one-tile puddle is shallow no matter how
 *     much of the body it covers, and a body standing on the floor of a
 *     three-tile pool is deep no matter where its feet are.
 *
 * Merged rectangles are still built ({@link #rects}) for rendering.
 */
public class Water {
    public static final int TILE = 32;

    final int cols, rows;
    final boolean[] mask;                 // row-major: mask[r * cols + c]

    /** Merged water rectangles {x0, y0, x1, y1} - rendering only. */
    public final List<double[]> rects = new ArrayList<>();

    /** Current cells as {x0, y0, x1, y1, vx, vy} - one per tile. */
    final List<double[]> currents = new ArrayList<>();

    public Water(int cols, int rows) {
        this.cols = Math.max(0, cols);
        this.rows = Math.max(0, rows);
        this.mask = new boolean[this.cols * this.rows];
    }

    void set(int r, int c) {
        if (r >= 0 && r < rows && c >= 0 && c < cols) mask[r * cols + c] = true;
    }

    boolean isCell(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return false;
        return mask[r * cols + c];
    }

    public boolean isEmpty() {
        for (boolean b : mask) if (b) return false;
        return true;
    }

    public int columns() { return cols; }
    public int rowsCount() { return rows; }

    /** Water at a point? Boundary-inclusive on the far edges. */
    public boolean isWater(double x, double y) {
        int c = (int) Math.floor(x / TILE);
        int r = (int) Math.floor(y / TILE);
        return isCell(r, c);
    }

    /**
     * The water run containing (x, y): out[0] = surface Y (top of the run),
     * out[1] = run depth in px. False if the point is not in water.
     *
     * Note the run search goes UP and DOWN from the query row, so a point
     * below a floating pool reports the pool it is actually in.
     */
    private boolean runAt(double x, double y, double[] out) {
        int c = (int) Math.floor(x / TILE);
        int r = (int) Math.floor(y / TILE);
        if (!isCell(r, c)) return false;
        int top = r, bot = r;
        while (isCell(top - 1, c)) top--;
        while (isCell(bot + 1, c)) bot++;
        out[0] = top * (double) TILE;
        out[1] = (bot - top + 1) * (double) TILE;
        return true;
    }

    /**
     * Surface Y for a probe column, or NaN if there is no water there.
     *
     * Probes the feet first, then the body center. Feet-first matters: a body
     * standing on the floor of a one-tile pool has its feet exactly on the
     * bottom edge of the water cell, which is the next row down - outside the
     * mask. Falling back to the center keeps that body correctly "in water"
     * instead of reading as dry on a boundary.
     */
    private double surfaceFor(double x, double feetY, double centerY, double[] scratch) {
        if (runAt(x, feetY, scratch)) return scratch[0];
        if (runAt(x, centerY, scratch)) return scratch[0];
        return Double.NaN;
    }

    /**
     * Submersion of a body: fraction of its height below the local surface,
     * averaged over three sample columns across its width.
     *
     * Divided by the NUMBER OF SAMPLES, not by the number of samples that hit
     * water. A body straddling a pool edge therefore reads as partly submerged
     * instead of fully - it is half in the water, so it should behave half in
     * the water.
     */
    public double submersion(Physics.Body b) {
        double bottom = b.y + b.hh;
        double h = b.hh * 2.0;
        if (h <= 0) return 0;
        double[] scratch = new double[2];
        double[] xs = { b.x - b.hw + 1, b.x, b.x + b.hw - 1 };
        double sum = 0;
        for (double x : xs) {
            double sy = surfaceFor(x, bottom, b.y, scratch);
            if (Double.isNaN(sy)) continue;
            double depth = bottom - sy;
            if (depth <= 0) continue;
            sum += Math.min(depth, h) / h;
        }
        return sum / xs.length;
    }

    /** Deepest water run under the body, in px. 0 if the body is dry. */
    public double depthUnder(Physics.Body b) {
        double bottom = b.y + b.hh;
        double[] scratch = new double[2];
        double[] xs = { b.x - b.hw + 1, b.x, b.x + b.hw - 1 };
        double max = 0;
        for (double x : xs) {
            if (runAt(x, bottom, scratch) || runAt(x, b.y, scratch)) {
                max = Math.max(max, scratch[1]);
            }
        }
        return max;
    }

    /** Surface Y under the body (for splashes and breath), or NaN. */
    public double surfaceUnder(Physics.Body b) {
        double[] scratch = new double[2];
        if (runAt(b.x, b.y + b.hh, scratch) || runAt(b.x, b.y, scratch)) return scratch[0];
        return Double.NaN;
    }

    /** Register a current region (drift velocity in px/s). */
    public void addCurrent(double x0, double y0, double x1, double y1, double vx, double vy) {
        currents.add(new double[]{x0, y0, x1, y1, vx, vy});
    }

    /**
     * Drift velocity at a point: the AVERAGE of the currents covering it, or
     * null when there is none.
     *
     * Average, not sum. Summing would make a ten-tile current region ten times
     * as strong as a one-tile region of the same speed, so the size of a river
     * would change how fast it flows - the flow speed should be a property of
     * the current, not of how many cells were painted.
     */
    public double[] currentAt(double x, double y) {
        double sumX = 0, sumY = 0;
        int n = 0;
        for (double[] c : currents) {
            if (x >= c[0] && x <= c[2] && y >= c[1] && y <= c[3]) {
                sumX += c[4];
                sumY += c[5];
                n++;
            }
        }
        if (n == 0) return null;
        return new double[]{ sumX / n, sumY / n };
    }

    /**
     * Build merged rectangles from the mask, for rendering.
     *
     * Row-runs extended downward while the run has the same start and end
     * column, so a rectangular pool becomes one rect. Irregular shorelines
     * degrade to thin horizontal strips, which is still correct - just more
     * rects.
     */
    void buildRects() {
        rects.clear();
        int[] open = new int[cols];
        java.util.Arrays.fill(open, -1);
        for (int r = 0; r < rows; r++) {
            int[] next = new int[cols];
            java.util.Arrays.fill(next, -1);
            int c = 0;
            while (c < cols) {
                if (!isCell(r, c)) { c++; continue; }
                int start = c;
                while (c < cols && isCell(r, c)) c++;
                int end = c;  // run is [start, end)
                int idx = open[start];
                boolean canExtend = idx >= 0;
                if (canExtend) {
                    for (int cc = start; cc < end; cc++) {
                        if (open[cc] != idx) { canExtend = false; break; }
                    }
                }
                if (canExtend) {
                    rects.get(idx)[3] = (r + 1) * (double) TILE;
                } else {
                    rects.add(new double[]{
                        start * (double) TILE, r * (double) TILE,
                        end * (double) TILE, (r + 1) * (double) TILE
                    });
                    idx = rects.size() - 1;
                }
                for (int cc = start; cc < end; cc++) next[cc] = idx;
            }
            open = next;
        }
    }
}
