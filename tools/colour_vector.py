# -*- coding: utf-8 -*-
"""Converts a multi-colour SVG logo into an Android vector drawable.

Most marks in this app are single-colour silhouettes drawn on a brand tile.
A few logos cannot work that way — Duolingo's owl and Netflix's N are several
colours, and flattened to one they stop being the logo. Those keep their own
colours and fill the badge edge to edge.

Vector rather than a bitmap: same fidelity at any size, a fraction of the bytes,
and it cannot go stale the way a scraped store icon can.

    colour_vector.py SOURCE.svg NAME OUT.xml
        [--background #RRGGBB] [--inset F] [--fill #RRGGBB]

Handles what real logo exports do that a naive converter gets wrong:

  * fill-rule is inherited from an enclosing <g>. Duolingo's file sets it there
    and seven of its nine paths need it; without it the owl's eyes fill solid
    and the face becomes a green blob.
  * fills arrive as CSS classes rather than attributes. Netflix's file carries
    `.st0{fill:#b1060f}` in a <style> block, so an attribute-only reader paints
    every path black.
  * shapes are not all <path>. Illustrator keeps circles, polygons and rects as
    themselves — the dot over HBO Max's "O" is a <circle>, and a path-only
    reader drops it without saying so.
  * an SVG may declare no fill at all, meaning black. `--fill` says what the
    artwork should be instead, for a mark that has to sit on a dark tile.
  * groups nest and carry transforms. Netflix's are translate + scale, two deep.
  * a viewBox need not start at 0,0 or be square. Squeezing a 122x222 logo into
    a square drawable stretches it, so it is centred instead.

`--inset` shrinks the artwork inside the square; a mark that touches the badge's
rounded corners looks cramped, and real app icons leave a margin.

Paths filled with a gradient are skipped and reported. Android can express
gradients but not a `gradientTransform` matrix without baking it, and on the one
logo where this came up the gradient was an invisible depth effect at badge size
— checked by rendering with and without it.
"""
import io
import re
import sys

from normalise_arcs import normalise


def parse_style_classes(svg):
    """Returns {class name: {property: value}} from any <style> blocks."""
    classes = {}
    for block in re.findall(r'<style[^>]*>([\s\S]*?)</style>', svg):
        for name, body in re.findall(r'\.([A-Za-z0-9_-]+)\s*\{([^}]*)\}', block):
            props = {}
            for decl in body.split(';'):
                if ':' in decl:
                    key, _, value = decl.partition(':')
                    props[key.strip()] = value.strip()
            classes[name] = props
    return classes


def attr(tag, name):
    m = re.search(r'\b%s="([^"]*)"' % name, tag)
    return m.group(1) if m else None


def resolve(tag, classes, prop):
    """A presentation property, from the attribute or the element's class."""
    direct = attr(tag, prop)
    if direct:
        return direct
    for name in (attr(tag, 'class') or '').split():
        if name in classes and prop in classes[name]:
            return classes[name][prop]
    return None


def normalise_colour(value):
    if not value:
        return None
    value = value.strip().lower()
    if value.startswith('url('):
        return 'GRADIENT'
    if value == 'none':
        return None
    if re.fullmatch(r'#[0-9a-f]{3}', value):
        return '#' + ''.join(c * 2 for c in value[1:])
    if re.fullmatch(r'#[0-9a-f]{6}', value):
        return value
    return {'black': '#000000', 'white': '#ffffff'}.get(value)


def android_group(transform):
    """SVG transform to Android <group> attributes, or None if there is none."""
    if not transform:
        return None
    parts = []
    pair = re.search(r'translate\(\s*([-\d.eE]+)[,\s]+([-\d.eE]+)\s*\)', transform)
    if pair:
        parts.append('android:translateX="%s"' % pair.group(1))
        parts.append('android:translateY="%s"' % pair.group(2))
    else:
        single = re.search(r'translate\(\s*([-\d.eE]+)\s*\)', transform)
        if single:
            parts.append('android:translateX="%s"' % single.group(1))
    scale = re.search(r'scale\(\s*([-\d.eE]+)(?:[,\s]+([-\d.eE]+))?\s*\)', transform)
    if scale:
        parts.append('android:scaleX="%s"' % scale.group(1))
        parts.append('android:scaleY="%s"' % (scale.group(2) or scale.group(1)))
    return " ".join(parts) if parts else None


