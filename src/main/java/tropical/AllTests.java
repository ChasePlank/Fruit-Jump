package tropical;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Every suite in one command.
 *
 *   java -cp out tropical.AllTests            all of them
 *   java -cp out tropical.AllTests egg climb  only suites whose name contains a term
 *
 * Each suite runs in its OWN JVM, on purpose. Several of them call System.exit, and
 * one crashing (an OOM in a stress sweep, say) should not take the report with it -
 * a runner that dies with its first failure tells you less than one that says which
 * suite died. It also means the suites cannot leak state into each other.
 *
 * Exit code is 0 only if every suite passed.
 */
public class AllTests {
    /** Suite name -> what it covers, so a failure reads as something. */
    static final String[][] SUITES = {
        {"LevelSequenceTest",    "the levels the game actually builds"},
        {"RoomsValidatorTest",   "keys before locks, along the room path"},
        {"WaterTest",            "swimming, breath, currents, the river crossing"},
        {"WaterEnemyTest",       "what water does to enemies, not just the player"},
        {"GroundFillTest",       "grounded terrain, the chamber, the parser, climbs"},
        {"JumpFeelTest",         "coyote time, buffering, cut, apex hang"},
        {"VisibilityTest",       "line of sight, falloff, explored memory"},
        {"PathGridTest",         "A* and flow fields"},
        {"RoomNavTest",          "pixel rooms to tile navigation, doors included"},
        {"TopDownAITest",        "enemies chase what they can see"},
        {"DoorStressTest",       "100 generated levels, end to end"},
        {"CrackedPocketTest",    "the bombable chamber, with physics"},
    };

    public static void main(String[] args) throws Exception {
        List<String> filters = new ArrayList<>(List.of(args));
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String cp = System.getProperty("java.class.path");

        int passed = 0, failed = 0;
        List<String> failures = new ArrayList<>();
        long start = System.nanoTime();

        System.out.println("Running " + SUITES.length + " suites" + (filters.isEmpty() ? "" : " (filtered)"));
        System.out.println("--------------------------------------------------------------------------------");
        for (String[] suite : SUITES) {
            String name = suite[0];
            if (!filters.isEmpty() && filters.stream().noneMatch(f -> name.toLowerCase().contains(f.toLowerCase()))) {
                continue;
            }
            long t0 = System.nanoTime();
            Process p = new ProcessBuilder(java, "-cp", cp, "tropical." + name)
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();
            double secs = (System.nanoTime() - t0) / 1e9;
            String last = out.strip().lines().reduce((a, b) -> b).orElse("(no output)");
            boolean ok = code == 0;
            if (ok) passed++; else { failed++; failures.add(name); }
            System.out.printf("%-4s %-20s %5.1fs  %s%n", ok ? "ok" : "FAIL", name, secs,
                    ok ? suite[1] : last);
        }

        double total = (System.nanoTime() - start) / 1e9;
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("%d passed, %d failed in %.1fs%n", passed, failed, total);
        if (passed + failed == 0) {
            // A filter that matches nothing is a typo, not a green run. Reporting
            // success for having run no tests is the worst possible answer.
            System.out.println("no suite matched " + filters + " - nothing ran");
            System.exit(1);
        }
        if (!failures.isEmpty()) {
            System.out.println("failed: " + String.join(" ", failures));
            System.exit(1);
        }
        System.out.println("ALL SUITES PASS");
    }
}
