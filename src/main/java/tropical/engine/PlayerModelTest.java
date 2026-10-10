package tropical.engine;

import tropical.GameplayScreen;

/**
 * The bot that proves levels are completable must model the same player the game runs.
 *
 * <p><b>WHY THIS EXISTS.</b> `GameplayScreen` carries the comment "Tuned constants (match the validator's verified
 * values)" over its RUN_SPEED and JUMP_V, and nothing checked it. `LevelValidator` - the blind-traversal bot that
 * provides the empirical half of the generator's "completable by construction" claim - keeps its own copy of both
 * numbers, because the engine cannot reach up into the screen layer that owns them.
 *
 * <p><b>THE FAILURE THIS PREVENTS IS SILENT.</b> Tune the player's jump and leave the bot's copy alone, and the bot
 * keeps clearing levels using velocities the game no longer has. The gate stays green, the level is valid by every
 * measure the project has, and the game is unclearable. Nothing about that is visible from any existing check.
 *
 * <p><b>THIS IS THE PARITY PATTERN AGAIN, one layer in:</b> the engine against the release, and now the bot's model
 * against the game. Two numbers that must agree, asserted rather than assumed, and the assertion is in the engine
 * package because that is where LevelValidator's constants can be read.
 */
public class PlayerModelTest {
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
        verdict("the validator runs at the speed the player runs (" + (int) LevelValidator.RUN_SPEED + " vs "
                + (int) GameplayScreen.RUN_SPEED + " px/s)", LevelValidator.RUN_SPEED == GameplayScreen.RUN_SPEED);
        verdict("and jumps with the velocity the player jumps (" + (int) LevelValidator.JUMP_V + " vs "
                + (int) GameplayScreen.JUMP_V + " px/s)", LevelValidator.JUMP_V == GameplayScreen.JUMP_V);
        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
