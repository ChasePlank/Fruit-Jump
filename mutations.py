#!/usr/bin/env python3
"""
Mutation testing for the engine suites.

    python3 mutations.py              run every mutation
    python3 mutations.py water        only mutations whose label matches
    python3 mutations.py --list       show them

Why this file exists
--------------------
A check that has never been seen to fail is unverified. Twice in this project a suite passed
happily while the code it was supposed to protect was broken - a mutation that reintroduced a
real bug went undetected - and both misses were checks that compared a measurement to the
constant defining it, which can never fail.

That lesson is useless as a note ("remember to mutation-test"). It is useful as a script.
Each entry below breaks one invariant on purpose, compiles, runs the suite that should notice,
and reports whether it noticed. Restores the file from a copy - never `git checkout`, which
would also revert uncommitted work in the tree (that mistake cost me two finished edits).

Adding a check? Add a mutation for it here, and confirm it is caught.
"""
import pathlib, re, subprocess, sys, shutil, tempfile

JAVAC = "/root/jdk-27+35/bin/javac"
JAVA = "/root/jdk-27+35/bin/java"
MODULES = ["javafx.controls", "javafx.graphics", "javafx.media", "javafx.swing"]
FX = "/root/javafx-sdk-27/lib"
OUT = "/tmp/mutation-out"

# label | file | the line to break | what to break it to | the suite that must notice
MUTATIONS = [
    ("water: buoyancy sign/scale",      "engine/WaterSystem.java", "public static final double BUOYANCY = 1.35;", "public static final double BUOYANCY = 0.8;", "WaterTest"),
    ("water: swim-out hysteresis",      "engine/WaterSystem.java", "public static final double WADE_EXIT = 0.12;", "public static final double WADE_EXIT = 0.35;", "WaterTest"),
    ("water: drag removed",             "engine/WaterSystem.java", "public static final double DRAG_X = 2.5;", "public static final double DRAG_X = 0.0;", "WaterTest"),
    ("water: nothing is deep enough",   "engine/WaterSystem.java", "public static final double SWIM_DEPTH = 48.0;", "public static final double SWIM_DEPTH = 1000.0;", "WaterEnemyTest"),
    ("light: rim tiles go black",       "engine/Visibility.java",  "static final double MIN_LIGHT = 0.08;", "static final double MIN_LIGHT = 0.0;", "VisibilityTest"),
    ("light: memory as bright as sight","engine/Visibility.java",  "static final double MEMORY_LIGHT = 0.22;", "static final double MEMORY_LIGHT = 1.0;", "VisibilityTest"),
    ("paths: corner-cutting allowed",   "engine/PathGrid.java",    "return !isBlocked(nc, r) && !isBlocked(c, nr);", "return true;", "PathGridTest"),
    ("paths: diagonals cost the same",  "engine/PathGrid.java",    "public static final int COST_DIAGONAL = 14;", "public static final int COST_DIAGONAL = 10;", "PathGridTest"),
    ("terrain: grounding pass skipped", "engine/LevelGen.java",    "        groundFill(g);", "        // groundFill(g);", "GroundFillTest"),
    ("terrain: flooded gaps off",       "engine/LevelGen.java",    "    static final double FLOODED_GAPS = 0.35;", "    static final double FLOODED_GAPS = 0.0;", "GroundFillTest"),
    ("terrain: level number dropped",   "engine/LevelGen.java",    "return new LevelGen(60, 14, 1000L + levelNum, levelNum);", "return new LevelGen(60, 14, 1000L + levelNum);", "LevelSequenceTest"),
    ("parse: empty lines dropped",      "engine/LevelMap.java",    "        for (String line : raw) {", "        for (String line : raw) {\n            if (line.isEmpty()) continue;", "GroundFillTest"),
    ("rooms: no key for a locked door", "engine/RoomWorld.java",   "                    prevItems.add(Pickup.key(k[0], k[1]));", "                    // key skipped", "RoomsValidatorTest"),
    ("rooms: no doors at all",          "engine/RoomWorld.java",   "        if (pathIdx > 0 && pathIdx % 4 == 0 && pathIdx < mainPath.size() - 1) {", "        if (false) {", "RoomsValidatorTest"),
    ("nav: tile size halved",           "engine/RoomNav.java",     "public static final int TILE = 32;", "public static final int TILE = 16;", "RoomNavTest"),
    ("ai: ignores whether it can see",  "engine/World.java",       "e.updateTopDownNav(dt, nav, canSee, playerBody.x, playerBody.y);", "e.updateTopDownNav(dt, nav, true, playerBody.x, playerBody.y);", "TopDownAITest"),
    ("ai: no pursuit memory",           "engine/Enemy.java",       "static final double SIGHT_GIVE_UP = 1.6;", "static final double SIGHT_GIVE_UP = 0.0;", "TopDownAITest"),
    ("jump: no coyote time",            "engine/JumpFeel.java",    "public static final double COYOTE_TIME = 0.10;", "public static final double COYOTE_TIME = 0.0;", "JumpFeelTest"),
    ("jump: release-cut disabled",      "engine/JumpFeel.java",    "public static final double CUT_MULTIPLIER = 0.45;", "public static final double CUT_MULTIPLIER = 1.0;", "JumpFeelTest"),
]

