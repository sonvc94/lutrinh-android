# 05 — Map (OpenStreetMap)

## Choice

* **Data:** OpenStreetMap, served as **vector tiles** by OpenFreeMap (OpenMapTiles schema), free,
  no API key, no registration.
* **Renderer:** MapLibre Native (Android `org.maplibre.gl:android-sdk` 11.x). Reasons: open
  source (BSD), same engine and style format on iOS (MapLibre Native iOS), GPU rendering, GeoJSON
  sources + line/symbol layers for route and pins, vector styles we can repaint at runtime.
  osmdroid was the alternative: Android-only, raster only.
* **Style:** OpenFreeMap **Positron** (a light, low-contrast style made to put data on top),
  loaded from its URL, then repainted at runtime in Lữ Trình's warm colours (see "Basemap
  tuning"). Positron, not `liberty`/`bright`: those are full-colour "general" maps where a route
  has to compete with roads and POI icons.

Before/after comparison of the route redraw (main@55b3966 raster OSM vs. now), Hà Giang sample:
`img/route-redraw/before-*.png` and `after-*.png` (country, region, city zoom, flight leg,
selected step, trip detail, Bản đồ của tôi; plus `after-leg-selected.png`).

## Basemap — the one place to change it

`map/MapConfig.kt`:

| Constant | Value |
| --- | --- |
| `STYLE_URL` | `https://tiles.openfreemap.org/styles/positron` |
| `ATTRIBUTION` | `OpenFreeMap © OpenMapTiles © OpenStreetMap contributors` |
| `USER_AGENT` | `LuTrinh/<versionName> (Android; vn.lutrinh.app)` |
| `MAX_ZOOM` | 19 (vector tiles stop at z14 and are over-zoomed; streets stay sharp) |
| `BACKGROUND` | `#F3EFE6` — painted while the style loads and if it fails (never a black map) |
| `BASEMAP_TUNING`, `LABEL_*` | runtime repaint, below |

The style pulls everything from `tiles.openfreemap.org`: TileJSON `/planet`, vector tiles, fonts
(`/fonts/{fontstack}/{range}.pbf`), sprite (`/sprites/ofm_f384/ofm`) and a Natural Earth shaded
relief raster for z0–6. Reachability from the development network in Vietnam, 2026-11-09:

```
curl -sI https://tiles.openfreemap.org/styles/positron  → HTTP/2 200 (content-type: application/json)
curl https://tile.openstreetmap.org/1/1/0.png            → (35) Recv failure: Connection reset by peer
```

(`*.openstreetmap.org` resets TLS there; the previous raster source was `tile.openstreetmap.de`.)

**Usage.** OpenFreeMap is a donated public instance with no key and no published limits, asking
for attribution; it is fine for development and a beta. For production either self-host the
same OpenMapTiles data (OpenFreeMap is open source) or move to a paid OSM-based provider
(MapTiler, Stadia…): only `STYLE_URL` and `ATTRIBUTION` change; tuning entries for layer ids the
new style lacks are skipped.

**User-Agent.** MapLibre downloads through OkHttp; at app start `MapSetup.init` installs an
OkHttp client whose network interceptor sets `User-Agent: MapConfig.USER_AGENT`. No personal
data is sent. On iOS set the header via `MLNNetworkConfiguration`.

**Attribution.** Every map shows `MapConfig.ATTRIBUTION` in its bottom-right corner (9 sp,
`#4A524E`, on white at 75 % opacity, drawn by the app). The MapLibre logo and the (i) button
are hidden because the label already gives the required credit.

**Caching.** MapLibre keeps its default ambient HTTP cache (tiles seen online, in the same
database as the offline areas); areas the user downloads are kept until deleted ("Offline maps").

### Basemap tuning (runtime, after the style loads — `RouteLayers.kt` `tuneBasemap`)

