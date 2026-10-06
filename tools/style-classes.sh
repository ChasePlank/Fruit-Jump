#!/usr/bin/env bash
#
# style-classes.sh - does every style class the code asks for actually exist in the stylesheet?
#
#   tools/style-classes.sh [repo-dir]      (default: the repository this script is in)
#
# Exits 1 if the code uses a class the stylesheet does not define. Reports the reverse (defined but never used)
# as a note, because that one is harmless.
#
# WHY THIS EXISTS. On 2026-10-04 the platformer's game-over screen turned out to put a class called "menu-item"
# on four labels while the stylesheet had NO RULE FOR IT, so they fell back to the default near-black text and
# two of the lines - "Press ENTER to Retry" and "Press ESC for Main Menu" - were nearly invisible. It was found
# by opening a screenshot, which is the only instrument that reads a colour.
#
# THAT IS NOT A THING TO FIND BY LOOKING. A class name is a string in the source and a selector in the CSS, and
# whether they meet is a set comparison - the kind of question a machine answers exactly and a person answers
# occasionally. The same shape as the name mismatches this project keeps producing: two files that are both
# valid, never meet, and raise no error.
#
# It looks at .java for getStyleClass().add("...") and at every .css under src/main/resources. A class used
# with a computed name cannot be seen and is not guessed at.
set -u
cd "${1:-$(dirname "$0")/..}" || exit 2
[ -d src/main/java ] || { echo "style-classes: no src/main/java here" >&2; exit 2; }

used=$(grep -rhoE 'getStyleClass\(\)\.add\("[A-Za-z0-9_-]+"\)' src/main/java --include=*.java 2>/dev/null \
       | sed 's/.*add("//; s/")//' | sort -u)
cssfiles=$(find src/main/resources -name '*.css' 2>/dev/null)
if [ -z "$cssfiles" ]; then
  # NO STYLESHEET IS NOT A FAULT, and the first version of this treated it as one. Aside has no stylesheet
  # because its screens are drawn on a canvas - but it still asks for "pause-overlay" in the FNAF port, because
  # the standalone that port came from HAS a stylesheet and that class is defined there. Removing it here would
  # make the two copies differ for no gain, which is the opposite of what the FNAF match check is for.
  #
  # So: a note and exit 0. The failure this script exists for is a stylesheet that is there and does not define
  # what the code asks for.
  echo "=== no stylesheet under src/main/resources ==="
  echo "  Every style class is a no-op here. That is normal for a canvas-drawn UI, but it means these names are"
  echo "  doing nothing and are kept only to match a copy that has a stylesheet:"
  [ -n "$used" ] && echo "  asked for: $(echo "$used" | tr '\n' ' ')"
  echo
  echo "=== nothing to compare ==="
  exit 0
fi
defined=$(cat $cssfiles | grep -oE '^\.[A-Za-z0-9_-]+' | tr -d '.' | sort -u)

missing=0
echo "=== style classes the code asks for ==="
for c in $used; do
  if echo "$defined" | grep -qx "$c"; then
    printf '  %-20s defined\n' "$c"
  else
    printf '  %-20s MISSING from the stylesheet - the label keeps its default colour\n' "$c"
    missing=$((missing+1))
  fi
done
[ -z "$used" ] && echo "  (none)"

unused=$(comm -13 <(echo "$used") <(echo "$defined") | tr '\n' ' ')
echo
if [ -n "$unused" ]; then
  echo "=== defined but never used (harmless, listed so it is not a surprise) ==="
  echo "  $unused"
  echo
fi

if [ $missing -gt 0 ]; then
  echo "=== $missing class(es) used but not defined ==="
  exit 1
fi
echo "=== every class the code asks for is defined ==="
