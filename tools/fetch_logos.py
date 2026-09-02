# -*- coding: utf-8 -*-
"""Fetches simple-icons brand glyphs and converts them to Android vector drawables.

Bundling rather than fetching at runtime is deliberate: a request to a brand's
own server for its logo would tell that brand which subscriptions the user
tracks, which is exactly the data the app promises never leaves the device.
"""
import io
import os
import re
import subprocess
import sys

from normalise_arcs import normalise

# preset id -> simple-icons slug. None means simple-icons has no glyph for it,
# usually because the brand is not in the set or the mark is a wordmark rather
# than a symbol. Those keep the existing initial-letter tile.
SLUGS = {
    "netflix": "netflix",
    "spotify": "spotify",
    "youtube": "youtube",
    # simple-icons carries no Disney mark. Its wordmark is a single colour, so
    # it is fitted from the published SVG by fit_vector.py instead.
    "disney": None,
    "max": "hbomax",
    "appletv": "appletv",
    "primevideo": "primevideo",
    "hulu": "hulu",
    "crunchyroll": "crunchyroll",
    "applemusic": "applemusic",
    "chatgpt": "openai",
    "claude": "claude",
    "github_copilot": "githubcopilot",
    "notion": "notion",
    "figma": "figma",
    "adobe_cc": "adobecreativecloud",
    # No mark here is deliberate. "microsoftoffice" and "google" were tried and
    # are the wrong products - a sibling's old icon and the parent company. A
    # recognisable wrong mark is worse than a letter tile, because it still
    # reads as an answer. Both real logos are multi-colour and cannot be reduced
    # to a white silhouette.
    "microsoft365": None,
    "slack": "slack",
    "icloud": "icloud",
    "google_one": None,
    "dropbox": "dropbox",
    "backblaze": "backblaze",
    "amazon_prime": "amazonprime",
    "nordvpn": "nordvpn",
    "1password": "1password",
    "psplus": "playstation",
    "xbox_gamepass": "xbox",
    "nintendo_online": "nintendoswitch",
    "headspace": "headspace",
    "strava": "strava",
    "duolingo": "duolingo",
    "nytimes": "newyorktimes",
    "medium": "medium",
}

CDN = "https://cdn.jsdelivr.net/npm/simple-icons@latest/icons/%s.svg"
OUT = sys.argv[1]


def fetch(slug):
    """Returns the SVG text, or None when simple-icons has no such glyph."""
    proc = subprocess.run(
        ["curl", "-sL", "-m", "20", CDN % slug],
        capture_output=True, text=True,
    )
    body = proc.stdout
    if not body.startswith("<svg"):
        return None
    return body


def to_vector(svg, name):
    """Converts a single-path 24x24 simple-icons SVG to an Android vector.

    The glyph is drawn white and sits on the existing brand-coloured tile, so
    it slots into the badge exactly where the initial letter used to be. Colour
    is fixed rather than tinted at the call site because these are logos - a
    half-transparent or theme-tinted brand mark reads as a rendering bug.
    """
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
    if not paths:
        return None
    body = "\n".join(
        '    <path\n        android:fillColor="#FFFFFFFF"\n'
        '        android:pathData="%s" />' % normalise(p).replace("&", "&amp;")
        for p in paths
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- %s brand mark, from simple-icons. Bundled rather than fetched:\n'
        '     requesting a logo from the brand\'s own server would disclose which\n'
        '     subscriptions the user tracks. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="24"\n'
        '    android:viewportHeight="24">\n'
        '%s\n'
        '</vector>\n' % (name, body)
    )


def main():
    os.makedirs(OUT, exist_ok=True)
    ok, missing = [], []
    for preset_id, slug in sorted(SLUGS.items()):
        svg = fetch(slug) if slug else None
        vector = to_vector(svg, preset_id) if svg else None
        if not vector:
            missing.append((preset_id, slug))
            continue
        # Android resource names: lowercase, digits and underscore only, and
        # they may not start with a digit ("1password").
        res = "brand_" + re.sub(r"[^a-z0-9_]", "_", preset_id.lower())
        io.open(os.path.join(OUT, res + ".xml"), "w",
                encoding="utf-8", newline="\n").write(vector)
        ok.append((preset_id, res, len(vector)))

    print("resolved %d of %d" % (len(ok), len(SLUGS)))
    total = sum(size for _, _, size in ok)
    print("total vector xml: %.1f KB" % (total / 1024.0))
    if missing:
        print("\nno glyph (keep the letter tile):")
        for preset_id, slug in missing:
            print("  %-18s tried slug %r" % (preset_id, slug))


main()
