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
  # THE WATER, which is where this game's tension lives and which had no mutation at all. A long stretch of it
  # drowns you; twelve seconds of breath is what makes a river a decision rather than a detour.
  "water: you can hold your breath forever|src/main/java/tropical/engine/WaterSystem.java|BREATH_SECONDS = 12.0;|BREATH_SECONDS = 1200.0;|tropical.engine.WaterSuite"
  "water: drowning never happens|src/main/java/tropical/engine/WaterSystem.java|DROWN_INTERVAL = 1.0;|DROWN_INTERVAL = 1000.0;|tropical.engine.WaterSuite"
  "water: the breath meter never refills|src/main/java/tropical/engine/WaterSystem.java|BREATH_REFILL = 3.0;|BREATH_REFILL = 0.0;|tropical.engine.WaterSuite"
  "water: you cannot swim|src/main/java/tropical/engine/WaterSystem.java|SWIM_SPEED = 0.60;|SWIM_SPEED = 0.0;|tropical.engine.WaterSuite"
  "water: nothing floats|src/main/java/tropical/engine/WaterSystem.java|BUOYANCY = 1.35;|BUOYANCY = 0.0;|tropical.engine.WaterSuite"
  # The rest of the game: the arrows, the length of a run, the boss's armour, and the tutorial's last sign.
  "arrows do no damage|src/main/java/tropical/engine/World.java|ARROW_DAMAGE = 3.0;|ARROW_DAMAGE = 0.0;|all"
  "the run is two levels long|src/main/java/tropical/engine/LevelGen.java|FINAL_LEVEL = 40;|FINAL_LEVEL = 2;|all"
  "the boss takes unlimited hits in a window|src/main/java/tropical/engine/Boss.java|MAX_HITS_PER_WINDOW = 2;|MAX_HITS_PER_WINDOW = 99;|all"
  # THE TUTORIAL'S SIGNS ARE NOT THE MECHANISM, which this entry got wrong for a week. Deleting a sign came back
  # NOT CAUGHT and I read that as a gap - but the lesson lives in the LEVEL, not the text: Tutorial's own comment says
  # "No sign: this level shows rather than says, and the platform moving is the explanation." CoverageTest asserts
  # that every mechanic the run uses appears in a tutorial GRID, and it passes. The mutation was unfair, not the
  # check. This is the fair version of it: take the mechanic away and see whether the check notices.
  "the tutorial stops introducing moving platforms|src/main/java/tropical/Tutorial.java|built.addMover(LevelMap.MoverSpec.vertical(|if (false) built.addMover(LevelMap.MoverSpec.vertical(|all"
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