def shape_to_path(kind, tag):
    """Turns the non-<path> SVG shapes into path data.

    Illustrator exports circles, polygons and rects as themselves rather than
    flattening them, so a path-only reader silently drops parts of a logo. HBO
    Max's file keeps the dot over the "O" as a <circle> and the HBO block as a
    <polygon>; without this the mark comes out as three quarters of itself.
    """
    def num(name, default=0.0):
        m = re.search(r'\b%s="([-\d.eE]+)"' % name, tag)
        return float(m.group(1)) if m else default

    if kind in ('circle', 'ellipse'):
        cx, cy = num('cx'), num('cy')
        rx = num('r') or num('rx')
        ry = num('r') or num('ry')
        if rx <= 0 or ry <= 0:
            return None
        # Two half-arcs: a single 360-degree arc draws nothing.
        return ('M%g,%g a%g,%g 0 1 0 %g,0 a%g,%g 0 1 0 %g,0 Z'
                % (cx - rx, cy, rx, ry, 2 * rx, rx, ry, -2 * rx))

    if kind in ('polygon', 'polyline'):
        raw = re.search(r'points="([^"]+)"', tag)
        if not raw:
            return None
        nums = [float(v) for v in
                re.findall(r'-?[\d.]+(?:[eE][-+]?\d+)?', raw.group(1))]
        if len(nums) < 6:
            return None
        pairs = list(zip(nums[0::2], nums[1::2]))
        d = 'M%g,%g ' % pairs[0] + " ".join('L%g,%g' % pt for pt in pairs[1:])
        return d + (' Z' if kind == 'polygon' else '')

    if kind == 'rect':
        x, y, w, h = num('x'), num('y'), num('width'), num('height')
        if w <= 0 or h <= 0:
            return None
        return 'M%g,%g h%g v%g h%g Z' % (x, y, w, h, -w)

    return None


