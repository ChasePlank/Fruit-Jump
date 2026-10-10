package tropical.engine;

/**
 * Landing sounds are rate-limited, and in this repository they were not.
 *
 * <p><b>PORTED FROM THE SIBLING REPOSITORY, where the same fault was found by tools/tautologies.py reporting
 * LAND_COOLDOWN_TIME as switchable off with nothing noticing.</b> The cooldown was checked AFTER the cue had been
 * posted, and Sound.drain plays everything in the queue, so ten landings in one instant reached the speakers as ten
 * sounds while a headless run's log said "SFX land" once. A log line was making a claim about the audio that the
 * audio was not making.
 *
 * <p>It lives in the engine package because Sfx is package-private, so playSfx cannot be called from anywhere else.
 */
public class AudioRateTest {
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

        AudioSystem s = new AudioSystem();
        for (int i = 0; i < 10; i++) s.playSfx(AudioSystem.Sfx.LAND);
        int burst = s.drainPending().size();
        verdict("ten landings in one instant reach the backend as ONE sound, not ten (" + burst + ")", burst == 1);

        for (double t = 0; t < 0.2; t += GameLoop.DT) s.update(GameLoop.DT);
        s.playSfx(AudioSystem.Sfx.LAND);
        int after = s.drainPending().size();
        verdict("and a landing after the cooldown window does reach it (" + after + ")", after == 1);

        s.playSfx(AudioSystem.Sfx.JUMP);
        s.playSfx(AudioSystem.Sfx.JUMP);
        int jumps = s.drainPending().size();
        verdict("and other cues are not rate-limited by it (two jumps give " + jumps + ")", jumps == 2);

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
