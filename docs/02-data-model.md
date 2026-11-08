# 02 — Data model

Three entities in a strict tree: **Trip 1—n Step 1—n Photo**. Every child belongs to exactly one
parent; deleting a parent deletes its children (R7).

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

## Photo

| Field | Type | Null | Rule |
| --- | --- | --- | --- |
| id | String (UUID) | no | generated on device; kept when the step is edited (R8) |
| stepId | String | no | owning step |
| fileName | String | no | unique; name of the file in the app's private `photos/` folder (e.g. `3f2c….jpg`), never a path or URI |
| position | Int | no | 0-based display order; position 0 = step cover |
| createdAt | Instant | no | |

## Storage (Android: SQLite via Room, database `lutrinh.db`, version 1)

| Table | Columns | Keys / indexes |
| --- | --- | --- |
| `trips` | id TEXT PK, title, description, start_date TEXT `yyyy-MM-dd`, end_date TEXT nullable, created_at INTEGER, updated_at INTEGER | — |
| `steps` | id TEXT PK, trip_id TEXT, title, note, latitude REAL, longitude REAL, arrived_at TEXT `yyyy-MM-dd'T'HH:mm`, created_at, updated_at | FK trip_id → trips.id ON DELETE CASCADE; index (trip_id), (trip_id, arrived_at) |
| `photos` | id TEXT PK, step_id TEXT, file_name TEXT, position INTEGER, created_at INTEGER | FK step_id → steps.id ON DELETE CASCADE; index (step_id); UNIQUE (file_name) |

Dates are stored as ISO-8601 text so that text order equals time order (used by `ORDER BY` and by
the step date range query) and the values are readable in any SQLite browser. The exported schema
is in `app/schemas/vn.lutrinh.app.data.local.AppDatabase/1.json`.

Photo files live outside the database in `<app private files>/photos/<uuid>.<ext>`; the database
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

Visibility/privacy, travel buddies, transport mode, drafts, likes/comments, planned steps, sync
state and tombstones, photo EXIF/GPS, videos. See 03-screens.md "Intentional changes".
