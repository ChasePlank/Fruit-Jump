package tropical.engine;

/**
 * Parallax background layer: scrolls at a fraction of camera speed.
 * 
 * Multiple layers with different scroll factors create depth illusion.
 * Far layers (low factor) move slowly, near layers (high factor) move faster.
 */
public class ParallaxLayer {
    double scrollFactor;  // 0 = fixed in screen space, 1 = moves with camera
    double offsetY;        // vertical position of layer
    
    public ParallaxLayer(double scrollFactor, double offsetY) {
        this.scrollFactor = scrollFactor;
        this.offsetY = offsetY;
    }
    
    /** Get X offset for this layer given camera position. */
    public double getOffsetX(Camera camera) {
        return camera.parallaxOffset(scrollFactor);
    }
    
    /** Get Y position (fixed for now, could be parallax too). */
    public double getOffsetY() {
        return offsetY;
    }
    
    /** Far layer (mountains, sky) — very slow scroll. */
    public static ParallaxLayer far(double offsetY) {
        return new ParallaxLayer(0.3, offsetY);
    }
    
    /** Near layer (trees, buildings) — medium scroll. */
    public static ParallaxLayer near(double offsetY) {
        return new ParallaxLayer(0.6, offsetY);
    }
}
