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

* Deleting a **trip** deletes its steps, their photo rows **and the photo files**.
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
* A photo already attached to the step keeps its `id` and `createdAt` (matched by file name).
* Photos removed from the list lose their row **and file** after the save commits.
* Position 0 is the step cover; tapping a thumbnail moves it to position 0.
* At most 30 photos per step (`TOO_MANY_PHOTOS`).

*Tests:* `UseCasesTest.savingStepNumbersPhotosAndKeepsIdsOfExistingOnes`,
`RoomRepositoryTest.savingStepReplacesPhotosAndReportsRemovedFiles`.

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

## Text rules

Titles, descriptions and notes are trimmed before validation and storage. Limits count
characters (UTF-16 code units on Android; use `String.count` on iOS, which counts grapheme
clusters — Vietnamese precomposed letters give the same count; combining-mark input may differ).
