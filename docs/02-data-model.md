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

Derived (not stored): `TripSummary` = trip + step count + photo count + cover photo. The cover is
the first photo (by position) of the first step in timeline order (R2), or none.

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
| fileName | String | no | unique; name of the file in the app's private `photos/` folder (e.g. `3f2c….jpg`), never a path or URI |
| position | Int | no | 0-based display order; position 0 = step cover |
| createdAt | Instant | no | |

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

## Storage (Android: SQLite via Room, database `lutrinh.db`, version 3)

| Table | Columns | Keys / indexes |
| --- | --- | --- |
| `trips` | id TEXT PK, title, description, start_date TEXT `yyyy-MM-dd`, end_date TEXT nullable, created_at INTEGER, updated_at INTEGER | — |
| `steps` | id TEXT PK, trip_id TEXT, title, note, latitude REAL, longitude REAL, arrived_at TEXT `yyyy-MM-dd'T'HH:mm`, created_at, updated_at, transport_mode TEXT nullable (v2) | FK trip_id → trips.id ON DELETE CASCADE; index (trip_id), (trip_id, arrived_at) |
| `photos` | id TEXT PK, step_id TEXT, file_name TEXT, position INTEGER, created_at INTEGER | FK step_id → steps.id ON DELETE CASCADE; index (step_id); UNIQUE (file_name) |
| `tracks` (v3) | id TEXT PK, trip_id TEXT, status TEXT, started_at INTEGER, ended_at INTEGER nullable, active_ms INTEGER, resumed_at INTEGER nullable, segment INTEGER, distance_m REAL, point_count INTEGER, interrupted INTEGER (0/1), created_at, updated_at | FK trip_id → trips.id ON DELETE CASCADE; index (trip_id) |
| `track_points` (v3) | id INTEGER PK AUTOINCREMENT, track_id TEXT, segment INTEGER, recorded_at INTEGER, latitude REAL, longitude REAL, accuracy_m REAL nullable, altitude_m REAL nullable, speed_mps REAL nullable | FK track_id → tracks.id ON DELETE CASCADE; index (track_id, segment, recorded_at) |

Dates are stored as ISO-8601 text so that text order equals time order (used by `ORDER BY` and by
the step date range query) and the values are readable in any SQLite browser. The exported schemas
are in `app/schemas/vn.lutrinh.app.data.local.AppDatabase/<version>.json` (1.json, 2.json, 3.json).
Track times are epoch-millisecond integers (instants, not local times): a recording can cross time
zones, and only durations and order matter. Each stored point is written in one transaction with
its track's `distance_m` / `point_count` / `updated_at`, so a process death never leaves them out
of step.

### Migrations (user data must survive every upgrade)

| From → to | Change | SQL |
| --- | --- | --- |
| 1 → 2 | leg transport (R12) | `ALTER TABLE steps ADD COLUMN transport_mode TEXT` (existing steps: null = not set) |
| 2 → 3 | recorded tracks (R16) | `CREATE TABLE tracks …`, `CREATE TABLE track_points …` + their indexes (exact SQL = `3.json`); existing tables untouched |

* There is **no destructive fallback**: a missing migration crashes on open instead of wiping
  the user's trips. Each version bump adds `MIGRATION_x_y` to `AppDatabase.MIGRATIONS`, bumps
  `AppDatabase.VERSION`, commits the exported schema JSON and extends `MigrationTest`.
* `MigrationTest.version1UpgradesToCurrentWithoutDataLoss` builds a real version-1 file from the
  SQL in `1.json`, fills a trip, steps and a photo, opens it with the current Room database and
  checks that every row survives; `everySchemaVersionHasAnExportedFileAndAMigration` checks the
  chain 1 → current. `version2UpgradesWithTrackTablesAndKeepsTripsAndSteps` does the same from
  `2.json`, then writes a track with points, deletes it (points go too) and checks that deleting
  the trip cascades to its tracks and points. Also checked by hand: the main@b07fcf6 build with sample data, then the v2
  build installed over it (`img/27-upgrade-before-v1.png`, `img/28-upgrade-after-v2.png`).

Photo files live outside the database in `<app private files>/photos/<uuid>.<ext>` (`jpg` for
re-encoded photos, `png` when transparent, otherwise the picked format — R11); the database
only stores the file name, so the folder can move (backup/restore, iOS container) without a
migration.

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
tombstones, videos; links between a track and steps (no automatic steps from a track), GPX
import/export. Photo EXIF date/GPS is read once when steps are made from photos (R13) and
never stored (the stored copies have no GPS, R11). See 03-screens.md "Intentional changes".
