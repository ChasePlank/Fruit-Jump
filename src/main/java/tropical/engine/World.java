package tropical.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Game world: entities, physics, collision resolution.
 * 
 * The update cycle:
 *   1. Apply gravity
 *   2. Move entities
 *   3. Resolve collisions (swept AABB, push-out)
 *   4. Update grounded state
 */
public class World {
    final List<Physics.Body> bodies = new ArrayList<>();
    public final List<Physics.AABB> tiles = new ArrayList<>();

    /** Water: buoyancy, drag, currents, breath. Always present; with no field set it is a no-op, so nothing that
     *  does not use water pays for it. Ported from the Fruit-Jump release, where it lived alone. */
    public final WaterSystem water = new WaterSystem();

    /** Set the level's water field (see LevelMap.buildWater). */
    public void setWater(Water w) {
        water.setWater(w);
    }
    public final List<Physics.AABB> oneways = new ArrayList<>(); // platforms to jump through
    final List<MovingPlatform> movers = new ArrayList<>(); // kinematic platforms
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Physics.AABB> cracked = new ArrayList<>(); // destroyable tiles
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Door> doors = new ArrayList<>();
    public final List<Enemy> enemies = new ArrayList<>();  // for save system tracking
    /** Flying pursuers. They cannot hurt the player - a hit only knocks
     *  them flat for a moment and makes the bat disengage. */
    public final List<Bat> bats = new ArrayList<>();
    /** Raised when the player is inside a blast. The view consumes it and
     *  applies the HP loss, so the damage RULE lives in the engine and the HP
     *  write stays in one place. */
    public boolean playerBlastPending = false;
    /**
     * The player body. Set explicitly by whoever adds it.
     *
     * This used to be sniffed by looking for a body with `oneway == true`,
     * which is a property of the COLLISION, not of being the player - so a
     * headless test whose player was not marked oneway found nothing, and the
     * bat loop dereferenced null. Being explicit removes the whole class of
     * bug.
     */
    public Physics.Body playerBody;
    /**
     * Level extents in world units. Anything leaving these is culled. Kept on the World so nothing has to
     * hardcode a level size again.
     *
     * <p>These values are only a fallback - LevelMap.buildWorld calls setBounds with the real size, and it
     * should always be called. They are 60x20 cells because that is what the level was when they were written;
     * the generator now makes 14 rows and the tutorial 20, so a body relying on these would be relying on a
     * size no level has.
     */
    public double boundsLeft = 0, boundsTop = 0, boundsRight = 1920, boundsBottom = 640;

    /** Set the level extents (called when a map builds into this world). */
    public void setBounds(double w, double h) {
        boundsLeft = 0;
        boundsTop = 0;
        boundsRight = w;
        boundsBottom = h;
    }
    final List<Emitter> emitters = new ArrayList<>();
    final List<Slope> slopes = new ArrayList<>();  // walkable inclined surfaces
    public final List<Physics.AABB> spikes = new ArrayList<>();  // contact-damage zones
    final ParticlePool particles;
    AudioSystem audio = null;  // optional

    // Slope tuning
    static final double SLOPE_SNAP_UP = 12.0;    // max px/frame feet can snap up (walking up)
    static final double SLOPE_SNAP_DOWN = 14.0;  // max px/frame feet can snap down (walking down)
    static final double SLOPE_SLIDE_ACC = 900.0; // slide acceleration on steep slopes
    
    public World(int particlePoolSize) {
        this.particles = new ParticlePool(particlePoolSize);
    }
    
    public World() {
        this(200);  // default pool size
    }
    
    /** Attach audio system. */
    public void setAudio(AudioSystem audio) {
        this.audio = audio;
    }
    
    /** Add a solid tile. */
    public void addTile(double x0, double y0, double x1, double y1) {
        tiles.add(new Physics.AABB(x0, y0, x1, y1));
    }
    
    /** Add a cracked tile (destroyable by bombs). */
    public void addCracked(double x0, double y0, double x1, double y1) {
        Physics.AABB box = new Physics.AABB(x0, y0, x1, y1);
        cracked.add(box);
        tiles.add(box);  // also solid
    }
    
    /** Add a one-way platform (can jump through from below). */
    public void addOneway(double x0, double y0, double x1, double y1) {
        oneways.add(new Physics.AABB(x0, y0, x1, y1));
    }

