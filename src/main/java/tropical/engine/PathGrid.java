package tropical.engine;

/**
 * PathGrid - navigation over a tile grid: A* for one-off routes, a Dijkstra flow
 * field for many agents heading to the same place.
 *
 * Both are here because they answer different questions and a dungeon needs
 * both:
 *
 *   findPath(c0,r0,c1,r1)   "what is the route from here to there" - one agent,
 *                           one destination, and it can be anywhere on the map.
 *   flowTo(c,r) + flowNext  "which way is the target from here" - a target every
 *                           agent shares. One search, then every agent's
 *                           per-frame answer is an array read. Twenty enemies
 *                           chasing the player is one flow field, not twenty
 *                           paths.
 *
 * Movement is 8-way with a corner rule: a diagonal step is only allowed when both
 * orthogonal neighbours are open, so nobody squeezes between two diagonal walls.
 * Step costs are the classic integers (10 straight, 14 diagonal) which keeps every
 * comparison exact and integer - no epsilon, no "close enough" ordering bugs in
 * the open list. A* and the flow field use the SAME costs, so their answers are
 * directly comparable, and PathGridTest holds them against each other.
 *
 * No allocation per query: the search state is preallocated and a generation
 * stamp marks which nodes are current, so a repeated query clears nothing.
 */
public class PathGrid {
    public static final int COST_STRAIGHT = 10;
    public static final int COST_DIAGONAL = 14;

    private final int cols, rows;
    private final boolean[] blocked;

    /** Diagonal movement allowed (with the corner rule). */
    public boolean diagonal = true;

    // --- A* scratch -------------------------------------------------------
    private final int[] gCost;
    private final int[] cameFrom;
    private final int[] visitGen;
    private int gen = 0;
    private final int[] heap;
    private int heapSize;
    private final int[] fCost;

    // --- flow field -------------------------------------------------------
    private final int[] flowCost;
    private final int[] flowNext;

    // 8-way offsets, diagonals last so the corner rule is a local test
    private static final int[] DC = { 1, -1, 0, 0,  1, 1, -1, -1 };
    private static final int[] DR = { 0, 0, 1, -1,  1, -1, 1, -1 };

