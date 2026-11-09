# 04 — Business rules

Each rule has an id used in code comments and tests. All rules live in the domain layer
(`domain/usecase/*`) and are covered by unit tests (`app/src/test/.../domain/*Test.kt`,
`data/RoomRepositoryTest.kt`). An iOS port must reproduce them exactly and can reuse the test
cases as its specification.

## R1 — Trip order

Trips are listed by `startDate` descending (newest trip first); ties by `createdAt` descending;
then by `id` ascending so the order is total and stable.
*Test:* `OrderingTest.tripsNewestStartFirstThenNewestCreated`.

## R2 — Step order (timeline)

Steps of a trip are ordered by `arrivedAt` ascending; ties by `createdAt` ascending; then `id`.
Steps cannot be dragged into another order: to move a step, change its date/time. Step numbers
shown in the UI ("1", "2", …) are positions in this order, not stored values.
*Test:* `OrderingTest.stepsSortByArrivalThenCreationThenId`.
*Inspired by* polarsteps docs/22 §1.3 ("ordering of steps by arrival date/time, no drag-reorder").

## R3 — Route

The route line joins step locations in R2 order with straight segments (straight on the
Web-Mercator map). Zero or one step → no line.
*Test:* `OrderingTest.routeFollowsTimelineNotInsertionOrder`.

## R4 — Trip dates

* `startDate` is required; `endDate` is optional (open-ended trip).
* If `endDate` is set: `endDate >= startDate` (same day allowed) — else `END_BEFORE_START`.
* Editing a trip must not leave existing steps outside the new range: if the earliest step date
  is before the new start, or the latest step date is after the new end → `STEPS_OUTSIDE_DATES`
  (the user moves or deletes those steps first). The app never moves steps silently.
* *Tests:* `ValidationTest.endBeforeStartIsRejected_sameDayAndOpenEndAreFine`,
  `ValidationTest.newDatesMustStillContainExistingSteps`, `UseCasesTest.shrinkingDatesBelowExistingStepsIsRejected`.
* Trips may overlap each other (change from the reference app, which blocks overlapping dates).

## R5 — Step date inside the trip

The **date part** of `arrivedAt` must satisfy `startDate <= date` and, if the trip has an end,
`date <= endDate` (both inclusive). Time of day is free. Errors: `BEFORE_TRIP_START`,
`AFTER_TRIP_END`. *Tests:* `ValidationTest.stepDateMustBeInsideTripDatesInclusive`,
`UseCasesTest.stepOutsideTripDatesIsRejected`. The step form's date picker opens on the current value; validation happens on
save.

## R6 — New-step defaults

When "Thêm điểm dừng" opens an empty step form:

1. **Arrival:** if today lies within the trip (inclusive; open-ended trips include every day from
   the start), use *now* truncated to the minute. Otherwise take the date of the last step (R2)
   plus one day, or the trip start date when there are no steps, at **09:00**, clamped into the
   trip range.
2. **Picker start point:** the step's own point (when editing); else the last step's location;
   else the Vietnam overview `(16.0, 106.0)` at zoom 5.

*Tests:* `OrderingTest.newStepDefaultsToNowWhenTravellingToday`,
`OrderingTest.newStepDefaultsToDayAfterLastStepClampedToTrip`, `OrderingTest.pickerOpensAtLastStep`.
*Inspired by* polarsteps docs/22 §1.3 (later steps default to the next day at a fixed morning
time, picker starts at the previous step). *Changed:* 09:00 instead of 10:00 and "now" when the
trip is in progress.

## R7 — Delete cascade

* Deleting a **trip** deletes its steps, their photo rows **and the photo files**, and its
  recorded tracks with their points (R16) and stop decisions (R17); a recording of that trip is stopped first.
* Deleting a **step** deletes its photo rows and files; other steps are untouched.
* Order: the database transaction runs first and returns the file names that were attached;
  the files are deleted afterwards. A crash between the two leaves orphan files (harmless, can be
  swept later) but never a row pointing to a missing file.
