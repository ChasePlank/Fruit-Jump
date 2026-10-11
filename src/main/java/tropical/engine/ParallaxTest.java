package tropical.engine;

/**
 * The ridges move less the further away they are, and they move with the camera.
 *
 * <p><b>WRITTEN BECAUSE THE RELEASE HAD IT INVERTED AND NOTHING NOTICED.</b> `ParallaxLayer` documents its own
 * convention - "0 = fixed in screen space, 1 = moves with camera" - and defines far() as 0.3 and near() as 0.6. The
 * release's Camera multiplied by `(1 - scrollFactor)`, so the distant ridge travelled 70% of the camera and the near
 * one 40%: backwards, and swapped relative to each other. It was found by tools/check-platformer-match.sh, not by a
 * test, and the reason is that this repository's only rendering check takes a screenshot and saves it WITHOUT
 * ASSERTING ANYTHING ABOUT WHAT IT DREW.
 *
 * <p>No rendering needed: the offset is arithmetic, so this runs headless like the rest.
 */
public class ParallaxTest {
    static int failures = 0;
    static int passes = 0;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (ok) passes++; else failures++;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    public static int runAll() {
        failures = 0;
        passes = 0;

        Camera cam = new Camera(1280, 720);
        cam.x = 1000;
        ParallaxLayer far = ParallaxLayer.far(64);
        ParallaxLayer near = ParallaxLayer.near(26);

        double farX = far.getOffsetX(cam);
        double nearX = near.getOffsetX(cam);

        verdict("the distant ridge moves WITH the camera (" + (int) farX + "px at camera x=1000)",
                farX > 0);
        verdict("and moves less than the near one - " + (int) farX + "px against " + (int) nearX + "px",
                Math.abs(farX) < Math.abs(nearX));
        verdict("and both are the fractions the layers document (0.3 and 0.6 of the camera)",
                Math.abs(farX - 0.3 * 1000) < 1.0 && Math.abs(nearX - 0.6 * 1000) < 1.0);
        verdict("and a camera at the origin puts both at the origin",
                ParallaxLayer.far(64).getOffsetX(new Camera(1280, 720)) == 0.0);

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
