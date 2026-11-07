# SkyLens — notes for agents

This project is built with **Lightbuild** (Google's declarative YAML build), pinned to
0.0.30 in `project.lightbuild.yaml`, through the `lightbuild` CLI that ships inside the
Lightbuild SDK package; the **Android CLI** installs the SDK, deploys and inspects. There are
no Gradle files to edit: `project.lightbuild.yaml` lists the modules, each module has a
`lightbuild.yaml`, and `*/resolved.deps` pins the dependency resolution.

```sh
android sdk install build/lightbuild/0.0.30                          # once
bash "$(android info sdk)/build/lightbuild/0.0.30/cli/install.sh"   # ~/.local/bin/lightbuild
lightbuild query "..."                  # list targets (--output=files for where they write)
lightbuild build "app:app-debug.apk"    # debug APK (builds astro + ui first)
lightbuild build "app:app-release.apk"  # R8-minified release APK
lightbuild test "astro:hostTest"        # astronomy engine unit tests (JVM)
lightbuild test "ui:hostTest"           # ui unit tests (JVM)
lightbuild test "ui:deviceTest"         # Compose UI tests on the connected device/emulator
lightbuild resolve                      # regenerate resolved.deps after a dependency change
```

Rough edges to remember (Lightbuild 0.0.30, Android CLI 1.1.16717390):

- The exit code of `lightbuild` is the verdict (1 on a compile error, a failing test or an
  unknown target); `--plain` turns the progress bars off. `android build "<target>"` (with
  `ANDROID_CLI_BUILD=true`) still builds a named target through the pinned daemon but cannot
  test, query or resolve, and its exit code was never reliable: CI uses `lightbuild`
  everywhere (`.github/scripts/install-lightbuild.sh` installs the pinned version).
- A `platform: jvm` module still cannot be depended on by an Android module (re-checked on
  0.0.30: `GradleExportTask` fails and `app` gets no build file), so the pure-Kotlin `astro`
  module is declared as an Android library with no Android code.
- Every dependency is resolved non-transitively from `resolved.deps`; a module with no other
  dependency must declare `org.jetbrains.kotlin:kotlin-stdlib` itself.
- A `ViewModel` test must never `advanceUntilIdle()`: the sky recompute loop runs forever on
  virtual time. Step the clock with `advanceTimeBy` and cancel `viewModelScope` at the end.

CI scripts: `.github/scripts/sky-journey.sh` (HUD, Objects, Settings via launch extras, then the
signed release smoke launch) and `.github/scripts/snapshot-multirun.sh <emulator> <avd> <apk>
<out> [cycles]` (quickboot snapshot cycles, app launched once). Both read the verdict from
`android layout`, so they need the `android` CLI on PATH.

Deterministic launches for screenshots and CI (debuggable builds only):

```sh
adb shell am start -n com.jpcottin.skylens/.MainActivity \
  --el skylens.time 1791954000000 --es skylens.location "49.2827,-123.1207,70" \
  --es skylens.pose "100,35,0" --ez skylens.map true \
  --es skylens.select sat:25544 --ez skylens.night false --es skylens.tab objects
```

Display AI Glasses: develop against a paired pair of AVDs, a phone and a glasses image (find
their serials with `adb devices`; `PHONE` and `GLASSES` below). Never wipe their data (it breaks
the pairing); restart them with `android emulator start <avd>`, phone first. Launch the projected
activity with `am start --display <ProjectionDisplay id>` on the phone and capture the lens with
`screencap -d <SurfaceFlinger id of ProjectionDisplay>`; when `dumpsys display` on the
phone shows the ProjectionDisplay `state OFF`, send key code 266 (ToggleWakeSleep) to the glasses
AVD (`adb -s $GLASSES shell input keyevent 266`) and relaunch the activity: the glasses
sysui overlay takes the display when it wakes. The glasses emulator's virtual rotation-vector
sensor registers but never reports (BAD_VALUE in its sensorservice), which is why the lens
falls back to the phone's sensors after three seconds (`PoseSources.withFallback`).

Never add AI attribution (Co-Authored-By, "Generated with…") to commits, PRs or files.
