#!/usr/bin/env bash
#
# self-test.sh - break what each tool looks at, and check the tool notices.
#
#   tools/self-test.sh
#
# WHY THIS EXISTS. `aside` has had a self-test for its tools since the day a mutation harness shipped broken while
# the suites it ran passed beautifully, and this repository's tools are newer and were written under the same
# pressure as everything else here. Every tool below IS a check. A check that cannot fail is worse than no check,
# because it is believed:
#
#   readme-counts.sh       would rot silently if the README parse stopped matching
#   check-jar-current.sh   already failed once ON ITS OWN BUG - a relative path that resolved to the filesystem
#                          root, so nothing was extracted and it announced "92 of 92 classes differ". A wholesale
#                          verdict is the tool talking about itself, and only breaking it on purpose shows that.
#
# It has now been fault-injected by hand twice, which is this project's signal for building it in.
#
# EVERYTHING IS RESTORED, and the trap restores it even if this script is interrupted.
set -u
cd "$(dirname "$0")/.." || exit 2

pass=0; fail=0
ok()  { printf '  %-34s ok      %s\n' "$1" "$2"; pass=$((pass+1)); }
bad() { printf '  %-34s FAIL    %s\n' "$1" "$2"; fail=$((fail+1)); }

README_BAK=$(mktemp); SRC_BAK=$(mktemp); MM_BAK=$(mktemp)
SRC=src/main/java/tropical/GameplayScreen.java
MM=src/main/java/tropical/MainMenu.java
cp README.md "$README_BAK"; cp "$SRC" "$SRC_BAK"; cp "$MM" "$MM_BAK"
restore() { cp "$README_BAK" README.md; cp "$SRC_BAK" "$SRC"; cp "$MM_BAK" "$MM"; rm -f "$README_BAK" "$SRC_BAK" "$MM_BAK"; }
trap restore EXIT

# ---- readme-counts.sh must notice a count that no longer matches the files ------------------------------------
# The engine-class count is checked against the tree, so claiming a different number has to be caught.
if grep -q 'engine ([0-9]* classes' README.md; then
  python3 - <<'PY'
import re
p='README.md'; s=open(p).read()
s2 = re.sub(r'engine \([0-9]+ classes', 'engine (999 classes', s, count=1)
assert s2 != s, 'the count line did not change'
open(p,'w').write(s2)
PY
  out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "MISMATCH"; then
    ok "readme-counts notices a bad count" "$(echo "$out" | grep MISMATCH | head -1 | tr -s ' ')"
  else
    bad "readme-counts notices a bad count" "reported clean with 999 classes claimed"
  fi
  cp "$README_BAK" README.md
else
  bad "readme-counts notices a bad count" "no 'engine (N classes' line in README to break"
fi

