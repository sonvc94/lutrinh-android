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

The route line joins step locations in R2 order, one leg (R12) after another. A leg with a usable
stored road route (R36) follows that road; every other leg is a straight segment (straight on the
Web-Mercator map; flights are arcs, 05-map.md). Zero or one step → no line.
*Tests:* `OrderingTest.routeFollowsTimelineNotInsertionOrder`,
`LegRoutesTest.roadLineIsJoinedToTheStopsByShortConnectors`.

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
save. **One exception, a step from a stop suggestion: see R17 "Add".** A manually added or edited
step never extends the trip: it keeps the error above.

## R6 — New-step defaults

When "Thêm điểm dừng" opens an empty step form:

1. **Arrival:** if today lies within the trip (inclusive; open-ended trips include every day from
   the start), use *now* truncated to the minute. Otherwise take the date of the last step (R2)
   plus one day, or the trip start date when there are no steps, at **09:00**, clamped into the
   trip range.
2. **Picker start point:** the step's own point (when editing); else the last step's location;
   else the Vietnam overview `(16.0, 106.0)` at zoom 5.
3. **Picker shortcuts (chips):** "Vị trí của tôi" (one device fix on demand; no stored location, no
   background permission) followed by the trip's other steps (timeline order, the edited step left
   out) — `stepChips`. There is no fixed list of places and no online search (D4).
4. **Default step name:** "Điểm dừng HH:mm" of the arrival, for a step made from a stop suggestion
   (R17) and for a suggestion from photos (R13) alike (one function, `DisplayFormat.defaultStepTitle`,
   test `DisplayFormatTest.defaultStepTitle_isTheArrivalTime`); a hand-made step is named by the user.
5. **Place wording** (step detail, step form, from-photos card): a point without a name is shown as
   "gần <nearest other step of the trip>, <km>" (`PlaceLabel`, straight line, km rule of
   docs/03 "Display conventions"; under 50 m just "gần <step>"), with the coordinates as a second
   line; with no other step the coordinates alone.

*Tests:* `OrderingTest.newStepDefaultsToNowWhenTravellingToday`,
`OrderingTest.newStepDefaultsToDayAfterLastStepClampedToTrip`, `OrderingTest.pickerOpensAtLastStep`,
`PlaceLabelTest` (nearest/label, own step excluded, no other step → null; the stop-suggestion card uses the same `PlaceLabel.nearest`),
`PhotoGroupingTest.suggestionsNumberAfterExistingStepsAndFlagDatesOutsideTheTrip` (default names).
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
* Position 0 is the step cover; tapping a thumbnail moves it to position 0; long-press + drag
  puts it anywhere (R27). The list may hold videos (R29); they follow the same rules.
* At most 30 photos and videos per step (`TOO_MANY_PHOTOS`).

*Tests:* `UseCasesTest.savingStepNumbersPhotosAndKeepsIdsOfExistingOnes`,
`RoomRepositoryTest.savingStepReplacesPhotosAndReportsRemovedFiles`,
`UseCasesTest.savingStepStoresPhotoCaptureTimesAndKeepsThemOnEdit`.

## R9 — Unsaved imports

Photos are copied into app storage as soon as they are picked (06-photos.md). If the form is
left without saving, or a picked photo is removed before saving, those copies are deleted, so
storage only holds files referenced by a row.

## R10 — Stats

*Tests:* `OrderingTest.haversineHaGiangToNinhBinh`, `OrderingTest.statsCountDaysInclusive`,
`OrderingTest.distanceIsTheStraightLineUnlessGpsTracksCoverTheRoute`,
`LegRoutesTest.tripTotalsUseRoadKmAndTimeAndMarkPartialTotals`.

