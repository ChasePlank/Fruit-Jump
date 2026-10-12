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
  "the parallax moves the wrong way|src/main/java/tropical/engine/Camera.java|return x * scrollFactor;|return x * (1 - scrollFactor);|all"
  # PORTED FROM THE ENGINE'S LIST, where each of these was already caught. Same engine, same anchors - the paths and
  # the suite are the only things that differ, and the suite is `all` because this repository has no SelfTest to name.
  "piranha: it never drifts when idle|src/main/java/tropical/engine/Piranha.java|IDLE_SPEED = 28;|IDLE_SPEED = 0.0;|all"
  "damage: the knockback pushes you IN|src/main/java/tropical/engine/Combat.java|player.vx = dir * KNOCKBACK_X;|player.vx = -dir * KNOCKBACK_X;|all"
  "slopes: no snap onto a ramp from a ledge|src/main/java/tropical/engine/World.java|SLOPE_SNAP_DOWN = 14.0;|SLOPE_SNAP_DOWN = 0.0;|all"
  "stomp: no bounce off an enemy|src/main/java/tropical/engine/Combat.java|STOMP_BOUNCE = -400;|STOMP_BOUNCE = 0.0;|all"

  # PORTED FROM THE ENGINE'S LIST, where each of these is already caught. Same engine, same anchors.
  "boss: an arrow does no damage|src/main/java/tropical/engine/World.java|static final double ARROW_DAMAGE = 3.0;|static final double ARROW_DAMAGE = 0.0;|all"
  "enemy: a death with no fade|src/main/java/tropical/engine/Enemy.java|DEATH_SECONDS = 0.35;|DEATH_SECONDS = 0.0;|all"
  "run: no one-way planks at all|src/main/java/tropical/engine/LevelGen.java|int PLANK_EVERY = 4;|int PLANK_EVERY = 40000;|all"
  "run: no bosses at all|src/main/java/tropical/engine/LevelGen.java|int BOSS_EVERY = 10;|int BOSS_EVERY = 40000;|all"
  "run: the game ends after ten levels|src/main/java/tropical/engine/LevelGen.java|int FINAL_LEVEL = 40;|int FINAL_LEVEL = 10;|all"
  "water: nothing is deep enough to swim|src/main/java/tropical/engine/WaterSystem.java|SWIM_DEPTH = 48.0;|SWIM_DEPTH = 100000.0;|all"
  "water: the water does not slow you|src/main/java/tropical/engine/WaterSystem.java|DRAG_X = 2.5;|DRAG_X = 0.0;|all"
  "hookshot: the stall guard switched off|src/main/java/tropical/engine/Hookshot.java|STALL_TIME = 0.3;|STALL_TIME = 0.0;|all"
  "weapons: arrows fly straight|src/main/java/tropical/engine/Projectile.java|ARROW_GRAVITY = 250;|ARROW_GRAVITY = 0.0;|all"
  "weapons: the blast hurts nobody|src/main/java/tropical/engine/Projectile.java|BLAST_DAMAGE_RANGE = 100;|BLAST_DAMAGE_RANGE = 0.0;|all"
  "piranha: no punish window after a miss|src/main/java/tropical/engine/Piranha.java|RECOVER_TIME = 1.1;|RECOVER_TIME = 0.0;|all"
  "piranha: it re-engages instantly|src/main/java/tropical/engine/Piranha.java|REAGGRO_DELAY = 2.5;|REAGGRO_DELAY = 0.0;|all"
  "damage: a hit does not move you|src/main/java/tropical/engine/Combat.java|KNOCKBACK_X = 250;|KNOCKBACK_X = 0.0;|all"
  "damage: no upward pop|src/main/java/tropical/engine/Combat.java|KNOCKBACK_Y = -300;|KNOCKBACK_Y = 0.0;|all"
  "slopes: a steep slope does not slide you|src/main/java/tropical/engine/World.java|SLOPE_SLIDE_ACC = 900.0;|SLOPE_SLIDE_ACC = 0.0;|all"
  "stomp: the positional rule flipped|src/main/java/tropical/engine/Combat.java|return (player.y + player.hh) < enemy.y;|return (player.y + player.hh) > enemy.y;|all"
  "blast: a bomb does not hurt the boss|src/main/java/tropical/engine/World.java|BLAST_DAMAGE = 9.0;|BLAST_DAMAGE = 0.0;|all"
  "blast: a bomb is worth thirteen arrows|src/main/java/tropical/engine/World.java|BLAST_DAMAGE = 9.0;|BLAST_DAMAGE = 40.0;|all"
  "model: the player's jump drifts|src/main/java/tropical/GameplayScreen.java|JUMP_V = -420;|JUMP_V = -380;|all"
  "model: the player's run speed drifts|src/main/java/tropical/GameplayScreen.java|RUN_SPEED = 200;|RUN_SPEED = 240;|all"
  "water: the up-current does nothing|src/main/java/tropical/engine/LevelMap.java|CURRENT_UP_SPEED = 70.0;|CURRENT_UP_SPEED = 0.0;|all"
  "bats: the stun does nothing|src/main/java/tropical/engine/Bat.java|STUN_SECONDS = 1.0;|STUN_SECONDS = 0.0;|all"
  "loop: the frame clamp stops the world|src/main/java/tropical/engine/GameLoop.java|MAX_FRAME = 0.25;|MAX_FRAME = 0.0;|all"
  "loop: the frame clamp protects nothing|src/main/java/tropical/engine/GameLoop.java|MAX_FRAME = 0.25;|MAX_FRAME = 5.0;|all"
  "bats: the pursuit does nothing|src/main/java/tropical/engine/Bat.java|PURSUE_SPEED = 130;|PURSUE_SPEED = 0.0;|all"
  "bats: it outruns the player it is meant to lure|src/main/java/tropical/engine/Bat.java|PURSUE_SPEED = 130;|PURSUE_SPEED = 240.0;|all"
  "bats: the dive is not faster than pursuit|src/main/java/tropical/engine/Bat.java|SWOOP_SPEED = 330;|SWOOP_SPEED = 0.0;|all"
  "bats: the dive never ends|src/main/java/tropical/engine/Bat.java|SWOOP_TIME = 0.5;|SWOOP_TIME = 0.0;|all"
  "bats: it cannot dive again for twenty seconds|src/main/java/tropical/engine/Bat.java|SWOOP_RECOVER = 0.85;|SWOOP_RECOVER = 20.0;|all"
  "boss: it never lobs a volley|src/main/java/tropical/engine/Boss.java|return phase >= 2 && rng.nextDouble() < 0.5 ? Attack.VOLLEY : Attack.CHARGE;|return phase >= 99 && rng.nextDouble() < 0.5 ? Attack.VOLLEY : Attack.CHARGE;|all"
  "tutorial: one level short of the boss|src/main/java/tropical/Tutorial.java|public static final int LAST = 10;|public static final int LAST = 9;|all"

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
  "moving platform: a quarter of a period is a sixth of one|src/main/java/tropical/engine/MovingPlatform.java|double phase = (pathTimer / period) * 2 * Math.PI;|double phase = (pathTimer / period) * Math.PI;|all"
  "moving platform: the path is twice as wide as it says|src/main/java/tropical/engine/MovingPlatform.java|x = x0 + Math.sin(phase) * amplitude;|x = x0 + Math.sin(phase) * amplitude * 2;|all"
  "moving platform: the rider is carried TWICE as far|src/main/java/tropical/engine/World.java|b.x += delta[0] * dt;|b.x += 2 * delta[0] * dt;|all"
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
