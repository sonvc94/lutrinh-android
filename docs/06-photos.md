# 06 — Photos

## Picking

* The step form's "Thêm ảnh" opens the **system photo picker** (Android Photo Picker via
  `PickMultipleVisualMedia`, images only, at most 30 per pick).
* **No storage permission** is requested or declared. The picker runs in the system process and
  grants the app temporary read access to the chosen items only. (Merged manifest permissions:
  `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — all for map tiles; the location
  permissions MapLibre would add are removed.)
* On devices without the new picker, androidx falls back to the documents UI
  (`ACTION_OPEN_DOCUMENT`) automatically — still no permission.
* Selection order is kept: the first picked photo becomes the step cover if the step had none.

*Inspired by* polarsteps docs/22 §3.1 (androidx Photo Picker as the "Library" button). *Changed:*
no in-app MediaStore gallery, no "allow all photos" rationale sheet, no date-filtered
suggestions, no camera capture, no videos — all of these need broad media permissions or more
UI; they can come later.

## Persisting: copy into app storage

The URI from the picker is temporary (valid until the app process ends). To survive restarts
each picked image is **copied right away** into the app's private folder:

```
<filesDir>/photos/<random-uuid>.<ext>      ext from MIME: jpg (default) | png | webp | heic | gif
```

* Copy is a byte-for-byte stream copy on a background thread; the original file is untouched,
  so EXIF (including GPS and orientation) stays in the copy. Nothing is resized or recompressed.
* The database stores only the file name (02-data-model.md), never the content URI.
* A failed copy (unreadable URI, disk full) is skipped and counted; the form shows
  "Không đọc được N ảnh". A partial file is deleted.
* Why copy instead of `takePersistableUriPermission`: the Photo Picker's URIs cannot be persisted
  on all Android versions; and a copy keeps working if the user deletes the photo from the
  gallery, is included in the app's backup, and maps directly to iOS (copy the picked data into
  the app container).
* Cost: storage use doubles for picked photos. Future work: downscale to e.g. 2048 px long side
  on import, and/or keep a thumbnail.

## Lifecycle of a photo file

| Event | File effect |
| --- | --- |
| Picked in the form | copied → `photos/x.jpg` (not yet in the database) |
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

EXIF reading (date/GPS → suggest step date/location), videos, camera capture, upload to a server
(see 01-architecture.md), image downscaling, photo captions, sharing.