Shown on Trip detail: number of steps, number of photos, distance = sum of the legs (R12): the
road distance of a leg with a usable stored route (R36), otherwise the great-circle (haversine,
Earth radius 6371.0088 km) distance between its two steps. The second line says where the number
comes from (`TripStats.distanceSource`): "đường chim bay" (no leg along the road), "theo đường
đi" (every leg along the road), "theo đường đi, có chặng chim bay" (some). Below the counts, when
at least one leg has a road route: "~T di chuyển (ước tính)" = the sum of those legs' router
estimates; when other legs have none, "~T di chuyển (ước tính, chưa tính N chặng)"
(`TripStats.durationS`, `.legsWithoutTime`). When the **ended
tracks** of the trip add up to at least that straight-line distance (and more than 0), the tile
shows the track total instead, second line "đã ghi GPS" (`TripStats.distanceSource`); a shorter
track total (a partial recording) never lowers the figure. Days = `endDate − startDate + 1` (only
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
* A leg's distance is its road distance and estimated time when a usable route is stored (R36):
  "Xe máy · 48 km · ~1 giờ 33 phút"; otherwise the haversine distance (R10) between its two steps,
  marked "chim bay": "Thuyền · 4,1 km chim bay".
* Drawing per mode (stroke kind, great-circle flight arc, badge): 05-map.md. Road-following lines
  for Đi bộ, Xe máy, Ô tô, Xe buýt and "Chưa chọn": R36.

*Tests:* `LegsTest.legsFollowTimelineOrderAndTakeTheModeOfTheArrivalStep`,
`LegsTest.codesRoundTripAndUnknownCodesReadAsNotSet`, `LegsTest.onlyFlightsAreDrawnAsArcs`,
`LegsTest.setLegTransportUpdatesAndClearsAndSaveStepKeepsIt`, drawing maths: `RouteGeometryTest` (flight arc,
simplification, badge midpoint), `CameraMathTest`,
`MigrationTest.version1UpgradesToCurrentWithoutDataLoss`; UI: `TripFlowsTest.setLegTransport_fromTheTimeline_showsAndStoresTheMode`.
*Inspired by* polarsteps docs/22 (transport mode per leg). *Changed:* own mode list (xe máy,
xe buýt/xe khách for Vietnam), no automatic mode guess; routed lines come from an OSM router (R36).

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

Suggestions (`suggestStepsFrom`): one per group, titled "Điểm dừng HH:mm" (the group's arrival time, as for a stop suggestion). Undated groups get the R6 arrival. A group whose date lies outside the
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
* Each trip gets a colour from an 8-colour palette (docs/08 "Trip colours") by creation order (`createdAt`, then id),
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
  of the offline basemap "Bản đồ" (`Positron.STYLE_URL`; never satellite imagery, R33), its sprite, its fonts (Latin/Vietnamese glyph ranges, no CJK) and
  every tile of the style's sources inside the box for **zoom 0–14**. The vector tiles end at
  z14, so maps over-zoom them up to z19 with the same street and village names. Style, sprite
  and fonts are shared between areas and stored once. Nothing about trips, steps or photos is
  stored there (they are already local).
* **Size.** Before a download the screen shows an estimate: 17 MB fixed (style + sprite + fonts)
  + Σ over z0–14 of (tiles in the box at z × the measured average tile size at z). The download
  limit is **R31** (50 MB and 3 000 tiles per area). Measured on
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
  trip, **and only while the trip has not ended** (`TrackRules.canRecordOn`: no end date, or end
  date today or later). For a trip whose end date is before today both screens show "Tạo chuyến
  mới và ghi" instead (opens the trip form; the saved trip opens and starts recording). The
  recorder itself still accepts any existing trip. **One recording in the whole app**: while a track is open (recording or paused) other
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
  Vietnamese decimal comma), "125 km"; a running timer as "m:ss" or "h:mm:ss", a finished track's duration in words ("6 giờ 45 phút"); see 03-screens.md "Display conventions".
* **Stop.** Ends the track (`ended`, `endedAt = now`, open segment closed), stops the GPS and
  removes the notification. A track with **fewer than 2 points or under 50 m** in total
  (`TrackRules.MIN_KEEP_POINTS`, `MIN_KEEP_DISTANCE_M`, `isWorthKeeping`) is **deleted** instead
  of kept; every stop emits a `StopOutcome` (`Saved` / `Dropped`) and the app shows a snackbar
  "Đã lưu hành trình 12,3 km" or "Hành trình quá ngắn (dưới 50 m) nên không được lưu". (A track
  ended by process death, see below, follows the same keep rule, without a snackbar.)
* **Process death.** Every accepted point is stored at once, together with the track's distance /
  count / `updatedAt`, in one transaction: nothing accepted is lost. If the app process dies
  while a track is open (force-stop, crash, low-memory kill of the service), the system does not
  restart the recording (Android: `START_NOT_STICKY`); at the next app start every open track
  that is not the live one is **ended**: `interrupted = true`, `endedAt` = its last save (last
  point, or the pause/resume time), duration counted up to that moment; the points stay. The list
  shows it with "Dừng do ứng dụng bị đóng khi đang ghi". Recording never resumes by itself.
  **The keep rule of Stop applies here too:** an interrupted track with fewer than 2 points or
  under 50 m (`isWorthKeeping`) is deleted with its points, not kept as an empty row;
  `recoverInterrupted()` returns one `StopOutcome` (`Saved` / `Dropped`) per track found, not
  shown to the user (nobody is looking at a fresh process).
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
with its saved points`, `process death drops an empty or too short open track like stop does`, `a paused track interrupted keeps the time before the pause`, `delete
removes an ended track and its points but never the open one`, `deleting the trip stops its
recording`, `a track deleted under the recorder ends the recording instead of crashing`, `stopping
drops a track with fewer than 2 points or under 50 m and says so`, `the keep rule needs 2 points
and 50 m`, `recording is offered unless the trip ended before today`);
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
  trip, R6). Photos already in the trip are never moved into it; photos in the phone's gallery
  taken during the stop are offered, ticked, in "Ảnh chụp trong lúc dừng" (R19).
* **Add outside the trip's dates (extends the trip).** A suggestion can lie outside the trip's
  dates (a track recorded on a day the trip does not cover). Only for a step from a suggestion,
  when "Lưu" fails with nothing but `BEFORE_TRIP_START` / `AFTER_TRIP_END`, the editor asks:
  one of three texts (`TripExtension.caseOf` → `ExtendCase`): **after the end** "Điểm dừng này sau ngày
  kết thúc chuyến đi (<current dates>). Kéo dài chuyến đi đến <new dates>?"; **before the start**
  "Điểm dừng này trước ngày bắt đầu chuyến đi (<current dates>). Lùi ngày bắt đầu về <new dates>?";
  **open-ended trip** (always before the start) "Điểm dừng này trước ngày bắt đầu chuyến đi (<từ date>,
  chưa có ngày kết thúc). Lùi ngày bắt đầu về <new dates>?" — with "Kéo dài và lưu" / "Hủy".
  *Test:* `PlaceLabelTest` (`ExtendCase` per case, three distinct strings, no "đến từ"). Accepting widens the trip **only as far as needed** and saves
  trip + step in **one transaction** (`StepRepository.saveStepAndTrip`; `SaveStep(…, extendTrip =
  true)`), then writes the `added` decision as usual: a day before the start moves `startDate` to
  that day, a day after the end moves `endDate` to it (date only; `updatedAt` of the trip is
  stamped). A trip without an end date (ongoing) only ever widens its start; it never gets an
  end. "Hủy" saves nothing, the editor stays open with the R5 error shown. Any other error
  (title blank, …) is shown as usual and no question is asked; an invalid step never changes the
  trip. The pure computation is `TripExtension.widen(start, end, day): TripDates?` (null =
  inside, nothing to do). Other flows (manual steps, "Thêm từ ảnh", trip edit) are unchanged.
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
`UseCasesTest.savingStepStoresPhotoCaptureTimesAndKeepsThemOnEdit`; extending: `ValidationTest`
(`extensionMovesStartBackWhenDayIsBeforeIt`, `extensionMovesEndForwardWhenDayIsAfterIt`,
`extensionIsNullInsideTheDatesInclusive`, `ongoingTripOnlyWidensItsStart`), `UseCasesTest`
(`extendTripWidensTheEndAndSavesTheStep`, `extendTripWidensTheStartOnly`,
`extendTripLeavesAnOngoingTripOpen`, `extendTripChangesNothingWhenTheStepIsInside`,
`invalidStepDoesNotWidenTheTrip`), `RoomRepositoryTest.saveStepAndTripStoresBothTogether`;
`MigrationTest.version3UpgradesWithPhotoTimesAndStopDecisionsAndKeepsTracks`; UI:
`TripFlowsTest.tripWithSampleTrack_showsStopSuggestions_andDismissHidesOne`,
`TripFlowsTest.stopSuggestionOutsideTheTrip_offersToExtendIt`. A real recording with
a 20-min stay, accepting and dismissing are checked by hand on the emulator
(`img/stop-suggest/`).

## R18 — Share a trip ("Chia sẻ chuyến đi")

Rules live in `domain/usecase/TripShare.kt` (stats, photo choice, file names) and
`domain/usecase/TripPage.kt` (the page and its size cap), both pure; the iOS port copies them.
Rendering (map, image, photo encoding, files) is `share/TripExporter.kt`, `share/SummaryImage.kt`
and `map/StaticTripMap.kt` (docs/05 "Static map for sharing", docs/06 "Export").

* **What.** Two outputs of one trip, made on the phone, nothing uploaded (no link, no server):
  **"Ảnh dọc"** = one portrait PNG 1080 × 1920; **"Trang xem offline"** = one self-contained HTML
  file that opens offline in any browser. Both are rebuilt live while the share screen is open
  (trip, steps, tracks, "Ẩn ghi chú").
* **Image contents.** Title (max 2 lines, then "…"), the trip dates as on the trip screen
  ("… – …", open-ended "từ …"), the map with the route (R3, legs drawn per mode, R12), the
  recorded tracks (R16) and numbered pins, fitted to all steps and track points (docs/05); the
  map attribution in its bottom-right corner; four stats; up to four photos; the "Lữ Trình" mark.
* **Stats** (`TripShare.stats`). km = R10 distance rounded to a whole km (straight lines between
  steps, not the GPS tracks); ngày = `endDate − startDate + 1`, or for an open-ended trip from the
  start date to the last step's date (+1), at least 1; điểm dừng and ảnh = counts of the trip.
  Shown in this order on the image, the page and Trip detail: điểm dừng, ảnh, km, ngày
  (`DisplayFormat`, 03-screens.md "Display conventions"; thousands with ".").
* **Image photos** (`TripShare.imagePhotos`). At most 4. With ≥ 4 steps that have photos: the
  first photo (by position) of 4 of them evenly spaced along the timeline, first and last such
  step always in (index `round(i·(n−1)/3)`). With fewer: each such step's first photo, then their
  second photos in turn, and so on, until 4 or none are left. No photos → no photo row.
* **Page contents** (`TripPage.build`). Title, dates, trip description, the map image with the
  plain-text caption "Bản đồ: <attribution>", the four stats, then every step in timeline order
  (R2) grouped by day "Ngày N · <weekday>, dd/MM/yyyy" (N from the trip start, as on Trip detail):
  number, title, time `HH:mm`, note (line breaks kept) and its photos in position order; footer
  "Tạo bằng Lữ Trình …". A photo whose file is missing is skipped.
* **Offline and safe.** All user text (title, description, step titles, notes) is HTML-escaped
  (`& < > " '`); CSS is inline; images are `data:image/jpeg;base64` URIs only; no `<script>`, no
  `<link>`, no `url(…)`; a `Content-Security-Policy` meta tag (`default-src 'none'; img-src data:;
  style-src 'unsafe-inline'`) forbids any request. The attribution is text, never a link.
* **Size cap** (`TripPage.buildWithinCap`, `CAP_BYTES` = 15 000 000 bytes, UTF-8). The page is
  built at the first level that fits: photos 1280 px / JPEG q80 → 1280/q65 → 1024/q60 → 800/q50 →
  800/q50 with at most 4, then 2, then 1, then 0 photos per step (a step's first photos are
  kept). If even the last level is over the cap it is used anyway. Removed photos are counted:
  the page footer and the share screen say "Đã lược bớt N ảnh …".
* **"Ẩn ghi chú".** A switch on the page output, off by default, not stored. On: step notes are
  left out of the page (titles, times and photos stay). The image never has notes.
* **Privacy.** The files contain the trip's places (map and pins), step names and times, notes
  (unless hidden) and photos. The share screen always shows "Tệp chia sẻ có bản đồ, tên và thời
  gian các điểm dừng của bạn. Chỉ gửi cho người bạn tin." Photo files carry no metadata (re-encoded,
  docs/06), so no EXIF GPS or device data leaves the phone; coordinates are not written as text.
* **File names** (`TripShare.fileName`). `LuTrinh-<slug>-<start yyyyMMdd>.png|html`; slug =
  title without Vietnamese marks (đ → d), lower case, anything else → "-", at most 40 characters,
  empty → "chuyen-di". Example: `LuTrinh-vong-cung-ha-giang-20251020.png`.
* **Temporary files.** Exports are written to the app cache `share/` folder, which is emptied
  when the share screen opens; "Chia sẻ" hands the file to the system share sheet as a
  `content://` URI (FileProvider) with a read grant (Android `ACTION_SEND`).
* **"Lưu vào máy".** Android 10+: a copy goes to shared storage, the image to
  `Pictures/LuTrinh`, the page to `Download/LuTrinh` (no permission needed); a name already
  there gets a number from the system. Android 8–9: the system "save as" picker (no storage
  permission requested). Confirmation "Đã lưu vào <folder>".
* **No basemap.** Offline with the trip area not downloaded (or the style failing within 15 s),
  the map is the route on the plain basemap background colour; the share screen says so. It is
  never an error.

*Tests:* `TripShareTest` (`statsCountStepsPhotosDaysAndRoundKm`,
`openEndedTripCountsDaysUpToTheLastStep`, `imagePhotosSpreadCoversOverTheTrip`,
`fewStepsWithPhotosFillWithTheirNextPhotos`, `fileNameIsAsciiSlugWithStartDate`,
`pageEscapesUserTextIncludingScript`, `pageLoadsNothingFromTheNetwork`,
`pageHasTimelineByDayAndHidesNotesOnRequest`, `capLowersQualityThenDropsPhotos`,
`pageSizeIsCountedInUtf8Bytes`, `mapFitKeepsEveryPointInsideThePadding`,
`mapFitFollowsTheCameraRules`); UI: `TripFlowsTest.shareTrip_fromTheTripScreen_offersImageAndPage`.
The rendered image, the page in a browser, the share sheet, saving and airplane mode are checked
by hand on the emulator (`img/share-trip/`).