| Positron layer | Property | Colour |
| --- | --- | --- |
| `background` | background-color | `#F3EFE6` warm cream |
| `park` | fill-color | `#E4E9DC` |
| `landcover_wood` | fill-color | `#DFE6D7` |
| `landuse_residential` | fill-color | `#EDE8DD` |
| `water` | fill-color | `#BFD4DC` |
| `waterway` | line-color | `#B3CBD4` |
| `building` | fill / outline | `#EAE5DA` / `#DDD7CB` |
| `boundary_2` / `boundary_3` | line-color | `#B9B2A6` / `#CFC8BC` |

Labels: every symbol layer whose id starts with `label_`, `water_name`, `waterway_line_label`,
`highway-name` or `airport` gets `text-field = coalesce(name:vi, name:latin, name)` (Vietnamese
first — the tiles carry `name:vi`), text colour `#5F6661` and halo `#F7F4EE`. The share
snapshot cannot list a style's layers, so the same label layers are also named one by one in
`MapConfig.LABEL_LAYER_IDS` (another style needs its own list). Roads keep
Positron's light greys. Result: the land is quiet and the route is the darkest thing on the map.

## Map surfaces

| Where | Size | Interactive | Content |
| --- | --- | --- | --- |
| Trip detail header | 220 dp tall | no (static preview; expand button opens Trip map) | pins + legs |
| Trip map | full screen | pan + pinch zoom; rotate/tilt off | pins + legs + mode badges; tap pin → card, tap badge or leg → leg mode sheet |
| Step form location card | small | no | the step's point |
| Step detail | card | no | whole trip, this step highlighted |
| Location picker | full screen | pan + zoom | fixed centre pin, other steps as context pins |
| Thêm từ ảnh review | 180 dp tall | no | numbered pins of the kept suggestions, no route (R13) |
| Bản đồ của tôi | full screen | pan + zoom | every trip: one coloured line + stop dots per trip; tap → select, tap the card → open (R14) |
| Share image / page (R18) | 960 × 980 px bitmap | no (a picture) | pins + legs + recorded tracks + attribution, "Static map for sharing" |

## Data prep per leg

The route source holds **one LineString feature per leg** (R12) with properties `mode` (code or
`none`), `kind` (stroke kind, below), `selected` (the leg whose mode sheet is open) and `to`
(arrival step id, for taps).

1. **Geometry** — `LegGeometry.path` (`domain/usecase/Legs.kt`): ground/water/walk legs are a
   straight segment between the two steps (no routing). A **flight** is
   * a **great circle** A→B: spherical linear interpolation of the two unit vectors, ends exact,
     longitudes unwrapped (never a 360° jump across the antimeridian); one point per 20 km,
     at least 25, at most 129 (`arcPointCount`);
   * plus a decorative **sideways lift**: each interior point is moved to the **left** of the
     travel direction by `0.2 × 4t(1 − t) × chord`, measured in Web Mercator (x = lon,
     y = Mercator y in degrees) so the bow looks round at any latitude — 0 at both ends,
     `0.2 × chord` at mid-leg. Reason: at Vietnam scale a pure great circle is visually a
     straight line, and a flight must read as "by air".
2. **Simplification** — `Polyline.simplify` (Douglas–Peucker, ε = 2·10⁻⁴°, ≈ 22 m) before
   drawing; ends always kept, ≤ 2-point lines unchanged. Today it only thins flight arcs; it is
   there for future GPS/road geometry.
3. **Badge position** — `Polyline.pointAt(path, 0.5)`: the point at half the drawn length
   (length in a cos(lat)-scaled plane), so it sits on the arc, not on the chord.

Tests: `RouteGeometryTest` (great-circle ends/spacing/antimeridian, lift at ends/mid/quarter,
left side, point count, DP tolerance/corners/short lines, simplified arc, `pointAt`),
`LegsTest.onlyFlightsAreDrawnAsArcs`.

## Layers (bottom → top, `RouteLayers.kt` `addRouteLayers`)

Widths are dp and interpolate linearly with zoom between the stops z5 / z9 / z13 (clamped
outside); "→" gives the selected-leg value. Colour = route green `#1F5C45` (theme primary), the
selected leg rice gold `#E0A526`. Selected features have `line-sort-key` 2 (drawn on top).

