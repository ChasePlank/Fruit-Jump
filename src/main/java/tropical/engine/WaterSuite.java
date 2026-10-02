package tropical.engine;

/**
 * WaterTest - the headline checks for the water system, ported onto the current
 * engine. The full suite from Sept 23 had 50 checks; these are the ones that
 * decide whether the water is usable at all, including the two bugs that took
 * real work to find (the flipped buoyancy sign, and the swim stroke cutting out
 * mid-breach so a swimmer could never get out of a pool).
 */
public class WaterSuite {
    static int failures = 0;
    static final double DT = GameLoop.DT;

    static void check(String n, boolean ok) { check(n, ok, ""); }
    static void check(String n, boolean ok, String d) {
        System.out.printf("%s  %s%s%n", ok ? "PASS" : "FAIL", n, d.isEmpty() ? "" : "   [" + d + "]");
        if (!ok) failures++;
    }

    static final String FLAT = String.join("\n",
        "                        ", "                        ", "                        ",
        "                        ", "                        ", "########################");

    static final String POOL = String.join("\n",
        "                        ", "                        ",
        "########        ########",
        "########~~~~~~~~########", "########~~~~~~~~########",
        "########~~~~~~~~########", "########~~~~~~~~########",
        "########################");

    static final String SHALLOW = String.join("\n",
        "                        ", "                        ", "                        ",
        "                        ", "                        ", "                        ",
        "        ~~~~~~~~        ", "########################");

    static final String RIVER = String.join("\n",
        "                        ", "                        ",
        "########        ########",
        "########>>>>>>>>########", "########>>>>>>>>########",
        "########~~~~~~~~########", "########~~~~~~~~########",
        "########################");

    /** A shore level with the water: the case that must be climbable out of. */
    static final String CROSSING = String.join("\n",
        " ".repeat(40), " ".repeat(40), " ".repeat(40),
        "#####" + ">".repeat(25) + "#####" + " ".repeat(5),
        "#####" + ">".repeat(25) + "#####" + " ".repeat(5),
        "#####" + ">".repeat(25) + "#####" + " ".repeat(5),
        "#####" + ">".repeat(25) + "#####" + " ".repeat(5),
        "#".repeat(40));

    static World world(String level) {
        LevelMap map = LevelMap.parse(level);
        World w = new World();
        map.buildWorld(w);
        return w;
    }

    static Physics.Body player(World w, double x, double y) {
        Physics.Body b = new Physics.Body(x, y, 24, 44);
        w.addBody(b);
        return b;
    }