## R19 — Gallery photos of a stop ("Ảnh chụp trong lúc dừng")

Code: `domain/usecase/StopPhotos.kt` (pure: window, de-dup, offer, `GallerySelection`),
`data/GalleryReader.kt` (MediaStore, permissions), `ui/steps/StopPhotosUi.kt` + `StepEditViewModel`.

* **Where.** Only in the step editor opened with "Thêm điểm dừng" from a stop suggestion (R17).
  Manual new steps, step edits and "Thêm từ ảnh" have no such section.
* **Window.** From the stay's start − 10 min to its end + 10 min, both converted to the device's
  local time and truncated to the minute; inclusive at both ends (a photo at 09:50:59 is inside a
  window ending 09:50). Times are compared as local wall clock, like `takenAt` (docs/06).
* **Capture time of a gallery photo.** Read like at import: EXIF `DateTimeOriginal`, else
  `DateTimeDigitized`, else `DateTime` (local wall clock, to the minute); else the gallery's own
  date taken (Android `DATE_TAKEN`, iOS `creationDate`) in the device zone. No date → never
  offered.
* **Already in the trip (never offered twice).** A gallery photo is skipped when a photo of any
  step of this trip has the same `sourceId` (gallery id), or the same `takenAt` minute **and** the
  same `sourceSize` (original byte size; both must be known on both sides). The second test covers
  photos added with the Photo Picker, which gives no gallery id. Photos imported before DB v5
  have neither and so cannot be recognised (they may be offered once more).
* **Offer.** Inside the window, not already in the trip, each gallery id once; oldest first (ties
  by id). Video and cloud-only items are not read.
* **Selection.** Every offered photo starts ticked; a tap unticks / reticks. Only the unticks are
  remembered, so a refresh (screen resumed, more access granted) keeps them and newly visible
  photos arrive ticked; unticks of photos no longer offered are forgotten.
* **Import on save.** "Lưu" first copies the ticked photos exactly like picked photos (R11: copy
  into app storage, re-encoded, EXIF/GPS stripped; `takenAt` = their capture time;
  `sourceId` = gallery id; `sourceSize` = gallery size), appended after the step's "Ảnh (n)"
  photos, then saves the step (R8). The 30-photo limit counts both (`TOO_MANY_PHOTOS`, nothing
  copied). If one cannot be read, nothing is saved and "Không đọc được N ảnh" is shown (untick it
  and save again). Files copied for a save that then failed validation or waits for the
  "Kéo dài chuyến đi?" dialog (R17) are reused by the next "Lưu", not copied again; back without
  saving deletes them (R9). Nothing is copied before "Lưu".
* **Source on other imports.** Since v5 every import records `sourceSize`, and `sourceId` when the
  URI names a gallery item ("Thêm từ ảnh" documents `image:<id>`); Photo Picker imports have only
  the size.
* **Permission.** Nothing is asked until the user taps "Cho phép xem ảnh" under the explanation.
  Android 13+: `READ_MEDIA_IMAGES` (+ `READ_MEDIA_VISUAL_USER_SELECTED` on 14+, so the system
  offers "Allow limited access"); ≤ 12: `READ_EXTERNAL_STORAGE`. Full = all photos; partial
  (14+, only `READ_MEDIA_VISUAL_USER_SELECTED` granted) = only the photos the user selected, plus
  "Chọn thêm ảnh" which asks again; denied = the note, and the Photo Picker "Thêm ảnh" still
  works. The access is re-checked whenever the editor or the trip screen resumes (a revoke in
  Settings takes effect at once). Never asked for video, location in media (that stays "Thêm từ
  ảnh"'s `ACCESS_MEDIA_LOCATION`, R13) or anything in the background.
* **Count on the suggestion card.** With full or partial access the card adds "N ảnh trong máy"
  = the number this rule would offer for that suggestion now (0 included); without access
  nothing is added and nothing is asked from the trip screen.

*Tests:* `StopPhotosTest` (`window is ten minutes before the start to ten minutes after the end, to
the minute`, `window bounds are inclusive and seconds do not matter`, `window uses the given zone
for track instants`, `query range covers the window with a day of slack each side`, `only photos
with a capture time inside the window are offered, oldest first`, `a gallery photo already in the
trip by id is not offered again`, `same capture minute and same original size counts as already
imported`, `unknown size or time never matches by time and size`, `the same gallery id is offered
once`, `every offered photo starts ticked and a tap unticks and reticks it`, `an untick survives a
refresh, new photos arrive ticked, gone ones are forgotten`, `toggling a photo that is not offered
changes nothing`); `UseCasesTest.savingStepStoresPhotoSourcesAndKeepsThemOnEdit`;
`MigrationTest.version4UpgradesWithPhotoSourcesAndKeepsEverything`; UI:
`TripFlowsTest.stopSuggestionEditor_showsGalleryPhotosOfTheStop`. The permission dialog, partial
access, denial, import and de-dup are checked by hand on the emulator with photos pushed into
the gallery (`img/stop-photos/`, AGENTS.md "Test JPEGs").

## R20 — Draft steps ("Lưu nháp" / "Nháp" / "Đăng")

*Tests:* `DraftsAndCoverTest.tripStatsLeaveDraftsOut`, `.legsAndRouteSkipDrafts`,
`.onlyPublishedStepsAreNumbered`, `.shareLeavesDraftsOut`, `.openEndedShareDaysIgnoreALaterDraft`,
`.myMapTotalsLeaveDraftsOut`, `.saveDraftStoresADraftAndSaveOrPublishPublishesIt`,
`.draftsAreCountedApartInTheTripList`; `RoomRepositoryTest.draftsAreCountedApartAndCanBePublished`;
`MigrationTest.version5UpgradesWithDraftsAndCoverAndKeepsEverything`.

Code: `domain/usecase/Drafts.kt` (`published()`, `stepNumbers`), `PublishStep` in `UseCases.kt`,
`StepInput.draft`, `Step.isDraft` (DB v6 `steps.is_draft`).

* **Two saves.** The step form has "Lưu nháp" (stores the step as a draft) next to "Lưu" (stores
  it published). Both validate exactly like any step (R5, R8 …); a draft is not a half-filled step.
  "Lưu" on a draft publishes it; "Lưu nháp" on a published step makes it a draft again. The top-bar
  "Lưu" = the bottom "Lưu". The R17 extend-dates question keeps the button the user pressed.
* **Publish.** Step detail of a draft shows "Nháp", the hint "Bản nháp chưa có trên lộ trình, thống
  kê, bản đồ của tôi và tệp chia sẻ. Bấm “Đăng” khi đã xong." and the button "Đăng" (= publish,
  `updatedAt` = now, nothing else changes).
* **Where a draft shows.** In the trip timeline in its R2 place (hollow dot with a pencil icon
  instead of a number, muted text, "Nháp" badge), in step detail (title without number, prev/next
  include it), on the trip map and the header map as a dashed hollow pin (docs/05) with a card
  marked "Nháp", and in the location-picker chips. The trip list card adds "· N nháp".
* **Where a draft is left out.** Legs and route (R3, R12: the leg runs from the published step
  before to the published step after, so its transport mode is the one stored on that next
  step), numbering (published steps only, 1..n), trip stats (R10: steps, photos, km, GPS
  comparison), the trip list counts ("N điểm dừng · M ảnh" = published only), the share image and
  page (R18: stats, map, photos, steps; an open-ended trip's days ignore draft dates),
  "Bản đồ của tôi" (R14: pins, lines, totals).
* **Still counted everywhere it protects data:** R4 date checks, R7 delete confirm counts (trip
  delete says the total steps and photos, drafts included), R17 "already a step" matching, R19
  de-duplication and nearest-step place labels — a draft is a real stop, only not shown off yet.
* **Upgrade.** Existing steps are published (`is_draft` default 0).
* *Polarsteps (docs/22 §1.3) has "Publish / Save as draft" in the step editor; we keep the idea,
  with two buttons on the form instead of a sheet, and nothing leaves the device either way.*

## R21 — Trip cover ("Ảnh bìa": "Tự động" or a chosen photo)

*Tests:* `DraftsAndCoverTest.coverIsTheChosenPhotoElseTheFirstPhoto`,
`.coverFallsBackToAutomaticWhenTheChosenPhotoIsGone`, `.setTripCoverIsKeptByTripEditsAndClearedByAutomatic`;
`RoomRepositoryTest.chosenCoverWinsAndFallsBackToAutomaticWhenItsPhotoIsGone`;
`MigrationTest.version5UpgradesWithDraftsAndCoverAndKeepsEverything`.

Code: `TripCover` in `domain/usecase/Drafts.kt`, `SetTripCover` in `UseCases.kt`, `Trip.coverPhotoId`
(DB v6 `trips.cover_photo_id`), the cover subquery in `TripDao.observeSummaries`, `ui/trips/CoverPicker.kt`.

* **Choice.** Any photo of the trip's steps (drafts included; candidates in timeline order, then
  photo position) or "Tự động". Picked in "Sửa chuyến đi" (row "Ảnh bìa" → "Đổi ảnh bìa", stored
  on "Lưu", Back without saving discards it) or from trip detail ⋮ "Đổi ảnh bìa" (stored at once).
  The row and the menu entry exist only for a saved trip; a trip without photos shows "Chuyến đi
  chưa có ảnh…" in the sheet.
