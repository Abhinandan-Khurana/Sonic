#!/usr/bin/env python3
"""Explicit public-network check; never put credentials in process arguments."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--device", required=True)
parser.add_argument("--seconds", type=int, default=900)
parser.add_argument("--secrets", type=Path, default=Path(".argent/secrets.env"))
args = parser.parse_args()
if not 100 <= args.seconds <= 1200:
    parser.error("--seconds must be between 100 and 1200")
if not args.secrets.is_file() or args.secrets.stat().st_mode & 0o077:
    parser.error("The secrets file must exist and have mode 0600")
values = dict(line.split("=", 1) for line in args.secrets.read_text().splitlines() if "=" in line and not line.startswith("#"))
keys = ("ARGENT_SECRET_SLSK_USERNAME", "ARGENT_SECRET_SLSK_PASSWORD")
if any(not values.get(key) for key in keys):
    parser.error("The secrets file needs both ARGENT_SECRET_SLSK_USERNAME and ARGENT_SECRET_SLSK_PASSWORD")
sdk = os.environ.get("ANDROID_HOME", "")
adb = str(Path(sdk) / "platform-tools/adb") if sdk else "adb"
base = [adb, "-s", args.device]
private_file = "files/runout-live-account.env"
payload = "".join(f"{key}={values[key]}\n" for key in keys).encode()
try:
    subprocess.run(base + ["shell", "am", "force-stop", "app.runout.dev"], check=True)
    subprocess.run(base + ["shell", "run-as", "app.runout.dev", "sh", "-c",
                   f"'cat > {private_file}; chmod 600 {private_file}'"], input=payload, check=True)
    result = subprocess.run(base + ["shell", "am", "instrument", "-w", "-e", "class",
        "app.runout.SoulseekLiveNetworkTest", "-e", "runout.live", "true", "-e",
        "runout.liveSeconds", str(args.seconds),
        "app.runout.dev.test/androidx.test.runner.AndroidJUnitRunner"], capture_output=True, text=True)
    # Print only the runner result. Metrics are in logcat under RunoutLive.
    print(result.stdout)
    if result.returncode or "OK (1 test)" not in result.stdout or "FAILURES" in result.stdout:
        sys.exit(1)
finally:
    subprocess.run(base + ["shell", "am", "force-stop", "app.runout.dev"], check=False, stdout=subprocess.DEVNULL)
    subprocess.run(base + ["shell", "run-as", "app.runout.dev", "rm", "-f", private_file], check=False)
