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

## Text rules

Titles, descriptions and notes are trimmed before validation and storage. Limits count
characters (UTF-16 code units on Android; use `String.count` on iOS, which counts grapheme
clusters — Vietnamese precomposed letters give the same count; combining-mark input may differ).
