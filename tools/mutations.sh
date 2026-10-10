#!/usr/bin/env bash
#
# Break one invariant at a time and ask the check that claims it whether it notices.
#
#   tools/mutations.sh          run them all
#   tools/mutations.sh boss     only those whose label matches
#   tools/mutations.sh --list   show them without running anything
#
# WHY. A check that has never been seen to fail is unverified, and these checks protect the copy players actually
# have. The sibling engine spent a week finding out one at a time what an unverified check costs - a suite that
# passed while its code was broken, a measurement compared to the constant that defined it, two constants that 636
# checks could not see, and a landing-sound rate limit that gated the log line instead of the queue.
#
# EACH ENTRY NAMES ITS SUITE, so a mutation is judged by the check that claims the invariant and not by the gate: the
# gate's jar-current check compares the jar against the sources, so EVERY source mutation would fail it and every
# entry would report "caught" for a reason that has nothing to do with the invariant.
#
# THREE WAYS THIS CAN SAY NOTHING, and all three are reported separately because they are not the same thing:
#   NOT CAUGHT       - the mutation applied and the check did not notice. This is the finding.
#   ANCHOR MISSING   - the mutation never applied, so the run proves nothing.
#   AMBIGUOUS ANCHOR - the anchor fits more than one place, so it may have changed something else entirely.
# The third is not hypothetical: in the sibling game's list the trigger-reset entry landed in a different function,
# changed nothing observable, and reported "no test covers this" about a claim that is covered.
#
# NOT PART OF THE GATE, deliberately: each entry is a full compile plus a suite, which is seconds each and minutes
# together. An audit to run when adding a check, not a tax on every commit.
set -u
cd "$(dirname "$0")/.." || exit 2

FX="${FX:-/root/javafx-sdk-27/lib}"
JDK="${JDK:-/root/jdk-27+35}"
MODS=javafx.controls,javafx.graphics,javafx.media,javafx.swing
[ -d "$FX" ] || { echo "mutations: no JavaFX at $FX - set FX=" >&2; exit 2; }

# label | file | anchor | replacement | suite
MUTATIONS=(
  "audio: landing sounds are not rate-limited|src/main/java/tropical/engine/AudioSystem.java|LAND_COOLDOWN_TIME = 0.1;|LAND_COOLDOWN_TIME = 0.0;|tropical.engine.AudioRateTest"
  "boss: the volley is not wired|src/main/java/tropical/engine/World.java|b.setVolleyCallback((x, y, dirX) -> addProjectile(Projectile.bomb(x, y, dirX < 0 ? -1 : 1)));|// volley not wired|tropical.engine.BlastTest"
  "boss: it does not think every frame|src/main/java/tropical/engine/World.java|if (boss != null && !boss.dead) boss.update(dt, playerBody);|;|tropical.engine.BlastTest"
  "the validator stops matching the player|src/main/java/tropical/GameplayScreen.java|public static final double RUN_SPEED = 200;|public static final double RUN_SPEED = 300;|tropical.engine.PlayerModelTest"
  "rooms: no transition at all|src/main/java/tropical/engine/ScreenManager.java|TRANSITION_TIME = 0.3;|TRANSITION_TIME = 0.0;|tropical.engine.TransitionTest"
)

if [ "${1:-}" = "--list" ]; then
  for m in "${MUTATIONS[@]}"; do echo "  ${m%%|*}"; done
  exit 0
fi

filter="${1:-}"
caught=0; missed=0; broken=0
for m in "${MUTATIONS[@]}"; do
  label="${m%%|*}"; rest="${m#*|}"
  file="${rest%%|*}"; rest="${rest#*|}"
  anchor="${rest%%|*}"; rest="${rest#*|}"
  repl="${rest%%|*}"; suite="${rest##*|}"
  if [ -n "$filter" ] && [[ "$label" != *"$filter"* ]]; then continue; fi
  printf '  %-44s ' "$label"

  BAK="$(mktemp)"; cp "$file" "$BAK"
  ANCHOR="$(printf '%b' "$anchor")" REPL="$(printf '%b' "$repl")" FILE="$file" node -e '
    const fs = require("fs");
    const p = process.env.FILE, a = process.env.ANCHOR, r = process.env.REPL;
    const s = fs.readFileSync(p, "utf8");
    const n = s.split(a).length - 1;
    if (n === 0) process.exit(3);
    if (n > 1) process.exit(4);
    fs.writeFileSync(p, s.replace(a, r));
  ' 2>/dev/null; rc=$?
  if [ "$rc" -eq 3 ] || [ "$rc" -eq 4 ]; then
    [ "$rc" -eq 4 ] && printf 'AMBIGUOUS ANCHOR - fits more than one place, so this proves nothing\n' \
                     || printf 'ANCHOR MISSING - the mutation never applied, so this proves nothing\n'
    broken=$((broken + 1)); cp "$BAK" "$file"; rm -f "$BAK"; continue
  fi

  if ! "$JDK/bin/javac" --module-path "$FX" --add-modules "$MODS" -cp classes -d classes \
        $(find src/main/java -name '*.java') >/tmp/tp-mutation-compile.log 2>&1; then
    printf 'WILL NOT COMPILE - the mutation is not a state the code can be in\n'
    broken=$((broken + 1)); cp "$BAK" "$file"; rm -f "$BAK"; continue
  fi
  if timeout 300 "$JDK/bin/java" --module-path "$FX" --add-modules "$MODS" -cp "classes:src/main/resources" \
        "$suite" >/tmp/tp-mutation-run.log 2>&1; then
    printf 'NOT CAUGHT - %s does not cover this\n' "${suite##*.}"
    missed=$((missed + 1))
  else
    printf 'caught by %s\n' "${suite##*.}"
    caught=$((caught + 1))
  fi
  cp "$BAK" "$file"; rm -f "$BAK"
done

echo
echo "  caught: $caught   not caught: $missed   never applied, ambiguous or uncompilable: $broken"
if [ "$missed" -eq 0 ] && [ "$broken" -eq 0 ]; then
  echo "  OK every invariant listed here is one a check would notice breaking"
  exit 0
fi
echo "  A mutation nobody notices is a claim nobody is checking."
exit 1
