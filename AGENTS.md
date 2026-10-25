# SkyLens — notes for agents

This project is built with **Lightbuild** (Google's declarative YAML build) through the
**Android CLI**. There are no Gradle files to edit: `project.lightbuild.yaml` lists the modules,
each module has a `lightbuild.yaml`, and `*/resolved.deps` pins the dependency resolution.

```sh
export ANDROID_CLI_BUILD=true          # unlocks `android build`
sh .github/scripts/android-build.sh query "..."              # list targets
android build "app:app-debug.apk"      # debug APK (builds astro + ui first)
android build "app:app-release.apk"    # R8-minified release APK
sh .github/scripts/android-build.sh test "astro:hostTest"    # astronomy engine unit tests (JVM)
sh .github/scripts/android-build.sh test "ui:hostTest"       # ui unit tests (JVM)
sh .github/scripts/android-build.sh test "ui:deviceTest"     # Compose UI tests on the connected device/emulator
sh .github/scripts/android-build.sh resolve                  # regenerate resolved.deps after a dependency change
```

Rough edges to remember (Lightbuild 0.0.20-alpha02, Android CLI 1.1.16717390):

- Android CLI 1.1 dropped `android build test` / `query` / `resolve` (its `build` only
  assembles the named target, so `android build "ui:hostTest"` runs nothing) and stopped
  streaming Lightbuild's output (it prints `Build log: /tmp/build-log-<n>.log` instead). The
  wrapper drives the pinned Lightbuild daemon directly for those commands and reads the log
  file for the verdict; use it rather than the bare CLI for tests.

- `android build test` / `query` / `resolve` print a spurious *"Target is required for command
  build"* failure before doing their real work and exit 1 even on success. CI goes through
  `.github/scripts/android-build.sh`, which looks at the output after the last
  *"Lightbuild is experimental"* banner and fails only on a reported failure.
- `platform: jvm` modules are not exported to Gradle yet (`GradleExportTask` fails), so the
  pure-Kotlin `astro` module is declared as an Android library with no Android code.
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
