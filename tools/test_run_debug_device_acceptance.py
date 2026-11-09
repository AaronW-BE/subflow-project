import unittest

from tools.run_debug_device_acceptance import EXPECTED_TESTS, verify_result


def green():
    return "".join(f"INSTRUMENTATION_STATUS: test=case{i}\nINSTRUMENTATION_STATUS_CODE: 0\n" for i in range(EXPECTED_TESTS)) + f"OK ({EXPECTED_TESTS} tests)\nINSTRUMENTATION_CODE: -1\n"


class NativeResultTest(unittest.TestCase):
    def test_all_expected_successes(self):
        self.assertEqual([f"case{i}" for i in range(EXPECTED_TESTS)], verify_result(green()))

    def test_adb_zero_exit_does_not_make_crash_success(self):
        with self.assertRaises(ValueError):
            verify_result("INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n")

    def test_failed_status_rejected_even_with_success_footer(self):
        with self.assertRaises(ValueError):
            verify_result("INSTRUMENTATION_STATUS_CODE: -2\n" + green())

    def test_incomplete_suite_rejected(self):
        with self.assertRaises(ValueError):
            verify_result(green().replace(f"OK ({EXPECTED_TESTS} tests)", f"OK ({EXPECTED_TESTS - 1} tests)"))

    def test_duplicate_names_rejected(self):
        with self.assertRaises(ValueError):
            verify_result(green().replace("test=case6", "test=case5"))

    def test_missing_success_status_rejected(self):
        with self.assertRaises(ValueError):
            verify_result(green().replace("test=case6", "unrelated=case6"))


if __name__ == "__main__":
    unittest.main()
