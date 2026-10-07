#!/usr/bin/env bash
#
# check-jar-current.sh - does the committed jar still match the source it is supposed to be built from?
#
#   tools/check-jar-current.sh
#
# WHY THIS EXISTS AS ITS OWN FILE. `java -jar tropical-punch.jar` is what README.md tells people to run, so the
# committed jar IS the distribution for anyone who clones this repository. A jar built from older source is a
# silent bug: the fix is in the repository and not in the thing people download. That already happened here - the
# jar sat four days stale while source fixes accumulated - and nothing could see it.
#
# It was a block inside run-suites.sh. It is its own script because a check that can only be exercised by running a
# twelve-suite gate is a check that will not be exercised, and this one has now been fault-injected by hand twice
# to prove it works, which is this project's signal for building it into the path.
#
# Class bytes are deterministic for the same source and flags, so this compiles the source fresh and compares it
# class by class. It exits 1 on any difference, naming the first few.
set -u

JAVAC="${JAVAC:-/root/jdk-27+35/bin/javac}"
FX="${FX:-/root/javafx-sdk-27/lib}"
MODS="javafx.controls,javafx.graphics,javafx.media,javafx.swing"
JAR="${JAR:-tropical-punch.jar}"

cd "$(dirname "$0")/.." || exit 2
[ -f "$JAR" ] || { echo "  jar-current          FAIL   no $JAR" >&2; exit 1; }
JAR_ABS="$(pwd)/$JAR"
[ -d "$FX" ] || { echo "  jar-current          FAIL   no JavaFX at $FX" >&2; exit 2; }

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

"$JAVAC" --release 17 --module-path "$FX" --add-modules "$MODS" -d "$TMP" \
    $(find src/main/java -name '*.java') 2>/dev/null
[ -d "$TMP/tropical" ] || { echo "  jar-current          FAIL   the fresh compile produced nothing" >&2; exit 2; }

mkdir -p "$TMP/jar"
# AN ABSOLUTE PATH, and the extraction is CHECKED. A relative one resolved to the filesystem root once, so nothing
# was extracted, every comparison ran against a missing file, and the check announced "92 of 92 classes differ".
# A wholesale verdict like that is the tool talking about itself.
unzip -qo "$JAR_ABS" 'tropical/*' -d "$TMP/jar" || { echo "  jar-current          FAIL   could not read $JAR" >&2; exit 1; }

stale=0; shown=0
for f in $(cd "$TMP/tropical" && find . -name '*.class' | sort); do
  if ! cmp -s "$TMP/tropical/$f" "$TMP/jar/tropical/$f"; then
    stale=$((stale + 1))
    [ $shown -lt 3 ] && { echo "      differs: $f"; shown=$((shown + 1)); }
  fi
done
classes=$(cd "$TMP/tropical" && find . -name '*.class' | wc -l)

# AND THE RESOURCES, WHICH THIS DID NOT LOOK AT. The jar carries style.css and about ninety audio files beside
# the classes, and only the classes were compared - so a stylesheet or a sound that changed without the jar being
# rebuilt would pass this check. That is the same shape as the unwired list covering three of four: a check that
# covers part of what it claims.
#
# Both directions, because a file that is missing from the jar and a file that is stale in it are different faults.
res_stale=0; res_checked=0
for f in $(unzip -Z1 "$JAR_ABS" | grep -vE '^tropical/|^META-INF' | grep -v '/$'); do
  src=""
  [ "$f" = "style.css" ] && src="src/main/resources/style.css"
  case "$f" in audio/*) src="$f";; esac
  [ -n "$src" ] || continue
  res_checked=$((res_checked + 1))
  if [ ! -f "$src" ]; then
    echo "      in the jar but not in the tree: $f"; res_stale=$((res_stale + 1))
  elif ! cmp -s <(unzip -p "$JAR_ABS" "$f") "$src"; then
    echo "      stale in the jar: $f"; res_stale=$((res_stale + 1))
  fi
done
# and the other way: something in the tree the jar does not carry
for f in audio/*; do
  [ -f "$f" ] || continue
  unzip -Z1 "$JAR_ABS" | grep -qx "$f" || { echo "      in the tree but not in the jar: $f"; res_stale=$((res_stale + 1)); }
done
unzip -Z1 "$JAR_ABS" | grep -qx "style.css" || { echo "      style.css is in the tree but not in the jar"; res_stale=$((res_stale + 1)); }

if [ "$stale" -eq 0 ] && [ "$res_stale" -eq 0 ]; then
  echo "  jar-current          ok     $classes class(es) and $res_checked resource(s) in the jar match the tree"
  exit 0
fi
if [ "$res_stale" -gt 0 ]; then
  echo "  jar-current          FAIL   $res_stale resource(s) differ - rebuild with tools/make-jar.sh" >&2
fi
# ONLY WHEN THE CLASSES ARE THE PROBLEM. Without this guard a resource-only failure printed the class line too,
# saying "0 of 93 class(es) differ" underneath a resource failure - two messages for one fault, and the second one
# reads as a contradiction.
if [ "$stale" -gt 0 ]; then
  echo "  jar-current          FAIL   $stale of $classes class(es) differ - rebuild with tools/make-jar.sh" >&2
fi
exit 1
