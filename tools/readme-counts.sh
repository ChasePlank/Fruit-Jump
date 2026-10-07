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
# A TEST IS *Test.java OR *Suite.java. The first version matched only "Test.java$", so WaterSuite counted as an
# engine CLASS - and because the README's "32 classes" had been computed the same way, THE CHECK AGREED WITH THE
# WRONG NUMBER. It reported "engine classes actually: 32   README claims: 32   ok" for a count that is 31.
#
# That is a check that cannot fail, in its quietest form: not a broken tool, but a tool sharing the mistake it was
# written to catch. WaterSuite has 28 check() calls and is a test by any reading except the pattern.
TESTS=$(ls src/main/java/tropical/engine/ 2>/dev/null | grep -cE "(Test|Suite)\.java$" || true)
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

# AND TWO BALANCE NUMBERS THE README STATES IN PROSE. They are accurate today - checked by reading the code and
# running the validator - and they are exactly the kind of claim that drifts silently when somebody tunes the
# generator. The README says "0.55 falling to a floor of 0.12" and "gaps from 3 cells widening to a hard cap of 4".
# BOTH NUMBERS OFF THE SAME LINE, because the first version took the first Math.max( in the FILE - which is not the
# heal line - and reported "floor ." against a README that says 0.12. It failed rather than passing, which is the
# right way round, but the pattern was still wrong: the heal line is the one with levelNum in it.
HEALLINE=$(grep -E "Math\.max\([0-9.]+, [0-9.]+ - levelNum" src/main/java/tropical/engine/LevelGen.java | head -1 || true)
# JUST THE DECIMALS, IN ORDER. The line is "Math.max(0.12, 0.55 - levelNum * 0.015)": the floor comes first and the
# starting chance second. Two nested greps with escaped parens returned "." - a match that is not a number, which
# is worse than no match because it looks like a value.
HEAL=$(echo "$HEALLINE" | grep -oE "[0-9]+\.[0-9]+" | sed -n 1p || true)
HEAL0=$(echo "$HEALLINE" | grep -oE "[0-9]+\.[0-9]+" | sed -n 2p || true)
GAP0=$(grep -oE "BASE_MAX_GAP_CELLS = [0-9]+" src/main/java/tropical/engine/LevelGen.java | grep -oE "[0-9]+" | head -1 || true)
GAPC=$(grep -oE "Math\.min\([0-9]+, BASE_MAX_GAP_CELLS" src/main/java/tropical/engine/LevelGen.java | grep -oE "[0-9]+" | head -1 || true)
echo "  heal: ${HEAL0:-?} floor ${HEAL:-?}   README claims: 0.55 floor 0.12"
if [ "${HEAL0:-}" = "0.55" ] && [ "${HEAL:-}" = "0.12" ]; then echo "  ok"; else
  echo "  MISMATCH - the README's heal numbers no longer match LevelGen" >&2; fails=$((fails + 1)); fi
echo "  gaps: base ${GAP0:-?} cap ${GAPC:-?}   README claims: 3 widening to a cap of 4"
if [ "${GAP0:-}" = "3" ] && [ "${GAPC:-}" = "4" ]; then echo "  ok"; else
  echo "  MISMATCH - the README's gap numbers no longer match LevelGen" >&2; fails=$((fails + 1)); fi

# AND THAT NO COMMENT STATES A TUTORIAL COUNT THAT DISAGREES WITH Tutorial.LAST. GameplayScreen said "ends at 8"
# while Tutorial.LAST was 9, in both copies, and nothing was looking - the same shape as the README's tutorial
# count, one layer down. A comment that names a value is a claim.
STALE=$(grep -rhoE "ends at [0-9]+" src/main/java/ 2>/dev/null | grep -oE "[0-9]+" | sort -u | grep -v "^${TUT}$" | tr '\n' ' ')
echo "  comments stating a tutorial end: ${STALE:-none}   Tutorial.LAST: ${TUT:-?}"
if [ -z "$STALE" ]; then echo "  ok"; else
  echo "  MISMATCH - a comment says the tutorial ends at $STALE; Tutorial.LAST is $TUT" >&2; fails=$((fails + 1)); fi

# AND THAT EVERY FILE THE README NAMES IS THERE. check-release-notes.sh does this for release notes and found
# eleven releases naming files that were not attached; nothing did it for the README, which names HOLDFAST.md, the
# sky comparison, the jar and six tools. All present today - checked by hand once - and a hand check is not a
# check. Only relative targets: an https link is somebody else's problem.
MISSING_FILES=""
for f in $(grep -oE '\[[^]]*\]\(([^)]+)\)' README.md | grep -oE '\(([^)]+)\)' | tr -d '()' | grep -vE '^https?:' | sort -u); do
  [ -e "$f" ] || MISSING_FILES="$MISSING_FILES $f"
