package tropical.engine;

/**
 * What a blast does to what is inside it, and to what is not.
 *
 * <p><b>WHY THIS EXISTS.</b> `tools/tautologies.py` reported that `Projectile.BLAST_DAMAGE_RANGE` could be set to
 * zero - switching the blast's damage off entirely - with nothing in 649 checks noticing. The class doc says
 * "Bombs: thrown arc, fuse timer, explosion radius damages enemies": the arc and the fuse were pinned, and the part
 * that makes a bomb a weapon rather than a wall-opener was not.
 *
 * <p><b>IT CALLS handleExplosion DIRECTLY</b>, which it can because it lives in this package - the same reason
 * CrackedPocketTest exists. That is not the layer-skipping mistake the arrow-versus-boss check made: this IS the
 * routing that decides who is in the blast, and the layer above it (the throw, the fuse, the landing collision) is
 * already pinned by the weapons block in SelfTest.
 *
 * <p><b>FIXED DISTANCES, NOT THE CONSTANT.</b> The far enemy is placed 160px away as a number the game ships with,
 * not as a multiple of BLAST_DAMAGE_RANGE, because a fixture computed from the constant moves with it and can never
 * fail - the tautology rule 32 is about, one level along. 160 is chosen to be comfortably outside the shipped 100
 * and comfortably inside what a doubled radius would be, so it catches both "off" and "twice as big".
 */
public class BlastTest {
    static int failures = 0;
    static int passes = 0;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (ok) passes++; else failures++;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** Run the whole test and return the number of failures, so a gate can fold it in. */
    public static int runAll() {
        failures = 0;
        passes = 0;
        World w = new World();

        Enemy near = new Enemy(300, 100, 24, 24, Enemy.KIND_SPIDER);
        Enemy far = new Enemy(300 + 160, 100, 24, 24, Enemy.KIND_SPIDER);
        w.addEnemy(near);
        w.addEnemy(far);

        // The player stands in their own blast. That is a design decision with a reason - it is what makes a placed
        // bomb a decision rather than a free wall-opener (Kinger, Sept 29) - and World raises an event for it rather
        // than writing the player's HP, so the flag is the thing to look at.
        Physics.Body player = new Physics.Body(320, 100, 24, 44);
        player.oneway = true;
        w.addBody(player);
        w.playerBody = player;

        w.handleExplosion(300, 100);

        verdict("a blast kills a spider standing in it", near.dead);
        verdict("and leaves a spider 160px away alone", !far.dead);
        verdict("and hurts the player standing in their own blast", w.playerBlastPending);
        verdict("the radius that breaks tiles reaches FURTHER than the one that hurts - the documented order, so a "
                + "wall can be opened from just outside the lethal circle",
                Projectile.BLAST_RADIUS > Projectile.BLAST_DAMAGE_RANGE);

        // AND A BLAST IS WORTH THREE ARROWS, which the constant's own comment claims and nothing asserted:
        // "A blast is worth three arrows because a bomb costs the player something - it has a fuse, and it hurts
        // them at their own feet." Measured THROUGH THE ROUTING - handleExplosion, not Boss.hit - because calling
        // hit() directly would test the weak-point window rather than the damage the game deals, which is the
        // mistake the arrow-versus-boss check made first.
        World bossWorld = new World();
        Boss boss = new Boss(300, 100, 64, 64);
        boss.setSeed(7L);
        bossWorld.boss = boss;
        Physics.Body bait = new Physics.Body(1500, 100, 24, 44);
        // MARKED AS THE PLAYER, because that is how a World finds one - the release's World looks for a body flagged
        // `oneway` and this fixture did not carry the flag, so the boss sat with no player to attack, never opened a
        // window, and the blast measured zero. Found by porting this test to the release, where it failed and the
        // sibling passed: the same fixture behaving differently in two engines is a fixture fault, not an engine one.
        bait.oneway = true;
        bossWorld.addBody(bait);
        bossWorld.playerBody = bait;
        boolean window = false;
        for (int i = 0; i < 60 * 60 && !window; i++) {
            bossWorld.update(1.0 / 60);
            window = boss.weakPointOpen();
        }
        verdict("a boss in a real world opens a window to measure against", window);
        double beforeBlast = boss.hp();
        bossWorld.handleExplosion(boss.body.x, boss.body.y);
        double took = beforeBlast - boss.hp();
        // Three arrows of three is nine; a band rather than the number, so the magnitude stays the designer's and
        // the CLAIM - a bomb hits harder than a single shot, and not absurdly harder - is what is pinned.
        verdict("a blast is worth about three arrows off a boss - it took " + (int) took
                + " against one arrow's 3", took > 6 && took < 15);

        bossVolleyHurtsThePlayer();

        anArrowHittingABossTakesItsHealth();

        theWindowTakesTwoHitsAndThenCloses();

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }

