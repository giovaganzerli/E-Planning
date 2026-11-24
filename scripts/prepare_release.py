#!/usr/bin/env python3
"""Create the public update manifest from the APK actually built by Gradle."""
import hashlib
import json
import os
from pathlib import Path


def main():
    output = Path("app/build/outputs/apk/debug")
    metadata = json.loads((output / "output-metadata.json").read_text())
    artifact = metadata["elements"][0]
    code = int(os.environ["APP_VERSION_CODE"])
    version = os.environ["APP_VERSION_NAME"]
    if artifact["versionCode"] != code or artifact["versionName"] != version:
        raise SystemExit("APK version does not match release version")
    apk = output / artifact["outputFile"]
    repository = os.environ["GITHUB_REPOSITORY"]
    notes = Path("RELEASE_NOTES.md").read_text().strip()
    manifest = {
        "schemaVersion": 1,
        "versionCode": code,
        "versionName": version,
        "notes": notes,
        "apkUrl": f"https://github.com/{repository}/releases/download/v{version}/{apk.name}",
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "size": apk.stat().st_size,
    }
    (output / "update.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(f"Manifest ready for {apk.name} (versionCode {code})")


if __name__ == "__main__":
    main()
