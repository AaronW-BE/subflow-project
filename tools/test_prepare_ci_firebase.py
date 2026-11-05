import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from prepare_ci_firebase import DEBUG_CONFIG, prepare


class PrepareCiFirebaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()

    @patch.dict(os.environ, {"CI": "true"})
    def test_synthetic_debug_shape_and_release_untouched(self):
        release = self.root / "android/app/google-services.json"
        release.parent.mkdir(parents=True)
        release.write_bytes(b"local release sentinel\r\n")
        destination = prepare(self.root)
        self.assertEqual(destination, self.root / DEBUG_CONFIG)
        config = json.loads(destination.read_text(encoding="utf-8"))
        self.assertEqual(config["configuration_version"], "1")
        self.assertEqual(config["project_info"]["project_id"], "demo-subflow-ci-only")
        self.assertEqual(config["project_info"]["project_number"], "000000000000")
        client, = config["client"]
        self.assertEqual(client["client_info"]["android_client_info"]["package_name"],
                         "org.dpdns.alwaysup.subflow.debug")
        self.assertRegex(client["client_info"]["mobilesdk_app_id"], r"^1:0+:android:0+$")
        self.assertIn("SYNTHETIC_NOT_A_REAL_KEY", client["api_key"][0]["current_key"])
        self.assertEqual(release.read_bytes(), b"local release sentinel\r\n")
        self.assertFalse((self.root / "android/app/src/release").exists())

    @patch.dict(os.environ, {"CI": "true"})
    def test_refuses_existing_without_changing_bytes(self):
        destination = self.root / DEBUG_CONFIG
        destination.parent.mkdir(parents=True)
        original = b"local debug sentinel\r\n"
        destination.write_bytes(original)
        with self.assertRaises(FileExistsError):
            prepare(self.root)
        self.assertEqual(destination.read_bytes(), original)

    def test_requires_exact_ci_guard_before_writing(self):
        for value in (None, "false", "TRUE", "1", ""):
            with self.subTest(ci=value), patch.dict(os.environ, clear=True):
                if value is not None:
                    os.environ["CI"] = value
                with self.assertRaisesRegex(RuntimeError, "CI=true"):
                    prepare(self.root)
                self.assertFalse((self.root / "android").exists())

    @patch.dict(os.environ, {"CI": "true"})
    def test_refuses_debug_redirect_to_release(self):
        debug = self.root / DEBUG_CONFIG.parent
        release = self.root / "android/app/src/release"
        release.mkdir(parents=True)
        try:
            debug.symlink_to(release, target_is_directory=True)
        except OSError:
            self.skipTest("Directory symlinks unavailable on this host")
        with self.assertRaisesRegex(RuntimeError, "redirected"):
            prepare(self.root)
        self.assertEqual(list(release.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
