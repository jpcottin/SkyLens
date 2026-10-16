#!/bin/sh
# Snapshot multi-run experiment: boots the same AVD several times with quickboot snapshots
# enabled, launches SkyLens only in cycle 1 (frozen clock, fixed position and pose), and in every
# later cycle checks whether the snapshot brought the app back by itself: still running, still
# rendering (focused window + a layout tree with the HUD), never relaunched.
#
#   snapshot-multirun.sh <emulator-binary> <avd-name> <debug-apk> <evidence-dir> [cycles]
#
# Requires: adb on PATH, the `android` CLI on PATH (layout dumps), ANDROID_HOME set.
set -u
EMU="$1"; AVD="$2"; APK="$3"; OUT="$4"; CYCLES="${5:-4}"
PKG=com.jpcottin.skylens
mkdir -p "$OUT"
SELF_PGID="$(ps -o pgid= -p $$ | tr -d ' ')"
FAILED=0

boot_and_check() {
  N="$1"
  echo "================ RUN $N: launching emulator (snapshots enabled) ================"
  # No -no-snapshot: load the snapshot if present, save on exit. setsid isolates the process
  # group so the shutdown fallback is scoped to this emulator instance.
  setsid "$EMU" @"$AVD" -no-window -gpu auto -noaudio -no-boot-anim -camera-back none -memory 4096 -verbose -show-kernel -debug-metrics -metrics-collection > "$OUT/emulator_run$N.txt" 2>&1 &
  EMU_PID=$!
  EMU_PGID=""
  for _ in $(seq 1 20); do
    P="$(ps -o pgid= -p "$EMU_PID" 2>/dev/null | tr -d ' ')"
    if [ -n "$P" ] && [ "$P" != "$SELF_PGID" ]; then EMU_PGID="$P"; break; fi
    kill -0 "$EMU_PID" 2>/dev/null || break
    sleep 0.5
  done
  for _ in $(seq 1 36); do
    kill -0 "$EMU_PID" 2>/dev/null || { echo "ERROR: emulator process exited early (run $N)"; tail -40 "$OUT/emulator_run$N.txt"; return 1; }
    timeout 10 adb get-state >/dev/null 2>&1 && break
    sleep 5
  done
  timeout 10 adb get-state >/dev/null 2>&1 || { echo "ERROR: no device after 180s (run $N)"; tail -40 "$OUT/emulator_run$N.txt"; return 1; }
  for _ in $(seq 1 48); do
    [ "$(timeout 15 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 10
  done
  [ "$(timeout 15 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || { echo "===== boot timeout (run $N) ====="; tail -60 "$OUT/emulator_run$N.txt"; return 1; }
  T_RESUME=$(date +%s)

  if [ "$N" = "1" ]; then
    adb shell settings put global hide_error_dialogs 1 || true
    adb shell settings put global window_animation_scale 0 || true
    adb shell settings put global transition_animation_scale 0 || true
    adb shell settings put global animator_duration_scale 0 || true
    adb install -r "$APK"
    adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
    adb shell input keyevent KEYCODE_WAKEUP || true
    adb shell wm dismiss-keyguard || true
    # The only launch in the whole job: frozen inputs so every cycle renders the same sky.
    adb shell "am start -W -n $PKG/.MainActivity --el skylens.time 1791954000000 --es skylens.location '49.2827,-123.1207,70' --es skylens.pose '100,35,0' --ez skylens.map true"
    for _ in $(seq 1 15); do
      APP_PID="$(timeout 15 adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
      [ -n "$APP_PID" ] && break
      sleep 1
    done
    echo "RUN $N app pid after launch: ${APP_PID:-none}"
  else
    adb exec-out screencap -p > "$OUT/run$N-entry.png" 2>/dev/null || true
    # Deliberately NOT relaunched: the question is whether the snapshot brings the app back.
    APP_PID="$(timeout 15 adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
    if [ -n "$APP_PID" ]; then echo "RUN $N app survived restore: YES (pid $APP_PID)"; else echo "RUN $N app survived restore: NO"; FAILED=1; fi
  fi
  sleep 5
  adb logcat -d -t 500 > "$OUT/logcat_run$N.txt" 2>/dev/null || true
  # The sky is static under a frozen clock: judge rendering by a focused window and a layout
  # tree that still contains the HUD, not by a screenshot diff.
  FOCUS="$(timeout 15 adb shell dumpsys window 2>/dev/null | grep -ciE "ocus.*skylens" || echo 0)"
  HUD=0
  for _ in $(seq 1 15); do
    android layout > "$OUT/layout_run$N.json" 2>/dev/null || true
    grep -qi 'E 100' "$OUT/layout_run$N.json" 2>/dev/null && { HUD=1; break; }
    sleep 1
  done
  if [ -n "${APP_PID:-}" ] && [ "$FOCUS" -gt 0 ] && [ "$HUD" = "1" ]; then
    echo "RUN $N rendering: YES (focused window, HUD in the layout tree)"
  else
    echo "RUN $N rendering: NO (pid='${APP_PID:-}' focus=$FOCUS hud=$HUD)"; FAILED=1
  fi
  adb exec-out screencap -p > "$OUT/run$N.png" 2>/dev/null || true

  T_KILL=$(date +%s)
  echo "RUN $N: requesting shutdown via adb emu kill (snapshot save)"
  adb emu kill || true
  for _ in $(seq 1 90); do kill -0 "$EMU_PID" 2>/dev/null || break; sleep 1; done
  if kill -0 "$EMU_PID" 2>/dev/null; then
    echo "WARNING: emulator still up 90s after 'adb emu kill'; terminating it"
    if [ -n "$EMU_PGID" ]; then kill -TERM -- "-$EMU_PGID" 2>/dev/null || true; else kill "$EMU_PID" 2>/dev/null || true; fi
    sleep 10
  fi
  echo "RUN $N played ~$(( T_KILL - T_RESUME ))s (resume -> kill)"
  if [ -n "$EMU_PGID" ]; then
    for _ in $(seq 1 120); do kill -0 -- "-$EMU_PGID" 2>/dev/null || break; sleep 1; done
    kill -0 -- "-$EMU_PGID" 2>/dev/null && { kill -KILL -- "-$EMU_PGID" 2>/dev/null || true; sleep 5; }
  fi
  echo "RUN $N: emulator gone $(( $(date +%s) - T_KILL ))s after the kill request"
  grep -inE "snapshot|quickboot" "$OUT/emulator_run$N.txt" | tail -8 || true
  return 0
}

i=1
while [ "$i" -le "$CYCLES" ]; do
  boot_and_check "$i" || { echo "RUN $i did not complete"; exit 1; }
  i=$((i + 1))
done
[ "$FAILED" = "0" ] && echo "multi-run OK: the app survived every restore" || { echo "multi-run: at least one restore lost the app or its rendering"; exit 1; }
