#!/bin/sh
# Installs Personal TV Home on a TV. Usage: ./install.sh <tv_ip> [--home] [apk]
# Without [apk] it builds from source (needs the Android SDK + JDK 17).
# --home also makes it the Home app (undo: see README).
set -e
cd "$(dirname "$0")"
[ -n "$1" ] || { echo "usage: $0 <tv_ip> [--home] [apk]"; exit 1; }
ADB="${ADB:-adb} -s $1:5555"
${ADB%% -s*} connect "$1:5555" >/dev/null
APK=${3:-app/build/outputs/apk/release/app-release.apk}
[ -n "$3" ] || ./gradlew assembleRelease --console=plain -q
$ADB install -r "$APK"
# Lets the launcher read media sessions for the album-art music player.
$ADB shell cmd notification allow_listener local.personalhome/local.personalhome.MusicListener
if [ "$2" = "--home" ]; then
  $ADB shell cmd package set-home-activity local.personalhome/.MainActivity
  $ADB shell input keyevent HOME
  sleep 2
  $ADB shell dumpsys activity activities | grep -m1 topResumedActivity
fi