    /**
     * AND WHAT THE BOSS DOES TO YOU, which is a blast as well: its volley lobs a charge.
     *
     * <p>This lives here rather than in the suite because the volley IS a blast - the boss lobs a bomb, whose blast
     * already raises `playerBlastPending` for a body marked `oneway` - and because this class is ported to the
     * release, where the boss was frozen until 10 October and its volley could therefore never fire at all.
     *
     * <p>It was three fixtures wrong before it was right, and every one of those was a fact about the game:
     * the volley needs PHASE 2 (chooseAttack returns VOLLEY only when phase >= 2), the boss WALKS TO THE PLAYER
     * before choosing (so a stationary body is always in the close branch, CHARGE or JUMP_SLAM), and a Boss's
     * Random is TIME-SEEDED (so the check passed alone and failed in the gate).
     */
    static void bossVolleyHurtsThePlayer() {
        World w = new World();
        Physics.Body p = new Physics.Body(1500, 100, 24, 44);   // far away, so the boss chooses to lob at it
        p.oneway = true;
        p.noGravity = true;
        w.addBody(p);
        w.playerBody = p;
        Boss boss = new Boss(300, 100, 64, 64);
        // SEEDED, because a Boss's Random is time-seeded and its attack choice is therefore not reproducible: this
        // check passed on its own and failed inside the gate on its first run. The class provides setSeed for exactly
        // this, and a flaky check is worse than no check.
        boss.setSeed(7L);
        w.setBoss(boss);                                        // THE METHOD, which wires the volley

        // IT ONLY LOBS FROM ITS SECOND PHASE - `chooseAttack` returns VOLLEY only when `phase >= 2` - so the boss has
        // to be damaged first, and that is FIXTURE work rather than the thing under test: how damage reaches the boss
        // is the arrow check's job. The first version of this check drove an undamaged boss for sixty seconds,
        // watched it charge, and reported no volley - a fixture that had not set up its own premise.
        // AND THE PLAYER HAS TO KEEP RUNNING, which is the whole shape of the attack. In IDLE the boss WALKS toward
        // the player before choosing, so against a stationary body it is always inside 250px and always picks the
        // close branch - CHARGE or JUMP_SLAM. The volley is the answer to a player who keeps their distance, and the
        // first two versions of this check never saw one for that reason: the fixture was standing still.
        for (int i = 0; i < 60 * 120 && boss.phase() < 2; i++) {
            p.vx = 200;                                  // the player's own run speed, fleeing
            w.update(GameLoop.DT);
            if (boss.weakPointOpen()) boss.hit(3.0);
        }
        verdict("boss: it reaches its second phase once it takes damage (phase " + boss.phase() + ")", boss.phase() >= 2);

        Projectile charge = null;
        for (int i = 0; i < 60 * 60 && charge == null; i++) {
            p.vx = 200;
            w.update(GameLoop.DT);
            for (Projectile pr : w.projectiles) {
                if (pr.type == Projectile.Type.BOMB) { charge = pr; break; }
            }
        }
        verdict("boss: its volley lobs a charge at the player (one in flight: " + (charge != null) + ")",
                charge != null);
        if (charge == null) return;

        // AND THE CHARGE IS A WEAPON, not a decoration: put the player on it and let the fuse run out.
        p.x = charge.x;
        p.y = charge.y;
        boolean hurt = false;
        for (int i = 0; i < 60 * 3 && !hurt; i++) {
            w.update(GameLoop.DT);
            hurt = w.playerBlastPending;
        }
        verdict("boss: and a player standing on that charge is flagged for damage", hurt);
    
    }

