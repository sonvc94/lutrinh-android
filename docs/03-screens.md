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

## Display conventions (one rule per kind of value)

All of them live in `domain/usecase/DisplayFormat.kt` (Swift: one `DisplayFormat` enum, tests
ported 1:1 from `DisplayFormatTest`); no screen formats km, durations or in-trip days itself.

| Value | Rule | Example |
| --- | --- | --- |
| Distance (trip, leg, totals) | Vietnamese decimal comma, one decimal under 10 km, whole above, "." for thousands; never "10,0" | "9,3 km", "51 km", "1.234 km" |
| Recorded track distance | "N m" under 1 km, one decimal under 100 km, whole above | "850 m", "51,5 km" |
| Duration (finished track, stop) | words, whole minutes rounded down | "35 phút", "6 giờ 45 phút", "1 giờ" |
| Travel-time estimate (leg, trip, R36) | "~" + the duration rule, rounded to whole minutes (at least 1); only from a stored road route | "~1 giờ 33 phút", "~18 phút" |
| Leg distance (chip, sheet, R12/R36) | road: "N km · ~T"; no road route: "N km chim bay" | "48 km · ~1 giờ 33 phút", "4,1 km chim bay"; a Xe máy leg drawn with the car profile (no motorbike route, R36): "61 km · ~1 giờ 30 phút · tuyến ô tô" |
| Live timer (recording panel, notification) | clock | "0:45", "2:05:09" |
| Day inside a trip (timeline, suggestions, tracks, step detail, trip map cards) | weekday + dd/MM | "Thứ Hai, 20/10"; with the time "Thứ Hai, 20/10 · 08:10" |
| Trip dates (list, header, pickers, errors) | "d MMM, yyyy" with year | "20 thg 10 – 23 thg 10, 2025" |
| Shared page (leaves the app) | weekday + dd/MM/yyyy | "Thứ Hai, 20/10/2025" |
| Km readout (trip detail, share image) | the total large, "km" and its source on the same line: "đường chim bay" (straight line through the steps), "theo đường đi" (every leg along the road, R36), "theo đường đi, có chặng chim bay" (some), or "đã ghi GPS" (sum of the finished tracks, used when it is at least the leg total, R10). The share image shows the number only | "139 km đường chim bay" over a 0 · 50 · 100 scale bar; "264 km theo đường đi, có chặng chim bay" |
| Place without a name (step detail, step form, from-photos card) | "gần <điểm dừng gần nhất của chuyến>, <km>" (the km rule above; "gần <điểm dừng>" alone under 50 m); coordinates `lat, lng` as a smaller second line; no other step → coordinates only. No gazetteer (`PlaceLabel`, Swift: same pure function) | "gần TP Hà Giang, 9,7 km" / "22.91042, 104.97814" |
| Default step name (from photos, stop suggestion) | "Điểm dừng HH:mm" of the step's arrival (`DisplayFormat.defaultStepTitle`, one function for both) | "Điểm dừng 09:05" |
| Stat order | trip detail and share image: the km readout first (number + scale bar, docs/08 §5), then điểm dừng, ảnh, ngày as one line of type; share page: điểm dừng, ảnh, km, ngày; My map: km, then chuyến đi, điểm dừng on the same line | |

