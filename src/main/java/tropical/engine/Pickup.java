package tropical.engine;

/**
 * Collectible items: hearts (heal), keys (unlock doors).
 * 
 * Simple overlap detection with player. On pickup, apply effect
 * and deactivate. Hearts heal 1 HP. Keys add to player inventory.
 */
public class Pickup {
    public enum Type { HEART, KEY, COIN }

    static int nextId = 0;
    public final int id;  // unique ID for save system

    public Type type;
    public double x, y;          // center
    public double hw, hh;        // half-extents (collision box)
    public boolean active = true;
    AudioSystem audio = null;  // optional

    static final double HEART_HW = 12, HEART_HH = 10;
    static final double KEY_HW = 8, KEY_HH = 8;
    static final double COIN_HW = 8, COIN_HH = 8;

    public Pickup(Type type, double x, double y) {
        this.id = nextId++;
        this.type = type;
        this.x = x;
        this.y = y;

        if (type == Type.HEART) {
            hw = HEART_HW;
            hh = HEART_HH;
        } else if (type == Type.KEY) {
            hw = KEY_HW;
            hh = KEY_HH;
        } else {
            hw = COIN_HW;
            hh = COIN_HH;
        }
    }

    /** Attach audio system. */
    public void setAudio(AudioSystem audio) {
        this.audio = audio;
    }

    public static Pickup heart(double x, double y) {
        return new Pickup(Type.HEART, x, y);
    }

    public static Pickup key(double x, double y) {
        return new Pickup(Type.KEY, x, y);
    }

    public static Pickup coin(double x, double y) {
        return new Pickup(Type.COIN, x, y);
    }
    
    public Physics.AABB aabb() {
        return new Physics.AABB(x - hw, y - hh, x + hw, y + hh);
    }
    
    /** Check overlap with player and apply effect. Returns true if collected. */
    public boolean tryCollect(Physics.Body player, Combat combat, PlayerInventory inv) {
        if (!active) return false;
        
        Physics.AABB box = aabb();
        Physics.AABB pbox = player.aabb();
        
        if (box.overlaps(pbox)) {
            if (type == Type.HEART) {
                if (combat.playerHP < 3) {  // cap at 3 HP
                    combat.playerHP += 1;
                    combat.events.add("HEART: hp=" + combat.playerHP);
                    if (audio != null) audio.playSfx(AudioSystem.Sfx.PICKUP);
                    active = false;
                    return true;
                }
            } else if (type == Type.KEY) {
                inv.keys += 1;
                combat.events.add("KEY: keys=" + inv.keys);
                if (audio != null) audio.playSfx(AudioSystem.Sfx.KEY);
                active = false;
                return true;
            } else if (type == Type.COIN) {
                inv.coins += 1;
                combat.events.add("COIN: coins=" + inv.coins);
                if (audio != null) audio.playSfx(AudioSystem.Sfx.PICKUP);
                active = false;
                return true;
            }
        }
        return false;
    }
}
