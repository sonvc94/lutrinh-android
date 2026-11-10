# 09 — Topo-outdoor redesign inventory

Every piece of UI of Lữ Trình and whether it follows the topo-outdoor spec (`docs/08-design.md`)
after batch 1 (branch `mate/lutrinh-topo-system`), batch 2 (`mate/lutrinh-topo-b2`) and batch 3 (`mate/lutrinh-topo-b3`), 2026-11-10. Paths are under
`app/src/main/java/vn/lutrinh/app/`.

Status:
* **on spec**: redone in the topo style, checked on screenshots (`docs/img/topo/`).
* **theme**: a stock Material component with nothing of its own; it takes the topo look from
  the theme (Card paper, 22 dp, Forest buttons, Noto Sans), so it is on spec without code.
* **tokens only**: the new colours, fonts and radii reach it through the theme, but its layout
  still has the old look (hairline cards, stock icons, own sizes); to be redone in the batch named.

A later batch removes its files from `DesignGuardTest.notYetTopo` (the guard then holds them to
TopoIcons and the type weights) and adds before/after shots to `docs/img/topo/`.

## Screens

| # | Screen | File | Status | Batch |
| --- | --- | --- | --- | --- |
| S1 | Trip list "Chuyến đi của tôi" (+ empty state) | `ui/trips/TripListScreen.kt` | on spec | 1 |
| S2 | Trip detail (header, km readout, timeline, leg chips, GPS card, stop suggestions, record control) | `ui/trips/TripDetailScreen.kt`, `ui/trips/LegUi.kt`, `ui/tracks/GpsCard.kt`, `ui/tracks/StopSuggestionUi.kt`, `ui/tracks/TrackUi.kt` | on spec | 1 |
| S3 | Trip map (carousel, map buttons, offline chip) | `ui/trips/TripMapScreen.kt`, `map/MapViews.kt`, `map/OfflineIndicator.kt` | on spec (standard 1: arrow in the card title row, chip on its own line, 72 dp right fit for the buttons, day chip `EaseInOut`; `img/std-map/`) | 1 |
| S4 | Step detail | `ui/steps/StepDetailScreen.kt` | on spec | 1 |
| S5 | Step editor (fields, location card, media strip, gallery photos R19) | `ui/steps/StepEditScreen.kt`, `ui/steps/MediaStrip.kt`, `ui/steps/StopPhotosUi.kt` | on spec | 1 |
| S6 | Settings "Cài đặt" | `ui/settings/SettingsScreen.kt` | on spec | 1 |
| S7 | Trip create / edit (cover, dates) | `ui/trips/TripEditScreen.kt` | on spec | 2 |
| S8 | Location picker + place search | `ui/picker/LocationPickerScreen.kt`, `ui/picker/PlaceSearchUi.kt`, `ui/common/PlaceUi.kt` | on spec | 2 |
| S9 | Add from photos "Thêm từ ảnh" | `ui/steps/FromPhotosScreen.kt` | on spec | 2 |
| S10 | My map "Bản đồ của tôi" | `ui/trips/MyMapScreen.kt` | on spec | 3 |
| S11 | Offline maps "Bản đồ offline" | `ui/offline/OfflineMapsScreen.kt` | on spec | 3 |
| S12 | Battery guide "Ghi hành trình ổn định" | `ui/tracks/BatteryGuideScreen.kt` | on spec | 3 |
| S13 | Photo viewer (black, full screen) | `ui/photos/PhotoViewerScreen.kt` | on spec | 3 |
| S14 | Share trip "Chia sẻ" | `ui/share/ShareTripScreen.kt` | on spec | 4 |

## Dialogs and popups

| # | Dialog | File (function) | Status | Batch |
| --- | --- | --- | --- | --- |
| D1 | Routing notice "Vẽ chặng theo đường đi?" (R36) | `ui/settings/SettingsScreen.kt` (`RoutingNoticeDialog`) | on spec | 1 |
| D2 | Confirm delete (trip, step, offline area) | `ui/common/Common.kt` (`ConfirmDeleteDialog`) | theme (on spec) | 1 |
| D3 | Discard changes "Bỏ các thay đổi?" | `ui/common/Common.kt` (`DiscardDialog`) | theme (on spec) | 1 |
| D4 | Location permission for "Vị trí của tôi" | `ui/common/MyLocation.kt` (`MyLocationButton`) | theme (on spec) | 1 |
| D5 | Track permission + "open settings" (R16) | `ui/tracks/TrackUi.kt` (`askNotificationsThenStart`, `SettingsDialog`) | theme (on spec) | 1 |
| D6 | Extend trip dates (step outside the trip) | `ui/steps/StepEditScreen.kt` (`capture`) | theme (on spec) | 1 |
| D7 | Time picker "Giờ đến" | `ui/steps/StepEditScreen.kt` (`TimePickDialog`) | theme (on spec) | 1 |
| D8 | Date picker (trip dates, "Ngày đến") | `ui/trips/TripEditScreen.kt` (`DatePickDialog`) | theme (on spec) | 1 |
| D9 | Picker location permission | `ui/picker/LocationPickerScreen.kt` (`locate`) | on spec | 2 |
| D10 | GPX export choice | `ui/transfer/TransferUi.kt` (`rememberTransfer`) | on spec | 4 |
| D11 | Busy (export/backup progress) | `ui/transfer/TransferUi.kt` (`rememberTransfer`) | on spec | 4 |
| D12 | Restore confirm | `ui/transfer/TransferUi.kt` (`rememberTransfer`) | on spec | 4 |
| D13 | Transfer error | `ui/transfer/TransferUi.kt` (`ErrorDialog`) | on spec | 4 |

