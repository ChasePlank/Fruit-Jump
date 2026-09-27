package tropical;
import tropical.engine.*;

/**
 * JumpFeelTest - frame-accurate checks on the input-forgiveness layer, plus the
 * two numbers that decide whether it is safe to ship:
 *
 *   1. Does a full-hold jump still clear what the level generator promised?
 *   2. What does apex hang do to the apex and the arc?
 *
 * Everything runs through the real World, so the gravityScale hook in
 * World.step is exercised, not simulated around.
 */
public class JumpFeelTest {
    static int failures = 0;
    static final double DT = GameLoop.DT;
    static final double RUN = 200;

    static void check(String n, boolean ok) { check(n, ok, ""); }

    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n,
            d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    static World floorWorld(Physics.Body[] out, double floorTop) {
        World w = new World();
        w.addTile(0, floorTop, 600, floorTop + 60);
        Physics.Body b = new Physics.Body(60, floorTop - 22, 24, 44);
        w.addBody(b);
        out[0] = b;
        return w;
    }

    /**
     * Jump and measure the peak rise. releaseFrame -1 = never released (a full
     * hold); otherwise the key comes up on that frame.
     */
    static double measureApex(double vy0, boolean hang, int releaseFrame) {
        Physics.Body[] out = new Physics.Body[1];
        World w = floorWorld(out, 500);
        Physics.Body b = out[0];
        for (int i = 0; i < 40; i++) w.update(DT);

        JumpFeel f = new JumpFeel();
        f.press();
        f.update(DT, b.grounded, b.vy);
        if (f.consumeJump(b.vy)) b.vy = vy0;

        double start = b.y, peak = start;
        for (int i = 0; i < 300; i++) {
            if (i == releaseFrame) f.release();
            b.vy = f.cutVelocity(b.vy);
            b.gravityScale = hang ? f.gravityScale(b.vy) : 1.0;
            f.update(DT, b.grounded, b.vy);
            w.update(DT);
            peak = Math.min(peak, b.y);
            if (i > 4 && b.grounded) break;
        }
        return start - peak;
    }

    /** Horizontal distance covered by a full-hold jump taken at run speed. */
    static double measureArc(double vy0, boolean hang) {
        Physics.Body[] out = new Physics.Body[1];
        World w = floorWorld(out, 500);
        Physics.Body b = out[0];
        for (int i = 0; i < 40; i++) w.update(DT);

        JumpFeel f = new JumpFeel();
        b.vx = RUN;
        f.press();
        f.update(DT, b.grounded, b.vy);
        double startX = b.x, startY = b.y;
        if (f.consumeJump(b.vy)) b.vy = vy0;

        for (int i = 0; i < 300; i++) {
            b.vx = RUN;                                   // hold right
            b.vy = f.cutVelocity(b.vy);
            b.gravityScale = hang ? f.gravityScale(b.vy) : 1.0;
            f.update(DT, b.grounded, b.vy);
            w.update(DT);
            if (i > 4 && b.grounded) return b.x - startX;
        }
        return b.x - startX;
    }