Theme: the app is light-only. The system bars are forced to the light style (dark icons) so the
clock and icons stay readable on the topo-paper app bar when the device is in dark mode (Android:
`enableEdgeToEdge(SystemBarStyle.light(…))`; the photo viewer, which is black, switches the status
bar icons to light while it is open). A dark palette is a separate decision. Library strings (sheet
"Đóng trang tính", drag handle "Nút kéo", pickers) are Vietnamese whatever the device language
(`MainActivity.attachBaseContext` sets a Vietnamese configuration on the activity's resources).
Look: **docs/08-design.md** ("Topo outdoor", after the huashu-design specimen 06-topo-outdoor) is
the design spec for every present and future screen: topo-paper ground + card-paper panels with a
2 dp ledge, a forest dark chip for the main action, one signal-orange route, radii 8/12/18/22,
component, map and imagery rules. `docs/09-redesign-inventory.md` lists which screens are redone.
Typography: every text style uses **Noto Sans** (SIL OFL 1.1, Regular/Medium/Bold TTFs bundled
in `res/font/`, licence in `assets/licenses/NotoSans-OFL.txt`; no downloadable-font call, the app
is offline); stand-alone numbers (stats, km) use **Barlow Condensed** Bold with tabular figures
(`TextStyle.numerals()`). Android: `ui/theme/Type.kt` (`AppTypography`); iOS: docs/07.
Every `surfaceContainer*` role of the colour scheme is defined from the palette (Card paper), so no
card falls back to the Material lavender default.

## Navigation graph

```
Trip list ──FAB "Chuyến đi mới"──────────────▶ Trip form (create) ──save──▶ Trip detail (replaces form)
   │  tap card                                                             ▲
   ├──────────────────────────────────────────▶ Trip detail ◀─────────────┘
   │  card ⋮ / long-press → Sửa                  │  ⋮ Sửa ─▶ Trip form (edit) ─save/back─▶ back
   ├──────────────────────────────▶ Trip form    │  ⋮ Xóa ─▶ confirm ─▶ back to list
   │  card ⋮ → Xóa → confirm                     │  header map / expand ─▶ Trip map
   ├─ globe "Bản đồ của tôi" ─▶ My map           │  FAB "Thêm" ─▶ "Thêm điểm dừng" ─▶ Step form
   │       └─ tap trip card ─▶ Trip detail       │            └ "Thêm từ ảnh" ─▶ From photos
   ├─ ⋮ "Bản đồ offline" ─▶ Offline maps         │  tap leg chip ─▶ Leg mode sheet
   ├─ card ⋮ → Chia sẻ chuyến đi ─▶ Share trip   │  share icon "Chia sẻ chuyến đi" ─▶ Share trip
   ├─ card ⋮ → Xuất GPX ─▶ dialog ─▶ save/share  │  ⋮ Xuất GPX ─▶ dialog ─▶ save as / share sheet
   ├─ ⋮ "Nhập tệp GPX" ─▶ file picker ─▶ new Trip detail
   ├─ ⋮ "Sao lưu dữ liệu" ─▶ save as ─▶ snackbar
   ├─ ⋮ "Khôi phục từ bản sao lưu" ─▶ file picker ─▶ confirm ─▶ list reloads
   ├─ ⋮ "Cài đặt" ─▶ Settings (§13)
   ├─ (from Google Maps: Share ─▶ Lữ Trình) ─▶ Add to trip (§15) ─▶ Trip detail + Step form (prefilled)
   ├─ ⋮ "Nhập mã lộ trình" / empty "Có mã lộ trình?" ─▶ Route code (§14) ─▶ Preview ─▶ Download
   │       └─ done ─▶ new Trip detail (replaces the code screen; badge "Lộ trình … · vN")
   │          badge ─▶ route sheet ─ "Tải thành chuyến đi mới" ─▶ Route code (prefilled) / "Thay bản trong máy"
   └─ (debug) ⋮ "Tạo dữ liệu mẫu"                │
                                                 │  tap step ─▶ Step detail
                                                 │  suggestion "Tạo điểm dừng ở đây" ─▶ Step form (prefilled)
From photos ── file picker ─▶ review ── "Chọn trên bản đồ" ─▶ Location picker ─▶ back with point
From photos ── "Tạo N điểm dừng" ─▶ back to Trip detail (toast "Đã tạo N điểm dừng từ ảnh")
Step form ── "Chọn trên bản đồ"/"Đổi vị trí" ──▶ Location picker ── "Dùng vị trí này" ─▶ back with point
Step form ── "Thêm ảnh" ──▶ system Photo Picker ─▶ back with URIs
Step detail ── pencil ─▶ Step form (edit) │ bin ─▶ confirm ─▶ back │ tap photo ─▶ Photo viewer
Trip map ── tap card ─▶ Step detail │ tap leg chip on a card / mode badge on the map ─▶ Leg mode sheet
Trip map ── top-bar icon "Bản đồ offline" ─▶ Offline maps (with this trip's area first)
Trip map / My map ── "Vị trí của tôi" ─▶ (rationale ─▶ system prompt) ─▶ waypoint-blue dot, camera flies there (R22)
Trip map / My map / Location picker ── "Kiểu bản đồ" ─▶ sheet "Bản đồ" | "Vệ tinh" ─▶ every map switches (R32)
GPS card ── "Ghi hành trình ổn định" ─▶ Battery guide │ first recording ever starts ─▶ Battery guide (once)
Share trip ── "Chia sẻ" ─▶ system share sheet │ "Lưu vào máy" ─▶ snackbar "Đã lưu vào …"
```

Route strings (Android, kept for deep links later): `trips`, `tripEdit?tripId=&record=` (`record=true` after "Tạo chuyến mới và ghi": the saved trip opens and starts recording), `trip/{tripId}?record=`,
`trip/{tripId}/map`, `trip/{tripId}/stepEdit?stepId=&stopTrack=&stopStart=&stopEnd=&lat=&lng=&title=` (the stop-suggestion arguments are optional, R17), `trip/{tripId}/step/{stepId}`,
`trip/{tripId}/pick?stepId=&lat=&lng=`, `trip/{tripId}/step/{stepId}/photos?index=`, `myMap`,
`trip/{tripId}/fromPhotos`, `offline?tripId=`, `trip/{tripId}/share`, `batteryGuide`, `settings`.
The one-time routing notice (R36) is a dialog at the navigation root, over whatever screen is open. The leg mode sheet is a bottom sheet, not a route.
The picker returns its result to the step form through the previous screen's saved state
(`picked_location` = `"lat,lng"`); on iOS use a binding or a callback closure.

## 1. Trip list — "Chuyến đi của tôi"

* **Purpose:** all trips, newest first (R1); entry point.
* **Content:** large title; one card per trip with cover photo (R21: the chosen "Ảnh bìa", else
  automatic; with no photo at all, the trip's own route drawn on paper with a graticule,
  `RouteCover`, docs/08 "Imagery"), title, date range (`6 thg 11 – 9 thg 11, 2026`, or `từ …`
  when open-ended), "N điểm dừng · M ảnh" (published steps, R20) and "· K nháp" when the trip has drafts.
* **Actions:** tap → Trip detail; the card ⋮ (label "Tùy chọn: <tên chuyến đi>") or long-press → Sửa / Chia sẻ chuyến đi (§3d) / Xuất GPX (§12) / Xóa; FAB "Chuyến đi mới"; globe icon
  in the top bar (label "Bản đồ của tôi") → My map; top ⋮ menu (label "Tùy chọn khác") →
  "Bản đồ offline", "Nhập tệp GPX", "Nhập mã lộ trình" (§14, R40; hidden when the build has no route server), "Sao lưu dữ liệu", "Khôi phục từ bản sao lưu" (§12), "Cài đặt" (§13, R36) (all builds). Delete asks "Xóa chuyến đi này?" with the counts of steps and photos that will be removed.
* **Empty:** a contour mark (docs/08 "Empty states"), "Chưa có chuyến đi nào" +
  invitation text, left-aligned; "Có mã lộ trình?" (§14, R40); in debug builds a
  "Tạo dữ liệu mẫu" button (also in the top ⋮ menu) seeds a Hà Giang loop and a Ninh Bình trip; the
  Hà Giang trip also gets an ended sample track on day 1 (08:10–15:50) with three stays — at Dốc
  Bắc Sum, at Cổng Trời (matches that step, so not suggested) and a paused stay at Tam Sơn — so
  "Gợi ý điểm dừng" shows two suggestions (R17).
* **Screens:** `img/01-trip-list-empty.png`, `img/04-trip-list.png`, `img/15-trip-list-sample.png`,
  `img/17-trip-delete-confirm.png`, `img/18-trip-list-after-delete.png`,
  `img/draft-cover/07-trip-list-draft-count.png` ("· 1 nháp"), `img/draft-cover/10-trip-list-chosen-cover.png`
  (chosen cover), `img/draft-cover/13-trip-list-auto-cover.png` (back to "Tự động").
* **Inspired by:** polarsteps docs/22 §1.1 (Me tab trip cards, long-press sheet for trip settings).
  **Changed:** no profile/"Me" page, no trip-type sheet (future / now / past) before the form, no
  privacy entries; sharing makes files (§3d) instead of links; delete is reachable from the list, not only from settings.

## 2. Trip form — "Tạo chuyến đi" / "Sửa chuyến đi"

* **Fields:** Tên chuyến đi (counter n/60, required), Mô tả ngắn (optional), Ngày đi (default
  today), Ngày về (optional, "Chưa xác định"; "Bỏ ngày về" clears it). Dates use the Material
  date picker, always in Vietnamese whatever the device language ("Chọn ngày", "23 thg 10, 2025",
  "tháng 10 năm 2025", week from T2 to CN; `img/21-date-picker-vietnamese.png` was taken on an
  English emulator). The step form's time picker (24 h) gets the same treatment.
* **Look (docs/08):** card-paper outlined fields (`topoFieldColors`), date and time buttons in the
  same style with a Calendar / Clock icon (`FieldButton`), the cover row a ledge card, forest save.
* **Validation:** on save, via the domain (02-data-model.md); errors appear under the fields. Edit
  also blocks date changes that would orphan steps (R4).
* **Edit only:** row "Ảnh bìa" (R21) under the dates: thumbnail of the cover as it will be,
  "Tự động" or "Đã chọn", and "Đổi ảnh bìa" → the cover sheet ("Đổi ảnh bìa": a wide "Tự động" tile
  — the automatic photo dimmed, "Ảnh đầu tiên của điểm dừng đầu tiên" — then every photo of the
  trip's steps in timeline order, 3 per row; the current choice has a ring and a tick; a tap picks
  and closes; no photos: "Chuyến đi chưa có ảnh. Thêm ảnh vào điểm dừng để chọn ảnh bìa."). The
  choice is stored with "Lưu"; Back asks to discard like any other edit.
  "Xóa chuyến đi" button → same confirm as the list.
* **After create:** opens the new trip's detail (form removed from the back stack). After edit: back.
* **Screens:** `img/02-trip-create.png`, `img/03-trip-edit.png`, `img/19-trip-validation.png`,
  `img/draft-cover/11-trip-form-cover-row.png`, `img/draft-cover/12-cover-picker-chosen.png`.
* **Inspired by:** docs/22 §1.2 (name required, start required, end optional, delete at the bottom
  of edit with a strong warning) and §1.2 cover photo. **Changed:** the cover is picked from the
  trip's own step photos or left automatic (no upload or crop); no travel buddies / privacy / tracker / location-privacy fields, no "no overlap with
  other trips" rule (overlapping trips are allowed), plain date pickers instead of a full-screen
  calendar, Vietnamese copy written for this app.

## 3. Trip detail

* **Purpose:** one trip as a timeline.
* **Order (top to bottom):** header map preview (180 dp, numbered pins, route R3, expand button; every pin is kept clear of the credit strip at the bottom and the expand button, see docs/05 "Camera rules");
  title, date range, description, a hairline and the km readout; the collapsed **"Hành trình GPS" card** (§3b);
  then the timeline, so "Ngày 1" is on the first screen without scrolling.
* **Content:** the km readout (docs/08 §5): the km total in large numerals with "km" and its
  source ("đường chim bay", or "đã ghi GPS" when finished tracks cover the trip, R10) as the
  eyebrow of a stats panel: one row of large numerals over muted labels (km, điểm dừng, ảnh, ngày),
  then a signal-orange scale bar of the km total with its ticks (0 · 50 · 100 for 139 km). When at least one leg has a road route (R36), a line under it:
  "~10 giờ 20 phút di chuyển (ước tính)", or "… (ước tính, chưa tính 2 chặng)" when other legs
  have none (R10).
  Timeline grouped by day ("Ngày 2 · Thứ Bảy, 07/11"), each step with its number, time, note
  preview and up to a row of photo thumbnails. Between two steps a **leg chip** (mode icon + name
  in the mode colour, or "Chọn cách di chuyển" when not set, then "· 48 km · ~1 giờ 33 phút" along
  the road or "· 4,1 km chim bay", R12/R36) sits on the timeline connector; under it the **leg
  note** (R41, note icon + text, 2 lines, then "Xem thêm" / "Thu gọn"); the header map draws
  the legs per mode, routed legs along the road (05-map.md), and the recorded tracks
  (ink, 05-map.md "Recorded tracks").
* **Reorder (R44):** long-press a stop card and drag it between two others (the card follows the finger,
  an orange line marks the place, the list scrolls at its edges); on release the stop's arrival time becomes
  the middle of its new neighbours (30 min before the first / after the last) and a snackbar says "Đã chuyển
  “X”. Giờ đến mới: HH:mm" with "Sửa giờ". When a leg with a note or a mode is broken by the move, a bottom
  sheet asks which new leg ("A → B") takes it, or to drop it; with no room between two stops a dialog opens the
  stop's form instead. Screenshots: `img/stop-drag/drag.png` (drop line while dragging), `leg-choice.png` (sheet), `moved-snackbar.png` (new time + "Sửa giờ").
* **View switch (R45):** under the GPS card (only with a published stop) two segments "Theo thứ tự" (the timeline above)
  and "Gần tôi". "Gần tôi" lists the published stops nearest → farthest by road distance from the user's position
  (stop number disc, title, "12 km · ~29 phút", "· tuyến ô tô" when only the car profile has a route), with
  "Làm mới" above the list; a stop with no road distance goes last as "Chưa tính được đường · …". Opening it
  asks for the location permission and a position once. One message with a button replaces the list when there is
  nothing to show: "Gửi vị trí để tính đường?" (consent: routing off or notice not accepted, button "Đồng ý và tính
  đường"), "Chưa có quyền dùng vị trí", "Vị trí đang tắt", "Chưa lấy được vị trí", "Chưa tính được đường" (offline),
  each with "Thử lại". Drag-reorder exists in "Theo thứ tự" only. Screenshots: `img/near-me/list.png`,
  `consent.png`, `permission-denied.png`, `offline.png`.
* **Top bar:** back, share icon ("Chia sẻ chuyến đi") and ⋮. The trip title appears in the bar only
  after the header has scrolled away (it is not shown twice); there is one entry to the Trip map
  (the header map and its expand button), no map icon in the bar.
* **Drafts (R20):** a draft step sits in the timeline in its time order with a hollow dot (pencil
  icon) instead of a number, muted text and a "Nháp" badge; no leg chip leads into it, the chip
  above the next published step is the leg from the published step before the draft. The
  readout ignores drafts.
* **Actions:** share icon → Share trip (§3d); header map / expand → Trip map; ⋮ → Sửa chuyến đi /
  Đổi ảnh bìa (the cover sheet of §2, stored at once, R21) / Xuất GPX (§12, R25) / Xóa chuyến đi; tap
  step → Step detail; tap a leg chip → Leg mode sheet; **one FAB** "Thêm" that shrinks to "+" when
  the list scrolls down and opens a small menu: "Thêm điểm dừng" → Step form, "Thêm từ ảnh" → From
  photos. The FAB floats over the list, so while scrolling it can pass over a card; at rest at the
  top of the list it covers no button.
* **Empty:** "Chưa có điểm dừng" + text + button "Thêm điểm dừng"; the map shows Vietnam overview.
* **Screens:** `img/trip-detail-declutter/trip-detail-collapsed.png` (timeline first, collapsed GPS
  card, km tile), `img/trip-detail-declutter/gps-card-expanded.png`,
  `img/trip-detail-declutter/gps-card-scrolled.png` (title in the bar, FAB as "+"),
  `img/03` (empty state), `img/08-trip-detail.png` (older layout), `img/14-photos-after-restart.png`,
  `img/28-upgrade-after-v2.png` (leg chips), `img/30-from-photos-result.png` (older two-FAB layout),
  `img/draft-cover/02-timeline-draft-badge.png`, `03-stats-unchanged-by-draft.png`, `08-timeline-after-publish.png`.
* **Inspired by:** docs/22 §1.1 (trip opens on its map with step cards, drawer with stats).
  **Changed:** a scrolling timeline with the map as a header instead of a map-first screen with a
  drawer; day numbers are computed from the trip start date; GPS tools are folded into one card
  so the timeline leads.

## 3b. Record a track — "Ghi hành trình" (R16)

* **Where:** the **"Hành trình GPS" card** of the Trip detail (under the stats, above the
  timeline), and a compact floating control at the top right of the Trip map (§4). Both drive the
  same recording.
* **The card:** collapsed by default (chevron toggles; it opens by itself when the user arrives
  from "Tạo chuyến mới và ghi"). The header shows "Hành trình GPS" and one summary line: live
  "Đang ghi · 0:45 · 120 m" while recording, "Chưa ghi" when it has nothing, else counts
  "N hành trình · N gợi ý" (zero parts omitted). Opening it scrolls the card to the top of the screen (so the FAB stays clear of its buttons). Expanded it holds, in order, the record control,
  the track list and the stop suggestions (§3c).
* **Ended trips (R16):** when the trip's end date is before today there is no "Ghi hành trình"
  (detail card and Trip map); a hint "Chuyến đi đã kết thúc: không ghi thêm hành trình vào chuyến
  này." and the tonal button **"Tạo chuyến mới và ghi"** take its place → Trip form (create) →
  on save the new trip opens and recording starts (the usual permission flow). Ongoing trips (no
  end date) and trips ending today or later can record.
* **Stop feedback:** every stop (button or notification action) shows a snackbar, app-wide: "Đã
  lưu hành trình 12,3 km", or, when the track had fewer than 2 points or under 50 m, "Hành trình
  quá ngắn (dưới 50 m) nên không được lưu", and that track is deleted (R16). After a process death
  the same rule runs at the next start (no snackbar): an interrupted track under 50 m / 2 points
  is deleted, a longer one stays in the list as "Dừng do ứng dụng bị đóng khi đang ghi".
* **Idle:** a short hint ("Bấm "Ghi hành trình" khi lên đường: …", detail only) and the tonal
  button **"Ghi hành trình"** (route icon). Tap → permission flow of R16: explanation dialog
  "Ghi hành trình bằng GPS" (Tiếp tục / Hủy) → system location prompt → (Android 13+)
  notification prompt → recording starts. Refused precise location → dialog "Chưa có quyền vị
  trí chính xác" with "Mở cài đặt"; device location off → "Vị trí đang tắt" with "Mở cài đặt".
* **Recording (this trip):** a red dot + "Đang ghi", the ticking duration · distance, a pause
  icon button ("Tạm dừng") and the button "Dừng". **Paused:** "Tạm dừng", the frozen duration ·
  distance, a play icon button ("Tiếp tục") and "Dừng". Another trip is recording: the text "Đang
  ghi hành trình cho chuyến đi khác" instead of the button (one recording at a time).
* **Notification** (while open, cannot be swiped away): small route icon, "Đang ghi hành trình"
  with a ticking chronometer and "<distance> · <trip>", actions "Tạm dừng" and "Dừng"; paused:
  "Đã tạm dừng ghi hành trình", "<duration> · <distance> · <trip>", actions "Tiếp tục" and
  "Dừng". Tap → opens the app.
* **Stable recording (R22):** under the record control of the card, the text button **"Ghi hành
  trình ổn định"** (battery icon) opens §3f. The same screen opens by itself, once per install,
  right after the first recording ever starts (the recording keeps running behind it).
* **Track list** "Hành trình đã ghi (N)" in the same section, newest first: one row per track with
  start date and time ("09/11/2026 14:05"), distance · duration · "N điểm GPS", and its state
  ("Đang ghi" / "Tạm dừng" in red, "Dừng do ứng dụng bị đóng khi đang ghi" for an interrupted
  track). Ended tracks have a delete icon ("Xóa hành trình") → confirm "Xóa hành trình này?" with
  time, distance and point count → deleted with its points. The open track cannot be deleted.
* **Screens:** `img/trip-detail-declutter/gps-card-expanded.png` (ended trip: hint + "Tạo chuyến
  mới và ghi", track row, suggestions), `img/trip-detail-declutter/trip-map-ended.png`,
  `img/gps-track/01-recording-notification.png`,
  `img/gps-track/02-trip-detail-recording.png`, `img/gps-track/03-trip-map-track.png`,
  `img/gps-track/04-airplane-trip-map-track.png`, `img/gps-track/05-track-list-interrupted.png`,
  `img/gps-track/06-my-map-track.png`, `img/gps-track/07-stop-saved-snackbar.png`,
  `img/gps-track/08-stop-dropped-snackbar.png`, `img/gps-track/09-new-trip-auto-record.png`,
  `img/gps-track/10-recovered-after-force-stop.png`.
* **Inspired by:** polarsteps' automatic route tracking (its docs describe a background tracker
  that fills the trip line between steps). **Changed:** recording is manual and visible
  (start / pause / stop, always with a notification), stored per trip as separate tracks, drawn
  apart from the planned route; no background-location permission, no automatic steps.

## 3c. Stop suggestions — "Gợi ý điểm dừng" (R17)

* **Where:** Trip detail, inside the "Hành trình GPS" card (§3b) right under the track list
  ("Hành trình đã ghi"), counted in the card's header; hidden when there is nothing to suggest. On the Trip detail header map and the Trip
  map each suggestion is also a marker: a white dot with an ink ring on a soft ink halo
  (track colour, so it reads as "from the track", distinct from the numbered step pins;
  05-map.md). Markers are not tappable.
* **Content:** "Gợi ý điểm dừng (N)", the hint "Nơi hành trình đã dừng từ 20 phút trở lên mà
  chưa có điểm dừng.", then one card per suggestion, oldest first: pin icon,
  "<day> · HH:mm–HH:mm", "Dừng 35 phút · 2 ảnh chụp lúc này" (only non-zero counts are shown),
  then " · N ảnh trong thư viện" when the app may read the phone's gallery (R19: gallery photos of
  the stop window not yet in the trip; omitted at 0 or without access), the place line
  "gần <nearest step title>, 9,7 km" when a step is within 5 km (track-distance format), else the
  nearest gazetteer place (R24, "gần Tên (Tỉnh), N km"), else the nearest step at any distance,
  else the coordinates; a neutral outline (not the track red); buttons "Bỏ qua" (text) and
  "Tạo điểm dừng ở đây" (tonal).
* **Tạo điểm dừng ở đây:** opens the Step form (§5) as a new step prefilled with "Điểm dừng HH:mm",
  the stay's date/time and centre (shown on the location card; "Đổi vị trí" opens the picker there),
  plus the section "Ảnh chụp trong lúc dừng" (§5, R19).
  Saving adds the step to the timeline as usual and the card disappears (a stored `added`
  decision); back without saving keeps the card.
  **Outside the trip's dates:** when the suggestion's day is before the trip's start or after its
  end, "Lưu" opens a dialog "Kéo dài chuyến đi?" with one of three bodies (R17): after the end —
  "Điểm dừng này sau ngày kết thúc chuyến đi (20 thg 10 – 23 thg 10, 2025). Kéo dài chuyến đi đến
  20 thg 10 – 27 thg 10, 2025?"; before the start — "Điểm dừng này trước ngày bắt đầu chuyến đi (…).
  Lùi ngày bắt đầu về …?"; open-ended trip — "Điểm dừng này trước ngày bắt đầu chuyến đi (từ 20 thg 10,
  2025, chưa có ngày kết thúc). Lùi ngày bắt đầu về …?" — with "Hủy" (text) and
  "Kéo dài và lưu". "Kéo dài và lưu" widens the start or end as little as needed and saves the
  step (R17, docs/04); "Hủy" closes the dialog, the editor stays open and the red R5 line
  ("Ngày đến phải trước hoặc bằng …") stays under the date. An ongoing trip (no end) is shown
  "từ <date>" and only its start can move. Editing or adding a step by hand never shows this
  dialog (R5 error only).
* **Bỏ qua:** the card disappears at once and stays hidden after restarts (stored `dismissed`).
* **Screens:** `img/stop-suggest/01-trip-detail-suggestions.png`,
  `img/stop-suggest/02-step-form-prefilled.png`, `img/stop-suggest/03-trip-with-new-step.png`,
  `img/stop-suggest/04-dismissed-after-restart.png`, `img/stop-suggest/05-trip-map-markers.png`,
  `img/extend-dates/dialog-after-end.png`, `img/extend-dates/dialog-before-start.png`,
  `img/extend-dates/after-cancel.png`, `img/extend-dates/after-extend.png`,
  `img/extend-dates/manual-edit-keeps-error.png`, `img/stop-photos/06-card-gallery-count.png`.
* **Inspired by:** polarsteps' step suggestions from location tracking (its docs describe
  suggestions after several hours of tracking). **Changed:** made from the user's own recorded
  tracks only (no background tracking), shown as a list on the trip with explicit add / dismiss,
  no place names (offline, no geocoding), our own thresholds (150 m, 20 min).