    /** Add a walkable slope. (x0,y0)-(x1,y1) is the surface segment. */
    public void addSlope(double x0, double y0, double x1, double y1) {
        slopes.add(new Slope(x0, y0, x1, y1));
    }

    /** Add a spike (contact-damage zone). */
    public void addSpike(double x0, double y0, double x1, double y1) {
        spikes.add(new Physics.AABB(x0, y0, x1, y1));
    }
    
    /** Add a dynamic body (player, enemy). */
    public void addBody(Physics.Body b) {
        bodies.add(b);
    }

    /** Clear all room content for a room swap (top-down prototype):
     *  bodies, tiles, oneways, enemies, projectiles, pickups, doors.
     *  The caller re-adds the player and the new room's geometry. */
    public void clearRoom() {
        bodies.clear();
        tiles.clear();
        oneways.clear();
        enemies.clear();
        bats.clear();
        projectiles.clear();
        pickups.clear();
        doors.clear();
    }
    
    /** Add an enemy (also adds its body). */
    public void addEnemy(Enemy e) {
        enemies.add(e);
        bodies.add(e.body);
    }

    /** Add a bat (flying, no gravity, but still collides with terrain). */
    public void addBat(Bat b) {
        bats.add(b);
        bodies.add(b.body);
    }
    
    /** Add a kinematic moving platform. */
    public void addMover(MovingPlatform m) {
        movers.add(m);
    }
    
    /** Add a projectile. */
    public void addProjectile(Projectile p) {
        projectiles.add(p);
    }
    
    /** Add a pickup. */
    public void addPickup(Pickup p) {
        pickups.add(p);
    }
    
    /** Add a door. */
    public void addDoor(Door d) {
        doors.add(d);
        tiles.add(d.aabb());  // solid while locked
    }
    
    /** Add an emitter. */
    /** The particle pool, for the view. Particles are simulated here and drawn there. */
    public ParticlePool particles() { return particles; }

    public void addEmitter(Emitter e) {
        emitters.add(e);
    }
    
    /** Wall sensor: is there solid geometry just ahead of the body? */
    public boolean senseWall(Physics.Body b, int dir) {
        double probe = 2.0;
        double ox = b.x + dir * (b.hw + probe);
        for (Physics.AABB tile : tiles) {
            if (ox >= tile.x0 && ox <= tile.x1 && b.y - b.hh < tile.y1 && b.y + b.hh > tile.y0) {
                return true;
            }
        }
        return false;
    }
    
    /** Ledge sensor: is there ground just ahead-below the body? */
    public boolean senseLedge(Physics.Body b, int dir) {
        double probeX = 4.0;
        double probeY = 8.0;
        double ox = b.x + dir * (b.hw + probeX);
        double oy = b.y + b.hh + probeY;
        for (Physics.AABB tile : tiles) {
            if (ox >= tile.x0 && ox <= tile.x1 && oy >= tile.y0 && oy <= tile.y1) {
                return false; // ground ahead — not a ledge
            }
        }
        for (MovingPlatform m : movers) {
            Physics.AABB a = m.aabb();
            if (ox >= a.x0 && ox <= a.x1 && oy >= a.y0 && oy <= a.y1) {
                return false;
            }
        }
        return true; // no ground ahead — ledge
    }
    