* Both deletes need a confirm dialog that states what will be lost ("… cùng 2 điểm dừng và 3 ảnh
  sẽ bị xóa khỏi máy. Không thể hoàn tác."). There is no undo and no trash in this phase.

*Tests:* `UseCasesTest.deleteTripCascadesToStepsAndPhotoFiles`, `UseCasesTest.deleteStepRemovesItsPhotoFilesOnly`,
`RoomRepositoryTest.deletingTripCascadesToStepsAndPhotos`, `RoomRepositoryTest.deletingStepDeletesItsPhotos`.

## R8 — Photo list on step save

The step form edits a list of file names; on save:

* Duplicates are removed (first occurrence wins); positions are renumbered 0..n-1 in list order.
* A photo already attached to the step keeps its `id`, `createdAt` and `takenAt` (matched by file
  name); a new photo gets `takenAt` from its EXIF date read before import (R17, docs/06).
* Photos removed from the list lose their row **and file** after the save commits.
* Position 0 is the step cover; tapping a thumbnail moves it to position 0.
* At most 30 photos per step (`TOO_MANY_PHOTOS`).

*Tests:* `UseCasesTest.savingStepNumbersPhotosAndKeepsIdsOfExistingOnes`,
`RoomRepositoryTest.savingStepReplacesPhotosAndReportsRemovedFiles`,
`UseCasesTest.savingStepStoresPhotoCaptureTimesAndKeepsThemOnEdit`.

## R9 — Unsaved imports

Photos are copied into app storage as soon as they are picked (06-photos.md). If the form is
left without saving, or a picked photo is removed before saving, those copies are deleted, so
storage only holds files referenced by a row.

## R10 — Stats

*Tests:* `OrderingTest.haversineHaGiangToNinhBinh`, `OrderingTest.statsCountDaysInclusive`.

Shown on Trip detail: number of steps, number of photos, distance = sum of great-circle
(haversine, Earth radius 6371.0088 km) distances between consecutive route points (R3), labelled
"km (đường chim bay)" because it is not a road distance; days = `endDate − startDate + 1` (only
when the trip has an end date).

## R11 — Photo import processing

Every picked photo is stored upright, with a long edge of at most 2560 px and without location
EXIF (details and the EXIF orientation table in 06-photos.md):

* long edge > 2560 → scaled down to exactly 2560, aspect kept; smaller images are never upscaled;
* EXIF orientation ≠ 1 → pixels rotated (and mirrored for 2/4/5/7) and re-encoded; 90/270 swap
  width and height;
* small, upright JPEG/PNG/WebP → original bytes kept, GPS tags removed in place;
* re-encoded files are JPEG quality 85 (PNG when transparent) and carry no EXIF;
* GIF and formats the platform cannot decode are kept byte for byte.

*Tests:* `PhotoImportPlanTest` (`large landscape is downscaled to 2560 on the long edge keeping
aspect`, `small and boundary images keep their size`, `rotated 90 or 270 swaps width and height of
the stored copy`, `sample size is the largest power of two that still covers the target`,
`reencode only when too large, not upright or not strippable in place`, `each exif orientation puts
the stored top-left corner where the spec says`, `transpose maps x to y`, `unknown exif values are
treated as upright`). Decoding/encoding itself is checked on a device (06-photos.md).

## R12 — Leg transport

* A leg is a pair of consecutive steps in R2 order; a trip with *n* steps has *n − 1* legs.
* The mode is stored on the **arrival** step (`arrivalTransport`); the first step's value is
  ignored. Choices: Đi bộ, Xe máy, Ô tô, Xe buýt/xe khách, Tàu hỏa, Máy bay, Thuyền, or none
  ("Chưa chọn", the default — also for every step that existed before database version 2).
* When the order changes (a step's date changes, a step is inserted or deleted), the mode moves
  with its arrival step: inserting C between A and B gives A→C "none" and C→B keeps B's mode.
  The app never guesses a mode.
* Saving a step from the step form keeps its mode (the form does not edit it). Setting the mode
  updates the step's `updatedAt`.
* A leg's distance is the haversine distance (R10) between its two steps, shown as "· N km".
* Drawing per mode (stroke kind, great-circle flight arc, badge): 05-map.md. No road snapping/routing.

*Tests:* `LegsTest.legsFollowTimelineOrderAndTakeTheModeOfTheArrivalStep`,
`LegsTest.codesRoundTripAndUnknownCodesReadAsNotSet`, `LegsTest.onlyFlightsAreDrawnAsArcs`,
`LegsTest.setLegTransportUpdatesAndClearsAndSaveStepKeepsIt`, drawing maths: `RouteGeometryTest` (flight arc,
simplification, badge midpoint), `CameraMathTest`,
`MigrationTest.version1UpgradesToCurrentWithoutDataLoss`; UI: `TripFlowsTest.setLegTransport_fromTheTimeline_showsAndStoresTheMode`.
*Inspired by* polarsteps docs/22 (transport mode per leg). *Changed:* own mode list (xe máy,
xe buýt/xe khách for Vietnam), no automatic mode guess, no routed lines.

## R13 — Steps from photos ("Thêm từ ảnh")

For each picked photo the app reads, **before import** (06-photos.md):
* **date** = EXIF `DateTimeOriginal`, else `DateTimeDigitized`, else `DateTime`
  (`yyyy:MM:dd HH:mm:ss`, no time zone — like `arrivedAt`); malformed/zero dates = none;
* **location** = EXIF GPS latitude/longitude; missing, out of range or exactly (0, 0) = none.

Grouping (pure function `PhotoGrouping.group`):
1. Photos with a location are sorted by date (undated last), then by pick order.
2. Each photo joins the current group when **all** hold: same calendar day as the group's first
   photo (or both undated), within **2 km** (haversine) of the group's centroid, and the group has
   fewer than 30 photos (R8 limit). Otherwise it starts a new group.
3. Photos without a location form **one** last group (split by 30 if needed).
4. Group location = centroid (mean latitude/longitude); arrival = earliest photo date, truncated
   to the minute.

Suggestions (`suggestStepsFrom`): one per group, titled "Điểm dừng N" (N continues after the
trip's existing steps). Undated groups get the R6 arrival. A group whose date lies outside the
trip (R5) is flagged "ngoài thời gian chuyến đi" and cannot be kept. The group without a
location is not kept until the user places it on the map (or it is skipped). The user can edit
titles and untick groups; "Tạo N điểm dừng" imports each kept group's photos (R11 — the stored
copies have no GPS) and saves one step per group through the normal step save (R5/R8 apply).

*Tests:* `PhotoGroupingTest.samePlaceSameDayIsOneGroupAndDayOrPlaceChangeSplits`,
`photosWithoutLocationFormOneLastGroup`, `undatedLocatedPhotosGroupByPlaceAfterDatedOnes`,
`aGroupNeverExceedsThePhotoLimit`, `exifDatesParseAndRejectGarbage`,
`suggestionsNumberAfterExistingStepsAndFlagDatesOutsideTheTrip`.
*Inspired by* polarsteps docs/22/16 (photo-based step suggestions). *Changed:* explicit review
screen, 2 km / same-day rule, no automatic place names (no geocoding offline).

## R14 — "Bản đồ của tôi" (all trips)

* Totals: số chuyến = all trips; số điểm dừng = all their steps; km = **sum over trips** of each
  trip's R10 distance (no line between the end of one trip and the start of the next).
  Provinces are not counted (needs geocoding).
* Each trip gets a colour from an 8-colour palette by creation order (`createdAt`, then id),
  wrapping after 8; a newly created trip never changes the colour of older trips (deleting one
  shifts the later ones).
* Trips without steps count in "số chuyến" but draw nothing.

*Tests:* `LegsTest.overallStatsSumEachTripSeparately`,
`LegsTest.tripColoursFollowCreationOrderAndStayWhenTripsAreAdded`; UI:
`TripFlowsTest.openMyMap_fromTheTripList_showsTotalsAndTrips`.

## R15 — Offline map areas ("Bản đồ offline")

All numbers live in `map/OfflineConfig.kt` (pure Kotlin; the iOS port copies them).

* **Areas.** Ready-made: **Hà Giang** `ha-giang` (S 22.70, W 104.85, N 23.42, E 105.60: TP Hà
  Giang, Quản Bạ, Yên Minh, Lũng Cú, Đồng Văn, Mã Pí Lèng, Mèo Vạc, Du Già) and **Ninh Bình**
  `ninh-binh` (S 20.10, W 105.48, N 20.45, E 106.05: Tràng An, Tam Cốc, Hang Múa, Hoa Lư, Bái
  Đính, Vân Long, Cúc Phương). **Trip area** `trip:<tripId>`: the box around all the trip's steps
  plus 10 km on each side (latitude margin 10/111.32°, longitude margin measured at the box's
  widest latitude); a trip without steps has no area.
* **What is stored.** For each area MapLibre's offline store keeps the basemap style
  (`MapConfig.STYLE_URL`), its sprite, its fonts (Latin/Vietnamese glyph ranges, no CJK) and
  every tile of the style's sources inside the box for **zoom 0–14**. The vector tiles end at
  z14, so maps over-zoom them up to z19 with the same street and village names. Style, sprite
  and fonts are shared between areas and stored once. Nothing about trips, steps or photos is
  stored there (they are already local).
* **Size.** Before a download the screen shows an estimate: 17 MB fixed (style + sprite + fonts)
  + Σ over z0–14 of (tiles in the box at z × the measured average tile size at z). Areas whose
  estimate is over **150 MB** cannot be downloaded (keeps the donated server's load modest and
  forbids whole provinces/the country); for a trip that wide the screen explains why. Measured on
  2026-11-09: Hà Giang ~26 MB (estimate ~29,6 MB), Ninh Bình ~25 MB (estimate ~23,9 MB) stored (both together 33,5 MB on disk, the fonts
  being shared). After the download the stored size is shown.
* **Start conditions.** A download starts only when the device is online and the free space on
  the store's volume is at least estimate + **50 MB**; otherwise the card shows "Không có mạng…" or
  "Không đủ bộ nhớ trống…" and nothing is stored.
* **Running.** Downloads belong to the app process, not to the screen. Losing the network pauses
  the transfer ("Mất mạng · … · sẽ tự tải tiếp khi có mạng") and it continues by itself when the
  network returns. "Tạm dừng" stops it until "Tiếp tục"; the paused flag is stored with the area,
  so after the app is killed an unpaused, incomplete download resumes at the next app start and a
  paused one stays paused. Server errors are retried by MapLibre; storage or other errors stop the
  download with an error and a retry button.
* **Cancel / delete.** "Hủy tải" and "Xóa" (after a confirmation) both remove the area from the
  store, then clear MapLibre's ambient cache and pack the database: resources no other area uses
  are freed on disk (MapLibre alone would keep them as up to 50 MB of cache); resources shared
  with another downloaded area (fonts, low-zoom tiles) stay. The tiles cached by online viewing
  go too. Measured 2026-11-09: both areas 33,7 MB → delete Ninh Bình 28,2 MB → delete Hà Giang
  0,07 MB. Deleting a trip does **not** delete its area: it stays
  under "Vùng của các chuyến đi" until deleted there.
* **Freshness.** Once per app process, when online, every downloaded area is re-checked against
  the server (MapLibre fetches only what changed). OpenFreeMap versions its tileset (TileJSON
  `tiles` URLs contain a date); without this check a style refresh after a new tileset would point
  to tiles not in the pack.
* **Use.** No setting: every map (trip detail, trip map, step maps, location picker, Bản đồ của
  tôi) reads from the store automatically when the network is gone. Offline, a map shows the chip
  "Ngoại tuyến · <area>" when a downloaded area's box contains the camera centre, otherwise "Không
  có mạng / Vùng này chưa tải bản đồ offline."; online it shows nothing. Outside downloaded areas
  low zoom levels may still appear (z0–7 tiles are large and spill over), blurry when zoomed in.

*Tests:* `OfflineConfigTest.readyMadeRegions_coverTheirPlaces`,
`readyMadeRegions_stayWithinTheSizeTarget`, `tileCount_isOnePerZoomInsideOneTile_andGrowsWithZoom`,
`tripArea_isTheStepsBoxPlusTenKmEachSide`, `tripArea_ofOneStepIsAboutTwentyKmWide_andNoStepsGivesNone`,
`tripArea_spanningTheCountryIsOverTheLimit`, `storageCheck_keepsAReserve`,
`covering_findsTheAreaContainingThePoint`, `phases_followTheStoredStatus`,
`problems_areMappedFromOfflineErrors`, `progress_isTheShareOfStoredResources`; UI:
`TripFlowsTest.openOfflineMaps_fromTheTripListMenu_listsTheReadyMadeRegions`,
`TripFlowsTest.openOfflineMaps_fromATripMap_offersTheTripArea`. Download, resume after kill and
airplane-mode use are checked by hand on the emulator (docs/05 "Offline maps").

## R16 — Recorded GPS tracks ("Ghi hành trình")

Constants live in `domain/usecase/TrackLogic.kt` (`TrackRules`); the iOS port copies them.

* **What.** A track is the real path of the traveller, recorded by GPS on demand and attached to
  a whole trip (data in docs/02 "Track"). It is separate from the planned route (R3): steps are
  never created or moved by a track on their own (a user can add a step from a stop suggestion,
  R17), and a track is never snapped to roads.
* **Start.** Only from the UI, by "Ghi hành trình" on the trip detail or the trip map, for that
  trip. **One recording in the whole app**: while a track is open (recording or paused) other
  trips show "Đang ghi hành trình cho chuyến đi khác" instead of the button; a deleted trip
  cannot be recorded (`TripNotFound`). Starting creates the track (`recording`, segment 0) before
  the first fix arrives.
* **Permissions (in this order).** 1) Fine location granted already → skip to 2; otherwise an
  explanation dialog ("Ghi hành trình bằng GPS": what is recorded, only after tapping, always a
  notification while recording, data stays on the device) → the system location prompt (fine +
  coarse). Approximate-only or refused → a dialog that explains and opens the app's settings;
  nothing is recorded. 2) Android 13+: the notification permission is asked; recording works
  whether or not it is granted (without it the notification is hidden, the recording is not).
  3) The device location switch is off → a dialog that opens the location settings. **Background
  location is never requested**: the recording runs in a foreground service started while the
  app is visible, so "while using the app" is enough (iOS: "When In Use" + background mode,
  docs/07). The Android manifest removes `ACCESS_BACKGROUND_LOCATION` explicitly.
