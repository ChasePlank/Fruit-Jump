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

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