ROOT = pathlib.Path(__file__).parent


def compile_tree():
    files = [str(p) for p in (ROOT / "src/main/java").rglob("*.java")]
    cmd = [JAVAC, "--module-path", FX, "--add-modules", ",".join(MODULES), "-d", OUT] + files
    return subprocess.run(cmd, capture_output=True, text=True)


SUITES = ["LevelSequenceTest", "RoomsValidatorTest", "WaterTest", "WaterEnemyTest", "GroundFillTest",
          "JumpFeelTest", "VisibilityTest", "PathGridTest", "RoomNavTest", "SlopeTest", "TopDownAITest",
          "DoorStressTest", "CrackedPocketTest"]


def find_constants():
    """Numeric constants the ENGINE declares and a TEST file mentions."""
    engine = (ROOT / "src/main/java/tropical/engine")
    tests = [p for p in (ROOT / "src/main/java/tropical").glob("*Test*.java")]
    test_text = "\n".join(t.read_text() for t in tests)
    decl = re.compile(r'static\s+final\s+(?:double|int)\s+([A-Z][A-Z_0-9]{2,})\s*=\s*(-?[0-9]+(?:\.[0-9]+)?)')
    out = []
    for f in engine.rglob("*.java"):
        src = f.read_text()
        for m in decl.finditer(src):
            name, value = m.group(1), m.group(2)
            if name not in test_text:
                continue                      # a constant no test mentions is a tuning knob, not a claim
            if float(value) in (0.0, 1.0):
                continue                      # nothing safe to change them to
            out.append((name, str(f.relative_to(ROOT / "src/main/java/tropical")), m.group(0), value))
    return out