    /**
     * An arrow that reaches a boss takes health off it.
     *
     * <p><b>THE CHECK THE MUTATION HARNESS SAID WAS MISSING.</b> Setting World's ARROW_DAMAGE from 3.0 to 0.0 - a
     * weapon that does nothing - was caught by nothing in 642 checks. The reason is structural: Boss.hit() is
     * reachable from here and ARROW_DAMAGE is not, because the constant is package-private to
     * aside.games.fruitjump.engine. So this goes through the ROUTING rather than around it - a real World, a real
     * Boss, a real Projectile - which is the only way a check can be about the damage the game deals.
     *
     * <p><b>AND IT IS RELATIONAL, ON PURPOSE.</b> "the health fell" rather than "the health fell by ARROW_DAMAGE":
     * the second compares a measurement to the constant that defines it and passes whatever that constant becomes,
     * which is exactly the fault that made this check necessary.
     *
     * <p>The player stands 1200px away for the same reason as the check above: the boss only opens its mark after
     * an attack, and up close it never picks one.
     */
    static void anArrowHittingABossTakesItsHealth() {
        World w = new World();
        Physics.Body player = new Physics.Body(1200, 500, 24, 44);
        w.addBody(player);
        w.playerBody = player;
        Boss b = new Boss(200, 400, 64, 64);
        b.setSeed(7L);
        w.boss = b;

        boolean opened = false;
        for (int i = 0; i < 60 * 40 && !opened; i++) {
            w.update(GameLoop.DT);
            if (b.weakPointOpen()) opened = true;
        }
        verdict("a boss in a real world opens its weak point within forty seconds", opened);
        if (!opened) return;

        double before = b.hp();
        // An arrow spawned inside the boss's own box, so the routing's overlap test is the thing under test and
        // not the flight. One frame is enough: the collision is checked on the projectile's update.
        w.projectiles.add(Projectile.arrow(230, 430, 1));
        w.update(GameLoop.DT);
        verdict("an arrow overlapping a boss takes health off it (fell, rather than merely changed)", b.hp() < before);
    }

    /**
     * The window takes two hits and then closes, and the next one is fresh.
     *
     * <p><b>FOUND BY A MUTATION NOBODY NOTICED.</b> `MAX_HITS_PER_WINDOW = 2` could be set to 99 with every check
     * green: the fight's whole shape is that the armour limits how much you can do while the mark is lit, and nothing
     * asserted it. Thirty hit points at three a hit is ten hits; the armour is what makes that a fight rather than an
     * arithmetic problem, which is what the constant's own comment says.
     *
     * <p>BOTH HALVES ARE CHECKED, because either alone would pass on a broken boss: a window that takes two hits and
     * never resets is a boss you can only hit twice, and a window that resets after every hit has no armour at all.
     * The counts are printed rather than only compared, so a failure says which way it went.
     */
    static void theWindowTakesTwoHitsAndThenCloses() {
        World w = new World();
        Physics.Body p = new Physics.Body(1500, 100, 24, 44);
        p.oneway = true;
        p.noGravity = true;
        w.addBody(p);
        w.playerBody = p;
        Boss b = new Boss(300, 100, 64, 64);
        b.setSeed(7L);
        w.boss = b;

        boolean opened = false;
        for (int i = 0; i < 60 * 60 && !opened; i++) {
            w.update(GameLoop.DT);
            opened = b.weakPointOpen();
        }
        verdict("a weak-point window opens, to measure the armour against", opened);
        if (!opened) return;

        int landed = 0;
        for (int i = 0; i < 4; i++) if (b.hit(3.0)) landed++;
        verdict("and the window takes exactly two hits, not four (" + landed + " of four landed)", landed == 2);

        int second = 0;
        for (int i = 0; i < 60 * 60 && second == 0; i++) {
            w.update(GameLoop.DT);
            if (b.weakPointOpen()) for (int k = 0; k < 4; k++) if (b.hit(3.0)) second++;
        }
        verdict("and the next window is fresh rather than the same exhausted one (" + second + " landed)", second == 2);
    }

}
