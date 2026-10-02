package tropical.engine;

/**
 * Camera system: follows a target with smooth interpolation,
 * look-ahead in movement direction, and room boundary clamping.
 * 
 * The camera operates in world space. Rendering would offset all
 * positions by -cameraX, -cameraY to achieve the scrolling effect.
 */
public class Camera {
    double x, y;              // camera center (world coordinates)
    double targetX, targetY;  // actual target position (before lerp)
    
    double viewportW, viewportH;  // screen dimensions
    double roomW, roomH;           // current room dimensions

    /**
     * Logical -> physical zoom factor.
     *
     * The engine works in LOGICAL world units (32px cells, an 800x600
     * logical view). The high-res canvas is 1600x1200 PHYSICAL pixels.
     * worldToScreen is the only place that conversion belongs: every
     * draw call feeds the result straight to the GraphicsContext and
     * multiplies SIZES by the same factor, so positions and sizes stay
     * in the same space.
     *
     * This is what was missing. The camera was built with a PHYSICAL
     * viewport (1600x1200) but handed LOGICAL room bounds (1920x448),
     * so the room read as far shorter than the view; the vertical
     * clamp had no room to scroll and pinned the camera, and the whole
     * level rendered at 1x in the top 448px of a 1200px window. Sprites
     * were still drawn at 2x on top of those 1x positions, so every
     * sprite sat half a tile off its own collision box -- the door read
     * as sunk a tile into the floor, pickups sat off-centre, the exit
     * floated. One missing multiply.
     */
    double scale = 1.0;

    /** Viewport width in the camera's own units (logical). */
    double viewW() { return viewportW / scale; }

    /** Viewport height in the camera's own units (logical). */
    double viewH() { return viewportH / scale; }
    
    // Smoothing
    double lerpFactor = 4.0;       // higher = snappier, lower = smoother
    double lookAheadDist = 40;     // pixels ahead of player in movement dir
    double lookAheadSpeed = 150;   // player speed threshold to activate
    
    // Current look-ahead offset (smoothed)
    double lookAheadX = 0;
    double lookAheadSmooth = 8.0;  // smoothing factor for look-ahead
    
    public Camera(double viewportW, double viewportH) {
        this.viewportW = viewportW;
        this.viewportH = viewportH;
    }
    
    /** Set room bounds for clamping. */
    public void setRoom(double roomW, double roomH) {
        this.roomW = roomW;
        this.roomH = roomH;
    }

    /** Set the logical -> physical zoom applied by worldToScreen. */
    public void setScale(double scale) {
        this.scale = scale;
    }
    
    /** Update camera position to follow target. */
    public void update(double dt, double targetX, double targetY, double targetVX) {
        // Look-ahead: offset in movement direction
        double desiredLookAhead = 0;
        if (Math.abs(targetVX) > lookAheadSpeed) {
            desiredLookAhead = Math.signum(targetVX) * lookAheadDist;
        }
        
        // Smooth the look-ahead transition
        lookAheadX += (desiredLookAhead - lookAheadX) * Math.min(1, lookAheadSmooth * dt);
        
        // Target position with look-ahead
        this.targetX = targetX + lookAheadX;
        this.targetY = targetY;
        
        // Lerp camera position
        x += (this.targetX - x) * Math.min(1, lerpFactor * dt);
        y += (this.targetY - y) * Math.min(1, lerpFactor * dt);
        
        // Clamp to room bounds
        clampToBounds();
    }
    
    /**
     * Clamp camera so viewport stays within room.
     *
     * Camera state is LOGICAL -- the same units as the bodies it
     * follows (update() is handed player.x/player.y directly) and the
     * same units as the room bounds in setRoom(). The viewport is
     * PHYSICAL, so it is divided down here rather than scaling the
     * camera up. worldToScreen is the only place the two spaces meet.
     *
     * Mixing them here is what pinned the camera: a physical viewport
     * compared against logical room bounds made the room look 448px
     * tall against a 1200px view, max(min, ...) collapsed the scroll
     * range to a single point, and the level rendered at 1x in the top
     * third of the window.
     *
     * A room smaller than the view has nowhere to scroll; centring it
     * is the only answer that cannot look broken.
     */
    void clampToBounds() {
        double vw = viewW(), vh = viewH();
        if (roomW <= vw) {
            x = roomW / 2;
        } else {
            x = Math.max(vw / 2, Math.min(roomW - vw / 2, x));
        }
        if (roomH <= vh) {
            y = roomH / 2;
        } else {
            y = Math.max(vh / 2, Math.min(roomH - vh / 2, y));
        }
    }

    /** Convert world X to screen X (physical pixels, scaled). */
    public double worldToScreenX(double worldX) {
        return (worldX - (x - viewW() / 2)) * scale;
    }

    /** Convert world Y to screen Y (physical pixels, scaled). */
    public double worldToScreenY(double worldY) {
        return (worldY - (y - viewH() / 2)) * scale;
    }
    
    /** Get parallax offset for a layer with given scroll factor (0 = fixed, 1 = camera speed). */
    public double parallaxOffset(double scrollFactor) {
        // Layer moves less than camera, creating depth
        return x * (1 - scrollFactor);
    }
}