| # | Layer id | Filter `kind` | Paint |
| --- | --- | --- | --- |
| 1–55 | Positron basemap (tuned) | | |
| 56 | `route-shadow` | `ground` | `#0E2A20`, width 5 / 8 / 16, blur 3 → 8 (z5 → z13), opacity 0.28 → 0.16, translate (0, 1 dp), round cap/join |
| 57 | `route-casing` | `ground` | white, width 4.6 / 6.6 / 9 → 6 / 8.6 / 12, round cap/join |
| 58 | `route-ground` | `ground` | route colour, width 2.2 / 3.4 / 5 → 3.4 / 5 / 7.5, round cap/join |
| 59 | `route-walk` | `walk` | route colour, dots: dasharray `[0, 2.2]` + round cap, width 1.8 / 2.8 / 4 → 2.6 / 4 / 5.5, no casing |
| 60 | `route-boat` | `boat` | route colour, dots: dasharray `[0, 1.7]` + round cap, width 2.6 / 3.8 / 5.2 → 3.6 / 5.2 / 7.5, no casing |
| 61 | `route-flight` | `flight` | route colour, dashes `[2.4, 1.6]` (in line widths) + butt cap, width 2 / 3 / 4.2 → 3 / 4.5 / 6.5, no casing/shadow |
| 62 | `leg-badges-layer` | | symbol, mode badge at `pointAt(0.5)`; `icon-allow-overlap` **off** (badges give way to pins and each other, so short legs drop theirs at low zoom), `symbol-sort-key` = order (selected first), `symbol-avoid-edges` |
| 63 | `steps-layer` | | symbol, step pins; `icon-allow-overlap` on (no pin ever hides), `symbol-sort-key` puts the selected pin, then start/end, on top |

Recorded tracks (R16) sit between the route and the badges: `track-casing` and `track-line`
(see "Recorded tracks" below), then the stop-suggestion markers (R17) `stop-suggest-halo` and
`stop-suggest-dot` (see "Stop suggestions" below), so pins and badges stay on top of them.

Pins and badges scale with zoom: `icon-size` 0.75 at z4, 0.9 at z8, 1 from z10.
MapLibre's `line-dasharray` is **not** data-driven, so each stroke kind is its own layer
filtered with `["==", ["get", "kind"], "<kind>"]`.

### Legs per transport mode (R12)

| Mode | Stroke kind | Icon colour | Icon (Material) |
| --- | --- | --- | --- |
| Đi bộ | `walk` — thin dots | `#6D4C41` | DirectionsWalk |
| Xe máy | `ground` | `#E65100` | TwoWheeler |
| Ô tô | `ground` | `#3949AB` | DirectionsCar |
| Xe buýt/xe khách | `ground` | `#8E24AA` | DirectionsBus |
| Tàu hỏa | `ground` | `#455A64` | Train |
| Máy bay | `flight` — dashed great-circle arc | `#039BE5` | Flight |
| Thuyền | `boat` — dots | `#00897B` | DirectionsBoat |
| none | `ground` | — | — (no badge) |

The line itself is always route green (gold when selected): the mode reads from the stroke kind
and the badge. The mode colours of `map/TransportStyle.kt` (one table for map, timeline chips
and mode sheet) tint the badge icon, the chips and the sheet.

**Badges** (`rememberTransportBadges`, bitmaps drawn in code): 24 dp white disc with a soft
shadow, a 1.5 dp ring in the mode colour at 55 % alpha, the icon (60 % of the disc) in the mode
colour. Selected leg: 28 dp disc filled with the mode colour, white ring, white icon.
Tapping a badge — or the leg line itself, within 11 dp, for legs that dropped their badge —
opens the leg's mode sheet.

## Step pins (`map/Pins.kt`)

Bitmaps drawn in code (no font glyphs or sprite sheet), each with a soft drop shadow, the step
number (R2 position, 1-based) in bold:

