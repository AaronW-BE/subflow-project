# -*- coding: utf-8 -*-
"""Expands compact SVG elliptical-arc flags so Android can parse them.

An arc command takes seven parameters: rx ry x-axis-rotation large-arc-flag
sweep-flag x y. The two flags are single digits, and SVG permits them to run
together with each other and with the following coordinate, so a minifier emits

    a9.23 9.23 0 00-.24-2.19

meaning flags 0 and 0 followed by (-0.24, -2.19). Android's VectorDrawable path
parser tokenises numbers greedily: it reads "00" as a single value, which shifts
every later parameter by one and turns the rest of the subpath into nonsense.
The browser renders the same data correctly, which is what made this look like a
conversion bug rather than a parser incompatibility.

Rewriting them with explicit separators is lossless — the numbers are untouched,
only the whitespace between them changes.
"""
import io
import re
import sys

# A number as SVG path grammar allows: optional sign, digits with optional
# decimal point on either side, optional exponent.
NUMBER = re.compile(r'[+-]?(?:\d*\.\d+|\d+\.?)(?:[eE][+-]?\d+)?')


def _read_number(text, i):
    m = NUMBER.match(text, i)
    if not m:
        return None, i
    return m.group(0), m.end()


def _skip_separators(text, i):
    while i < len(text) and text[i] in ' \t\r\n,':
        i += 1
    return i


def normalise(d):
    """Returns d with every arc command's parameters explicitly separated."""
    out = []
    i = 0
    while i < len(d):
        ch = d[i]
        if ch not in 'Aa':
            out.append(ch)
            i += 1
            continue

        out.append(ch)
        i += 1
        # Consume as many 7-parameter groups as follow this command letter.
        while True:
            j = _skip_separators(d, i)
            params = []
            for index in range(7):
                j = _skip_separators(d, j)
                if index in (3, 4):
                    # Flags are exactly one character, '0' or '1'.
                    if j < len(d) and d[j] in '01':
                        params.append(d[j])
                        j += 1
                    else:
                        params = None
                        break
                else:
                    value, j = _read_number(d, j)
                    if value is None:
                        params = None
                        break
                    params.append(value)
            if not params:
                break
            out.append(' ' + ' '.join(params))
            i = j
            # Another group only if a number follows; anything else ends it.
            k = _skip_separators(d, i)
            if k >= len(d) or not NUMBER.match(d, k):
                break
    return ''.join(out)


def main():
    changed = 0
    for path in sys.argv[1:]:
        xml = io.open(path, encoding='utf-8').read()
        updated = re.sub(
            r'android:pathData="([^"]+)"',
            lambda m: 'android:pathData="%s"' % normalise(m.group(1)),
            xml,
        )
        if updated != xml:
            io.open(path, 'w', encoding='utf-8', newline='\n').write(updated)
            print("  normalised %s" % path.replace('\\', '/').split('/')[-1])
            changed += 1
    print("%d file(s) changed" % changed)


if __name__ == '__main__':
    main()
