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
    
    /** Clamp camera so viewport stays within room. */
    void clampToBounds() {
        // Don't scroll if room is smaller than viewport
        double minX = viewportW / 2;
        double maxX = Math.max(minX, roomW - viewportW / 2);
        double minY = viewportH / 2;
        double maxY = Math.max(minY, roomH - viewportH / 2);
        
        x = Math.max(minX, Math.min(maxX, x));
        y = Math.max(minY, Math.min(maxY, y));
    }
    
    /** Convert world X to screen X. */
    public double worldToScreenX(double worldX) {
        return worldX - (x - viewportW / 2);
    }
    
    /** Convert world Y to screen Y. */
    public double worldToScreenY(double worldY) {
        return worldY - (y - viewportH / 2);
    }
    
    /** Get parallax offset for a layer with given scroll factor (0 = fixed, 1 = camera speed). */
    public double parallaxOffset(double scrollFactor) {
        // Layer moves less than camera, creating depth
        return x * (1 - scrollFactor);
    }
}
