#!/usr/bin/env bash
# fix_agp_compat.sh — Align root project AGP/Kotlin versions with the miuix
# submodule so Gradle's AgpVersionCompatibilityRule stops rejecting the
# composite build (includeBuild("submodule/miuix")).
#
# Context:
#   Root libs.versions.toml:  agp = "9.3.2", kotlin = "2.4.10"
#   submodule/miuix libs.versions.toml:  agp = "9.4.0", kotlin = "2.4.20"
#
# Gradle 9.x forbids multiple AGP versions inside one composite build,
# so we bump the root to match the submodule (safer than downgrading the
# upstream library).

set -euo pipefail

ROOT_VERSIONS="gradle/libs.versions.toml"

if [ ! -f "$ROOT_VERSIONS" ]; then
    echo "ERROR: $ROOT_VERSIONS not found (run from project root)." >&2
    exit 1
fi

echo "==> Patching $ROOT_VERSIONS"

# AGP: 9.3.2 -> 9.4.0
sed -i 's/^agp = "9\.3\.2"$/agp = "9.4.0"/' "$ROOT_VERSIONS"

# Kotlin: 2.4.10 -> 2.4.20
sed -i 's/^kotlin = "2\.4\.10"$/kotlin = "2.4.20"/' "$ROOT_VERSIONS"

echo "==> Resulting [versions] block:"
grep -E '^(agp|kotlin) = ' "$ROOT_VERSIONS"

echo
echo "==> Running ./gradlew assembleDebug to verify…"
./gradlew assembleDebug