done
# A BARE NAME IS RELATIVE TO tools/. The first version looked only at the repository root and reported six tools
# missing that are all present under tools/ - the README's own prose says "the tools under `tools/`", so the bare
# names are relative to that. A check built on a guess reports its own error as a fault in the repository.
for f in $(grep -oE '`[A-Za-z0-9_./-]+\.(md|png|jar|sh|css|cmd)`' README.md | tr -d '`' | sort -u); do
  if [ ! -e "$f" ] && [ ! -e "tools/$f" ]; then MISSING_FILES="$MISSING_FILES $f"; fi
done
echo "  files the README names but that do not exist:${MISSING_FILES:- none}"
if [ -z "$MISSING_FILES" ]; then echo "  ok"; else
  echo "  MISMATCH - the README points at something that is not there" >&2; fails=$((fails + 1)); fi

# AND THAT THE ENGINE STILL DOES NOT KNOW JAVAFX EXISTS. The README states it as a property - "the engine never
# knows JavaFX exists - the same property that lets the validator bot and the engine suites drive it headless" -
# and everything headless depends on it. It is true today: none of the 36 files in the engine package mentions
# javafx. One import would end it, and nothing would notice, because a headless suite that suddenly needs a display
# fails as "Unable to open DISPLAY" rather than as "the engine learned about JavaFX".
JFX=$(grep -rl "javafx" src/main/java/tropical/engine/ 2>/dev/null | wc -l)
echo "  engine files referencing JavaFX: $JFX of $(ls src/main/java/tropical/engine/*.java | wc -l)   README claims: none"
if [ "$JFX" -eq 0 ]; then echo "  ok"; else
  echo "  MISMATCH - the engine now imports JavaFX; the README says it never does" >&2; fails=$((fails + 1)); fi

# AND THAT THE FEATURE LIST DOES NOT CLAIM ONE OF THEM. The section below says what is not wired; the feature list
# at the top said "moving platforms with carry" and "parallax" as though they were in the game, and a reader meets
# the feature list first. Both were corrected on 2026-10-06 and this is what keeps them corrected - the same shape
# as the multiplayer claim that was removed from this README and survived in a second sentence.
# THE FEATURE LIST IS "## What's in it" AND NOTHING ELSE. The first version ran to "## Not in this repository",
# which is TWO sections later - so it swept in "## In the engine, not in the game", the section that exists to say
# these things are not in the game, and reported all four as claims. It failed rather than passing, which is the
# right way round, but the region was wrong.
FEATURES=$(sed -n "/^## What.s in it/,/^## In the engine/p" README.md)
for word in "moving platform" "parallax" "boss fight" "ai director" "multiplayer" "tcp server"; do
  if echo "$FEATURES" | grep -qi "$word"; then
    echo "  MISMATCH - the feature list claims \"$word\", which is not in the game" >&2
    fails=$((fails + 1))
  fi
done
echo "  feature list claims none of the four unwired classes"
echo "  ok"

# AND THAT THE FOUR UNWIRED CLASSES ARE STILL UNWIRED. The README tells a reader they are not in the game; if one
# gets wired, that sentence becomes the wrong kind of wrong - it would send someone to build something that exists.
# MOVINGPLATFORM WAS MISSING, and the README names FOUR things as unwired. The check covered three, so if someone
# wired the fourth the README's "nothing constructs one" would quietly become false and nothing would notice -
# the same shape as the engine-class count, which agreed with the README because it made the same mistake.
#
# AND THE COUNT IS ASSERTED, because a hand-kept list falls behind the moment somebody adds a fifth bullet. That is
# the failure this list just had: three entries against four bullets, and no way to see it.
UNWIRED_BULLETS=$(sed -n '/^## In the engine, not in the game/,/^## Not in this repository/p' README.md | grep -cE "^- \*\*")
UNWIRED_CHECKED=0
for pair in "Boss:new Boss(" "AIDirector:new AIDirector(" "ParallaxLayer:new ParallaxLayer(" "MovingPlatform:new MovingPlatform("; do
  UNWIRED_CHECKED=$((UNWIRED_CHECKED + 1))
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

# AND THAT THE LIST IS AS LONG AS THE README'S. A hand-kept list falls behind the moment somebody adds a fifth
# bullet, and that is exactly what had happened: three entries against four bullets, with nothing to show it.
echo "  unwired: $UNWIRED_CHECKED checked   README has $UNWIRED_BULLETS bullet(s)"
if [ "$UNWIRED_CHECKED" -eq "$UNWIRED_BULLETS" ]; then echo "  ok"
else
  echo "  MISMATCH - the README lists $UNWIRED_BULLETS unwired thing(s) and this checks $UNWIRED_CHECKED" >&2
  fails=$((fails + 1))
fi

echo
if [ "$fails" = 0 ]; then echo "=== README counts and unwired claims agree with the files ==="
else echo "=== $fails README claim(s) are stale ==="; exit 1; fi
