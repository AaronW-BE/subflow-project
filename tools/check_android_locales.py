#!/usr/bin/env python3
"""Check complete Android translations and per-quantity format placeholders."""

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[1] / "android/app/src/main/res"
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]")


def placeholders(element):
    text = "".join(element.itertext()).replace("%%", "")
    return sorted(FORMAT.findall(text))


def load(path):
    elements = list(ET.parse(path).getroot())
    names = Counter(element.attrib["name"] for element in elements)
    return {element.attrib["name"]: element for element in elements}, [
        name for name, count in names.items() if count > 1
    ]


def main():
    base, _ = load(RES / "values/strings.xml")
    required = {name for name, element in base.items() if element.get("translatable") != "false"}
    errors = []
    paths = sorted(RES.glob("values*/strings.xml"))
    for path in paths:
        translated, duplicates = load(path)
        problems = [f"duplicate {name}" for name in duplicates]
        problems += [f"missing {name}" for name in sorted(required - translated.keys())]
        problems += [f"unknown {name}" for name in sorted(translated.keys() - base.keys())]
        for name in sorted(required & translated.keys()):
            source, target = base[name], translated[name]
            if source.tag != target.tag:
                problems.append(f"type mismatch {name}")
            elif source.tag == "plurals":
                quantities = {item.attrib["quantity"]: item for item in source}
                target_quantities = [item.attrib["quantity"] for item in target]
                if "other" not in target_quantities or len(target_quantities) != len(set(target_quantities)):
                    problems.append(f"invalid quantities {name}")
                for item in target:
                    quantity = item.attrib["quantity"]
                    reference = quantities.get(quantity, quantities["other"])
                    if placeholders(item) != placeholders(reference):
                        problems.append(f"format mismatch {name}[{quantity}]")
            elif placeholders(source) != placeholders(target):
                problems.append(f"format mismatch {name}")
        print(f"{path.parent.name}: {len(translated)} keys, {len(problems)} errors")
        errors.extend(f"{path.parent.name}: {problem}" for problem in problems)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"PASS: {len(paths)} locales; complete keys, no duplicates, format parity")
    return 0


if __name__ == "__main__":
    sys.exit(main())
