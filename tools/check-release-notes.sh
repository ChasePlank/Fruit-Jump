#!/usr/bin/env bash
#
# check-release-notes.sh - does every file the release notes tell you to download actually exist?
#
#   tools/check-release-notes.sh            # every release
#   tools/check-release-notes.sh <tag>      # one
#
# WHY THIS EXISTS. Eleven of the fifteen published Holdfast releases told the reader to download a file that was
# not attached to anything:
#
#   1.14 notes  "Download `Holdfast-windows-x64.zip`"     asset  fj114.zip
#   1.13 notes  "Download `Holdfast-linux-x64.tar.gz`"    asset  fj113.tar.gz
#   1.13 notes  "`fruit-jump-1.13.jar`"                   asset  fj113.jar
#   1.12 notes  "`fruit-jump-1.12.jar`"                   asset  fj112.jar      ...and 1.11, 1.10, 1.9, 1.8, 1.7,
#   1.6/1.5/1.2 notes  "`fruit-jump-1.N.jar`"             assets fj1N.jar       1.6 and 1.5, 1.4, 1.2
#
# The cause is visible in the history: releases 1.0 to 1.3 attached `fruit-jump-1.N.jar`, matching what the notes
# said. From 1.4 the attachments were renamed to `fj1N.jar` and THE NOTES WERE NEVER UPDATED - so every release
# after the rename pointed at a name that had stopped existing.
#
# And it is worse than a wrong name, because the notes give COMMANDS built on them:
#
#   tar xzf Holdfast-linux-x64.tar.gz      -> No such file or directory
#   java -jar fruit-jump-1.13.jar          -> No such file or directory
#
# Somebody copy-pasting the instructions the release gives them gets an error and no explanation. Same rule as the
# README's: a documented command must be RUN before it is repeated, and a documented file must EXIST before it is
# believed. The README had three stale counts; the releases had eleven stale names.
#
# IT NEEDS THE NETWORK so it is NOT in run-suites.sh - a gate that fails when the network is down is one people
# learn to ignore. Run it before publishing.
#
# ONE API CALL. The first version called per-release twice and pulled the asset list for every tag separately, about
# forty-five calls, and GitHub's rate limiter turned that into six releases "with no assets" - false findings
# produced by the instrument. `GET /releases` returns every release WITH its assets, so it is one call.
set -u

REPO="${REPO:-ChasePlank/Fruit-Jump}"
WANT="${1:-}"