# ---- check-jar-current.sh must notice source that has moved past the jar --------------------------------------
if grep -q 'RUN_SPEED = 200' "$SRC"; then
  sed -i 's/RUN_SPEED = 200/RUN_SPEED = 201/' "$SRC"
  out=$(tools/check-jar-current.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "GameplayScreen.class"; then
    ok "jar-current notices stale source" "$(echo "$out" | grep FAIL | head -1 | tr -s ' ')"
  else
    bad "jar-current notices stale source" "reported clean with RUN_SPEED changed"
  fi
  cp "$SRC_BAK" "$SRC"
else
  bad "jar-current notices stale source" "no 'RUN_SPEED = 200' line to break"
fi

# ---- readme-counts.sh must notice a WRONG TUTORIAL COUNT, not just a wrong class count --------------------------
# The self-test already proved the tool can fail, by breaking the engine-class count. That is not the same as
# proving THIS assertion works: the tutorial pattern was wrong when it was written - it looked for a qualified name
# that does not appear in the file - and the existing injection would not have noticed.
# NOT anchored to the line start: the line is "- **The tutorial** — 9 hand-built levels", so a ^ pattern
# matches nothing and the injection reports "no line to break" - which is a failure, but the wrong one.
if grep -qE "[0-9]+ hand-built levels" README.md; then
  RD_BAK=$(mktemp); cp README.md "$RD_BAK"
  sed -i -E 's/[0-9]+ hand-built levels/99 hand-built levels/' README.md
  out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "tutorial levels actually"; then
    ok "readme-counts notices a wrong tutorial count" "$(echo "$out" | grep 'tutorial levels' | tr -s ' ')"
  else
    bad "readme-counts notices a wrong tutorial count" "reported clean with 99 levels claimed"
  fi
  cp "$RD_BAK" README.md; rm -f "$RD_BAK"
else
  bad "readme-counts notices a wrong tutorial count" "no 'N hand-built levels' line to break"
fi

# ---- readme-counts.sh must notice a BALANCE NUMBER that no longer matches the README ---------------------------
# The patterns for these were wrong twice before they worked - one matched the wrong Math.max in the file and
# returned "." as a value, which is worse than no match because it looks like a number. An assertion whose pattern
# is wrong passes on a broken README, so it gets the same injection as the others.
LG=src/main/java/tropical/engine/LevelGen.java
if grep -qE "Math\.max\(0\.12, 0\.55 - levelNum" "$LG"; then
  LG_BAK=$(mktemp); cp "$LG" "$LG_BAK"
  sed -i -E 's/Math\.max\(0\.12, 0\.55 - levelNum/Math.max(0.20, 0.55 - levelNum/' "$LG"
  out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "heal: 0.55 floor 0.20"; then
    ok "readme-counts notices a changed heal floor" "$(echo "$out" | grep 'heal:' | tr -s ' ')"
  else
    bad "readme-counts notices a changed heal floor" "reported clean with the floor at 0.20"
  fi
  cp "$LG_BAK" "$LG"; rm -f "$LG_BAK"
else
  bad "readme-counts notices a changed heal floor" "no heal line to change"
fi

if grep -qE "BASE_MAX_GAP_CELLS = 3" "$LG"; then
  LG_BAK2=$(mktemp); cp "$LG" "$LG_BAK2"
  sed -i -E 's/BASE_MAX_GAP_CELLS = 3/BASE_MAX_GAP_CELLS = 5/' "$LG"
  out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "gaps: base 5"; then
    ok "readme-counts notices a changed gap base" "$(echo "$out" | grep 'gaps:' | tr -s ' ')"
  else
    bad "readme-counts notices a changed gap base" "reported clean with the base at 5"
  fi
  cp "$LG_BAK2" "$LG"; rm -f "$LG_BAK2"
else
  bad "readme-counts notices a changed gap base" "no gap constant to change"
fi

# ---- readme-counts.sh must notice the feature list claiming something that is not in the game -------------------
# The original fault: the feature list said "moving platforms with carry" and "parallax" as though they were in the
# game, while a later section said nothing constructs or draws either. A reader meets the feature list first.
if grep -q "one-way platforms$" README.md; then
  RD2_BAK=$(mktemp); cp README.md "$RD2_BAK"
  # MOVERS AND PARALLAX ARE IN THE GAME NOW, so they are no longer what this injects: the case was testing that the
  # check still forbade two features that exist. It injects a claim about something that IS still unwired.
  sed -i 's/- \*\*Movement\*\* — run, jump, one-way platforms/- **Movement** — run, jump, one-way platforms, an ai director/' README.md
  out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q 'claims "ai director"'; then
    ok "readme-counts notices a feature not in the game" 'the feature list claims "ai director"'
  else
    bad "readme-counts notices a feature not in the game" "reported clean with an ai director claimed"
  fi
  cp "$RD2_BAK" README.md; rm -f "$RD2_BAK"
else
  bad "readme-counts notices a feature not in the game" "no movement line to change"
fi

# ---- readme-counts.sh must notice a file the README names that is not there -------------------------------------
# check-release-notes.sh does this for release notes and found eleven releases naming files that were not attached.
# Nothing did it for the README, which names HOLDFAST.md, the sky comparison, the jar and six tools.
if [ -f HOLDFAST.md ]; then
  HF_BAK=$(mktemp); cp HOLDFAST.md "$HF_BAK"; rm HOLDFAST.md
  out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "HOLDFAST.md"; then
    ok "readme-counts notices a missing file the README names" "$(echo "$out" | grep 'files the README' | tr -s ' ')"
  else
    bad "readme-counts notices a missing file the README names" "reported clean with HOLDFAST.md deleted"
  fi
  cp "$HF_BAK" HOLDFAST.md; rm -f "$HF_BAK"
else
  bad "readme-counts notices a missing file the README names" "no HOLDFAST.md to remove"
fi

# ---- check-jar-current.sh must notice a RESOURCE that changed without the jar being rebuilt ----------------------
# The check compared the jar's classes and nothing else, so style.css and ninety audio files could go stale in the
# jar and it would pass. Same shape as the unwired list covering three of four: a check covering part of what it
# claims. The fault is one appended line to the stylesheet.
JCC=tools/check-jar-current.sh
if [ -f src/main/resources/style.css ]; then
  CSS2_BAK=$(mktemp); cp src/main/resources/style.css "$CSS2_BAK"
  printf '\n/* fault injection */\n' >> src/main/resources/style.css
  out=$("$JCC" 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "stale in the jar: style.css"; then
    ok "jar-current notices a stale resource" "$(echo "$out" | grep 'resource(s) differ' | tr -s ' ')"
  else
    bad "jar-current notices a stale resource" "reported clean with the stylesheet changed"
  fi
  cp "$CSS2_BAK" src/main/resources/style.css; rm -f "$CSS2_BAK"
else
  bad "jar-current notices a stale resource" "no stylesheet to change"
fi

# ---- readme-counts.sh must notice one of the four unwired classes being wired -----------------------------------
# MovingPlatform was simply absent from the list: the README named four things as unwired and the check covered
# three, so wiring the fourth would have made the README quietly false with nothing to show it.
# AND THIS CASE NOW INJECTS THE BOSS, because MovingPlatform left the unwired list when it was wired: the case was
# checking that the tool still called a wired class unwired.
if ! grep -q "new AIDirector(" src/main/java/tropical/engine/World.java; then
  W_BAK=$(mktemp); cp src/main/java/tropical/engine/World.java "$W_BAK"
  python3 - <<'PY'
p='src/main/java/tropical/engine/World.java'; s=open(p).read()
i = s.index('public void addMover(')
open(p,'w').write(s[:i] + '    // fault injection\n    void probe() { AIDirector d = new AIDirector(); }\n\n' + s[i:])
PY
  out=$(tools/readme-counts.sh 2>&1)
  if echo "$out" | grep -q "WIRED?   AIDirector"; then
    ok "readme-counts notices a wired AIDirector" "WIRED? AIDirector is constructed in 1 file"
  else
    bad "readme-counts notices a wired AIDirector" "reported clean with AIDirector constructed"
  fi
  cp "$W_BAK" src/main/java/tropical/engine/World.java; rm -f "$W_BAK"
else
  bad "readme-counts notices a wired MovingPlatform" "MovingPlatform is already constructed"
fi

# ---- readme-counts.sh must notice a fifth unwired bullet the list does not cover ---------------------------------
# THE PARALLAX BULLET IS GONE - it is wired - so this hangs itself on the Boss bullet, which is still there. The
# case is about the COUNT: a bullet the list of checks does not cover has to be noticed.
if grep -q "^- \*\*AI Director\*\*" README.md; then
  RB_BAK=$(mktemp); cp README.md "$RB_BAK"
  python3 - <<'PY'
p='README.md'; s=open(p).read()
old = '- **AI Director**'
assert old in s, 'the director bullet is not there'
open(p,'w').write(s.replace(old, old + '\n- **Something else** — nothing builds it either.', 1))
PY
  out=$(tools/readme-counts.sh 2>&1)
  if echo "$out" | grep -q "the README lists 2 unwired thing"; then
    ok "readme-counts notices an uncovered unwired bullet" "$(echo "$out" | grep 'unwired:' | tr -s ' ')"
  else
    bad "readme-counts notices an uncovered unwired bullet" "reported clean with two bullets and one check"
  fi
  cp "$RB_BAK" README.md; rm -f "$RB_BAK"
else
  bad "readme-counts notices an uncovered unwired bullet" "no director bullet to follow"
fi

# ---- readme-counts.sh must notice WaterSuite is a test, not a class ---------------------------------------------
# THE FAULT IS THE ORIGINAL PATTERN. Matching only "Test.java$" counted WaterSuite as an engine class, and because
# the README's number had been computed the same way, the check reported "actually: 32, claims: 32, ok" for a count
# that is 31. A tool sharing the mistake it was written to catch is the quietest kind of check that cannot fail.
RC=tools/readme-counts.sh
if grep -q '(Test|Suite)\\.java\$' "$RC"; then
  RC_BAK=$(mktemp); cp "$RC" "$RC_BAK"
  # PYTHON, NOT SED. The pattern contains ( and |, which sed's basic regex treats as literals, so the
  # substitution silently did nothing and the injection reported "the old pattern did not reproduce the
  # fault" - a failure, but the wrong one. Third time this week sed's escaping has cost a round.
  python3 - "$RC" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
old = '(Test|Suite)' + chr(92) + '.java$'
assert old in s, 'the pattern is not there to change'
open(p, 'w').write(s.replace(old, '(Test)' + chr(92) + '.java$', 1))
PY
  out=$(tools/readme-counts.sh 2>&1); rc=$?
  # THE TEST IS THE NUMBER, NOT THE EXIT CODE. With the old pattern the count comes out 32 - which is what the
  # README used to say and what the check used to agree with. Now that the README says 31, the old pattern FAILS
  # rather than passing, so requiring rc=0 tested the wrong thing.
  if echo "$out" | grep -q "engine classes actually: 32"; then
    ok "readme-counts notices WaterSuite is a test" "with the old pattern it counts 32 classes instead of 31"
  else
    bad "readme-counts notices WaterSuite is a test" "the old pattern did not reproduce the fault"
  fi
  cp "$RC_BAK" "$RC"; rm -f "$RC_BAK"
else
  bad "readme-counts notices WaterSuite is a test" "the pattern is not what was expected"
fi

# ---- readme-counts.sh must notice a comment stating a stale tutorial end ----------------------------------------
# The original fault, and there were THREE of them: both copies of GameplayScreen and TutorialTest itself all said
# the tutorial "ends at 8" while Tutorial.LAST was 9. The check found the third one, which I had not seen.
if grep -q "ends at 8 back to the" src/main/java/tropical/TutorialTest.java; then
  bad "readme-counts notices a stale tutorial end" "the fixture is already stale"
else
  ST_BAK=$(mktemp); cp src/main/java/tropical/TutorialTest.java "$ST_BAK"
  sed -i 's/and it ends back at the/ends at 8 back to the/' src/main/java/tropical/TutorialTest.java
  out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "a comment states a tutorial count of 8"; then
    ok "readme-counts notices a stale tutorial end" "$(echo "$out" | grep 'a comment states' | tr -s ' ')"
  else
    bad "readme-counts notices a stale tutorial end" "reported clean with a comment saying 8"
  fi
  cp "$ST_BAK" src/main/java/tropical/TutorialTest.java; rm -f "$ST_BAK"
fi

# ---- readme-counts.sh must notice the SAME CLAIM SPELLED AS A WORD -------------------------------------------------
# THE INJECTION THAT WAS MISSING, and the whole reason this check was widened. The check above proves the check
# works on "ends at 8" - the phrasing it was written for - and it passed for months while four comments in the two
# repositories said "eight hand-built levels", which is the same false claim written a way the pattern could not
# see. A self-test that only exercises the phrasing the check already handles is a self-test that certifies the
# blind spot. This is the fault that actually escaped, injected verbatim.
if grep -q "// Eight hand-built levels" src/main/java/tropical/MainMenu.java; then
  bad "readme-counts notices a word-form count" "the fixture is already stale"
else
  sed -i 's|// Ten hand-built levels|// Eight hand-built levels|' src/main/java/tropical/MainMenu.java
  if ! grep -q "// Eight hand-built levels" src/main/java/tropical/MainMenu.java; then
    bad "readme-counts notices a word-form count" "the injection did not apply"
  else
    out=$(tools/readme-counts.sh 2>&1); rc=$?
    if [ $rc -ne 0 ] && echo "$out" | grep -q "a comment states a tutorial count of 8"; then
      ok "readme-counts notices a word-form count" "$(echo "$out" | grep 'a comment states' | tr -s ' ')"
    else
      bad "readme-counts notices a word-form count" "reported clean with a comment saying Eight"
    fi
  fi
  cp "$MM_BAK" src/main/java/tropical/MainMenu.java
fi

# ---- and readme-counts.sh must NOT fire on a claim it is only DISCUSSING -------------------------------------------
# A NEGATIVE TEST, which the block above cannot be. The widened check skips quoted text, because Tutorial.java's
# header quotes the old wrong wording in order to record the mistake - so a check that could not tell a quoted
# claim from an asserted one would fail on the correction itself. That makes "does not fire" the correct behaviour
# here, and a rule that is only ever tested in the firing direction is not tested. If the quote-skipping breaks,
# nothing else in this file notices; this does.
if grep -q '"Eight hand-built levels"' src/main/java/tropical/MainMenu.java; then
  bad "readme-counts ignores a quoted claim" "the fixture is already quoted"
else
  sed -i 's|// Ten hand-built levels, one mechanic each|// the header once claimed "Eight hand-built levels"|' src/main/java/tropical/MainMenu.java
  if ! grep -q '"Eight hand-built levels"' src/main/java/tropical/MainMenu.java; then
    bad "readme-counts ignores a quoted claim" "the injection did not apply"
  else
    out=$(tools/readme-counts.sh 2>&1); rc=$?
    if [ $rc -eq 0 ]; then
      ok "readme-counts ignores a quoted claim" "quoted text did not trip the check"
    else
      bad "readme-counts ignores a quoted claim" "flagged quoted text: $(echo "$out" | grep 'a comment states' | tr -s ' ')"
    fi
  fi
  cp "$MM_BAK" src/main/java/tropical/MainMenu.java
fi

# ---- TutorialTest must notice water the climber cannot swim in --------------------------------------------------
# THE ORIGINAL FAULT, inverted: the pool was made one row deep, which is a flooded WALK, and a walk is not `deep`
# even at the same submersion - both measure about 0.73. So the sign saying "SPACE swims up" would sit above water
# you cannot swim in, which is a sign promising a rule the code does not enforce.
if grep -q "g\[FLOOR - 2\]\[c\] = '~'" src/main/java/tropical/Tutorial.java; then
  TUT2_BAK=$(mktemp); cp src/main/java/tropical/Tutorial.java "$TUT2_BAK"
  sed -i "s/for (int c = 44; c <= 50; c++) { g\[FLOOR - 2\]\[c\] = '~'; g\[FLOOR - 1\]\[c\] = '~'; }/for (int c = 44; c <= 50; c++) { g[FLOOR - 1][c] = '~'; }/" src/main/java/tropical/Tutorial.java
  /root/jdk-27+35/bin/javac --module-path /root/javafx-sdk-27/lib \
      --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing -cp out -d out \
      $(find src/main/java -name '*.java') >/dev/null 2>&1
  out=$(DISPLAY="${DISPLAY:-:99}" timeout 300 /root/jdk-27+35/bin/java \
      --module-path /root/javafx-sdk-27/lib \
      --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
      -cp out:src/main/resources tropical.TutorialTest 2>&1)
  if echo "$out" | grep -q "swim in .*FAIL"; then
    ok "TutorialTest notices water you cannot swim in" "$(echo "$out" | grep 'level 9 pool:' | tr -s ' ')"
  else
    bad "TutorialTest notices water you cannot swim in" "reported clean with a one-row pool"
  fi
  cp "$TUT2_BAK" src/main/java/tropical/Tutorial.java; rm -f "$TUT2_BAK"
  /root/jdk-27+35/bin/javac --module-path /root/javafx-sdk-27/lib \
      --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing -cp out -d out \
      $(find src/main/java -name '*.java') >/dev/null 2>&1
else
  bad "TutorialTest notices water you cannot swim in" "no two-row pool to flatten"
fi

# ---- readme-counts.sh must notice the engine learning about JavaFX ---------------------------------------------
# THE FAULT IS ONE IMPORT. The README states the property - "the engine never knows JavaFX exists" - and everything
# headless depends on it. A headless suite that suddenly needs a display fails as "Unable to open DISPLAY", which
# reads as a broken display rather than as the engine having grown a dependency.
ENG=src/main/java/tropical/engine/GameLoop.java
if ! grep -q "javafx" "$ENG"; then
  ENG_BAK=$(mktemp); cp "$ENG" "$ENG_BAK"
  sed -i '1a import javafx.scene.paint.Color;' "$ENG"
  out=$(tools/readme-counts.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "engine files referencing JavaFX: 1"; then
    ok "readme-counts notices the engine importing JavaFX" "$(echo "$out" | grep 'referencing JavaFX' | tr -s ' ')"
  else
    bad "readme-counts notices the engine importing JavaFX" "reported clean with one import added"
  fi
  cp "$ENG_BAK" "$ENG"; rm -f "$ENG_BAK"
else
  bad "readme-counts notices the engine importing JavaFX" "GameLoop already references javafx"
fi

# ---- TutorialTest must notice a control the README documents and no sign teaches -------------------------------
# A GAME TEST IN A TOOL SELF-TEST, which needs a word of justification: the rule this file enforces is that every
# check gets broken on purpose before it is trusted, and TutorialTest now carries the check that would have caught
# the hookshot. It compares signs to each other for collisions and cannot see a mechanic with no sign at all, so
# the coverage check is the one that matters and it gets the same treatment as the tools.
TUT=src/main/java/tropical/Tutorial.java
if grep -q "HOOKSHOT   X" "$TUT"; then
  TUT_BAK=$(mktemp); cp "$TUT" "$TUT_BAK"
  python3 - <<'PY'
import re
p='src/main/java/tropical/Tutorial.java'; s=open(p).read()
# BY THE TEXT, NOT BY THE POSITION. The first version matched "44 * 32" and broke the moment the sign moved to
# column 8 - it reported "reported clean with the hookshot sign removed" because it removed nothing.
pat = re.compile(r'^\s*s\.add\(new Sign\([^)]*"HOOKSHOT   X[^"]*"\)\);$', re.M)
assert pat.search(s), 'the hookshot sign is not there to remove'
open(p,'w').write(pat.sub('                // removed for the fault injection', s, count=1))
PY
  /root/jdk-27+35/bin/javac --module-path /root/javafx-sdk-27/lib \
      --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing -cp out -d out \
      $(find src/main/java -name '*.java') >/dev/null 2>&1
  out=$(DISPLAY="${DISPLAY:-:99}" timeout 300 /root/jdk-27+35/bin/java \
      --module-path /root/javafx-sdk-27/lib \
      --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
      -cp out:src/main/resources tropical.TutorialTest 2>&1)
  if echo "$out" | grep -q "NO SIGN TEACHES: hookshot"; then
    ok "TutorialTest notices an untaught control" "NO SIGN TEACHES: hookshot"
  else
    bad "TutorialTest notices an untaught control" "reported clean with the hookshot sign removed"
  fi
  cp "$TUT_BAK" "$TUT"; rm -f "$TUT_BAK"
  /root/jdk-27+35/bin/javac --module-path /root/javafx-sdk-27/lib \
      --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing -cp out -d out \
      $(find src/main/java -name '*.java') >/dev/null 2>&1
else
  bad "TutorialTest notices an untaught control" "no hookshot sign to remove"
fi

# ---- check-jar-reproducible.sh must notice a build that is not reproducible ------------------------------------
# THE ORIGINAL FAULT, injected: the normalizer is what makes the build reproducible, so removing its call puts the
# two timestamps `jar` writes itself back and the two builds diverge again.
MJ=tools/make-jar.sh
if grep -q "normalize-jar.py" "$MJ"; then
  MJ_BAK=$(mktemp); cp "$MJ" "$MJ_BAK"
  python3 - <<'PY'
p='tools/make-jar.sh'; s=open(p).read()
s2 = s.replace('python3 "$(dirname "$0")/normalize-jar.py" "$OUT" >/dev/null', ': normalize-jar.py disabled for the fault injection')
assert s2 != s, 'the normalizer call did not come out'
open(p,'w').write(s2)
PY
  out=$(tools/check-jar-reproducible.sh 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "differ in"; then
    ok "jar-reproducible notices a lost normalizer" "$(echo "$out" | grep 'differ in' | head -1 | tr -s ' ')"
  else
    bad "jar-reproducible notices a lost normalizer" "reported clean with the normalizer disabled"
  fi
  cp "$MJ_BAK" "$MJ"; rm -f "$MJ_BAK"
else
  bad "jar-reproducible notices a lost normalizer" "no normalize-jar.py call to remove"
fi

# ---- style-classes.sh must notice a class the code asks for and the stylesheet does not define ------------------
# THIS IS THE ORIGINAL BUG, injected on purpose. On 2026-10-04 four labels asked for `menu-item`, the stylesheet had
# no rule for it, and two lines of the game-over screen were nearly unreadable. Deleting the rule reproduces it.
CSS=src/main/resources/style.css
if grep -q '^\.menu-item' "$CSS"; then
  CSS_BAK=$(mktemp); cp "$CSS" "$CSS_BAK"
  python3 - <<'PY'
import re
p='src/main/resources/style.css'; s=open(p).read()
s2 = re.sub(r'\.menu-item\s*\{[^}]*\}', '', s, count=1)
assert s2 != s, 'the rule did not come out'
open(p,'w').write(s2)
PY
  out=$(tools/style-classes.sh . 2>&1); rc=$?
  if [ $rc -ne 0 ] && echo "$out" | grep -q "menu-item"; then
    ok "style-classes notices a missing rule" "$(echo "$out" | grep -i missing | head -1 | tr -s ' ')"
  else
    bad "style-classes notices a missing rule" "reported clean with .menu-item deleted"
  fi
  cp "$CSS_BAK" "$CSS"; rm -f "$CSS_BAK"
else
  bad "style-classes notices a missing rule" "no .menu-item rule to delete"
fi

# ---- check-release-notes.sh --draft must notice a note naming a file that will not be attached -----------------
# THE FAULT IS THE ONE THE TOOL WAS WRITTEN FOR, one step earlier in time. The published-release check found eleven
# releases naming files that were not attached; the DRAFT is the same fault caught before it ships, and until this
# mode existed the notes about to be attached were the only ones never checked - a whole release ran end to end on
# 2026-10-07 and 1.15's notes were not among the fifteen releases it verified.
NOTES="docs/release-notes-1.15.md"
if [ -f "$NOTES" ]; then
  RN_BAK=$(mktemp); cp "$NOTES" "$RN_BAK"
  printf '\nDownload `holdfast-1.15-macos-x64.tar.gz` for macOS.\n' >> "$NOTES"
  out=$(tools/check-release-notes.sh --draft "$NOTES" holdfast-1.15-windows-x64.zip \
        holdfast-1.15-linux-x64.tar.gz tropical-punch.jar 2>&1); rc=$?
  cp "$RN_BAK" "$NOTES"; rm -f "$RN_BAK"
  if [ $rc -ne 0 ] && echo "$out" | grep -q "holdfast-1.15-macos-x64.tar.gz"; then
    ok "check-release-notes --draft notices an unattached name" "$(echo "$out" | grep 'DRAFT FAILS' | tr -s ' ')"
  else
    bad "check-release-notes --draft notices an unattached name" "reported clean with a name attached to nothing"
  fi
else
  bad "check-release-notes --draft notices an unattached name" "no draft notes to break"
fi

# ---- and both must be CLEAN once restored, or the injections above proved nothing -----------------------------
if tools/readme-counts.sh >/dev/null 2>&1; then ok "readme-counts clean after restore" "exit 0"
else bad "readme-counts clean after restore" "still failing once restored"; fi
if tools/check-jar-current.sh >/dev/null 2>&1; then ok "jar-current clean after restore" "exit 0"
else bad "jar-current clean after restore" "still failing once restored"; fi

echo
echo "=== $pass tool self-test(s) passed, $fail failed ==="
[ "$fail" -eq 0 ] || exit 1
