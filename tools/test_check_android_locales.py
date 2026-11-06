import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import check_android_locales as checker


class LocaleCheckTest(unittest.TestCase):
    def run_check(self, translation):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder, content in (
                ("values", '<string name="label">Trial %1$s</string>'
                 '<string name="internal" translatable="false">internal</string>'
                 '<plurals name="days"><item quantity="one">%d day</item>'
                 '<item quantity="other">%d days</item></plurals>'),
                ("values-zh", translation),
            ):
                path = root / folder / "strings.xml"
                path.parent.mkdir()
                path.write_text(f"<resources>{content}</resources>", encoding="utf-8")
            with patch.object(checker, "RES", root), contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                return checker.main()

    good = ('<string name="label">试用 %1$s</string>'
            '<plurals name="days"><item quantity="other">%d 天</item></plurals>')

    def test_omits_nontranslatable_and_allows_different_plural_categories(self):
        self.assertEqual(0, self.run_check(self.good))

    def test_rejects_missing_key(self):
        self.assertEqual(1, self.run_check(self.good.replace('<string name="label">试用 %1$s</string>', '')))

    def test_rejects_duplicate_key(self):
        self.assertEqual(1, self.run_check(self.good + '<string name="label">重复</string>'))

    def test_rejects_wrong_string_placeholder(self):
        self.assertEqual(1, self.run_check(self.good.replace('%1$s', '%1$d')))

    def test_rejects_wrong_plural_placeholder(self):
        self.assertEqual(1, self.run_check(self.good.replace('%d 天', '%s 天')))

    def test_requires_other_quantity(self):
        self.assertEqual(1, self.run_check(self.good.replace('quantity="other"', 'quantity="one"')))


if __name__ == "__main__":
    unittest.main()
