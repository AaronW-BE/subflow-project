"""Run destructive, explicitly opted-in BIN-94 Debug-only physical-device tests."""
import argparse
import os
from pathlib import Path
import re
import subprocess

PACKAGE = "org.dpdns.alwaysup.subflow.debug"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS = "org.dpdns.alwaysup.subflow.Bin94NavHostAcceptanceTest"
EXPECTED_TESTS = 8


def verify_result(output: str) -> list[str]:
    """Reject adb's successful exit when AndroidJUnitRunner actually failed/crashed."""
    bad = ("FAILURES!!!", "Process crashed", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_STATUS_CODE: -")
    if any(marker in output for marker in bad):
        raise ValueError("Native runner reported failure; inspect run.log")
    if not re.search(rf"OK \({EXPECTED_TESTS} tests\)", output) or not re.search(r"INSTRUMENTATION_CODE: -1\s*$", output):
        raise ValueError(f"Native runner did not finish with {EXPECTED_TESTS} passing tests")
    blocks = re.split(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", output)
    names = []
    for index in range(1, len(blocks), 2):
        if blocks[index] == "0":
            matches = re.findall(r"INSTRUMENTATION_STATUS: test=([A-Za-z0-9_]+)\s*$", blocks[index - 1], re.MULTILINE)
            if matches:
                names.append(matches[-1])
    if len(names) != EXPECTED_TESTS or len(set(names)) != EXPECTED_TESTS:
        raise ValueError(f"Expected {EXPECTED_TESTS} distinct successful native test statuses")
    return names


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True, help="One explicit adb serial; never select all devices")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--output", type=Path, required=True, help="New local evidence directory")
    parser.add_argument("--allow-debug-data-reset", action="store_true", help="Authorize disposable Debug fixtures/preferences")
    parser.add_argument("--stop-android-cli-server", action="store_true", help="Release only the test-owned Android CLI UiAutomation server")
    args = parser.parse_args()
    if not args.allow_debug_data_reset:
        parser.error("--allow-debug-data-reset is required; tests erase Debug fixtures/preferences")
    env = dict(os.environ, MSYS_NO_PATHCONV="1")
    command = [args.adb, "-s", args.device]

    def adb(*parts: str) -> bytes:
        return subprocess.check_output(command + list(parts), env=env)

    focus = adb("shell", "dumpsys", "window").decode("utf-8", "replace")
    owners = [line for line in focus.splitlines() if "mCurrentFocus=" in line]
    if len(owners) != 1 or PACKAGE + "/" not in owners[0]:
        parser.error("Open the authorized Debug app first; refusing to interrupt another foreground owner")
    registered = adb("shell", "pm", "list", "instrumentation").decode("utf-8", "replace")
    if f"instrumentation:{RUNNER} (target={PACKAGE})" not in registered:
        parser.error("Install the current Debug and Debug androidTest APKs first")
    args.output.mkdir(parents=True, exist_ok=False)
    if args.stop_android_cli_server:
        adb("shell", "am", "force-stop", "com.android.cli.interact.instrumentation")
    result = subprocess.run(command + ["shell", "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS, RUNNER],
                            env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    output = result.stdout.decode("utf-8", "replace")
    (args.output / "run.log").write_text(output, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"adb failed ({result.returncode}); inspect {args.output / 'run.log'}")
    names = verify_result(output)
    screenshots = names + ["csv-receiver"]
    for name in screenshots:
        image = adb("exec-out", "run-as", PACKAGE, "cat", f"cache/bin94-{name}.png")
        if not image.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError(f"Missing valid native screenshot for {name}")
        (args.output / f"{name}.png").write_bytes(image)
    print(f"Native Debug acceptance: {EXPECTED_TESTS} pass; {len(screenshots)} screenshots saved in {args.output}")


if __name__ == "__main__":
    main()
