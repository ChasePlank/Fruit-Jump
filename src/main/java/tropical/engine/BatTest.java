package tropical.engine;

/**
 * The bat, which had NO checks in the release at all.
 *
 * <p><b>MOVED HERE FROM tropical.engine.SelfTest, WHICH THE RELEASE DOES NOT HAVE.</b> Six mutations - the stun, the
 * pursuit, the outrun rule, the dive's speed, its duration and its recovery - all came back NOT CAUGHT from every
 * suite in the release, and every one of them WAS checked, in a class only one edition can run. The bat was a whole
 * enemy verified in one place and verified nowhere in the other.
 *
 * <p>That is the seventh instance of the same gap, which is why the fix is a class with a main rather than a seventh
 * patch.
 */
public class BatTest {
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

        batsChaseWhatIsNear();
        aGroupOfBatsDoesNotPinYou();
        aBatPursuesWhatItCannotSwoop();
        aBatDivesAndThenCannotDiveAgainImmediately();
        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }

    static void batsChaseWhatIsNear() {
        for (long seed : new long[]{7L, 11L, 42L}) {
            double near = closestApproach(100, seed);
            double far = closestApproach(900, seed);
            verdict("bats: a bat 100px away closes in (seed " + seed + ", got within " + (int) near + "px)",
                    near < 70);
            // The far signal is small and it is worth saying so rather than picking a number that looks
            // decisive: at an aggro range of 260 the far bat stays 895-900px away across these seeds, and at
            // 10,000 it comes to 850-851. The threshold sits between the two measured populations, so a
            // failure here says which side it fell on.
            verdict("bats: a bat 900px away stays out of range (seed " + seed + ", stayed " + (int) far + "px)",
                    far > 870);
        }
    }
    /**
     * A GROUP OF BATS DOES NOT PIN YOU. Three bats around a player leave it standing most of the time.
     *
     * <p>REAGGRO_DELAY exists because of a real bug, and the comment on the constant says how it was found:
     * "the bat comes straight back and the player is flat ~1s out of every ~1.75s, which is a stun-lock in
     * practice even though every individual stun ends correctly. Found by test, not by reading." The mutation
     * sweep says the fix was never protected - zeroing it leaves every check green.
     *
     * <p>The measure is the fraction of frames the player spends stunned: 10% of ten seconds with three bats.
     *
     * <p><b>What this check actually protects is the GROUP BREAK-OFF, not REAGGRO_DELAY.</b> Setting
     * REAGGRO_DELAY to zero leaves the number at 10% - measured, not assumed - because the loop in World.update
     * already makes every bat break off when one connects, and that is what ends the stun-lock. Removing THAT
     * fails this check. So the constant is a second guard behind the one that does the work, and the check says
     * which is which rather than implying both are covered.
     */
    static void aGroupOfBatsDoesNotPinYou() {
        World w = new World();
        Physics.Body p = new Physics.Body(200, 100, 24, 44);
        w.addBody(p);
        w.playerBody = p;
        for (int i = 0; i < 3; i++) w.addBat(new Bat(210 + i * 14, 100, 5L + i));
        int stunned = 0, total = 0;
        for (double t = 0; t < 10.0; t += GameLoop.DT) {
            w.update(GameLoop.DT);
            total++;
            if (p.stunTimer > 0) stunned++;
        }
        int pct = (int) Math.round(100.0 * stunned / total);
        // BOTH BOUNDS, and the lower one is the whole reason this line changed. `pct <= 33` alone is satisfied by
        // ZERO - bats that never stun anybody pass a check about not being pinned by bats. Found by the sweep
        // reporting STUN_SECONDS as caught when doubled and halved but not when zeroed, which is the signature of a
        // bound that only looks one way; the same shape as Hookshot's stall guard, where `stalled > 0` accepted the
        // mutation that removed it.
        verdict("bats: three bats stun you some of the time (" + pct + "% of ten seconds) and do not pin you"
                + " (under a third)", pct >= 5 && pct <= 33);
    }
    /**
     * A bat closes on a player it cannot dive at, and cannot outrun them.
     *
     * <p><b>FOUND BY tools/tautologies.py:</b> `Bat.PURSUE_SPEED` could be set to zero with nothing noticing, and
     * the reason is in the existing check rather than in the constant. `batsChaseWhatIsNear` starts its bats 100px
     * away - INSIDE `SWOOP_RANGE` (150) - so they close by diving, and a bat that cannot pursue still gets there.
     * Between `SWOOP_RANGE` and `AGGRO_RANGE` (260) pursuit is the only thing moving it, and that band is what this
     * measures.
     *
     * <p><b>AND THE CLASS DOC'S OWN CLAIM, which nothing asserted either:</b> "PURSUE_SPEED must stay BELOW the
     * player's run speed (200). A bat that can outrun the player is a chase, not a tool - you cannot lead something
     * that simply catches you." That is a design rule with a reason attached, and it became checkable only
     * yesterday, when GameplayScreen.RUN_SPEED was made public for the player-model check.
     */
    static void aBatPursuesWhatItCannotSwoop() {
        // THE PLAYER IS ABOVE THE BAT, so the dive condition - which needs the player below - can never fire, and
        // pursuit is the only thing moving it. Measured: 199px at the start, 56px half a second later.
        World w = new World();
        Physics.Body p = new Physics.Body(200, 100, 24, 44);
        p.oneway = true;
        // THE PLAYER MUST NOT FALL, or the gap closes because the player descends onto the bat rather than because
        // the bat pursued - which is exactly how the first version of this check passed with PURSUE_SPEED set to
        // zero. The measurement has to be attached to the thing the constant moves.
        p.noGravity = true;
        w.addBody(p);
        w.playerBody = p;
        Bat b = new Bat(200, 300, 5L);
        w.addBat(b);
        w.update(GameLoop.DT);
        double start = Math.hypot(p.x - b.body.x, p.y - b.body.y);
        verdict("bats: one starts between its swoop range and its aggro range (" + (int) start + "px, swoop at "
                + (int) Bat.SWOOP_RANGE + ", aggro at " + (int) Bat.AGGRO_RANGE + ")",
                start > Bat.SWOOP_RANGE && start < Bat.AGGRO_RANGE);

        for (int i = 0; i < 30; i++) w.update(GameLoop.DT);
        double after = Math.hypot(p.x - b.body.x, p.y - b.body.y);
        // THE CLOSING AMOUNT, not the remaining distance: that is what the constant controls. Half a second at 130px/s
        // is 65px, and the measured value is 65 - which is also how the first version of this was caught being wrong,
        // when a falling player made the gap close for a reason that had nothing to do with the bat.
        verdict("bats: and pursuit closes the gap by " + (int) (start - after) + "px in half a second",
                start - after > 40);

        verdict("bats: and it cannot outrun the player, which is what makes it a tool rather than a chase ("
                        + (int) Bat.PURSUE_SPEED + " vs "
                        + (int) tropical.GameplayScreen.RUN_SPEED + " px/s)",
                Bat.PURSUE_SPEED < tropical.GameplayScreen.RUN_SPEED);
    }
    /**
     * A bat dives at a player below it, and then has to wait before diving again.
     *
     * <p><b>THE SWOOP WAS THE ONE MECHANIC NO CHECK OBSERVED.</b> `tools/tautologies.py` reported SWOOP_RECOVER and
     * HIT_COOLDOWN as unwatched, and setting either to TWENTY changed nothing - which is not "these do not matter"
     * but "nothing here has ever seen a dive". The reason the stun checks could not see it is that the bat stuns on
     * CONTACT: World sets the stun in its `else if (connected)` branch, so bats that cannot swoop at all still
     * pursue at 130px/s and touch the player, and the stun fraction reads the same.
     *
     * <p><b>AND THE FIXTURE IS THE BAIT WINDOW ITSELF.</b> A dive commits to a FIXED vector - the comment in Bat
     * calls it "the bait window" - so moving the player sideways immediately after the commit makes the dive miss,
     * which is the only path that sets `recover` and therefore the only way to reach SWOOP_RECOVER at all. Measured:
     * dive speed 330px/s, duration 0.52s, and the second dive 0.87s after the first ended.
     *
     * <p>All three are bounded in both directions, because a dive that never happens and a dive that lasts forever
     * are different bugs with the same "nothing noticed" signature.
     */
    static void aBatDivesAndThenCannotDiveAgainImmediately() {
        World w = new World();
        Physics.Body p = new Physics.Body(200, 220, 24, 44);   // below the bat: a dive needs the player lower
        p.oneway = true;
        p.noGravity = true;
        w.addBody(p);
        w.playerBody = p;
        Bat b = new Bat(200, 100, 5L);                          // 120px above, inside SWOOP_RANGE
        w.addBat(b);
        w.update(GameLoop.DT);

        Bat.State prev = b.state;
        double diveStart = -1, diveEnd = -1, secondDive = -1, fastest = 0;
        boolean baited = false;
        for (int i = 0; i < 60 * 8; i++) {
            double t = i * GameLoop.DT;
            // ONCE, and the flag is why: keying this on "diveStart is unset" moved the player EVERY FRAME of the
            // dive, because diveStart is only assigned when the transition is observed - so the player flew away at
            // 70px a frame and the bat never came back. The first version of this check failed for that reason and
            // not because anything was wrong with the bat.
            if (b.state == Bat.State.SWOOP && !baited) { p.x += 70; baited = true; }
            w.update(GameLoop.DT);
            if (b.state == Bat.State.SWOOP) fastest = Math.max(fastest, Math.hypot(b.body.vx, b.body.vy));
            if (b.state != prev) {
                if (b.state == Bat.State.SWOOP) {
                    if (diveStart < 0) diveStart = t;
                    else if (secondDive < 0) secondDive = t;
                }
                if (prev == Bat.State.SWOOP && diveEnd < 0) diveEnd = t;
                prev = b.state;
            }
        }

        verdict("bats: a dive commits at swoop speed, not at pursuit speed (" + (int) fastest + "px/s)", fastest > 250);
        double lasted = diveEnd - diveStart;
        verdict("bats: and the dive lasts about half a second (" + String.format("%.2fs", lasted) + ")", lasted > 0.3 && lasted < 0.8);
        double waited = secondDive - diveEnd;
        verdict("bats: and it dives again about a second later, not immediately and not never ("
                + String.format("%.2fs", waited) + ")", secondDive > 0 && waited > 0.4 && waited < 2.0);
    }
    static double closestApproach(double distance, long seed) {
        World w = new World();
        Physics.Body p = new Physics.Body(200, 100, 24, 44);
        w.addBody(p);
        w.playerBody = p;
        Bat bat = new Bat(200 + distance, 100, seed);
        w.addBat(bat);
        double closest = Double.MAX_VALUE;
        for (double t = 0; t < 6.0; t += GameLoop.DT) {
            w.update(GameLoop.DT);
            closest = Math.min(closest, Math.hypot(bat.body.x - p.x, bat.body.y - p.y));
        }
        return closest;
    }
}
