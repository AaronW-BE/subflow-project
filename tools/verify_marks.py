# -*- coding: utf-8 -*-
"""Rebuilds every vector brand mark and checks it still matches what is committed.

colour_vector.py has been changed four times, each time for a real gap in it -
arc flags, CSS-class fills, group fill-rule inheritance, non-path shapes,
missing fills, transparent shapes. Every one of those was found because the
marks it had already produced were re-converted and compared. This makes that
check a command rather than an act of memory:

    python tools/verify_marks.py

The recipes are here because they were nearly lost. Re-converting Netflix
during the Figma work produced a file that differed from the committed one, and
the reason turned out to be the flags it was originally built with, recorded
nowhere. A mark whose recipe is unknown cannot be regression-checked, only
guessed at.

Only comments and the display name are ignored; geometry, colours, fill rules
and groups must match exactly.

Microsoft 365 is absent on purpose - it is built by make_m365.py from the four
<rect> elements of a wordmark-plus-symbol file, not by this converter. The
bitmap marks (Google One, Prime Video, Disney+) have no source to rebuild from.
"""
import io
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES = os.path.join(HERE, "logo-sources")
DRAWABLE = os.path.join(
    HERE, os.pardir, "android", "app", "src", "main", "res", "drawable")

# drawable name -> (source file, display name, extra flags)
MARKS = {
    "duolingo": ("duolingo-seeklogo.svg", "duolingo", []),
    "netflix": ("netflix-seeklogo.svg", "netflix",
                ["--background", "#000000", "--inset", "0.72"]),
    "max": ("hbo-max-2025-seeklogo.svg", "hbomax",
            ["--fill", "#FFFFFF", "--background", "#0D1017", "--inset", "0.80"]),
    "figma": ("figma-Symbol.svg", "Figma",
              ["--background", "#FFFFFF", "--inset", "0.62"]),
    # 1.35 rather than something under 1: this file already carries a wide
    # margin of its own, and at the usual inset the hash came out at 43% of the
    # badge where its neighbours sit near 70%.
    "slack": ("slack-Symbol.svg", "Slack",
              ["--background", "#FFFFFF", "--inset", "1.35"]),
}


def signature(path):
    """Everything about a drawable that is not a comment or a name."""
    s = io.open(path, encoding="utf-8").read()
    return {
        "paths": re.findall(r'android:pathData="([^"]+)"', s),
        "colours": re.findall(r'android:fillColor="([^"]+)"', s),
        "fillTypes": re.findall(r'android:fillType="([^"]+)"', s),
        "groups": re.findall(r"<group ([^>]*)>", s),
        "viewport": re.findall(r'android:viewport\w+="([^"]+)"', s),
    }


def main():
    tmp = os.path.join(HERE, "_verify_tmp.xml")
    failures = []

    for name, (source, display, flags) in sorted(MARKS.items()):
        committed = os.path.join(DRAWABLE, "brand_colour_%s.xml" % name)
        src = os.path.join(SOURCES, source)

        if not os.path.exists(committed):
            failures.append("%s: no committed drawable" % name)
            continue
        if not os.path.exists(src):
            failures.append("%s: missing source %s" % (name, source))
            continue

        subprocess.check_output(
            [sys.executable, os.path.join(HERE, "colour_vector.py"),
             src, display, tmp] + flags,
            stderr=subprocess.STDOUT)

        mine, theirs = signature(tmp), signature(committed)
        if mine == theirs:
            print("  %-10s reproduces exactly" % name)
        else:
            differing = [k for k in mine if mine[k] != theirs[k]]
            print("  %-10s DIFFERS in %s" % (name, ", ".join(differing)))
            failures.append("%s: %s" % (name, ", ".join(differing)))

    if os.path.exists(tmp):
        os.remove(tmp)

    if failures:
        print("\n%d mark(s) no longer reproduce:" % len(failures))
        for f in failures:
            print("  - %s" % f)
        return 1
    print("\nAll %d vector marks reproduce from source." % len(MARKS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