* **Automatic** (`coverPhotoId` null): the first photo (by position) of the first step in timeline
  order that has photos — unchanged from before v6. Videos are never the cover (R29): the picker
  lists photos only and both cover subqueries add `media_type = 'photo'`, so a step whose first item
  is a video gives its first photo. Drafts count here: the cover is private (the
  trip list), it never leaves the device.
* **Fallback.** A chosen id that no longer names a photo of this trip (photo removed from its step,
  step or trip deleted, an id from another trip) reads as automatic; the stale id is not cleaned
  up, and the form shows "Tự động" and stores null on the next "Lưu". Editing a step keeps its
  photos' ids (R8), so a chosen cover survives edits of its step.
* **Where it shows:** the trip list card (the only place a trip cover is drawn). The share image
  (R18) keeps its own photo choice (`TripShare.imagePhotos`), not the cover.
* *Polarsteps (docs/22 §1.2) lets the trip editor pick a cover photo; ours picks from the trip's own
  step photos only (no upload, no crop).*

## R22 — My location on the maps and the battery guide ("Vị trí của tôi", "Ghi hành trình ổn định")

Code: `ui/common/MyLocation.kt` (state + button + rationale), `ui/picker/DeviceLocation.kt`
(`requestFix`), `map/maplibre/MyLocationLayers.kt` (docs/05), `ui/tracks/BatteryGuideScreen.kt`,
`domain/usecase/BatteryGuide.kt` (`BatteryGuide.makerOf`, `ShowOnce`), `data/LocalPrefs.kt`.

**My location (trip map, My map)**

* **One fix per tap.** Nothing is tracked while the screen is open and nothing is stored or sent;
  the chàm dot is the fix of the last tap and goes with the screen.
* **Flow.** Tap → location permission granted (fine or coarse)? No: the explanation dialog "Dùng vị
  trí của bạn?" (Hủy / Tiếp tục) → system prompt. Granted → location switched on in the system?
  No: snackbar "Vị trí của máy đang tắt." + "Mở cài đặt" (location settings). Yes → one fix
  (GPS if fine permission and enabled, else network, else last known); the dot + accuracy circle are
  drawn and the camera flies there (zoom ≥ 15, never zooming out).
* **Refused.** After the system prompt, still no permission: snackbar "Chưa được phép dùng vị trí
  của máy." + "Mở cài đặt" (the app's details page). No fix: "Chưa xác định được vị trí của bạn…".
* **Tap again** = a new fix and the camera re-centres. A tap while a fix is pending is ignored.
* Never asks for background location (the manifest still removes it).

**Battery guide**

* **Section by maker** (`BatteryGuide.makerOf(Build.MANUFACTURER, Build.BRAND)`, case-insensitive,
  trimmed): xiaomi/redmi/poco → Xiaomi; oppo/realme/oneplus → Oppo; samsung → Samsung;
  vivo/iqoo → Vivo; anything else (or blank) → general steps only. The manufacturer is tried first,
  then the brand (a Redmi reports manufacturer "Xiaomi"; some resellers set only the brand).
* **Show once.** `ShowOnce.consume()` is true exactly once: the first time a recording actually
  starts (permissions granted, location on) the guide screen opens by itself; `markSeen()` (the
  guide opened from the GPS card) also uses it up. A recording that fails to start (location off,
  permission refused) does not use it up. The flag lives in on-device preferences and is never
  sent anywhere.
* **Button.** "Mở cài đặt pin của Lữ Trình" opens the system battery-optimisation list
  (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), the app's details page if that fails. The
  app never requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

*Tests:* `BatteryGuideTest` (`xiaomi_family_shares_a_section`, `oppo_family_shares_a_section`,
`samsung_and_vivo`, `others_and_blank_get_the_general_steps_only`,
`matching_ignores_case_and_spaces`, `guide_opens_by_itself_only_the_first_time`,
`opening_the_guide_by_hand_uses_up_the_automatic_one`,
`a_flag_remembered_from_an_earlier_run_is_respected`). The location button, the permission
dialog, the denied snackbar, the guide on the first and second recording and the settings button
are checked by hand on the emulator (`img/my-location/`, `adb emu geo fix 105.85 21.03`).

## R23 — Offline place search ("Tìm địa danh" in the location picker)

Code: `domain/usecase/Places.kt` (`PlaceText`, `Gazetteer.search`), `data/PlaceIndex.kt` (loads the
asset once, in the background), `ui/picker/PlaceSearchUi.kt`, `ui/picker/LocationPickerScreen.kt`.
Data: `assets/places/vn_places.tsv`, built from OpenStreetMap by `tools/gazetteer/build_gazetteer.py`
(docs/05 "Place search"). **Search never uses the network**: it works in airplane mode.

* **Normalising** (query and names alike): Unicode NFD, combining marks dropped, đ/Đ → d, lower
  case, every run of other characters → one space. "Đồng Văn", "dong van" and "DONG-VAN" are equal.
* **Keys of a place:** its name and aliases (OSM `alt_name`, `old_name`, `short_name`,
  `official_name`, `name:en`, `name`), each also without one generic leading word ("xã", "phường",
  "thị trấn", "thành phố", "tp", "tỉnh", "huyện", "khu du lịch (sinh thái)", "quần thể danh thắng",
  "bãi biển", "phố cổ", "cột cờ", "đèo", "núi", "thác", "chùa", "đền"). Words that also start real
  names without accents ("động/đồng", "hồ", "bản", "làng/lạng", "hang", "thôn", "bãi") are not
  generic.
* **Provinces:** the 34 in force since 1 July 2025; each lists the provinces merged into it as
  former names (`FORMER_PROVINCES`, e.g. Tuyên Quang ← Hà Giang, Ninh Bình ← Hà Nam + Nam Định). A
  former name finds the merged province and is shown with the result ("trước đây Hà Giang").
* **Match** (query of ≥ 2 characters; every query word must start a word): tier 0 = a key equals
  the query; 1 = a key starts with it; 2 = every query word starts a word of the keys; 3 = some
  word is in the keys and the rest in the province's current or former name ("dong van ha giang").
* **Ranking:** tier, then score = importance − 12 · ln(1 + km/10), km = distance to the nearest
  step of the trip (no steps: importance only), then name. Importance: province 100, city 80, town
  60, commune/ward 55, bay 60, sight 50, pass 50, viewpoint/peak/waterfall/cave/beach/island/lake/
  worship 45, museum/historic 40, camp site 30, village 30, hamlet 20; +20 with a Wikidata/
  Wikipedia link. Results with the same name (without its generic word) in one province within
  5 km are folded into the first. At most 20 results.
* **Picking** a result moves the pin there (zoom 8.5 for a province, else the picker zoom), closes
  the list and shows the name on the bottom card; "Dùng vị trí này" returns the point **and** that
  name while the pin has not moved more than 100 m (a province is never returned as a name).

*Tests:* `PlacesTest` (`normaliser drops diacritics, đ and case`, `ascii query finds the accented
place, exact before prefix`, `generic prefix and alias words match`, `former province name finds
the merged province`, `ranking by importance, then distance to the trip, folding duplicates`,
`bundled gazetteer - 34 provinces and the acceptance searches`). Checked by hand in airplane mode:
`img/place-search/01`–`04`.

## R24 — Automatic place name ("Tên địa điểm" follows the location until the user types one)

Code: `Gazetteer.nearest`, `AutoName`, `AutoTitle` (`domain/usecase/Places.kt`); used by
`StepEditScreen` (`StepForm.placed`), `FromPhotosScreen` (`StepSuggestion.placed`) and
`PlaceLabel.label(…, places)`.

* **Nearest place of a point:** the closest sight, village, hamlet, town or city within **3 km**;
  if none, the closest commune/ward, town or city centre within **15 km**; else none (sea, border).
* **When the location is set** — search pick (its name), pin drop, "Vị trí của tôi" or a step chip
  in the picker, a step from photos (the photo GPS), a step from a stop suggestion (the stay's centre)
  — the step name becomes that place's name **if the name is not typed**: blank, still the default
  "Điểm dừng HH:mm" (R6), or the name this rule filled in last. A name the user typed (anything
  else) is never replaced. When the point moves somewhere without a nearby place, an auto-filled
  name goes back to what was there before it (blank or the default).
* The lookup runs after the gazetteer is loaded (a few seconds after the app starts); a location
  set earlier gets its name as soon as it is ready, unless the location changed meanwhile.
* **Place labels** (step form, step detail, from-photos cards, stop suggestion cards): a step of
  the trip within **5 km** keeps "gần <điểm dừng>, N km"; otherwise the gazetteer place: "<Tên>,
  <Tỉnh>" within 0.3 km, else "gần <Tên> (<Tỉnh>), N km"; with neither, the old label (nearest
  step at any distance, or coordinates only).

*Tests:* `PlacesTest` (`nearest place - sight or village within 3 km, else a commune centre within
15 km`, `auto title fills a blank or default name and never overwrites a typed one`, `place label
keeps a near step, else names the gazetteer place`, `bundled gazetteer - a point near Ma Pi Leng
gets a nearby name`). Checked by hand: `img/place-search/05`–`07`.

## R25 — GPX export and import ("Xuất GPX", "Nhập tệp GPX")

