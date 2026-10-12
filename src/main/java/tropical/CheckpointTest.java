package tropical;

import tropical.engine.Combat;
import tropical.engine.PlayerInventory;
import tropical.engine.SaveSystem;

/**
 * The checkpoint that carries a run into the next level.
 *
 * <p><b>WRITTEN BECAUSE THE RELEASE HAD NO CHECK FOR IT AND THE ENGINE DID.</b> A player reported "lives still reset
 * each level"; the engine fixed it by extracting six inline statements from its screen into `checkpointFor` so a
 * headless test could call them, and its mutation sweep is what showed why - changing the HP line to a flat 3 left
 * its gate green at 449 passed, 0 failed, because a screen needs a UiManager and a display.
 *
 * <p><b>AND FIXING THIS EDITION FOUND A SECOND FAULT IN THE SAME SIX LINES: `maxHP` was never written.</b>
 * `SaveSystem.applyState` reads `combat.maxHP = Math.max(3, state.maxHP)`, and the checkpoint left the field at its
 * default of 3 - so a lives cap raised by the heart-in-a-jar was reset at every level transition, in the edition
 * that ships. The check below is the one that would have caught it.
 *
 * <p>Headless: no screen, no display.
 */
public class CheckpointTest {
    static int failures = 0;
    static int passes = 0;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (ok) passes++; else failures++;
    }

    public static void main(String[] args) { System.exit(runAll() == 0 ? 0 : 1); }

    public static int runAll() {
        failures = 0; passes = 0;

        Combat combat = new Combat();
        combat.playerHP = 1;          // a rough run, one heart left
        combat.maxHP = 5;             // and a heart-in-a-jar raised the cap
        PlayerInventory inv = new PlayerInventory();
        inv.keys = 2;

        SaveSystem.GameState c = GameplayScreen.checkpointFor(7, combat, inv, 42.5);

        verdict("the checkpoint carries your health, not a fresh bar (hp " + c.playerHP + ")",
                c.playerHP == 1);
        verdict("and the lives cap with it, which a heart-in-a-jar raised (" + c.maxHP + ", was 5)",
                c.maxHP == 5);
        verdict("and the keys you are holding (" + c.keys + ")", c.keys == 2);
        verdict("and which level it is for (" + c.levelNum + ")", c.levelNum == 7);
        verdict("and it spawns at the level start rather than mid-level (x " + (int) c.playerX + ")",
                c.playerX < 0 && c.playerY < 0);
        verdict("and it remembers the clock (" + c.playTime + ")", c.playTime == 42.5);

        // THE CAP IS THE ONE THAT WAS BROKEN HERE, so it is checked through the RESTORE as well: applyState reads
        // maxHP and floors it at 3, which is exactly how the missing line turned into a reset cap rather than a
        // missing number.
        Combat restored = new Combat();
        new SaveSystem().applyState(c, new tropical.engine.Physics.Body(0, 0, 24, 44), restored, inv,
                new tropical.engine.World());
        verdict("and the cap survives a restore, not just the snapshot (" + restored.maxHP + ")",
                restored.maxHP == 5);

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
