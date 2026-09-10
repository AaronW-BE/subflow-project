"""Check docs/play-store-copy.md before it goes into Play Console.

Run from the repo root:

    python tools/check_play_copy.py

Checks, and exits non-zero if any fail:

- the newest release-notes heading names the versionCode/versionName that
  android/app/build.gradle.kts will actually build;
- every language the app ships has notes in the newest block, and no note is
  over Play's 500-character limit;
- every store listing is within 30 / 80 / 4000 characters;
- Simplified Chinese contains nothing outside GB2312 (so no Traditional
  characters slipped in) and Traditional Chinese nothing outside Big5 (so no
  Simplified ones).

It checks form, not meaning. Whether a claim is still true is a matter of
reading the code; see the claims table in the copy file.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COPY = os.path.join(ROOT, "docs", "play-store-copy.md")
GRADLE = os.path.join(ROOT, "android", "app", "build.gradle.kts")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

# Resource directory -> the Play Console language codes it is shown under.
PLAY_CODES = {
    "values": ["en-US"],
    "values-zh": ["zh-CN"],
    "values-b+zh+Hant": ["zh-TW", "zh-HK"],
    "values-de": ["de-DE"],
    "values-fr": ["fr-FR"],
    "values-es": ["es-ES"],
    "values-ja": ["ja-JP"],
}
SIMPLIFIED = {"zh-CN"}
TRADITIONAL = {"zh-TW", "zh-HK"}
NOTE_LIMIT = 500
LISTING_LIMITS = (30, 80, 4000)

failures = []


def fail(msg):
    failures.append(msg)


def script_problems(code, text):
    encoding = "gb2312" if code in SIMPLIFIED else "cp950" if code in TRADITIONAL else None
    if not encoding:
        return []
    bad = set()
    for ch in text:
        if ord(ch) < 0x3400:
            continue
        try:
            ch.encode(encoding)
        except UnicodeEncodeError:
            bad.add(ch)
    return sorted(bad)


def shipped_codes():
    codes = []
    for directory, play in PLAY_CODES.items():
        if os.path.exists(os.path.join(RES, directory, "strings.xml")):
            codes.extend(play)
    unknown = [
        d for d in os.listdir(RES)
        if d.startswith("values-") and os.path.exists(os.path.join(RES, d, "strings.xml"))
        and d not in PLAY_CODES
    ]
    for d in unknown:
        fail(f"{d} ships strings but has no Play code in PLAY_CODES; add it to this script")
    return codes


def main():
    copy = io.open(COPY, encoding="utf-8").read()
    gradle = io.open(GRADLE, encoding="utf-8").read()

    version_code = re.search(r"^\s*versionCode = (\d+)", gradle, re.M).group(1)
    version_name = re.search(r'^\s*versionName = "([^"]+)"', gradle, re.M).group(1)

    # ---------------------------------------------------------- release notes
    headings = list(re.finditer(r"^## (\S+) \(versionCode (\d+)\): release notes", copy, re.M))
    if not headings:
        fail("no '## X.Y.Z (versionCode N): release notes' heading found")
    else:
        newest = headings[0]
        name, code = newest.group(1), newest.group(2)
        if (name, code) != (version_name, version_code):
            fail(f"newest notes are for {name} (versionCode {code}) but gradle builds "
                 f"{version_name} (versionCode {version_code})")
        end = headings[1].start() if len(headings) > 1 else len(copy)
        section = copy[newest.start():end]
        notes = dict(re.findall(r"<([a-z]{2}-[A-Z]{2})>\n(.*?)\n</\1>", section, re.S))
        print(f"release notes {name} (versionCode {code}):")
        for play_code in shipped_codes():
            text = notes.get(play_code)
            if text is None:
                fail(f"release notes: no <{play_code}> for a shipped language")
                continue
            status = "ok"
            if len(text) > NOTE_LIMIT:
                fail(f"release notes <{play_code}>: {len(text)} > {NOTE_LIMIT}")
                status = "OVER"
            bad = script_problems(play_code, text)
            if bad:
                fail(f"release notes <{play_code}>: wrong-script characters {''.join(bad)}")
                status = "SCRIPT"
            print(f"  {play_code:6} {len(text):4}/{NOTE_LIMIT}  {status}")

    # --------------------------------------------------------------- listings
    start = copy.find("## Store listings")
    if start < 0:
        fail("no '## Store listings' section found")
    else:
        stop = copy.find("\n## ", start + 1)
        listings = copy[start:stop if stop > 0 else len(copy)]
        print("store listings (title / short / full):")
        for part in re.split(r"\n### ", listings)[1:]:
            code = part.split()[0]
            blocks = re.findall(r"```\n(.*?)\n```", part, re.S)[:3]
            if len(blocks) != 3:
                fail(f"listing {code}: expected 3 fields, found {len(blocks)}")
                continue
            sizes = []
            for label, block, limit in zip(("title", "short", "full"), blocks, LISTING_LIMITS):
                sizes.append(f"{len(block)}/{limit}")
                if len(block) > limit:
                    fail(f"listing {code} {label}: {len(block)} > {limit}")
            covered = [code] + (["zh-HK"] if code == "zh-TW" else [])
            for c in covered:
                bad = script_problems(c, "".join(blocks))
                if bad:
                    fail(f"listing {c}: wrong-script characters {''.join(bad)}")
            print(f"  {code:6} " + "  ".join(sizes))

    if failures:
        print("\nFAILED:")
        for f in failures:
            print("  - " + f)
        sys.exit(1)
    print("\nall checks passed")


if __name__ == "__main__":
    main()