Code: `domain/usecase/Gpx.kt` (`GpxFormat`, `GpxWriter`, `GpxImport`, `ExportGpx`, `ImportGpx`),
`domain/usecase/GpxParser.kt` (SAX), `data/RoomTripImportRepository.kt` (one transaction),
`ui/transfer/TransferUi.kt` (menus, file picker, dialogs). File format: 02-data-model.md "GPX file".
No storage permission: files go through the system file picker (Android SAF / iOS document picker)
or the share sheet.

* **Export** (trip card ⋮ or trip detail ⋮ → "Xuất GPX" → dialog "Xuất tệp GPX" → "Lưu vào tệp…"
  (system "save as", default name `<tên chuyến đi>.gpx`) or "Chia sẻ…" (share sheet)): ONE GPX 1.1
  file per trip:
  * `metadata`: trip title, description (when not blank), export time;
  * one `wpt` per **published** step (drafts are never exported, R20) in timeline order: `lat`/`lon`,
    `time` = the step's local arrival time read in the device time zone and written in UTC, `name`
    = step title, `desc` = note (when not blank);
  * one `trk` per recorded track with points (oldest first), named after the trip; one `trkseg`
    per track segment (pauses split segments, R16); each `trkpt` has `lat`/`lon`, `ele` when the fix
    had an altitude, and `time` in UTC. Every stored point is written (never simplified).
  * Times: ISO-8601 UTC with `Z`, fractions only when not whole seconds (`2025-10-20T01:00:00Z`,
    `…02:00:00.123Z`). Coordinates: decimal degrees, ≤ 7 decimals, never an exponent.
  * Text is XML-escaped (`& < > " '`); characters XML 1.0 forbids (control characters) are dropped;
    Vietnamese is written as UTF-8 as is.
* **Import** (trip list ⋮ "Tùy chọn khác" → "Nhập tệp GPX" → pick a file; any MIME type is
  offered, because many apps label GPX `application/octet-stream`): always a **NEW** trip, never
  merged into an existing one.
  * Read: GPX 1.1 and 1.0 (elements matched by local name, any namespace); `trk`/`trkseg`/`trkpt`,
    `rte`/`rtept`, `wpt`; `name`, `desc` (else `cmt`), `time`, `ele`; extensions and unknown
    elements are ignored. A time without an offset is read as UTC.
  * Trip: title = metadata `name`, else the first track/route name, else the file name without
    extension, else "Chuyến đi từ GPX" (≤ 60 characters); description = metadata `desc`
    (≤ 1000); start/end date = the local dates of the earliest/latest time in the file (no time
    at all: today, one day).
  * Steps: one per `wpt`, published, in file order: name kept (≤ 80), else the R24 automatic place
    name, else "Điểm dừng HH:mm"; note = `desc`/`cmt` (≤ 2000); arrival = the waypoint time in local
    time at minute precision, or the trip's first day 00:00 when it has none. No photos.
  * Tracks: each `trk` and each `rte` (one segment) becomes one ended track (status `ended`, not
    interrupted); empty segments are dropped; a point without a time takes the previous point's
    time (the first ones: the first time in the track); active time = sum of each segment's
    first→last time; distance = the R16 sum within segments.
  * Trip, steps, tracks and points are written in **one transaction**, then the new trip opens.
* **Errors — nothing is created**, a dialog "Không nhập được tệp GPX" says why: not well-formed
  XML (truncated, binary; with the line where reading stopped), root is not `<gpx>`, a point
  without a valid `lat`/`lon` (with its line), a file without any `trkpt`/`rtept`/`wpt`, an
  unreadable file. External DTDs and entities are never loaded (no file or network access from a GPX).

*Tests:* `GpxTest` (`writerOutputIsWellFormedGpx11`, `writerEscapesVietnameseAndXmlSpecials`,
`writerTimesAreUtcIso8601`, `parserReadsTracksRoutesAndWaypoints`, `parserAcceptsGpx10WithoutNamespace`,
`malformedFilesAreErrors`, `externalEntitiesAreNeverLoaded`, `malformedOrEmptyFileCreatesNothing`, `importMakesANewTripFromTheFile`,
`titleFallsBackToTheFileName`, `exportThenImportKeepsPointsAndStepNames`); fixture
`app/src/test/resources/gpx/sample.gpx`. Checked by hand on the captain's data: the Hà Giang
export has 359 `trkpt` (= its track points) and 10 `wpt` (= its published steps), `xmllint` clean;
importing it gives a new trip with 10 steps and 359 points (`img/gpx-backup/03`); a truncated file
shows the error (`img/gpx-backup/04`).

## R26 — Local backup and restore ("Sao lưu dữ liệu", "Khôi phục từ bản sao lưu")

Code: `domain/usecase/Backup.kt` (`BackupManifest`, `BackupCounts`, `BackupProblem`,
`BackupRules`), `data/BackupManager.kt` (write / prepare / restore), `ui/transfer/TransferUi.kt`.
File format: 02-data-model.md "Backup file". Data stays on the device: no account, no cloud, no
schedule; the user picks where the file goes (Downloads, a USB stick, a Drive folder through the
system picker — the app only sees a file).

* **Backup** (trip list ⋮ "Tùy chọn khác" → "Sao lưu dữ liệu" → system "save as", default name
  `LuTrinh-saoluu-<yyyy-MM-dd>.zip`): one zip with `manifest.json`, `lutrinh.db` (a consistent copy
  of all user tables, every table from the same moment) and `photos/<file>` for every photo file a
  `photos` row refers to (step photos, which also covers chosen trip covers, R21). A row whose file
  is already missing on the device is counted in `missingPhotoFiles` and skipped. Snackbar "Đã sao
  lưu <counts> (<size>)". A failed write deletes the half-written document.
* **Restore** (→ "Khôi phục từ bản sao lưu" → pick the file):
  1. **Checks, nothing changes yet:** the zip has `manifest.json` with format `lutrinh-backup`
     (else "không phải bản sao lưu"); `formatVersion` ≤ the app's; `schemaVersion` ≤ the app's
     database version — a **newer schema is refused** ("Hãy cập nhật ứng dụng"), an older one is
     accepted; `lutrinh.db` is present and its `user_version` equals the manifest; the number of
     `photos/` entries equals `photoFiles` and every name is a plain file name (no paths); the copy
     opens with the app's own migrations (older schema → current, like an app update) and its row
     counts equal the manifest. Any failure: a dialog "Không khôi phục được … Dữ liệu hiện tại không
     thay đổi."
  2. **Confirmation** "Thay thế toàn bộ dữ liệu?" with the backup's date and counts and the current
     counts, which "sẽ bị THAY THẾ hoàn toàn"; "Thay thế" (error colour) or "Hủy".
  3. **Replace, all or nothing:** photo files not on the device yet are added first; then in ONE
     database transaction every user table is emptied and refilled from the copy and the counts
     are checked; if anything fails the transaction rolls back and the added files are deleted, so
     the current data stays exactly as it was. Only after the commit are photo files that no row
     refers to any more deleted. The screens reload from the database (no restart).
* Refused while a track is recording ("Hãy dừng ghi trước khi khôi phục"). After a restore, a track
  the backup caught while recording is closed as interrupted (R16 recovery), not resumed.
* Not in a backup: downloaded offline maps (R15), the place gazetteer (app data), on-device flags
  (R22 battery guide), files in the share cache.

*Tests:* `BackupManagerTest` (Robolectric, real schema: `backupHoldsManifestDatabaseAndEveryPhoto`,
`restoreReplacesAllDataWithTheBackup`, `restoreFailureLeavesCurrentDataIntact`, `newerSchemaIsRefused`,
`otherFilesAreRefusedAndChangeNothing`, `olderSchemaBackupIsMigrated`, `backupRoundTripKeepsAVideo`,
`version6BackupRestoresIntoVersion7`). Since v7 the `photos/` entries include videos (R29) and the
counts "ảnh" in the snackbar and the confirmation count photos and videos together. Checked by hand on the captain's data:
backup of `2|14|16|1|359|1` (287 717 B, 16 photo files), restored over the same data: every row and
every photo file byte-identical (`img/gpx-backup/05`, `06`); and a schema-7 backup with two videos
(`2|15|19|1|359|1`, 454 951 B) restored over 18 files: 19 files md5-identical (`img/photo-video/11`).

## R27 — Photo order ("Nhấn giữ rồi kéo để đổi thứ tự")

Code: `MediaOrder` in `domain/usecase/Media.kt` (pure), `ReorderableMediaRow` in
`ui/steps/MediaStrip.kt`, `StepEditViewModel.reorderPhotos`; stored as `photos.position` (R8,
dense since DB v7, 02-data-model.md).

* **Gesture.** In the step editor's strip ("Ảnh & video"), long-press a tile, then drag it
  sideways: the tile lifts (slightly larger, shadow), the others slide aside as its centre passes
  theirs, and the strip scrolls when the tile reaches an edge. On release the new order replaces
  the form's list; **"Lưu" stores it** (Back without saving discards it, like any edit). A tap
  still means "make this the step cover" (R8: moves it to the front); "×" still removes. Each tile
  also has the accessibility actions "Đưa lên trước" / "Đưa ra sau" (TalkBack; one place, nothing
  past the ends). Gallery suggestions ticked in R19 are not in the strip yet (they become files on
  save, appended at the end) and do not take part.
