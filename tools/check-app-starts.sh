#!/usr/bin/env bash
#
# Does the app START? Not "do the suites pass" - does the class a person double-clicks come up.
#
#   tools/check-app-starts.sh
#
# WHY THIS EXISTS. `tropical.Main` is listed in this repository's gate as a TOOL, excluded from the suite list: the
# suites build their own screens and the Robot tests build their own scenes, so nothing started the app itself. A
# break in the entry point - `getResource("/style.css")` returning null, an asset missing, a screen blowing up on
# construction - would be invisible to every check here.
#
# IT DOES NOT USE A SNAPSHOT MODE, because this edition's Main has none: the engine's app has one and this one does
# not. What this asserts is weaker and still worth having - THE PROCESS COMES UP AND STAYS UP, with no exception on
# the way - and the engine's check, which draws a frame and reads the app's own asset report, is the stronger one.
# Saying which is which matters more than making them look alike.
set -u
cd "$(dirname "$0")/.." || exit 2

JAVA="${JAVA:-/root/jdk-27+35/bin/java}"
FX="${FX:-/root/javafx-sdk-27/lib}"
OUT="${OUT:-classes}"
MODS="javafx.controls,javafx.graphics,javafx.media,javafx.swing"

LOG=$(mktemp); trap 'rm -f "$LOG"' EXIT

# ":99" AND NOT "99" - the first version of this line read DISPLAY="${DISPLAY_NUM:-99}", which set the variable to
# the bare number and OVERRODE the working display with a name that does not resolve, so the check failed with
# "Unable to open DISPLAY" and called it the app failing to start. A missing colon, reported as a broken app.
DISPLAY="${DISPLAY:-:99}" "$JAVA" --module-path "$FX" --add-modules "$MODS" \
    -cp "$OUT:src/main/resources" tropical.Main > "$LOG" 2>&1 &
PID=$!
sleep 8

ALIVE=no
kill -0 "$PID" 2>/dev/null && ALIVE=yes

if grep -qE 'Exception in Application|Exception in thread|Unable to open DISPLAY|Error:' "$LOG"; then
  echo "  the app failed on its way up:"
  grep -E 'Exception in Application|Exception in thread|Unable to open DISPLAY|Error:' "$LOG" | head -4 | sed 's/^/    /'
  kill "$PID" 2>/dev/null
  exit 1
fi
if [ "$ALIVE" != yes ]; then
  echo "  the app exited within 8 seconds, and said nothing that looks like a failure:"
  grep -viE '^WARNING' "$LOG" | tail -6 | sed 's/^/    /'
  exit 1
fi

kill "$PID" 2>/dev/null
wait "$PID" 2>/dev/null
echo "  === the app starts and stays up, with no exception ==="
