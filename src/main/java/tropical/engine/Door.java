package tropical.engine;

/**
 * Locked door: solid until unlocked with a key.
 * 
 * Zelda-style: player overlaps, spends a key, door opens
 * (becomes non-solid). The door is a physics tile while locked.
 */
public class Door {
    static int nextId = 0;
    final int id;  // unique ID for save system
    
    double x0, y0, x1, y1;  // AABB bounds
    boolean locked = true;
    Physics.AABB aabb;
    AudioSystem audio = null;  // optional
    
    public Door(double x0, double y0, double x1, double y1) {
        this.id = nextId++;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.aabb = new Physics.AABB(x0, y0, x1, y1);
    }
    
    /** Attach audio system. */
    public void setAudio(AudioSystem audio) {
        this.audio = audio;
    }
    
    public Physics.AABB aabb() {
        return aabb;
    }
    
    /** Check if player can unlock. Returns true if door opened. */
    public boolean tryUnlock(Physics.Body player, PlayerInventory inv, Combat combat) {
        if (!locked) return false;
        
        Physics.AABB pbox = player.aabb();
        // Interaction box: slightly larger than the door itself — a
        // player pressed against the door face is pushed out to ~12px
        // short of overlap by collision resolution and would otherwise
        // never "touch" it (found by validator trace).
        Physics.AABB interact = new Physics.AABB(
            aabb.x0 - 16, aabb.y0, aabb.x1 + 16, aabb.y1);
        if (!interact.overlaps(pbox)) return false;
        
        if (inv.keys > 0) {
            inv.keys -= 1;
            locked = false;
            combat.events.add("DOOR: unlocked (keys=" + inv.keys + ")");
            if (audio != null) audio.playSfx(AudioSystem.Sfx.DOOR);
            return true;
        }
        return false;
    }
    
    /** Is this door still solid? */
    public boolean isSolid() {
        return locked;
    }
}
