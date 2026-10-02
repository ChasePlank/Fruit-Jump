package tropical.engine;

/**
 * Projectile system: arrows and bombs.
 * 
 * Arrows: fast, affected by gravity, kill enemies on first hit.
 * Bombs: thrown arc, fuse timer, explosion radius damages enemies
 * and destroys cracked terrain.
 */
public class Projectile {
    public enum Type { ARROW, BOMB }
    
    public Type type;
    public double x, y;          // center
    double vx, vy;
    double hw, hh;        // half-extents
    int dir;              // facing direction (1=right, -1=left)
    
    public boolean active = true;
    boolean armed = true; // arrows can hit after first frame
    public double timer = 0;     // bomb fuse
    
    // Arrow params
    static final double ARROW_SPEED = 700;      // fast enough to cross gaps
    static final double ARROW_GRAVITY = 250;     // light arc
    static final double ARROW_HW = 12, ARROW_HH = 3;
    
    // Bomb params
    static final double BOMB_SPEED = 70;        // a short toss: it lands where you are
    static final double BOMB_GRAVITY = 600;
    static final double BOMB_HW = 6, BOMB_HH = 6;
    /**
     * How long before it goes off.
     *
     * The bomb lands at your feet and drifts forward at BOMB_SPEED, so running
     * the way you threw it only opens the gap at (RUN_SPEED - BOMB_SPEED) =
     * 130px/s. At a 1.0s fuse and a 120px damage radius that left TEN PIXELS of
     * margin for a player running flat out the whole time - and nothing at all
     * for one who clipped a step, a slope or an enemy on the way. A tester who
     * never stopped running still got hit, which is the honest description of a
     * 10px margin (playtest, Oct 2).
     *
     * 1.3s gives 169px of separation against a 100px radius: 69px of slack,
     * about a third of a second of fumbling.
     */
    public static final double FUSE_TIME = 1.3;

    /**
     * The radius the blast DRAWS, and the radius that hurts.
     *
     * These two are deliberately different, and this way round:
     * BLAST_RADIUS (the tiles it breaks) reaches further than the damage does,
     * so a wall can be opened from just outside the lethal circle. Damage
     * reaching further than the break would be the wrong way round - it would
     * mean standing at the edge of what looks like a useful blast and being
     * killed by it.
     *
     * BLAST_DAMAGE_RANGE is the number drawn on screen, because it is the one
     * the player has to judge.
     */
    public static final double BLAST_RADIUS = 115;
    public static final double BLAST_DAMAGE_RANGE = 100;
    
    public Projectile(Type type, double x, double y, int dir) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.dir = dir;
        
        if (type == Type.ARROW) {
            vx = dir * ARROW_SPEED;
            vy = -50;  // slight upward arc
            hw = ARROW_HW;
            hh = ARROW_HH;
        } else {
            vx = dir * BOMB_SPEED;
            vy = -300;  // throw arc
            hw = BOMB_HW;
            hh = BOMB_HH;
            timer = FUSE_TIME;
        }
    }
    
    public void update(double dt) {
        if (!active) return;
        
        x += vx * dt;
        y += vy * dt;
        
        if (type == Type.ARROW) {
            vy += ARROW_GRAVITY * dt;
            // No world-bounds test here. It used to hardcode `y > 500`, written
            // for a 14-row level; when the level grew to 20 rows the floor moved
            // to y=544 and every arrow spawned already past the line, so it
            // deactivated on its first frame and arrows simply vanished.
            // Culling belongs in World, against the level's REAL bounds.
        } else {
            vy += BOMB_GRAVITY * dt;
            timer -= dt;
            if (timer <= 0) {
                explode();
            }
        }
        
        armed = true;  // can hit after first frame
    }
    
    void explode() {
        active = false;
        // Explosion handled by World — radius check on enemies and cracked tiles
    }
    
    public Physics.AABB aabb() {
        return new Physics.AABB(x - hw, y - hh, x + hw, y + hh);
    }
    
    /** Create an arrow. */
    public static Projectile arrow(double x, double y, int dir) {
        return new Projectile(Type.ARROW, x, y, dir);
    }
    
    /** Create a bomb. */
    public static Projectile bomb(double x, double y, int dir) {
        return new Projectile(Type.BOMB, x, y, dir);
    }
}
