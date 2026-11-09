# 03 — Screens and navigation

Eleven destinations, one activity/window. Every screen that reads data shows one of four states:

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
   ├─ globe "Bản đồ của tôi" ─▶ My map           │  FAB "Thêm điểm dừng" ─▶ Step form (create)
   │       └─ tap trip card ─▶ Trip detail       │  small FAB "Thêm từ ảnh" ─▶ From photos
   ├─ ⋮ "Bản đồ offline" ─▶ Offline maps         │  tap leg chip ─▶ Leg mode sheet
   └─ (debug) ⋮ "Tạo dữ liệu mẫu"                │
                                                 │  tap step ─▶ Step detail
From photos ── file picker ─▶ review ── "Chọn trên bản đồ" ─▶ Location picker ─▶ back with point
From photos ── "Tạo N điểm dừng" ─▶ back to Trip detail (toast "Đã tạo N điểm dừng từ ảnh")
Step form ── "Chọn trên bản đồ"/"Đổi vị trí" ──▶ Location picker ── "Dùng vị trí này" ─▶ back with point
Step form ── "Thêm ảnh" ──▶ system Photo Picker ─▶ back with URIs
Step detail ── pencil ─▶ Step form (edit) │ bin ─▶ confirm ─▶ back │ tap photo ─▶ Photo viewer
Trip map ── tap card ─▶ Step detail │ tap leg chip on a card / mode badge on the map ─▶ Leg mode sheet
Trip map ── top-bar icon "Bản đồ offline" ─▶ Offline maps (with this trip's area first)
```

Route strings (Android, kept for deep links later): `trips`, `tripEdit?tripId=`, `trip/{tripId}`,
`trip/{tripId}/map`, `trip/{tripId}/stepEdit?stepId=`, `trip/{tripId}/step/{stepId}`,
`trip/{tripId}/pick?stepId=&lat=&lng=`, `trip/{tripId}/step/{stepId}/photos?index=`, `myMap`,
`trip/{tripId}/fromPhotos`, `offline?tripId=`. The leg mode sheet is a bottom sheet, not a route.
The picker returns its result to the step form through the previous screen's saved state
(`picked_location` = `"lat,lng"`); on iOS use a binding or a callback closure.

## 1. Trip list — "Chuyến đi của tôi"

* **Purpose:** all trips, newest first (R1); entry point.
* **Content:** large title; one card per trip with cover photo (or a gradient placeholder), title,
  date range (`6 thg 11 – 9 thg 11, 2026`, or `từ …` when open-ended), "N điểm dừng · M ảnh".
* **Actions:** tap → Trip detail; ⋮ or long-press → Sửa / Xóa; FAB "Chuyến đi mới"; globe icon
  in the top bar (label "Bản đồ của tôi") → My map; top ⋮ menu (label "Thêm tùy chọn") →
  "Bản đồ offline" (all builds). Delete asks "Xóa chuyến đi này?" with the counts of steps and photos that will be removed.
* **Empty:** suitcase icon, "Chưa có chuyến đi nào" + invitation text; in debug builds a
  "Tạo dữ liệu mẫu" button (also in the top ⋮ menu) seeds a Hà Giang loop and a Ninh Bình trip.
* **Screens:** `img/01-trip-list-empty.png`, `img/04-trip-list.png`, `img/15-trip-list-sample.png`,
  `img/17-trip-delete-confirm.png`, `img/18-trip-list-after-delete.png`.
* **Inspired by:** polarsteps docs/22 §1.1 (Me tab trip cards, long-press sheet for trip settings).
  **Changed:** no profile/"Me" page, no trip-type sheet (future / now / past) before the form, no
  share/privacy entries; delete is reachable from the list, not only from settings.

## 2. Trip form — "Tạo chuyến đi" / "Sửa chuyến đi"

* **Fields:** Tên chuyến đi (counter n/60, required), Mô tả ngắn (optional), Ngày đi (default
  today), Ngày về (optional, "Chưa xác định"; "Bỏ ngày về" clears it). Dates use the Material
  date picker, always in Vietnamese whatever the device language ("Chọn ngày", "23 thg 10, 2025",
  "tháng 10 năm 2025", week from T2 to CN; `img/21-date-picker-vietnamese.png` was taken on an
  English emulator). The step form's time picker (24 h) gets the same treatment.
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
  time, note preview and up to a row of photo thumbnails. Between two steps a **leg chip** (mode
  icon + name in the mode colour, or "Chọn cách di chuyển" when not set, then "· N km") sits on
  the timeline connector (R12); the header map draws the legs per mode (05-map.md).
* **Actions:** map icon → Trip map; ⋮ → Sửa / Xóa; tap step → Step detail; tap a leg chip → Leg
  mode sheet; FAB "Thêm điểm dừng"; small FAB above it (photo icon, label "Thêm từ ảnh") → From
  photos.
* **Empty:** "Chưa có điểm dừng" + text + button "Thêm điểm dừng"; the map shows Vietnam overview.
* **Screens:** `img/03` (empty state is the same layout), `img/08-trip-detail.png`,
  `img/14-photos-after-restart.png`, `img/28-upgrade-after-v2.png` (leg chips),
  `img/30-from-photos-result.png` (both FABs).
* **Inspired by:** docs/22 §1.1 (trip opens on its map with step cards, drawer with stats).
  **Changed:** a scrolling timeline with the map as a header instead of a map-first screen with a
  drawer; day numbers are computed from the trip start date.

## 4. Trip map — full screen

* **Content:** map (muted OpenFreeMap vector basemap, Vietnamese labels) with all pins + legs
  styled per stroke kind with a mode badge at each leg's middle (05-map.md), fitted to the whole
  route (camera rules) in the area above the card carousel, so no pin hides under a card; first
  pin white with a green ring (start), last pin clay (end); a horizontal carousel of step cards at the bottom (thumbnail, "1. Title",
  date · time; from card 2 on, the chip of the leg that arrives at this step).
* **Behaviour:** tapping a pin selects it (larger, gold ring + halo) and scrolls the carousel to
  its card; tapping a card selects its step, tapping the selected card opens Step detail; each
  selection flies the camera to that step (zoom ≥ 11, never zooming the user out); tapping a badge, a leg line or a
  card's leg chip opens the Leg mode sheet, and the leg turns gold and is framed above the sheet. Empty
  trip: message "Chuyến đi chưa có điểm dừng nào để hiển thị."
* **Screens:** `img/09-trip-map-route.png`, `img/16-sample-hagiang-map.png`,
  `img/20-trip-map-pins-above-cards.png`, `img/23-trip-map-leg-modes.png`,
  `img/24-trip-map-legs-zoomed.png`; before/after the route redraw: `img/route-redraw/`.
* **Inspired by:** docs/22 §1.1/§2 (map with step-card carousel, fit-to-steps camera, transport
  mode per leg) and docs/23 (route rendering: casing + shadow on ground legs, zoom-dependent
  widths, flight arc, simplification). **Changed:** straight legs (no snap-to-roads), one route
  colour (deep green, gold when selected) instead of colour per trip, own stroke/badge per mode,
  great-circle flight arc with a sideways lift, own pin design (numbered discs, distinct
  start/end), no map-style switcher.

## 4b. Leg mode sheet — "Từ A đến B"

* **Opened from:** a leg chip on the Trip detail timeline or the Trip map cards, or a mode badge on
  the Trip map.
* **Content:** title "Từ <step> đến <step>", "Bạn di chuyển bằng gì? (N km đường chim bay)", one
  row per mode (icon in its colour + name: Đi bộ, Xe máy, Ô tô, Xe buýt/xe khách, Tàu hỏa, Máy
  bay, Thuyền) and "Chưa chọn"; the current choice is marked. Tap → saved at once (R12), sheet
  closes; swipe down/outside → no change.
* **Screen:** `img/25-leg-mode-sheet.png`.
* **Inspired by:** docs/22 (transport mode per leg). **Changed:** own list for Vietnam (xe máy,
  xe buýt/xe khách), a single bottom sheet instead of a separate screen, no automatic guess.

## 4c. My map — "Bản đồ của tôi"

* **Purpose:** every trip on one map (R14).
* **Content:** full-screen map; at the top a card with three totals: số chuyến đi, điểm dừng, km
  (sum of each trip's straight-line distance); at the bottom a carousel of trip cards (colour
  dot, title, dates, "N điểm dừng · M km"). Each trip is drawn in its own colour on a white
  casing with a soft shadow; the camera fits all trips between the totals card and the carousel.
* **Behaviour:** tap a trip's line or dots → that trip is selected (wider line, on top, card
  scrolled into view and highlighted); tap a card → Trip detail. No trips: "Chưa có chuyến đi nào để
  hiện trên bản đồ."
* **Screen:** `img/26-my-map.png`.
* **Inspired by:** polarsteps' world map of all trips (docs/22). **Changed:** totals limited to
  what works offline — no countries/provinces (needs geocoding), no world-percentage.

## 5. Step form — "Điểm dừng mới" / "Sửa điểm dừng"

* **Fields:** Tên địa điểm (required, n/80), Vị trí card (mini map + coordinates; "Chọn trên bản
  đồ" / "Đổi vị trí"), Ngày đến + Giờ đến (Material pickers, Vietnamese), Ghi chú (multi-line), Ảnh (n) with
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

## 10. From photos — "Thêm từ ảnh"

* **Opens:** on Android 10+ first the system permission dialog for photo location
  (`ACCESS_MEDIA_LOCATION`; on Android 14+ the "photos and videos" dialog), then the system
  **file picker** (multi-select, images) — not the Photo Picker, which hides GPS (06-photos.md).
  Cancelling the picker with nothing picked returns to the trip. While reading: progress.
* **Review content:** a 180 dp map with the numbered kept suggestions; "N ảnh → M điểm dừng gợi
  ý" + hint; one card per suggestion (R13): checkbox (label "Giữ điểm dừng N"), number, editable
  title ("Điểm dừng N", R5 length check), date · time from the photos (or "Ảnh không có ngày
  chụp · dùng <R6 date>"), a red "Chụp ngày …, ngoài thời gian chuyến đi · không thể thêm" warning
  when outside the trip (cannot be kept), place line with "Chọn trên bản đồ" / "Đổi vị trí" → Location picker, thumbnails and
  "N ảnh". Photos without GPS form the last card "Ảnh không có vị trí", unticked until placed.
* **Confirm:** bottom button "Tạo N điểm dừng" (disabled while no card is kept or a kept card has
  no title/place) → imports the kept photos (R11), saves one step per card, back to Trip detail
  with a toast "Đã tạo N điểm dừng từ ảnh" (+ "Không đọc được M ảnh" when an import failed; such
  photos are skipped, the step is still made).
* **Screens:** `img/29-from-photos-review.png`, `img/30-from-photos-result.png`.
* **Inspired by:** docs/22/16 (steps suggested from photo location/date). **Changed:** the user
  picks the photos (no gallery scan), an explicit review screen with editable titles, same-day +
  2 km grouping, no place-name lookup.

## 11. Offline maps — "Bản đồ offline"

* **Opens from:** the trip list ⋮ menu (all trips' areas) or the Trip map top bar (icon with
  label "Bản đồ offline"; then this trip's area is listed first). Route `offline?tripId=`.
* **Content (one list):** intro text (what it is, © OpenStreetMap via OpenFreeMap); when the
  device has no network a banner "Đang không có mạng — chỉ xem được các vùng đã tải.";
  * **"Chuyến đi này"** (only when opened from a trip): card "Tải vùng của chuyến đi này" with
    "Khung bao mọi điểm dừng của “<title>”, thêm 10 km mỗi phía."; a trip without steps shows
    "Chuyến đi chưa có điểm dừng nào để tải bản đồ."; a trip too wide for the limit shows
    "Chuyến đi trải quá rộng để tải một lần (ước tính …, tối đa 150,0 MB). Hãy tải từng vùng có sẵn."
  * **"Vùng có sẵn"**: Hà Giang and Ninh Bình, each with the places it covers;
  * **"Vùng của các chuyến đi"**: trip areas already stored (downloading or downloaded) that
    are not the current trip's;
  * footer "Bộ nhớ bản đồ đang dùng: X (gồm cả bộ đệm khi xem online) · Còn trống: Y".
* **One card per area, by phase (R15):**

| Phase | Status line | Buttons |
| --- | --- | --- |
| not downloaded | "Ước tính ~29,6 MB" | "Tải về" |
| downloading | "Đang tải 42% · 11,0 MB" + progress bar | "Tạm dừng", "Hủy tải" |
| waiting for network | "Mất mạng · 42% · sẽ tự tải tiếp khi có mạng" | "Tạm dừng", "Hủy tải" |
| paused | "Đã tạm dừng · 42% · 11,0 MB" | "Tiếp tục", "Hủy tải" |
| failed | error text: "Không có mạng. Hãy kết nối Wi‑Fi hoặc 4G rồi thử lại." / "Không đủ bộ nhớ trống (cần ~X). …" / "Máy chủ bản đồ đang lỗi, đang thử lại…" / "Không tải được vùng này. Hãy thử lại." | "Tải về" (retry) or "Tiếp tục", "Hủy tải" when something is stored |
| downloaded | "Đã tải · 26,3 MB" (stored size) | "Xóa" → dialog "Xóa bản đồ offline “Hà Giang”?" / "Giải phóng tới 26,3 MB (phần dùng chung với vùng khác được giữ lại). …" / Xóa · Hủy |

* **Behaviour:** downloads run in the app process, not in the screen: leaving the screen does
  not stop them; after the app is killed they resume at the next start (unless paused).
* **On every map** (trip detail header, trip map, step maps, location picker, Bản đồ của tôi),
  only while the device is offline: top-left chip "Ngoại tuyến · Hà Giang" when a downloaded
  area contains the camera centre, otherwise a red card "Không có mạng" / "Vùng này chưa tải bản
  đồ offline." (the basemap there is empty or blurry; route and pins are still drawn).
* **Screens:** `img/offline/01-offline-list.png` (not downloaded, estimates),
  `img/offline/02-offline-downloaded.png` (both downloaded with sizes),
  `img/offline/03-airplane-hagiang-region.png`, `img/offline/04-airplane-hagiang-city.png`,
  `img/offline/05-airplane-hoian-not-downloaded.png`, `img/offline/06-delete-confirm.png`.
* **Inspired by:** nothing in the polarsteps reference (offline maps are a Lữ Trình addition for
  mountain areas without signal). Ready-made regions instead of drawing a box on a map: simpler,
  and keeps downloads bounded for the donated tile server.

## Screenshot index (`docs/img/`, Android emulator, Pixel 6 size)

| File | Shows |
| --- | --- |
| 01-trip-list-empty.png | first run, empty list |
| 02-trip-create.png | create form filled in |
| 03-trip-edit.png | edit form with changed title, delete button |
| 04-trip-list.png | list after create/edit and an app restart, cover from step photo |
| 05-location-picker.png | picker on the OpenFreeMap basemap, pin near Du Già |
| 06-step-create.png | new step with map-picked location, date, note and 3 photos |
| 07-photo-picker.png | Android Photo Picker with 3 selected |
| 08-trip-detail.png | detail: header map with route (cased green line, start/end pins), stats, timeline |
| 09-trip-map-route.png | full trip map, numbered pins joined by the route, card carousel (old raster basemap, before the route redraw) |
| 10-step-detail.png | step detail with photo, mini map |
| 11-step-edit.png | edit step: new name, re-picked location |
| 12-photo-viewer.png | full-screen viewer 2 / 3 |
| 13-step-delete-confirm.png | delete step confirm |
| 14-photos-after-restart.png | trip detail after force-stop + relaunch: photos still there |
| 15-trip-list-sample.png | list with the debug sample trips |
| 16-sample-hagiang-map.png | Hà Giang loop at country zoom (pins/badges shrink, short legs drop their badges) |
| 17-trip-delete-confirm.png | delete trip confirm with counts |
| 18-trip-list-after-delete.png | list after the trip was deleted |
| 19-trip-validation.png | "Hãy đặt tên cho chuyến đi" on empty title |
| 20-trip-map-pins-above-cards.png | step 3 selected: camera flew to it above the cards, larger pin with gold halo |
| 21-date-picker-vietnamese.png | date picker in Vietnamese on an English-language emulator |
| 22-photo-exif-rotated-upright.png | 4800×3600 photo with EXIF orientation 6 + GPS, stored and shown upright |
| 23-trip-map-leg-modes.png | Hà Giang trip map fitted above the cards: cased ground legs, dotted walk, dashed flight arc, badges |
| 24-trip-map-legs-zoomed.png | zoomed to town scale: wider cased lines, dotted walk leg 4→5 |
| 25-leg-mode-sheet.png | leg mode sheet "Từ … đến …"; the leg (gold) framed above it |
| 26-my-map.png | "Bản đồ của tôi": all trips in their colours, totals, trip cards |
| route-redraw/before-*.png, after-*.png | the same Hà Giang views on main@55b3966 (raster OSM) and after the route redraw: country, region, city, flight, selected, trip-detail, mymap (+ after-leg-selected) |
| 27-upgrade-before-v1.png | main@b07fcf6 build (database v1) with sample data, before the upgrade |
| 28-upgrade-after-v2.png | same data after installing the v2 build over it, leg chips "Chọn cách di chuyển" |
| 29-from-photos-review.png | "Thêm từ ảnh" review: 2 GPS groups + the placed no-GPS group |
| 30-from-photos-result.png | the trip after "Tạo 3 điểm dừng" |
| offline/01-offline-list.png | "Bản đồ offline" before any download: ready-made regions with estimates |
| offline/02-offline-downloaded.png | Hà Giang and Ninh Bình downloaded, stored sizes, storage footer |
| offline/03-airplane-hagiang-region.png | airplane mode, app restarted: Hà Giang trip map at region zoom from the pack, chip "Ngoại tuyến · Hà Giang" |
| offline/04-airplane-hagiang-city.png | same at town zoom (street + village labels, route, pins) |
| offline/05-airplane-hoian-not-downloaded.png | airplane mode, location picker at Hội An (not downloaded): "Không có mạng / Vùng này chưa tải bản đồ offline." |
| offline/06-delete-confirm.png | "Xóa bản đồ offline “Ninh Bình”?" with the space it frees |

## Accessibility and UI tests

* The extended FABs ("Chuyến đi mới", "Thêm điểm dừng") and the icon buttons "Thêm từ ảnh" and
  "Bản đồ của tôi" carry their label as content description:
  Material 3 1.3 exposes no text for them, so TalkBack would only say "Button".
* Instrumented Compose tests (`app/src/androidTest/.../ui/TripFlowsTest.kt`) drive the real app
  on a device: create trip, edit trip, delete trip, add a step with the "Mã Pí Lèng" quick-place
  chip, set a leg's mode to "Xe máy" from the timeline (checked in the database), open "Bản đồ
  của tôi" from the list, open "Bản đồ offline" from the list menu (ready-made regions listed) and
  from a trip map ("Tải vùng của chuyến đi này"). They find nodes by the Vietnamese texts/labels above (changing a string means updating the
  test) and delete every trip whose title starts with `UITEST` afterwards. Run with
  `./gradlew connectedDebugAndroidTest` (needs network for tiles only cosmetically; the picker
  centre updates without tiles).
