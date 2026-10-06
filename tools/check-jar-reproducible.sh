#!/usr/bin/env bash
#
# check-jar-reproducible.sh - does the same source produce the same bytes twice?
#
#   tools/check-jar-reproducible.sh
#
# WHY. On 2026-10-06 two consecutive builds of identical source produced jars with different MD5s, 788 bytes apart,
# all of it timestamps. That quietly defeated `artifact-changed.sh`, which compares BYTES and refuses a release
# identical to the published one - a rebuild always changes the bytes, so it caught a re-uploaded file and not a
# rebuilt one, which is the case it was written for. It also left the committed jar dirty in git after every
# build, which trains you to ignore the one file that matters.
#
# The fix is a fixed mtime on the extracted tree plus tools/normalize-jar.py for the two entries `jar` stamps
# itself. This is the check that keeps it fixed, because a property nobody tests is a property that comes back.
#
# IT BUILDS TWICE, which is about ten seconds. That is the cost of testing the thing itself rather than a proxy.
set -u
cd "$(dirname "$0")/.." || exit 2

[ -x tools/make-jar.sh ] || { echo "  jar-reproducible     FAIL   no tools/make-jar.sh" >&2; exit 2; }

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
cp tropical-punch.jar "$TMP/original.jar" 2>/dev/null || true

tools/make-jar.sh >/dev/null 2>&1 || { echo "  jar-reproducible     FAIL   the first build failed" >&2; exit 1; }
mv tropical-punch.jar.new "$TMP/first.jar" || { echo "  jar-reproducible     FAIL   no jar from the first build" >&2; exit 1; }
tools/make-jar.sh >/dev/null 2>&1 || { echo "  jar-reproducible     FAIL   the second build failed" >&2; exit 1; }
mv tropical-punch.jar.new "$TMP/second.jar" || { echo "  jar-reproducible     FAIL   no jar from the second build" >&2; exit 1; }

# put the committed jar back, so running this check does not leave the tree modified
[ -f "$TMP/original.jar" ] && cp "$TMP/original.jar" tropical-punch.jar

a=$(md5sum "$TMP/first.jar" | cut -d' ' -f1)
b=$(md5sum "$TMP/second.jar" | cut -d' ' -f1)
if [ "$a" = "$b" ]; then
  echo "  jar-reproducible     ok     two builds of the same source: $a"
  exit 0
fi
n=$(cmp -l "$TMP/first.jar" "$TMP/second.jar" 2>/dev/null | wc -l)
echo "  jar-reproducible     FAIL   two builds differ in $n byte(s): $a vs $b" >&2
echo "                              a rebuild changes the bytes, so artifact-changed.sh cannot see a rebuilt" >&2
echo "                              release that is identical to the published one." >&2
exit 1
