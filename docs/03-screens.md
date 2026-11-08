# 03 — Screens and navigation

Nine destinations, one activity/window. Every screen that reads data shows one of four states:

| State | Meaning | UI |
| --- | --- | --- |
| Loading | first value of the stream not yet arrived | centred progress + "Đang tải…" |
| Missing | the trip/step id no longer exists (e.g. deleted elsewhere) | message "Không tìm thấy dữ liệu này. Có thể nó đã bị xóa." + back |
| Failed | the read threw | "Không đọc được dữ liệu: …" |
| Ready | content (may itself be empty: see each screen) | |

Forms keep their own transient state (field values, dirty flag, errors). Leaving a dirty form with
Back asks "Bỏ các thay đổi?" (Bỏ / Tiếp tục sửa).

## Navigation graph

```
Trip list ──FAB "Chuyến đi mới"──────────────▶ Trip form (create) ──save──▶ Trip detail (replaces form)
   │  tap card                                                             ▲
   ├──────────────────────────────────────────▶ Trip detail ◀─────────────┘
   │  card ⋮ / long-press → Sửa                  │  ⋮ Sửa ─▶ Trip form (edit) ─save/back─▶ back
   ├──────────────────────────────▶ Trip form    │  ⋮ Xóa ─▶ confirm ─▶ back to list
   │  card ⋮ → Xóa → confirm                     │  map icon / header expand ─▶ Trip map
   └─ (debug) ⋮ "Tạo dữ liệu mẫu"                │  FAB "Thêm điểm dừng" ─▶ Step form (create)
                                                 │  tap step ─▶ Step detail
Step form ── "Chọn trên bản đồ"/"Đổi vị trí" ──▶ Location picker ── "Dùng vị trí này" ─▶ back with point
Step form ── "Thêm ảnh" ──▶ system Photo Picker ─▶ back with URIs
Step detail ── pencil ─▶ Step form (edit) │ bin ─▶ confirm ─▶ back │ tap photo ─▶ Photo viewer
Trip map ── tap card ─▶ Step detail
```

Route strings (Android, kept for deep links later): `trips`, `tripEdit?tripId=`, `trip/{tripId}`,
`trip/{tripId}/map`, `trip/{tripId}/stepEdit?stepId=`, `trip/{tripId}/step/{stepId}`,
`trip/{tripId}/pick?stepId=&lat=&lng=`, `trip/{tripId}/step/{stepId}/photos?index=`.
The picker returns its result to the step form through the previous screen's saved state
(`picked_location` = `"lat,lng"`); on iOS use a binding or a callback closure.

## 1. Trip list — "Chuyến đi của tôi"

* **Purpose:** all trips, newest first (R1); entry point.
* **Content:** large title; one card per trip with cover photo (or a gradient placeholder), title,
  date range (`6 thg 11 – 9 thg 11, 2026`, or `từ …` when open-ended), "N điểm dừng · M ảnh".
* **Actions:** tap → Trip detail; ⋮ or long-press → Sửa / Xóa; FAB "Chuyến đi mới".
  Delete asks "Xóa chuyến đi này?" with the counts of steps and photos that will be removed.
* **Empty:** suitcase icon, "Chưa có chuyến đi nào" + invitation text; in debug builds a
  "Tạo dữ liệu mẫu" button (also in the top ⋮ menu) seeds a Hà Giang loop and a Ninh Bình trip.
* **Screens:** `img/01-trip-list-empty.png`, `img/04-trip-list.png`, `img/15-trip-list-sample.png`,
  `img/17-trip-delete-confirm.png`, `img/18-trip-list-after-delete.png`.
* **Inspired by:** polarsteps docs/22 §1.1 (Me tab trip cards, long-press sheet for trip settings).
  **Changed:** no profile/"Me" page, no trip-type sheet (future / now / past) before the form, no
  share/privacy entries; delete is reachable from the list, not only from settings.

## 2. Trip form — "Tạo chuyến đi" / "Sửa chuyến đi"

* **Fields:** Tên chuyến đi (counter n/60, required), Mô tả ngắn (optional), Ngày đi (default
  today), Ngày về (optional, "Chưa xác định"; "Bỏ ngày về" clears it). Dates use the system date
  picker.
* **Validation:** on save, via the domain (02-data-model.md); errors appear under the fields. Edit
  also blocks date changes that would orphan steps (R4).
* **Edit only:** "Xóa chuyến đi" button → same confirm as the list.
* **After create:** opens the new trip's detail (form removed from the back stack). After edit: back.
* **Screens:** `img/02-trip-create.png`, `img/03-trip-edit.png`, `img/19-trip-validation.png`.
* **Inspired by:** docs/22 §1.2 (name required, start required, end optional, delete at the bottom
  of edit with a strong warning). **Changed:** no cover-photo picker (cover comes from the first
  step photo), no travel buddies / privacy / tracker / location-privacy fields, no "no overlap with
  other trips" rule (overlapping trips are allowed), plain date pickers instead of a full-screen
  calendar, Vietnamese copy written for this app.

## 3. Trip detail

* **Purpose:** one trip as a timeline.
* **Content:** header map (220 dp) with numbered pins and the route (R3) and an expand button;
  title, date range, description; four stat tiles: điểm dừng, ảnh, km (đường chim bay = straight
  line, R10), ngày; timeline grouped by day ("Ngày 2 · Thứ Bảy, 7/11"), each step with its number,
  time, note preview and up to a row of photo thumbnails.
* **Actions:** map icon → Trip map; ⋮ → Sửa / Xóa; tap step → Step detail; FAB "Thêm điểm dừng".
* **Empty:** "Chưa có điểm dừng" + text + button "Thêm điểm dừng"; the map shows Vietnam overview.
* **Screens:** `img/03` (empty state is the same layout), `img/08-trip-detail.png`,
  `img/14-photos-after-restart.png`.