| Kind | Used for | Look |
| --- | --- | --- |
| START | first step of a route | 28 dp **white** disc, 3 dp route-green ring, green number |
| STEP | other steps; every pin when the route is hidden (Thêm từ ảnh) | 24 dp route-green disc, 2 dp white ring, white number |
| END | last step of a route | 28 dp **clay** `#B85C2E` disc, 2.5 dp white ring, white number |
| CONTEXT | other steps in the location picker | 22 dp clay disc, white ring |
| DRAFT | a draft step (R20) on the trip map and the trip-detail / step-detail mini maps | 22 dp translucent white disc, dashed route-green ring, **no number**; never START/END, no leg to or from it (the route and legs join the published steps around it) |
| selected / highlighted | tapped pin; step in Step detail / Step form | same pin ×1.3, rice-gold ring, on a 54 dp translucent gold halo |

No clustering: overlapping pins stay visible (a Hà Giang loop at country zoom is a tight group).

### All trips ("Bản đồ của tôi", R14)

`map/AllTripsMap.kt`: one GeoJSON LineString per trip with ≥ 2 steps (R3 order; flight legs
drawn as the same arc, every other leg straight; simplified like above; no dashes or badges — the
colour identifies the trip) and one point per step.
Layers (dp at z5 / z9 / z13, "→" selected): `trips-shadow-layer` (same soft shadow as
`route-shadow`), `trips-casing-layer` (white, 4 / 6.4 / 9 → 5.6 / 8.6 / 12), `trips-layer` (trip
colour, 2 / 3.2 / 5 → 3.2 / 5 / 7.5), `trip-stops-layer` (circles in the trip colour, radius
3 / 4.5 / 6 → 4 / 6 / 7.5, white stroke 1.5 → 2.5). The selected trip is sorted on top. A tap
within 18 dp of a line or dot selects that trip; the camera fits all trips.
Screenshot: `img/26-my-map.png`.

### Recorded tracks (R16)

`RouteLayers.kt` `addTrackLayers` + `TrackLine.toFeatureCollection`: one GeoJSON source
(`tracks-src` on trip maps, `all-tracks-src` on Bản đồ của tôi) with one LineString per drawn
segment (`TrackMath.displayLines`: one line per track segment, Douglas–Peucker 5·10⁻⁵°; a pause
splits the line), properties `track` (id) and `live` (the open track).

| Layer id | Paint (dp at z5 / z9 / z13) |
| --- | --- |
| `track-casing` | white, width 3.2 / 4.6 / 6.5, opacity 0.9, round cap/join |
| `track-line` | crimson `#D7263D`, width 1.6 / 2.6 / 4, round cap/join, solid |

