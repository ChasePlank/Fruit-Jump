#!/usr/bin/env bash
#
# make-jar.sh - rebuild tropical-punch.jar from source, keeping everything else that is already in it.
#
#   tools/make-jar.sh [output-jar]        (default: tropical-punch.jar.new)
#
# WHY THIS EXISTS. The README says "java -jar tropical-punch.jar", and that file is committed - so it IS the
# distribution for anyone who clones the repo. It was last built on 4 October, BY HAND, and there is no script:
# the method was to extract the old jar, drop in freshly compiled classes, and re-jar it, preserving the JavaFX
# runtime, its natives and the audio that are already inside.
#
# That means every source fix since 4 October is invisible to anyone running the jar as documented. Three fixes
# went in today alone - the snack sprite and two overlapping tutorial signs - and the jar still shows a red heart
# under a sign that says SNACK.
#
# THE OLD JAR IS THE TEMPLATE, and it must be there for this to run. Everything except the game's own classes and
# resources is copied straight through, so the JavaFX runtime, the twenty natives and the audio are exactly what
# the working jar had.
#
# IT WRITES TO A NEW FILE BY DEFAULT and never overwrites the one it is reading, because the failure mode of a
# half-written jar in a repository whose README says to run it is worse than a stale one.
set -u

JAVA="${JAVA:-/root/jdk-27+35/bin/java}"
JAVAC="${JAVAC:-/root/jdk-27+35/bin/javac}"
JAR_TOOL="${JAR_TOOL:-/root/jdk-27+35/bin/jar}"
FX="${FX:-/root/javafx-sdk-27/lib}"

cd "$(dirname "$0")/.." || exit 2
TEMPLATE=tropical-punch.jar
OUT="${1:-tropical-punch.jar.new}"

[ -f "$TEMPLATE" ] || { echo "make-jar: no $TEMPLATE to use as a template" >&2; exit 2; }
[ -d "$FX" ] || { echo "make-jar: no JavaFX at $FX" >&2; exit 2; }
if [ "$OUT" = "$TEMPLATE" ]; then echo "make-jar: refusing to overwrite the template it is reading" >&2; exit 2; fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "unpacking $TEMPLATE ..."
unzip -q "$TEMPLATE" -d "$TMP" || exit 1

# Out with the old game, in with the new. Everything else in the template stays untouched.
rm -rf "$TMP/tropical" "$TMP/style.css" "$TMP/audio"

echo "compiling ..."
# --release 17, because the README promises Java 17+. Compiling with the JDK that happens to be here would stamp
# class files a Java 17 runtime cannot read, and nothing would notice until someone with Java 17 tried.
"$JAVAC" --release 17 --module-path "$FX" \
    --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
    -d "$TMP" $(find src/main/java -name '*.java') 2>&1 | grep "error:" && { echo "compile failed" >&2; exit 1; }

cp src/main/resources/style.css "$TMP/style.css"
cp -r audio "$TMP/audio"

echo "packing $OUT ..."
"$JAR_TOOL" --create --file "$OUT" --main-class tropical.Main -C "$TMP" . >/dev/null || { echo "jar failed" >&2; exit 1; }

# VERIFY, do not trust. A jar whose main class is missing comes back fine from `jar` and fails only when run.
SIZE=$(stat -c%s "$OUT")
echo "wrote $OUT ($((SIZE / 1048576)) MB)"
for need in tropical/Main.class style.css audio/arrow.wav; do
    unzip -l "$OUT" | grep -q "$need" || { echo "  MISSING from the jar: $need" >&2; exit 1; }
done
echo "  main class, stylesheet and audio are all inside"
echo
echo "NOW VERIFY IT RUNS, from a directory with nothing else in it, and look at the result:"
echo "  tools/play.sh   (or)   java -jar $OUT"