def tautology_sweep(only):
    """For every constant a test mentions: mutate it, run every suite, see whether anything notices.

    A constant that a test file REFERENCES but that no failure follows from is the signature of a
    self-referential assertion - a check comparing a measurement to the constant that defines it. Two
    such were found by hand earlier; this is the same finding, mechanised.
    """
    pathlib.Path(OUT).mkdir(parents=True, exist_ok=True)
    results = []
    for name, rel, line, value in find_constants():
        if only and only not in name.lower():
            continue
        target = ROOT / "src/main/java/tropical" / rel
        original = target.read_text()
        if original.count(line) != 1:
            continue
        # BOTH DIRECTIONS. Mutating only upward is a blind spot: a bigger buffer, a deeper pool or a
        # roomier threshold never fails a check that asserts something still works, so such constants
        # read as UNCONSTRAINED when they are merely unconstrained in one direction. JUMP_BUFFER
        # reported unconstrained for exactly that reason - its check is behavioural and its message
        # merely quotes the constant - and SWIM_DEPTH for the opposite one.
        is_int = "." not in value
        if is_int:
            v = int(value)
            candidates = [v + 7, v - 5 if v - 5 > 0 else v + 3]
        else:
            f = float(value)
            candidates = [f + 7.3, f * 0.35 if f * 0.35 > 0.0001 else f + 11.7]
        for new_value in ["%d" % c if is_int else "%.4f" % c for c in candidates]:
            if new_value == value:
                continue
            backup = pathlib.Path(tempfile.mkstemp(suffix=".java")[1])
            shutil.copy(target, backup)
            try:
                target.write_text(original.replace(line, line.replace(value, new_value)))
                if compile_tree().returncode != 0:
                    continue
                failed = [s for s in SUITES
                          if subprocess.run([JAVA, "-cp", OUT, "tropical." + s],
                                            capture_output=True, text=True).returncode != 0]
                if failed:
                    results.append((name, "constrained", ",".join(f.replace("Test", "") for f in failed)))
                    break
            finally:
                shutil.copy(backup, target)
                backup.unlink()
        else:
            # The for-loop's else runs only when no direction caused a failure, so the row is emitted
            # exactly once. The first version could print a constant twice - UNCONSTRAINED and then
            # constrained - which made the tally wrong even when the verdicts were right.
            results.append((name, "UNCONSTRAINED", "neither direction failed anything"))
        if True:
            finally:
                shutil.copy(backup, target)
                backup.unlink()
    print("%-34s %-14s %s" % ("constant a test mentions", "verdict", "suites that noticed"))
    for name, verdict, who in results:
        print("%-34s %-14s %s" % (name, verdict, who))
    loose = [r for r in results if r[1] == "UNCONSTRAINED"]
    print()
    print("%d swept, %d UNCONSTRAINED" % (len(results), len(loose)))
    if loose:
        print("UNCONSTRAINED means a test file references it and nothing fails when it changes - check "
              "whether that test compares a measurement to the constant itself (a tautology).")
    return 0


def main():
    args = [a for a in sys.argv[1:]]
    if "--tautology" in args:
        rest = [a for a in args if a != "--tautology"]
        return tautology_sweep(rest[0].lower() if rest else None)
    if "--list" in args:
        for label, path, _, _, suite in MUTATIONS:
            print("%-38s %-22s %s" % (label, path, suite))
        return 0
    only = args[0].lower() if args else None
    selected = [m for m in MUTATIONS if not only or only in m[0].lower()]
    pathlib.Path(OUT).mkdir(parents=True, exist_ok=True)

    caught = missed = broken = 0
    for label, rel, old, new, suite in selected:
        target = ROOT / "src/main/java/tropical" / rel
        original = target.read_text()
        if original.count(old) != 1:
            print("%-38s %-20s ANCHOR GONE (code changed?)" % (label, suite))
            broken += 1
            continue
        backup = pathlib.Path(tempfile.mkstemp(suffix=".java")[1])
        shutil.copy(target, backup)
        try:
            target.write_text(original.replace(old, new))
            c = compile_tree()
            if c.returncode != 0:
                print("%-38s %-20s DID NOT COMPILE" % (label, suite))
                broken += 1
                continue
            r = subprocess.run([JAVA, "-cp", OUT, "tropical." + suite], capture_output=True, text=True)
            tail = [l for l in r.stdout.strip().split("\n") if l.strip()]
            ok = r.returncode != 0
            caught += ok
            missed += (not ok)
            print("%-38s %-20s %s  %s" % (label, suite, "CAUGHT" if ok else "*** NOT CAUGHT ***",
                                          tail[-1][:26] if tail else ""))
        finally:
            shutil.copy(backup, target)          # never `git checkout`: it eats uncommitted work
            backup.unlink()

    print()
    print("%d caught, %d missed, %d unusable" % (caught, missed, broken))
    if missed or broken:
        print("A miss means an invariant no test protects. An unusable entry means the code moved "
              "and the mutation needs updating.")
    return 0 if (missed == 0 and broken == 0) else 1


if __name__ == "__main__":
    sys.exit(main())
