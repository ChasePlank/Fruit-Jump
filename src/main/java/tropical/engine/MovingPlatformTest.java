package tropical.engine;

/**
 * The platform that carries you: the path it travels, and the rider it is supposed to hold.
 *
 * <p><b>WRITTEN BECAUSE THE MECHANIC HAD NO CHECK IN EITHER EDITION.</b> `LevelGen` places a moving platform every
 * third level - over a gap, one row above the walk, 96x16, amplitude 48, period 3s - the tutorial teaches it, the
 * screen draws it, and a sweep of all 31 engine classes against every test file in both repositories found
 * `MovingPlatform` named by NOTHING. It was one of four in that position; the other three are `Emitter`,
 * `Particle` and `ParticlePool` (see the note at the end of this file, which is a note rather than a check).
 *
 * <p><b>NO RENDERING NEEDED.</b> The path is arithmetic and the carry is a displacement, so this runs headless like
 * the other engine suites.
 *
 * <p><b>AND IT FOUND THE FERRY SLIDING OUT FROM UNDER ITS RIDER.</b> Measured through `World` before any fix: the
 * rider's offset from the platform drifted 24.4px across a 96px platform at the parameters the generator places -
 * 461.6px at amplitude 120, and 4598px on a circular path at amplitude 48, where the rider simply fell off. The rider
 * gained 1.4963x the platform's travel, the extra being exactly the platform's per-frame delta on alternating frames.
 *
 * <p><b>THE CAUSE WAS TWO CARRY MECHANISMS BOTH FIRING IN ONE FRAME.</b> `World` carries a rider by direct
 * displacement when `isCarrying` is true, and again in the collision response when the body lands on the mover's top
 * face. Both are needed - a body at rest generates no collision at all (tEntry=0 is rejected), and a platform fast
 * enough to outrun `isCarrying`'s 4px tolerance is only caught by the response - but on the frames where the resting
 * cycle's gravity dip is resolved, both fired and the rider was carried twice. The response now asks
 * `Physics.Body.carriedThisFrame` first.
 *
 * <p><b>THE BOUND THIS FILE ASSERTS IS ONE PIXEL, and that is derived rather than convenient.</b> With the fix the
 * residual drift is 0.333px on every path at the generator's amplitude - one frame of the gravity dip, which is what
 * a rider resting on any surface in this engine sits within - while the fault it replaces produced tens to thousands
 * of pixels. A bound a hair above the resting dip catches a return of the fault with three orders of magnitude to
 * spare and does not fire on the dip itself.
 *
 * <p>NOT ASSERTED, and why: at amplitude 120 a VERTICAL or CIRCULAR platform moves faster than `isCarrying`'s 4px
 * tolerance and loses its rider (22.95px and 5431px, unchanged by the fix). That is a real limitation of a
 * predicate-based carry, it is not reachable from the generator - which places only horizontal movers, 96x16, at
 * amplitude 48 and period 3.0 - and encoding it here as an expected failure would make this file assert the fault.
 */
