# 02 — Data model

Three entities in a strict tree: **Trip 1—n Step 1—n Photo**, plus recorded GPS tracks:
**Trip 1—n Track 1—n TrackPoint** (R16). Every child belongs to exactly one parent; deleting a
parent deletes its children (R7).

Types below are neutral: `String`, `Int`, `Double`, `Date` (calendar date, no time zone),
`LocalDateTime` (date + time to the minute, no time zone), `Instant` (UTC epoch milliseconds).

## Trip

| Field | Type | Null | Rule |
| --- | --- | --- | --- |
| id | String (UUID) | no | generated on device, never changes |
| title | String | no | trimmed; 1..60 characters |
| description | String | no (may be empty) | trimmed; 0..1000 characters |
| startDate | Date | no | required (inspired by polarsteps docs/22 §1.2: start date required) |
| endDate | Date | yes | null = open-ended trip; if set, `endDate >= startDate` |
| createdAt | Instant | no | set on create, kept on edit |
| updatedAt | Instant | no | set on every save |
| coverPhotoId | String | yes | R21 (v6): id of the photo chosen as cover ("Ảnh bìa"); null = "Tự động". Not a foreign key: a stale id (photo or its step deleted) reads as automatic |

Derived (not stored): `TripSummary` = trip + published step count + published photo count +
draft count + draft photo count (R20) + cover photo. The cover (R21) is the photo `coverPhotoId`
names when it still belongs to one of the trip's steps; otherwise the first photo (by position)
of the first step in timeline order (R2), drafts included, or none.

## Step (a stop / place on the trip)

| Field | Type | Null | Rule |
| --- | --- | --- | --- |
| id | String (UUID) | no | generated on device |
| tripId | String | no | the owning trip; must exist |
| title | String | no | trimmed; 1..80 characters (the place name typed by the user) |
| note | String | no (may be empty) | trimmed; 0..2000 characters |
| location | GeoPoint (latitude, longitude: Double) | no | required; latitude −90..90, longitude −180..180, not NaN |
| arrivedAt | LocalDateTime | no | its date must lie within the trip dates (R5) |
| createdAt | Instant | no | tie-breaker for ordering (R2) |
| updatedAt | Instant | no | |
| photos | list of Photo | — | 0..30, ordered by `position` |
| arrivalTransport | TransportMode | yes | how the traveller reached this step from the previous one (R12); null = not set; ignored on the first step |
| isDraft | Boolean | no | R20 (v6): true = saved with "Lưu nháp": shown in the trip (timeline, map) but left out of legs, stats, share and "Bản đồ của tôi"; false = published (default; every step before v6) |

### TransportMode

Stored as a short code; unknown codes (e.g. written by a newer app version) read as null.

| Code | Vietnamese label | Code | Vietnamese label |
| --- | --- | --- | --- |
| `walk` | Đi bộ | `train` | Tàu hỏa |
| `motorbike` | Xe máy | `plane` | Máy bay |
| `car` | Ô tô | `boat` | Thuyền |
| `bus` | Xe buýt/xe khách | (null) | Chưa chọn |

A **leg** is not stored: it is the pair (step *i*, step *i+1*) of a trip in R2 order, and its mode
is `arrivalTransport` of step *i+1* (R12).

## Photo

| Field | Type | Null | Rule |
| --- | --- | --- | --- |
| id | String (UUID) | no | generated on device; kept when the step is edited (R8) |
| stepId | String | no | owning step |
| fileName | String | no | unique; name of the file in the app's private `photos/` folder (e.g. `3f2c….jpg`, a video `….mp4`), never a path or URI |
| position | Int | no | 0-based display order, dense (0, 1, 2 …) within a step since v7; the user sets it by drag (R27) or by "Đặt làm ảnh bìa" (moves to 0); position 0 = step cover. Every list (timeline, step detail, viewer, share, GPX, backup) reads `ORDER BY position` |
| mediaType | `photo` \| `video` | no | (v7, R29) default `photo`; a video is a stored `.mp4` copy (≤ 60 s, ≤ 200 MB) shown with a play badge; never a trip cover (R21) |
| durationMs | Long | yes | (v7, R29) length of a video in milliseconds, read once on import; null for photos |
| createdAt | Instant | no | |
| takenAt | LocalDateTime | yes | capture time from the picked file's EXIF (`DateTimeOriginal`, docs/06; local wall clock, minute precision), read **before** import because the stored copy has no EXIF (R11). Set for photos added since DB v4 (step editor and "Thêm từ ảnh") and in sample data; null for older photos and files without a date. Used to count photos in a stop suggestion (R17) and to recognise a gallery photo already in the trip (R19); kept when the step is edited |
| sourceId | String | yes | (v5, R19) the phone gallery's id of the original (Android MediaStore `_ID`, iOS PHAsset `localIdentifier`) when the photo came from "Ảnh chụp trong lúc dừng" or "Thêm từ ảnh"; null for Photo Picker imports (the picker gives no id), older photos and sample data. Only used so the same gallery photo is not offered twice (R19) |
| sourceSize | Long | yes | (v5, R19) byte size of the original file as picked (before re-encoding, R11); recorded for every import since v5, null before. With `takenAt` it recognises a Photo Picker import of the same gallery photo (R19) |

