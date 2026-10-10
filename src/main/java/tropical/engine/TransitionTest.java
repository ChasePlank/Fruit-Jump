package tropical.engine;

/**
 * A room transition takes about a third of a second, and it does end.
 *
 * <p><b>FOUND BY tools/tautologies.py:</b> `ScreenManager.TRANSITION_TIME` could be set to anything with nothing
 * noticing - the last unwatched constant the sweep could reach. It is the fade between rooms in Rooms mode, which is
 * the architecture the real game is being built towards, and both of its failure modes are player-visible: a
 * transition of zero frames is a hard cut where the room changes before anyone can see it happen, and one that never
 * ends is a game that has stopped responding.
 *
 * <p><b>Both bounds, and the room change is asserted too</b> - a timer that counts down correctly while the player
 * stays in the old room would pass a duration check and be useless.
 */
public class TransitionTest {
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

        Physics.Body player = new Physics.Body(100, 100, 24, 44);
        ScreenManager m = new ScreenManager(player);
        m.add(new Room("a", 24, 9));
        m.add(new Room("b", 24, 9));
        m.start("a");
        World w = new World();

        verdict("the manager starts in the room it was told to", "a".equals(m.currentRoomId()));

        m.triggerTransition("b", 2, w);          // 2 = east
        double elapsed = 0;
        boolean finished = false;
        for (int i = 0; i < 60 * 3 && !finished; i++) {
            m.update(GameLoop.DT, w);
            elapsed += GameLoop.DT;
            finished = !m.transitioning;
        }

        verdict("a room transition finishes (" + String.format("%.2fs", elapsed) + ")", finished);
        verdict("and takes about a third of a second rather than being instant ("
                + String.format("%.2fs", elapsed) + ")", elapsed > 0.2 && elapsed < 0.6);
        verdict("and the player is in the next room afterwards (" + m.currentRoomId() + ")",
                "b".equals(m.currentRoomId()));

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
