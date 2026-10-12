package tropical.engine;

/**
 * Particles: the pool that hands them out, the droplets themselves, and the emitters that spawn them.
 *
 * <p><b>WRITTEN BECAUSE THREE CLASSES HAD NO TEST IN EITHER EDITION.</b> A sweep of every engine class against
 * every test file in both repositories found `Emitter`, `Particle` and `ParticlePool` named by nothing - the same
 * sweep that found `MovingPlatform`, whose carry was carrying a rider twice. These three are the splash droplets: a
 * body entering water spawns a burst of 6 to 16 of them, and they are drawn.
 *
 * <p><b>AND IT FOUND A CRASH ON THE FIRST RUN.</b> `ParticlePool.acquire()` sets `nextIndex = particles.size()`
 * when it grows - one past the last index - so the NEXT acquire called `particles.get(size)` and threw. Measured
 * with a pool of two: the third acquire grew it to three, and the fourth threw
 * "IndexOutOfBoundsException: Index 3 out of bounds for length 3". The game's pool holds 200 and a splash is at
 * most sixteen, so it takes roughly thirteen splashes inside a particle's 0.25-0.6s lifetime to reach - and when
 * it does, `World.update` is called from the JavaFX animation pulse with nothing catching, so the frame loop dies
 * rather than the effect. Fixed in `ParticlePool`; the check below is what keeps it fixed.
 *
 * <p>Headless: no rendering, no display. Particles are arithmetic.
 */
