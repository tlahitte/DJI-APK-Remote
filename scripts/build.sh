#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation outside this repository}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK outside this repository}"
: "${DJI_PRIVATE_DIR:?Set DJI_PRIVATE_DIR to a private directory OUTSIDE this repository}"
mkdir -p "$DJI_PRIVATE_DIR"
DJI_PRIVATE_DIR="$(cd "$DJI_PRIVATE_DIR" && pwd -P)"
case "$DJI_PRIVATE_DIR/" in "$ROOT/"*) echo "Private build directory must be outside the repository" >&2; exit 1;; esac
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$DJI_PRIVATE_DIR/gradle}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$DJI_PRIVATE_DIR/android}"
export DJI_DEBUG_KEYSTORE="${DJI_DEBUG_KEYSTORE:-$DJI_PRIVATE_DIR/signing/debug.keystore}"
mkdir -p "$ANDROID_USER_HOME" "$(dirname "$DJI_DEBUG_KEYSTORE")"
python3 - "$ROOT" "$DJI_DEBUG_KEYSTORE" <<'CHECK'
from pathlib import Path
import sys
root,key=map(lambda p:Path(p).resolve(),sys.argv[1:])
if key == root or root in key.parents:
    raise SystemExit('Refusing to store the signing key inside the repository')
CHECK
chmod 700 "$DJI_PRIVATE_DIR" "$(dirname "$DJI_DEBUG_KEYSTORE")"
python3 "$ROOT/scripts/check-secrets.py"
"$ROOT/gradlew" -p "$ROOT" --project-cache-dir "$DJI_PRIVATE_DIR/project-cache" testDebugUnitTest lintDebug assembleDebug
python3 "$ROOT/scripts/check-secrets.py"
printf '\nAPK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$ROOT"