## Bottom sheets

| # | Sheet | File | Status | Batch |
| --- | --- | --- | --- | --- |
| B1 | Leg mode "Bạn di chuyển bằng gì?" | `ui/trips/LegUi.kt` (`LegModeSheet`) | on spec | 1 |
| B2 | Map style "Kiểu bản đồ" | `ui/common/MapStyle.kt` (`MapStyleButton`) | on spec | 1 |
| B3 | Cover picker | `ui/trips/CoverPicker.kt` (`CoverPickerSheet`) | on spec | 2 |
| B4 | From-photos intro | `ui/steps/FromPhotosScreen.kt` (`FromPhotosIntro`) | on spec | 2 |

## Menus

| # | Menu | File | Status | Batch |
| --- | --- | --- | --- | --- |
| M1 | Trip list ⋮ (offline, GPX import, backup, restore, settings, sample data) | `ui/trips/TripListScreen.kt` | on spec | 1 |
| M2 | Trip card ⋮ (edit, delete) | `ui/trips/TripListScreen.kt` (`TripCard`) | on spec | 1 |
| M3 | Trip detail ⋮ (edit, cover, add step, from photos, GPX export) | `ui/trips/TripDetailScreen.kt` | on spec | 1 |
| M4 | Trip detail "Thêm" FAB menu | `ui/trips/TripDetailScreen.kt` | on spec | 1 |

## Snackbars and toasts

| # | Message | File | Status | Batch |
| --- | --- | --- | --- | --- |
| N1 | Snackbars (deleted / undo, saved, track, basemap fallback R33, my location, transfer results) | `ui/trips/TripListScreen.kt`, `ui/trips/TripDetailScreen.kt`, `ui/trips/TripMapScreen.kt`, `ui/common/MapStyle.kt`, `ui/common/MyLocation.kt`, `ui/transfer/TransferUi.kt`, `ui/picker/LocationPickerScreen.kt`, `ui/share/ShareTripScreen.kt`, `ui/trips/MyMapScreen.kt` | theme (on spec: Forest callout) | 1 |
| N2 | Toast after "Thêm từ ảnh" (system toast, not stylable) | `ui/AppNavigation.kt` | system | — |
| N3 | Recording notification (system notification, `ic_stat_track`) | `tracking/TrackRecordingService.kt` | system | — |

## Map overlays

| # | Element | File | Status | Batch |
| --- | --- | --- | --- | --- |
| O1 | Pins, legs, track, my location, route colours | `map/Pins.kt`, `map/maplibre/OverlayLayers.kt`, `map/maplibre/MyLocationLayers.kt`, `map/TransportStyle.kt` | on spec | 1 |
| O2 | Basemap repaint (Bản đồ, Vệ tinh) | `map/basemap/OpenFreeMap.kt`, `map/basemap/EsriWorldImagery.kt` | on spec | 1 |
| O3 | Map credit | `map/MapViews.kt` (`MapAttribution`) | on spec (standard 1: one text, OSM named once, paper panel under every live map) | 1 |

## Share outputs

| # | Output | File | Status | Batch |
| --- | --- | --- | --- | --- |
| X1 | Summary image PNG (R18) | `share/SummaryImage.kt`, `map/StaticTripMap.kt` | on spec (stats row, ledge map, photos fit above the mark) | 4 |
| X2 | HTML trip page (R18) | `domain/usecase/TripPage.kt` | on spec (topo hex in its CSS) | 4 |
| X3 | GPX / backup files | `domain/usecase/Gpx.kt`, `data/BackupManager.kt` | no visual | — |
| X4 | Launcher icon, splash | `res/drawable/ic_launcher_foreground.xml`, `res/values/colors.xml` | on spec | 1 |

## Later batches

Each batch is a contained set of files; shared components (`ui/common/`, `ui/theme/`) are done.

* **Batch 2: create and place (done, on spec).** `ui/trips/TripEditScreen.kt`, `ui/trips/CoverPicker.kt`,
  `ui/picker/LocationPickerScreen.kt`, `ui/picker/PlaceSearchUi.kt`, `ui/common/PlaceUi.kt`,
  `ui/steps/FromPhotosScreen.kt` (S7–S9, D9, B3, B4); shots `docs/img/topo/before-/after-b2-*.png`.
  `PlaceSearchUi.kt` and `PlaceUi.kt` were already free of Material icons and off-scale weights
  (not in `notYetTopo`); the other files left the list. New: `FieldButton` (TripEditScreen.kt,
  an outlined field-style button with a TopoIcons icon, also used for the step time) and the
  `TopoIcons.Calendar` icon.
* **Batch 3: maps and tools (done, on spec).** `ui/trips/MyMapScreen.kt`, `ui/offline/OfflineMapsScreen.kt`,
  `ui/tracks/BatteryGuideScreen.kt`, `ui/photos/PhotoViewerScreen.kt` (S10–S13).
* **Batch 4: share and transfer.** `ui/share/ShareTripScreen.kt`, `share/SummaryImage.kt`,
  `domain/usecase/TripPage.kt`, `ui/transfer/TransferUi.kt` (S14, D10–D13, X1, X2). Done (branch `mate/lutrinh-topo-b4`).

Count: 14 screens + 13 dialogs + 4 sheets + 4 menus + 3 messages + 3 map overlays + 4 share
outputs = 45 entries.