public class ParticleTest {
    static int failures = 0;
    static int passes = 0;
    static final double DT = GameLoop.DT;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (ok) passes++; else failures++;
    }

    public static void main(String[] args) { System.exit(runAll() == 0 ? 0 : 1); }

    public static int runAll() {
        failures = 0; passes = 0;

        // ---- the pool ----
        ParticlePool pool = new ParticlePool(4);
        verdict("a new pool holds what it was asked for and has nothing active ("
                + pool.poolSize() + ", " + pool.activeCount() + " active)",
                pool.poolSize() == 4 && pool.activeCount() == 0);

        Particle a = pool.acquire();
        a.init(0, 0, 0, 0, 1.0, 2, 1, 1, 1);
        Particle b = pool.acquire();
        b.init(0, 0, 0, 0, 1.0, 2, 1, 1, 1);
        verdict("two acquires hand out two different particles", a != b);

        // THE POOL'S STATED PURPOSE: "avoids allocation during gameplay". An inactive particle is reused and the
        // pool does NOT grow - which is the whole reason the class exists rather than `new Particle()` at the call
        // site.
        //
        // WHAT IT DOES NOT PROMISE, and the first version of this asserted it: that you get the SAME particle back.
        // The cursor rotates, so the pool scans forward from wherever it left off and takes the first inactive one
        // it finds - `a` here, or `c` two places along. The property worth pinning is that the returned particle was
        // ALREADY IN THE POOL and the pool did not grow.
        a.deactivate();
        int before = pool.poolSize();
        Particle reused = pool.acquire();
        verdict("an inactive particle is reused rather than allocated (" + before + " -> " + pool.poolSize() + ")",
                pool.poolSize() == before && pool.getAll().contains(reused));

        // ---- AND THE CURSOR STAYS IN RANGE WHEN THE POOL GROWS ----
        // Every particle active, so every acquire grows the pool by one. Before the fix the SECOND of these threw.
        ParticlePool full = new ParticlePool(2);
        full.acquire().init(0, 0, 0, 0, 10, 1, 1, 1, 1);
        full.acquire().init(0, 0, 0, 0, 10, 1, 1, 1, 1);
        boolean threw = false;
        String threwWith = "";
        int grew = 0;
        for (int i = 0; i < 6; i++) {
            try {
                full.acquire().init(0, 0, 0, 0, 10, 1, 1, 1, 1);
                grew++;
            } catch (RuntimeException e) {
                threw = true; threwWith = e.toString(); break;
            }
        }
        verdict("a pool that has to grow still hands out particles (" + grew + " of 6, size "
                + full.poolSize() + (threw ? ", threw " + threwWith : "") + ")", !threw && grew == 6);
        verdict("and it grew by exactly the number it handed out (" + full.poolSize() + " = 2 + 6)",
                full.poolSize() == 8);

        // ---- the droplet ----
        Particle d = new Particle();
        d.init(10, 20, 30, -40, 1.0, 3, 0.85, 0.95, 1.0);
        verdict("a new particle is active and fully opaque", d.isActive() && d.palpha() == 1.0);
        verdict("and it carries the colour it was given, which is the emitter's",
                d.pr() == 0.85 && d.pg() == 0.95 && d.pb() == 1.0);
        d.update(0.25, 0);
        verdict("it moves by its velocity and fades as it ages (" + String.format("%.1f,%.1f alpha %.2f",
                d.px(), d.py(), d.palpha()) + ")", Math.abs(d.px() - 17.5) < 1e-9 && Math.abs(d.palpha() - 0.75) < 1e-9);
        d.update(1.0, 0);
        verdict("and past its lifetime it is inactive and transparent",
                !d.isActive() && d.palpha() == 0.0);
        Particle dead = new Particle();
        dead.init(0, 0, 0, 0, 1, 1, 1, 1, 1);
        dead.deactivate();
        double dx = dead.px();
        dead.update(1, 0);
        verdict("an inactive particle does not move when updated", dead.px() == dx);

        // ---- the emitters ----
        ParticlePool bp = new ParticlePool(64);
        Emitter burst = Emitter.burst(100, 50, 12)
                .speed(40, 80).angle(-3.0, -0.1).lifetime(0.3, 0.6).size(2, 4).gravity(600).color(0.9, 0.95, 1.0);
        burst.update(DT, bp);
        verdict("a burst emits exactly its count (" + bp.activeCount() + " of 12)", bp.activeCount() == 12);
        burst.update(DT, bp);
        verdict("and a one-shot burst emits nothing on the next update (" + bp.activeCount() + ")",
                bp.activeCount() == 12);

        ParticlePool sp = new ParticlePool(128);
        Emitter stream = Emitter.stream(0, 0, 60).speed(10, 20).lifetime(1, 2).size(2, 3);
        int steps = 30;
        for (int i = 0; i < steps; i++) stream.update(DT, sp);
        int expected = (int) Math.floor(steps * DT * 60);
        verdict("a stream emits at its rate over half a second (" + sp.activeCount() + " of about "
                + expected + ")", Math.abs(sp.activeCount() - expected) <= 1);

        ParticlePool tp2 = new ParticlePool(16);
        double[] follow = {5, 7};
        Emitter trail = Emitter.trail(() -> follow[0], () -> follow[1], 30).speed(1, 1).lifetime(1, 1).size(1, 1);
        follow[0] = 123; follow[1] = 456;
        // MORE THAN ONE UPDATE: a stream or trail emits while its accumulator has passed 1/rate, and at 30 a second
        // one frame of 1/60 is HALF an interval - so the first version of this checked after a single update and
        // found nothing, which reads as "the emitter is broken" rather than "the fixture was one frame short".
        for (int i = 0; i < 4; i++) trail.update(DT, tp2);
        boolean atTarget = false;
        for (Particle p : tp2.getAll()) {
            if (p.isActive() && p.px() == 123 && p.py() == 456) atTarget = true;
        }
        verdict("a trail emits from where its target is now, not where it was built ("
                + tp2.activeCount() + " emitted)", atTarget && tp2.activeCount() >= 2);

        // ---- and the emitters do not outrun the pool they are given ----
        // A burst larger than the pool is the case that reached the bug: the pool grows, and then it is asked again.
        ParticlePool small = new ParticlePool(4);
        Emitter big = Emitter.burst(0, 0, 20).speed(1, 2).lifetime(5, 5).size(1, 1);
        boolean burstThrew = false;
        try {
            big.update(DT, small);
        } catch (RuntimeException e) {
            burstThrew = true;
        }
        verdict("a burst four times the pool's size grows it and finishes (" + small.poolSize()
                + " particles, " + small.activeCount() + " active" + (burstThrew ? ", THREW" : "") + ")",
                !burstThrew && small.activeCount() == 20 && small.poolSize() == 20);

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