## Track (a recorded GPS track, "Ghi hành trình", R16)

A track belongs to the whole trip (not to a step or a leg). A trip can have any number of tracks;
at most one track in the whole app is open (recording or paused) at a time.

| Field | Type | Null | Rule |
| --- | --- | --- | --- |
| id | String (UUID) | no | generated on device |
| tripId | String | no | the owning trip; must exist when recording starts |
| status | TrackStatus | no | `recording`, `paused` or `ended`; unknown codes read as `ended` |
| startedAt | Instant | no | when the user tapped "Ghi hành trình" |
| endedAt | Instant | yes | null while open; the stop time, or the time of the last save after a process death (R16) |
| activeMs | Int (ms) | no | recording time of the closed segments; pauses never count |
| resumedAt | Instant | yes | start of the open segment while `recording`; null when paused or ended |
| segment | Int | no | index of the last segment: 0 at start, +1 on every resume |
| distanceM | Double | no | sum of distances between consecutive points **of the same segment**, metres |
| pointCount | Int | no | number of stored points |
| interrupted | Bool | no | true when the track was ended because the app died while recording |
| createdAt / updatedAt | Instant | no | updatedAt = last save (start, pause, resume, stop, every stored point) |

Derived: duration = `activeMs + (now − resumedAt)` while recording, else `activeMs`.

## TrackPoint (a stored GPS fix)

| Field | Type | Null | Rule |
| --- | --- | --- | --- |
| trackId | String | no | owning track |
| segment | Int | no | segment of the track the fix belongs to; lines never join two segments |
| recordedAt | Instant | no | when the fix was taken |
| location | GeoPoint | no | WGS84; already passed the R16 filter |
| accuracyM | Float | yes | horizontal accuracy radius, metres |
| altitudeM | Double | yes | metres above the ellipsoid/sea as given by the platform |
| speedMps | Float | yes | platform speed, m/s |

Points are stored raw (filtered, never simplified); simplification happens only when drawing (R16).

## Stop suggestions ("Gợi ý điểm dừng", R17)

`Dwell` and `StopSuggestion` are **derived, never stored**: they are recomputed from the points
of the trip's ended tracks, its steps and the decisions below whenever any of them changes (so a
track that ends or is deleted updates the list at once). Code: `domain/model/StopSuggestion.kt`.

| Dwell field | Type | Rule |
| --- | --- | --- |
| trackId | String | the track the stay was found in |
| startAt / endAt | Instant (epoch ms) | time of the first / last point of the stay; identity = (trackId, startAt) |
| center | GeoPoint | mean of the stay's points |
| pointCount | Int | points in the stay (≥ 1; a pause gap gives 2) |

`StopSuggestion` = a dwell + `photoCount` (photos of the trip whose `takenAt` lies inside the
window, R17). The trip card's "N ảnh trong máy" and the editor's "Ảnh chụp trong lúc dừng" (R19)
are read live from the phone's gallery and never stored; only the photos the user saves become
`Photo` rows (with `sourceId` / `sourceSize`).

### StopDecision (stored, v4)

What the user did with a suggestion. Matched to a recomputed dwell of the same track whose window
overlaps (`startAt..endAt`), so a small change of the detection keeps old decisions working.

