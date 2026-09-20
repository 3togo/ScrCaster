#!/usr/bin/env bash
# Source from build.sh or run with ADB SERIAL APPLICATION_ID after installing an APK.
optimize_installed_apk() {
    local adb=$1 serial=$2 package=$3 output
    [[ "$package" =~ ^[a-zA-Z0-9_.]+$ ]] || return 1
    if [[ ${SCRCASTER_SKIP_DEXOPT:-0} == 1 ]]; then
        printf 'Skipping APK startup optimization (SCRCASTER_SKIP_DEXOPT=1).\n'
        return 0
    fi
    # Coocaa Android 9 left our sideloaded APK at run-from-apk, spending seconds
    # loading/verifying code before TvActivity. Compile after installation, never
    # during app startup. Without -f Android can reuse an already optimized APK.
    printf 'Optimizing startup on %s; this can take a few minutes on older TVs...\n' "$serial"
    if output=$("$adb" -s "$serial" shell cmd package compile -m speed "$package" 2>&1) &&
       [[ "${output//$'\r'/}" == Success ]]; then
        printf 'APK startup optimization completed on %s.\n' "$serial"
    else
        # Some OEMs do not expose the package compiler. The installed app remains usable.
        printf 'Warning: APK installed, but startup optimization was unavailable on %s: %s\n' "$serial" "$output" >&2
    fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    set -euo pipefail
    [[ $# == 3 ]] || { echo "Usage: $0 ADB SERIAL APPLICATION_ID" >&2; exit 2; }
    optimize_installed_apk "$@"
fi