# ---- DRAFT MODE -----------------------------------------------------------------------------------------------
#
#   tools/check-release-notes.sh --draft <notes.md> <artifact> [<artifact>...]
#
# WHY. release.sh's step 6 runs this tool, and this tool reads PUBLISHED releases - so the notes that are about to
# be attached are the only ones never checked. On 2026-10-07 the whole release ran end to end and passed, and the
# draft notes for 1.15 were not among the fifteen releases it verified. The one document the publish command uses
# was the one document outside the check.
#
# The artifacts are named on the command line because a draft has no release to read them from. They are the names
# the publish command will attach, which is exactly what the notes have to agree with.
if [ "${1:-}" = "--draft" ]; then
  NOTES="${2:-}"
  [ -n "$NOTES" ] || { echo "usage: check-release-notes.sh --draft <notes.md> <artifact> [<artifact>...]" >&2; exit 2; }
  shift 2
  [ $# -gt 0 ] || { echo "check-release-notes: name the artifacts that will be attached" >&2; exit 2; }
  [ -f "$NOTES" ] || { echo "check-release-notes: no such notes file: $NOTES" >&2; exit 2; }
  command -v python3 >/dev/null 2>&1 || { echo "check-release-notes: no python3" >&2; exit 2; }
  DRAFT_NOTES="$NOTES" python3 - "$@" <<'PY'
import os, re, sys

# the same extraction the published-release check uses, deliberately - a draft checked by different rules is not
# evidence about what the publish will do
def promised(body):
    got = set(re.findall(r'[A-Za-z0-9._-]+\.(?:zip|tar\.gz|tgz|tar|AppImage)', body))
    got |= set(re.findall(r'Download\s*(?:\*\*)?\s*`?([A-Za-z0-9._-]+\.jar)', body))
    got |= set(re.findall(r'java\s+-jar\s+`?([A-Za-z0-9._-]+\.jar)', body))
    return got

path = os.environ["DRAFT_NOTES"]
body = open(path).read()
will_attach = set(sys.argv[1:])
named = promised(body)
missing = sorted(named - will_attach)
print(f"  draft {os.path.basename(path)}: names {len(named)} file(s); the release would attach {len(will_attach)}")
for n in sorted(named):
    print(f"      names   {n}")
for n in sorted(will_attach):
    print(f"      attaches {'(named)' if n in named else '(NOT NAMED)'} {n}")
if missing:
    print()
    for n in missing:
        print(f"     draft: notes say '{n}', which is not among the artifacts to be attached", file=sys.stderr)
    print(f"=== DRAFT FAILS: {len(missing)} name(s) in the notes are attached to nothing ===", file=sys.stderr)
    sys.exit(1)
print("=== DRAFT OK: every file the notes name is one this release would attach ===")
PY
  exit $?
fi

command -v gh >/dev/null 2>&1 || { echo "check-release-notes: no gh" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "check-release-notes: no python3" >&2; exit 2; }

if ! payload=$(gh api "repos/$REPO/releases?per_page=100"); then
  echo "check-release-notes: could not read releases for $REPO" >&2
  exit 2
fi

REPO="$REPO" WANT="$WANT" python3 - "$payload" <<'PY'
import json, os, re, sys

releases = json.loads(sys.argv[1])
want = os.environ.get("WANT", "").strip()
if want:
    releases = [r for r in releases if r.get("tag_name") == want]
if not releases:
    print("check-release-notes: no releases matched", file=sys.stderr); sys.exit(2)

# every asset name anywhere in the repository, so a note that deliberately points at another release is not
# called missing - 1.14's notes do exactly that and the first version flagged it
everywhere = {a["name"] for r in releases for a in r.get("assets", [])}

# WHAT COUNTS AS A PROMISED DOWNLOAD, and the first version got this wrong in the usual direction - it flagged
# EVERY filename in the prose, so "double-click `Holdfast.bat`" (inside the archive) and `java.exe` (which the
# bundle carries) were reported as missing assets. Twelve lines of noise over four real findings.
#
# NARROWED BY WHAT THE FILENAME IS FOR:
#   archives  .zip .tar.gz .tgz .AppImage - always the thing you download, never inside another one. Anywhere.
#   jars      only after "Download" or after "java -jar"; `Holdfast.jar` inside the Windows zip is carried by the
#             bundle, while `fruit-jump-1.13.jar` is the notes telling you what to fetch.
def promised(body):
    got = set(re.findall(r'[A-Za-z0-9._-]+\.(?:zip|tar\.gz|tgz|tar|AppImage)', body))
    got |= set(re.findall(r'Download\s*(?:\*\*)?\s*`?([A-Za-z0-9._-]+\.jar)', body))
    got |= set(re.findall(r'java\s+-jar\s+`?([A-Za-z0-9._-]+\.jar)', body))
    return got

bad = notes = 0
for r in releases:
    tag = r.get("tag_name", "?")
    have = {a["name"] for a in r.get("assets", [])}
    for n in sorted(promised(r.get("body") or "") - have):
        if n in everywhere:
            print(f"NOTE {tag}: names '{n}', which is on another release - fine, and worth a glance")
            notes += 1
        else:
            print(f"     {tag}: notes say '{n}', attached to NO release "
                  f"(this one has: {', '.join(sorted(have)) or 'nothing'})")
            bad += 1

print()
if bad == 0:
    print(f"=== {len(releases)} release(s) checked, {notes} cross-release mention(s), "
          f"every file the notes name is attached to a release ===")
    sys.exit(0)
print(f"=== {bad} release-note reference(s) name a file attached to nothing ===", file=sys.stderr)
sys.exit(1)
PY