    /** Physics step. */
    public void update(double dt) {
        // Update kinematic platforms and carry riders.
        // Carry BEFORE physics: a resting player generates no collision
        // (tEntry=0 is rejected), so collision-response carry never fires.
        // Direct displacement is the standard approach: if the player is
        // standing on the platform, move them with it.
        for (MovingPlatform m : movers) {
            double[] delta = m.update(dt);
            for (Physics.Body b : bodies) {
                if (m.isCarrying(b)) {
                    b.x += delta[0] * dt;
                    b.y += delta[1] * dt;
                }
            }
        }
        
        // Update projectiles
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            double oldX = p.x, oldY = p.y;
            p.update(dt);

            // Cull against the LEVEL's bounds. This is where the old hardcoded
            // `y > 500` in Projectile.update belonged, and its absence is what
            // made arrows disappear: the level got taller and the constant did
            // not, so every arrow spawned already outside the world.
            if (p.active) {
                double m = 200;
                if (p.x < boundsLeft - m || p.x > boundsRight + m
                        || p.y > boundsBottom + m || p.y < boundsTop - m) {
                    p.active = false;
                }
            }
            
            // Bomb tile collision - stop on ground
            if (p.active && p.type == Projectile.Type.BOMB) {
                Physics.AABB pbox = p.aabb();
                for (Physics.AABB tile : tiles) {
                    if (tile.overlaps(pbox)) {
                        // Landing: bomb was ABOVE the tile's top last frame
                        // and is falling. Otherwise it hit a wall from the
                        // side — just stop horizontal movement (no teleport
                        // on top of walls).
                        if (p.vy > 0 && oldY + p.hh <= tile.y0 + 1) {
                            p.y = tile.y0 - p.hh;
                            p.vy = 0;
                            p.vx *= 0.3;  // friction
                        } else {
                            p.vx = 0;
                        }
                        break;
                    }
                }
            }
            
            // Arrow tile collision - deactivate on hit
            if (p.active && p.type == Projectile.Type.ARROW) {
                Physics.AABB pbox = p.aabb();
                for (Physics.AABB tile : tiles) {
                    if (tile.overlaps(pbox)) {
                        p.active = false;
                        break;
                    }
                }
                // Arrow-enemy collision: kill on first hit. This was
                // missing entirely — arrows flew through enemies
                // (playtest: "arrows fire, but do nothing").
                if (p.active) {
                    for (Enemy e : enemies) {
                        if (!e.dead && pbox.overlaps(e.body.aabb())) {
                            e.dead = true;
                            p.active = false;
                            break;
                        }
                    }
                }
                // Arrows hit bats too. They were not in `enemies`, so an arrow
                // flew straight through one (playtest: the bat reads as
                // invincible).
                if (p.active) {
                    for (int bi = bats.size() - 1; bi >= 0; bi--) {
                        Bat bat = bats.get(bi);
                        if (pbox.overlaps(bat.body.aabb())) {
                            bats.remove(bi);
                            bodies.remove(bat.body);
                            p.active = false;
                            break;
                        }
                    }
                }
            }
            
            if (!p.active) {
                if (p.type == Projectile.Type.BOMB) {
                    handleExplosion(p.x, p.y);
                }
                projectiles.remove(i);
            }
        }
        
        // Update emitters
        for (int i = emitters.size() - 1; i >= 0; i--) {
            Emitter e = emitters.get(i);
            e.update(dt, particles);
            if (!e.active) {
                emitters.remove(i);
            }
        }
        
        // Update particles
        for (Particle p : particles.getAll()) {
            p.update(dt, 300);  // light gravity for particles
        }
        
        // Update enemy AI (find player body for chase logic)
        Physics.Body playerBody = this.playerBody;
        if (playerBody == null) {
            // Fallback for callers that have not set it. Still a heuristic, so
            // it can find nothing - every use below must null-check.
            for (Physics.Body b : bodies) {
                if (b.oneway) { playerBody = b; break; }
            }
        }
        for (Enemy e : enemies) {
            if (!e.dead && playerBody != null) {
                if (e.topDown) {
                    e.hitWall = e.senseWallTopDown(tiles);
                } else {
                    e.hitWall = senseWall(e.body, e.dir);
                    e.atLedge = senseLedge(e.body, e.dir);
                }
                e.updateAI(dt, playerBody.x, playerBody.y);
            }
        }

        // Bats. Their whole point is the FOLLOW: the player can lead one away
        // from a gap or bait its dive, so the bat is positioning play rather
        // than a timed jump. PURSUE_SPEED is deliberately below the player's
        // run speed, because you cannot lead something that outruns you.
        for (int bi = bats.size() - 1; bi >= 0; bi--) {
            Bat bat = bats.get(bi);
            if (playerBody == null) continue;
            // Null-checked BEFORE bat.update, which dereferences the player.
            // The check used to come after, so a caller with no player body
            // crashed here instead of skipping the bats.
            boolean connected = bat.update(dt, playerBody);
            if (!bat.body.aabb().overlaps(playerBody.aabb())) continue;
            // Coming down on top of one squashes it, exactly like a ground
            // enemy. A bat is NOT invincible - the flight is what makes it hard
            // to reach, not armour (playtest: "the bat is invincible, i tried
            // jumping on it and it stunned me instead").
            boolean stomped = playerBody.vy > 0
                    && (playerBody.y + playerBody.hh) < bat.body.y;
            if (stomped) {
                bats.remove(bi);
                bodies.remove(bat.body);
                playerBody.vy = -400;      // same bounce as stomping an enemy
                if (audio != null) audio.playSfx(AudioSystem.Sfx.STOMP);
            } else if (connected) {
                playerBody.stunTimer = Bat.STUN_SECONDS;
                // EVERY bat breaks off, not just the one that connected. With
                // three bats the old relay meant each stunned in turn and the
                // player stayed pinned for the whole sequence - the more bats,
                // the longer the lock.
                for (Bat other : bats) other.flee();
            }
        }