## 3d. Share trip — "Chia sẻ chuyến đi" (R18)

* **Where:** the share icon in the Trip detail top bar (before the map icon) and "Chia sẻ chuyến
  đi" in a trip card's ⋮ menu on the Trip list. Both open this screen for that trip.
* **Content, top to bottom:** top bar "Chia sẻ chuyến đi" with back; a two-option segmented
  control **"Ảnh dọc"** (photo icon, selected first) / **"Trang xem offline"** (article icon); one
  line explaining the selected output; the **live preview** filling the rest (spinner + "Đang tạo…" while it is made); an info line (page: "0,5 MB · 12 ảnh", "Đã lược bớt N ảnh để
  trang không quá 15 MB" when the cap removed photos, and "Không tải được nền bản đồ …" when the map
  had no basemap); for the page only, the row **"Ẩn ghi chú"** with a switch (off by default; the
  whole row toggles it) and "Không đưa ghi chú của các điểm dừng vào trang."; the privacy note
  (plain small text) "Tệp chia sẻ có bản đồ, tên, thời gian và vị trí các điểm dừng của bạn. Chỉ gửi cho người
  bạn tin."; buttons **"Lưu vào máy"** (outlined, download icon) and **"Chia sẻ"** (filled,
  share icon), both disabled until the file is ready.
* **Ảnh dọc** — preview of the 1080 × 1920 PNG itself (R18): topo paper background; title (Noto Sans
  Bold, ink, max 2 lines), dates; the map (960 × 820 px, 22 dp-equivalent corners on a 2 dp ledge) with the route, recorded tracks,
  numbered pins and the attribution; the km readout panel (Card with a ledge; the specimen's stats row: km, điểm dừng, ảnh, ngày as Barlow Condensed numbers over
  bold muted labels, then a signal-orange scale bar); one row of up to four square photos (they shrink to the room above the mark, left out below 110 px); the app's route mark (docs/08 §5) + "Lữ Trình" at the bottom.
* **Trang xem offline** — the generated HTML in a WebView (JavaScript and network off; a tap on a link does nothing in the preview): title, dates,
  description, the map, the stats, then the timeline "Ngày N · <weekday>, dd/MM/yyyy" with each
  step's number, name, time, note (unless hidden), a Forest chip link **"Dẫn đường"** (R42: Google Maps directions to the stop, opens in the reader's browser/Maps app) and photos; footer "Tạo bằng Lữ Trình, nhật ký du lịch trên điện thoại." Topo outdoor like the app (docs/08 §8): Parchment page, the dates as an eyebrow over a 28 px title, the map and the stats in Card panels with a 2 px ledge, one Card per step with a Trail / Waypoint / Forest number disc, Noto Sans and Barlow Condensed by name (nothing is loaded; the CSP stays).
* **Chia sẻ:** the system share sheet ("Sharing 1 file" + the file name) with the file as a
  `content://` URI (read permission granted to the receiving app).
* **Lưu vào máy:** Android 10+ saves a copy to `Pictures/LuTrinh` (image) or `Download/LuTrinh`
  (page) and shows "Đã lưu vào Pictures/LuTrinh"; Android 8–9 open the system "save as" picker
  with the file name.
* **Errors:** export failed → error icon + "Không tạo được tệp: <reason>"; trip deleted → "Không tìm
  thấy dữ liệu này…". No basemap (offline, area not downloaded) is not an error: the route is drawn on a plain
  background and the info line says so.
* **Screens:** `img/share-trip/01-share-screen-image.png`, `img/share-trip/02-share-screen-page.png`,
  `img/share-trip/03-share-sheet.png`, `img/share-trip/04-summary-hagiang.png` (the PNG itself),
  `img/share-trip/05-page-hagiang-chrome.png` (the HTML in desktop Chrome, network off),
  `img/share-trip/06-airplane-image.png`, `img/share-trip/07-no-basemap.png`,
  `img/share-trip/08-trip-list-card-menu.png`.
* **Inspired by:** polarsteps' trip sharing (docs/15 §4: a public trip link on its website, and a
  printed travel book). **Changed:** no account, link or server — the app makes files (an image
  and an offline page) and hands them to the system share sheet; nothing is published by us.

## 3f. Battery guide — "Ghi hành trình ổn định" (R22)

* **Opened from:** the link under the record control in the GPS card (§3b), and by itself once,
  right after the first recording starts (dismissable: Back or "Đã hiểu"). Opening it either way
  uses up the automatic showing (kept in `LocalPrefs`, key `battery_guide_seen`).