# THE TREE HAS TO BE CLEAN BEFORE THIS RUNS AND CLEAN WHEN IT FINISHES, and both halves are checked. A trap restores
# on a clean exit, but a closed session or a SIGKILL runs no trap at all - two interrupted runs in one hour left four
# mutated files in this tree, and `git status` was the only thing that said so. A tool whose whole job is to change
# files has to be able to say whether it left them changed.
if [ -n "$(git status --porcelain -- src/main/java)" ]; then
  echo "mutations: src/main/java is already modified - commit or discard first, so a leftover from this run can be" >&2
  echo "           told apart from a change that was already here." >&2
  exit 2
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

  # FIVE FIELDS, AND A MALFORMED ENTRY IS REFUSED RATHER THAN INTERPRETED. A stray pipe makes the replacement read
  # as everything after the LAST one, which is nothing, so the mutation deletes the matched text instead of changing
  # it: it compiles, it changes something harmless, and it reports "NOT CAUGHT" about a check that does cover it.
  if [ "$(printf '%s' "$m" | tr -cd '|' | wc -c)" -ne 4 ]; then
    printf 'MALFORMED ENTRY - five fields required (label|file|anchor|replacement|suite)\n'
    broken=$((broken + 1)); continue
  fi

  # A TRAP, because a run that is interrupted - a closed session, a timeout, Ctrl-C - otherwise leaves the tree with
  # a mutation in it, and the next person to build gets a game with a constant broken and no way to tell that from a
  # real bug. Learned the hard way an hour before this line existed: two hand-rolled loops of my own left three
  # mutated files in this tree, and `git status` was the only thing that said so.
  BAK="$(mktemp)"; cp "$file" "$BAK"
  trap 'cp "$BAK" "$file" 2>/dev/null; rm -f "$BAK"' EXIT
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
  # A SUITE NAMED `all` RUNS EVERY GAMEPLAY SUITE, and that is the honest question: a mutation judged by one suite
  # reports "no check covers this" about a claim another suite holds. The artefact checks are excluded on purpose -
  # jar-current compares the committed jar against the sources, so every source mutation fails it and would be
  # reported as caught for a reason unrelated to the invariant.
  #
  # IT ALSO STARTS A DISPLAY IF THERE IS NOT ONE, because ten of these suites open a window and a dead display makes
  # them all fail - which reads as "every suite noticed", the opposite of the truth. Both of those traps caught me
  # by hand in the same hour, which is why they are here rather than in a note.
  if [ "$suite" = "all" ]; then
    if [ -z "${DISPLAY:-}" ] || ! command -v xdpyinfo >/dev/null 2>&1 || ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
      Xvfb "${DISPLAY_NUM:-:99}" -screen 0 1400x900x24 >/tmp/tp-mutation-xvfb.log 2>&1 &
      export DISPLAY="${DISPLAY_NUM:-:99}"
      for _ in $(seq 1 40); do xdpyinfo -display "$DISPLAY" >/dev/null 2>&1 && break; sleep 0.25; done
    fi
    noticed=""
    printf '  ...running every suite: '
    for s in $(grep -rl "public static void main" src/main/java --include='*.java' \
                 | sed 's|src/main/java/||; s|\.java$||; s|/|.|g'); do
      # GLOBS, because $s is a qualified name: the first version wrote `Main`, which does not match `tropical.Main`
      # - so the GAME was run as if it were a check, it timed out on a mutated build, and the timeout was counted as
      # "caught by Main". A program that fails to start is not a check that noticed; it is the thing the check
      # exists to protect, failing to start.
      case "$s" in *jar*|*readme*|*style*|*release*|*Main|*Playthrough|*Sound|*KeyCheck|*ImageProbe) continue;; esac
      timeout 60 "$JDK/bin/java" --module-path "$FX" --add-modules "$MODS" -cp "classes:src/main/resources" \
        "$s" >/dev/null 2>&1 || noticed="$noticed ${s##*.}"
    done
    # EVERY NOTICER IS VERIFIED BEFORE IT COUNTS, and this is the fix for three false verdicts in one week. A suite
    # can fail for a reason that has nothing to do with the mutation: a dead display failed fourteen of them at once
    # and a mutation looked "caught by fourteen suites"; a mutated build that would not start was reported as
    # "caught by Main"; and a flaky Robot test aborting was counted as a detection, WHICH HID A REAL GAP - the run's
    # length is not asserted by anything and the tool said it was.
    #
    # So: put the source back, REBUILD (or the suites would run the mutated classes that are still in classes/), and
    # run the noticers again. A suite that fails without the mutation is not evidence; it is a baseline failure, and
    # it is reported as one. Twice, because a flaky abort needs more than one chance to show itself - and the
    # residual is named rather than hidden: a suite that flakes less often than one in two will still get through.
    real=""
    if [ -n "$noticed" ]; then
      cp "$BAK" "$file"
      "$JDK/bin/javac" --module-path "$FX" --add-modules "$MODS" -cp classes -d classes \
        $(find src/main/java -name '*.java') >/tmp/tp-mutation-rebuild.log 2>&1
      for s in $noticed; do
        q=$(grep -rl "public static void main" src/main/java --include='*.java' \
              | sed 's|src/main/java/||; s|\.java$||; s|/|.|g' | grep -E "\.$s\$" | head -1)
        [ -z "$q" ] && continue
        baseline=""
        for _ in 1 2; do
          timeout 60 "$JDK/bin/java" --module-path "$FX" --add-modules "$MODS" -cp "classes:src/main/resources" \
            "$q" >/dev/null 2>&1 || baseline="yes"
        done
        if [ -n "$baseline" ]; then
          printf '\n    (%s fails WITHOUT the mutation too - not evidence)\n' "$s"
        else
          real="$real $s"
        fi
      done
    fi
    if [ -n "$real" ]; then
      printf 'caught by%s\n' "$real"
      caught=$((caught + 1))
    elif [ -n "$noticed" ]; then
      printf 'NOT CAUGHT - the only suite(s) that failed also fail unmutated\n'
      missed=$((missed + 1))
    else
      printf 'NOT CAUGHT - no suite notices this\n'
      missed=$((missed + 1))
    fi
  elif timeout 60 "$JDK/bin/java" --module-path "$FX" --add-modules "$MODS" -cp "classes:src/main/resources" \
        "$suite" >/tmp/tp-mutation-run.log 2>&1; then
    printf 'NOT CAUGHT - %s does not cover this\n' "${suite##*.}"
    missed=$((missed + 1))
  else
    printf 'caught by %s\n' "${suite##*.}"
    caught=$((caught + 1))
  fi
  cp "$BAK" "$file"; rm -f "$BAK"
done

# and the postcheck: the state it leaves has to be the state it found
if [ -n "$(git status --porcelain -- src/main/java)" ]; then
  echo "  THIS RUN LEFT THE TREE MODIFIED - a restore did not happen:" >&2
  git status --porcelain -- src/main/java >&2
  broken=$((broken + 1))
fi

echo
echo "  caught: $caught   not caught: $missed   never applied, ambiguous or uncompilable: $broken"
if [ "$missed" -eq 0 ] && [ "$broken" -eq 0 ]; then
  echo "  OK every invariant listed here is one a check would notice breaking"
  exit 0
fi
echo "  A mutation nobody notices is a claim nobody is checking."
exit 1
