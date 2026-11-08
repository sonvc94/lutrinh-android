# AGENTS.md — Lữ Trình (Android)

Lữ Trình (`vn.lutrinh.app`) is a local-only travel journal for Vietnam: trips → steps (place on an
OpenStreetMap map, date/time, note, photos) shown as a timeline and a route map. Native Android
now (Kotlin, Compose M3, Room, MapLibre, Coil 3); an iOS/Swift port will follow, built from `docs/`.
No server, login or API in this phase.

## Standing rule

**Every behaviour change updates `docs/01`–`07`** (the platform-neutral spec the iOS app is built
from) in the same branch: rules R1–R11 in `docs/04` with their test names, screens in `docs/03`,
map in `docs/05`, photos in `docs/06`, and the Swift equivalent in `docs/07-ios-port.md`. Add a
screenshot to `docs/img/` (and the index in `docs/03`) when a screen changes.

## Layout

```
app/src/main/java/vn/lutrinh/app/
  domain/   models, repository interfaces, validation, ordering, use cases (pure Kotlin)
  data/     Room DB, repositories, PhotoStorage (import: EXIF/downscale/GPS strip), data/photo/ (pure maths), sample data
  map/      MapConfig.kt (the ONLY place for tile URL/attribution/User-Agent), MapLibre views
  ui/       screens + view models; ui/common/Common.kt has formatters and VietnameseLocale
app/src/test/         JVM tests (Robolectric for Room)
app/src/androidTest/  Compose UI tests (TripFlowsTest)
docs/                 spec 01–07 + img/
```

## Build and test

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export ANDROID_HOME=/Volumes/wdblack/flutter/android-sdk
./gradlew clean assembleDebug testDebugUnitTest          # JVM tests
ANDROID_SERIAL=emulator-5580 ./gradlew connectedDebugAndroidTest   # UI tests on the AVD below
```

`connectedDebugAndroidTest` uninstalls the app afterwards (app data is gone); run `installDebug`
again before manual checks.

## Emulator

Own AVD `trip-app-avd` (pixel_6, android-36 arm64, English) on port 5580:

```bash
nohup $ANDROID_HOME/emulator/emulator -avd trip-app-avd -port 5580 -gpu host -no-snapshot &
$ANDROID_HOME/platform-tools/adb -s emulator-5580 shell getprop sys.boot_completed   # 1 = ready
```

Never use, restart or kill emulator-5554, 5556 or 5570 (other projects).

## Traps

* zsh does not word-split `ADB="adb -s emulator-5580"`; call the full adb path with `-s`.
* `adb shell input text` cannot type Vietnamese diacritics (use ASCII, or Compose tests, which can).
* `*.openstreetmap.org` resets TLS from this network → tiles come from `tile.openstreetmap.de`
  (`MapConfig.TILE_URL`).
* MapLibre merges location permissions into the manifest → removed with `tools:node="remove"`.
* A Compose `Dialog` re-provides `LocalConfiguration`/`LocalContext`: locale overrides must go
  **inside** dialog content (see `VietnameseLocale`). Don't wrap the whole app: MapLibre's
  `MapView` needs the real Activity context.
* Material 3 1.3 `ExtendedFloatingActionButton` exposes no text in semantics; label it with a
  content description (tests and TalkBack rely on it).
* uiautomator substring matches are greedy ("Bỏ" also matches "Bỏ các thay đổi?") — match exact
  text when tapping dialogs by script.
* Polarsteps analysis docs (`/Volumes/wdblack/workspace/vietmap/vietmap/polarsteps/docs`) are reference only; write our own code and copy.