* **Content:** top bar "Ghi hành trình ổn định" with Back; one paragraph saying why (some phones
  stop background apps when the screen is off, so the track can break); a card **"Máy của bạn:
  <hãng>"** (highlighted) when the maker is Xiaomi/Redmi/POCO, Oppo/Realme/OnePlus, Samsung or
  Vivo/iQOO, with 3 steps in Vietnamese (battery mode "Không hạn chế"/background activity,
  auto-start, lock the app in recents); a card **"Các bước chung cho Android"** with 4 steps
  (battery "Không hạn chế", location "Chỉ khi dùng ứng dụng" + precise, do not swipe the app away
  while recording, switch the phone's battery saver off); the note that menu names vary; the
  button **"Mở cài đặt pin của Lữ Trình"** (system battery-optimisation list; the app's details
  page when a phone has no such screen) and **"Đã hiểu"**. Other makers see the general card only.
* **No exemption request:** the app never asks for `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; it
  only opens the settings page.
* **Screens:** `img/my-location/06-gps-card-guide-link.png`,
  `img/my-location/07-guide-auto-first-recording.png`, `img/my-location/08-system-battery-settings.png`
  (the button's target on the emulator).
* **Inspired by:** Polarsteps' battery-optimisation guidance for phones that kill background
  tracking (docs/22 §2.5). **Changed:** own wording for Vietnam's common phones, one screen with
  the device's section first, shown once, no permission requested.

## 4. Trip map — full screen

* **Content:** map (muted OpenFreeMap vector basemap, Vietnamese labels) with all pins + legs
  styled per stroke kind with a mode badge at each leg's middle (05-map.md), the recorded tracks
  (forest, under the badges and pins), stop-suggestion markers (§3c), the compact "Ghi hành trình" control at the top right
  (§3b), fitted to the whole
  route (camera rules) in the area above the card carousel, so no pin hides under a card; first
  pin trail green (start), last pin forest (end), the others waypoint blue; a horizontal carousel of step cards at the bottom (thumbnail, "1. Title",
  date · time; from card 2 on, the chip of the leg that arrives at this step, on card 1 the text "Điểm xuất phát"
  — every card has the same height); three square map buttons (44 dp map chips, docs/08 §5) stacked above the carousel's right end:
  **"Kiểu bản đồ"** (layers, R32, §4d) over **"Vị trí của tôi"** (crosshair, R22) over "Xem toàn bộ hành trình" (fit icon).
  Drafts (R20): a smaller hollow pin with a dashed outline and no number, no leg to or from it
  (the line joins the published steps around it); its card has no number and shows "Nháp" where the leg chip
  would be; start/end pins are the first/last published steps. `img/draft-cover/06-trip-map-draft-pin.png`
  (the draft was placed on Du Già's point, so the 9 → 10 leg ends under it).
* **"Vị trí của tôi" (R22):** tap → (first time: dialog "Dùng vị trí của bạn?" explaining that the
  position is taken only on tap, not recorded or sent; "Tiếp tục" → system prompt) → one fix: a waypoint-blue dot
  with a pale accuracy circle appears and the camera flies there (zoom ≥ 15, above the carousel).
  Tap again → a fresh fix and the camera re-centres. Refused → snackbar "Chưa được phép dùng vị trí của
  máy." with "Mở cài đặt" (app details); device location off → "Vị trí của máy đang tắt." with "Mở cài
  đặt" (location settings); no fix → "Chưa xác định được vị trí của bạn…". The dot is that tap's fix (not
  tracked), so it stays where it was while the user moves; it goes when the screen closes.
* **Behaviour:** tapping a pin selects it (larger, signal-orange ring + halo) and scrolls the carousel to
  its card; the carousel **snaps** so one card is centred (the first and last can reach the centre), and
  when a swipe settles the centred card's step becomes the selected one (pin highlighted, camera flies to it);
  tapping a card selects its step; every card has an arrow ›  (`TopoIcons.Next`, 22 dp, content description "Xem chi tiết") at the end of its title row and a 32 dp Sand square button with `TopoIcons.Navigate` ("Dẫn đường", R42) at the end of the leg row, and tapping the selected card opens Step detail. The leg chip has its own line under the title row and ends in "…" when "Xe máy · 77 km · ~4 giờ 06 phút" is wider than the card (260 dp);
  "Xem toàn bộ hành trình" clears the selection and refits the whole route above the cards; each
  selection moves the camera to that step (R37); tapping a badge, a leg line or a
  card's leg chip opens the Leg mode sheet, and the leg turns SignalDeep and is framed above the sheet. Empty
  trip: message "Chuyến đi chưa có điểm dừng nào để hiển thị."
* **Motion (R37):** the camera moves only once the cards stop: a swipe first snaps a card to the
  centre, then the camera glides (ease in-out, 400–1800 ms by distance and zoom change) to centre
  that step in the map area above the cards, zoomed to show the leg arriving there (≤ z13.5); a
  pin tap selects the pin at once, the carousel scrolls to its card, then the camera follows (our
  own scroll never counts as a swipe). The leg arriving at that step glows (a soft, wide
  signal-orange halo under the SignalDeep line); the others stay normal; nothing glows after
  "Xem toàn bộ hành trình". A small panel **"Ngày N/M"** at the bottom left above the cards
  (Barlow numerals; content description "Ngày N/M") gives the day of the trip of the camera's step
  (day 1 = the trip's start date; M = the trip's days, or up to its latest step while it has no
  end); the number rolls up when the day grows and down when it shrinks. Opening Step detail from
  the selected card grows the card into the screen (400 ms, the detail's content fades in after
  150 ms); Back shrinks the screen into the same card, and the trip map keeps the selection and
  camera it had. With system animations off ("Tỉ lệ thời lượng hình động" = 0) every camera move
  is a jump and the other motions are instant. Frames: `img/trip-motion/` (index below).
* **Screens:** `img/09-trip-map-route.png`, `img/16-sample-hagiang-map.png`,
  `img/20-trip-map-pins-above-cards.png`, `img/23-trip-map-leg-modes.png`,
  `img/24-trip-map-legs-zoomed.png`; "Vị trí của tôi": `img/my-location/01-trip-map-my-location.png`,
  `img/my-location/04-permission-explanation.png`, `img/my-location/05-permission-denied-snackbar.png`; before/after the route redraw: `img/route-redraw/`; the snapping,
  selecting carousel with the fit-all button: `img/ux-flows/01-trip-map-selected.png`. Pins 4 and 5 of the
  sample trip lie a few hundred metres apart, so they touch at the overview zoom; the selected pin is drawn on top
  and the carousel/pin tap zooms in (no pin clustering).
* **Inspired by:** docs/22 §1.1/§2 (map with step-card carousel, fit-to-steps camera, transport
  mode per leg) and docs/23 (route rendering: casing + shadow on ground legs, zoom-dependent
  widths, flight arc, simplification). **Changed:** straight legs (no snap-to-roads), one route
  colour (signal orange, SignalDeep when selected) instead of colour per trip, own stroke/badge per mode,
  great-circle flight arc with a sideways lift, own pin design (numbered discs, distinct
  start/end), no map-style switcher.

## 4b. Leg mode sheet — "Từ A đến B"

* **Opened from:** a leg chip on the Trip detail timeline or the Trip map cards, or a mode badge on
  the Trip map.
* **Content:** title "Từ <step> đến <step>", "Bạn di chuyển bằng gì? (N km đường chim bay)" (or,
  with a road route, "(N km theo đường đi, ~T)", R36), one
  row per mode (icon in its colour + name: Đi bộ, Xe máy, Ô tô, Xe buýt/xe khách, Tàu hỏa, Máy
  bay, Thuyền) and "Chưa chọn"; the current choice is marked. Tap → saved at once (R12), sheet
  closes; swipe down/outside → no change.
* **Leg note (R41):** above the modes (hairline between) a row "Thêm ghi chú chặng" (or "Ghi chú
  chặng" + the note, 2 lines) with a pencil; a tap turns the sheet into the editor: a field (hint
  "Ví dụ: đường dốc khó đi, gửi xe phía đối diện 30k", count "n/1000" or the error "Ghi chú chặng
  tối đa 1000 ký tự"), then "Xóa ghi chú" (only when a note exists), "Hủy" (back to the modes) and
  the Forest "Lưu" (saves, closes). An empty field saved = the note is removed.
* **Screen:** `img/25-leg-mode-sheet.png` (before R41); with the note: `img/leg-note/`.
* **Inspired by:** docs/22 (transport mode per leg). **Changed:** own list for Vietnam (xe máy,
  xe buýt/xe khách), a single bottom sheet instead of a separate screen, no automatic guess.

## 4c. My map — "Bản đồ của tôi"

Every map (this one, the trip map, step maps, the location picker, the share image) labels
Vietnam's archipelagos "Quần đảo Hoàng Sa" / "Quần đảo Trường Sa" and never shows another
country's administrative names there — Chinese, Philippine "Kalayaan", Malaysian, Taiwanese; only
Vietnamese ones remain (R30, docs/05; `img/sovereignty/`).

* **Purpose:** every trip on one map (R14).
* **Drafts (R20)** are not on this map: no pin, not in the lines, the totals or the card counts.
* **Content:** full-screen map; at the top a floating panel (22 dp, docs/08) with the compact km readout: the km total
  (sum of each trip's straight-line distance) with số chuyến đi and điểm dừng on its line, and the
  scale bar under it; at the bottom a carousel of trip cards (colour
  line in the trip colour, title, dates, "N điểm dừng · M km" in the numeral face; map card, 18 dp, chip shadow, the selected card a
  2 dp forest edge, no coloured side bar). Each trip is drawn in its own colour on a white
  casing with a soft shadow; recorded tracks of every trip are drawn on top in forest
  (05-map.md); the camera fits all trips between the totals card and the carousel.
* **Behaviour:** tap a trip's line or dots → that trip is selected (wider line, on top, card
  scrolled into view and highlighted); tap a card → Trip detail. No trips: "Chưa có chuyến đi nào để
  hiện trên bản đồ."
* **"Vị trí của tôi" (R22):** the same square map button at the bottom right above the carousel, same
  flow and messages as §4; the camera flies to the device (zoom ≥ 15) and the waypoint-blue dot is drawn above the
  trip lines. Screen: `img/my-location/02-my-map-my-location.png`.
* **"Kiểu bản đồ" (R32):** layers button above "Vị trí của tôi" (§4d). Screen:
  `img/map-style/04-my-map-satellite.png`.
* **Screen:** `img/26-my-map.png`.
* **Inspired by:** polarsteps' world map of all trips (docs/22). **Changed:** totals limited to
  what works offline — no countries/provinces (needs geocoding), no world-percentage.

## 4d. Map style — "Kiểu bản đồ" (R32–R34)

* **Entry:** the square layers map button (`TopoIcons.Layers`, content description "Kiểu bản
  đồ") on the trip map (§4), "Bản đồ của tôi" (§4c) and the location picker (§6).
* **Content:** modal bottom sheet titled "Kiểu bản đồ" with one row per basemap provider:
  * "Bản đồ" (map icon), subtitle "Dùng được offline với vùng đã tải";
  * "Vệ tinh" (satellite icon), subtitle "Cần mạng · không tải offline được".
  The current one has its radio button on.
* **Behaviour:** tapping a row stores the choice (`map_style`), closes the sheet, and switches
  every map at once. Route, pins, selection, camera and my location stay.
  * "Vệ tinh": Esri photos under the OpenFreeMap roads, borders and Vietnamese labels (white on
    dark).
  * The credit at the bottom right follows the style (R34).
  * Offline with "Vệ tinh" chosen: the maps show "Bản đồ" (downloaded areas work) and a
    snackbar says "Ảnh vệ tinh cần mạng" (R33). When the network returns, satellite comes back.
* **Not affected:** offline downloads and shared images always use "Bản đồ".
* **Screens:** `img/map-style/03-style-sheet.png`, `01-trip-map-satellite-ma-pi-leng.png`,
  `02-trang-an-satellite.png`, `06-offline-fallback-snackbar.png`,
  `07-truong-sa-satellite.png`, `08-hoang-sa-satellite.png`.

## 5. Step form — "Điểm dừng mới" / "Sửa điểm dừng"

* **Fields:** Tên địa điểm (required, n/80), Vị trí card (mini map + coordinates; "Chọn trên bản
  đồ" / "Đổi vị trí"), Ngày đến + Giờ đến (Material pickers, Vietnamese), Ghi chú (multi-line), "Ảnh & video (n)" with
  three tonal buttons **"Thêm ảnh/video"** (Photo Picker, photos and videos), **"Chụp ảnh"** (R28)
  and **"Quay video"** (R29), all disabled at 30 items or while importing; a one-row strip of
  thumbnails with a remove (×) button, first item marked "Ảnh bìa"; tapping a photo makes it the
  cover; hint "Chạm vào ảnh để đặt làm ảnh bìa. Nhấn giữ rồi kéo để đổi thứ tự." — **long-press +
  drag** moves a tile (R27: it lifts, the others slide aside, the strip scrolls at its edges; "Lưu"
  stores the order). A video tile shows a still with a play badge and its length ("▶ 0:08"). A
  refused video shows a red line under the strip: "Video dài 1:05: chỉ thêm được video tối đa 60
  giây (ứng dụng không cắt video).", "Video <size>: chỉ thêm được video tối đa 200 MB." or "Không
  đọc được video này."; no camera app: "Không mở được ứng dụng máy ảnh." **One grid:** photos picked from the gallery section below (R19) are
  part of it, badged "Từ thư viện", and counted in "Ảnh (n)" (n = saved photos + ticked gallery photos);
  removing a badged tile (×) unticks it. The Vị trí card shows the R-label "gần <điểm dừng>, N km"
  (Display conventions) with the coordinates as a smaller second line; "Đổi vị trí" / "Chọn trên bản đồ" is the
  same tonal-less text button here, in from-photos cards and in the picker flow. The time/date chips use the same
  colour. "Lưu" is in the top bar and as the bottom button, like the trip form; next to the bottom
  "Lưu" is **"Lưu nháp"** (outlined, R20: saves the step as a draft). Editing a draft shows "Nháp"
  in the top bar.
* **Defaults (new step):** R6 — date/time and the point where the picker opens. From a stop
  suggestion (§3c, R17): name "Điểm dừng HH:mm", the stay's date/time and centre.
* **Automatic name (R24):** each time the location is set (picker result, pin, "Vị trí của tôi",
  stop suggestion) and the name is blank, still "Điểm dừng HH:mm" or the name filled in last by
  R24, "Tên địa điểm" becomes the nearest gazetteer place ("Mã Pí Lèng Pass"); a name the user typed
  is never replaced. The Vị trí card label uses the gazetteer when no step of the trip is within
  5 km ("gần Tên (Tỉnh), N km"). Screens: `img/place-search/05-step-auto-name.png`,
  `06-step-typed-name-kept.png`, `07-step-pin-drop-auto-name.png`.
* **"Ảnh chụp trong lúc dừng" (only when opened from a stop suggestion, R19):** a card below
  "Ảnh (n)" (which already holds the ticked photos): photo-library icon, title, "Ảnh trong máy chụp trong khoảng 08:50–09:36 (từ 10 phút
  trước đến 10 phút sau lúc dừng)", then one of:
  * **No access yet:** "Lữ Trình có thể tìm trong thư viện ảnh của máy những ảnh bạn chụp lúc
    dừng ở đây. Ảnh chỉ được sao chép vào chuyến đi khi bạn bấm Lưu; không có gì được gửi đi."
    and the tonal button "Cho phép xem ảnh" → the system dialog (Android 14+: "Allow limited
    access / Allow all / Don’t allow"; 13: allow / don't allow; ≤ 12: storage).
  * **Denied:** "Chưa được phép xem thư viện ảnh. Bạn vẫn có thể chọn ảnh bằng nút “Thêm ảnh/video” ở mục Ảnh & video
    phía trên." and a text button "Cho phép xem ảnh" (asks again; when the system no longer shows the
    dialog nothing happens and the note stays). "Thêm ảnh/video" (Photo Picker) works as always.
  * **Access:** "Đã thêm x/y ảnh vào mục Ảnh ở trên; ảnh được sao chép khi bạn bấm Lưu." All start
    ticked and sit in the "Ảnh (n)" grid; the photos the user unticked (× on the tile) are listed
    here under "Chưa thêm — chạm để thêm lại:" as a 3-column grid of washed-out square thumbnails with their time
    ("09:12") and an empty circle; a tap ticks one again (checkbox semantics, label "Ảnh lúc HH:mm"). None in the window: "Không có ảnh nào
    trong máy chụp trong khoảng này."
  * **Partial access (Android 14+):** the grid shows only the photos the user allowed, then "Bạn
    chỉ cho phép xem một số ảnh." and "Chọn thêm ảnh" (re-opens the system dialog, where the user
    can select more photos or allow all; on Android 16 it reads "Allow Lữ Trình to access more
    photos and videos…" with "Allow limited access / Allow all / Don’t select more"). Photos
    added there arrive ticked.
  The section re-reads the gallery when the screen resumes (back from settings or the system
  sheet). The ticked photos are copied into the step on "Lưu" (after the regular photos, in the grid's order); until then
  the grid shows them straight from the gallery. Back without saving copies nothing.
* **Photo times:** each added photo's EXIF capture time is read before import and saved with the
  photo (`takenAt`, docs/06); nothing is shown for it on this screen.
* **Save:** top-bar "Lưu" and bottom button (published); "Lưu nháp" (draft, R20); errors per field
  for both. Edit mode also has delete.
* **Screens:** `img/06-step-create.png`, `img/11-step-edit.png`, `img/stop-photos/01-…09-*.png` (before the
  single grid), `img/ux-flows/02-step-form-from-suggestion.png`, `03-step-form-one-photo-grid.png`,
  `img/draft-cover/01-step-form-save-draft.png` ("Lưu nháp" | "Lưu"), `img/photo-video/01`, `02`, `05`–`08`
  (strip, drag, system camera, captured photo, video badges, refused video).
* **Inspired by:** docs/22 §1.3 (New/Edit Step: map header with edit pencil, name, arrival date +
  time, media box, note) and its "Publish / Save as draft" choice. **Changed:** the choice is two
  buttons on the form ("Lưu nháp" / "Lưu") instead of a sheet, publishing later is "Đăng" on step detail, location search offline in the picker (R23, bundled gazetteer, no API), suggested photos only for a step made
  from a stop suggestion (its window, not the whole day), no top spots/activities, default time 09:00 instead of 10:00, name filled from the
  bundled offline gazetteer (R24) instead of an online reverse geocoder.

## 6. Location picker — "Chọn vị trí"

* **Search (R23):** at the top an outlined field "Tìm địa danh: Đồng Văn, Tràng An…" (search icon,
  × clears). Typing (≥ 2 characters, diacritics optional: "dong van" finds "Đồng Văn") replaces the
  chips by a result card: up to 20 rows "Tên" / "Loại · Tỉnh" (former province in brackets when
  the query used it: "Tuyên Quang (trước đây Hà Giang)"), then the line "Tìm ngay trên máy, không
  cần mạng · Địa danh © OpenStreetMap (ODbL)". No match: "Không tìm thấy địa danh nào." While the
  gazetteer is still loading: "Đang tìm địa danh…". The keyboard's search key only hides the
  keyboard. A tap on a row moves the pin there, clears the field (chips return) and shows the
  place name above the coordinates on the bottom card; "Dùng vị trí này" returns the point with
  that name (R24) while the pin is still on it. Works in airplane mode.
* **Look (docs/08):** the search field and the chips float over the map as card-paper chips
  (12 dp, `Lift.Chip`); results panel 22 dp, `Lift.Panel`; bottom panel = `TopoPanel(floating)`
  with an eyebrow hint, the place name, coordinates in Barlow numerals and a forest button.
* **Content:** full map with a fixed pin in the centre; under the search field one row of chips — "Vị trí của tôi" first,
  then **this trip's other steps** by title in timeline order (the step being edited is left out;
  a trip with no other step shows only "Vị trí của tôi"); the other steps also as small context
  pins; bottom card "Kéo bản đồ để đặt ghim vào đúng chỗ", live coordinates, button "Dùng vị trí
  này". No online search (D4); the search above is offline (R23).
* **"Vị trí của tôi":** asks for approximate/precise location with a short rationale first ("Dùng vị
  trí của bạn?" · "Lữ Trình chỉ lấy vị trí một lần để đặt ghim ở chỗ bạn đang đứng. Vị trí không
  được ghi lại và không gửi đi đâu." · "Tiếp tục"), then reads **one** fix (no background location,
  nothing stored) and flies the pin there (zoom ≥ 11 like every camera move); while waiting "Đang tìm vị trí của bạn…". Denied,
  GPS off and timeout each show a snackbar ("Chưa được phép dùng vị trí của máy…", "Vị trí của máy
  đang tắt…", "Chưa xác định được vị trí của bạn…"); the user can still drag the map. A step chip
  flies to that step's point.
* **Opens at:** the step's current point; else the last step of the trip; else Vietnam overview.
* **"Kiểu bản đồ" (R32):** layers button at the right edge below the chips (§4d); satellite
  helps to place the pin on a building or a viewpoint. Screen: `img/map-style/05-picker-satellite.png`.
* **Screens:** `img/05-location-picker.png` (before search), `img/place-search/01-search-dong-van-offline.png`
  (airplane mode), `02-search-trang-an.png`, `03-search-former-province.png`, `04-picker-search-picked.png`.
* **Inspired by:** docs/22 §1.3 ("Search step location" map + pin; "Change step location" shows
  neighbours). **Changed:** drag-the-map-under-a-fixed-pin instead of tap-to-move, an offline
  gazetteer of Vietnam (OSM, bundled) instead of LocationIQ, plus "my location" + trip-step chips.

## 7. Step detail

* **Content:** cover photo (only when the step has photos), title, date · time, place line
  "gần <điểm dừng khác>, N km" with the coordinates as a smaller line (Display conventions; no other step within
  5 km: the gazetteer place, R24 "Tên, Tỉnh" / "gần Tên (Tỉnh), N km"; neither: the coordinates alone), **a Forest button "Dẫn đường" (`TopoIcons.Navigate`, R42) at the end of the place card**, note ("Chưa có ghi chú." when empty), mini map of the trip with this step highlighted, photo
  grid "Ảnh & video (n)" in the stored order (R27; a video cell = still + play badge + length, R29;
  the cover header is the first item's still when it is a video). **Draft (R20):** the title has no number and a card under it shows "Nháp", "Bản nháp chưa có
  trên lộ trình, thống kê, bản đồ của tôi và tệp chia sẻ. Bấm “Đăng” khi đã xong." and the button
  **"Đăng"** (publishes; the card goes, the step gets its number).
* **Actions:** pencil → Step form (edit); bin → "Xóa điểm dừng này?" (shows the photo count);
  tap photo → viewer at that index. **Bottom bar:** "‹ Trước: <title>" and "Sau: <title> ›" for the
  neighbouring steps in timeline order (a side without a neighbour is empty; none at all when the trip
  has one step); a tap replaces this screen with that step's detail, so Back still returns to the trip. The mini map's
  OpenStreetMap attribution keeps a fixed 9 dp text size so it stays a thin strip at any font scale.
* **Screens:** `img/10-step-detail.png`, `img/13-step-delete-confirm.png`, `img/ux-flows/04-step-detail-prev-next.png`,
  `img/draft-cover/05-step-detail-draft-publish.png`, `img/photo-video/03-reorder-saved-detail.png`,
  `09-step-detail-videos.png`.
* **Inspired by:** docs/22 §1.3 (step story with ⋮ Edit / Delete, delete confirm). **Changed:** a
  scrolling detail page instead of a full-screen story; delete is never blocked (no sync).

## 8. Photo viewer

* Black full screen pager over the step's photos, counter "2 / 3", close button; pinch to
  zoom (1×–5×), pan when zoomed, swipe to the next photo when not zoomed. Missing file →
  "Không tìm thấy tệp ảnh". Pages follow the stored order (R27).
* **Video page (R29):** the still until the player is ready, then the video plays full screen
  (fitted, centred) as soon as its page is current and pauses when swiped away; a tap shows the
  platform controls (play/pause, seek bar, "00:03 / 00:08"). No zoom on a video page.
* **Screens:** `img/12-photo-viewer.png`, `img/photo-video/04-reorder-viewer.png`, `10-viewer-video-playing.png`.

## 9. System Photo Picker (not our UI)

* Opened from the step form ("Thêm ảnh/video"), images and videos (R29), up to 30 per pick. Shows "Lữ Trình will only have access
  to the photos you select". **Screen:** `img/07-photo-picker.png`.

## 10. From photos — "Thêm từ ảnh"

* **Opens:** first a bottom sheet "Thêm điểm dừng từ ảnh" (what to pick: photos with a GPS location and a
  date; nothing is created until "Tạo điểm dừng"; originals untouched; the phone asks for the photo-location
  permission and, if refused, the photos still come in but need a place) with "Chọn ảnh" and "Hủy"
  (dismiss/Hủy return to the trip); only then, on Android 10+, the system permission dialog for photo location
  (`ACCESS_MEDIA_LOCATION`; on Android 14+ the "photos and videos" dialog), then the system
  **file picker** (multi-select, images) — not the Photo Picker, which hides GPS (06-photos.md).
  Cancelling the picker with nothing picked returns to the trip. While reading: progress.
* **Look (docs/08):** the map sits in an 18 dp ledge card; each suggestion is a `TopoCard` with a
  Waypoint-orange number disc (Barlow numerals), card-paper fields, `TopoIcons` on the date / place
  lines, a dimmed card when unticked, the photo count as an eyebrow; forest "Tạo điểm dừng" bar.
* **Review content:** a 180 dp map with the numbered kept suggestions; "N ảnh → M điểm dừng gợi
  ý" + hint; one card per suggestion (R13): checkbox (label "Giữ điểm dừng N"), number, editable
  title (the nearest gazetteer place of its photo GPS by R24, e.g. "Phố cổ Đồng Văn"; "Điểm dừng
  HH:mm" of its arrival when there is none or the card has no location yet; filled again when the
  card is placed or moved unless the user typed a title; R5 length check), date · time from the photos (or "Ảnh không có ngày
  chụp · dùng <R6 date>"), a red "Chụp ngày …, ngoài thời gian chuyến đi · không thể thêm" warning
  when outside the trip (cannot be kept), place line with "Chọn trên bản đồ" / "Đổi vị trí" → Location picker, thumbnails and
  "N ảnh". Photos without GPS form the last card "Ảnh không có vị trí", unticked until placed.
* **Back from the review** (system Back or the top-bar arrow) asks "Bỏ các điểm dừng gợi ý?" ("Chưa có
  điểm dừng nào được tạo từ N ảnh này. Thoát bây giờ sẽ bỏ phần đã gom.") with "Bỏ" / "Tiếp tục sửa"; nothing
  is saved either way. The place line of a card is "gần <điểm dừng>, N km" (existing steps of the trip)
  with the coordinates below, and the same "Đổi vị trí" button as the step form.
* **Confirm:** bottom button "Tạo N điểm dừng" (disabled while no card is kept or a kept card has
  no title/place) → imports the kept photos (R11), saves one step per card, back to Trip detail
  with a toast "Đã tạo N điểm dừng từ ảnh" (+ "Không đọc được M ảnh" when an import failed; such
  photos are skipped, the step is still made).
* **Screens:** `img/29-from-photos-review.png`, `img/30-from-photos-result.png`,
  `img/ux-flows/05-from-photos-intro.png`, `06-from-photos-discard.png`.
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
    "Vùng của chuyến đi quá rộng để tải một lần: ước tính <size>, <n> ô bản đồ; mỗi lần tải tối đa
    50 MB và 3.000 ô (điều khoản của OpenFreeMap). Hãy tải một vùng nhỏ hơn, ví dụ các vùng có sẵn ở
    trên." (limit R31; no download button)
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
| downloaded | ✓ "Đã tải · 26,3 MB" (stored size); the status is an Easy badge (green wash) with a check; every area is a topo card (card paper, 2 dp ledge) | "Xóa" → dialog "Xóa bản đồ offline “Hà Giang”?" / "Giải phóng tới 26,3 MB (phần dùng chung với vùng khác được giữ lại). …" / Xóa · Hủy |

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

## 12. GPX and backup — "Xuất GPX", "Nhập tệp GPX", "Sao lưu dữ liệu", "Khôi phục từ bản sao lưu" (R25, R26)

No screen of their own: menu entries, the system file picker and dialogs (`ui/transfer/TransferUi.kt`).

* **Entries:** trip card ⋮ and trip detail ⋮ → "Xuất GPX"; trip list ⋮ "Tùy chọn khác" → "Nhập
  tệp GPX", "Sao lưu dữ liệu", "Khôi phục từ bản sao lưu" (between "Bản đồ offline" and the debug
  "Tạo dữ liệu mẫu").
* **Export:** dialog "Xuất tệp GPX" / "Hành trình GPS đã ghi và các điểm dừng (không gồm nháp) của
  “<tên>”, đọc được bằng các ứng dụng bản đồ khác." / "Chia sẻ…" (share sheet) · "Lưu vào tệp…"
  (system "save as" with `<tên chuyến đi>.gpx`) → snackbar "Đã lưu tệp GPX".
* **Import:** system "open" picker → while reading a modal "Đang xử lý tệp GPX…" → the new trip
  opens, snackbar "Đã nhập “<tên>”: N điểm dừng, M điểm GPS". Error: dialog "Không nhập được tệp
  GPX" with the reason and "Không có gì được tạo." · "Đóng".
* **Backup:** system "save as" with `LuTrinh-saoluu-<ngày>.zip` → modal "Đang sao lưu…" → snackbar
  "Đã sao lưu 2 chuyến đi, 14 điểm dừng, 16 ảnh, 1 hành trình GPS (359 điểm) (0,3 MB)" (size as on the offline screen).
* **Restore:** system "open" picker → modal "Đang khôi phục…" while checking → dialog "Thay thế
  toàn bộ dữ liệu?" / "Bản sao lưu ngày <thứ, dd/MM/yyyy HH:mm> có: <counts>. Dữ liệu hiện có trên máy
  (<counts>) sẽ bị THAY THẾ hoàn toàn và không lấy lại được." / "Hủy" · "Thay thế" (error colour)
  → modal "Đang khôi phục…" → snackbar "Đã khôi phục <counts>"; the list reloads. Errors: dialog
  "Không khôi phục được" ending "Dữ liệu hiện tại không thay đổi." (not a backup / newer version
  "Hãy cập nhật ứng dụng" / damaged / recording / failed).
* The modal cannot be dismissed while working; the work continues if the screen rotates.
* **Screens:** `img/gpx-backup/01-trip-list-menu.png`, `img/gpx-backup/02-gpx-export-choice.png`,
  `img/gpx-backup/03-gpx-imported-trip.png`, `img/gpx-backup/04-gpx-malformed-error.png`,
  `img/gpx-backup/05-restore-confirm.png`, `img/gpx-backup/06-restored-step-photos.png`.
* **Inspired by:** Polarsteps has no GPX import or local backup (its data lives on its server);
  this is Lữ Trình's way out while it is local-only.

## 13. Settings — "Cài đặt" and the routing notice (R36)

* **Opened from:** trip list top ⋮ → "Cài đặt" (settings icon).
* **Content:** section "Bản đồ và chặng đi"; one row "Tính đường đi theo đường thực tế" with a
  switch (the whole row toggles; one TalkBack node) and its state text: on "Bật: chặng Xe máy, Ô
  tô, Xe buýt, Đi bộ (và chặng chưa chọn) được vẽ theo đường, km và thời gian là ước tính theo
  đường đi." / off "Tắt: mọi chặng là đường thẳng (chim bay), không gửi gì lên mạng."; under a
  hairline the privacy text (what is sent, to FOSSGIS in Germany, © OpenStreetMap ODbL).
* **Notice:** the first time a leg needs a road route while routing is on, a dialog "Vẽ chặng
  theo đường đi?" explains what is sent (stop positions and travel mode only; no names, notes or
  photos) and where; "Đã hiểu" keeps routing on, "Tắt tính đường" turns it off; either answer (or
  dismissing it) means it never shows again. Nothing is sent before it was shown.
* **Screens:** `img/road-routing/01-notice.png`, `05-settings-on.png`, `06-settings-off.png`.

## 14. Route code — "Nhập mã lộ trình", preview, download (R40)

* **Opened from:** trip list top ⋮ → "Nhập mã lộ trình", the empty list's "Có mã lộ trình?", or
  the route sheet's "Tải thành chuyến đi mới" (prefilled, looked up at once). While a download runs
  the entry opens on its progress.
* **Code** (top bar "Nhập mã lộ trình"): eyebrow "LỘ TRÌNH SOẠN SẴN", heading "Bạn có mã lộ
  trình?", one line on what happens; a large monospace-like field "Mã lộ trình" that upper-cases,
  drops spaces/dashes and draws `XXXX-XXXX`, hint "8 ký tự · không có chữ O, I, L, U và số 0, 1";
  errors under the field in the error colour (typed problems on the device, then the server's
  answers, R40); the Forest button "Xem lộ trình" (enabled only for a valid code; "Đang tìm lộ
  trình…" while asking); the privacy line under a hairline.
* **Preview** ("Xem trước"): the route line drawn on topo paper (no live map, no tiles), eyebrow
  "MÃ WFAZ-ME3M · PHIÊN BẢN 1", title, description, the stats panel (km · điểm dừng · ngày · ảnh ·
  video, Barlow numerals, scale bar), "Ngày bắt đầu" (date picker; default next Saturday),
  the checkbox "Tải luôn bản đồ offline vùng Hà Giang (~29,6 MB)", the size line, the Forest
  button "Tải về máy". Back returns to the code.
* **Download** ("Đang tải lộ trình"): title, a Card with the status ("Đang tải 32 % · 1,4 MB /
  4,3 MB", "Đã tạm dừng · 2,5 MB đã tải", "Đang chờ…", "Chưa tải được lộ trình" + the reason), a
  progress bar, the lines "Lộ trình, 9 điểm, 8 chặng" / "Ảnh và video 9 / 14" / "Bản đồ offline … ·
  sau khi tải xong", buttons "Tạm dừng"/"Tiếp tục" + "Hủy tải" (failed: "Thử lại", "Đóng" keeping the staged files, "Hủy tải"), the
  note that it continues in the background; offline a Forest strip "Mất mạng · 9/14 tệp · sẽ tự tải
  tiếp khi có mạng". When done the new trip opens with the snackbar "Đã thêm chuyến đi từ lộ trình".
* **Dialogs** (R38 grammar): "Cần có mạng để tải lộ trình", "Máy chủ đang bận, thử lại sau", "Hãy
  cập nhật ứng dụng để tải lộ trình này", "Không đủ bộ nhớ trống (cần …)", "Tải … bằng dữ liệu di
  động?" ("Vẫn tải").
* **On the trip page** (§3): under the title the Sand badge "Lộ trình WFAZ-ME3M · v1" ("· có bản
  mới" while a newer version is known); tap → the route sheet (version, "Đây là bản mới nhất đã
  biết.", "Nguồn ảnh và dữ liệu" credits) or, for a newer version, "CÓ BẢN MỚI · PHIÊN BẢN 2 · <date>",
  "Lộ trình đã được cập nhật", the change lines and note, "Tải thành chuyến đi mới", "Thay bản trong
  máy" (unedited only, else the line "Không thay được: bạn đã sửa chuyến đi này."), "Để sau". A
  closable Card banner "Lộ trình này đã ngừng chia sẻ" when the server stopped sharing it.
* **Screens:** `img/routes/01-trip-list-menu.png`, `02-code-empty.png`, `03-code-typo.png`
  (WFAZ-ME3K refused on the device), `04-preview.png`, `05-download-progress.png`,
  `06-download-paused.png`, `07-trip-from-route.png` (badge + snackbar), `08-badge-sheet.png`.
* **Inspired by:** the approved design page 05 (9 topo mockups). **Changed:** the preview draws the
  server's simplified `overview` line on paper instead of its `cover` photo (follow-up).

## 15. Add a place shared from Google Maps — "Thêm vào chuyến đi" (R43)

* **Opened from:** outside the app. In Google Maps, a place → "Share" → the system sheet lists
  **Lữ Trình** (`img/share-in/01-maps-share-sheet.png`). Any other text shared to Lữ Trình shows a
  toast "Lữ Trình chỉ nhận địa điểm chia sẻ từ Google Maps." and the app closes / goes back.
* **Content** (top bar "Thêm vào chuyến đi" + back): a Card with the eyebrow "ĐỊA ĐIỂM TỪ GOOGLE MAPS", the
  shared name (title; "Địa điểm chưa có tên" when none), the address when the text had one, and one
  status line: "Đang lấy vị trí…" with a small spinner while the short link is followed (≤ 12 s), then the
  coordinates, or "Liên kết không có tọa độ. Hãy chọn vị trí trên bản đồ." / "Không có mạng để đọc liên
  kết. Hãy chọn vị trí trên bản đồ.". Under it the eyebrow "CHỌN CHUYẾN ĐI" and one Card per trip (title,
  dates, counts, like the trip list) plus a Card "Chuyến đi mới". Cards are not tappable until the link has
  been followed. Nothing is saved on this screen (`img/share-in/02-pick-trip.png`).
* **A trip** → its detail opens, then the stop form "Điểm dừng mới" on top, prefilled with the place's
  name and location (`img/share-in/03-prefilled-form.png`); "Lưu" saves as ever (extend-dates question,
  R17, included) and back from the form lands on the trip (`img/share-in/04-stop-in-trip.png`).
  **"Chuyến đi mới"** → the new-trip form; once saved, the stop form follows for that trip.
* **No position** (offline, or Google's place-id answer missing or changed; a named place's link has no coordinates, but its place id gives the exact position first, R43 "Position by place id"): the
  offline gazetteer is searched with the name (and the address parts of the link, R43 "Guess by name"). **Guess
  found**: the place card's status line reads "Vị trí đoán theo tên — kéo ghim cho đúng"; the form has the
  name, the location = the guess and a Card "VỊ TRÍ ĐOÁN" with the same sentence, and the location picker (§6)
  opens once on top **centred on the guess** (zoom 13, needle on it) so the user drags the map to the right
  spot and taps "Dùng vị trí này" (`img/share-in/09-guess-picker.png`, `10-guess-form-note.png`,
  `11-guess-picker-offline.png`). The Card disappears once the position is no longer the guess. **No match**:
  the form has the name, a Card "CHƯA CÓ VỊ TRÍ" with the reason, and the picker opens at the default view
  so the user places the stop (`img/share-in/05-offline-pick-trip.png`, `06-offline-picker.png`,
  `07-offline-form-note.png`). Closing the picker leaves the form without a location when there is no guess
  ("Lưu" needs one); with a guess the guess stays. A dropped pin keeps its exact position and no note
  (`img/share-in/12-dropped-pin-keeps-position.png`).
* **Design:** Parchment, TopoCard + Eyebrow, no new tokens (docs/08).
* **Inspired by:** the captain's request (Google Maps' "Share" lists Lữ Trình). Polarsteps has no such entry.

## Screenshot index (`docs/img/`, Android emulator, Pixel 6 size)

### Gần tôi (`img/near-me/`, R45, §3)

Own AVD (pixel_6, android-36), debug build, `adb emu geo fix 105.3600 23.2800` (next to Phố cổ Đồng Văn), sample trip
"Vòng cung Hà Giang". 540 px wide.

| File | Shows |
| --- | --- |
| list.png | the list by road distance (the straight-line order of the same stops differs, e.g. Làng Lô Lô Chải 9,1 km chim bay but 28 km by road) |
| consent.png | "Gửi vị trí để tính đường?" while routing's notice was not accepted |
| permission-denied.png | permission refused: message + "Thử lại" |
| offline.png | airplane mode: "Chưa tính được đường" + "Thử lại" |

### Share from Google Maps (`img/share-in/`, R43, §15)

Own AVD (pixel_6, android-36, English system), debug build, the real Google Maps app for the share sheet; offline
shots with airplane mode on. 540 px wide.

| File | Shows |
| --- | --- |
| 01-maps-share-sheet.png | Google Maps' system share sheet listing Lữ Trình |
| 02-pick-trip.png | "Thêm vào chuyến đi": the place card and the trips to choose from |
| 03-prefilled-form.png | the stop form with the shared name and location |
| 04-stop-in-trip.png | the trip with the new stop at the shared place |
| 05-offline-pick-trip.png | the same screen offline: "Không có mạng để đọc liên kết…" |
| 06-offline-picker.png | offline: the location picker opened over the form |
| 07-offline-form-note.png | offline: the form with the name and the "CHƯA CÓ VỊ TRÍ" note |
| 08-not-maps-message.png | a text that is not a Google Maps place: the toast |
| 09-guess-picker.png | "Hoàn Kiếm Lake" (link without coordinates): the picker centred on the guessed lake (21.02883, 105.85254) |
| 10-guess-form-note.png | the form after closing that picker: the guess as location + the "VỊ TRÍ ĐOÁN" Card |
| 11-guess-picker-offline.png | offline, "Cột cờ Lũng Cú": the guess needs no network (the basemap is missing, as offline) |
| 12-dropped-pin-keeps-position.png | a dropped pin: exact position, no guess note |

### Leg note (`img/leg-note/`, R41)

Own AVD (pixel_6, android-36, English system), debug build; sample data, and a route downloaded by
code from a local stub server serving the spec sample with `arrivalNote` on two stops. 540 px wide.

| File | Shows |
| --- | --- |
| 01-leg-sheet.png | the leg sheet of Ninh Bình's Tràng An leg: "Thêm ghi chú chặng" above the modes |
| 02-leg-note-editor.png | the editor: field with a typed note, count, "Hủy" / "Lưu" |
| 03-reopened-leg-note-on-timeline.png | after a force-stop and reopening the trip: the note under the leg chip |
| 05-imported-leg-note-clamped.png | a trip downloaded by code: the long leg note clamped to 2 lines + "Xem thêm" |
| 06-imported-leg-note-expanded.png | the same note expanded, "Thu gọn" |

### Dẫn đường (`img/navigate-stop/`, R42)

| File | Shows |
| --- | --- |
| navigate-stop/01-step-detail.png | step detail: Forest "Dẫn đường" button under the place and coordinates |
| navigate-stop/02-trip-map-card.png | trip map: the Navigate square at the end of the leg row |
| navigate-stop/03-share-page.png | share page preview: a Forest "Dẫn đường" chip on every step (taps do nothing in the preview) |

### Route codes (`img/routes/`, R40)

Own AVD (pixel_6, android-36, English system), debug build, the real local route server
(`lutrinh-server` main @df870f7, `lutrinh seed-sample`, code WFAZ-ME3M) at `http://10.0.2.2:8000`;
the download shots went through a ~200 KB/s local proxy so the progress stays on screen. 540 px wide.

| File | Shows |
| --- | --- |
| 01-trip-list-menu.png | trip list top ⋮ with "Nhập mã lộ trình" |
| 02-code-empty.png | the code screen, empty, with the privacy line |
| 03-code-typo.png | WFAZ-ME3K: "Có thể gõ nhầm một ký tự…", "Xem lộ trình" disabled |
| 04-preview.png | preview of "Vòng cung Hà Giang 4 ngày": 170 km · 9 · 4 · 14, start Sat 14/11, offline map ticked |
| 05-download-progress.png | "Đang tải 26 %", route ready, media 9 / 14 |
| 06-download-paused.png | paused at 3,7 MB, the lines keep the last progress |
| 07-trip-from-route.png | the new trip: badge "Lộ trình WFAZ-ME3M · v1", road legs from the server, snackbar |
| 08-badge-sheet.png | the badge sheet: version 1, latest known, credits |

### Trip-map motion (`img/trip-motion/`, R37)

Own vi-VN AVD, sample trip "Vòng cung Hà Giang", `adb shell screenrecord` frames (60 fps, VFR)
tiled left → right.

| File | Shows |
| --- | --- |
| 01-swipe-settle-camera.png / .gif | swipe from card 1: the cards slide and snap first, then the camera glides to step 2, leg 1 → 2 glows, "Ngày 1/4" appears |
| 02-pin-tap-scrolls.png | pin 10 tapped: the pin is selected at once, the carousel scrolls to card 10, then the camera moves and leg 9 → 10 glows |
| 03-travelled-leg.png | step 10 focused: the arriving leg 9 → 10 glows, the other legs are normal; "Ngày 4/4" |
| 04-card-expand.png | selected card 10 tapped: the card grows into Step detail, content fades in, the mini map appears once the move ends |
| 05-card-collapse.png | Back: the detail shrinks into card 10; selection, camera and glow are kept |
| 06-day-flip.png | swipe from step 10 (day 4) to step 9 (day 3): "4" slides down and out, "3" comes in from above |
| 07-reduced-motion.png | animator duration scale 0: pin 10 tapped, the camera jumped (`CameraPlan: z8.84 0 ms`) |

### Topo outdoor, before/after (`img/topo/`, docs/08, batch 1)

The current look. Same sample data ("Tạo dữ liệu mẫu"), same scripted flow on a separate vi-VN
review AVD: `before-*` from main (6b6ad19, "Sổ bản đồ"), `after-*` from the topo-outdoor branch.
Batch 2 (`b2`) was shot against main d93bbab (batch 1 look) the same way.
540 px wide. **Every screenshot below the batch sections shows an older look** (atlas, or earlier):
they still document behaviour and layout, not colours, type or shapes; a later batch
(docs/09) replaces them screen by screen.

| File | Shows |
| --- | --- |
| specimen.png | The huashu-design specimen `06-topo-outdoor.html` (MIT) rendered in headless Chrome, 1200 × 750 |
| specimen-vs-app.png | Specimen beside the restyled trip list and trip detail |
| before-/after-empty.png | Trip list without trips: contour mark, heading, forest "Tạo dữ liệu mẫu" |
| before-/after-routing-notice.png | Routing notice (R36): card-paper dialog, 22 dp corners, forest button |
| before-/after-trip-list.png | Trip list: eyebrow + 28 sp heading, card-paper cards with a 2 dp ledge and a 12 dp photo thumbnail (contour cover when no photo), forest chip FAB |
| before-/after-menu.png | Trip list ⋮ menu on card paper |
| before-/after-settings.png | Settings "Cài đặt": routing switch in a ledge card |
| before-/after-trip-detail.png | Hà Giang detail: header map with the signal route, trail/waypoint/forest pins, stats panel (Barlow numerals, signal scale bar) |
| before-/after-trip-detail-2.png | Timeline: day eyebrows, step cards, leg chips with SignalDeep mode icons |
| before-/after-leg-sheet.png | Leg mode sheet "Bạn di chuyển bằng gì?": 22 dp panel, chosen mode on signal wash |
| before-/after-step-detail.png | Step detail: photo, 28 sp title, date/place in a ledge card, note, mini map with the selected pin |
| before-/after-dialog.png | Confirm-delete dialog (generic `ConfirmDeleteDialog`) |
| before-/after-step-edit.png, before-/after-step-edit-2.png | Step editor: card-paper fields, location card, media strip |
| before-/after-trip-map.png | Trip map: topo basemap repaint, signal legs on white casing, map chips, step carousel |
| before-/after-map-style.png | Map style sheet "Kiểu bản đồ" |
| b3-before-/b3-after-my-map.png | "Bản đồ của tôi" (batch 3): stats readout in a floating 22 dp panel, trip cards as map cards (18 dp, chip shadow, line key in the trip colour, numerals) |
| b3-before-/b3-after-offline.png | "Bản đồ offline": eyebrow group, `TopoCard` per area, forest "Tải về" |
| b3-after-offline-downloading.png, b3-after-offline-done.png | Offline area while downloading (6 dp progress bar, "Tạm dừng" / "Hủy tải") and stored (Easy badge + check, "Xóa") |
| b3-before-/b3-after-battery.png | "Ghi hành trình ổn định": battery mark, `TopoCard` sections with numbered discs, forest main button |
| b3-before-/b3-after-photo-viewer.png | Photo viewer: black stage, counter as a map chip, close as a map button |
| before-/after-b2-trip-new.png | Trip form "Tạo chuyến đi": card-paper fields, date buttons with the Calendar icon, forest save button |
| before-/after-b2-trip-new-error.png | Trip form with validation errors (red helper text under the fields) |
| before-/after-b2-trip-edit.png | Trip form "Sửa chuyến đi": cover row as a ledge card (thumbnail, eyebrow "Ảnh bìa", "Đổi ảnh bìa"), delete button |
| before-/after-b2-cover-picker.png | Cover sheet "Đổi ảnh bìa": "Tự động" tile as a forest veil, 2 dp forest selection ring with a check disc |
| before-/after-b2-picker.png | Location picker: search field and chips as floating chips, bottom panel with eyebrow hint, Barlow coordinates, forest button |
| before-/after-b2-picker-search.png | Place search results: 22 dp card-paper panel over the map, "Tên" / "Loại · Tỉnh" rows |
| before-/after-b2-picker-permission.png | "Dùng vị trí của bạn?" rationale dialog: signal location icon, forest "Tiếp tục" |
| before-/after-b2-from-photos-intro.png | "Thêm điểm dừng từ ảnh" sheet: signal photo icon, 22 dp padding, forest "Chọn ảnh" |
| before-/after-b2-from-photos-review.png | Review of photo suggestions: map in an 18 dp ledge card, numbered Waypoint discs, topo suggestion cards, forest "Tạo điểm dừng" bar |
| after-b2-from-photos-review-2.png | Same review scrolled: the "Ảnh không có vị trí" card and an excluded (dimmed) suggestion |

### Topo outdoor, batch 4: share and transfer (`img/topo/`, docs/09)

Same flow and AVD as above (Hà Giang sample, vi-VN); `before-` = main (d93bbab). 540-720 px wide.

| File | Shows |
| --- | --- |
| b4-before-/after-share-image.png | Share screen, "Ảnh dọc": segmented control, preview well (Sand, 18 dp, ledge) with the PNG on a 12 dp frame, no info icon. Before: the old preview shows the brand mark on top of the fourth photo |
| b4-after-summary.png | The 1080 × 1920 PNG itself (real file from `cacheDir/share`, 960 px): map on a ledge, stats panel (Barlow numbers over bold labels), four photos clear of the mark |
| b4-before-/after-share-page.png | Share screen, "Trang xem offline": the page in the preview; "Ẩn ghi chú" in a ledge card (the row toggles) |
| b4-after-page.png | The HTML page itself in desktop Chrome, network off (520 px wide, fonts fall back to the system's) |
| b4-before-/after-d10.png | "Xuất tệp GPX": Route mark in Signal, forest "Lưu vào tệp…" button, "Chia sẻ…" as text |
| b4-before-/after-d11.png | Busy dialog "Đang sao lưu…": Ink text (the spinner is frozen by the AVD's animation scale 0) |
| b4-before-/after-d12.png | "Thay thế toàn bộ dữ liệu?": Restore mark in Signal, destructive "Thay thế" in Error text |
| b4-before-/after-d13.png | "Không nhập được tệp GPX": error dialog, unchanged (already on the theme) |

### Topo outdoor, standardisation batch 3: space scale, transition, My map fit (`img/std-scale/`, docs/08 §4–§6a, R39)

Hà Giang sample on a review AVD (vi-VN); left = before (batch 2), right = after.

| File | Shows |
| --- | --- |
| 01–05 | trip list, trip detail, trip map, step detail, step editor: same look, paddings snapped to 4/8/12/16/24 |
| 06 | "Bản đồ của tôi" at fit-all: the route sits left of the button column |
| 07 / 08 | list → trip → step and back as frames (real speed; 4× slowed): 300 ms fade + 24 dp slide |

### Topo outdoor, standardisation batch 2: dialogs, buttons, states (`img/topo/std2-*`, docs/08 §6, R38)

Hà Giang sample on a review AVD (vi-VN), 540 px wide.

| File | Shows |
| --- | --- |
| std2-dlg-notice.png | Notice dialog (routing, R36): Signal icon, title 22, filled Forest main button |
| std2-dlg-confirm.png | Confirm dialog (delete trip): two text buttons, destructive "Xóa" in Error |
| std2-dlg-message.png | Message dialog (GPX import error): one text button |
| std2-mode-badges.png | Trip map: leg mode badges as 8 dp tiles, not circles |
| std2-media-row.png | Step editor: three media buttons on one row; × on a photo as an 8 dp rounded square |
| std2-dlg-stepdetail.png | Step detail: top bar "Điểm dừng 1", the title only as the heading |
| std2-empty-list.png | Trip list without trips: `MessageState`, title 22 |
| std2-empty-search.png / -stopphotos.png / -mymap.png | the three new empty states (compact `MessageState`): place search, stop photos, "Bản đồ của tôi" |

### Design atlas, before/after (`img/design-atlas/`, docs/08)

Same sample data, same flow, scripted on a separate review AVD: `before-*` from a main build
(3e4f484), `after-*` from the design-atlas branch. 540 px wide.

| File (before-… / after-…) | Shows |
| --- | --- |
| empty-state.png | Trip list without trips: icon disc → left-aligned type with a chàm rule |
| trip-list.png | Trip list, sample trips: generated drawings → real Commons photos (debug only), hairline cards |
| trip-list-no-cover.png | Ninh Bình trip without photos: gradient + icon → its route on a graticule (`RouteCover`) |
| trip-detail.png | Hà Giang detail: green/clay/gold → chàm route and pins, ink end pin and track; hairline stat cells, tabular km |
| step-detail.png | Step detail: mini map pins, bottom bar with a hairline instead of a shadow |
| step-form.png | Step form: field and chip colours from the palette |
| location-picker.png | Location picker (map tiles did not draw in the scripted run on either build; they draw on a separate AVD, see b3-after-location-picker.png) |
| location-picker-layers.png | Map style sheet "Kiểu bản đồ" on the picker: chàm selection, 12 dp radius |
| my-map.png | My map: coloured left bar on each card → a short legend line in the trip colour; chàm selection edge |
| offline-list.png | Offline maps list: terrace-green downloaded card → chàm wash; hairline cards |
| share-screen.png | Share screen with the portrait image preview |
| share-image.png | The 1080 × 1920 share PNG: cream/green → limestone paper, ink title, hairline stat cells |
| font-check.png | Vietnamese marks (ữ ằ ệ ỗ) in Be Vietnam Pro, Barlow Condensed, Newsreader, Source Serif 4, Literata |

B3, signature parts (docs/08 §5): `b3-before-*` from main at ecef295 (the B2 look above),
`b3-after-*` from the B3 branch, same data and flow on a separate AVD (emulator-5594), 540 px wide.

| File (b3-before-… / b3-after-…) | Shows |
| --- | --- |
| trip-list.png | Trip list: shadowed pill FAB "Chuyến đi mới" → flat chàm block (4 dp, no shadow), own icons (atlas, more, add) |
| trip-detail.png | Hà Giang detail: four stat tiles → "139 km đường chim bay" over a chàm scale bar (0 · 50 · 100), counts as a line of type; square "Xem bản đồ" button, flat FAB "Thêm", end pin as an ink square with an ink edge |
| step-detail.png | Step detail: own back/edit/delete icons, edged pins |
| share-screen.png | Share screen: segmented label "Ảnh dọc cho mạng xã hội" on two lines → "Ảnh dọc" on one line; 4 dp segmented/buttons; preview with the readout |
| share-image.png | 1080 × 1920 share PNG: hairline stat cells → km readout + scale bar; stock route glyph → the app's route mark before "Lữ Trình" |
| my-map.png | My map: three totals tiles → compact readout "149 km · 2 chuyến đi · 14 điểm dừng" + scale bar; square map buttons (layers, my location) |
| location-picker.png (after only) | Picker renders on a separate AVD; stock pin → own centre needle (its lower end is the point), own search icon |

Still Material (docs/08 §10): leg-mode badge glyphs and small in-row icons; satellite label halo.

Retaken in the atlas look (B4, separate AVD emulator-5594, debug sample data): 01–06, 08–21, 23–26.
Also retaken: trip-detail-declutter/*, share-trip/01, 02, 04, 08, ux-flows/01, 04, 07,
map-style/03, my-location/01, 02. The other feature folders still show the old look (docs/08 §10).
Still the old green/clay look: 07 (the system Photo Picker, no app UI), 22 and 29–30 (need pushed
EXIF photos), 27–28 (a historical upgrade from the v1 build).

| File | Shows |
| --- | --- |
| 01-trip-list-empty.png | first run, empty list |
| 02-trip-create.png | create form filled in |
| 03-trip-edit.png | edit form with changed title, cover row "Ảnh bìa · Tự động" |
| 04-trip-list.png | list with the two sample trips (refreshed after the UX base batch) |
| 05-location-picker.png | picker on the OpenFreeMap basemap from a new Hà Giang step: search field, trip-step chips, own centre needle |
| 06-step-create.png | the empty new-step form (name, location, date and time, note, photos) |
| 07-photo-picker.png | Android Photo Picker with 3 selected |
| 08-trip-detail.png | detail: header map with route, title plate, km readout "139 km đường chim bay" + scale bar, counts, collapsed GPS card, Ngày 1 |
| trip-detail-declutter/trip-detail-collapsed.png | Hà Giang detail: km readout "139 km đường chim bay" + scale bar, collapsed "Hành trình GPS · 1 hành trình · 2 gợi ý", "Ngày 1" visible, one FAB "Thêm", Be Vietnam Pro title |
| trip-detail-declutter/gps-card-expanded.png | the card open on an ended trip: hint, "Tạo chuyến mới và ghi", track row, suggestions |
| trip-detail-declutter/gps-card-scrolled.png | card just opened (scrolled to the top): title in the bar, FAB shrunk to "+", suggestion cards ("gần Mịch B (Tuyên Quang), 1,9 km") with "Tạo điểm dừng ở đây" |
| trip-detail-declutter/trip-map-ended.png | Hà Giang trip map: "Tạo chuyến mới và ghi" instead of "Ghi hành trình" |
| 09-trip-map-route.png | full trip map, numbered pins joined by the route, legs and track, card carousel |
| 10-step-detail.png | step detail with photo, mini map |
| 11-step-edit.png | edit step: mini map, location, date and time, note, photo with cover label |
| 12-photo-viewer.png | full-screen viewer 2 / 3 |
| 13-step-delete-confirm.png | delete step confirm |
| 14-photos-after-restart.png | trip detail after force-stop + relaunch, scrolled to the timeline: photos still there |
| 15-trip-list-sample.png | list with the debug sample trips |
| 16-sample-hagiang-map.png | Hà Giang loop at country zoom (pins/badges shrink, short legs drop their badges) |
| 17-trip-delete-confirm.png | delete trip confirm with counts |
| 18-trip-list-after-delete.png | list after the trip was deleted |
| 19-trip-validation.png | "Hãy đặt tên cho chuyến đi" on empty title |
| 20-trip-map-pins-above-cards.png | step 3 selected: camera flew to it above the cards, larger pin with a bright chàm halo, card 3 with a chàm edge |
| 21-date-picker-vietnamese.png | date picker in Vietnamese on an English-language emulator |
| 22-photo-exif-rotated-upright.png | 4800×3600 photo with EXIF orientation 6 + GPS, stored and shown upright |
| 23-trip-map-leg-modes.png | Hà Giang trip map fitted above the cards: cased chàm legs with mode badges (the sample is all xe máy), ink GPS track, square map buttons |
| 24-trip-map-legs-zoomed.png | zoomed to town scale near Quản Bạ: wider cased lines, the ink track beside the route |
| 25-leg-mode-sheet.png | leg mode sheet "Từ TP Hà Giang đến Cổng Trời Quản Bạ" over the trip detail, "Xe máy" ticked |
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
| gps-track/01-recording-notification.png | recording the Hà Giang trip: the notification with chronometer, distance, Tạm dừng / Dừng |
| gps-track/02-trip-detail-recording.png | trip detail while recording: "Đang ghi" control and the track list |
| gps-track/03-trip-map-track.png | Hà Giang trip map zoomed to TP Hà Giang: the recorded track (crimson, along the road, gap = a pause) next to the planned straight leg |
| gps-track/04-airplane-trip-map-track.png | airplane mode, app restarted: basemap from the offline pack + the track |
| gps-track/05-track-list-interrupted.png | after a force-stop while recording: the track ended, "Dừng do ứng dụng bị đóng khi đang ghi" |
| gps-track/07-stop-saved-snackbar.png | Stop on an ongoing trip (7 points, 1,0 km): snackbar "Đã lưu hành trình 1,0 km", the track row in the list |
| gps-track/08-stop-dropped-snackbar.png | Stop after one fix: snackbar "Hành trình quá ngắn (dưới 50 m) nên không được lưu", no new row |
| gps-track/09-new-trip-auto-record.png | "Tạo chuyến mới và ghi" from an ended trip → trip form → the new trip opens already recording |
| gps-track/10-recovered-after-force-stop.png | force-stop while recording 334 m, reopened: the track is kept as interrupted ("Dừng do ứng dụng bị đóng khi đang ghi"); a 1-fix recording killed the same way left no row |
| gps-track/06-my-map-track.png | "Bản đồ của tôi" zoomed to TP Hà Giang: the recorded track over the trip line |
| stop-suggest/01-trip-detail-suggestions.png | a track recorded with `emu geo fix` (drive, then 22 min at one spot) ended: "Gợi ý điểm dừng" on the trip detail |
| stop-suggest/02-step-form-prefilled.png | "Thêm điểm dừng" from a suggestion: step form with "Điểm dừng HH:mm", date/time and place prefilled |
| stop-suggest/03-trip-with-new-step.png | after saving: the new step in the timeline, that suggestion gone |
| stop-suggest/04-dismissed-after-restart.png | after "Bỏ qua" and a force-stop + restart: the dismissed suggestion still hidden |
| share-trip/01-share-screen-image.png | "Chia sẻ chuyến đi" for the Hà Giang sample: "Ảnh dọc" preview (map with route + track, stats, 4 photos) |
| share-trip/02-share-screen-page.png | same screen, "Trang xem offline": the HTML in the preview, size line, "Ẩn ghi chú" |
| share-trip/03-share-sheet.png | "Chia sẻ" → Android share sheet "Sharing 1 file" with the HTML |
| share-trip/04-summary-hagiang.png | the 1080 × 1920 summary PNG pulled from `Pictures/LuTrinh` |
| share-trip/05-page-hagiang-chrome.png | the summary HTML pulled from `Download/LuTrinh`, headless Chrome with network blocked |
| share-trip/06-airplane-image.png | airplane mode, app restarted: the image with the basemap from the offline pack |
| share-trip/07-no-basemap.png | airplane mode, Ninh Bình (outside the pack): route on the plain background + the note |
| share-trip/08-trip-list-card-menu.png | trip card ⋮ on the list: Sửa / Chia sẻ chuyến đi / Xuất GPX / Xóa (own icons) |
| stop-suggest/05-trip-map-markers.png | sample Hà Giang track on the trip map: suggestion markers (white dot, crimson ring and halo) |
| stop-photos/01-editor-explain-access.png | "Thêm điểm dừng" from the 09:05 suggestion: "Ảnh chụp trong lúc dừng" explains, "Cho phép xem ảnh" |
| stop-photos/02-system-permission-dialog.png | Android 16 dialog "Allow limited access / Allow all / Don’t allow" |
| stop-photos/03-preselected-grid.png | after "Allow all": the 4 photos pushed inside 08:55–09:50 (08:58, 09:12, 09:31, 09:47), all ticked; 08:40 and 10:30 not offered |
| stop-photos/04-one-unticked.png | 09:12 unticked: "Đã chọn 3/4" |
| stop-photos/05-no-double-offer.png | an overlapping suggestion (window 08:50–09:36) after saving: only 09:12 offered (08:58 and 09:31 are in the trip) |
| stop-photos/06-card-gallery-count.png | suggestion cards "… · 1 ảnh trong máy" / "· 0 ảnh trong máy" |
| stop-photos/07-access-denied.png | media permission revoked, "Don’t allow": the note and "Thêm ảnh" below |
| stop-photos/08-partial-access-grid.png | "Allow limited access", 08:58 and 09:12 selected in the system sheet: "Đã chọn 2/2", "Bạn chỉ cho phép xem một số ảnh.", "Chọn thêm ảnh" |
| stop-photos/09-partial-pick-more.png | "Chọn thêm ảnh" → "Allow limited access", 09:31 added: "Đã chọn 3/3" |
| ux-flows/01-trip-map-selected.png | trip map after a swipe: centred, selected card, highlighted pin, fit-all button (older card layout; the current one is `std-map/01`) |
| std-map/01-hagiang-cards-1-5.png, 02-hagiang-cards-6-10.png | every card of the Hà Giang sample: arrow in the title row, leg chip on its own line (ellipsis on the long ones) |
| std-map/03-hagiang-fit-all.png, 04-ninhbinh-fit-all.png | full trip map at fit-all: the route ends left of the map-button column |
| std-map/05-credit-one-line.png | trip detail mini map: the whole credit on one line, OSM named once |
| std-map/06-return-to-map-frames.png | frames of map → step detail → back: paper under the map, no dark frame |
| ux-flows/02-step-form-from-suggestion.png | step form from a stop suggestion: "gần …" place line, name "Điểm dừng 09:05", one "Ảnh (3)" grid |
| ux-flows/03-step-form-one-photo-grid.png | the same form scrolled: gallery photos badged "Từ thư viện" and the card below ("Đã thêm 3/3 ảnh…") |
| ux-flows/04-step-detail-prev-next.png | step detail with "Trước: …" / "Sau: …" bottom bar and the "gần …" place line |
| ux-flows/05-from-photos-intro.png | "Thêm điểm dừng từ ảnh" intro sheet before the file picker |
| ux-flows/06-from-photos-discard.png | "Bỏ các điểm dừng gợi ý?" after Back from the review |
| ux-flows/07-location-picker-chips.png | location picker: "Vị trí của tôi" + the trip's steps as chips |
| my-location/01-trip-map-my-location.png | trip map after "Vị trí của tôi" with `geo fix 105.85 21.03`: chàm dot, camera on Hà Nội, the stacked square map buttons |
| my-location/02-my-map-my-location.png | Bản đồ của tôi, same fix |
| my-location/03-accuracy-circle-80m.png | same, from a temporary build forcing accuracy = 80 m (the emulator's fixes carry none) to check the accuracy circle's size |
| my-location/04-permission-explanation.png | permission revoked: "Dùng vị trí của bạn?" |
| my-location/05-permission-denied-snackbar.png | "Don’t allow" in the system prompt: snackbar "Chưa được phép dùng vị trí của máy." + "Mở cài đặt" |
| my-location/06-gps-card-guide-link.png | opened GPS card: record button and the "Ghi hành trình ổn định" link |
| my-location/07-guide-auto-first-recording.png | the guide opened by itself at the first recording (Google emulator = general steps only) |
| my-location/08-system-battery-settings.png | "Mở cài đặt pin của Lữ Trình" → system "App battery usage" |
| ux-flows/08-from-photos-review.png | from-photos review: names "Điểm dừng HH:mm", "gần Phố cổ Đồng Văn, 0,2 km" with coordinates below, "Đổi vị trí" |
| draft-cover/01-step-form-save-draft.png | step form (R20): "Lưu nháp" next to "Lưu", one photo added |
| draft-cover/02-timeline-draft-badge.png | Hà Giang timeline, day 4: the draft (hollow pencil dot, muted, "Nháp"), the Xe máy leg Mèo Vạc → Du Già skips it |
| draft-cover/03-stats-unchanged-by-draft.png | trip detail with the draft saved: tiles still 10 điểm dừng · 12 ảnh · 139 km · 4 ngày |
| draft-cover/04-share-image-unchanged.png | "Chia sẻ chuyến đi" with the draft saved: 10 · 12 · 139 · 4, no pin and no photo of the draft |
| draft-cover/05-step-detail-draft-publish.png | step detail of the draft: "Nháp" card with the hint and "Đăng" |
| draft-cover/06-trip-map-draft-pin.png | trip map, draft card selected: dashed hollow pin, card with "Nháp" |
| draft-cover/07-trip-list-draft-count.png | trip list card "10 điểm dừng · 12 ảnh · 1 nháp" |
| draft-cover/08-timeline-after-publish.png | after "Đăng": the step is number 10 with its legs, no badge (the tiles above then read 11 điểm dừng · 13 ảnh) |
| draft-cover/09-cover-picker.png | trip detail ⋮ "Đổi ảnh bìa" (R21): "Tự động" selected, the trip's photos below |
| draft-cover/10-trip-list-chosen-cover.png | trip list card with the chosen cover (Cột cờ Lũng Cú photo) |
| draft-cover/11-trip-form-cover-row.png | "Sửa chuyến đi": row "Ảnh bìa · Đã chọn · Đổi ảnh bìa" |
| draft-cover/12-cover-picker-chosen.png | the cover sheet from the form: the chosen photo ringed and ticked |
| draft-cover/13-trip-list-auto-cover.png | after "Tự động" + "Lưu": the card shows the automatic cover again |
| place-search/01-search-dong-van-offline.png | picker search (R23) in airplane mode: "dong van" → Đồng Văn (Xã · Tuyên Quang) first |
| place-search/02-search-trang-an.png | "trang an" → Tràng An (Ninh Bình) first |
| place-search/03-search-former-province.png | "ha giang" → Tuyên Quang "trước đây Hà Giang", then Hà Giang city (Tuyên Quang) |
| place-search/04-picker-search-picked.png | "ma pi leng" picked: pin on Mã Pí Lèng Pass, its name on the bottom card |
| place-search/05-step-auto-name.png | new step after "Dùng vị trí này": "Tên địa điểm" = Mã Pí Lèng Pass (R24) |
| place-search/06-step-typed-name-kept.png | typed name "Nha nghi cua toi" kept after the pin moved |
| place-search/07-step-pin-drop-auto-name.png | name cleared, pin dragged: the name is filled again from the nearest place |
| small-fixes/01-backup-done-photos-and-video.png | the backup snackbar: "16 ảnh · 1 video" (R26) |
| small-fixes/02-restore-confirm-photos-and-video.png | restore confirmation, photos and videos counted apart (R26) |
| small-fixes/03-trip-map-video-play-mark.png | trip map stop card whose first item is a video: still frame with ▶ (R29) |
| small-fixes/04-search-deo-ma-pi-leng.png | place search "ma pi leng": "Đèo Mã Pí Lèng" (R23) |
| gpx-backup/01-trip-list-menu.png | trip list ⋮ "Tùy chọn khác": Bản đồ offline / Nhập tệp GPX / Sao lưu dữ liệu / Khôi phục từ bản sao lưu / Tạo dữ liệu mẫu (debug) |
| gpx-backup/02-gpx-export-choice.png | "Xuất GPX" on the Hà Giang card: dialog "Xuất tệp GPX" with "Chia sẻ…" and "Lưu vào tệp…" |
| gpx-backup/03-gpx-imported-trip.png | the exported Hà Giang file imported (R25): a new trip, 10 steps, the GPS track (359 points) on the header map |
| gpx-backup/04-gpx-malformed-error.png | a truncated GPX: "Không nhập được tệp GPX … (lỗi đọc ở dòng 168). Không có gì được tạo." |
| gpx-backup/05-restore-confirm.png | "Thay thế toàn bộ dữ liệu?" with the backup's and the current counts (R26) |
| gpx-backup/06-restored-step-photos.png | after the restore: Cổng Trời Quản Bạ with its 2 photos |
| photo-video/01-strip-before-reorder.png | "Sửa điểm dừng" Đèo Mã Pí Lèng: "Ảnh & video (3)", the three add buttons, strip and drag hint (R27) |
| photo-video/02-reorder-dragging.png | the last photo lifted and dragged to the front |
| photo-video/03-reorder-saved-detail.png | after "Lưu", Back and reopen: step detail grid in the new order |
| photo-video/04-reorder-viewer.png | the viewer at "3 / 3" following the new order |
| photo-video/05-system-camera.png | "Chụp ảnh": the system camera app (emulated scene) opened by the app (R28) |
| photo-video/06-captured-photo-strip.png | the captured photo in the strip of a new step |
| photo-video/07-strip-video-badges.png | strip with a photo (cover) and two videos "▶ 0:03" / "▶ 0:08" (R29) |
| photo-video/08-video-too-long.png | a picked 65 s video refused: "Video dài 1:05: chỉ thêm được video tối đa 60 giây (ứng dụng không cắt video)." |
| photo-video/09-step-detail-videos.png | step detail "Ảnh & video (n)" with video cells |
| photo-video/10-viewer-video-playing.png | a video playing full screen, controls at 00:03 / 00:08 |
| photo-video/11-restore-with-videos.png | restoring a v7 backup with videos: "Thay thế toàn bộ dữ liệu?" 19 vs 18 ảnh (R26) |
| road-routing/01-notice.png | R36 one-time notice "Vẽ chặng theo đường đi?" right after the sample data was seeded |
| road-routing/02-hagiang-detail-road-km.png | Hà Giang trip: "264 km theo đường đi, có chặng chim bay", "~10 giờ 20 phút di chuyển (ước tính, chưa tính 2 chặng)", leg chip "Xe máy · 48 km · ~1 giờ 33 phút" (27 km straight) |
| road-routing/03-hagiang-trip-map-roads.png | trip map: legs follow the roads, GPS track (ink) on top, router credit "Đường đi: Valhalla · FOSSGIS · © OpenStreetMap (ODbL) · Sửa bản đồ"; 3→4 (Lũng Cú) stays straight: Valhalla has no motor_scooter path there |
| road-routing/04-share-image-roads.png | share image: road legs and the router credit with the fixthemap address |
| road-routing/05-settings-on.png | "Cài đặt", routing on |
| road-routing/06-settings-off.png | "Cài đặt", routing off |
| road-routing/07-routing-off-straight.png | routing off: every leg straight, no router credit, 0 requests |
| road-routing/08-not-chosen-routed-as-motorbike.png | a "Chưa chọn" leg: "Chọn cách di chuyển · 9,1 km · ~35 phút" (routed like Xe máy) |
| road-routing/09-airplane-new-leg-chim-bay.png | airplane mode: the leg just set to Đi bộ shows "4,1 km chim bay" and is queued |
| road-routing/10-airplane-stored-routes-draw.png | airplane mode: stored routes still draw ("Không có mạng" banner), the new leg straight |
| road-routing/11-online-leg-fetched.png | back online: the queued leg is fetched, "Đi bộ · 9,1 km · ~2 giờ 56 phút" |
| road-routing/16-ninhbinh-all-legs-routed.png | Ninh Bình sample: all three legs along the road ("Ô tô · 4,4 km · ~11 phút", "Xe máy · 4,7 km · ~16 phút", "Đi bộ · 5,0 km · ~1 giờ 03 phút"), no "chim bay" |
| road-routing/17-ninhbinh-preview-all-pins.png | trip detail header map of Ninh Bình: pins 1–4 and the route inside the frame, clear of the credit and the expand button |
| road-routing/18-hagiang-preview-all-pins.png | the same for Hà Giang (10 pins) |
| road-routing/13-lung-cu-car-route-chip.png | Yên Minh → Lũng Cú: no motorbike route, the car route is drawn and measured, chip "Xe máy · 59 km · ~1 giờ 43 phút · tuyến ô tô" |
| road-routing/14-lung-cu-car-route-sheet.png | the same leg's sheet: "(59 km theo đường đi, ~1 giờ 43 phút · tuyến ô tô)" |
| road-routing/15-lung-cu-car-route-trip-map.png | trip map: the Lũng Cú leg follows the road (car route) |
| sovereignty/before-hoang-sa-z8.png | before R30 (main APK), "Bản đồ của tôi" at z8 over the Paracels: Chinese admin labels "Tam Sa", "Yongle Qundao" |
| sovereignty/before-truong-sa-z7.png | before R30, z7 over the Spratlys: "Quận Nam Sa", "Zhubi" |
| sovereignty/after-hoang-sa-z8.png | R30: same view, Chinese admin labels hidden, "Quần đảo Hoàng Sa" drawn |
| sovereignty/after-truong-sa-z7.png | R30 as first shipped: same view, "Quần đảo Trường Sa"; the Philippine "Kalayaan" still showed (hidden since the claimants rule, below) |
| sovereignty/claimants-truong-sa-z7.png | R30 claimants rule, z7 over the Spratlys (fix 10.3 N 113.5 E): no "Kalayaan" (11.05 N 114.29 E), no "Quận Nam Sa"; "Quần đảo Trường Sa" |
| sovereignty/claimants-truong-sa-z10.png | z10 around Thitu (Pag-asa) island, bottom: no "Kalayaan"/"Pag-asa"; "Đảo Đá Nam" shown |
| sovereignty/claimants-hoang-sa-z8.png | z8 over the Paracels: no Chinese or other admin name; "Quần đảo Hoàng Sa" |
| sovereignty/after-overview-z5.png | R30 at z5: Việt Nam, Biển Đông, "Quần đảo Hoàng Sa" and "Quần đảo Trường Sa" |

## Accessibility and UI tests

* Every ⋮ names what it opens: "Tùy chọn: <tên chuyến đi>" on a trip card, "Tùy chọn khác" on the
  top bars (a screen reader would otherwise read the same label for every card).
* The extended FABs ("Chuyến đi mới", "Thêm điểm dừng") and the icon buttons "Thêm từ ảnh" and
  "Bản đồ của tôi" carry their label as content description:
  Material 3 1.3 exposes no text for them, so TalkBack would only say "Button".
* Instrumented Compose tests (`app/src/androidTest/.../ui/TripFlowsTest.kt`) drive the real app
  on a device: create trip, edit trip, delete trip, add a step with a trip-step chip of the location picker ("Mã Pí Lèng", next to "Vị trí của tôi"), set a leg's mode to "Xe máy" from the timeline (checked in the database), open "Bản đồ
  của tôi" from the list, open "Bản đồ offline" from the list menu (ready-made regions listed) and
  from a trip map ("Tải vùng của chuyến đi này"), check that a trip shows the "Ghi hành
  trình" button (not tapped: it would raise the system location prompt), and open a trip with the
  sample track: "Gợi ý điểm dừng (3)", "Bỏ qua" one → "(2)" and a stored decision, open "Thêm điểm dừng" from a suggestion with gallery access granted (the section "Ảnh chụp trong
  lúc dừng" lists the stop's photos or says there are none, no "Cho phép xem ảnh"), and open
  "Chia sẻ chuyến đi" from a trip (both "Ảnh dọc" and "Trang xem offline"; the page shows "Ẩn ghi chú"). They find nodes by the Vietnamese texts/labels above (changing a string means updating the
  test) and delete every trip whose title starts with `UITEST` afterwards. Run with
  `./gradlew connectedDebugAndroidTest` (needs network for tiles only cosmetically; the picker
  centre updates without tiles).