| Field | Type | Null | Rule |
| --- | --- | --- | --- |
| trackId | String | no | the dwell's track; deleting the track deletes its decisions |
| startAt / endAt | Instant (epoch ms) | no | the dwell's window when decided |
| kind | StopDecisionKind | no | `dismissed` ("Bỏ qua") or `added` ("Thêm điểm dừng" saved a step); unknown codes read as `dismissed` |
| stepId | String | yes | the step created (`added` only); not a foreign key: deleting that step brings the suggestion back (R17) |
| decidedAt | Instant (epoch ms) | no | |

## LegRoute (a leg's road route, "Tính đường đi", R36, stored v8, `usedProfile` v9)

The router's answer for one leg, keyed by the leg's arrival step. Used only while its inputs still
match the leg (both positions within 1e-4°, same profile) and routing is on; otherwise the leg is
straight and, while routing is on, pending.

| Field | Type | Null | Rule |
| --- | --- | --- | --- |
| toStepId | String | no | the leg's arrival step (R12); deleting the step deletes the row |
| from / to | GeoPoint | no | the stop positions that were sent |
| profile | RouteProfile | no | `motorbike` (also for "Chưa chọn"), `car`, `bus`, `walk`; unknown codes → the row is ignored |
| provider | String | no | router id (`valhalla`, `osrm`) for the credit |
| status | LegRouteStatus | no | `ok` (road line used), `no_route` (router found none), `too_far` (off-road guard: a stop > 500 m from the road or road > 5× straight); only `ok` is drawn and counted |
| distanceM / durationS | Double | no | road metres and the router's time estimate in seconds (0 for `no_route`) |
| path | List<GeoPoint> | no | the road line, simplified to about 10 m, without the connectors to the stops; stored as polyline6 text |
| fetchedAt | Instant (epoch ms) | no | |
| usedProfile | RouteProfile | yes (v9) | the profile that answered: `car` on a `motorbike` row = the car fallback ("tuyến ô tô", R36); null = same as `profile` (rows of v8); `motorbike` + `no_route` + null/`motorbike` is asked once more with `car` |

## Storage (Android: SQLite via Room, database `lutrinh.db`, version 9)

| Table | Columns | Keys / indexes |
| --- | --- | --- |
| `trips` | id TEXT PK, title, description, start_date TEXT `yyyy-MM-dd`, end_date TEXT nullable, created_at INTEGER, updated_at INTEGER, cover_photo_id TEXT nullable (v6) | — |
| `steps` | id TEXT PK, trip_id TEXT, title, note, latitude REAL, longitude REAL, arrived_at TEXT `yyyy-MM-dd'T'HH:mm`, created_at, updated_at, transport_mode TEXT nullable (v2), is_draft INTEGER NOT NULL DEFAULT 0 (v6) | FK trip_id → trips.id ON DELETE CASCADE; index (trip_id), (trip_id, arrived_at) |
| `photos` | id TEXT PK, step_id TEXT, file_name TEXT, position INTEGER, created_at INTEGER, taken_at TEXT `yyyy-MM-dd'T'HH:mm` nullable (v4), source_id TEXT nullable (v5), source_size INTEGER nullable (v5), media_type TEXT NOT NULL DEFAULT 'photo' (v7), duration_ms INTEGER nullable (v7) | FK step_id → steps.id ON DELETE CASCADE; index (step_id); UNIQUE (file_name) |
| `tracks` (v3) | id TEXT PK, trip_id TEXT, status TEXT, started_at INTEGER, ended_at INTEGER nullable, active_ms INTEGER, resumed_at INTEGER nullable, segment INTEGER, distance_m REAL, point_count INTEGER, interrupted INTEGER (0/1), created_at, updated_at | FK trip_id → trips.id ON DELETE CASCADE; index (trip_id) |
| `track_points` (v3) | id INTEGER PK AUTOINCREMENT, track_id TEXT, segment INTEGER, recorded_at INTEGER, latitude REAL, longitude REAL, accuracy_m REAL nullable, altitude_m REAL nullable, speed_mps REAL nullable | FK track_id → tracks.id ON DELETE CASCADE; index (track_id, segment, recorded_at) |
| `leg_routes` (v8, v9) | to_step_id TEXT PK, from_lat REAL, from_lng REAL, to_lat REAL, to_lng REAL, profile TEXT, provider TEXT, status TEXT, distance_m REAL, duration_s REAL, geometry TEXT (polyline6), fetched_at INTEGER, used_profile TEXT nullable (v9) | FK to_step_id → steps.id ON DELETE CASCADE |
| `stop_decisions` (v4) | track_id TEXT, start_at INTEGER, end_at INTEGER, decision TEXT, step_id TEXT nullable, decided_at INTEGER | PK (track_id, start_at); FK track_id → tracks.id ON DELETE CASCADE; index (track_id) |

