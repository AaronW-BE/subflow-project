# -*- coding: utf-8 -*-
"""Converts a multi-colour SVG logo into an Android vector drawable.

Most marks in this app are single-colour silhouettes drawn on a brand tile.
A few logos cannot work that way — Duolingo's owl and Google One's ribbon are
several colours, and flattened to one they become unreadable blobs. Those keep
their own colours and fill the badge edge to edge.

Vector rather than a bitmap: same fidelity at any size, and a fraction of the
bytes.

Two details this handles that a naive converter gets wrong:

  * fill-rule is inherited. These files set `fill-rule="evenodd"` on a wrapping
    <g>, and paths that need it silently fill solid without it — the holes in
    the owl's eyes disappear.
  * the viewBox may not start at 0,0 or be 24 wide. Rather than rewrite
    coordinates, the drawable declares the source viewport and lets Android map
    it, so path data stays byte-identical to the source.
"""
import io
import re
import sys

from normalise_arcs import normalise

SRC, NAME, OUT = sys.argv[1], sys.argv[2], sys.argv[3]

svg = io.open(SRC, encoding='utf-8', errors='replace').read()

vb = re.search(r'viewBox="\s*([-\d.eE]+)[,\s]+([-\d.eE]+)[,\s]+'
               r'([-\d.eE]+)[,\s]+([-\d.eE]+)\s*"', svg)
if not vb:
    raise SystemExit("no viewBox in " + SRC)
min_x, min_y, width, height = (float(g) for g in vb.groups())


def attr(tag, name):
    m = re.search(r'\b%s="([^"]*)"' % name, tag)
    return m.group(1) if m else None


def emit(d, fill, even_odd):
    """One <path> element, with the colour and fill rule it actually needs."""
    colour = (fill or "#000000").strip()
    if colour.startswith("#") and len(colour) == 4:  # #abc -> #aabbcc
        colour = "#" + "".join(c * 2 for c in colour[1:])
    if not colour.startswith("#"):
        colour = "#000000"
    lines = ['    <path',
             '        android:fillColor="#FF%s"' % colour[1:].upper()]
    if even_odd:
        lines.append('        android:fillType="evenOdd"')
    lines.append('        android:pathData="%s" />' % normalise(d).replace("&", "&amp;"))
    return "\n".join(lines)


# Walk the file in order, tracking whether an enclosing <g> set evenodd.
paths, group_evenodd, depth_stack = [], False, []
for token in re.finditer(r'<(/?)(g|path)\b([^>]*)>', svg):
    closing, kind, rest = token.group(1), token.group(2), token.group(3)
    if kind == 'g':
        if closing:
            group_evenodd = depth_stack.pop() if depth_stack else False
        else:
            depth_stack.append(group_evenodd)
            if attr(rest, 'fill-rule') == 'evenodd':
                group_evenodd = True
        continue

    d = attr(rest, 'd')
    if not d:
        continue
    rule = attr(rest, 'fill-rule')
    even_odd = group_evenodd if rule is None else (rule == 'evenodd')
    paths.append(emit(d, attr(rest, 'fill'), even_odd))

if not paths:
    raise SystemExit("no paths in " + SRC)

# A non-zero viewBox origin is expressed as a translate on the whole drawing.
body = "\n".join(paths)
if min_x or min_y:
    indented = "\n".join("    " + line for line in body.split("\n"))
    body = ('    <group android:translateX="%g" android:translateY="%g">\n'
            '%s\n    </group>' % (-min_x, -min_y, indented))

xml = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<!-- %s brand mark, full colour.\n'
    '\n'
    '     Bundled rather than fetched: requesting a logo from the brand\'s own\n'
    '     server would disclose which subscriptions the user tracks.\n'
    '\n'
    '     Multi-colour, so it fills the badge edge to edge and carries its own\n'
    '     background instead of sitting on the brand-coloured tile. Flattened to\n'
    '     a single-colour silhouette this mark is an unreadable blob.\n'
    '\n'
    '     Vector rather than a bitmap: crisp at any size, and it cannot go stale\n'
    '     the way a store\'s app icon can - the seasonal variant both app stores\n'
    '     were serving is exactly what this replaces. -->\n'
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="24dp"\n'
    '    android:height="24dp"\n'
    '    android:viewportWidth="%g"\n'
    '    android:viewportHeight="%g">\n'
    '%s\n'
    '</vector>\n' % (NAME, width, height, body)
)

io.open(OUT, 'w', encoding='utf-8', newline='\n').write(xml)
print("%s: viewBox %g x %g (origin %g,%g), %d paths, %d bytes"
      % (NAME, width, height, min_x, min_y, len(paths), len(xml)))