def convert(svg, name, background=None, inset=1.0, default_fill=None):
    classes = parse_style_classes(svg)

    vb = re.search(r'viewBox="\s*([-\d.eE]+)[,\s]+([-\d.eE]+)[,\s]+'
                   r'([-\d.eE]+)[,\s]+([-\d.eE]+)\s*"', svg)
    if not vb:
        raise SystemExit("no viewBox in " + name)
    min_x, min_y, width, height = (float(g) for g in vb.groups())

    canvas = max(width, height)
    pad_x = (canvas - width * inset) / 2.0 - min_x * inset
    pad_y = (canvas - height * inset) / 2.0 - min_y * inset

    out, skipped, depth = [], [], 0
    # fill-rule is inherited, so an enclosing <g> can set it for paths that
    # never mention it. Each entry is (rule outside this <g>, group emitted?).
    rule_stack, inherited_rule = [], None

    def pad():
        return '    ' + '    ' * depth

    shapes = 'path|circle|ellipse|polygon|polyline|rect'
    for token in re.finditer(r'<(/?)(g|%s)\b([^>]*?)/?>' % shapes, svg):
        closing, kind, rest = token.group(1), token.group(2), token.group(3)

        if kind == 'g':
            if closing:
                if rule_stack:
                    inherited_rule, emitted = rule_stack.pop()
                    if emitted:
                        depth -= 1
                        out.append(pad() + '</group>')
                continue
            attrs = android_group(attr(rest, 'transform'))
            rule_stack.append((inherited_rule, bool(attrs)))
            own = (resolve(rest, classes, 'fill-rule')
                   or resolve(rest, classes, 'clip-rule'))
            if own:
                inherited_rule = own
            # A <group> is emitted only to carry a transform; the rule is
            # tracked either way.
            if attrs:
                out.append(pad() + '<group %s>' % attrs)
                depth += 1
            continue

        d = attr(rest, 'd') if kind == 'path' else shape_to_path(kind, rest)
        if not d:
            continue

        # An SVG that declares no fill paints black, which is invisible on a
        # dark tile. default_fill says what the artwork should actually be.
        fill = normalise_colour(resolve(rest, classes, 'fill')) or default_fill
        if fill == 'GRADIENT':
            skipped.append(attr(rest, 'id') or '(unnamed)')
            continue
        if not fill:
            continue

        rule = (resolve(rest, classes, 'fill-rule')
                or resolve(rest, classes, 'clip-rule')
                or inherited_rule)
        parts = [pad() + '<path',
                 pad() + '    android:fillColor="#FF%s"' % fill[1:].upper()]
        if rule == 'evenodd':
            parts.append(pad() + '    android:fillType="evenOdd"')
        parts.append(pad() + '    android:pathData="%s" />'
                     % normalise(d).replace('&', '&amp;'))
        out.append("\n".join(parts))

    while depth > 0:
        depth -= 1
        out.append('    ' + '    ' * depth + '</group>')

    body = "\n".join(out)
    if pad_x or pad_y or inset != 1.0:
        scale_attrs = ''
        if inset != 1.0:
            scale_attrs = ' android:scaleX="%g" android:scaleY="%g"' % (inset, inset)
        nested = "\n".join('    ' + line for line in body.split('\n'))
        body = ('    <group android:translateX="%g" android:translateY="%g"%s>\n'
                '%s\n'
                '    </group>' % (pad_x, pad_y, scale_attrs, nested))
    if background:
        ground = ('    <path\n'
                  '        android:fillColor="#FF%s"\n'
                  '        android:pathData="M0,0 H%g V%g H0 Z" />'
                  % (background.lstrip('#').upper(), canvas, canvas))
        body = ground + "\n" + body

    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- %s brand mark, full colour.\n'
        '\n'
        '     Bundled rather than fetched: requesting a logo from the brand\'s own\n'
        '     server would disclose which subscriptions the user tracks.\n'
        '\n'
        '     Multi-colour, so it fills the badge edge to edge and carries its own\n'
        '     background instead of sitting on the brand-coloured tile. Flattened\n'
        '     to a single-colour silhouette this mark stops being the logo.\n'
        '\n'
        '     Generated by tools/colour_vector.py. Path data is unmodified from\n'
        '     the source; rescaling coordinates by hand is how a logo ends up\n'
        '     subtly wrong. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="%g"\n'
        '    android:viewportHeight="%g">\n'
        '%s\n'
        '</vector>\n' % (name, canvas, canvas, body)
    )
    return xml, skipped, canvas


def read_flag(name, default=None):
    """Reads --name=value or --name value from argv."""
    for arg in sys.argv[1:]:
        if arg.startswith(name + '='):
            return arg.split('=', 1)[1]
    if name in sys.argv:
        index = sys.argv.index(name)
        if index + 1 < len(sys.argv):
            return sys.argv[index + 1]
    return default


def positional():
    """argv without flags or the values they consume."""
    raw, out, skip = sys.argv[1:], [], False
    for i, arg in enumerate(raw):
        if skip:
            skip = False
            continue
        if arg.startswith('--'):
            if '=' not in arg and i + 1 < len(raw) and not raw[i + 1].startswith('--'):
                skip = True
            continue
        out.append(arg)
    return out


def main():
    args = positional()
    if len(args) < 3:
        raise SystemExit(__doc__)
    src, name, out = args[0], args[1], args[2]

    svg = io.open(src, encoding='utf-8', errors='replace').read()
    xml, skipped, canvas = convert(
        svg, name,
        read_flag('--background'),
        float(read_flag('--inset', '1.0')),
        normalise_colour(read_flag('--fill')))
    io.open(out, 'w', encoding='utf-8', newline='\n').write(xml)

    print("%s: square viewport %g, %d bytes" % (name, canvas, len(xml)))
    if skipped:
        print("  skipped %d gradient-filled path(s): %s"
              % (len(skipped), ", ".join(skipped)))


if __name__ == '__main__':
    main()
