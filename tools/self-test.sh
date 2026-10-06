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
