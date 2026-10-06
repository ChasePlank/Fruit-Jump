#!/usr/bin/env bash
#
# run-suites.sh - one command that runs everything this repository can check.
#
#   tools/run-suites.sh
#
# WHY THIS EXISTS. `aside` has had a single gate for months. This repository has had NONE - its twelve test classes
# were each run by hand, and nothing ran them together, so nothing was the thing that failed when the release
# drifted. It did drift: over two days six bugs were found here that the engine had already fixed, including a
# whole enemy that did not exist and a player who could not be hurt by their own bomb. A repository with no gate
# is a repository whose health depends on someone remembering.
#
# THREE KINDS OF CHECK, because they fail differently:
#
#   THE TESTS       twelve classes, each printing SUCCESS or FAILURE. Run with a display, since they drive the
#                   real JavaFX screens.
#   THE README      readme-counts.sh, which compares the counts in README.md against the files.
#   THE JAR         the one that matters most here and did not exist before. `java -jar tropical-punch.jar` is
#                   what the README tells people to run, so a committed jar built from older source is a silent
#                   distribution bug - the fix is in the repository and not in the thing people download. This
#                   compares the classes INSIDE the committed jar against a fresh compile of the source.
#
# Set JAVA / JAVAC / FX to override; the defaults are this machine's.
set -u

JAVA="${JAVA:-/root/jdk-27+35/bin/java}"
JAVAC="${JAVAC:-/root/jdk-27+35/bin/javac}"
FX="${FX:-/root/javafx-sdk-27/lib}"
MODS="javafx.controls,javafx.graphics,javafx.media,javafx.swing"
OUT="${OUT:-out}"
JAR=tropical-punch.jar

cd "$(dirname "$0")/.." || exit 2

[ -x "$JAVA" ]  || { echo "run-suites: no java at $JAVA - set JAVA=" >&2; exit 2; }
[ -d "$FX" ]    || { echo "run-suites: no JavaFX at $FX - set FX=" >&2; exit 2; }
# THE DISPLAY IS CHECKED, NOT ASSUMED PRESENT. Ten of these thirteen suites open a real window. When the Xvfb
# server died mid-session every one of them failed with a JavaFX stack trace and no verdict line, which reads as
# ten broken tests rather than one dead display - and I spent a round of this session believing the tests had
# broken. A missing display is an ENVIRONMENT failure; say so once, at the top, with the thing to run.
if [ -z "${DISPLAY:-}" ]; then
  echo "  NOTE: DISPLAY is not set. The screen tests cannot open a window and will fail." >&2
elif ! command -v xdpyinfo >/dev/null 2>&1; then
  echo "  note: no xdpyinfo to check \$DISPLAY=$DISPLAY against; carrying on"
elif ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
  echo "  NOTE: \$DISPLAY=$DISPLAY is set but nothing is serving it - start Xvfb, or the screen tests" >&2
  echo "        will fail for a reason that has nothing to do with the code." >&2
fi

pass=0; fail=0; failed_names=()

echo "compiling ..."
"$JAVAC" --module-path "$FX" --add-modules "$MODS" -cp "$OUT" -d "$OUT" \
    $(find src/main/java -name '*.java') 2>&1 | grep "error:" && { echo "  COMPILE FAILED" >&2; exit 1; }

# THE SUITE LIST IS DISCOVERED, NOT WRITTEN DOWN.
#
# The sibling gate in `aside` has said for weeks: "a list in this file would be stale the first time someone forgot
# it." Mine WAS such a list - thirteen names typed out - so the first test class anyone adds to this repository
# would simply not run, and the gate would report a smaller number and pass. It is the same defect as a check that
# cannot fail, one level up: a gate that cannot notice an addition.
#
# Everything with a `main` is discovered, and the few files that are tools rather than tests are named below AND
# PRINTED, so a new one is visible rather than silently absent.
TOOLS="Main Playthrough Sound"
echo "=== the tests ==="
suites=()
while IFS= read -r f; do
  base=$(basename "$f" .java)
  case " $TOOLS " in *" $base "*) continue ;; esac
  pkg=$(sed -n 's/^package \(.*\);/\1/p' "$f" | head -1)
  suites+=("$pkg.$base")
done < <(grep -rl "public static void main" src/main/java --include='*.java' | sort)
echo "  (${#suites[@]} discovered; not run here: $TOOLS)"

