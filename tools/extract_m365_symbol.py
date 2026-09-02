# -*- coding: utf-8 -*-
"""Builds the Microsoft 365 badge mark from the published SVG's four squares.

That file is the symbol *plus* the "Microsoft 365" wordmark, 1319 units wide
against 213 tall. Fitted whole into a square badge the words would be a grey
smear - the same trap the "amazon prime" wordmark fell into. Only the symbol is
taken.

The squares are <rect> elements, not paths, which is why the earlier
path-only extraction found nothing but grey text and concluded the file had no
symbol in it. Their coordinates and colours are read from the file rather than
retyped, so the geometry is Microsoft's and not an approximation.

The symbol has a transparent background, unlike Google One's artwork which
carries its own white. A badge drawn edge to edge with transparency would show
the card behind it and break the tile shape, so a white ground is included and
the squares are inset on it.
"""
import io
import re
import sys

SRC, OUT = sys.argv[1], sys.argv[2]

svg = io.open(SRC, encoding='utf-8', errors='replace').read()

rects = []
for m in re.finditer(r'<rect\b([^>]*?)/?>', svg):
    attrs = m.group(1)

    def num(name):
        found = re.search(r'\b%s="([-\d.eE]+)"' % name, attrs)
        return float(found.group(1)) if found else 0.0

    fill = re.search(r'fill="(#[0-9a-fA-F]{6})"', attrs)
    if not fill:
        continue
    colour = fill.group(1).lower()
    # #737373 is the wordmark's own rectangle, not part of the symbol.
    if colour == '#737373':
        continue
    rects.append((num('x'), num('y'), num('width'), num('height'), colour))

if len(rects) != 4:
    raise SystemExit("expected the four symbol squares, found %d" % len(rects))

extent_x = max(x + w for x, y, w, h, c in rects)
extent_y = max(y + h for x, y, w, h, c in rects)
extent = max(extent_x, extent_y)

# Match how the other full-bleed marks sit: symbol centred, comfortable margin.
CANVAS = 300.0
FRACTION = 0.62
scale = CANVAS * FRACTION / extent
offset = (CANVAS - extent * scale) / 2.0

body = ['    <path\n'
        '        android:fillColor="#FFFFFFFF"\n'
        '        android:pathData="M0,0 H%g V%g H0 Z" />' % (CANVAS, CANVAS)]
for x, y, w, h, colour in rects:
    px, py = offset + x * scale, offset + y * scale
    pw, ph = w * scale, h * scale
    body.append(
        '    <path\n'
        '        android:fillColor="#FF%s"\n'
        '        android:pathData="M%.4f,%.4f h%.4f v%.4f h-%.4f Z" />'
        % (colour[1:].upper(), px, py, pw, ph, pw))

xml = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<!-- Microsoft 365 brand mark, full colour.\n'
    '\n'
    '     Bundled rather than fetched: requesting a logo from the brand\'s own\n'
    '     server would disclose which subscriptions the user tracks.\n'
    '\n'
    '     The symbol only. The published SVG is the four squares plus the\n'
    '     "Microsoft 365" wordmark, 1319 units wide against 213 tall, and fitted\n'
    '     whole into a square badge the words become an unreadable smear.\n'
    '\n'
    '     Square coordinates and colours are read from that file, not retyped,\n'
    '     so the geometry is Microsoft\'s. They are <rect> elements, which is why\n'
    '     an earlier path-only look at the file found only grey text and\n'
    '     concluded there was no symbol in it.\n'
    '\n'
    '     The white ground is ours: the symbol is transparent, and a badge drawn\n'
    '     edge to edge with transparency would show the card behind it. -->\n'
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="24dp"\n'
    '    android:height="24dp"\n'
    '    android:viewportWidth="%g"\n'
    '    android:viewportHeight="%g">\n'
    '%s\n'
    '</vector>\n' % (CANVAS, CANVAS, "\n".join(body))
)

io.open(OUT, 'w', encoding='utf-8', newline='\n').write(xml)
print("four squares: extent %.2f, scale %.4f, offset %.2f -> %d bytes"
      % (extent, scale, offset, len(xml)))
for x, y, w, h, c in rects:
    print("   %s at (%.2f, %.2f) %.2f x %.2f" % (c, x, y, w, h))
