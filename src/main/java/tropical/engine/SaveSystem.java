package tropical.engine;

import java.io.*;
import java.util.*;

/**
 * Save/Load system: persist and restore game state.
 * 
 * Serializes:
 *   - Player: position, HP, inventory (keys)
 *   - Room: cleared enemies, collected pickups, unlocked doors
 *   - Global: bosses defeated, play time
 * 
 * Format: simple text (key=value pairs), human-readable for debugging.
 * A real game would use JSON or binary, but this is clear and sufficient.
 */
public class SaveSystem {
    
    /** Game state snapshot for saving. */
    public static class GameState {
        // Player
        public double playerX, playerY;
        public double playerVX, playerVY;
        public int playerHP;
        public int keys;
        public int coins;
        /** Which game the save belongs to: "platformer" or "rooms".
         *  Continue branches on this — a rooms save used to dump the
         *  player into the platformer (playtest round 5). */
        public String mode = "platformer";
        /** Level number for checkpoint saves (deterministic seed).
         *  -1 = not a level checkpoint (mid-room saves from the engine's
         *  own save tests). */
        public int levelNum = -1;
        
        // Room state
        public String roomId = "start";
        public Set<Integer> clearedEnemies = new HashSet<>();
        public Set<Integer> collectedPickups = new HashSet<>();
        public Set<Integer> unlockedDoors = new HashSet<>();
        
        // Global progress
        public Set<String> bossesDefeated = new HashSet<>();
        public double playTime = 0;
        
        /** Create a snapshot from current world state. */
        public static GameState snapshot(
            Physics.Body player,
            Combat combat,
            PlayerInventory inv,
            World world,
            String roomId,
            double playTime
        ) {
            GameState state = new GameState();
            
            // Player
            state.playerX = player.x;
            state.playerY = player.y;
            state.playerVX = player.vx;
            state.playerVY = player.vy;
            state.playerHP = (int) combat.playerHP;
            state.keys = inv.keys;
            state.coins = inv.coins;
            
            // Room state
            state.roomId = roomId;
            for (Enemy e : world.enemies) {
                if (e.dead) state.clearedEnemies.add(e.id);
            }
            for (Pickup p : world.pickups) {
                if (!p.active) state.collectedPickups.add(p.id);
            }
            for (Door d : world.doors) {
                if (!d.locked) state.unlockedDoors.add(d.id);
            }
            
            // Global
            state.playTime = playTime;
            
            return state;
        }
    }
    
    /** Save game state to file. */
    public void save(GameState state, String filename) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(filename))) {
            out.println("# Save file");
            out.println();
            
            out.println("[player]");
            out.println("x=" + state.playerX);
            out.println("y=" + state.playerY);
            out.println("vx=" + state.playerVX);
            out.println("vy=" + state.playerVY);
            out.println("hp=" + state.playerHP);
            out.println("keys=" + state.keys);
            out.println("coins=" + state.coins);
            out.println("mode=" + state.mode);
            out.println("level=" + state.levelNum);
            out.println();
            
            out.println("[room]");
            out.println("id=" + state.roomId);
            out.println("cleared_enemies=" + joinSet(state.clearedEnemies));
            out.println("collected_pickups=" + joinSet(state.collectedPickups));
            out.println("unlocked_doors=" + joinSet(state.unlockedDoors));
            out.println();
            
            out.println("[global]");
            out.println("bosses_defeated=" + joinStringSet(state.bossesDefeated));
            out.println("play_time=" + state.playTime);
        }
    }
    
    /** Load game state from file. */
    public GameState load(String filename) throws IOException {
        GameState state = new GameState();
        
        try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
            String section = "";
            String line;
            
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1);
                    continue;
                }
                
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;
                
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                switch (section) {
                    case "player":
                        parsePlayer(state, key, value);
                        break;
                    case "room":
                        parseRoom(state, key, value);
                        break;
                    case "global":
                        parseGlobal(state, key, value);
                        break;
                }
            }
        }
        
        return state;
    }
    
    private void parsePlayer(GameState state, String key, String value) {
        switch (key) {
            case "x": state.playerX = Double.parseDouble(value); break;
            case "y": state.playerY = Double.parseDouble(value); break;
            case "vx": state.playerVX = Double.parseDouble(value); break;
            case "vy": state.playerVY = Double.parseDouble(value); break;
            case "hp": state.playerHP = Integer.parseInt(value); break;
            case "keys": state.keys = Integer.parseInt(value); break;
            case "coins": state.coins = Integer.parseInt(value); break;
            case "mode": state.mode = value; break;
            case "level": state.levelNum = Integer.parseInt(value); break;
        }
    }
    
    private void parseRoom(GameState state, String key, String value) {
        switch (key) {
            case "id": state.roomId = value; break;
            case "cleared_enemies": parseSet(state.clearedEnemies, value); break;
            case "collected_pickups": parseSet(state.collectedPickups, value); break;
            case "unlocked_doors": parseSet(state.unlockedDoors, value); break;
        }
    }
    
    private void parseGlobal(GameState state, String key, String value) {
        switch (key) {
            case "bosses_defeated": parseStringSet(state.bossesDefeated, value); break;
            case "play_time": state.playTime = Double.parseDouble(value); break;
        }
    }
    
    private void parseSet(Set<Integer> set, String value) {
        if (value.isEmpty()) return;
        for (String s : value.split(",")) {
            set.add(Integer.parseInt(s.trim()));
        }
    }
    
    private void parseStringSet(Set<String> set, String value) {
        if (value.isEmpty()) return;
        for (String s : value.split(",")) {
            set.add(s.trim());
        }
    }
    
    private String joinSet(Set<Integer> set) {
        if (set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int id : set) {
            if (!first) sb.append(",");
            sb.append(id);
            first = false;
        }
        return sb.toString();
    }
    
    private String joinStringSet(Set<String> set) {
        if (set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String s : set) {
            if (!first) sb.append(",");
            sb.append(s);
            first = false;
        }
        return sb.toString();
    }
    
    /** Apply loaded state to world. */
    public void applyState(GameState state, Physics.Body player, Combat combat, PlayerInventory inv, World world) {
        // Player
        player.x = state.playerX;
        player.y = state.playerY;
        player.vx = state.playerVX;
        player.vy = state.playerVY;
        combat.playerHP = state.playerHP;
        inv.keys = state.keys;
        inv.coins = state.coins;
        
        // Room state
        for (Enemy e : world.enemies) {
            if (state.clearedEnemies.contains(e.id)) {
                e.dead = true;
            }
        }
        for (Pickup p : world.pickups) {
            if (state.collectedPickups.contains(p.id)) {
                p.active = false;
            }
        }
        for (Door d : world.doors) {
            if (state.unlockedDoors.contains(d.id)) {
                d.locked = false;
            }
        }
    }
}
