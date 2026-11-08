# 06 — Photos

## Picking

* The step form's "Thêm ảnh" opens the **system photo picker** (Android Photo Picker via
  `PickMultipleVisualMedia`, images only, at most 30 per pick).
* **No storage permission** is requested or declared. The picker runs in the system process and
  grants the app temporary read access to the chosen items only. (Merged manifest permissions:
  `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — all for map tiles — plus
  `ACCESS_MEDIA_LOCATION`, asked only by "Thêm từ ảnh" below; the location permissions MapLibre
  would add are removed.)
* On devices without the new picker, androidx falls back to the documents UI
  (`ACTION_OPEN_DOCUMENT`) automatically — still no permission.
* Selection order is kept: the first picked photo becomes the step cover if the step had none.

### "Thêm từ ảnh" (steps from photos, R13): file picker + media location

Making steps from photos needs each photo's **GPS**, which the Photo Picker hides:

* Verified on the emulator (Android 16, API 36) with JPEGs carrying GPS: a Photo Picker URI
  (`content://media/picker/…`) returns the EXIF date but **no GPS** — even with
  `ACCESS_MEDIA_LOCATION` granted — and `MediaStore.setRequireOriginal` on it throws
  `UnsupportedOperationException: Require Original is not supported for Picker URI`.
* The system **file picker** (`ACTION_OPEN_DOCUMENT`, `OpenMultipleDocuments` with `image/*`,
  multi-select) returns `content://com.android.providers.media.documents/…` URIs; with
  `ACCESS_MEDIA_LOCATION` granted, a plain `openInputStream` keeps the GPS tags.

So "Thêm từ ảnh" (and only it — the step form keeps the Photo Picker):
1. On Android 10+ asks for `ACCESS_MEDIA_LOCATION` (declared in the manifest) if not granted.
   On Android 14+ the system shows its photos dialog ("Allow limited access / Allow all /
   Don't allow"); "limited" first asks the user to select photos, then grants the permission.
   Android 8–9 have no such permission (GPS is readable).
2. Opens the file picker whatever the answer. **Denied** → GPS reads as none, so all photos end
   up in the "Ảnh không có vị trí" group that the user places on the map or skips (dates still
   work).
3. Reads date + GPS of every photo (`data/PhotoMetaReader`, `ExifInterface` on the stream)
   **before** import — the stored copy has its GPS stripped (R11). The values are only used for
   grouping (R13) and never stored.
4. On confirm, imports the kept photos exactly like the step form (below); unticked groups are
   never copied. The URIs are only used during this screen (no persisted permission).

Test photos: JPEGs with known `DateTimeOriginal` and GPS were generated, pushed to
`/sdcard/Pictures/…` and picked through this flow (5 photos, 2 days/places + 1 without GPS →
3 suggestions, `img/29-from-photos-review.png`, `img/30-from-photos-result.png`); the stored
copies pulled with `run-as` had the date and **no GPS**.

*Inspired by* polarsteps docs/22 §3.1 (androidx Photo Picker as the "Library" button). *Changed:*
no in-app MediaStore gallery, no "allow all photos" rationale sheet, no automatic
suggestions from the whole gallery (only from photos the user picks), no camera capture, no videos — all of these need broad media permissions or more
UI; they can come later.

## Persisting: import into app storage

The URI from the picker is temporary (valid until the app process ends). To survive restarts
each picked image is **imported right away** into the app's private folder:

```
<filesDir>/photos/<random-uuid>.<ext>
```

* The database stores only the file name (02-data-model.md), never the content URI.
* A failed import (unreadable URI, undecodable file, disk full, out of memory) is skipped and
  counted; the form shows "Không đọc được N ảnh". A partial file is deleted.
* Why copy instead of `takePersistableUriPermission`: the Photo Picker's URIs cannot be persisted
  on all Android versions; and a copy keeps working if the user deletes the photo from the
  gallery, is included in the app's backup, and maps directly to iOS (copy the picked data into
  the app container).

### Import processing (rule R11 in 04-business-rules.md)

Runs on a background thread (`data/PhotoStorage.import`); the sizing/orientation maths is pure
code in `data/photo/PhotoImportPlan.kt` and unit-tested (`PhotoImportPlanTest`).

1. Copy the picked bytes to a temp file in the cache folder (read several times below; deleted at
   the end whatever happens).
2. **GIF** → kept byte for byte (may be animated, carries no EXIF). Stored as `.gif`.
3. Read the pixel size without decoding. Not decodable by the platform (e.g. HEIC before
   Android 9) → kept byte for byte with its original extension (location EXIF stays — rare case,
   accepted).
4. Read the EXIF `Orientation` tag (1–8; missing/unknown = 1).
5. **Keep as is** when all of: long edge ≤ **2560 px**, orientation 1 (upright), and the format is
   JPEG/PNG/WebP. The original bytes are copied, then every **GPS EXIF tag is removed in place**
   (`ExifInterface.saveAttributes`). Other EXIF (date, camera) stays. If the metadata cannot be
   rewritten, fall through to step 6.
6. **Re-encode** otherwise (too large, rotated/mirrored, HEIC/HEIF or other format):
   * decode with the largest power-of-two subsample that still gives ≥ 2560 px on the long edge
     (`sampleSize`), to bound memory;
   * scale so the long edge is exactly 2560 px (aspect kept, rounded; never upscaled);
   * apply the orientation: rotate clockwise 0/90/180/270, then mirror horizontally for EXIF
     2/4/5/7 (table below); width/height swap for 90/270;
   * write **JPEG quality 85** (`.jpg`), or **PNG** (`.png`) when the image has transparency.
     The new file has **no EXIF at all** (orientation applied in the pixels, location gone).

| EXIF | meaning | rotate (cw) | then mirror |
| --- | --- | --- | --- |
| 1 | normal | 0 | no |
| 2 | flip horizontal | 0 | yes |
| 3 | rotate 180 | 180 | no |
| 4 | flip vertical | 180 | yes |
| 5 | transpose | 90 | yes |
| 6 | rotate 90 cw | 90 | no |
| 7 | transverse | 270 | yes |
| 8 | rotate 270 cw | 270 | no |

Result: every stored file is upright for any viewer, at most 2560 px, and carries no location.
Checked on the emulator with a 4800×3600 JPEG (EXIF 6 + GPS, 374 KB): stored as a 1920×2560 JPEG,
79 KB, no EXIF, shown upright (`img/22-photo-exif-rotated-upright.png`); a 1200×900 upright JPEG
with GPS was kept at 1200×900 with its EXIF minus the 4 GPS tags.

*Inspired by* polarsteps docs/16 U5 (EXIF orientation → mirror/rotate matrix, re-encoding drops
EXIF). *Changed:* own size limit (2560 px) and quality (85), one stored copy instead of
thumbnails + original, small upright images are not re-encoded, GPS is removed explicitly.

## Lifecycle of a photo file

| Event | File effect |
| --- | --- |
| Picked in the form | imported → `photos/x.jpg` (not yet in the database) |
| Removed (×) in the form before saving | stays until the form ends, then deleted (R9) |
| Form saved | rows written; copies picked in this session but no longer in the list are deleted |
| Form left without saving (Back → "Bỏ") or the process ends the screen | all copies of this session deleted (R9) |
| Photo removed from an already-saved step and saved | row removed, file deleted (R8) |
| Step deleted / trip deleted | rows removed by cascade, then files deleted (R7) |

Deletion only accepts plain file names inside `photos/` (names containing `/` or `..` are
ignored) so a corrupted row can never delete other files.

## Display

* Loaded from local files with Coil 3 (core only — the network module is not included, so Coil
  can never fetch a URL). Images are decoded at the size of the view.
* Thumbnails: step form (row with × and "Ảnh bìa" badge), trip timeline (row per step), step
  detail grid, map carousel cards, trip card cover.
* Trip cover = first photo of the first step (R2 order) — computed in the summary query, not
  stored.
* Full-screen viewer: horizontal pager over the step's photos in position order, counter
  "i / n", pinch zoom 1×–5× with pan; while zoomed, horizontal drags pan the image instead of
  changing page. A missing file shows "Không tìm thấy tệp ảnh" instead of crashing.

## Sample data (debug builds only)

"Tạo dữ liệu mẫu" generates simple landscape images (sky, sun, layered green hills) in code and
writes them through the same file store, so sample trips behave exactly like user data (delete
removes their files). They are plain drawings, not real photos.

The screenshots in `img/` used four similar generated JPEGs (`LT_TEST_*.jpg`, with a "TEST …"
caption) pushed into the emulator gallery, so that the real Photo Picker flow could be shown.

## Not done (later)

Suggestions from the whole gallery without picking (needs broad media access), thumbnails (Coil decodes at view size from the 2560 px copy), videos,
camera capture, upload to a server (see 01-architecture.md), photo captions, sharing, removing
location from XMP blocks (only EXIF GPS tags are removed).
