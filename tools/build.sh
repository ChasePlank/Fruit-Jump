#!/usr/bin/env bash
# Compile the tree, and FAIL LOUDLY if it does not.
#
#   tools/build.sh [outdir]      default /root/workspace/tp-out
#
# Why this exists: three times now a compile check written as `javac ... | grep -c error` has printed
# "errors: 0" while javac was absent, because grep counts lines and "command not found" contains no "error".
# The last time, the toolchain had been wiped by a sandbox reset. A script that checks the exit status cannot
# make that mistake, and calling a script is easier than remembering not to write the grep.
set -euo pipefail
JDK="${JDK:-/root/jdk-27+35/bin/javac}"
FX="${FX:-/root/javafx-sdk-27/lib}"
OUT="${1:-/root/workspace/tp-out}"
[ -x "$JDK" ] || { echo "build: no compiler at $JDK - the toolchain was probably reset"; exit 2; }
[ -d "$FX" ]  || { echo "build: no JavaFX at $FX"; exit 2; }
mkdir -p "$OUT"
"$JDK" --module-path "$FX" \
  --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
  -d "$OUT" $(find src/main/java -name '*.java')
echo "build: ok ($(find "$OUT" -name '*.class' | wc -l) classes)"
