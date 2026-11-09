# AGENTS.md — Lữ Trình (Android)

Lữ Trình (`vn.lutrinh.app`) is a local-only travel journal for Vietnam: trips → steps (place on an
OpenStreetMap map, date/time, note, photos) shown as a timeline and a route map. Native Android
now (Kotlin, Compose M3, Room, MapLibre, Coil 3); an iOS/Swift port will follow, built from `docs/`.
No server, login or API in this phase.

## Standing rule

**Every behaviour change updates `docs/01`–`07`** (the platform-neutral spec the iOS app is built
from) in the same branch: rules R1–R14 in `docs/04` with their test names, screens in `docs/03`,
map in `docs/05`, photos in `docs/06`, and the Swift equivalent in `docs/07-ios-port.md`. Add a
screenshot to `docs/img/` (and the index in `docs/03`) when a screen changes.

## Layout

```
app/src/main/java/vn/lutrinh/app/
  domain/   models, repository interfaces, validation, ordering, use cases (pure Kotlin)
  data/     Room DB, repositories, PhotoStorage (import: EXIF/downscale/GPS strip), data/photo/ (pure maths), sample data
  map/      MapConfig.kt (the ONLY place for style URL/basemap tuning/attribution/User-Agent),
            OfflineConfig.kt (the ONLY place for offline areas/zooms/limits), OfflineMaps.kt,
            RouteLayers.kt (route + recorded-track layer stack), Pins.kt, MapLibre views
  tracking/ TrackRecordingService (location foreground service + notification); the logic is
            domain/usecase/TrackLogic.kt + TrackRecorder.kt (R16, pure, unit-tested);
            stop suggestions from tracks = domain/usecase/StopSuggestions.kt (R17)
  share/    TripExporter (files, FileProvider, ACTION_SEND, MediaStore) + SummaryImage (R18);
            rules in domain/usecase/TripShare.kt + TripPage.kt, map in map/StaticTripMap.kt
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
* `*.openstreetmap.org` resets TLS from this network → the basemap is the OpenFreeMap vector
  style (`MapConfig.STYLE_URL`, `curl -sI` it first). Runtime repaint ids in `BASEMAP_TUNING` are
  Positron layer ids; another style needs its own ids.
* Map screenshots by script: pinch needs root; zoom in with a double tap
  (`input tap x y; sleep 0.1; input tap x y` — without the sleep it is two single taps) and out
  with a quick-scale (`input motionevent DOWN/UP/DOWN`, MOVE upwards, UP).
* Location permissions are real since GPS tracks (R16): fine/coarse + `FOREGROUND_SERVICE_LOCATION`
  + `POST_NOTIFICATIONS`; `ACCESS_BACKGROUND_LOCATION` stays removed with `tools:node="remove"`
  (check: `aapt dump permissions app-debug.apk`). Never ask for background location.
* GPS on the emulator (R16): `adb -s emulator-5580 emu geo fix <lon> <lat>` (longitude first)
  feeds the platform GPS provider the recorder uses. Send fixes ≥ 6 s apart and ≥ 10 m apart (the
  filter drops closer ones; > 85 m/s between fixes = dropped as a jump). Log:
  `logcat -s TrackRecording` prints each fix with its verdict. Query the DB in place (the AVD has
  `/system/bin/sqlite3`): `adb -s emulator-5580 shell 'run-as vn.lutrinh.app sqlite3
  databases/lutrinh.db "select status,point_count,distance_m from tracks"'`. A real road for
  fixes: `router.project-osrm.org/route/v1/driving/<lon,lat;lon,lat>?overview=full&geometries=geojson`
  answers from this network (OSM data), resample to ~150 m, one fix every 7 s. Screen off:
  `input keyevent 26`; back: `keyevent 224` + `wm dismiss-keyguard`. Process death:
  `am force-stop vn.lutrinh.app` (`am kill` does not kill a foreground service); the next start
  ends the track as interrupted. Notification shade: `cmd statusbar expand-notifications`.
  Fix time is the wall clock (no fake time source), so a ≥ 20-min stay for stop suggestions (R17)
  needs ~21 real minutes of fixes; alternate two points ~25 m apart every 60 s (a fix < 10 m from
  the last is dropped). The "Thêm điểm dừng" FAB and the suggestion button share their text.
  Shortcut for a suggestion on any date: `adb push` an SQL file of `insert into tracks …` + 27
  `track_points` rows (60 s apart, two points ~25 m apart) to `/data/local/tmp`, then
  `run-as vn.lutrinh.app sh -c 'sqlite3 databases/lutrinh.db < /data/local/tmp/seed.sql'` (R17 extend-dates check).
* A Compose `Dialog` re-provides `LocalConfiguration`/`LocalContext`: locale overrides must go
  **inside** dialog content (see `VietnameseLocale`). Don't wrap the whole app: MapLibre's
  `MapView` needs the real Activity context.
* Material 3 1.3 `ExtendedFloatingActionButton` exposes no text in semantics; label it with a
  content description (tests and TalkBack rely on it).
* uiautomator substring matches are greedy ("Bỏ" also matches "Bỏ các thay đổi?") — match exact
  text when tapping dialogs by script.
* The Android **Photo Picker hides EXIF GPS** (even with `ACCESS_MEDIA_LOCATION`;
  `setRequireOriginal` throws on picker URIs). "Thêm từ ảnh" therefore uses `ACTION_OPEN_DOCUMENT`
  + `ACCESS_MEDIA_LOCATION` (docs/06). On API 34+ that permission shows the "photos and videos"
  dialog; "limited access" first opens a selection sheet. Read GPS **before**
  `PhotoStorage.import` — the stored copy has none (R11).
* MapLibre `line-dasharray` is not data-driven: one `LineLayer` per dash kind, filtered by `mode`.
* Room schema bump = bump `AppDatabase.VERSION`, add `MIGRATION_x_y` to `MIGRATIONS`, commit
  `app/schemas/.../<v>.json`, extend `MigrationTest` (no destructive fallback, on purpose).
* `adb shell input text` into a Compose field: wait ~1.5 s after tapping it, select all with
  `input keycombination 113 29`, then type.
* Test JPEGs with GPS/date: write the EXIF with a small script (APP1 with IFD0 + Exif + GPS IFDs),
  `adb push` to `/sdcard/Pictures/…`, then per file
  `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/…`; check stored copies via `run-as vn.lutrinh.app cat files/photos/<f>`.
* Offline maps (docs/05 "Offline maps"): airplane mode on the AVD with
  `adb -s emulator-5580 shell cmd connectivity airplane-mode enable|disable`
  (`settings get global airplane_mode_on`); MapLibre's store is `files/mbgl-offline.db`
  (`run-as vn.lutrinh.app ls -l files/`). Offline, z0–7 tiles of a pack also draw far outside
  its box — judge coverage at town zoom. `connectedDebugAndroidTest` wipes downloaded areas too.
* Share (R18): `logcat -s ShareTrip TripExporter StaticTripMap` prints each share URI, page
  size/level and save path; saved files are `/sdcard/Pictures/LuTrinh/*.png` and
  `/sdcard/Download/LuTrinh/*.html` (`adb pull`). The snapshotter cannot list style layers, so
  label ids for it are in `MapConfig.LABEL_LAYER_IDS` (change them with the style).
* Polarsteps analysis docs (`/Volumes/wdblack/workspace/vietmap/vietmap/polarsteps/docs`) are reference only; write our own code and copy.