        // Stun tick. While a body is flat it keeps its gravity - an airborne
        // player keeps falling - but loses all horizontal control. This lives
        // in the engine, not the view, so the validator and the recovery test
        // see exactly the state the player does.
        for (Physics.Body b : bodies) {
            if (b.stunTimer > 0) {
                b.stunTimer -= dt;
                b.vx = 0;
            }
        }
        
        for (Physics.Body b : bodies) {
            step(b, dt);
            // Ground probe: a body resting exactly on a surface generates no
            // collision (tEntry=0 rejected), so grounded would flicker false.
            // Cast a short downward ray — if ground is within a small tolerance,
            // the body is grounded even without a collision this frame.
            if (!b.grounded && b.vy >= 0) {
                double probe = 1.0; // pixels
                for (Physics.AABB tile : tiles) {
                    Physics.Hit h = Physics.raycastAABB(
                        b.x, b.y + b.hh, 0, probe, tile);
                    if (h != null) { b.grounded = true; break; }
                }
                if (!b.grounded) {
                    for (MovingPlatform m : movers) {
                        Physics.Hit h = Physics.raycastAABB(
                            b.x, b.y + b.hh, 0, probe, m.aabb());
                        if (h != null) { b.grounded = true; break; }
                    }
                }
            }
        }
    }
    
    /**
     * A splash of droplets at a surface crossing, scaled by impact speed.
     *
     * The water system has known how to report a crossing since it came upstream
     * - splashEvents(), consumeSplash(), splashX/Y/Speed - and nothing here ever
     * asked. So a body could enter a pool and the pool said nothing: no droplets,
     * no sound, nothing to tell a player the water was a place they had arrived
     * at rather than a colour they had walked into.
     */
    void spawnSplash(double x, double y, double speed) {
        double s = Math.min(1.0, speed / 400.0);
        emitters.add(Emitter.burst(x, y, (int) (6 + 10 * s))
            .speed(30 + 60 * s, 80 + 160 * s)
            .angle(-Math.PI * 0.95, -Math.PI * 0.05)
            .lifetime(0.25, 0.6)
            .size(2, 2 + 3 * s)
            .gravity(600)
            .color(0.85, 0.95, 1.0));   // pale water, so a droplet reads against the pool
        if (audio != null) audio.playSfx(AudioSystem.Sfx.SPLASH);
    }

    /** Handle bomb explosion: damage enemies, destroy cracked tiles. */
    void handleExplosion(double x, double y) {
        if (audio != null) audio.playSfx(AudioSystem.Sfx.EXPLOSION);

        // Blast damage. This used to set `hitByExplosion` on every body in
        // range and NOTHING ever read the flag - so a bomb killed nothing at
        // all, not even enemies. Applied directly now.
        //
        // A blast is not selective: the player is inside its own blast radius
        // like anything else (Kinger, Sept 29). That is also what makes a
        // placed bomb a real decision rather than a free wall-opener.
        for (Enemy e : enemies) {
            if (!e.dead && inBlast(e.body.x, e.body.y, x, y)) e.dead = true;
        }
        for (int i = bats.size() - 1; i >= 0; i--) {
            Bat bat = bats.get(i);
            if (inBlast(bat.body.x, bat.body.y, x, y)) {
                bats.remove(i);
                bodies.remove(bat.body);
            }
        }
        for (Physics.Body b : bodies) {
            if (b.oneway && inBlast(b.x, b.y, x, y)) {
                // The player is marked `oneway`. World has no Combat, so it
                // raises an event and the view applies it - keeps the damage
                // rule in the engine and the HP write in one place.
                playerBlastPending = true;
            }
        }
        
        // Destroy cracked tiles in range
        for (int i = cracked.size() - 1; i >= 0; i--) {
            Physics.AABB c = cracked.get(i);
            double cx = (c.x0 + c.x1) / 2;
            double cy = (c.y0 + c.y1) / 2;
            double dx = cx - x;
            double dy = cy - y;
            double dist = Math.sqrt(dx*dx + dy*dy);
            if (dist < Projectile.BLAST_RADIUS) {
                cracked.remove(i);
                tiles.remove(c);
            }
        }
    }

    private static boolean inBlast(double bx, double by, double x, double y) {
        double dx = bx - x, dy = by - y;
        return Math.sqrt(dx * dx + dy * dy) < Projectile.BLAST_DAMAGE_RANGE;
    }
    
    /** Unlock a door (remove from solid tiles). */
    public void unlockDoor(Door d) {
        // Remove door AABB from tiles (door.locked already set to false by tryUnlock)
        tiles.remove(d.aabb());
    }
    
    void step(Physics.Body b, double dt) {
        boolean wasGrounded = b.grounded;  // previous frame's support state

        // Water runs BEFORE gravity. When the body is swimming, water has already
        // accounted for vertical motion, so gravity must not also apply - both in one
        // frame double-counts. In shallow water this returns false and gravity applies
        // as normal.
        //
        // Ported from the Fruit-Jump release. Note it does NOT carry that copy's
        // gravityScale factor, which aside does not have: an integration, not a copy.
        boolean mediumApplied = water.applyMedium(b, dt);

        // Apply gravity (unless disabled)
        if (!b.noGravity && !mediumApplied) {
            b.vy += Physics.GRAVITY * dt;
        }

        // Reset grounded
        b.grounded = false;

        // Move and collide (AABB sweep handles all solid geometry)
        moveBody(b, dt);

        // Water state at the post-move position. Runs after the move so a body that has just entered or left
        // the water this frame is measured where it actually ended up, not where it started.
        water.postStep(b, dt);
        // The splash has to be spent here, for the same reason: the crossing happens DURING the move, so only
        // comparing pre-move and post-move state can tell an entry from a body already floating.
        if (water.consumeSplash()) spawnSplash(water.splashX(), water.splashY(), water.splashSpeed());

        // Slope resolution: snap feet to slope surfaces.
        // Runs AFTER the sweep so slopes never fight the solid collision.
        resolveSlopes(b, dt, wasGrounded);

        // Ground probe: a body resting exactly on a surface boundary can
        // end the sweep with grounded=false (strict-inequality embedded
        // check treats "exactly touching" as not embedded, and gravity's
        // tiny downward step may not re-collide this frame). The flicker
        // (g alternating true/false at rest) breaks any consumer that
        // gates an action on grounded — the level validator's jump fired
        // a frame late and its arc landed 18px short. Probe 1px below
        // the feet: if solid is there, the body is supported.
        if (!b.grounded && b.vy >= 0) {
            Physics.AABB probe = new Physics.AABB(b.x - b.hw + 1, b.y + b.hh,
                                                  b.x + b.hw - 1, b.y + b.hh + 1);
            for (Physics.AABB tile : tiles) {
                if (tile.overlaps(probe)) { b.grounded = true; break; }
            }
        }
    }

    /**
     * Slope resolution pass — the height-function approach.
     *
     * For each slope covering the body's foot span:
     *   - Walking up: feet penetrate the surface → snap up (capped, so
     *     jumping through a slope from below doesn't teleport you).
     *   - Walking down: feet leave the surface by less than SNAP_DOWN
     *     while moving roughly horizontally → snap down (no launch off
     *     downhill ramps). Only when already near the surface — a body
     *     in free fall past a slope must not get yanked onto it.
     *   - Steep slopes: don't ground; accelerate the body downhill
     *     instead (slide).
     */
    void resolveSlopes(Physics.Body b, double dt, boolean wasGrounded) {
        if (slopes.isEmpty()) return;

        double footLeft = b.x - b.hw;
        double footRight = b.x + b.hw;
        double feet = b.y + b.hh;

        for (Slope s : slopes) {
            if (!s.containsX(footLeft) && !s.containsX(footRight) &&
                !(footLeft < s.x0 && footRight > s.x1)) {
                continue;
            }

            double surfaceY = s.surfaceYUnder(
                Math.max(footLeft, s.x0), Math.min(footRight, s.x1));

            if (s.steep) {
                // Too steep to stand on: slide downhill, GLUED to the
                // surface. Use the CENTER-x surface height — the span's
                // highest point (min-y) is the left edge on a descending
                // slope, up to span*ratio above the center (96px on this
                // one), which glues the body high off the real surface.
                double centerSurface = s.surfaceYAt(b.x);
                if (feet >= centerSurface - 4 && feet <= centerSurface + 24) {
                    b.vx += s.downhillX * SLOPE_SLIDE_ACC * dt;
                    b.y = centerSurface - b.hh;      // glue to surface
                    if (b.vy > 0) b.vy = 0;
                    // NOT grounded — sliding, not standing.
                }
                continue;
            }

            if (feet > surfaceY) {
                // Feet below the surface: penetration.
                // Guard by velocity, not depth: a depth cap breaks at
                // flat-to-slope seams (leading foot enters up to
                // span*ratio below the rising surface). Only refuse the
                // snap when moving upward fast — jumping through from
                // below must stay allowed.
                if (b.vy >= -60) {
                    b.y = surfaceY - b.hh;
                    if (b.vy > 0) b.vy = 0;  // kill downward velocity
                    b.grounded = true;
                }
            } else if (wasGrounded || Math.abs(b.vy) < 60) {
                // Feet above surface. Snap down only if recently
                // supported / moving horizontally — walking down a ramp
                // without bouncing. Free-falling bodies pass by cleanly.
                double gap = surfaceY - feet;
                if (gap > 0 && gap <= SLOPE_SNAP_DOWN) {
                    b.y = surfaceY - b.hh;
                    if (b.vy > 0) b.vy = 0;
                    b.grounded = true;
                }
            }
        }
    }
    
    void moveBody(Physics.Body b, double dt) {
        // Iteratively resolve collisions until we've moved the full frame
        // or run out of collisions (max iterations to prevent infinite loops)
        double remaining = 1.0;
        int maxIterations = 10;
        
        for (int iter = 0; iter < maxIterations && remaining > 0.0001; iter++) {
            Physics.Hit hit = null;
            double minTime = remaining;
            double nx = 0, ny = 0;
            boolean isOneway = false;
            MovingPlatform hitMover = null;
            
            // Find earliest collision against all static geometry
            for (Physics.AABB tile : tiles) {
                Physics.Hit h = Physics.sweepAABB(b, tile, dt * remaining);
                if (h != null && h.time < minTime) {
                    minTime = h.time;
                    nx = h.nx;
                    ny = h.ny;
                    hit = h;
                    isOneway = false;
                }
            }
            
            // Moving platforms (solid, carry the player)
            for (MovingPlatform m : movers) {
                Physics.AABB a = m.aabb();
                Physics.Hit h = Physics.sweepAABB(b, a, dt * remaining);
                if (h != null && h.time < minTime) {
                    minTime = h.time;
                    nx = h.nx;
                    ny = h.ny;
                    hit = h;
                    isOneway = false;
                    hitMover = m;
                }
            }
            
            // One-way platforms (only when falling)
            if (b.vy > 0) {
                for (Physics.AABB p : oneways) {
                    Physics.Hit h = Physics.sweepAABB(b, p, dt * remaining);
                    if (h != null && h.time < minTime && h.ny == -1) {
                        minTime = h.time;
                        nx = h.nx;
                        ny = h.ny;
                        hit = h;
                        isOneway = true;
                    }
                }
            }
            
            // Move to collision point (minTime fraction of the frame;
            // with no hit, minTime == remaining, so this is the full move)
            double eps = 0.0001;
            b.x += b.vx * dt * minTime;
            b.y += b.vy * dt * minTime;

            if (hit != null) {
                // For embedded case (time=0), teleport to boundary
                // For swept case, push out slightly
                if (minTime < 0.0001) {
                    // Embedded: use contact point from hit
                    b.x = hit.px;
                    b.y = hit.py;
                } else {
                    // Swept: push out along normal
                    b.x += nx * eps;
                    b.y += ny * eps;
                }

                // Remove velocity along normal (slide)
                double dot = b.vx * nx + b.vy * ny;
                b.vx -= dot * nx;
                b.vy -= dot * ny;

                // Grounded if we hit from above (normal points up in screen coords)
                if (ny == -1) {
                    b.grounded = true;
                }

                // Carried by moving platform: inherit its velocity
                if (hitMover != null && ny == -1) {
                    b.x += hitMover.vx * dt * remaining;
                    b.y += hitMover.vy * dt * remaining;
                }

                // Continue with remaining time
                remaining = (remaining - minTime);
            } else {
                // No collision — the move above already covered the full
                // remaining time (minTime == remaining). Do NOT move again.
                // (The old double-move here made every free-moving body
                // travel 2x its velocity — exposed by the slope test's
                // px/frame measurement, invisible to qualitative tests.)
                remaining = 0;
            }
        }
    }
}