* **Order everywhere.** Saving renumbers the list 0…n-1 (R8) and keeps every photo's `id`,
  `createdAt`, `takenAt`, source and video length; the first item is the step cover. Timeline
  thumbnails, step detail (header + grid), the viewer (and its "i / n"), the share image/page (R18
  photo choice reads position), GPX has no photos (R25), the backup (R26) copies the column.
* **Trip cover.** A chosen trip cover (R21) is a photo id, so reordering never changes it; the
  automatic cover follows the new first photo of the first step.
* **Upgrade.** DB v6 → v7 renumbers existing positions densely in the order already shown
  (`position`, then `createdAt`, then `id`), so nothing moves on screen (02-data-model.md
  "Migrations").

*Tests:* `MediaTest.moveTakesAnItemToAnyPlaceAndKeepsTheOthersInOrder`,
`.moveEarlierAndLaterStepOnePlaceAndStopAtTheEnds`, `.reorderedPhotosPersistWithTheirIdsAndTheCoverIsUnaffected`,
`.aSavedVideoKeepsItsTypeAndLengthThroughReorder`; `RoomRepositoryTest.photosReadInStoredOrderAndAVideoIsNeverTheCover`;
`MigrationTest.version6UpgradesKeepingPhotoOrderAndAddsMediaType`. Checked by hand on the
captain's data (emulator-5580): "Đèo Mã Pí Lèng" photos dragged last → first, saved, Back to the
list and reopened: the timeline, the detail grid and the viewer show the new order, photo ids
unchanged (`img/photo-video/01`–`04`); then dragged back (dump identical to before).
*Polarsteps lets the step editor drag photos too; ours keeps the order per step, no captions.*

## R28 — Take a photo in the app ("Chụp ảnh")

Code: `data/MediaCapture.kt`, `CaptureWithSystemCamera` in `ui/steps/MediaStrip.kt`,
`StepEditViewModel.startCapture` / `onCaptureResult`; FileProvider path `capture/` in
`res/xml/share_paths.xml`.

* **Camera.** "Chụp ảnh" (next to "Thêm ảnh/video") opens the phone's own camera app
  (`ACTION_IMAGE_CAPTURE`). The app declares **no CAMERA permission** and has no camera code: the
  camera writes into a file the app owns, `<cache>/capture/<uuid>.jpg`, handed over as a
  FileProvider `content://` URI with a one-time write grant. No camera app → "Không mở được ứng
  dụng máy ảnh."
* **Result.** OK with a written file → imported like a picked photo (R11: upright, ≤ 2560 px, no
  GPS EXIF) into `photos/`, appended to the strip; `takenAt` = now (minute), no gallery source;
  its place is the step's (photos have no location of their own). A camera that ignores the file
  and returns its own URI is imported from that URI.
* **No leftovers.** The capture file is deleted in every case — cancelled, OK with nothing written,
  failed import, success (`MediaCapture.finish`). The pending capture's name is kept in the saved
  state, so a result after process death is still handled; when the editor opens with no capture
  pending, files left in `capture/` by a dead process are cleared. Like any import, a captured
  photo not saved with the step is deleted when the form is left (R9).
* At most 30 photos/videos per step (R8): the three add buttons are disabled once the strip holds 30
  (and while an import runs).

*Tests:* `MediaCaptureTest.aCancelledCaptureLeavesNoFile`, `.anOkResultWithNothingWrittenLeavesNoFile`,
`.aTakenPhotoIsImportedAndTheTemporaryFileRemoved`, `.aCameraThatReturnsItsOwnUriIsImportedFromThere`,
`.aFailedImportStillRemovesTheTemporaryFile`, `.leftoversAreClearedAndOnlyPlainNamesResolve`.
Checked by hand on emulator-5580 (emulated camera): cancel → `files/photos` count 16 → 16 and
`cache/capture` empty; shutter + "Done" → 17 and the photo in the strip (`img/photo-video/05`, `06`).

## R29 — Short videos ("Quay video", videos in "Thêm ảnh/video")

Code: `VideoRules` / `VideoProblem` in `domain/usecase/Media.kt` (pure), `PhotoStorage.importVideo`,
`data/VideoMeta.kt`, `Photo.mediaType` / `durationMs` (DB v7), `VideoBadge` / `LocalMedia` in
`ui/common/Common.kt`, `VideoPage` in `ui/photos/PhotoViewerScreen.kt`.

* **Adding.** "Quay video" opens the system camera for video (`ACTION_VIDEO_CAPTURE`,
  `EXTRA_DURATION_LIMIT` 60 s) into `<cache>/capture/<uuid>.mp4` exactly as R28 (no permission,
  file always deleted, `takenAt` = now). "Thêm ảnh/video" (Photo Picker, images **and** videos)
  adds videos among the photos in the order picked; a picked video's `takenAt` is its recording
  time from the file metadata (UTC → local wall clock, minute), else null.
* **Limits.** A video is accepted when its length is **≤ 60 whole seconds** (60.9 s passes, so a
  recording stopped by the camera's limit is kept; 61.0 s is refused) and its size **≤ 200 MB**
  (decimal, 200 000 000 B; also enforced while copying, for providers that report no size). Longer,
  larger or unreadable videos are **refused, nothing is copied**, with "Video dài 1:05: chỉ thêm
  được video tối đa 60 giây (ứng dụng không cắt video).", "Video <size>: chỉ thêm được video tối
  đa 200 MB." or "Không đọc được video này." No trimming, no re-encoding.
* **Storage.** An accepted video is a byte copy `photos/<uuid>.mp4` (or its own extension) and a
  `photos` row with `mediaType = video`, `durationMs`; it counts towards the 30 per step (R8), takes
  part in order (R27) and in deletes (R7, R9) like a photo.
* **Showing.** Every thumbnail (strip, timeline, step detail) is a still frame with a play badge
  and the length ("Video 0:08", `DisplayFormat.clock`). The step-detail heading is "Ảnh & video
  (n)". In the viewer a video page plays full screen in the platform player (Android: VideoView +
  MediaController, no player library; tap shows play/pause/seek); it starts when its page becomes
  current and pauses when swiped away.
* **Not a cover.** A video is never the trip cover (R21: left out of the picker and the automatic
  choice). As the first item it is still the step's first thumbnail.
* **Share.** The share image and page (R18) show a still frame of a video, never the video.
* **Backup.** Videos are files in `photos/` and rows of `photos`, so a backup (R26) carries them and
  restore puts them back byte for byte; a schema-6 backup (no videos yet) restores into v7.

*Tests:* `MediaTest.videosUpTo60SecondsAnd200MbAreAcceptedLongerOrLargerRefused`,
`.aSavedVideoKeepsItsTypeAndLengthThroughReorder`, `.aVideoIsNeverTheTripCover`;
`VideoMetaTest.utcRecordingTimeBecomesLocalWallClockMinute`, `.missingPlaceholderOrGarbageDatesAreUnknown`;
`RoomRepositoryTest.photosReadInStoredOrderAndAVideoIsNeverTheCover`;
`BackupManagerTest.backupRoundTripKeepsAVideo`, `.version6BackupRestoresIntoVersion7`.
Checked by hand on emulator-5580: a ~3 s recording and a picked 8 s video show badges and play in
the viewer (`img/photo-video/07`, `09`, `10`); a picked 65 s video is refused with the message and
no file (`08`); backup with two videos and restore: every file md5-identical (`11`).

## R30 — Hoàng Sa and Trường Sa labels (sovereignty)

Code: `map/SovereigntyLabels.kt` (pure: areas, blocked names, the rule `hides`, the label GeoJSON;
the iOS port copies it), `map/maplibre/SovereigntyLayers.kt` (the same rule as a MapLibre filter + our label
layer, on every basemap of R32: `BasemapTest.hybrid_keepsSovereigntyFilterOnEveryLabelLayer`,
`.light_keepsTodaysLook`, `.snapshot_declaredPlanFiltersTheKnownLabelLayers`). Lữ Trình is made for Vietnam: no map ever shows a Chinese administrative name for Vietnam's
Hoàng Sa (Paracel) and Trường Sa (Spratly) archipelagos.

* **Areas.** Hoàng Sa box S 12.2, W 110.0, N 17.4, E 113.2; Trường Sa box S 6.8, W 110.0, N 12.2,
  E 116.3 (clear of the Vietnamese coast, Hainan, Palawan and Sabah). The boxes include the open sea
  west and south of the reefs, where the tiles name seamounts, ridges and hills in Chinese only
  (晋卿西海山 at 15.3 N 112.5 E, 10.7–15.7 N 110.2–112.7 E in all): rule (d) hides them
  (`class` strait, no `name:vi`). Land labels (Hainan, mainland, Philippines) are outside the boxes.
