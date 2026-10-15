# SkyLens — notes for agents

This project is built with **Lightbuild** (Google's declarative YAML build) through the
**Android CLI**. There are no Gradle files to edit: `project.lightbuild.yaml` lists the modules,
each module has a `lightbuild.yaml`, and `*/resolved.deps` pins the dependency resolution.

```sh
export ANDROID_CLI_BUILD=true          # unlocks `android build`
android build query "..."              # list targets
android build "app:app-debug.apk"      # debug APK (builds astro + ui first)
android build "app:app-release.apk"    # R8-minified release APK
android build test "astro:hostTest"    # astronomy engine unit tests (JVM)
android build test "ui:hostTest"       # ui unit tests (JVM)
android build test "ui:deviceTest"     # Compose UI tests on the connected device/emulator
android build resolve                  # regenerate resolved.deps after a dependency change
```

Rough edges to remember (Lightbuild 0.0.20-alpha02, Android CLI 1.0.16500706):

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

Deterministic launches for screenshots and CI (debuggable builds only):

```sh
adb shell am start -n com.jpcottin.skylens/.MainActivity \
  --el skylens.time 1791954000000 --es skylens.location "49.2827,-123.1207,70" \
  --es skylens.pose "100,35,0" --ez skylens.map true \
  --es skylens.select sat:25544 --ez skylens.night false --es skylens.tab objects
```

Never add AI attribution (Co-Authored-By, "Generated with…") to commits, PRs or files.
