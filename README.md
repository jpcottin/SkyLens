# SkyLens

[![CI](https://github.com/jpcottin/SkyLens/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jpcottin/SkyLens/actions/workflows/ci.yml)

Raise your phone at the sky and SkyLens shows what is up there: **satellites with their
trajectory**, the **Sun, the Moon and the planets**, and the **bright stars**, drawn over the
camera image and labelled. A list tells you where everything is right now and when each
satellite next passes over you, and *Point me* guides you to it with an arrow at the edge of
the screen. Phone first; Display AI Glasses next.

Everything is computed on the device from a 70 KB star catalogue and the orbital elements
of the satellites: SGP4 for the satellites, Meeus for the Sun and the Moon, the JPL
approximate elements for the planets. The only network traffic is the periodic download of
the element sets from Celestrak, with a bundled copy as fallback.

> **Status:** phone app complete — AR (camera + sensors) and drawn-sky modes, objects list
> with search, satellite pass prediction, settings with layers, night mode and a manual
> position; adaptive to phones, foldables (list-detail when unfolded, split layout in tabletop
> posture), tablets and desktop windows. Glasses projection is the next milestone.

## Screenshots

Captured on a Pixel 10 Pro Fold emulator with a frozen clock (2026-10-14 05:00 UTC, Vancouver)
through the debug launch extras described below, so the pictures are reproducible.

<p>
<img src="docs/screenshots/sky-phone.png" width="240" alt="Drawn sky on a phone: stars, Saturn, Uranus, Aldebaran, a satellite with its dashed trajectory, the horizon with cardinal ticks">
<img src="docs/screenshots/sky-selected-phone.png" width="240" alt="A selected satellite with its card: direction, range, speed, and the Point me chip">
<img src="docs/screenshots/objects-phone.png" width="240" alt="Objects list: satellites above the horizon first, then Sun, Moon and planets, then bright stars">
</p>
<p>
<img src="docs/screenshots/objects-unfolded.png" width="480" alt="Unfolded foldable: the objects list and the ISS detail with its next passes side by side, with a navigation rail">
</p>
<p>
<img src="docs/screenshots/settings-phone.png" width="240" alt="Settings: layers, star magnitude slider, satellite groups, display options, manual position, orbit data status">
<img src="docs/screenshots/sky-night-vision-phone.png" width="240" alt="Night mode: the same sky drawn in red on black">
</p>

## What it shows

- **Satellites** — the Celestrak *visual* group (the ~150 brightest objects) and the *space
  stations* group by default, Starlink optionally. Each is a diamond with its name, a dashed
  trajectory for the next minutes with an arrow in the direction of travel, dimmed when the
  satellite is in the Earth's shadow. The card and the detail screen give the slant range,
  the speed, the NORAD number and the orbital period.
- **Sun and Moon** — the Moon is drawn with its phase (illuminated fraction, waxing or
  waning) and corrected for parallax, which moves it by up to a degree.
- **Planets** — Mercury to Neptune, coloured and labelled, with an approximate magnitude.
- **Stars** — every star to magnitude 5.0 (1 637 of them) from the HYG database, coloured by
  their B−V index, the named ones labelled; the faintest magnitude drawn is a setting.
- **Horizon** — the horizon line with a tick every 10° and the cardinal points; in the drawn
  mode an altitude/azimuth grid and shaded ground.
- **Passes** — for any satellite, the passes over the next two days: rise, culmination and
  set times with their compass directions, duration, and whether the pass is *visible*
  (the satellite sunlit while the observer is in twilight or night).
- **Point me** — pick an object and an arrow at the edge of the screen points the way
  until it is in view, where a ring marks it.

## How it works

```
                      ┌────────────────────────── astro (pure Kotlin, no Android) ──────────────────────────┐
 Celestrak TLE ──►  Tle.parseAll ──► Sgp4 ──► TEME ──┐                                                       │
                                                     ├─► ECEF ──► ENU ──► azimuth / elevation ──► SkySnapshot │
 Meeus (Sun, Moon), JPL elements (planets) ──► ECI ──┘                                                       │
 HYG catalogue (RA/Dec J2000) ──► precession ──► hour angle ──► azimuth / elevation ─────────────┘           │
                      └──────────────────────────────────────────────────────────────────────────────────────┘
                                                                          │
 rotation-vector sensor ──► DevicePose (forward, up in ENU) ──► SkyCamera ─┤
 Camera2 characteristics ──► field of view ──► FovFit ────────────────────┘──► Canvas overlay on the viewfinder
```

- **`astro`** is a pure-Kotlin module with no Android dependency: two-line element parsing,
  an SGP4 propagator ported from the Vallado reference implementation (near-Earth orbits;
  deep-space objects are skipped), Julian dates and sidereal time, frame conversions
  (TEME → ECEF → East-North-Up), Sun and Moon from Meeus' *Astronomical Algorithms*, planets
  from the JPL approximate Keplerian elements, J2000 → date precession for the stars, the
  bundled star catalogue, a pass predictor (coarse stepping, bisection on the horizon
  crossings, cylindrical Earth-shadow test), and the pinhole `SkyCamera` that projects a sky
  direction to normalised screen coordinates and back.
- **`ui`** is the Android library: the data sources (rotation-vector sensor with display
  rotation remapping and magnetic declination, fused location, Camera2 field of view,
  Celestrak repository with a disk cache and the bundled fallback, Preferences DataStore
  settings), the `SkyViewModel` that recomputes the sky twice a second, and the Compose UI.
- **`app`** is the application shell (manifest, application id, R8 configuration).

Accuracy, checked in the unit tests against the `python-sgp4` library and `skyfield` with the
JPL DE421 ephemeris: satellite positions agree to a millimetre at the TLE epoch, the Sun to
2′, the Moon to 6′ including parallax, the planets to a few arcminutes (Saturn ≈ 10′), the
stars to 6′ (nutation and aberration are ignored). Timing uses UTC for UT1 and TT; the
difference is under a second.

## The UI

- **Sky** — the camera viewfinder (CameraX Compose) with the overlay on top. The HUD shows
  where the camera points; the toggle switches to the drawn sky, which you pan by dragging:
  that is also what the emulator, the previews and the UI tests use. Tap an object to select
  it; the card offers *Details* and *Point me*.
- **Objects** — searchable list: satellites (above the horizon first), then Sun, Moon and
  planets, then the bright named stars that are up. Selecting one opens the detail with the
  next passes.
- **Settings** — layers, faintest star magnitude, satellite groups, labels and trajectory
  length, red **night mode**, camera passthrough, a fixed position for when there is no
  location, the minimum pass altitude, and the orbit data status with a manual refresh.

### Adaptive layouts

- The navigation suite scaffold shows a bottom bar on phones and a rail on tablets, unfolded
  foldables and desktop windows.
- Objects and Detail are a Navigation 3 list-detail pair: stacked on a phone, side by side
  (with a placeholder until something is selected) when the window is at least 840 dp wide.
- In **tabletop posture** (foldable half open with the hinge horizontal) the sky takes the
  upper half, where the camera points at the sky, and the lower, flat half shows the
  selected object's card or the objects list.
- Every screen has `@Preview`s for phone, foldable, tablet and desktop, light and dark,
  landscape and the folded cover screen, plus previews of every state (no location, no
  camera, selected object, night mode, loading passes, missing object, orbit update error).

## Deterministic launches

Debuggable builds honour launch extras that freeze the inputs, which is how the screenshots
above and the CI journey are produced:

```sh
adb shell am start -n com.jpcottin.skylens/.MainActivity \
  --el skylens.time 1791954000000 \
  --es skylens.location "49.2827,-123.1207,70" \
  --es skylens.pose "100,35,0" \
  --ez skylens.map true
```

`skylens.time` freezes the clock (epoch millis), `skylens.location` is `lat,lon[,altitude]`,
`skylens.pose` is `azimuth,elevation[,roll]` in degrees and `skylens.map` starts in the
drawn-sky mode. Release builds ignore them.

## Build system: Lightbuild and the Android CLI

There are no Gradle files in this repository. The project is described by
`project.lightbuild.yaml` and one `lightbuild.yaml` per module, and built with the
[Android CLI](https://developer.android.com/tools/agents/android-cli):

```sh
export ANDROID_CLI_BUILD=true
android build "app:app-debug.apk"       # debug APK
android build "app:app-release.apk"     # R8-minified release APK
android build test "astro:hostTest"     # 58 astronomy engine tests on the JVM
android build test "ui:hostTest"        # 36 ui unit tests on the JVM
android build test "ui:deviceTest"      # 28 Compose UI tests on the connected device
android run --apks app/build/outputs/apk/debug/app-debug.apk
```

Lightbuild 0.0.20-alpha02 notes: `platform: jvm` modules cannot be exported yet, so `astro`
is declared as an Android library that happens to contain no Android code; the CLI's exit
code is unreliable for `test`, `query` and `resolve`, so CI runs every build through
`.github/scripts/android-build.sh`, which reads the verdict from the output. See
[AGENTS.md](AGENTS.md) for the full list.

## Testing

| Suite | Where | What |
|---|---|---|
| `astro:hostTest` | JVM | SGP4 against `python-sgp4` reference states (position to 1 µm, velocity to 1 nm/s); Sun, Moon and planets against skyfield/DE421 at six instants and three sites; sidereal time; frames; TLE parsing and checksums; star catalogue; pass prediction; camera projection; the sky engine's determinism |
| `ui:hostTest` | JVM | `SkyViewModel` with fakes (location states, debug overrides, AR/map modes, selection, settings, passes); `TleRepository` with MockWebServer (bundled fallback, cache, courtesy interval, failures); FOV fitting; device pose from rotation matrices and declination; debug extras parsing |
| `ui:deviceTest` | emulator | Compose UI tests: sky HUD, overlay semantics, tap hit-testing, selected card, location and camera prompts; objects list, search and sections; detail with passes; settings switches, groups, manual position validation, orbit refresh; navigation between tabs, list → detail → *Point me* → sky |
| CI journey | emulator | Launches the debug APK with frozen inputs, reads the HUD and the objects list back with `android layout`, then smoke-launches the minified release APK and checks the process survives |

## Data and credits

- Orbital elements: [Celestrak](https://celestrak.org) GP data, fetched at most once every
  two hours per group as their usage policy asks; a snapshot is bundled for offline use.
- Stars: the [HYG database](https://github.com/astronexus/HYG-Database) v4.1 by David Nash,
  CC BY-SA 4.0, cut at magnitude 5.0.
- Sun and Moon: Jean Meeus, *Astronomical Algorithms*, 2nd ed. Planets: E. M. Standish,
  *Keplerian Elements for Approximate Positions of the Major Planets* (JPL).
- SGP4: David Vallado's reference implementation ("Revisiting Spacetrack Report #3").

## Android development skills applied

- **adaptive** — navigation suite scaffold, list-detail scene strategy, tabletop posture from
  `currentWindowAdaptiveInfo()`, form-factor previews.
- **navigation-3** — typed keys, `NavDisplay` with the saveable-state and ViewModel-store
  decorators, scene strategies.
- **edge-to-edge** — `enableEdgeToEdge()` with `safeDrawingPadding` on the HUD and cards.
- **camerax** — `CameraXViewfinder` bound to the lifecycle for the passthrough.
- **testing-setup** — JVM, instrumented and journey tests, previews for every state.

## License

MIT, see [LICENSE](LICENSE). The star catalogue keeps its CC BY-SA 4.0 licence.
