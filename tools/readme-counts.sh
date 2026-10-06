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

echo
if [ "$fails" = 0 ]; then echo "=== README counts agree with the files ==="
else echo "=== $fails README count(s) are stale ==="; exit 1; fi
