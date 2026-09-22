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
    public final List<Physics.AABB> oneways = new ArrayList<>(); // platforms to jump through
    final List<MovingPlatform> movers = new ArrayList<>(); // kinematic platforms
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Physics.AABB> cracked = new ArrayList<>(); // destroyable tiles
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Door> doors = new ArrayList<>();
    public final List<Enemy> enemies = new ArrayList<>();  // for save system tracking
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
    
    /** Add an enemy (also adds its body). */
    public void addEnemy(Enemy e) {
        enemies.add(e);
        bodies.add(e.body);
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
        Physics.Body playerBody = null;
        for (Physics.Body b : bodies) {
            if (b.oneway) { playerBody = b; break; }  // player is marked oneway=true
        }
        for (Enemy e : enemies) {
            if (!e.dead && playerBody != null) {
                e.hitWall = senseWall(e.body, e.dir);
                e.atLedge = senseLedge(e.body, e.dir);
                e.updateAI(dt, playerBody.x, playerBody.y);
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
    
    /** Handle bomb explosion: damage enemies, destroy cracked tiles. */
    void handleExplosion(double x, double y) {
        if (audio != null) audio.playSfx(AudioSystem.Sfx.EXPLOSION);
        
        // Damage enemies in range
        for (Physics.Body b : bodies) {
            double dx = b.x - x;
            double dy = b.y - y;
            double dist = Math.sqrt(dx*dx + dy*dy);
            if (dist < Projectile.BLAST_DAMAGE_RANGE) {
                // Mark for death — combat system will handle
                // For now, we use a simple flag on the body
                b.hitByExplosion = true;
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
    
    /** Unlock a door (remove from solid tiles). */
    public void unlockDoor(Door d) {
        // Remove door AABB from tiles (door.locked already set to false by tryUnlock)
        tiles.remove(d.aabb());
    }
    
    void step(Physics.Body b, double dt) {
        boolean wasGrounded = b.grounded;  // previous frame's support state

        // Apply gravity (unless disabled)
        if (!b.noGravity) {
            b.vy += Physics.GRAVITY * dt;
        }

        // Reset grounded
        b.grounded = false;

        // Move and collide (AABB sweep handles all solid geometry)
        moveBody(b, dt);

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