    /** Bisect the launch velocity that reproduces a target apex with hang on. */
    static double calibrate(double targetApex) {
        double lo = -600, hi = -200;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            double apex = measureApex(mid, true, -1);
            if (apex > targetApex) lo = mid;   // too high: weaken the launch
            else hi = mid;
        }
        return (lo + hi) / 2;
    }

    static boolean coyoteAfter(int frames) {
        World w = new World();
        w.addTile(0, 300, 200, 360);                      // floor ends at x=200
        Physics.Body b = new Physics.Body(150, 278, 24, 44);
        w.addBody(b);
        JumpFeel f = new JumpFeel();
        for (int i = 0; i < 40; i++) { f.update(DT, b.grounded, b.vy); w.update(DT); }

        int guard = 0;
        while (b.grounded && guard++ < 600) {             // walk off the ledge
            b.vx = RUN;
            f.update(DT, b.grounded, b.vy);
            w.update(DT);
        }
        for (int i = 0; i < frames; i++) {
            b.vx = RUN;
            f.update(DT, b.grounded, b.vy);
            w.update(DT);
        }
        f.press();
        f.update(DT, b.grounded, b.vy);
        return f.consumeJump(b.vy);
    }

    static boolean bufferJump() {
        World w = new World();
        w.addTile(0, 300, 400, 360);
        Physics.Body b = new Physics.Body(100, 300 - 22 - 5, 24, 44);  // feet 5px up
        w.addBody(b);
        JumpFeel f = new JumpFeel();
        f.press();                                        // pressed while airborne
        boolean fired = false, onGround = false;
        for (int i = 0; i < 40; i++) {
            f.update(DT, b.grounded, b.vy);
            if (f.consumeJump(b.vy)) { fired = true; onGround = b.grounded; b.vy = JumpFeel.BASE_JUMP_V; }
            w.update(DT);
        }
        return fired && onGround;
    }

    public static void main(String[] args) {
        System.out.println("Jump feel - input forgiveness for the platformer");
        System.out.println("================================================");

        // --- the contract: what a full-hold jump does ---------------------
        double stockApex = measureApex(JumpFeel.BASE_JUMP_V, false, -1);
        double hangApex = measureApex(JumpFeel.BASE_JUMP_V, true, -1);
        double stockArc = measureArc(JumpFeel.BASE_JUMP_V, false);
        double hangArc = measureArc(JumpFeel.BASE_JUMP_V, true);
        double calibrated = calibrate(stockApex);
        double calibratedApex = measureApex(JumpFeel.CALIBRATED_JUMP_V, true, -1);

        System.out.printf("  stock apex        %.1f px  (2.3 cells)%n", stockApex);
        System.out.printf("  hang apex         %.1f px  (%+.1f)%n", hangApex, hangApex - stockApex);
        System.out.printf("  stock arc @200    %.0f px  (%.1f cells)%n", stockArc, stockArc / 32);
        System.out.printf("  hang arc  @200    %.0f px  (%.1f cells)  (%+.0f)%n",
            hangArc, hangArc / 32, hangArc - stockArc);
        System.out.printf("  calibrated launch %.1f  -> apex %.1f px%n", calibrated, calibratedApex);
        System.out.println();

        check("contract: hang never lowers the apex", hangApex >= stockApex,
            String.format("%.1f -> %.1f", stockApex, hangApex));
        check("contract: hang never shortens the arc", hangArc >= stockArc,
            String.format("%.0f -> %.0f px", stockArc, hangArc));
        // The generator's comment says "apex ~= 73px", which is the CONTINUOUS
        // value (420^2 / 2g = 73.5). The engine integrates in 1/60 steps and
        // actually reaches 70.0 - about 3.5px less than the comment implies.
        // It has never mattered because MAX_STEP_CELLS is conservative, but the
        // number that decides whether a step is climbable is this one, not the
        // one in the comment. What matters here is the margin over a 2-cell step.
        check("contract: a full-hold jump clears a 2-cell step (64px) with margin",
            stockApex > 64 + 4,
            String.format("discrete apex %.1f px vs continuous 73.5 (2-cell step = 64)",
                stockApex));
        check("calibration: CALIBRATED_JUMP_V restores the stock apex",
            Math.abs(calibratedApex - stockApex) < 1.5,
            String.format("%.1f vs %.1f px", calibratedApex, stockApex));
        check("calibration: the constant matches a fresh bisection",
            Math.abs(calibrated - JumpFeel.CALIBRATED_JUMP_V) < 1.0,
            String.format("bisected %.1f, constant %.1f",
                calibrated, JumpFeel.CALIBRATED_JUMP_V));

        // --- coyote time --------------------------------------------------
        System.out.println();
        boolean coyote4 = coyoteAfter(4);
        boolean coyote8 = coyoteAfter(8);
        check("coyote: a jump 4 frames after leaving a ledge still fires", coyote4);
        check("coyote: a jump 8 frames after leaving a ledge does not",
            !coyote8, "window is " + (int) (JumpFeel.COYOTE_TIME / DT) + " frames");

        // --- jump buffering ----------------------------------------------
        check("buffer: a press just before landing fires on the landing frame", bufferJump(),
            "window is " + (int) (JumpFeel.JUMP_BUFFER / DT) + " frames");

        // --- variable height ---------------------------------------------
        double tapApex = measureApex(JumpFeel.BASE_JUMP_V, true, 3);
        check("cut: a 3-frame tap is a short hop, not a full jump",
            tapApex < hangApex * 0.55,
            String.format("tap %.1f px vs full %.1f px", tapApex, hangApex));

        JumpFeel f2 = new JumpFeel();
        f2.update(DT, true, 0);
        f2.press();
        f2.consumeJump(0);
        f2.release();
        double cut1 = f2.cutVelocity(-400);
        double cut2 = f2.cutVelocity(-400);
        check("cut: applies once - repeated release events do not keep shaving it",
            cut1 > -400 && cut2 == -400,
            String.format("first %.0f, second %.0f", cut1, cut2));

        // --- no free extra jumps -----------------------------------------
        JumpFeel f3 = new JumpFeel();
        f3.update(DT, true, 0);
        f3.press();
        boolean groundJump = f3.consumeJump(0);
        f3.update(DT, false, 0);
        f3.press();
        boolean airJump = f3.consumeJump(-200);
        check("no double jump: a ground jump consumes the coyote window",
            groundJump && !airJump);

        JumpFeel f4 = new JumpFeel();
        f4.update(DT, true, 0);
        f4.update(DT, false, 0);          // walked off, coyote still alive
        f4.press();
        boolean bounceJump = f4.consumeJump(-300);   // launched upward by a bounce
        check("no double jump: cannot jump while already flying upward",
            !bounceJump, "vy=-300 from a stomp bounce");

        // --- the engine hook must be inert by default ---------------------
        System.out.println();
        int completed = 0;
        for (long seed = 201; seed <= 300; seed++) {
            LevelGen gen = new LevelGen(60, 14, seed);
            gen.generate();
            if (LevelValidator.validateGenerated(gen, 30.0)) completed++;
        }
        check("gate: generated levels still complete 100/100 (gravityScale defaults to 1.0)",
            completed == 100, completed + "/100");

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