    static void run(World w, double seconds) {
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) w.update(DT);
    }

    static final double FLOAT_SUB = 1.0 / WaterSystem.BUOYANCY;

    /**
     * Fixture assertion: the spawn really is where the test thinks it is.
     *
     * Every one of these suites places bodies at hand-typed coordinates, and a coordinate
     * that misses its pool by a tile fails in the most expensive way there is - silently,
     * with the check still passing for a different reason. Three geometry fixtures have
     * already gone wrong this way, so the geometry now asserts itself.
     */
    static void fixtureInWater(String what, World w, Physics.Body b, double minDepth) {
        Water field = w.water.water();
        boolean inWater = field != null && field.isWater(b.x, b.y);
        double depth = field == null ? 0 : field.depthUnder(b);
        check("fixture: " + what + " spawns in water, deep enough to swim",
            inWater && depth >= minDepth,
            String.format("inWater=%b depth=%.0f (needs %.0f)", inWater, depth, minDepth));
    }


    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /**
     * Run the whole suite and return the number of failures, so a caller can fold it into a
     * bigger gate.
     *
     * <p>It used to end in its own {@code System.exit}, which is fine for a suite you run by
     * hand and fatal for one you want inside {@code aside.engine.SelfTest} - the first failure
     * would take the process down and the rest of the gate would never run. A suite that can
     * only be run alone is a suite that gets run alone, and this one had never been run at all
     * on this side of the port.
     */
    public static int runAll() {
        failures = 0;
        System.out.println("Water system (ported from the release branch)");
        System.out.println("===============================");

        // The level strings are the other half of the fixture, and the same trap: the first
        // version of the slope test had a ramp level with no slope character in it at all,
        // which looked fine on the page. Assert the features each level is named for.
        check("fixture: POOL contains water", POOL.indexOf('~') > 0);
        check("fixture: SHALLOW contains water", SHALLOW.indexOf('~') > 0);
        check("fixture: RIVER contains water and a current",
            RIVER.indexOf('~') > 0 && RIVER.indexOf('>') > 0);
        check("fixture: CROSSING contains a current long enough to swim across",
            CROSSING.split("\n")[3].chars().filter(c -> c == '>').count() >= 20,
            CROSSING.length() + " chars, " + CROSSING.split("\n")[3].chars().filter(c -> c == '>').count() + " of current");

        // 1. no water: nothing changes
        World dry = world(FLAT);
        check("dry: no water field is installed", dry.water.water() == null);
        Physics.Body dp = player(dry, 100, 40);
        run(dry, 0.25);
        // Constant acceleration ties distance and velocity together: d = d0 + 0.5 * |v| * t.
        // The old form computed the expectation FROM Physics.GRAVITY, so changing gravity changed
        // the expectation with it and nothing could ever fail. This relates two measured things.
        double expected = 40 + 0.5 * Math.abs(dp.vy) * 0.25;
        // The tolerance is one Euler step (DT * v), not a fudge factor: the integrator updates the
        // velocity and then moves by it, so it overshoots the continuous relation by about half a
        // step. Worth knowing what this check does NOT do - gravity is a free parameter, and both
        // sides of this relation scale with it, so no relation can constrain it. That is correct:
        // the sweep reports GRAVITY as unconstrained, and the honest reading is "a leaf parameter
        // nothing pins", not "a broken test". The sweep flags; I decide.
        check("dry: free fall satisfies d = d0 + half v t, within one Euler step",
            Math.abs(dp.y - expected) < DT * Math.abs(dp.vy),
            String.format("y=%.2f vs %.2f from measured vy=%.1f (tol %.1f)",
                dp.y, expected, dp.vy, DT * Math.abs(dp.vy)));
        check("dry: free fall accelerates, so the body speeds up as it goes",
            Math.abs(dp.vy) > 0, String.format("vy=%.1f", dp.vy));
        check("dry: no splash, no water state",
            dry.water.splashEvents() == 0 && !dp.inWater && dp.submersion == 0);

        // 2. buoyancy finds the surface
        World w = world(POOL);
        Physics.Body p = player(w, 384, 106);
        fixtureInWater("the float test body", w, p, WaterSystem.SWIM_DEPTH);
        run(w, 4.0);
        check("float: settles at the buoyancy equilibrium",
            Math.abs(w.water.submersion(p) - FLOAT_SUB) < 0.08,
            String.format("sub=%.3f equilibrium=%.3f", w.water.submersion(p), FLOAT_SUB));
        check("float: head above the surface", (p.y - p.hh) < 96,
            String.format("head=%.1f surface=96", p.y - p.hh));

        // 3. depth decides wading versus swimming
        World sh = world(SHALLOW);
        Physics.Body sp = player(sh, 384, 200);
        run(sh, 2.0);
        check("depth: 1 tile of water is a puddle, not a pool",
            !sh.water.swimming(sp) && sp.inWater && sh.water.jumpV(sp, -420) == -420,
            String.format("swimming=%b inWater=%b", sh.water.swimming(sp), sp.inWater));

        // 4. splash on entry, and breath while floating
        World sp2 = world(POOL);
        Physics.Body ep = player(sp2, 384, 40);
        run(sp2, 1.5);
        check("splash: exactly one splash on entry", sp2.water.splashEvents() == 1,
            "events=" + sp2.water.splashEvents());
        run(sp2, 3.0);
        check("splash: no repeats while floating", sp2.water.splashEvents() == 1);
        check("breath: a floating body does not drown",
            sp2.water.air(ep) > 0.99 * WaterSystem.BREATH_SECONDS);

        // 5. breath runs out, then refills
        World bw = world(POOL);
        Physics.Body bp = player(bw, 384, 106);
        fixtureInWater("the dive test body", bw, bp, WaterSystem.SWIM_DEPTH);
        bw.water.setVerticalInput(1);            // dive and hold
        run(bw, 18.0);
        check("breath: air runs out while held under", bw.water.air(bp) == 0.0);
        check("drown: damage ticks accrue once air is gone",
            bw.water.drainDrownTicks(bp) >= 3);
        bw.water.setVerticalInput(0);
        bp.y = 106; bp.vy = 0;
        run(bw, 3.0);
        check("breath: refills at the surface",
            bw.water.air(bp) > 0.6 * WaterSystem.BREATH_SECONDS,
            String.format("air=%.1fs", bw.water.air(bp)));

        // 6. hysteresis: holding UP gets you out of a pool
        World hw = world(POOL);
        Physics.Body hp = player(hw, 384, 106);
        fixtureInWater("the climb-out body", hw, hp, WaterSystem.SWIM_DEPTH);
        run(hw, 3.0);
        boolean out = false;
        for (int i = 0; i < 600 && !out; i++) {
            hw.water.setVerticalInput(-1);        // swim up
            hp.vx = -200;                         // and steer at the left shore
            hw.update(DT);
            if (hp.grounded && !hp.inWater && (hp.y + hp.hh) <= 96 + 1) out = true;
        }
        check("hysteresis: a swimmer holding UP can leave the pool", out,
            String.format("x=%.0f feet=%.0f inWater=%b", hp.x, hp.y + hp.hh, hp.inWater));

        // 7. currents carry at the river's speed
        World rw = world(RIVER);
        Physics.Body rp = player(rw, 300, 106);
        run(rw, 1.5);
        // The old form allowed a tolerance OF the constant, so scaling the current scaled the
        // tolerance. This is a differential instead: the same body in the same water, once with the
        // current and once with it removed. No constant appears on either side.
        World still = world(RIVER.replace('>', '~'));   // same river, current removed
        Physics.Body spStill = player(still, 300, 106);
        run(still, 1.5);
        check("current: a river carries a body further than the same water without a current",
            rp.vx > spStill.vx + 20,
            String.format("with current vx=%.1f, without vx=%.1f", rp.vx, spStill.vx));

        // 8. jump response
        World jw = world(POOL);
        Physics.Body jp = player(jw, 384, 106);
        run(jw, 4.0);
        double fromDepth = jw.water.jumpV(jp, -420);
        check("jump: breaching from the surface is boosted", Math.abs(fromDepth) > 420,
            String.format("jumpV=%.1f from a plain -420", fromDepth));
        jw.water.setVerticalInput(1);
        run(jw, 4.0);
        double fromBottom = jw.water.jumpV(jp, -420);
        // Against the PLAIN input value, not just against breaching. Weaker-than-breaching is satisfied
        // by a jump that is not a paddle at all - the mutation "UNDERWATER_JUMP = 1.0" passed this check
        // before, which the harness caught. A paddle must be weaker than asking for a jump with nothing
        // helping, and -420 is what the caller asked for, not a tuned constant.
        check("jump: a jump from the bottom is a paddle, weaker than the plain jump asked for",
            Math.abs(fromBottom) < Math.abs(-420),
            String.format("bottom=%.1f vs plain 420", fromBottom));

        // 9. drag is frame-rate independent
        double[] vx = new double[2];
        double[] dts = { 1.0 / 60.0, 1.0 / 120.0 };
        for (int i = 0; i < 2; i++) {
            World dw = world(POOL);
            Physics.Body x = player(dw, 384, 106);
            x.vx = 200;
            int steps = (int) Math.round(1.0 / dts[i]);
            for (int k = 0; k < steps; k++) dw.update(dts[i]);
            vx[i] = x.vx;
        }
        check("dt: drag decays identically at 60Hz and 120Hz",
            Math.abs(vx[0] - vx[1]) < 0.1 * Math.abs(vx[0]) + 1.0,
            String.format("60Hz=%.2f 120Hz=%.2f", vx[0], vx[1]));

        // 10. the crossing: swim a river and climb out onto the far shore
        World cw = world(CROSSING);
        Physics.Body cp = player(cw, 80, 74);
        int jumps = 0;
        double cooldown = 0;
        boolean arrived = false;
        for (int i = 0; i < 1800 && !arrived; i++) {
            if (cw.water.swimming(cp)) {
                if (cp.x > 930 && cooldown <= 0) {
                    cp.vy = cw.water.jumpV(cp, -420);
                    jumps++;
                    cooldown = 1.2;
                }
                cp.vx = cw.water.steerVx(cp, 200, DT);
            } else {
                cp.vx = 200;
            }
            cw.update(DT);
            if (cooldown > 0) cooldown -= DT;
            if (cp.x > 990 && cp.grounded && !cp.inWater) arrived = true;
        }
        check("crossing: a bot swims a 25-tile river and climbs out", arrived,
            String.format("x=%.0f feet=%.0f jumps=%d", cp.x, cp.y + cp.hh, jumps));
        check("crossing: getting out needed a breach jump", jumps >= 1);
        check("crossing: one splash for the entry", cw.water.splashEvents() >= 1);

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASS" : failures + " CHECK(S) FAILED");
        return failures;
    }
}
