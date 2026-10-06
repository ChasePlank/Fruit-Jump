#!/usr/bin/env bash
#
# readme-counts.sh - the README states file counts; this checks them against the files.
#
#   tools/readme-counts.sh
#
# WHY THIS EXISTS. This README has had wrong numbers twice: "25 classes" when the engine had 35, and then "35
# classes" when it had 32. Both times the number was correct when it was written and stopped being correct when
# files were added, and nothing looked. A count in a document is a measurement, and a measurement nobody re-runs
# is a claim.
#
# Rule 17 in this project: every factual claim in documentation traces to a run or a file, never to memory.
set -u
cd "$(dirname "$0")/.." || exit 2
fails=0

TOTAL=$(ls src/main/java/tropical/engine/*.java 2>/dev/null | wc -l)
TESTS=$(ls src/main/java/tropical/engine/ 2>/dev/null | grep -cE "Test\.java$" || true)
PROBE=$(ls src/main/java/tropical/engine/ 2>/dev/null | grep -cE "^WaterProbe\.java$" || true)
CLASSES=$((TOTAL - TESTS - PROBE))

CLAIM=$(grep -oE "engine \([0-9]+ classes" README.md | grep -oE "[0-9]+" || true)
echo "  engine classes actually: $CLASSES   README claims: ${CLAIM:-none}"
if [ "${CLAIM:-}" = "$CLASSES" ]; then
  echo "  ok"
else
  echo "  MISMATCH - update the README, or this check" >&2
  fails=$((fails + 1))
fi

# AND THE LINE COUNT IN THE UNWIRED SECTION, which drifted the same way - 326 when Boss.java was 326, then 339.
BOSS=$(wc -l < src/main/java/tropical/engine/Boss.java)
# No backticks in the pattern. The first version escaped them for the shell AND for grep, which matched nothing and
# reported "README claims: none" against a README that says the number plainly.
BCLAIM=$(grep -oE 'Boss. is [0-9]+ lines' README.md | grep -oE '[0-9]+' | head -1 || true)
echo "  Boss.java lines actually: $BOSS   README claims: ${BCLAIM:-none}"
if [ "${BCLAIM:-}" = "$BOSS" ]; then
  echo "  ok"
else
  echo "  MISMATCH - update the README, or this check" >&2
  fails=$((fails + 1))
fi

# AND THE TUTORIAL LEVEL COUNT, which was wrong when this check was written: the README said "eight hand-built
# levels" and Tutorial.LAST is 9. It had drifted the same way the others did - the tutorial grew a level and the
# sentence did not - and nothing was looking at it, which is the whole reason this file exists.
# The pattern is the FIELD, not "Tutorial.LAST" - the first version looked for a qualified name that does not appear
# in the file, found nothing, and reported "actually: ?" against a README that says 9. It failed rather than
# passing, which is the right way round, but the pattern was still wrong.
TUT=$(grep -oE "int LAST = [0-9]+" src/main/java/tropical/Tutorial.java | grep -oE "[0-9]+" | head -1 || true)
TCLAIM=$(grep -oE "[0-9]+ hand-built levels" README.md | grep -oE "[0-9]+" | head -1 || true)
echo "  tutorial levels actually: ${TUT:-?}   README claims: ${TCLAIM:-none}"
if [ -n "${TUT:-}" ] && [ "${TCLAIM:-}" = "$TUT" ]; then
  echo "  ok"
else
  echo "  MISMATCH - update the README, or this check" >&2
  fails=$((fails + 1))
fi

# AND THAT THE FOUR UNWIRED CLASSES ARE STILL UNWIRED. The README tells a reader they are not in the game; if one
# gets wired, that sentence becomes the wrong kind of wrong - it would send someone to build something that exists.
for pair in "Boss:new Boss(" "AIDirector:new AIDirector(" "ParallaxLayer:new ParallaxLayer("; do
  cls="${pair%%:*}"; call="${pair#*:}"
  # BY BASENAME, not by a pattern built from $cls. The first version interpolated the class name into a regex and
  # every file failed to match its own exclusion, so ParallaxLayer.java counted as a caller of itself.
  n=0
  while IFS= read -r f; do
    [ "$(basename "$f")" = "$cls.java" ] && continue
    case "$f" in *Test.java) continue;; esac
    n=$((n + 1))
  done < <(grep -rl -- "$call" src/main/java/ 2>/dev/null)
  if [ "$n" -eq 0 ]; then echo "  ok       $cls is still not constructed outside itself"
  else echo "  WIRED?   $cls is constructed in $n main-source file(s) - the README says nothing builds one" >&2; fails=$((fails + 1)); fi
done

echo
if [ "$fails" = 0 ]; then echo "=== README counts and unwired claims agree with the files ==="
else echo "=== $fails README claim(s) are stale ==="; exit 1; fi
