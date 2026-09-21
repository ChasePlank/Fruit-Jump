package tropical.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A grid-of-rooms world generator: the Zelda-style screen-transition
 * structure Tropical Punch is designed around.
 *
 * A level is an N×M grid of rooms. Each room is ONE screen (no scrolling
 * within a room — the camera is fixed per room; transitions happen at
 * edges). A guaranteed path runs from the start room to the exit room.
 * Some transitions are gated by locked doors (key placed in an earlier
 * room on the path).
 *
 * Room layout: a bordered screen with doorway gaps on edges that have
 * connections, interior platforms for verticality, hazards/enemies/pickups
 * per room.
 */
public class RoomWorld {
    public static final int ROOM_W = 800, ROOM_H = 480;

    public final int cols, rows;
    public final Room[][] grid;
    public String startRoomId, exitRoomId;
    public final List<String> mainPath = new ArrayList<>();  // room IDs start→exit
    final Random rng;

    public RoomWorld(int cols, int rows, long seed) {
        this.cols = cols;
        this.rows = rows;
        this.grid = new Room[rows][cols];
        this.rng = new Random(seed);
        generate();
    }

    void generate() {
        // --- Carve a guaranteed path: random walk from (0, midRow) to
        // (cols-1, someRow), moving mostly east ---
        int r = rows / 2, c = 0;
        List<int[]> path = new ArrayList<>();
        path.add(new int[]{r, c});
        while (c < cols - 1) {
            // mostly east, occasionally up/down
            int roll = rng.nextInt(10);
            if (roll < 7) c++;
            else if (roll < 9 && r > 0 && !onPath(path, r - 1, c)) r--;
            else if (r < rows - 1 && !onPath(path, r + 1, c)) r++;
            else c++;
            path.add(new int[]{r, c});
        }
        startRoomId = path.get(0)[0] + "," + path.get(0)[1];
        exitRoomId = path.get(path.size() - 1)[0] + "," + path.get(path.size() - 1)[1];
        for (int[] p : path) mainPath.add(p[0] + "," + p[1]);

        // --- Build every grid cell as a room; connect path-adjacent cells ---
        for (int rr = 0; rr < rows; rr++) {
            for (int cc = 0; cc < cols; cc++) {
                grid[rr][cc] = buildRoom(rr, cc, path);
            }
        }
        // Connections (bidirectional between path-adjacent rooms)
        for (int i = 0; i < path.size() - 1; i++) {
            int[] a = path.get(i), b = path.get(i + 1);
            Room ra = grid[a[0]][a[1]], rb = grid[b[0]][b[1]];
            if (b[1] > a[1]) { ra.connectEast(b[0] + "," + b[1]); rb.connectWest(a[0] + "," + a[1]); }
            else if (b[1] < a[1]) { ra.connectWest(b[0] + "," + b[1]); rb.connectEast(a[0] + "," + a[1]); }
            else if (b[0] > a[0]) { ra.connectSouth(b[0] + "," + b[1]); rb.connectNorth(a[0] + "," + a[1]); }
            else { ra.connectNorth(b[0] + "," + b[1]); rb.connectSouth(a[0] + "," + a[1]); }
        }
    }

    boolean onPath(List<int[]> path, int r, int c) {
        for (int[] p : path) if (p[0] == r && p[1] == c) return true;
        return false;
    }

    /** Build one room's geometry. Connections get doorway gaps in walls. */
    Room buildRoom(int rr, int cc, List<int[]> path) {
        Room room = new Room(rr + "," + cc, ROOM_W, ROOM_H);
        int wall = 32;
        int doorGap = 96;  // doorway gap height/width (pixels)

        // Border walls with doorway gaps where connections exist.
        // Connections are determined AFTER all rooms built, so build all
        // walls closed here EXCEPT the edges that are on the path —
        // determined by checking the path for neighbors.
        boolean openN = hasPathNeighbor(path, rr - 1, cc);
        boolean openS = hasPathNeighbor(path, rr + 1, cc);
        boolean openE = hasPathNeighbor(path, rr, cc + 1);
        boolean openW = hasPathNeighbor(path, rr, cc - 1);

        // Floor (with pit gaps sometimes for jump variety — keep the
        // walking line continuous: a pit is a 64-96px gap, jumpable)
        int floorY = ROOM_H - wall;
        if (rng.nextDouble() < 0.4 && ROOM_W > 400) {
            int pitX = 200 + rng.nextInt(ROOM_W - 400);
            int pitW = 64 + rng.nextInt(33);
            room.tile(0, floorY, pitX, ROOM_H);
            room.tile(pitX + pitW, floorY, ROOM_W, ROOM_H);
        } else {
            room.tile(0, floorY, ROOM_W, ROOM_H);
        }

        // Left wall (west): gap for openW at mid-height
        if (openW) {
            int gapY = floorY - doorGap;  // doorway at walking height
            room.tile(0, 0, wall, gapY);
            room.tile(0, gapY + doorGap, wall, ROOM_H);
        } else {
            room.tile(0, 0, wall, ROOM_H);
        }
        // Right wall (east)
        if (openE) {
            int gapY = floorY - doorGap;
            room.tile(ROOM_W - wall, 0, ROOM_W, gapY);
            room.tile(ROOM_W - wall, gapY + doorGap, ROOM_W, ROOM_H);
        } else {
            room.tile(ROOM_W - wall, 0, ROOM_W, ROOM_H);
        }
        // Top wall (north): gap for openN at mid-width
        if (openN) {
            int gapX = ROOM_W / 2 - doorGap / 2;
            room.tile(0, 0, gapX, wall);
            room.tile(gapX + doorGap, 0, ROOM_W, wall);
            // a ledge to reach the doorway
            room.tile(gapX - 64, wall + 96, gapX + doorGap + 64, wall + 96 + 16);
        } else {
            room.tile(0, 0, ROOM_W, wall);
        }
        // Bottom wall (south): gap for openS
        if (openS) {
            int gapX = ROOM_W / 2 - doorGap / 2;
            room.tile(0, floorY, gapX, ROOM_H);
            room.tile(gapX + doorGap, floorY, ROOM_W, ROOM_H);
        }
        // (floor already handles the closed-south case)

        // Interior platforms for verticality (1-2 floating platforms)
        int plats = 1 + rng.nextInt(2);
        for (int i = 0; i < plats; i++) {
            int pw = 96 + rng.nextInt(96);
            int px = wall + 48 + rng.nextInt(ROOM_W - 2 * wall - 96 - pw);
            int py = floorY - 96 - rng.nextInt(128);
            room.oneway(px, py, px + pw, py + 16);
        }

        return room;
    }

    boolean hasPathNeighbor(List<int[]> path, int rr, int cc) {
        return onPath(path, rr, cc);
    }
}
