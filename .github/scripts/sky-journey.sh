#!/bin/sh
# Deterministic end-to-end journey on a booted emulator.
#
#   sky-journey.sh <evidence-dir> <debug-apk> [release-apk]
#
# 1. installs the debug APK and launches it with a frozen clock (2026-10-14 05:00 UTC), a fixed
#    observer (Vancouver) and a fixed pose (east, 35° up) in map mode;
# 2. asserts through `android layout` that the HUD shows that pose and that the satellites loaded;
# 3. opens the Objects tab through the launch extra and asserts the list is shown;
# 4. if a release APK is given, installs it, launches it and checks the process survives.
set -eu
OUT="$1"; DEBUG_APK="$2"; RELEASE_APK="${3:-}"
PKG=com.jpcottin.skylens
mkdir -p "$OUT"

layout_contains() {
  # $1 = pattern, $2 = evidence name; polls up to 60 s.
  for _ in $(seq 1 12); do
    android layout > "$OUT/$2.json" 2>/dev/null || true
    adb shell screencap -p /data/local/tmp/j.png >/dev/null 2>&1 && adb pull /data/local/tmp/j.png "$OUT/$2.png" >/dev/null 2>&1 || true
    if grep -qi -- "$1" "$OUT/$2.json"; then return 0; fi
    sleep 5
  done
  echo "ERROR: '$1' not found in layout ($2)"; cat "$OUT/$2.json"; return 1
}

adb install -r "$DEBUG_APK"
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant $PKG android.permission.CAMERA || true
adb shell "am start -W -n $PKG/.MainActivity --el skylens.time 1791954000000 --es skylens.location '49.2827,-123.1207,70' --es skylens.pose '100,35,0' --ez skylens.map true"
layout_contains "E 100" sky-hud
layout_contains "satellites" sky-status
# Objects tab, opened through the launch extra so the check does not depend on where the
# navigation bar or rail puts the item. The header and the search field are always composed
# (the ISS itself may be far down the lazy list and therefore absent from the layout tree).
adb shell am force-stop $PKG
adb shell "am start -W -n $PKG/.MainActivity --el skylens.time 1791954000000 --es skylens.location '49.2827,-123.1207,70' --es skylens.pose '100,35,0' --ez skylens.map true --es skylens.tab objects" >/dev/null
layout_contains "Satellites" objects
layout_contains "Search the sky" objects-search

if [ -n "$RELEASE_APK" ]; then
  # Lightbuild writes an unsigned release APK; sign it with the debug keystore so it installs.
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
  [ -f "$HOME/.android/debug.keystore" ] || keytool -genkeypair -v -keystore "$HOME/.android/debug.keystore" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
  SIGNED="$OUT/app-release-signed.apk"
  "$APKSIGNER" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out "$SIGNED" "$RELEASE_APK"
  adb install -r "$SIGNED"
  adb logcat -c
  adb shell am start -W -n $PKG/.MainActivity
  sleep 8
  adb shell pidof $PKG >/dev/null || { echo "ERROR: release app died on launch"; adb logcat -d | grep -A30 "FATAL EXCEPTION" | head -60; exit 1; }
  if adb logcat -d | grep -q "FATAL EXCEPTION"; then echo "ERROR: crash in logcat"; adb logcat -d | grep -A30 "FATAL EXCEPTION" | head -60; exit 1; fi
  adb shell screencap -p /data/local/tmp/r.png && adb pull /data/local/tmp/r.png "$OUT/release.png" >/dev/null
fi
echo "journey OK"
