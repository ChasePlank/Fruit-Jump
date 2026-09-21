package tropical.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * A single screen/room in a Zelda-style world.
 * 
 * Each room has its own tile layout and defines which rooms connect on each edge.
 * When the player walks off an edge, the adjacent room loads.
 */
public class Room {
    final String id;
    final int width, height;  // in pixels
    
    // Connections: null = blocked, otherwise room ID
    String north, south, east, west;
    
    final List<Physics.AABB> tiles = new ArrayList<>();
    final List<Physics.AABB> oneways = new ArrayList<>();
    
    public Room(String id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }
    
    /** Add a solid tile. */
    public Room tile(double x0, double y0, double x1, double y1) {
        tiles.add(new Physics.AABB(x0, y0, x1, y1));
        return this;
    }
    
    /** Add a one-way platform. */
    public Room oneway(double x0, double y0, double x1, double y1) {
        oneways.add(new Physics.AABB(x0, y0, x1, y1));
        return this;
    }
    
    /** Set adjacent room to the north. */
    public Room connectNorth(String roomId) { north = roomId; return this; }
    public Room connectSouth(String roomId) { south = roomId; return this; }
    public Room connectEast(String roomId) { east = roomId; return this; }
    public Room connectWest(String roomId) { west = roomId; return this; }

    public List<Physics.AABB> getTiles() { return tiles; }
    public List<Physics.AABB> getOneways() { return oneways; }
}