* **Inspired by:** docs/22 §1.1 (trip opens on its map with step cards, drawer with stats).
  **Changed:** a scrolling timeline with the map as a header instead of a map-first screen with a
  drawer; day numbers are computed from the trip start date.

## 4. Trip map — full screen

* **Content:** map with all pins + route, fitted to all steps (05-map.md camera rules); a
  horizontal carousel of step cards at the bottom (thumbnail, "1. Title", date · time).
* **Behaviour:** tapping a pin selects it (gold) and scrolls the carousel to its card; tapping a
  card opens Step detail. Empty trip: message "Chuyến đi chưa có điểm dừng nào để hiển thị."
* **Screens:** `img/09-trip-map-route.png`, `img/16-sample-hagiang-map.png`.
* **Inspired by:** docs/22 §1.1/§2 (map with step-card carousel, fit-to-steps camera).
  **Changed:** straight route segments only (no transport mode, no snap-to-roads, no flight arcs),
  own pin design (numbered circles), no map-style switcher.

## 5. Step form — "Điểm dừng mới" / "Sửa điểm dừng"

* **Fields:** Tên địa điểm (required, n/80), Vị trí card (mini map + coordinates; "Chọn trên bản
  đồ" / "Đổi vị trí"), Ngày đến + Giờ đến (system pickers), Ghi chú (multi-line), Ảnh (n) with
  "Thêm ảnh", thumbnails with a remove (×) button, first photo marked "Ảnh bìa"; tapping a photo
  makes it the cover.
* **Defaults (new step):** R6 — date/time and the point where the picker opens.
* **Save:** top-bar "Lưu" and bottom button; errors per field. Edit mode also has delete.
* **Screens:** `img/06-step-create.png`, `img/11-step-edit.png`.
* **Inspired by:** docs/22 §1.3 (New/Edit Step: map header with edit pencil, name, arrival date +
  time, media box, note). **Changed:** no "Publish / Save as draft" sheet (a step is saved
  directly), no location search (offline: map + quick places), no "suggested photos from the step
  date", no top spots/activities, default time 09:00 instead of 10:00, name typed by the user
  instead of reverse-geocoded.

## 6. Location picker — "Chọn vị trí"

* **Content:** full map with a fixed pin in the centre; chips "Đi nhanh tới" (Hà Giang, Đồng Văn,
  Mã Pí Lèng, Ninh Bình, Tràng An, Hà Nội, Sa Pa, Hội An, …); the other steps of the trip as small
  context pins; bottom card "Kéo bản đồ để đặt ghim vào đúng chỗ", live coordinates, button
  "Dùng vị trí này".
* **Opens at:** the step's current point; else the last step of the trip; else Vietnam overview.
* **Screen:** `img/05-location-picker.png`.
* **Inspired by:** docs/22 §1.3 ("Search step location" map + pin; "Change step location" shows
  neighbours). **Changed:** drag-the-map-under-a-fixed-pin instead of tap-to-move, quick-place
  chips instead of online search (no geocoding API in the local-only phase).

## 7. Step detail

* **Content:** cover photo (only when the step has photos), title, date · time, coordinates,
  note ("Chưa có ghi chú." when empty), mini map of the trip with this step highlighted, photo
  grid "Ảnh (n)".
* **Actions:** pencil → Step form (edit); bin → "Xóa điểm dừng này?" (shows the photo count);
  tap photo → viewer at that index.
* **Screens:** `img/10-step-detail.png`, `img/13-step-delete-confirm.png`.
* **Inspired by:** docs/22 §1.3 (step story with ⋮ Edit / Delete, delete confirm). **Changed:** a
  scrolling detail page instead of a full-screen story; delete is never blocked (no sync).

## 8. Photo viewer

* Black full screen pager over the step's photos, counter "2 / 3", close button; pinch to
  zoom (1×–5×), pan when zoomed, swipe to the next photo when not zoomed. Missing file →
  "Không tìm thấy tệp ảnh".
* **Screen:** `img/12-photo-viewer.png`.

## 9. System Photo Picker (not our UI)

* Opened from the step form, images only, up to 30 per pick. Shows "Lữ Trình will only have access
  to the photos you select". **Screen:** `img/07-photo-picker.png`.

## Screenshot index (`docs/img/`, Android emulator, Pixel 6 size)

| File | Shows |
| --- | --- |
| 01-trip-list-empty.png | first run, empty list |
| 02-trip-create.png | create form filled in |
| 03-trip-edit.png | edit form with changed title, delete button |
| 04-trip-list.png | list after create/edit and an app restart, cover from step photo |
| 05-location-picker.png | picker with OSM tiles, pin on Đà Lạt |
| 06-step-create.png | new step with map-picked location, date, note and 3 photos |
| 07-photo-picker.png | Android Photo Picker with 3 selected |
| 08-trip-detail.png | detail: header map with route, stats, timeline with photos |
| 09-trip-map-route.png | full trip map, numbered pins joined by the route, card carousel |
| 10-step-detail.png | step detail with photo, mini map |
| 11-step-edit.png | edit step: new name, re-picked location |
| 12-photo-viewer.png | full-screen viewer 2 / 3 |
| 13-step-delete-confirm.png | delete step confirm |
| 14-photos-after-restart.png | trip detail after force-stop + relaunch: photos still there |
| 15-trip-list-sample.png | list with the debug sample trips |
| 16-sample-hagiang-map.png | Hà Giang loop on the map (8 steps) |
| 17-trip-delete-confirm.png | delete trip confirm with counts |
| 18-trip-list-after-delete.png | list after the trip was deleted |
| 19-trip-validation.png | "Hãy đặt tên cho chuyến đi" on empty title |