* **Hidden.** On every basemap label layer (place, water, road, airport names), a label whose point
  lies in an area is hidden when
  (a) its `name`, `name:vi`, `name:latin`, `name_en`, `name:en`, `name_int` or `name:zh` is a
  **blocked name**: 三沙市/三沙/Tam Sa/Thành phố Tam Sa/Sansha/Sansha City, 西沙区/西沙/Quận Tây
  Sa/Tây Sa/Xisha/Xisha District, 南沙区/南沙/Quận Nam Sa/Nam Sa/Nansha/Nansha District,
  永兴镇/Yongxing, 永乐群岛/Yongle Qundao, 七连屿/Qilianyu, 渚碧岛/Zhubi, and the other
  claimants' Latin names seen in the tiles: Kalayaan, Pag-asa, Pag-asa Island Harbor, Rancudo
  Airfield, Layang-Layang;
  (b) its `name:vi`, `name:latin`, `name_en` or `name:en` **contains** Tam Sa, Tây Sa, Nam Sa,
  Sansha, Xisha or Nansha (e.g. the water label "Hố Rồng (Tam Sa Vĩnh Lạc Long Động)");
  (c) **claimants:** it is an administrative place label — `class` country, state, province,
  region, county, district, municipality, city, town, village, hamlet, suburb, quarter,
  neighbourhood or isolated_dwelling — whose `name` is not one of the Vietnamese units
  **Trường Sa, Hoàng Sa, An Bang, Sinh Tồn, Song Tử Tây, Thuyền Chài D, Thuyền Chài E,
  Đá Tây A** (`VIETNAMESE_ADMIN`). So over the two archipelagos only Vietnamese administrative
  names remain: the Philippine municipality "Kalayaan" and its quarter "Pag-asa", Malaysian or
  Taiwanese units, Chinese ones in any script ("Tam Sa", "Xã khu Cam Tuyền", "Yongxing") all go,
  also when a name is not in (a); a new Vietnamese unit in a later tile build stays hidden until
  it is added to `VIETNAMESE_ADMIN` (the safe direction);
  (d) its local `name` is in a non-Latin script (tile field `name:nonlatin`), unless it is a
  natural feature (`class` island, ocean, sea, bay, strait, lake) with a Vietnamese `name:vi`, which
  is what the label shows (e.g. 永兴岛 → "Đảo Phú Lâm"; 太平島 → "Đảo Thái Bình"), so Chinese
  airports ("Sân bay Tam Sa Vĩnh Hưng") and Chinese-only names go.
  Kept: Vietnamese admin units above, islands/reefs through their Vietnamese `name:vi` ("Đá En
  Ca" for Malaysia's Terumbu Siput, "Đá Hoa Lau"), Vietnamese harbours ("Âu tàu Trường Sa"),
  ferry lines, "Sân bay Trường Sa". Outside the two boxes nothing changes (Guangzhou's own 南沙区
  keeps its label, Kalayaan's neighbours on Palawan too).
* **Shown.** The app draws **"Quần đảo Hoàng Sa"** at 16.45 N 111.95 E and **"Quần đảo Trường
  Sa"** at 10.0 N 114.3 E (italic, label ink, always placed) from **zoom 3 up to, not including,
  11**; above that the islands' own Vietnamese names take over.
* **Everywhere.** The trip map, step maps, location picker, "Bản đồ của tôi" and the share image /
  page map (R18) all apply it; offline packs (R15) store the same style, so offline maps too.

*Tests:* `SovereigntyLabelsTest.blockedNames_coverTheThreeAdminUnits_inBothScripts`,
`.chineseAdminUnits_areHidden_byEachOfTheirNames`, `.otherChineseScriptLabels_inTheAreas_areHidden`,
`.vietnameseAndLatinLabels_stay`, `.otherCountriesAdminNames_inTheAreas_areHidden` (Kalayaan, Pag-asa,
Malaysian and Taiwanese units, embedded "Tam Sa"), `.vietnameseAdminUnits_andNaturalFeatures_stay`,
`.outsideTheAreas_nothingIsHidden`, `.chineseSeamountNames_inTheSeaAroundTheReefs_areHidden`,
`.ourLabels_areInTheirAreas_andInTheGeoJson`, `.mapFilter_isTheSameRule`. Checked on emulator-5580,
"Bản đồ của tôi" (`img/sovereignty/`): before, "Tam Sa", "Yongle Qundao", "Quận Nam Sa", "Zhubi";
after, none of them, our two labels shown. Claimants (captain: "chỉ giữ tên tiếng Việt cho đơn vị
hành chính trong 2 vùng này", all countries): z5–14 tiles over both boxes decoded with
`tools/map-survey/` (docs/05) — no Kalayaan, Pag-asa or other foreign admin label remains; the
Vietnamese list of kept place labels is in docs/05.

## R31 — Offline download limit

OpenFreeMap's terms allow offline downloads of "≤3,000 tiles or 50 MB per offline download".
Code: `OfflineConfig.MAX_AREA_BYTES`, `MAX_AREA_TILES`, `estimateTiles`, `withinLimit`.

* **Limit.** One area may be downloaded only when its size estimate (R15) is **≤ 50 MB** (50 000 000
  B) **and** its tile estimate is **≤ 3 000** (both inclusive). Tile estimate = vector tiles in the
  box over z0–14 + relief raster tiles (style source `ne2_shaded`, up to z6) over z0–6. Hà Giang:
  1 764 estimated, 1 763 stored; ~29,6 MB estimated. In Vietnam the tile limit binds first (an area
  of 3 000 tiles is ~30–40 MB).
* **Refusal.** A larger trip area is not split: the trip card says "Vùng của chuyến đi quá rộng để
  tải một lần: ước tính <size>, <n> ô bản đồ; mỗi lần tải tối đa 50 MB và 3.000 ô (điều khoản của
  OpenFreeMap). Hãy tải một vùng nhỏ hơn, ví dụ các vùng có sẵn ở trên." and offers no download;
  the store also refuses it if asked. Ready-made areas (Hà Giang, Ninh Bình) stay within it.

*Tests:* `OfflineConfigTest.r31_limitsAreOpenFreeMapsTerms_inclusive`,
`.r31_tileEstimate_countsVectorAndRasterSources`, `.r31_readyMadeRegionsAndAHaGiangTripStayAllowed`,
`.r31_anAreaOverTheTileCapIsRefused_evenUnderTheByteCap`, `.tripArea_spanningTheCountryIsOverTheLimit`.

## R32 — Map style ("Kiểu bản đồ": "Bản đồ" or "Vệ tinh")

* **Where.** A layers button ("Kiểu bản đồ") on:
  * the trip map, above "Vị trí của tôi";
  * "Bản đồ của tôi", above "Vị trí của tôi";
  * the location picker, at the right edge below the quick-place chips.
* **Choices.** It opens a bottom sheet with one row per registered basemap provider, in
  registry order:
  * "Bản đồ", subtitle "Dùng được offline với vùng đã tải";
  * "Vệ tinh", subtitle "Cần mạng · không tải offline được".
  The current one has its radio button on. Tapping a row selects it and closes the sheet.
* **Persistence.** The choice is stored on the device: SharedPreferences `lutrinh_prefs`, key
  `map_style`, value = provider id `light` or `satellite`. It is read at the next start.
* **Scope.** It applies to every live map at once: the trip map, "Bản đồ của tôi", the picker
  and the small previews (trip detail header, step card).
* **Defaults.** No stored value or an unknown id (e.g. `mapbox-…` from an old build) = the
  default `light`. Choosing an unknown id stores `light`.
* **Never changed by the style:** routes, pins, step and leg selection, the camera, my
  location, the offline indicator.
* **Never "Vệ tinh":** offline packs (R15/R31) and the share image/page (R18) always use
  "Bản đồ".

*Tests:* `BasemapTest.registry_defaultIsTheOfflineMap`, `.registry_unknownOrMissingIdIsTheDefault`,
`.registry_defaultMustWorkOffline`, `.registry_idsAreUnique`, `.registry_newProviderNeedsNoOtherChange`,
`.mapStyle_choiceIsPersisted`, `.mapStyle_unknownStoredValueReadsAsDefault`,
`.offlinePacksAndShare_useTheOfflineMap`.

## R33 — Satellite needs a network

* **Online only.** "Vệ tinh" (Esri World Imagery) is online-only, since Esri does not allow
  storing its tiles. There is no offline pack for it and no satellite share image.
* **Fallback.** While the stored choice needs a network and the device is offline
  (`NetworkMonitor.online` = false), every map shows "Bản đồ" (with downloaded areas, R15).
* **Snackbar.** Each time the fallback starts, the screen with the map shows "Ảnh vệ tinh cần
  mạng".
* **Stored choice.** It stays "Vệ tinh". When the network returns, the maps show satellite
  again.

*Test:* `BasemapTest.satellite_offlineFallsBackToTheMap`.

## R34 — Attribution follows the style

* **Every map.** Every map shows the credit of the basemap it shows: live maps bottom right
  (9 dp text on white 75 %), the share image bottom right, and the share page footer.
  * "Bản đồ": "OpenFreeMap © OpenMapTiles © OpenStreetMap contributors".
  * "Vệ tinh": "Esri, Vantor, Earthstar Geographics, and the GIS User Community · OpenFreeMap ©
    OpenMapTiles © OpenStreetMap contributors". Roads and labels still come from OpenStreetMap.
* **Fallback.** During the offline fallback (R33) the credit is the one of "Bản đồ".
* **No SDK logo.** The MapLibre logo and (i) button stay hidden.

*Tests:* `BasemapTest.attribution_followsTheStyle`, `.esri_keylessWithoutKey_tokenWithKey`;
`TripShareTest` (share page credit).

## R35 — Maps only through the map interface

* **No SDK outside the adapter.** Screens, view models, share and data code use only:
  * the app's map interface (`MapController`, `MapEngine`, `OfflineMapStore`, map models);
  * the basemap providers (`BasemapProvider`, registry, settings).
* **One package.** The map SDK (MapLibre) is imported only in its adapter package
  `map/maplibre/`.
* **Changing the renderer or a tile provider** means writing one adapter and registering it,
  with no screen change (docs/05 "Map interface and providers").
* **Check:** `git grep -n "org.maplibre" -- app/src/main | grep -v app/src/main/java/vn/lutrinh/app/map/maplibre/`
  is empty.

*Tests:* `MapBoundaryTest.mapSdk_onlyInTheAdapterPackage`; the contract every adapter is driven by:
`MapPresentersTest.tripMap_pinsLegsAndBadges`, `.tripMap_cameraRules`,
`.tripMap_selectingAStepFliesThere_onlyOnChange`, `.tripMap_notInteractive_neverFlies`, `.tripMap_taps`,
`.tripMap_myLocationFliesOncePerSignal`, `.allTrips_linesDotsAndTaps`,
`.picker_startsOnce_andEasesToEachMove`; hybrid style: `BasemapTest.hybrid_imageryUnderRoadsAndLabels`,
`.hybrid_withoutBackgroundLayer_imageryGoesToTheBottom`.

## R36 — Legs along the road ("Tính đường đi theo đường thực tế")

* **Which legs.** Profile per leg mode (R12): Xe máy and **"Chưa chọn" → motorbike** (the most
  common way to travel in Vietnam), Ô tô → car, Xe buýt/xe khách → bus, Đi bộ → walk. Tàu hỏa,
  Máy bay and Thuyền are never routed (straight / arc / dotted as before).
* **Router.** Routing goes through the app's routing interface (`RoutingProvider`, like R35 for
  maps). Primary: Valhalla on the FOSSGIS OpenStreetMap server (keyless, ≤ 1 request/s);
  motorbike → Valhalla `motor_scooter`, the only profile that keeps off expressways (motorbikes are
  not allowed on them), tried again with `auto` when it has no route (car fallback below). Fallback for **car only** when the primary is unavailable: OSRM
  (FOSSGIS `routed-car`). The server addresses live in one registry, never in screens.
* **What is sent.** Per leg: the two stop positions and the profile, nothing else (no names,
  notes, photos, dates, account). It is the only data that leaves the device in this phase.
* **Switch and notice.** Routing is **on by default**. Before the first request the app shows a
  one-time notice ("Vẽ chặng theo đường đi?", what is sent, to FOSSGIS in Germany; "Đã hiểu" or
  "Tắt tính đường"); no request goes out before it was shown. "Cài đặt" (trip list menu) has the
  switch "Tính đường đi theo đường thực tế". Off = every leg straight, stored routes are not used
  (but kept), no request. Both flags are on-device preferences (`routing_enabled`,
  `routing_notice_seen`) and are not part of a backup.
* **Stored per leg** (table `leg_routes`, 02-data-model.md), keyed by the arrival step: the inputs
  (from/to position, profile), the answer (`ok`, `no_route`, `too_far`), road metres, seconds, the
  line (simplified to about 10 m) and the router id. A stored row is **usable only while its inputs
  match** the leg (both positions within 1e-4°, same profile): moving a stop, reordering or
  changing the mode makes that leg pending again; nothing else is refetched.
* **Off-road guard.** A stop far from any road (a cave, a boat landing) would snap to a distant
  road. The answer is kept as `too_far` and the leg stays **straight** when either stop is more than
  **500 m** from where the road line starts/ends, or the road is more than **5×** the straight
  distance. Otherwise the line is drawn stop → (straight connector) → road → (connector) → stop.
  Example: Tràng An (20.2546, 105.8954) snaps 713 m away; its legs stay straight (6.1 km, not
  25–29 km).
* **No route.** `no_route` (the router found none, e.g. Valhalla 442) is stored and the leg stays
  straight; it is not asked again until its inputs change. Exception, the car fallback below.
* **Car fallback ("tuyến ô tô").** A Xe máy or "Chưa chọn" leg (profile motorbike) for which the
  motorbike profile has **no route** is asked once more with the **car** profile (Valhalla `auto`;
  OSRM car when Valhalla is unavailable). Motorbikes are banned on Vietnamese expressways, so the
  car line may use roads a motorbike cannot: that is why it is marked. A car route that passes the
  off-road guard (500 m / 5×, unchanged) is stored, drawn along the road and measured like any
  road route (km, "~" time), and the leg chip / leg sheet end with **"tuyến ô tô"** ("48 km ·
  ~1 giờ 33 phút · tuyến ô tô"). The row keeps `profile` = the leg's profile (so it stays valid
  only for that mode) and records the profile that answered in `used_profile` (`car`). Both
  profiles without a route (or the car answer too far): stored (`used_profile` = `car`), straight
  "chim bay" as before, not asked again until the inputs change. Other modes (Ô tô, Xe buýt,
  Đi bộ) have no fallback. A `no_route` stored by an earlier version (`used_profile` empty =
  `motorbike`) is asked with the car profile **once** after the update, then final like any other.
* **Queue.** Pending legs of all trips are fetched one at a time, at most one request per second,
  only while routing is on, the notice was shown and the device is online. Offline or server
  failure: the leg stays straight ("chim bay") and is retried later (30 s, doubling, at most
  10 min; HTTP 429 pauses the whole queue 60 s); going online again restarts the queue. Every
  request carries the app's User-Agent (`LuTrinh/<version> (Android; vn.lutrinh.app)`).
* **Shown.** Leg chip and leg sheet: road km + "~" time, or "N km chim bay" (R12). Trip numbers:
  R10. Lines: trip map, Trip detail mini map, "Bản đồ của tôi", the share image and page.
  Everywhere a routed line is drawn the router's credit is shown: "Đường đi: Valhalla · FOSSGIS ·
  © OpenStreetMap (ODbL) · Sửa bản đồ" (opens https://www.openstreetmap.org/fixthemap); the share
  image and page print the address instead of a link.
* **Unchanged.** GPS tracks (R16) still draw separately on top; GPX export (R25) has no `rte`;
  backup (R26) includes `leg_routes`; offline basemap (R15/R33) as before.

*Tests:* `LegRoutesTest` (`notChosenIsRoutedExactlyLikeMotorbike`,
`groundModesAreRoutedTrainPlaneBoatStayStraight`, `haGiangToQuanBaKeepsTheRoadRouteAndShowsRoadKmAndTime`,
`trangAnOffRoadSampleStaysStraightInsteadOf29Km`, `aDetourOfMoreThanFiveTimesStaysStraight`,
`storedRouteAppliesOnlyWhileStopsAndProfileMatch`, `roadLineIsJoinedToTheStopsByShortConnectors`,
`storedLineIsSimplified`, `tripTotalsUseRoadKmAndTimeAndMarkPartialTotals`,
`approximateDurationsAlwaysCarryATilde`, `polyline6RoundTrips`,
`carFallbackRouteIsUsedOnlyForTheMotorbikeLegItAnswered`); `RouteSyncTest`
(`nothingIsSentBeforeTheNoticeIsShownThenEveryRoutedLegOnce`, `requestsAreAtLeastOneSecondApart`,
`switchOffMeansNoNetworkCallsAndStraightLines`, `turningOffMidwayStopsTheQueue`,
`offlineWaitsAndFetchesWhenBackOnline`, `unavailableCarLegFallsBackToOsrmMotorbikeLegIsRetriedLater`,
`noRouteIsStoredAndNotAskedAgain`, `motorbikeLegWithoutMotorbikeRouteIsStoredWithTheCarProfile`,
`carAnswerStillFacesTheOffRoadGuard`, `bothProfilesWithoutRouteKeepTheStraightLegAndAreNotAskedAgain`,
`motorbikeNoRouteStoredBeforeTheFallbackIsRetriedOnceWithCar`, `noticeAndSwitchFlagsArePersisted`); adapters:
`RouterAdaptersTest` (`valhallaCostingPerProfileNotChosenIsMotorScooter`,
`valhallaRequestCarriesOnlyTheTwoStopsAndTheCosting`, `valhallaAnswerIsConverted`,
`osrmIsCarOnlyAndConverted`, `registryHasValhallaFirstAndOsrmAsFallbackWithAUserAgent`,
`shareImageCreditWrapsAtSeparatorsInsteadOfBeingCut`); boundary: `RoutingBoundaryTest`
(`valhallaApiOnlyInItsAdapter`, `osrmApiOnlyInItsAdapter`, `routerAddressesOnlyInTheRegistry`,
`appUsesRoutingOnlyThroughTheDomainInterface`, `httpClientOnlyInAdapters`); storage:
`MigrationTest.version7UpgradesWithLegRoutesAndKeepsEverything`,
`MigrationTest.version8UpgradesWithUsedProfileAndKeepsStoredRoutes`. Screens: docs/img/road-routing/
(`13`–`15-lung-cu-car-route-*.png` = the Lũng Cú leg with "tuyến ô tô").
*Changed from the study* (docs/routing-feasibility.md): snap limit 500 m instead of 1 km, because
the Tràng An sample snaps 713 m away with a 4.1–4.8× detour, which 1 km / 5× would have accepted.

## Text rules

Titles, descriptions and notes are trimmed before validation and storage. Limits count
characters (UTF-16 code units on Android; use `String.count` on iOS, which counts grapheme
clusters — Vietnamese precomposed letters give the same count; combining-mark input may differ).
