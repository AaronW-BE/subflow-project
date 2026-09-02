# -*- coding: utf-8 -*-
"""Fits an arbitrary single-colour SVG into the 24x24 artboard the badge uses.

simple-icons already ships 24x24, but a logo taken from anywhere else has its
own viewBox - Disney+'s is 1041x565. Android vector drawables support a
viewport of any size, so rather than rewriting path coordinates the drawable
declares the source viewport and a group transform centres it inside 24x24.
That keeps the path data byte-identical to the source, which matters: rewriting
coordinates by hand is how a logo quietly ends up subtly wrong.
"""
import io
import re
import sys

from normalise_arcs import normalise

SRC, NAME, OUT = sys.argv[1], sys.argv[2], sys.argv[3]

svg = io.open(SRC, encoding='utf-8', errors='replace').read()

vb = re.search(r'viewBox="\s*([\d.eE+-]+)[,\s]+([\d.eE+-]+)[,\s]+'
               r'([\d.eE+-]+)[,\s]+([\d.eE+-]+)\s*"', svg)
if not vb:
    raise SystemExit("no viewBox in " + SRC)
minx, miny, vw, vh = (float(g) for g in vb.groups())

paths = re.findall(r'<path[^>]*?\sd="([^"]+)"', svg, re.S)
if not paths:
    raise SystemExit("no paths in " + SRC)

# Fit the long edge into 24 with a small margin, then centre the short edge.
MARGIN = 1.0
avail = 24.0 - 2 * MARGIN
scale = min(avail / vw, avail / vh)
tx = MARGIN + (avail - vw * scale) / 2.0 - minx * scale
ty = MARGIN + (avail - vh * scale) / 2.0 - miny * scale

body = "\n".join(
    '        <path\n            android:fillColor="#FFFFFFFF"\n'
    '            android:pathData="%s" />' % normalise(p.strip()).replace('&', '&amp;')
    for p in paths
)

xml = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<!-- %s brand mark. Bundled rather than fetched: requesting a logo from\n'
    '     the brand\'s own server would disclose which subscriptions the user\n'
    '     tracks. Source artwork is a single colour, redrawn white here so it\n'
    '     sits on the brand-coloured tile like every other mark.\n'
    '\n'
    '     The source viewBox is %gx%g, not the 24x24 the badge uses, so the\n'
    '     group transform below fits it. Path data is unmodified from the\n'
    '     source - rescaling coordinates by hand is how a logo ends up subtly\n'
    '     wrong. -->\n'
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="24dp"\n'
    '    android:height="24dp"\n'
    '    android:viewportWidth="24"\n'
    '    android:viewportHeight="24">\n'
    '    <group\n'
    '        android:scaleX="%.6f"\n'
    '        android:scaleY="%.6f"\n'
    '        android:translateX="%.4f"\n'
    '        android:translateY="%.4f">\n'
    '%s\n'
    '    </group>\n'
    '</vector>\n' % (NAME, vw, vh, scale, scale, tx, ty, body)
)

io.open(OUT, 'w', encoding='utf-8', newline='\n').write(xml)
print("%s: viewBox %gx%g -> scale %.5f, offset (%.2f, %.2f), %d paths, %d bytes"
      % (NAME, vw, vh, scale, tx, ty, len(paths), len(xml)))
