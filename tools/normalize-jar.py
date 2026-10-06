#!/usr/bin/env python3
"""
Set every timestamp in a jar to one fixed value, so the same source produces the same bytes.

    python3 tools/normalize-jar.py <jar>

WHY. `jar` records the current time on the entries IT writes - META-INF/ and META-INF/MANIFEST.MF - and it does
that after any mtimes you set on the extracted tree, so those two entries cannot be fixed from outside. On
2026-10-06 two consecutive builds from identical source produced jars differing in 788 bytes; setting the mtimes
of the extracted files first took that to 4, and the 4 were those two entries.

It costs two things. `artifact-changed.sh` compares BYTES and refuses a release identical to the published one,
and a rebuild always changes the bytes - so that guard catches a re-uploaded file and not a rebuilt one, which is
the case it was written for. And every rebuild leaves the committed jar dirty in git even when nothing changed,
which trains you to ignore the one file that matters.

WHAT IT DOES NOT DO. It does not touch anything but the two mod-time fields in each header. The bytes it rewrites
are metadata; the entries, their order, their CRCs and their contents are untouched, and the result is verified by
running the jar and by comparing it against a fresh compile of the source.
"""
import struct
import sys

# 2026-01-01 00:00:00 in DOS format: year-1980 in the top 7 bits, month and day below; time is zero.
DOS_DATE = ((2026 - 1980) << 9) | (1 << 5) | 1
DOS_TIME = 0


def normalize(path):
    d = bytearray(open(path, "rb").read())
    fixed = 0

    # local file headers: 30 bytes, mod time at 10, mod date at 12
    i = 0
    while True:
        i = d.find(b"PK\x03\x04", i)
        if i < 0:
            break
        d[i + 10:i + 12] = struct.pack("<H", DOS_TIME)
        d[i + 12:i + 14] = struct.pack("<H", DOS_DATE)
        fixed += 1
        i += 4

    # central directory headers: 46 bytes, mod time at 12, mod date at 14
    i = 0
    while True:
        i = d.find(b"PK\x01\x02", i)
        if i < 0:
            break
        d[i + 12:i + 14] = struct.pack("<H", DOS_TIME)
        d[i + 14:i + 16] = struct.pack("<H", DOS_DATE)
        fixed += 1
        i += 4

    open(path, "wb").write(bytes(d))
    return fixed


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: normalize-jar.py <jar>", file=sys.stderr)
        sys.exit(2)
    print(f"  normalized {normalize(sys.argv[1])} header(s)")