Dates are stored as ISO-8601 text so that text order equals time order (used by `ORDER BY` and by
the step date range query) and the values are readable in any SQLite browser. The exported schemas
are in `app/schemas/vn.lutrinh.app.data.local.AppDatabase/<version>.json` (1.json … 9.json).
Track times are epoch-millisecond integers (instants, not local times): a recording can cross time
zones, and only durations and order matter. Each stored point is written in one transaction with
its track's `distance_m` / `point_count` / `updated_at`, so a process death never leaves them out
of step.

### Migrations (user data must survive every upgrade)

| From → to | Change | SQL |
| --- | --- | --- |
| 1 → 2 | leg transport (R12) | `ALTER TABLE steps ADD COLUMN transport_mode TEXT` (existing steps: null = not set) |
| 2 → 3 | recorded tracks (R16) | `CREATE TABLE tracks …`, `CREATE TABLE track_points …` + their indexes (exact SQL = `3.json`); existing tables untouched |
| 3 → 4 | stop suggestions (R17) | `ALTER TABLE photos ADD COLUMN taken_at TEXT` (existing photos: null = time unknown), `CREATE TABLE stop_decisions …` + `index_stop_decisions_track_id` (exact SQL = `4.json`) |
| 4 → 5 | gallery photos of a stop (R19) | `ALTER TABLE photos ADD COLUMN source_id TEXT`, `ALTER TABLE photos ADD COLUMN source_size INTEGER` (existing photos: null = origin unknown) |
| 5 → 6 | draft steps (R20), trip cover (R21) | `ALTER TABLE steps ADD COLUMN is_draft INTEGER NOT NULL DEFAULT 0` (existing steps: published), `ALTER TABLE trips ADD COLUMN cover_photo_id TEXT` (existing trips: automatic cover) |
| 6 → 7 | photo order (R27), videos (R29) | renumber `position` densely per step in the current display order (`position`, then `created_at`, then `id`) through a temp table — a correlated `UPDATE photos SET position = (SELECT COUNT(*) …)` would see rows it already changed —, then `ALTER TABLE photos ADD COLUMN media_type TEXT NOT NULL DEFAULT 'photo'`, `ALTER TABLE photos ADD COLUMN duration_ms INTEGER` (existing rows: photos). Exact SQL: `AppDatabase.MIGRATION_6_7` |
| 7 → 8 | legs along the road (R36) | `CREATE TABLE leg_routes …` (exact SQL = `8.json`, no index: the key is the primary key); existing tables untouched, every leg starts without a route (straight until fetched). Test: `MigrationTest.version7UpgradesWithLegRoutesAndKeepsEverything` |
| 8 → 9 | car-route fallback (R36) | `ALTER TABLE leg_routes ADD COLUMN used_profile TEXT` (existing rows: NULL = answered with their own profile; a stored motorbike `no_route` is then asked once with `car`). Test: `MigrationTest.version8UpgradesWithUsedProfileAndKeepsStoredRoutes` |

* There is **no destructive fallback**: a missing migration crashes on open instead of wiping
  the user's trips. Each version bump adds `MIGRATION_x_y` to `AppDatabase.MIGRATIONS`, bumps
  `AppDatabase.VERSION`, commits the exported schema JSON and extends `MigrationTest`.
* `MigrationTest.version1UpgradesToCurrentWithoutDataLoss` builds a real version-1 file from the
  SQL in `1.json`, fills a trip, steps and a photo, opens it with the current Room database and
  checks that every row survives; `everySchemaVersionHasAnExportedFileAndAMigration` checks the
  chain 1 → current. `version2UpgradesWithTrackTablesAndKeepsTripsAndSteps` does the same from
  `2.json`, then writes a track with points, deletes it (points go too) and checks that deleting
  the trip cascades to its tracks and points. `version3UpgradesWithPhotoTimesAndStopDecisionsAndKeepsTracks`
  builds a v3 file from `3.json` with a trip, step, photo, track and points, upgrades it, checks
  every row (old photo: `takenAt` null), stores a decision and checks that deleting the track
  removes it. `version4UpgradesWithPhotoSourcesAndKeepsEverything` builds a v4 file from `4.json`
