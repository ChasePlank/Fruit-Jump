package tropical.engine;

/**
 * Slope — a walkable inclined surface (height-function approach).
 *
 * The slope is a line segment from (x0,y0) to (x1,y1) — the walkable
 * surface, not a solid volume. Solid tiles underneath give the slope
 * its body; this class only answers one question: "how high is the
 * ground at x?"
 *
 * This sidesteps the hard problem of swept-AABB vs diagonal collision:
 * the AABB sweep handles all solid geometry, and the slope resolution
 * pass (in World) snaps feet to the surface height after the sweep.
 *
 * Steepness: |dy/dx| > STEEP_RATIO means the slope is too steep to
 * walk on — the body slides down instead of grounding.
 */
public class Slope {
    static final double STEEP_RATIO = 2.0;  // ~63° — steeper than this, you slide

    final double x0, y0, x1, y1;  // surface segment (left to right)
    final double ratio;           // dy/dx (negative = rising to the right in screen coords)
    final boolean steep;
    /** Downhill x direction: +1 means the surface descends to the right. */
    final int downhillX;

    Slope(double x0, double y0, double x1, double y1) {
        // Normalize: x0 is always the left end
        if (x1 < x0) {
            double tx = x0; x0 = x1; x1 = tx;
            double ty = y0; y0 = y1; y1 = ty;
        }
        this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        this.ratio = (x1 > x0) ? (y1 - y0) / (x1 - x0) : 0;
        this.steep = Math.abs(ratio) > STEEP_RATIO;
        this.downhillX = (y1 > y0) ? 1 : (y1 < y0) ? -1 : 0;
    }

    /** Does this slope's x-range cover the given x? */
    boolean containsX(double x) {
        return x >= x0 && x <= x1;
    }

    /** Surface height at the given x (screen coords: lower y = higher up). */
    double surfaceYAt(double x) {
        if (x <= x0) return y0;
        if (x >= x1) return y1;
        return y0 + (x - x0) * ratio;
    }

    /**
     * Surface height under a body spanning [footLeft, footRight].
     * Returns the HIGHEST surface point (lowest y) in the span — this
     * is what the feet must clear. Using the max across the span makes
     * flat-to-slope seams seamless: at the seam the flat tile and the
     * slope agree, and just past it the rising slope lifts the leading
     * foot first.
     */
    double surfaceYUnder(double footLeft, double footRight) {
        double yl = surfaceYAt(footLeft);
        double yr = surfaceYAt(footRight);
        return Math.min(yl, yr);  // min y = highest surface
    }
}
