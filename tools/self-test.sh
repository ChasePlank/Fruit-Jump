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

README_BAK=$(mktemp); SRC_BAK=$(mktemp)
SRC=src/main/java/tropical/GameplayScreen.java
cp README.md "$README_BAK"; cp "$SRC" "$SRC_BAK"
restore() { cp "$README_BAK" README.md; cp "$SRC_BAK" "$SRC"; rm -f "$README_BAK" "$SRC_BAK"; }
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

# ---- TutorialTest must notice a control the README documents and no sign teaches -------------------------------
# A GAME TEST IN A TOOL SELF-TEST, which needs a word of justification: the rule this file enforces is that every
# check gets broken on purpose before it is trusted, and TutorialTest now carries the check that would have caught
# the hookshot. It compares signs to each other for collisions and cannot see a mechanic with no sign at all, so
# the coverage check is the one that matters and it gets the same treatment as the tools.
TUT=src/main/java/tropical/Tutorial.java
if grep -q "HOOKSHOT   X" "$TUT"; then
  TUT_BAK=$(mktemp); cp "$TUT" "$TUT_BAK"
  python3 - <<'PY'
p='src/main/java/tropical/Tutorial.java'; s=open(p).read()
old = '                s.add(new Sign(44 * 32, y, "HOOKSHOT   X   -   pulls you to a wall"));'
assert old in s, 'the hookshot sign is not there to remove'
open(p,'w').write(s.replace(old, '                // removed for the fault injection', 1))
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

# ---- and both must be CLEAN once restored, or the injections above proved nothing -----------------------------
if tools/readme-counts.sh >/dev/null 2>&1; then ok "readme-counts clean after restore" "exit 0"
else bad "readme-counts clean after restore" "still failing once restored"; fi
if tools/check-jar-current.sh >/dev/null 2>&1; then ok "jar-current clean after restore" "exit 0"
else bad "jar-current clean after restore" "still failing once restored"; fi

echo
echo "=== $pass tool self-test(s) passed, $fail failed ==="
[ "$fail" -eq 0 ] || exit 1