public class MovingPlatformTest {
    static int failures = 0;
    static int passes = 0;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (ok) passes++; else failures++;
    }

    public static void main(String[] args) { System.exit(runAll() == 0 ? 0 : 1); }

    static final double X0 = 400, Y0 = 200, W = 96, H = 16, AMP = 48, PERIOD = 3.0;

    static MovingPlatform horizontal() {
        return new MovingPlatform(MovingPlatform.PathType.HORIZONTAL, X0, Y0, W, H, AMP, PERIOD);
    }

    /** A body whose feet are exactly on the platform's top face. */
    static Physics.Body standingOn(MovingPlatform m) {
        return new Physics.Body(m.x, m.aabb().y0 - 22, 24, 44);
    }

    public static int runAll() {
        failures = 0; passes = 0;

        // ---- the path, at the four points that pin period, amplitude AND direction ----
        MovingPlatform m = horizontal();
        // TWENTY STEPS PER PERIOD, so five steps is exactly a quarter of it. The first version stepped a fixed
        // 0.1s against a 3.0s period and landed on a sixth of a cycle, so all four quarter checks failed against a
        // platform that was behaving perfectly - the fixture is a function of the period, not of a convenient dt.
        double dt = PERIOD / 20.0;
        double[] xs = new double[5];
        xs[0] = m.x;
        for (int step = 1; step <= 20; step++) {
            m.update(dt);
            if (step % 5 == 0) xs[step / 5] = m.x;
        }
        verdict("a horizontal platform holds its line (y stays " + (int) Y0 + ")",
                Math.abs(m.y - Y0) < 1e-9);
        verdict("a quarter cycle puts it at its far reach - " + (int) xs[1] + "px",
                Math.abs(xs[1] - (X0 + AMP)) < 1e-6);
        verdict("half a cycle puts it back at its origin - " + (int) xs[2] + "px",
                Math.abs(xs[2] - X0) < 1e-6);
        verdict("three quarters puts it at its near reach - " + (int) xs[3] + "px",
                Math.abs(xs[3] - (X0 - AMP)) < 1e-6);
        verdict("and a whole period returns it exactly to where it started",
                Math.abs(xs[4] - X0) < 1e-6);

        // ---- it never leaves its path ----
        MovingPlatform b = horizontal();
        double worstOut = 0;
        for (int i = 0; i < 60; i++) {
            b.update(GameLoop.DT);
            worstOut = Math.max(worstOut, Math.abs(b.x - X0) - AMP);
        }
        verdict("and never travels beyond its amplitude (worst overshoot " + String.format("%.3f", worstOut) + "px)",
                worstOut <= 1e-9);

        // ---- THE VELOCITY IT REPORTS IS THE DISPLACEMENT IT MADE ----
        // This is the contract the carry is built on: World moves a rider by `delta * dt`, so a velocity that did
        // not match the platform's own movement would walk the rider away from the platform it is standing on.
        MovingPlatform v = horizontal();
        double worstVel = 0;
        for (int i = 0; i < 60; i++) {
            double px = v.x, py = v.y;
            double[] d = v.update(GameLoop.DT);
            worstVel = Math.max(worstVel,
                    Math.max(Math.abs(d[0] * GameLoop.DT - (v.x - px)), Math.abs(d[1] * GameLoop.DT - (v.y - py))));
        }
        verdict("the velocity it reports is the displacement it just made (worst error "
                + String.format("%.1e", worstVel) + "px)", worstVel < 1e-9);

        // ---- a circular path stays on its circle ----
        MovingPlatform c = new MovingPlatform(MovingPlatform.PathType.CIRCULAR, 500, 300, 64, H, 80, 4.0);
        double worstR = 0;
        for (int i = 0; i < 60; i++) {
            c.update(GameLoop.DT);
            worstR = Math.max(worstR, Math.abs(Math.hypot(c.x - 500, c.y - 300) - 80));
        }
        verdict("a circular platform stays on its circle (worst departure "
                + String.format("%.1e", worstR) + "px)", worstR < 1e-9);

        // ---- the carry predicate, which is geometry with a tolerance ----
        MovingPlatform p = horizontal();
        Physics.Body onTop = standingOn(p);
        Physics.Body above = new Physics.Body(p.x, p.aabb().y0 - 22 - 6, 24, 44);
        Physics.Body beside = new Physics.Body(p.x + W, p.aabb().y0 - 22, 24, 44);
        verdict("a body with its feet on the top face is being carried", p.isCarrying(onTop));
        verdict("a body 6px above the top face is not", !p.isCarrying(above));
        verdict("a body level with it but clear of its width is not", !p.isCarrying(beside));

        // ---- AND WHAT A RIDER ACTUALLY DOES, through the World ----
        // The negative control first: a platform that does not move must not move a rider at all. Without this the
        // positive case below would pass for a fixture that is measuring the world's own settling rather than a
        // carry.
        World still = new World();
        MovingPlatform parked = new MovingPlatform(MovingPlatform.PathType.HORIZONTAL, X0, Y0, W, H, 0.0, PERIOD);
        still.addMover(parked);
        Physics.Body idler = standingOn(parked);
        still.addBody(idler);
        double stillStartX = idler.x, stillStartPx = parked.x;
        for (int i = 0; i < 60; i++) still.update(GameLoop.DT);
        double stillDrift = Math.abs((idler.x - stillStartX) - (parked.x - stillStartPx));
        verdict("a platform that does not move does not move a rider (drift "
                + String.format("%.3f", stillDrift) + "px)", stillDrift == 0.0);

        World world = new World();
        MovingPlatform ferry = new MovingPlatform(MovingPlatform.PathType.HORIZONTAL, X0, Y0, W, H, AMP, PERIOD);
        world.addMover(ferry);
        Physics.Body rider = standingOn(ferry);
        world.addBody(rider);
        double startOffset = rider.x - ferry.x;
        double startFerry = ferry.x, startRider = rider.x;
        double worstDrift = 0, excursion = 0;
        int frames = (int) Math.round(PERIOD / GameLoop.DT);
        for (int i = 0; i < frames; i++) {
            world.update(GameLoop.DT);
            worstDrift = Math.max(worstDrift, Math.abs((rider.x - ferry.x) - startOffset));
            // THE EXCURSION, not the net displacement: a whole period brings the ferry back to where it started,
            // so `ferry.x - startFerry` at the end is zero by construction and the first version of this line
            // asserted that a platform which had carried a rider for three seconds had not moved.
            excursion = Math.max(excursion, Math.abs(ferry.x - startFerry));
        }
        double travel = excursion;
        verdict("the ferry actually travelled while carrying it (" + String.format("%.1f", travel) + "px)",
                travel > AMP * 0.9);
        // ONE PIXEL, NOT HALF THE PLATFORM. Before the fix this bound was W/2, because the fault it was written
        // against produced 24.4px - and a bound set for a broken engine stays a licence for it afterwards.
        // MEASURED now: 0.333px, which is the gravity dip. Anything at or above a pixel is a carry double-counting.
        verdict("and the rider stays on it - drift " + String.format("%.3f", worstDrift)
                + "px against a 1px bound",
                worstDrift < 1.0);

        // ---- AND THE PATHS THAT WERE WORSE, at the amplitude the generator uses ----
        // A circular mover dropped its rider entirely before the fix (4598px) and a vertical one drifted 1.675px.
        // Both are 0.333px now, so they are asserted rather than mentioned.
        for (MovingPlatform.PathType t : new MovingPlatform.PathType[]{
                MovingPlatform.PathType.VERTICAL, MovingPlatform.PathType.CIRCULAR}) {
            World w2 = new World();
            MovingPlatform mov2 = new MovingPlatform(t, X0, Y0, W, H, AMP, PERIOD);
            w2.addMover(mov2);
            Physics.Body r2 = standingOn(mov2);
            w2.addBody(r2);
            double sox = r2.x - mov2.x, soy = r2.y - mov2.y, worst2 = 0;
            for (int i = 0; i < frames; i++) {
                w2.update(GameLoop.DT);
                worst2 = Math.max(worst2, Math.hypot((r2.x - mov2.x) - sox, (r2.y - mov2.y) - soy));
            }
            verdict("a " + t.name().toLowerCase() + " mover holds its rider - drift "
                    + String.format("%.3f", worst2) + "px", worst2 < 1.0);
        }

        // ---- AND WHAT THIS DOES NOT COVER ----
        // `Emitter`, `Particle` and `ParticlePool` are in the same position: no test in either edition names them.
        // They are not asserted here because a particle is drawn and not computed - what is checkable about it is
        // that the screen draws it, which is a rendering check and belongs with the frames, not here.
        System.out.println("\nnot covered here: Emitter, Particle and ParticlePool - no test in either edition names");
        System.out.println("them, and what is checkable about a particle is that it is DRAWN, which is a frame.");
        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