    public PathGrid(int cols, int rows) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        int n = this.cols * this.rows;
        this.blocked = new boolean[n];
        this.gCost = new int[n];
        this.fCost = new int[n];
        this.cameFrom = new int[n];
        this.visitGen = new int[n];
        this.heap = new int[n + 1];
        this.flowCost = new int[n];
        this.flowNext = new int[n];
    }

    public int cols() { return cols; }
    public int rows() { return rows; }

    public boolean inBounds(int c, int r) {
        return c >= 0 && c < cols && r >= 0 && r < rows;
    }

    public int index(int c, int r) { return r * cols + c; }
    public int col(int idx) { return idx % cols; }
    public int row(int idx) { return idx / cols; }

    public void setBlocked(int c, int r, boolean b) {
        if (inBounds(c, r)) blocked[r * cols + c] = b;
    }

    public boolean isBlocked(int c, int r) {
        return !inBounds(c, r) || blocked[r * cols + c];
    }

    /** Fill a wall mask from an ASCII map, '#' blocked (same shape as Visibility). */
    public void fromAscii(String[] rowsText) {
        for (int r = 0; r < rows && r < rowsText.length; r++) {
            String line = rowsText[r];
            for (int c = 0; c < cols && c < line.length(); c++) {
                setBlocked(c, r, line.charAt(c) == '#');
            }
        }
    }

    /**
     * A route from (c0,r0) to (c1,r1) as packed tile indices, start first and goal
     * last, or null when there is no route. {@link #lastCost()} is its cost.
     */
    public int[] findPath(int c0, int r0, int c1, int r1) {
        lastCost = -1;
        if (!inBounds(c0, r0) || !inBounds(c1, r1)) return null;
        if (isBlocked(c0, r0) || isBlocked(c1, r1)) return null;
        int start = index(c0, r0), goal = index(c1, r1);

        gen++;
        heapSize = 0;
        put(start, 0, heuristic(c0, r0, c1, r1), -1);

        while (heapSize > 0) {
            int current = popMin();
            if (current == goal) {
                lastCost = gCost[current];
                return reconstruct(current);
            }
            int cc = col(current), cr = row(current);
            int g = gCost[current];
            for (int i = 0; i < (diagonal ? 8 : 4); i++) {
                int nc = cc + DC[i], nr = cr + DR[i];
                if (isBlocked(nc, nr)) continue;
                boolean diag = i >= 4;
                if (diag && !canCutCorner(cc, cr, nc, nr)) continue;
                int step = diag ? COST_DIAGONAL : COST_STRAIGHT;
                int ng = g + step;
                int ni = index(nc, nr);
                if (visitGen[ni] != gen || ng < gCost[ni]) {
                    visitGen[ni] = gen;
                    gCost[ni] = ng;
                    cameFrom[ni] = current;
                    put(ni, ng, ng + heuristic(nc, nr, c1, r1), current);
                }
            }
        }
        return null;                       // exhausted the reachable set
    }

    /** Diagonal steps must not squeeze between two blocked orthogonal neighbours. */
    private boolean canCutCorner(int c, int r, int nc, int nr) {
        return !isBlocked(nc, r) && !isBlocked(c, nr);
    }

    private static int heuristic(int c0, int r0, int c1, int r1) {
        int dx = Math.abs(c1 - c0), dy = Math.abs(r1 - r0);
        int min = Math.min(dx, dy), max = Math.max(dx, dy);
        return COST_DIAGONAL * min + COST_STRAIGHT * (max - min);
    }

    private int lastCost = -1;
    /** Cost of the path from the most recent findPath, or -1. */
    public int lastCost() { return lastCost; }

    private int[] reconstruct(int goal) {
        int n = 0;
        for (int i = goal; i != -1; i = cameFrom[i]) n++;
        int[] path = new int[n];
        int i = n - 1;
        for (int node = goal; node != -1; node = cameFrom[node]) path[i--] = node;
        return path;
    }

    // --- open list: a binary heap over packed f-cost and node index ---------

    private void put(int node, int g, int f, int from) {
        visitGen[node] = gen;
        gCost[node] = g;
        fCost[node] = f;
        cameFrom[node] = from;
        int i = ++heapSize;
        while (i > 1 && better(f, node, fCost[heap[i / 2]], heap[i / 2])) {
            heap[i] = heap[i / 2];
            i /= 2;
        }
        heap[i] = node;
    }

    private int popMin() {
        int top = heap[1];
        int last = heap[heapSize--];
        int i = 1;
        while (true) {
            int kid = i * 2;
            if (kid > heapSize) break;
            if (kid + 1 <= heapSize && better(fCost[heap[kid + 1]], heap[kid + 1],
                                              fCost[heap[kid]], heap[kid])) kid++;
            if (better(fCost[heap[kid]], heap[kid], fCost[last], last)) {
                heap[i] = heap[kid];
                i = kid;
            } else break;
        }
        heap[i] = last;
        return top;
    }

    /** Order by f, then by node index so ties are deterministic (not by chance). */
    private boolean better(int fA, int nodeA, int fB, int nodeB) {
        return fA < fB || (fA == fB && nodeA < nodeB);
    }

    // --- flow field --------------------------------------------------------

    /**
     * Build a field pointing at (c,r) from every reachable tile. Cost is the same
     * integer octile metric A* uses, so the two agree; {@link #flowNext} then
     * answers each agent's step in O(1).
     */
    public void flowTo(int c, int r) {
        java.util.Arrays.fill(flowCost, Integer.MAX_VALUE);
        java.util.Arrays.fill(flowNext, -1);
        if (!inBounds(c, r) || isBlocked(c, r)) return;

        int target = index(c, r);
        flowCost[target] = 0;
        flowNext[target] = -1;
        heapSize = 0;
        gen++;
        put(target, 0, 0, -1);

        while (heapSize > 0) {
            int current = popMin();
            int cc = col(current), cr = row(current);
            int g = gCost[current];
            for (int i = 0; i < (diagonal ? 8 : 4); i++) {
                int nc = cc + DC[i], nr = cr + DR[i];
                if (isBlocked(nc, nr)) continue;
                boolean diag = i >= 4;
                if (diag && !canCutCorner(cc, cr, nc, nr)) continue;
                int ng = g + (diag ? COST_DIAGONAL : COST_STRAIGHT);
                int ni = index(nc, nr);
                if (ng < flowCost[ni]) {
                    flowCost[ni] = ng;
                    flowNext[ni] = current;   // this neighbour steps TOWARD current
                    put(ni, ng, ng, -1);
                }
            }
        }
    }

    /** Steps of travel from (c,r) to the flow target, or -1 if unreachable. */
    public int flowCost(int c, int r) {
        if (!inBounds(c, r)) return -1;
        int v = flowCost[index(c, r)];
        return v == Integer.MAX_VALUE ? -1 : v;
    }

    /** The tile to move to next, packed, or -1 (at the target, or unreachable). */
    public int flowNext(int c, int r) {
        return inBounds(c, r) ? flowNext[index(c, r)] : -1;
    }

    /** The full route from (c,r) by walking the field; null if unreachable. */
    public int[] flowPath(int c, int r) {
        if (flowCost(c, r) < 0) return null;
        int n = 1;
        for (int i = flowNext(c, r); i != -1; i = flowNext[i]) n++;
        int[] path = new int[n];
        int i = 0;
        for (int node = index(c, r); node != -1; node = flowNext[node]) path[i++] = node;
        return path;
    }
}