Crimson is not used by the route (green/gold), the leg modes or the 8 trip colours of R14, and a
thinner solid line on a thin casing reads as "where I really went" next to the thicker planned
legs. Drawn on: the trip detail header map, the full trip map (above the legs, below badges and
pins) and Bản đồ của tôi (every trip's tracks, above the trip lines, below the stop dots). Tracks
are not tappable. The step map and location picker draw no tracks.

* **Camera:** on trip maps the fit includes the points of ended tracks (a track that left the
  steps' box stays on screen); the **live** track never moves the camera (no re-fit on each fix).
  Bản đồ của tôi fits trips only.
* **Offline:** tracks come from the local database and draw over whatever basemap is available,
  including a downloaded offline area (R15) with no network (`img/gps-track/04-airplane-trip-map-track.png`).
* Screenshots: `img/gps-track/03-trip-map-track.png`, `img/gps-track/06-my-map-track.png`,
  `img/gps-track/02-trip-detail-recording.png`.

### Stop suggestions (R17)

`RouteLayers.kt` `addStopSuggestionLayers` + `List<StopMarker>.toStopFeatureCollection`: one
GeoJSON source `stop-suggest-src` with one Point per current suggestion (its centre), property
`key` (`<trackId>@<startAt>`). Two circle layers right above `track-line`, below the leg badges
and step pins:

| Layer id | Paint (radius dp at z5 / z10 / z14) |
| --- | --- |
| `stop-suggest-halo` | crimson `#D7263D` fill, opacity 0.16, radius 7 / 11 / 18, 1 dp crimson stroke at opacity 0.5 |
| `stop-suggest-dot` | white fill, radius 3.5 / 5 / 6, 2.5 dp crimson stroke |

Track colour on a soft halo says "found on your track"; a hollow white dot cannot be mistaken for
the filled, numbered step pins. Drawn on the trip detail header map and the full trip map only
(not on Bản đồ của tôi, the step map or the picker). Not tappable; the list on the trip detail
(docs/03 §3c) is where the user acts. The markers follow the list (a dismissed or added
suggestion's marker goes at once) and do not change the camera fit (the stays lie on the
tracks, which the fit already includes). Screenshot: `img/stop-suggest/05-trip-map-markers.png`.

### My location (R22, `map/MyLocationLayer.kt`)

One GeoJSON source `my-location-src` (a single Point with property `r20`) and two circle layers,
added by `TripMap` and `AllTripsMap` right above the tracks / stop suggestions and below the leg
badges and step pins: `my-location-src-accuracy` (blue `#1A73E8`, opacity 0.16, 1 dp stroke at
0.35; radius in dp = `accuracy / 0.07464 / cos(lat)` at zoom 20, interpolated with base 2 between
zoom 0 → 0 and zoom 20) and `my-location-src-dot` (radius 8 dp, white 3 dp stroke). The source is
the one fix of the last tap (`DeviceLocation.requestFix`: GPS with fine permission, else network,
else last known); the MapLibre location component is **not** used (it keeps a provider running while
the map is open, and a fix per tap is enough and light on battery). A new fix flies the camera to it
(`MapConfig.MY_LOCATION_ZOOM` = 15, never zooming out; padding keeps it above the carousel) and, on
the trip map, counts as the user's own move: the carousel re-measure does not re-fit afterwards.
Android 12+ with only "approximate" location still gives a fix, with a larger accuracy circle.

## Static map for sharing (R18, `map/StaticTripMap.kt`, `map/MapFit.kt`)

The "Ảnh dọc cho mạng xã hội" and "Trang xem offline" (04-business-rules.md R18) need the trip map as a bitmap,
without a visible map view. Two steps:

1. **Basemap** — MapLibre `MapSnapshotter` with `MapConfig.STYLE_URL`, size 960 × 980 logical
   px × pixel ratio 2.5 (`SummaryImage.MAP_*`), logo off, the camera from `MapFit`, and the same
   repaint as the live maps applied in `onDidFinishLoadingStyle` through `getLayer(id)`
   (`BASEMAP_TUNING` + the Vietnamese-first labels on `LABEL_LAYER_IDS`). Tiles come from the
   network or, offline, from MapLibre's store (an offline pack, R15, or the ambient cache). The
   snapshot runs on the main thread and is cancelled after **15 s** (`SNAPSHOT_TIMEOUT_MS`).
2. **Overlay** — drawn by us on a `Canvas` over the snapshot, not as runtime style layers in
   the snapshotter's style (not tried): drawing it ourselves gives the same picture with and
   without a basemap (the fallback below needs the drawing anyway) and reuses the pin bitmaps. Points are placed with our own
   Web Mercator projection (`MapFit.project`: world = 512 · 2^zoom px, the MapLibre tile size),
   which matches the snapshot. Order bottom → top: legs (ground: white casing 6.6 + green
   `#1F5C45` 3.8 px per logical px; flight dashed, boat/walk dotted, paths from `LegGeometry`
   like the live map), recorded tracks (white 4.6 + crimson 2.6, `TrackMath.displayLines`),
   numbered step pins (`Pins.kt` bitmaps at the snapshot's pixel ratio; start and end on top),
   then the attribution text in a translucent box in the bottom-right corner.

**Fit** (`MapFit.fit`, pure, `TripShareTest`): every step and track point inside the map minus 70
px padding, zoom capped at `FIT_MAX_ZOOM` (14); one point → `SINGLE_POINT_ZOOM`; none → the
Vietnam overview (`FALLBACK_CENTER`, `OVERVIEW_ZOOM`) — the camera rules below.

**No basemap.** Snapshot error or timeout (offline outside every downloaded area, style not
cached) → the map is the overlay on the plain `MapConfig.BACKGROUND` (`#F3EFE6`), with the
attribution still drawn; the share screen says "Không tải được nền bản đồ …". Never a crash.
Screens: `img/share-trip/06-airplane-image.png` (offline pack), `img/share-trip/07-no-basemap.png`.

## Camera rules

| Case | Rule |
| --- | --- |
| No steps | centre (16.0, 106.0) — Vietnam overview — zoom 5 |
| One step | centre on it, zoom 12 |
| Two or more | fit the bounds of **every drawn point** (steps and flight-arc points, so an arc never leaves the screen) inside the padded area, cap zoom at 14. Padding: 40 dp on the trip detail header and step detail (36 dp Thêm từ ảnh, 56 dp default); full trip map 48 dp on each side **plus the measured height of the card carousel at the bottom**, so no pin is drawn under the cards |
| Step selected (pin or card tap, full trip map) | animate to the step at zoom `max(current, 11)` capped at 14 (never zooms the user out), keeping the carousel padding so the step sits above the cards |
| Leg selected (mode sheet open) | animate to fit the leg's path, zoom ≤ 13, bottom padding = max(carousel padding, 55 % of the map height) so the leg stays visible above the sheet |
| Picker opening | target point at zoom 13 (see R6 for which point); overview at zoom 5 when none |
| Picker chip ("Vị trí của tôi" or one of the trip's steps) | fly to that point (`focusZoom`, ≥ 11) |
| "Xem toàn bộ hành trình" (trip map) | clear the selection, fit all pins above the cards, zoom ≤ `FIT_MAX_ZOOM` (14) |
| "Vị trí của tôi" (trip map, My map) | animate to the fix at zoom `max(current, 15)` (≤ 19), carousel padding kept |
| Carousel swipe settles on a card | that card's step is selected and flown to like a pin tap |

Animation length (`CameraMath.flyDurationMs`, tested in `CameraMathTest`):
`350 ms + 1.5 ms/km + 160 ms per zoom level`, clamped to 450–1600 ms.

The route is fitted once per change of the step list/modes — or of the bottom padding, until
the user has selected something — after the view has its size; a selection is not undone by a
later re-measure of the carousel, and the user's own panning is not reset. A single step is
centred in the whole view (well above the cards).
Screenshots: `img/20-trip-map-pins-above-cards.png` (step 3 selected), `img/23-trip-map-leg-modes.png`.

*Inspired by* polarsteps docs/22 §2.4 and docs/23 §6 (fit-to-points with padding, max zoom 14,
animated focus). *Changed:* zoom 12 for a single point (village scale suits rural Vietnam
better), no pitch/bearing, own durations.

## Location picker behaviour

* The pin is a fixed overlay in the centre; the user drags the map under it. The coordinate
  label updates when the camera stops (camera-idle event), 5 decimals (~1 m).
* "Dùng vị trí này" returns the centre point. Back returns nothing (step keeps its old point).
* No geocoding: the user types the place name; the app does not call Nominatim/LocationIQ in this
  phase. The picker's chips are "Vị trí của tôi" (one device fix, never stored) and the trip's own steps; there is no built-in place list.

*Changed from the reference:* polarsteps docs/22 §1.3/§2.6 search places through LocationIQ and
tap-to-move the pin; neighbour steps are drawn there as a dotted line, here as small context pins.

## Offline maps (R15)

Screen in 03-screens.md §11, rules in 04-business-rules.md R15, numbers in `map/OfflineConfig.kt`.

* **Engine.** MapLibre's own offline store: `OfflineManager.createOfflineRegion` with an
  `OfflineTilePyramidRegionDefinition(STYLE_URL, bounds, minZoom 0, maxZoom 14, pixelRatio =
  screen density, includeIdeographs = false)` and JSON metadata
  `{"key","name","description","paused"}`. MapLibre downloads style, sprite, glyphs and tiles of
  every source in the style (vector `openmaptiles` z0–14, Natural Earth relief raster z0–6) into
  `files/mbgl-offline.db` and serves them to every `MapView` when the network is gone — no change
  to the map code. The basemap repaint (`tuneBasemap`), route layers, pins and badges are built
  at runtime after the style loads, so they work offline unchanged.
* **App side.** `map/OfflineMaps.kt` (one per process, in `AppContainer`, started in
  `LuTrinhApp.onCreate`) lists regions, maps them to `OfflineArea`s by the metadata key, observes
  status/errors and exposes `states` (phase, bytes, resources, problem) to the screen and the
  indicator; `map/NetworkMonitor.kt` gives online/offline (`ConnectivityManager` default network
  callback, permission `ACCESS_NETWORK_STATE`); `map/OfflineIndicator.kt` draws the chip/card
  over every map (camera centre from the camera-idle listener).
* **Zoom range.** z14 is the last vector zoom of the OpenFreeMap tileset; storing z15+ would only
  duplicate it. Readability was checked at town zoom in airplane mode (img/offline/04).
* **Sizes (2026-11-09, emulator, xxhdpi):**

| Area | Tiles z0–14 (one source) | Estimate shown | Stored (screen) |
| --- | --- | --- | --- |
| Hà Giang | ~1 780 | ~29,6 MB | 26,3 MB |
| Ninh Bình | ~610 | ~23,9 MB | 24,5 MB |
| both | | | `mbgl-offline.db` 33,5 MB (fonts shared) |

  The fonts (~17 MB for all glyph ranges of the 3 Noto stacks) dominate small areas; tile bytes
  are only ~8 MB per area.
* **Deleting.** `OfflineRegion.delete` only unlinks the region: its resources become ambient
  cache (MapLibre keeps up to 50 MB) and the file does not shrink. `OfflineMaps.delete` therefore
  calls `OfflineManager.clearAmbientCache` + `packDatabase` afterwards (measured: 33,7 → 28,2 →
  0,07 MB deleting Ninh Bình then Hà Giang; the fonts stay while any area uses them).
* **Provider note.** OpenFreeMap is a donated public instance: downloads are kept modest on
  purpose (two ready-made regions, trip areas capped at 150 MB, no country/province packs, one
  revalidation per process). A production release with many users should self-host the
  OpenMapTiles data (or use a paid provider) before offering offline areas widely; only
  `STYLE_URL` changes, `OfflineConfig` re-measures `BYTES_PER_TILE`/`FIXED_BYTES`.
* **Tileset versions.** The style's vector source is a TileJSON (`/planet`, max-age 1 h) whose
  `tiles` URL contains a version (`/planet/20261101_080013_pt/{z}/{x}/{y}.pbf`). Offline, MapLibre
  uses the stored TileJSON, so the pack stays consistent. Online after a new OpenFreeMap
  release, the start-up revalidation downloads the new version's tiles; old tiles stay linked to
  the region until it is deleted (the pack can grow by one version's tiles; delete + re-download
  shrinks it).
* **Testing offline on the emulator.**
  `adb -s emulator-5580 shell cmd connectivity airplane-mode enable` (check
  `settings get global airplane_mode_on` = 1), `am force-stop vn.lutrinh.app` + `am start`, then
  `… airplane-mode disable`. Store size: `adb shell run-as vn.lutrinh.app ls -l files/mbgl-offline.db`.

## Not done (later)

Drawing a custom offline box on the map, background download as a foreground service/WorkManager
(today downloads stop when the process dies and resume at the next start), dark map, clustering for trips with many steps, road-following routes
(snap-to-roads / OSRM — legs stay straight or arcs), photo thumbnails inside pins, current-location
button and following the user on the map (location is used only while recording a track, R16),
snapping recorded tracks to roads, GPX export, a self-hosted or paid tile source
for production (also for offline areas, see "Provider note").