* **While recording.** A persistent, silent notification "Đang ghi hành trình" shows the elapsed
  recording time (ticking), the distance and the trip, with "Tạm dừng" and "Dừng"; tapping it opens
  the app. The recording continues with the screen off and with the user on any other screen or
  app; it ends only by Dừng (screen or notification), by deleting the trip, or by the process dying.
* **Sampling.** Fixes are requested from the platform GPS every **5 s** (`SAMPLE_INTERVAL_MS`)
  with a minimum movement of **10 m** (`MIN_DISTANCE_M`), high accuracy. Android: platform
  `LocationManager`, `GPS_PROVIDER` (not Play services' fused provider: no Google dependency,
  closest to iOS `CLLocationManager`, and the emulator's `geo fix` feeds it directly). Fix time =
  wall-clock now − the fix's age on the monotonic clock.
* **Filter** (`TrackFilter.check`, in order; only ACCEPT is stored):
  1. coordinates out of range / NaN → INVALID;
  2. accuracy radius > **50 m** (`MAX_ACCURACY_M`) → INACCURATE (no accuracy given → kept);
  3. fix time more than 2 s before the open segment started → STALE (cached fix from before a
     start/resume);
  4. first point of the segment → ACCEPT (never compared with the point before a pause);
  5. not later than the last point → STALE; closer than **10 m** to it → TOO_CLOSE; speed from it
     faster than **85 m/s** (≈ 300 km/h, `MAX_SPEED_MPS`) → JUMP (impossible jump).
* **Pause / resume.** Pause stops the GPS, closes the open segment (`activeMs += now −
  resumedAt`) and the notification shows "Đã tạm dừng…" with "Tiếp tục" / "Dừng". Fixes arriving
  while paused are IGNORED. Resume starts a **new segment** (segment + 1, `resumedAt = now`).
  Distance and drawn lines never join two segments (the traveller may have moved by bus while
  paused); duration never counts a pause.
* **Distance / duration.** Distance = Σ distance (haversine, R = 6371 km) between consecutive
  points of the same segment, kept up to date in the track row on every stored point. Duration =
  `activeMs` (+ `now − resumedAt` while recording). Shown as "850 m" (< 1 km), "2,4 km" (< 100 km,
  Vietnamese decimal comma), "125 km"; time as "m:ss" or "h:mm:ss".
* **Stop.** Ends the track (`ended`, `endedAt = now`, open segment closed), stops the GPS and
  removes the notification. A track with 0 or 1 point is kept (it shows in the list, draws
  nothing) so the user decides whether to delete it.
* **Process death.** Every accepted point is stored at once, together with the track's distance /
  count / `updatedAt`, in one transaction: nothing accepted is lost. If the app process dies
  while a track is open (force-stop, crash, low-memory kill of the service), the system does not
  restart the recording (Android: `START_NOT_STICKY`); at the next app start every open track
  that is not the live one is **ended**: `interrupted = true`, `endedAt` = its last save (last
  point, or the pause/resume time), duration counted up to that moment; the points stay. The list
  shows it with "Dừng do ứng dụng bị đóng khi đang ghi". Recording never resumes by itself.
* **Drawing.** Stored points are never simplified. For display each segment becomes one line,
  simplified with Douglas–Peucker at **5·10⁻⁵°** (≈ 5.5 m, `DISPLAY_EPSILON_DEG`, finer than the
  route's 2·10⁻⁴° so mountain switchbacks stay); segments of fewer than 2 points draw nothing.
  Layer style in docs/05 "Recorded tracks".
* **Delete.** A track is deleted from the list after a confirmation ("Xóa hành trình này?" with
  its time, distance and point count); its points go with it (one transaction). The open track
  has no delete button and cannot be deleted (`DeleteTrack` refuses); stop it first.

*Tests:* `TrackLogicTest` (`first fix of a segment is accepted`, `fix with accuracy worse than
50 m is dropped, missing accuracy is kept`, `invalid coordinates are dropped`, `fix closer than
10 m to the last point is dropped`, `impossible jump faster than 85 m per s is dropped`, `fix older
than the last point or the segment start is stale`, `after a pause the first fix is not compared
with the point before the pause`, `length sums the segments and never the gap of a pause`,
`display lines are one per segment and per track, single points draw nothing`, `display lines are
simplified but keep both ends`, `distance and duration formats`); `TrackRecorderTest` (`duration
excludes pauses and distance only joins points of one segment`, `filtered fixes are not stored`,
`only one recording at a time and only for an existing trip`, `process death ends the open track
with its saved points`, `a paused track interrupted keeps the time before the pause`, `delete
removes an ended track and its points but never the open one`, `deleting the trip stops its
recording`, `a track deleted under the recorder ends the recording instead of crashing`);
`MigrationTest.version2UpgradesWithTrackTablesAndKeepsTripsAndSteps`; UI:
`TripFlowsTest.tripDetail_showsTheRecordTrackControl`. Recording with simulated GPS, screen off,
force-stop and offline drawing are checked by hand on the emulator (AGENTS.md "GPS on the
emulator").

## R17 — Stop suggestions ("Gợi ý điểm dừng")

Constants live in `domain/usecase/StopSuggestions.kt` (`StopRules`); the iOS port copies them.
Detection and filtering are pure (`StopDetection`, `StopSuggestions`).

* **What.** Places where a finished track stayed a while are offered as steps the user may add.
  Nothing is created without the user tapping "Thêm điểm dừng". Suggestions are derived, never
  stored (docs/02 "Stop suggestions").
* **Which tracks.** Only **ended** tracks of the trip (a recording or paused track suggests
  nothing until it is stopped). Each track is scanned on its own.
* **Stay** (`StopDetection.detect`). Points are ordered by (segment, time). From each point
  (the anchor) the following points are taken while every one is within **150 m**
  (`RADIUS_M`) of the anchor; if the last one is **≥ 20 min** (`MIN_DWELL_MS`) after the anchor,
  those points are a stay and the scan continues after them, else the scan moves to the next
  point. The time between the first and last point counts, not the number of points: the
  recorder drops fixes closer than 10 m, so standing still stores one point and the next one is
  the first fix after leaving.
* **Pause gap.** Segments follow each other in time, so a pause between segments is a stay when
  the last point before it and the first point after it are within 150 m and ≥ 20 min apart
  (the same rule, no special case).
* **Merge.** Two consecutive stays whose centres are ≤ 150 m apart with ≤ **10 min**
  (`MERGE_MAX_GAP_MS`) between the end of one and the start of the next are one stay (a short
  walk around the same place).
* **Stay values.** Start/end = times of its first/last point; centre = mean of its points;
  duration = end − start.
* **Already a step** (`StopSuggestions.matchesStep`). A stay is not suggested when a step of the
  trip is within **300 m** (`STEP_MATCH_RADIUS_M`) of its centre **and** that step's arrival
  (local time, converted with the device zone) is between start − **60 min** and end + 60 min
  (`STEP_MATCH_TIME_MS`).
* **Decisions** (`StopSuggestions.isHidden`, stored in `stop_decisions`). "Bỏ qua" stores
  `dismissed`: hidden for good (survives restarts). Saving the step editor opened by "Thêm điểm
  dừng" stores `added` with the new step's id: hidden while that step exists, even if the user
  moved it or changed its time; deleting that step brings the suggestion back. A decision hides a
  stay of the same track whose window overlaps the decided window. Leaving the editor without
  saving stores nothing. Deleting a track deletes its decisions.
* **Recompute.** The list is recomputed whenever the trip's tracks, track points, steps or
  decisions change: a track that ends (stop, or ended as interrupted at the next start) brings
  its stays, a deleted track removes them, a saved/deleted step or a decision updates the list.
* **Order and photos.** Oldest first (by start). Photo count = photos of any step of the trip
  whose `takenAt` (minute precision) is between start and end, both truncated to the minute,
  inclusive; photos without `takenAt` (added before DB v4) are not counted.
* **Add** ("Thêm điểm dừng"). Opens the step editor (new step) prefilled: place = centre (the
  picker shows it as the suggested place), date/time = start (local, minute), title = "Điểm dừng
  HH:mm" of the start; note and photos empty. The usual step rules apply (R5 date inside the
  trip, R6). Existing photos are not pre-selected (the editor only adds new files).
* **Texts.** Card: "<day> · HH:mm–HH:mm" (local), "Dừng 35 phút" / "Dừng 1 giờ" / "Dừng 2 giờ 05
  phút" (whole minutes, rounded down) · "N ảnh chụp lúc này" or "Không có ảnh chụp lúc này",
  coordinates.

*Tests:* `StopDetectionTest` (`standing still for 20 min or more is one stop`, `exactly 20 min is
a stop, under 20 min is none`, `GPS jitter within the radius stays one stop`, `steady movement is
never a stop`, `a pause between segments at the same place counts as a stop`, `a pause between
far-apart positions is not a stop`, `two stays at the same place a few minutes apart merge into
one`, `points are ordered by segment and time before detection`, `a stay near an existing step in
place and time is not suggested`, `step arriving within an hour around the stay matches, later
does not`, `a recording in progress suggests nothing`, `a dismissed stay stays hidden`, `an added
stay is hidden while its step exists`, `photo count uses capture times inside the window`,
`suggestions of several tracks are sorted by time`, `duration reads in minutes and hours`);
`UseCasesTest.savingStepStoresPhotoCaptureTimesAndKeepsThemOnEdit`;
`MigrationTest.version3UpgradesWithPhotoTimesAndStopDecisionsAndKeepsTracks`; UI:
`TripFlowsTest.tripWithSampleTrack_showsStopSuggestions_andDismissHidesOne`. A real recording with
a 20-min stay, accepting and dismissing are checked by hand on the emulator
(`img/stop-suggest/`).

## Text rules

Titles, descriptions and notes are trimmed before validation and storage. Limits count
characters (UTF-16 code units on Android; use `String.count` on iOS, which counts grapheme
clusters — Vietnamese precomposed letters give the same count; combining-mark input may differ).
