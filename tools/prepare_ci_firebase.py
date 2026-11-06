"""Create a synthetic debug-only Firebase configuration for CI, never distribution."""

import argparse
import json
import os
from pathlib import Path


DEBUG_CONFIG = Path("android/app/src/debug/google-services.json")


def prepare(project_root: Path) -> Path:
    if os.environ.get("CI") != "true":
        raise RuntimeError("Synthetic Firebase configuration requires CI=true")
    root = project_root.resolve()
    destination = root / DEBUG_CONFIG
    if destination.resolve() != destination:
        raise RuntimeError("Refusing redirected Firebase destination")
    config = {
        "project_info": {
            "project_number": "000000000000",
            "project_id": "demo-subflow-ci-only",
            "storage_bucket": "demo-subflow-ci-only.appspot.com",
        },
        "client": [{
            "client_info": {
                "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
                "android_client_info": {
                    "package_name": "org.dpdns.alwaysup.subflow.debug",
                },
            },
            "oauth_client": [],
            "api_key": [{"current_key": "AIzaSyCI_ONLY_SYNTHETIC_NOT_A_REAL_KEY_00"}],
            "services": {"appinvite_service": {"other_platform_oauth_client": []}},
        }],
        "configuration_version": "1",
    }
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation protects existing local configurations, even in CI.
    with destination.open("x", encoding="utf-8") as output:
        json.dump(config, output, indent=2)
        output.write("\n")
    return destination


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        destination = prepare(args.project_root)
    except (OSError, RuntimeError) as error:
        parser.exit(1, f"Refusing CI Firebase setup: {error}\n")
    print(f"Created synthetic CI-only debug Firebase configuration: {destination}")


if __name__ == "__main__":
    main()
