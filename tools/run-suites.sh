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
[ -n "${DISPLAY:-}" ] || echo "  note: DISPLAY is not set; the screen tests will fail to open a window"

pass=0; fail=0; failed_names=()

echo "compiling ..."
"$JAVAC" --module-path "$FX" --add-modules "$MODS" -cp "$OUT" -d "$OUT" \
    $(find src/main/java -name '*.java') 2>&1 | grep "error:" && { echo "  COMPILE FAILED" >&2; exit 1; }

echo "=== the tests ==="
for t in AudioTest CustomizeTest GameplayRobotTest MenuRobotTest MenuSmokeTest RoomsTest SaveLoadTest \
         ScreenshotTest TutorialTest WeaponsRobotTest \
         engine.CrackedPocketTest engine.DoorStressTest engine.WaterEnemyTest; do
  out=$(timeout 300 "$JAVA" --module-path "$FX" --add-modules "$MODS" \
        -cp "$OUT:src/main/resources" "tropical.$t" 2>&1)
  # VERDICTS ARE NOT STANDARDISED IN THIS REPOSITORY, and the first version of this runner assumed they were:
  # it looked for SUCCESS and FAILURE and reported three suites as "no verdict - did it run?" when all three had
  # printed PASS. ScreenshotTest prints "PASS: screenshot saved", CrackedPocketTest prints "PASS: ..." a line at a
  # time, and WaterEnemyTest prints "ALL PASS". All three were fine and the runner called them unknown.
  last=$(echo "$out" | grep -E 'SUCCESS|FAILURE|ALL PASS|PASS|FAIL|completed|Door boxes' | tail -1)
  case "$last" in
    *FAILURE*|*"FAIL "*) printf '  %-20s FAIL   %s\n' "${t##*.}" "$last"; fail=$((fail+1)); failed_names+=("$t") ;;
    "")                  printf '  %-20s ?      no verdict - did it run?\n' "${t##*.}"; fail=$((fail+1)); failed_names+=("$t") ;;
    *)                   printf '  %-20s ok     %s\n' "${t##*.}" "$last"; pass=$((pass+1)) ;;
  esac
done

echo "=== the readme ==="
if [ -x tools/readme-counts.sh ]; then
  rc_out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -eq 0 ]; then printf '  %-20s ok     %s\n' "readme-counts" "$(echo "$rc_out" | tail -1)"
  else printf '  %-20s FAIL   %s\n' "readme-counts" "$(echo "$rc_out" | tail -1)"; fail=$((fail+1)); failed_names+=("readme-counts"); fi
fi

echo "=== the jar ==="
if [ -f "$JAR" ]; then
  # A fresh --release 17 compile, compared against what is actually inside the jar. Class bytes are deterministic
  # for the same source and flags, so any difference means the jar was built from different source.
  TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
  JAR_ABS="$(pwd)/$JAR"
  "$JAVAC" --release 17 --module-path "$FX" --add-modules "$MODS" -d "$TMP" \
      $(find src/main/java -name '*.java') 2>/dev/null
  mkdir -p "$TMP/jar"
  # AN ABSOLUTE PATH TO THE JAR. The first version used "../../../$JAR" relative to $TMP/jar, which resolves to
  # /tropical-punch.jar - nothing was extracted, every cmp then failed against a missing file, and the check
  # reported "92 of 92 class(es) differ". A wholesale result like that is the tool talking about itself.
  #
  # AND THE EXTRACTION IS CHECKED. It was piped through `|| true` with the output discarded, which is how a
  # failure to extract at all became a confident verdict about the source.
  if ! unzip -qo "$JAR_ABS" 'tropical/*' -d "$TMP/jar"; then
    printf '  %-20s FAIL   could not read %s\n' "jar-current" "$JAR"; fail=$((fail+1)); failed_names+=("jar-current")
  else
  stale=0
  for f in $(cd "$TMP/tropical" && find . -name '*.class' 2>/dev/null); do
    cmp -s "$TMP/tropical/$f" "$TMP/jar/tropical/$f" || { stale=$((stale+1)); [ $stale -le 3 ] && echo "      differs: $f"; }
  done
  classes=$(cd "$TMP/tropical" && find . -name '*.class' | wc -l)
  if [ "$stale" -eq 0 ]; then
    printf '  %-20s ok     %s class(es) in the jar match a fresh build\n' "jar-current" "$classes"; pass=$((pass+1))
  else
    printf '  %-20s FAIL   %s of %s class(es) differ - rebuild with tools/make-jar.sh\n' "jar-current" "$stale" "$classes"
    fail=$((fail+1)); failed_names+=("jar-current")
  fi
  fi
else
  printf '  %-20s FAIL   no %s\n' "jar-current" "$JAR"; fail=$((fail+1)); failed_names+=("jar-current")
fi

echo
echo "=== $pass check(s) passed, $fail failed ==="
if [ $fail -gt 0 ]; then
  echo "failed: ${failed_names[*]}"
  exit 1
fi
