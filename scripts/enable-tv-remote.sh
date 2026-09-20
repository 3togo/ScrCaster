#!/usr/bin/env bash
# Source this file from the installer, or run it with ADB SERIAL APPLICATION_ID.
# Coocaa firmware may hide Accessibility settings entirely. Keep this post-install
# step: merely installing the APK does not enable the key-filter service.
# The shell has permission to configure accessibility; an ordinary APK does not.
enable_tv_remote() {
    local adb=$1 serial=$2 package=$3
    local -a device=("$adb" -s "$serial")
    local features characteristics manufacturer
    features=$("${device[@]}" shell pm list features) || return 1
    characteristics=$("${device[@]}" shell getprop ro.build.characteristics) || return 1
    manufacturer=$("${device[@]}" shell getprop ro.product.manufacturer) || return 1
    if [[ "$features" != *android.software.leanback* && "$features" != *android.hardware.type.television* &&
          "$characteristics" != *tv* && "${manufacturer,,}" != *skyworth* && "${manufacturer,,}" != *coocaa* ]]; then
        printf 'Skipping TV remote setup on non-TV device %s.\n' "$serial"
        return 0
    fi
    [[ "$package" =~ ^[a-zA-Z0-9_.]+$ ]] || return 1
    local user component current updated verified enabled
    user=$("${device[@]}" shell am get-current-user) || return 1
    user=${user//$'\r'/}
    [[ "$user" =~ ^[0-9]+$ ]] || return 1
    component="$package/io.github.togo3.scrcaster.TvRemoteAccessibilityService"
    current=$("${device[@]}" shell settings --user "$user" get secure enabled_accessibility_services) || return 1
    current=${current//$'\r'/}
    [[ "$current" != null ]] || current=''
    # Only Android component-list characters are allowed through the remote shell.
    [[ "$current" =~ ^[a-zA-Z0-9_./:\$]*$ ]] || return 1
    updated=$current
    if [[ ":$current:" != *":$component:"* ]] &&
       { [[ "$package" != io.github.togo3.scrcaster ]] || [[ ":$current:" != *":$package/.TvRemoteAccessibilityService:"* ]]; }; then
        updated="${current:+$current:}$component"
    fi
    "${device[@]}" shell settings --user "$user" put secure enabled_accessibility_services "'$updated'" || return 1
    "${device[@]}" shell settings --user "$user" put secure accessibility_enabled 1 || return 1
    verified=$("${device[@]}" shell settings --user "$user" get secure enabled_accessibility_services) || return 1
    enabled=$("${device[@]}" shell settings --user "$user" get secure accessibility_enabled) || return 1
    [[ "${verified//$'\r'/}" == "$updated" && "${enabled//$'\r'/}" == 1 ]] || return 1
    printf 'TV remote controls enabled and verified on %s (user %s).\n' "$serial" "$user"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    set -euo pipefail
    [[ $# == 3 ]] || { echo "Usage: $0 ADB SERIAL APPLICATION_ID" >&2; exit 2; }
    enable_tv_remote "$@"
fi
