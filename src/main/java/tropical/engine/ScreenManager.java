package tropical.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages multiple rooms and handles transitions between them.
 * 
 * Zelda-style: when player crosses a boundary, load the adjacent room
 * and place player at the opposite edge.
 */
public class ScreenManager {
    final Map<String, Room> rooms = new HashMap<>();
    Room currentRoom;
    Physics.Body player;
    
    // Transition state
    boolean transitioning = false;
    double transitionTimer = 0;
    String nextRoomId = null;
    int entryEdge = 0; // 0=north, 1=south, 2=east, 3=west
    
    static final double TRANSITION_TIME = 0.3; // seconds
    
    public ScreenManager(Physics.Body player) {
        this.player = player;
    }
    
    /** Add a room to the world. */
    public ScreenManager add(Room room) {
        rooms.put(room.id, room);
        return this;
    }
    
    /** Start in a specific room. */
    public ScreenManager start(String roomId) {
        currentRoom = rooms.get(roomId);
        return this;
    }
    
    /** Update: check for edge crossings and handle transitions. */
    public void update(double dt, World world) {
        if (transitioning) {
            // Wait for transition
            transitionTimer -= dt;
            if (transitionTimer <= 0) {
                completeTransition(world);
            }
            return;
        }
        
        // Check if player crossed a boundary
        double margin = 20; // pixels from edge
        
        // North edge (Y < margin)
        if (player.y < margin && currentRoom.north != null) {
            triggerTransition(currentRoom.north, 1, world); // enter from south
        }
        // South edge (Y > height - margin)
        else if (player.y > currentRoom.height - margin && currentRoom.south != null) {
            triggerTransition(currentRoom.south, 0, world); // enter from north
        }
        // East edge (X > width - margin)
        else if (player.x > currentRoom.width - margin && currentRoom.east != null) {
            triggerTransition(currentRoom.east, 3, world); // enter from west
        }
        // West edge (X < margin)
        else if (player.x < margin && currentRoom.west != null) {
            triggerTransition(currentRoom.west, 2, world); // enter from east
        }
    }
    
    void triggerTransition(String roomId, int entryEdge, World world) {
        transitioning = true;
        transitionTimer = TRANSITION_TIME;
        nextRoomId = roomId;
        this.entryEdge = entryEdge;
    }
    
    void completeTransition(World world) {
        transitioning = false;
        
        // Load new room
        Room newRoom = rooms.get(nextRoomId);
        if (newRoom == null) {
            System.out.printf("    [ERROR] Room %s not found!%n", nextRoomId);
            return;
        }
        
        // Clear world geometry
        world.tiles.clear();
        world.oneways.clear();
        
        // Add new room's tiles
        world.tiles.addAll(newRoom.tiles);
        world.oneways.addAll(newRoom.oneways);
        
        // Reposition player at entry edge
        double margin = 50; // pixels from edge
        switch (entryEdge) {
            case 0: // Enter from north
                player.x = newRoom.width / 2;
                player.y = margin;
                player.vy = 0;
                break;
            case 1: // Enter from south
                player.x = newRoom.width / 2;
                player.y = newRoom.height - margin - player.hh;
                player.vy = 0;
                break;
            case 2: // Enter from east
                player.x = newRoom.width - margin - player.hw;
                player.y = newRoom.height / 2;
                player.vx = 0;
                break;
            case 3: // Enter from west
                player.x = margin + player.hw;
                player.y = newRoom.height / 2;
                player.vx = 0;
                break;
        }
        
        // Reset grounded state
        player.grounded = false;
        
        // Update current room
        currentRoom = newRoom;
    }
    
    public String currentRoomId() {
        return currentRoom != null ? currentRoom.id : "none";
    }
}