for full in "${suites[@]}"; do
  t="$full"
  out=$(timeout 300 "$JAVA" --module-path "$FX" --add-modules "$MODS" \
        -cp "$OUT:src/main/resources" "$t" 2>&1)
  rc=$?

  # THE EXIT CODE DECIDES. THE TEXT ONLY EXPLAINS.
  #
  # This runner spent an hour judging suites by what they PRINTED, and it was wrong twice over. First it looked for
  # SUCCESS and FAILURE and called three suites unknown, because verdicts here are not standardised - ScreenshotTest
  # prints "PASS: screenshot saved", CrackedPocketTest one PASS line per check, WaterEnemyTest "ALL PASS".
  #
  # Then, with those added, it still said "ok" for this:
  #
  #     DoorStressTest with the box-height assertion wrong   ->  prints "Door boxes: 59 with a bottom that does not
  #                                                              reach the floor, or no sprite"  AND EXITS 1
  #
  # A count of 59 faults, a non-zero exit, and the gate reported ok - because "Door boxes: 59" matches none of the
  # words a failure is spelled with. A suite that reports a NUMBER rather than a verdict is invisible to a parser
  # looking for verdict words, and the exit code was sitting there the whole time being authoritative.
  #
  # So: non-zero exit is a failure, whatever it printed. No matching line at all is also a failure - a suite that
  # printed nothing recognisable did not demonstrably run.
  # THE EXIT CODE DECIDES; THE TEXT ONLY EXPLAINS. This is the rule `aside`'s gate has used for
  # weeks, and I did not read it before writing this one. I had added a third rule - "no recognisable
  # verdict line is a failure" - and it has only ever produced FALSE failures: three suites that print
  # PASS, and WaterProbe, which asserts water shape and exits 1 on a fault but prints
  # "SHAPE OK: every pool is 2 rows deep..." and no verdict word my pattern knew.
  # A suite that exits 0 has passed. Asking it to also spell that in a word the parser recognises is
  # asking it to talk to the parser rather than to a person.
  last=$(echo "$out" | grep -E 'SUCCESS|FAILURE|ALL PASS|PASS|FAIL|completed|Door boxes|OK' | tail -1)
  [ -z "$last" ] && last=$(echo "$out" | grep -vE '^WARNING|^[[:space:]]*at |^$' | tail -1)
  # WHY IT FAILED, not just that it did. "(no output)" was wrong - a suite that died on a dead display printed a
  # full JavaFX stack trace. With no verdict line the first exception is the useful thing to show.
  why="$last"
  if [ -z "$why" ]; then
    why=$(echo "$out" | grep -m1 -E 'Exception|Error|Unable to' | cut -c1-70)
    why="${why:-no verdict line and no exception - did it run?}"
  fi
  if [ $rc -ne 0 ]; then
    printf '  %-20s FAIL   exit %s: %s\n' "${t##*.}" "$rc" "$why"
    fail=$((fail+1)); failed_names+=("$t")
  else
    printf '  %-20s ok     %s\n' "${t##*.}" "$last"; pass=$((pass+1))
  fi
done

echo "=== the readme ==="
if [ -x tools/readme-counts.sh ]; then
  rc_out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -eq 0 ]; then printf '  %-20s ok     %s\n' "readme-counts" "$(echo "$rc_out" | tail -1)"
  else printf '  %-20s FAIL   %s\n' "readme-counts" "$(echo "$rc_out" | tail -1)"; fail=$((fail+1)); failed_names+=("readme-counts"); fi
fi

echo "=== the jar ==="
# Its own script, so that it can be fault-injected without running the whole gate. See check-jar-current.sh
jar_out=$(tools/check-jar-current.sh 2>&1); jar_rc=$?
echo "$jar_out"
if [ $jar_rc -eq 0 ]; then pass=$((pass+1)); else fail=$((fail+1)); failed_names+=("jar-current"); fi

echo "=== the tools themselves ==="
# Every tool in this directory is a check, and a check that cannot fail is worse than no check because it is
# believed. self-test.sh breaks each one's subject and requires it to notice. Its output is filtered to the one
# summary line, because the rest is a list of things that were broken on purpose and restored.
st_out=$(tools/self-test.sh 2>&1); st_rc=$?
# ONLY THE FAILURES, matched in the STATUS COLUMN. Filtering on the word FAIL also matched a message that
# contained it, so a passing test whose message said "jar-current FAIL 1 of 93" was printed as a failure line.
# THE SUMMARY LINE, ALWAYS, plus any test whose STATUS is FAIL. Narrowing the filter to a status column also
# silenced the summary - which starts at column 0, not indented - so a passing run showed an empty section. An
# empty section is the same defect as a silent check: it looks the same whether it ran or not.
echo "$st_out" | grep -E '^=== |^ {2}.{0,34}FAIL' | sed 's/^/  /'
if [ $st_rc -eq 0 ]; then pass=$((pass+1)); else fail=$((fail+1)); failed_names+=("tool-self-tests"); fi

echo
echo "=== $pass check(s) passed, $fail failed ==="
if [ $fail -gt 0 ]; then
  echo "failed: ${failed_names[*]}"
  exit 1
fi