(trip, step, photo with `taken_at`, track, point, decision), upgrades it, checks every row (old
photo: `sourceId` / `sourceSize` null, `takenAt` kept) and saves a photo with a source.
`version5UpgradesWithDraftsAndCoverAndKeepsEverything` builds a v5 file from `5.json`, upgrades it,
checks that every step is published, the cover is automatic (`a.jpg`, counts 2 steps / 1 photo,
0 drafts), then sets a draft and a chosen cover.
`version6UpgradesKeepingPhotoOrderAndAddsMediaType` builds a v6 file whose photo positions have
ties and gaps, upgrades it, checks that the display order is unchanged and numbered 0…n-1, every
row is a `photo` with null duration, the chosen cover is kept, and a video row can be written. Also checked by hand: the main@b07fcf6 build with sample data, then the v2
  build installed over it (`img/27-upgrade-before-v1.png`, `img/28-upgrade-after-v2.png`); and
  main@d96f285 (v3) with sample data + a recorded track, then the v4 build installed over it:
  2 trips, 14 steps, 16 photos, 1 track, 6 points before and after (`pragma user_version` 3 → 4);
and main@a664e3e (v4) with sample data, then the v5 build installed over it with `install -r`:
2 trips, 14 steps, 16 photos, 1 track before and after, all 16 `taken_at` kept, `source_id`
null (`pragma user_version` 4 → 5); and main@520f4c9 (v5) with the captain's data on emulator-5580,
then the v6 build installed over it with `install -r`: counts trips|steps|photos|tracks|points|decisions
`2|14|16|1|359|1` before and after, every step `is_draft = 0`, every `cover_photo_id` null; and main@f6c7cfc (v6) with the captain's
data on emulator-5580, then the v7 build with `install -r`: counts `2|14|16|1|359|1` before and
after, `pragma user_version` 6 → 7, every row `media_type = 'photo'`, and the dump
`select step_id, position, created_at, id, file_name from photos order by step_id, position, created_at, id`
identical before and after (the captain's positions were already dense).

Photo files live outside the database in `<app private files>/photos/<uuid>.<ext>` (`jpg` for
re-encoded photos, `png` when transparent, otherwise the picked format — R11; videos are byte
copies `<uuid>.mp4` or the picked extension, R29); the database
only stores the file name, so the folder can move (backup/restore, iOS container) without a
migration; a backup (R26) stores the files under `photos/` with the same names.

## GPX file (R25)

Exchange format for one trip with other map apps (GPX 1.1, `http://www.topografix.com/GPX/1/1`,
UTF-8, MIME `application/gpx+xml`, extension `.gpx`). Not a backup: photos, drafts, transport
modes, covers and stop decisions are not in it.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Lữ Trình" xmlns="http://www.topografix.com/GPX/1/1" …>
  <metadata><name>trip title</name><desc>trip description</desc><time>export time, UTC</time></metadata>
  <wpt lat="22.8233" lon="104.9836">            <!-- one per published step, timeline order -->
    <time>2025-10-20T01:00:00Z</time>            <!-- arrival: local time → UTC -->
    <name>TP Hà Giang</name><desc>step note</desc>
  </wpt>
  <trk><name>trip title</name>                   <!-- one per recorded track -->
    <trkseg>                                     <!-- one per track segment (R16) -->
      <trkpt lat="22.8233" lon="104.9836"><ele>110.0</ele><time>2025-10-20T01:10:00Z</time></trkpt>
    </trkseg>
  </trk>
</gpx>
```

| GPX | Lữ Trình (export → import) |
| --- | --- |
| `metadata/name`, `metadata/desc` | trip `title`, `description` (import falls back to the first `trk`/`rte` name, then the file name) |
| `wpt@lat/@lon`, `time`, `name`, `desc` (`cmt` on import) | published step `latitude/longitude`, `arrived_at` (minute precision on import), `title`, `note` |
| `trk` (one `trkseg` per segment), `rte` (one segment, import only) | `tracks` row (`ended`), `track_points` with `segment` = index of the non-empty segment |
| `trkpt@lat/@lon`, `ele`, `time` | `track_points.latitude/longitude`, `altitude_m`, `recorded_at` |

Import reads GPX 1.0 too (elements by local name) and ignores extensions; rules in R25.

## Backup file (R26)

One zip (`application/zip`, default name `LuTrinh-saoluu-<yyyy-MM-dd>.zip`) with all user data:

```
manifest.json            what is in the file (below)
lutrinh.db               SQLite copy of the app database: Room schema `schemaVersion`, user tables
                         trips, steps, photos, tracks, track_points, stop_decisions, leg_routes (v8, v9; journal mode
                         TRUNCATE, no -wal/-shm files)
photos/<uuid>.<ext>      every photo and video file a `photos` row refers to (bytes as stored, no re-encode)
```

```json
{
  "format": "lutrinh-backup", "formatVersion": 1,
  "appId": "vn.lutrinh.app", "appVersion": "0.1.0",
  "schemaVersion": 9, "createdAt": "2026-11-09T10:58:31Z",
  "counts": { "trips": 2, "steps": 15, "photos": 19, "tracks": 1, "trackPoints": 359, "stopDecisions": 1, "videos": 2 },
  "photoFiles": 19, "missingPhotoFiles": 0
}
```

* `formatVersion` changes only when the zip layout changes; `schemaVersion` is the database
  version of this document (`pragma user_version`), so an iOS app on the same schema can restore
  an Android backup and vice versa (same tables and column names; dates are text as above).
* The copy is made by creating an empty database with the current schema and copying every
  user table row by row inside one read transaction of the live database (consistent, and the
  live WAL is never copied as a file). Room's own `room_master_table` comes with the new file.
* Restore checks and replaces as in R26; a copy with an older `schemaVersion` is migrated with
  the same migrations as an app update (MIGRATION_x_y above), e.g. a schema-6 backup made before
  videos existed restores into v7 with its photo order kept (`BackupManagerTest.version6BackupRestoresIntoVersion7`).
  `counts.photos` / `photoFiles` count photos and videos together (videos are rows of `photos`);
  `counts.videos` says how many of those rows are videos (optional, 0 when absent: it is informational
  and left out of the check against the database, so older files still match). The screens show
  "photos − videos ảnh · videos video".
  `leg_routes` rows (R36) travel with the backup but are not counted: they are a cache of the
  router's answers that the app can fetch again; a backup older than v8 restores without routes.

## Validation errors (returned by the domain, shown by the UI)

| Error | When | Vietnamese message |
| --- | --- | --- |
| Trip TITLE_BLANK | title empty after trim | "Hãy đặt tên cho chuyến đi" |
| Trip TITLE_TOO_LONG | > 60 | "Tên tối đa 60 ký tự" |
| Trip DESCRIPTION_TOO_LONG | > 1000 | "Mô tả tối đa 1000 ký tự" |
| Trip END_BEFORE_START | endDate < startDate | "Ngày về phải sau hoặc bằng ngày đi" |
| Trip STEPS_OUTSIDE_DATES | edit would leave a step outside the new dates (R4) | "Có điểm dừng nằm ngoài khoảng ngày mới (…). Hãy sửa các điểm dừng đó trước." |
| Step TITLE_BLANK / TITLE_TOO_LONG / NOTE_TOO_LONG | as above, limits 80 / 2000 | "Hãy nhập tên địa điểm", … |
| Step LOCATION_MISSING / LOCATION_INVALID | no point / out of range | "Hãy chọn vị trí trên bản đồ", "Tọa độ không hợp lệ" |
| Step BEFORE_TRIP_START / AFTER_TRIP_END | R5 | "Ngày đến phải từ … trở đi", "Ngày đến phải trước hoặc bằng …" |
| Step TOO_MANY_PHOTOS | > 30 | "Tối đa 30 ảnh mỗi điểm dừng" |
| Step TRIP_NOT_FOUND | the trip was deleted meanwhile | "Chuyến đi không còn tồn tại" |

Several errors can be returned at once; the form shows each next to its field.

## Not modelled yet (on purpose)

Visibility/privacy, travel buddies, drafts, likes/comments, planned steps, sync state and
tombstones, captions; links between a track and steps (no automatic steps from a track). GPX
export/import (R25) and the backup file (R26) are formats above, not tables. Photo EXIF date/GPS is read once when steps are made from photos (R13) and
never stored (the stored copies have no GPS, R11). See 03-screens.md "Intentional changes".
