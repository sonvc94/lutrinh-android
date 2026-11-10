# 05 — Map (OpenStreetMap)

## Choice

* **Data:** OpenStreetMap, served as **vector tiles** by OpenFreeMap (OpenMapTiles schema), free,
  no API key, no registration.
* **Renderer:** MapLibre Native (Android `org.maplibre.gl:android-sdk` 11.x). Reasons: open
  source (BSD), same engine and style format on iOS (MapLibre Native iOS), GPU rendering, GeoJSON
  sources + line/symbol layers for route and pins, vector styles we can repaint at runtime.
  osmdroid was the alternative: Android-only, raster only.
* **Interface:** screens never use the map SDK; they talk to the app's map interface and
  basemap providers through adapters ("Map interface and providers", R35). The renderer and each
  tile provider can be replaced by writing one adapter.
* **Basemaps:** "Bản đồ" (OpenFreeMap, below, the default and the only one offline) and "Vệ
  tinh" (Esri World Imagery under OpenFreeMap roads and labels, online only), chosen by the user
  (R32–R34).
* **Style:** OpenFreeMap **Positron** (a light, low-contrast style made to put data on top),
  loaded from its URL, then repainted at runtime in Lữ Trình's warm colours (see "Basemap
  tuning"). Positron, not `liberty`/`bright`: those are full-colour "general" maps where a route
  has to compete with roads and POI icons.

Before/after comparison of the route redraw (main@55b3966 raster OSM vs. now), Hà Giang sample:
`img/route-redraw/before-*.png` and `after-*.png` (country, region, city zoom, flight leg,
selected step, trip detail, Bản đồ của tôi; plus `after-leg-selected.png`).

## Map interface and providers (R32–R35)

Screens and view models never touch a map SDK (R35). Four parts, ported one to one to iOS
(07-ios-port.md):

| Part | Android | Role |
| --- | --- | --- |
| Map interface | `map/MapController.kt`: `MapController`, `MapListener`, models `MapCamera`, `MapInsets`, `CameraAnimation`, `MapIcon`, `MapPin`, `MapLeg`, `MapBadge`, `MapTripLine`, `MapTripDot`, `TrackLine`, `StopMarker`, `MyLocationFix`, `MapHit`; `map/MapEngine.kt`: `MapEngine`, `OfflineMapStore`, `MapEngines.active` | what the app asks of any map |
| Presenters | `map/MapPresenters.kt`: `TripMapPresenter`, `AllTripsPresenter`, `PickerPresenter`, `MapCameraRules`; composables in `map/MapViews.kt` (`TripMap`, `PickerMap`, `AllTripsMap`, `MapAttribution`) | the app's map behaviour (pins, legs, badges, camera rules, taps); engine-neutral, unit-tested with a fake controller |
| Basemap providers | `map/basemap/`: `BasemapProvider`, `BasemapStyle`, `RasterImagery`, `BasemapRegistry` + `Basemaps.registry`, `BasemapSettings` (the persisted choice), `BasemapPlanner` (what to hide, repaint and filter in a loaded style); adapters `OpenFreeMap.kt` ("Bản đồ") and `EsriWorldImagery.kt` ("Vệ tinh") | which tiles, which credit, offline or not |
| Engine adapter | `map/maplibre/` (the only package importing `org.maplibre`): `MapLibreEngine`, `MapLibreController`, `MapLibreOfflineStore`, `BasemapLayers.kt`, `OverlayLayers.kt`, `SovereigntyLayers.kt`, `MyLocationLayers.kt` | turns interface calls into MapLibre calls |

**Map interface.** Coordinates are `GeoPoint`, colours `#RRGGBB`, sizes px.

* `setBasemap(BasemapStyle)`.
* Overlays: `setRouteColors`, `setPins`, `setLegs(legs, badges)` (a `MapLeg` carries
  `selected` and `travelled`, R37), `setTrips(lines, dots)`,
  `setTracks`, `setStopSuggestions`, `setMyLocation`.
* Camera: `camera`, `moveTo(center, zoom, padding?, NONE | EASE | FLY)`,
  `fitPoints(points, padding, maxZoom, animation)`, `afterLayout(action)`, `widthPx`/`heightPx`,
  `animateTo(CameraPlan)` (R37: centre, zoom, padding and duration decided by the app; 0 ms = jump,
  otherwise an ease in-out glide of exactly that length).
* Events: `setListener` → `onTap(MapHit?)` (pin, leg, trip, or nothing) and
  `onCameraIdle(MapCamera)`. Tap boxes: pins 22 dp, badges 22 dp, leg lines 11 dp, trips 18 dp.
* `MapEngine`:
  * `MapSurface(interactive) { controller }`;
  * `createOfflineStore(basemap)` → `OfflineMapStore` (`states`, `storeBytes`, `download`,
    `pause`, `resume`, `delete`, `freeBytes`);
  * `snapshot(basemap, camera, w, h, pixelRatio)` → a bitmap, or null.

The adapter keeps the last value of every overlay and redraws it after each style load, so a
basemap switch keeps the route, pins, selection, my location and the camera.

**Flow.** A map composable takes a controller from `LocalMapEngine.current.MapSurface` and sets
the basemap shown (`rememberShownBasemap()`: the choice, or "Bản đồ" while the choice needs a
network that is missing, R33). Its presenter then pushes overlays and camera moves. The
composable draws the offline indicator and `MapAttribution` of the basemap shown (R34) on top.

### Basemap providers

| id (stored as `map_style`) | Name | Tiles | Attribution (R34) | Offline (R15) | Needs network |
| --- | --- | --- | --- | --- | --- |
| `light` (default) | Bản đồ | OpenFreeMap Positron vector style (below) | `OpenFreeMap © OpenMapTiles © OpenStreetMap contributors` | yes | no |
| `satellite` | Vệ tinh | Esri World Imagery raster under the Positron roads, borders and labels | `Esri, Vantor, Earthstar Geographics, and the GIS User Community · OpenFreeMap © OpenMapTiles © OpenStreetMap contributors` | no | yes |

* The first registered provider is the default and must work offline (checked when the
  registry is built).
* Offline packs (R15) and the share image and page (R18) always use `registry.offline`, i.e.
  "Bản đồ".
* The choice (R32) is app-wide: the trip map, "Bản đồ của tôi", the picker and the small
  previews all show the same basemap.

**"Vệ tinh" is a hybrid.** The Positron style plus a raster source (`RasterImagery`: 256-px
tiles, z0–18, over-zoomed above) inserted directly above `background` (or at the bottom if a
style has no `background`). The planner (`BasemapPlanner.plan`) then:

* hides every non-label `fill`, `fill-extrusion` and `hillshade` layer and `waterway`, which
  would cover the photo;
* keeps roads, rail and borders, with borders repainted Sand `#E8E3CE` / Contour `#C09A68` (docs/08 §2 "Map");
* draws labels in white `#FFFFFF` on a Forest halo `#1F2A24`.

Label layers are never hidden. In both styles the R30 filter goes on every label layer and the
"Quần đảo Hoàng Sa/Trường Sa" labels are added.

**Esri terms and key.**

* **Keyless tiles.** `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`,
  acceptable for development and a non-commercial prototype.
* **Key for a release.** Esri asks for an ArcGIS Location Platform API key (free tier: 2 million
  basemap tiles per month).
  * Put `esriApiKey=<key>` in `local.properties` (git-ignored) or pass `-PesriApiKey=<key>`.
  * The build turns it into `BuildConfig.ESRI_API_KEY`.
  * Tiles then come from `https://ibasemaps-api.arcgis.com/arcgis/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}?token=<key>`.
  * Never commit a key. Without one, the build uses the keyless URL.
* **No offline.** Esri does not allow storing its tiles for offline use, so "Vệ tinh" is
  online-only (R33) and gets no offline pack.
* **Credit.** It stays visible whenever imagery is shown (R34). Imagery over Hà Giang (Mã Pí
  Lèng, 0.34 m) and Ninh Bình (Tràng An, 0.5 m) is sharp at z13–17.

**Adding a provider** (no screen changes):

1. Write one adapter, `map/basemap/<Name>.kt`, implementing `BasemapProvider`. It declares:
   * the style URL, or Positron plus a `RasterImagery`;
   * the label-layer prefixes and ids of that style;
   * repaint and hidden layers;
   * the attribution and the offline policy (`offlineAllowed`, `needsNetwork`).
2. Register it in `Basemaps.registry`. The "Kiểu bản đồ" sheet lists it automatically.
3. Resurvey R30 for its label layers ("Hoàng Sa / Trường Sa" below) and check its terms for
   offline use and attribution.

**Another engine** (e.g. a different SDK): a new package implementing `MapEngine`,
`MapController` and `OfflineMapStore`, set in `MapEngines.active`. Presenters, screens and
providers stay as they are.

## Basemap "Bản đồ" — OpenFreeMap Positron

`map/basemap/OpenFreeMap.kt` (`OpenFreeMap` provider, `Positron` style ids) and `map/MapConfig.kt`
(shared by all basemaps):

| Constant | Value |
| --- | --- |
| `Positron.STYLE_URL` | `https://tiles.openfreemap.org/styles/positron` |
| `OpenFreeMap.ATTRIBUTION` | `OpenFreeMap © OpenMapTiles © OpenStreetMap contributors` |
| `MapConfig.USER_AGENT` | `LuTrinh/<versionName> (Android; vn.lutrinh.app)` |
| `MapConfig.MAX_ZOOM` | 19 (vector tiles stop at z14 and are over-zoomed; streets stay sharp) |
| `MapConfig.BACKGROUND` | `#EFEBDA` topo map paper (`Palette.MapPaper` = Parchment, docs/08) — painted while the style loads and if it fails (never a black map) |
| `Positron.REPAINT`, `Positron.LABEL_*` | runtime repaint, below |

The style pulls everything from `tiles.openfreemap.org`: TileJSON `/planet`, vector tiles, fonts
(`/fonts/{fontstack}/{range}.pbf`), sprite (`/sprites/ofm_f384/ofm`) and a Natural Earth shaded
relief raster for z0–6. Reachability from the development network in Vietnam, 2026-11-09:

```
curl -sI https://tiles.openfreemap.org/styles/positron  → HTTP/2 200 (content-type: application/json)
curl https://tile.openstreetmap.org/1/1/0.png            → (35) Recv failure: Connection reset by peer
```

(`*.openstreetmap.org` resets TLS there; the previous raster source was `tile.openstreetmap.de`.)

**Usage.** OpenFreeMap is a donated public instance with no key and no published limits, asking
for attribution; it is fine for development and a beta. Its terms allow offline downloads of
"≤3,000 tiles or 50 MB per offline download": rule R31, `OfflineConfig.MAX_AREA_TILES`/`MAX_AREA_BYTES`. For production either self-host the
same OpenMapTiles data (OpenFreeMap is open source) or move to a paid OSM-based provider
(MapTiler, Stadia…): one new provider adapter ("Adding a provider"); repaint entries for layer
ids the new style lacks are skipped.

**User-Agent.** MapLibre downloads through OkHttp; at app start `MapLibreEngine.initialize` (via `MapEngines.active` in `LuTrinhApp`) installs an
OkHttp client whose network interceptor sets `User-Agent: MapConfig.USER_AGENT`. No personal
data is sent. On iOS set the header via `MLNNetworkConfiguration`.

**Attribution.** Every map shows the attribution of the basemap shown (`BasemapProvider.attribution`, R34) in its bottom-right corner (9 sp,
`#6B6A58` (`Palette.MapCredit`), on Card `#FBFAF2` at 80 % opacity, drawn by the app). The MapLibre logo and the (i) button
are hidden because the label already gives the required credit.

**Caching.** MapLibre keeps its default ambient HTTP cache (tiles seen online, in the same
database as the offline areas); areas the user downloads are kept until deleted ("Offline maps").

### Basemap tuning (runtime, after the style loads — `Positron.REPAINT`, applied by `maplibre/BasemapLayers.kt` `applyBasemap`)

| Positron layer | Property | Colour |
| --- | --- | --- |
| `background` | background-color | `#EFEBDA` Parchment, the topo map paper (docs/08) |
| `park` | fill-color | `#D8E4C4` Vegetation |
| `landcover_wood` | fill-color | `#D8E4C4` Vegetation |
| `landuse_residential` | fill-color | `#E8E3CE` Sand |
| `water` | fill-color | `#BFDDE6` Water |
| `waterway` | line-color | `#BFDDE6` Water |
| `building` | fill / outline | `#E3DEC8` Grid / `#D9D4BE` Ledge |
| `boundary_2` / `boundary_3` | line-color | `#C09A68` Contour / `#D9D4BE` Ledge |

Labels: every symbol layer whose id starts with `label_`, `water_name`, `waterway_line_label`,
`highway-name` or `airport` gets `text-field = coalesce(name:vi, name:latin, name)` (Vietnamese
first — the tiles carry `name:vi`), text colour `#5A5948` MapLabel (5.94:1 on the map paper) and halo `#FBFAF2` Card. The share
snapshot cannot list a style's layers, so the same label layers are also named one by one in
`Positron.LABEL_LAYER_IDS` (another style needs its own list). Roads keep
Positron's light greys. Result: the land is quiet and the route is the darkest thing on the map.

### Hoàng Sa / Trường Sa (R30, `map/SovereigntyLabels.kt`, `map/maplibre/SovereigntyLayers.kt`)

OpenStreetMap, and so the OpenFreeMap tiles (checked 2026-11-01 build, z3–10 over both
archipelagos), carries Chinese administrative units there: city 三沙市 (`name:vi` "Tam Sa"),
districts 西沙区 ("Quận Tây Sa", at 16.83 N 112.33 E, label_city from z3) and 南沙区 ("Quận Nam
Sa", 9.55 N 112.89 E), towns 永兴镇/永乐群岛/七连屿/渚碧岛, villages 甘泉社区 ("Xã khu Cam
Tuyền") …, the airport 三沙永兴机场 and Chinese-only island and sea-mount names. The app is for
Vietnam, so (rule R30):

* **Filter.** `applyBasemap` (live maps, both basemaps) and `MapLibreEngine.snapshot` (share
  snapshot) add `all(<layer's own filter>, not(hidden))` to every label layer of the table above,
  where `hidden` = `within(Hoàng Sa box) or within(Trường Sa box)` (the boxes include the open sea to 110.0 E and 12.2 N, where Chinese-only seamount/ridge names sit, e.g. 晋卿西海山 at 15.3 N 112.5 E) and (any name field in
  `BLOCKED_NAMES`, or contains a blocked substring, or `class` in the admin classes and `name`
  not in `VIETNAMESE_ADMIN`, or `has name:nonlatin` and not (`class` in
  island/ocean/sea/bay/strait/lake and `has name:vi`)). Decided per feature by its tags, not by its text, so it does not depend on the
  label language; filtering by box keeps Guangzhou's own 南沙区 etc. untouched.
* **Our labels.** `Style.Builder.withSovereigntyLabels()` adds GeoJSON source
  `r30-archipelagos` (two points from `SovereigntyLabels.labelsGeoJson()`, bundled in code) and
  symbol layer `r30-archipelago-labels`: "Quần đảo Hoàng Sa" (16.45 N 111.95 E) and "Quần đảo
  Trường Sa" (10.0 N 114.3 E), Noto Sans Italic 11→14 sp (z4→z8), label ink + halo, always
  placed, z3 up to (not incl.) z11. `styleBuilder(basemap)` (every live map, both basemaps) and
  the snapshotter's style builder use it (the snapshotter has no `addLayer`, but it applies a
  `Style.Builder`'s sources and layers).
* **Offline.** Packs store the plain style and tiles; the filter and labels are applied at
  runtime, so offline maps show the same.
* **Kept.** Only Vietnamese administrative names (rule R30 (c): "Trường Sa", "An Bang", "Sinh
  Tồn", "Song Tử Tây", "Thuyền Chài D/E", "Đá Tây A"), islands/reefs through their Vietnamese
  name ("Đảo Phú Lâm", "Đá En Ca") and Vietnamese harbours/ferries/airports. The Philippine
  municipality "Kalayaan" and quarter "Pag-asa" and Malaysian/Taiwanese/Chinese units are hidden.
  Boundary lines are not changed.
* **Another style or tile build** may bring new names: rerun the survey and extend
  `BLOCKED_NAMES`/`VIETNAMESE_ADMIN`; screenshots in `img/sovereignty/` (before/after, "Bản đồ của
  tôi").
* **Survey method** (`tools/map-survey/`, Python stdlib only): `survey.py` fetches every z5–12
  tile over both boxes and the z13–14 children of non-empty tiles (≈ 25 000 tiles, 2026-11-01
  build), decodes the vector tiles itself (`mvt.py`) and dumps every named `place`, `water_name`,
  `waterway`, `transportation_name`, `aerodrome_label`, `poi` feature; `report.py` applies the rule
  and lists what stays. Positron draws no POI symbols, so `poi` is only listed. Tiles: z5–6 have no
  feature in the boxes; the first labels (the three Chinese admin cities, "Kalayaan", "Trường Sa")
  appear at z7.
* **Kept after the claimants rule** (every label the rule leaves in the boxes, z7–14; all
  Vietnamese): towns/villages Trường Sa, An Bang, Sinh Tồn, Song Tử Tây, Thuyền Chài D,
  Thuyền Chài E, Đá Tây A; islands Đảo An Bang, Đảo Nam Yết, Đảo Núi Le C, Đảo Phan Vinh (+B),
  Đảo Phú Lâm, Đảo Sinh Tồn Đông, Đảo Sơn Ca, Đảo Thuyền Chài (+E), Đảo Tiên Nữ A/B, Đảo Tri
  Tôn, Đảo Trường Sa, Đảo Đá Lát, Đảo Đá Lớn A/B/C, Đảo Đá Nam, Đảo Đá Đông C, Đảo Đông Đảo,
  Đảo Mĩ Tế Bắc; reefs and banks Đá Cô Lin, Đá En Ca, Đá Hoa Lau, Đá Hoa Quang, Đá Hải Sâm, Đá
  Lãng Hoa, Đá Toàn Phú, Đá Vĩnh Thử, Bãi Bắc Biên, Bãi Ngân Thước, Bãi Ngự Bình, Bãi Tung Đảo,
  Bãi Tân Mê, Bãi Xà Cừ; airports "Sân bay Trường Sa", "Sân bay Đá Hoa Lau"; water "Âu tàu
  Trường Sa", "Âu tàu Đá Tây A", "Âu tàu đảo Sinh Tồn", "Khu cảng Đảo Vĩnh Thử", "Đầm phá Đảo
  Áp Công"; ferry lines such as "Sinh Tồn – Nam Yết"; "Bờ bao âu tàu". (The Vietnamese names of
  Chinese-origin features — Khu cảng Đảo Vĩnh Thử, Đầm phá Đảo Áp Công — are kept as names, not
  as administrative units.) Screenshots: `img/sovereignty/claimants-*.png`.

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
| Share image / page (R18) | 960 × 820 px bitmap | no (a picture) | pins + legs + recorded tracks + attribution, "Static map for sharing" |

## Data prep per leg

The route source holds **one LineString feature per leg** (R12) with properties `mode` (code or
`none`), `kind` (stroke kind, below), `selected` (the leg whose mode sheet is open) and `to`
(arrival step id, for taps).

1. **Geometry** — a leg with a usable stored road route (R36, "Road routing" below) follows the
   road: `LegRoutes.path` = stop → straight connector → stored road line → connector → stop.
   Every other leg uses `LegGeometry.path` (`domain/usecase/Legs.kt`): ground/water/walk legs are
   a straight segment between the two steps. A **flight** is
   * a **great circle** A→B: spherical linear interpolation of the two unit vectors, ends exact,
     longitudes unwrapped (never a 360° jump across the antimeridian); one point per 20 km,
     at least 25, at most 129 (`arcPointCount`);
   * plus a decorative **sideways lift**: each interior point is moved to the **left** of the
     travel direction by `0.2 × 4t(1 − t) × chord`, measured in Web Mercator (x = lon,
     y = Mercator y in degrees) so the bow looks round at any latitude — 0 at both ends,
     `0.2 × chord` at mid-leg. Reason: at Vietnam scale a pure great circle is visually a
     straight line, and a flight must read as "by air".
2. **Simplification** — `Polyline.simplify` (Douglas–Peucker, ε = 2·10⁻⁴°, ≈ 22 m) before
   drawing; ends always kept, ≤ 2-point lines unchanged. It thins flight arcs and road lines (a
   road line is also stored already simplified at 1·10⁻⁴°, ≈ 11 m, R36).
3. **Badge position** — `Polyline.pointAt(path, 0.5)`: the point at half the drawn length
   (length in a cos(lat)-scaled plane), so it sits on the arc, not on the chord.

Tests: `RouteGeometryTest` (great-circle ends/spacing/antimeridian, lift at ends/mid/quarter,
left side, point count, DP tolerance/corners/short lines, simplified arc, `pointAt`),
`LegsTest.onlyFlightsAreDrawnAsArcs`, `LegRoutesTest.roadLineIsJoinedToTheStopsByShortConnectors`.

## Road routing (R36, `domain/routing/`, `routing/`)

Legs of Đi bộ, Xe máy, Ô tô, Xe buýt/xe khách and "Chưa chọn" (= Xe máy) follow the road; Tàu
hỏa, Máy bay and Thuyền keep their straight line / arc / dots. The stroke kind and badge stay those
of the mode (a routed Đi bộ leg is dots along the path). Rules: 04-business-rules.md R36.

**Interface + adapters (same pattern as the map, R35).** The app knows only the routing interface
in `domain/routing/Routing.kt`:

| Part | Role |
| --- | --- |
| `RouteProfile` | `motorbike`, `car`, `bus`, `walk`; `RouteProfile.of(mode)` (null mode → motorbike; train/plane/boat → none) |
| `RoutingProvider` | `id`, `credit` (`RoutingCredit` text + fix-the-map URL), `supports(profile)`, `suspend route(from, to, profile): RouteResult` |
| `RouteResult` | `Found(distanceM, durationS, path)`, `NoRoute`, `Unavailable(reason, rateLimited)` |
| `RoutingProviders` | the registry: `primary` + `fallbacks` (asked only when the primary is unavailable) |

Adapters (Android, `routing/`): `valhalla/ValhallaRouter` — `POST {base}/route` with
`{"locations":[{lat,lon},{lat,lon}],"costing":…,"directions_type":"none","units":"kilometers"}`;
costing motorbike `motor_scooter` (the only Valhalla profile that avoids expressways such as
CT.01, where motorbikes are banned), car `auto`, bus `bus`, walk `pedestrian`; answer
`trip.summary.length` (km) / `.time` (s) and `trip.legs[*].shape` (polyline6); HTTP 400 → no route,
429 → rate limited. `osrm/OsrmRouter` — car only, `GET {base}/route/v1/driving/lng,lat;lng,lat?overview=full&geometries=polyline6`;
`routes[0].distance/duration/geometry`, code `NoRoute`/`NoSegment` → no route. `Routers.kt` is the
only place with the server addresses (`https://valhalla1.openstreetmap.de`,
`https://routing.openstreetmap.de/routed-car`), the User-Agent and the registry (Valhalla primary,
OSRM fallback). A new router = one adapter + registration; no screen changes.
`RoutingBoundaryTest` fails when a router's words, host or HTTP client leave its adapter.

**Queue and storage.** `RouteSync` (pure) watches all steps and stored routes, and fetches pending
legs one at a time (≥ 1 s apart, FOSSGIS' limit) while routing is on, the one-time notice was shown
and `NetworkMonitor.online` is true. Answers go to `leg_routes` (02-data-model.md); the screens
observe them, so a fetched leg redraws by itself. Off-road guard (500 m / 5×), retry and fallback:
R36. **Car fallback:** when a `motorbike` request gets `NoRoute` (Valhalla `motor_scooter` has no
path to Lũng Cú, error 442), `RouteSync.fetch` asks the same routers again with `car`
(`LegRoutes.profilesFor`: motorbike → [motorbike, car], every other profile → itself); the stored
row keeps `profile` = motorbike and sets `usedProfile` = car (`LegRoute.carFallback`), the line is
drawn like any routed line and `LegMeasure.carRoute` makes the leg chip/sheet add "tuyến ô tô".
An answer stored before the fallback existed (`NO_ROUTE`, `usedProfile` = motorbike,
`LegRoute.needsCarRetry`) is pending once more and asked with the car profile.

**Credit.** Wherever a routed line is drawn (trip map, Trip detail mini map, "Bản đồ của tôi",
share image/page) the attribution adds a line "Đường đi: Valhalla · FOSSGIS · © OpenStreetMap
(ODbL) · Sửa bản đồ"; "Sửa bản đồ" opens https://www.openstreetmap.org/fixthemap (the OSM routing
servers' terms ask for the OSM credit and this link). The share image prints the address instead
(wrapped at " · " when too wide). With routing off no router line is drawn and no credit shown.
Screens: `img/road-routing/03-hagiang-trip-map-roads.png`, `04-share-image-roads.png`,
`07-routing-off-straight.png`, `10-airplane-stored-routes-draw.png` (stored routes draw offline).

## Layers (bottom → top, `maplibre/OverlayLayers.kt` `addOverlayLayers`, above the basemap)

Widths are dp and interpolate linearly with zoom between the stops z5 / z9 / z13 (clamped
outside); "→" gives the selected-leg value. Colour = route Signal `#E4572E` (the specimen's route orange, docs/08), the
selected leg SignalDeep `#B83A16`. Selected features have `line-sort-key` 2 (drawn on top).

| # | Layer id | Filter `kind` | Paint |
| --- | --- | --- | --- |
| 1–55 | Positron basemap (tuned) | | |
| 55b | `route-travelled` | `travelled` = true (R37) | route colour, halo width 12 / 16 / 24, blur 4 → 8 (z5 → z13), opacity 0.38, round cap/join; under the shadow/casing so the line stays crisp on top |
| 56 | `route-shadow` | `ground` | Forest `#1F2A24`, width 5 / 8 / 16, blur 3 → 8 (z5 → z13), opacity 0.28 → 0.16, translate (0, 1 dp), round cap/join |
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

| Mode | Stroke kind | Icon colour | Icon (`TopoIcons`, docs/08 §5) |
| --- | --- | --- | --- |
| Đi bộ | `walk` — thin dots | SignalDeep `#B83A16` | Walk |
| Xe máy | `ground` | SignalDeep `#B83A16` | Motorbike |
| Ô tô | `ground` | SignalDeep `#B83A16` | Car |
| Xe buýt/xe khách | `ground` | SignalDeep `#B83A16` | Bus |
| Tàu hỏa | `ground` | SignalDeep `#B83A16` | Train |
| Máy bay | `flight` — dashed great-circle arc | SignalDeep `#B83A16` | Plane |
| Thuyền | `boat` — dots | SignalDeep `#B83A16` | Boat |
| none | `ground` | — | — (no badge) |

The line itself is always route Signal (SignalDeep when selected): the mode reads from the stroke
kind and the badge icon, never from a hue (docs/08: one route colour family). `map/TransportStyle.kt`
(one table for map, timeline chips and mode sheet) gives every mode the same SignalDeep `#B83A16` tint.

**Badges** (`rememberTransportBadges`, bitmaps drawn in code): 24 dp white disc with a soft
shadow, a 1.5 dp ring in the mode colour at 55 % alpha, the icon (60 % of the disc) in the mode
colour. Selected leg: 28 dp disc filled with the mode colour, white ring, white icon.
Tapping a badge — or the leg line itself, within 11 dp, for legs that dropped their badge —
opens the leg's mode sheet.

## Step pins (`map/Pins.kt`)

Bitmaps drawn in code (no font glyphs or sprite sheet), each edged with a 1 dp 31 % Forest line (`0x501F2A24`, no drop shadow), the step
number (R2 position, 1-based) in bold:

| Kind | Used for | Look |
| --- | --- | --- |
| START | first step of a route | 26 dp Trail `#2D7A46` disc, 3 dp white ring, white number |
| STEP | other steps; every pin when the route is hidden (Thêm từ ảnh) | 24 dp Waypoint `#2B6CB0` disc, 3 dp white ring, white number |
| END | last step of a route | 26 dp Forest `#1F2A24` disc, 3 dp white ring, white number |
| CONTEXT | other steps in the location picker | 22 dp Forest disc, 2 dp white ring |
| DRAFT | a draft step (R20) on the trip map and the trip-detail / step-detail mini maps | 22 dp translucent white disc, dashed Waypoint ring, **no number**; never START/END, no leg to or from it (the route and legs join the published steps around it) |
| selected / highlighted | tapped pin; step in Step detail / Step form | same pin ×1.3, Signal `#E4572E` ring, on a 54 dp translucent Signal halo |

No clustering: overlapping pins stay visible (a Hà Giang loop at country zoom is a tight group).

### All trips ("Bản đồ của tôi", R14)

`AllTripsPresenter` (`map/MapPresenters.kt`) + `OverlayLayers.kt`: one GeoJSON LineString per trip with ≥ 2 steps (R3 order; flight legs
drawn as the same arc, every other leg straight; simplified like above; no dashes or badges — the
colour identifies the trip) and one point per step.
Layers (dp at z5 / z9 / z13, "→" selected): `trips-shadow-layer` (same soft shadow as
`route-shadow`), `trips-casing-layer` (white, 4 / 6.4 / 9 → 5.6 / 8.6 / 12), `trips-layer` (trip
colour, 2 / 3.2 / 5 → 3.2 / 5 / 7.5), `trip-stops-layer` (circles in the trip colour, radius
3 / 4.5 / 6 → 4 / 6 / 7.5, white stroke 1.5 → 2.5). The selected trip is sorted on top. A tap
within 18 dp of a line or dot selects that trip; the camera fits all trips.
Screenshot: `img/26-my-map.png`.

### Recorded tracks (R16)

`OverlayLayers.kt` `addTrackLayers` + `toTrackFeatures`: one GeoJSON source
(`tracks-src` on trip maps, `all-tracks-src` on Bản đồ của tôi) with one LineString per drawn
segment (`TrackMath.displayLines`: one line per track segment, Douglas–Peucker 5·10⁻⁵°; a pause
splits the line), properties `track` (id) and `live` (the open track).

| Layer id | Paint (dp at z5 / z9 / z13) |
| --- | --- |
| `track-casing` | white, width 3.2 / 4.6 / 6.5, opacity 0.9, round cap/join |
| `track-line` | Forest `#1F2A24` (`Palette.Track`), width 1.6 / 2.6 / 4, round cap/join, solid |

Forest is the darkest colour on the map and is not the route colour (Signal), a leg mode or one of the 8
trip colours of R14 (docs/08: one route colour only, so the track is Forest, not a second hue), and a
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

`OverlayLayers.kt` `addStopSuggestionLayers` + `toStopFeatures`: one
GeoJSON source `stop-suggest-src` with one Point per current suggestion (its centre), property
`key` (`<trackId>@<startAt>`). Two circle layers right above `track-line`, below the leg badges
and step pins:

| Layer id | Paint (radius dp at z5 / z10 / z14) |
| --- | --- |
| `stop-suggest-halo` | track Forest `#1F2A24` fill, opacity 0.16, radius 7 / 11 / 18, 1 dp ink stroke at opacity 0.5 |
| `stop-suggest-dot` | white fill, radius 3.5 / 5 / 6, 2.5 dp ink stroke |

Track colour on a soft halo says "found on your track"; a hollow white dot cannot be mistaken for
the filled, numbered step pins. Drawn on the trip detail header map and the full trip map only
(not on Bản đồ của tôi, the step map or the picker). Not tappable; the list on the trip detail
(docs/03 §3c) is where the user acts. The markers follow the list (a dismissed or added
suggestion's marker goes at once) and do not change the camera fit (the stays lie on the
tracks, which the fit already includes). Screenshot: `img/stop-suggest/05-trip-map-markers.png`.

### My location (R22, `map/maplibre/MyLocationLayers.kt`)

One GeoJSON source `my-location-src` (a single Point with property `r20`) and two circle layers,
added by `TripMap` and `AllTripsMap` right above the tracks / stop suggestions and below the leg
badges and step pins: `my-location-src-accuracy` (Waypoint `#2B6CB0`, docs/08, opacity 0.16, 1 dp stroke at
0.35; radius in dp = `accuracy / 0.07464 / cos(lat)` at zoom 20, interpolated with base 2 between
zoom 0 → 0 and zoom 20) and `my-location-src-dot` (radius 8 dp, white 3 dp stroke). The source is
the one fix of the last tap (`DeviceLocation.requestFix`: GPS with fine permission, else network,
else last known); the MapLibre location component is **not** used (it keeps a provider running while
the map is open, and a fix per tap is enough and light on battery). A new fix flies the camera to it
(`MapConfig.MY_LOCATION_ZOOM` = 15, never zooming out; padding keeps it above the carousel) and, on
the trip map, counts as the user's own move: the carousel re-measure does not re-fit afterwards.
Android 12+ with only "approximate" location still gives a fix, with a larger accuracy circle.

## Static map for sharing (R18, `map/StaticTripMap.kt`, `map/MapFit.kt`)

The "Ảnh dọc" and "Trang xem offline" (04-business-rules.md R18) need the trip map as a bitmap,
without a visible map view. Two steps:

1. **Basemap** — `MapEngine.snapshot` of the offline basemap ("Bản đồ", never satellite imagery,
   whose terms forbid storing it); on Android a MapLibre `MapSnapshotter` with `Positron.STYLE_URL`, size 960 × 820 logical
   px × pixel ratio 2.5 (`SummaryImage.MAP_*`), logo off, the camera from `MapFit`, and the same
   repaint as the live maps applied once the style loads through `getLayer(id)`
   (`BasemapPlanner.declared`: `Positron.REPAINT` + the Vietnamese-first labels on `Positron.LABEL_LAYER_IDS`). Tiles come from the
   network or, offline, from MapLibre's store (an offline pack, R15, or the ambient cache). The
   snapshot runs on the main thread and is cancelled after **15 s** (`SNAPSHOT_TIMEOUT_MS`).
2. **Overlay** — drawn by us on a `Canvas` over the snapshot, not as runtime style layers in
   the snapshotter's style (not tried): drawing it ourselves gives the same picture with and
   without a basemap (the fallback below needs the drawing anyway) and reuses the pin bitmaps. Points are placed with our own
   Web Mercator projection (`MapFit.project`: world = 512 · 2^zoom px, the MapLibre tile size),
   which matches the snapshot. Order bottom → top: legs (ground: white casing 6.6 + Signal
   `#E4572E` 3.8 px per logical px; flight dashed, boat/walk dotted, paths from `LegGeometry` or the
   stored road route (R36) like the live map), recorded tracks (white 4.6 + Forest 2.6, `TrackMath.displayLines`),
   numbered step pins (`Pins.kt` bitmaps at the snapshot's pixel ratio; start and end on top),
   then the attribution text in a translucent box in the bottom-right corner (basemap credit,
   plus the router credit with the fix-the-map address when a leg is routed, R36).

**Fit** (`MapFit.fit`, pure, `TripShareTest`): every step and track point inside the map minus 70
px padding, zoom capped at `FIT_MAX_ZOOM` (14); one point → `SINGLE_POINT_ZOOM`; none → the
Vietnam overview (`FALLBACK_CENTER`, `OVERVIEW_ZOOM`) — the camera rules below.

**No basemap.** Snapshot error or timeout (offline outside every downloaded area, style not
cached) → the map is the overlay on the plain `MapConfig.BACKGROUND` (`#EFEBDA`), with the
attribution still drawn; the share screen says "Không tải được nền bản đồ …". Never a crash.
Screens: `img/share-trip/06-airplane-image.png` (offline pack), `img/share-trip/07-no-basemap.png`.

## Camera rules

| Case | Rule |
| --- | --- |
| No steps | centre (16.0, 106.0) — Vietnam overview — zoom 5 |
| One step | centre on it, zoom 12 |
| Two or more | fit the bounds of **every drawn point** (steps and flight-arc points, so an arc never leaves the screen) inside the padded area, cap zoom at 14. Padding: 40 dp on the trip detail header and step detail (36 dp Thêm từ ảnh, 56 dp default); full trip map 48 dp on each side **plus the measured height of the card carousel at the bottom**, so no pin is drawn under the cards |
| Step focused (full trip map, R37) | after the carousel stops: glide (`animateTo(CameraMath.stepPlan)`) to centre the step in the visible map above the cards, zoom = fit of the step + its arriving leg (else leaving leg; no flights), ≤ `STEP_MAX_ZOOM` 13.5, ≥ 5, 12 with no leg |
| Leg selected (mode sheet open) | animate to fit the leg's path, zoom ≤ 13, bottom padding = max(carousel padding, 55 % of the map height) so the leg stays visible above the sheet |
| Picker opening | target point at zoom 13 (see R6 for which point); overview at zoom 5 when none |
| Picker chip ("Vị trí của tôi" or one of the trip's steps) | fly to that point (`focusZoom`, ≥ 11) |
| "Xem toàn bộ hành trình" (trip map) | clear the selection, fit all pins above the cards, zoom ≤ `FIT_MAX_ZOOM` (14) |
| "Vị trí của tôi" (trip map, My map) | animate to the fix at zoom `max(current, 15)` (≤ 19), carousel padding kept |
| Carousel swipe settles on a card | that card's step is selected and the camera moves to it (R37); a pin/card tap moves the camera only after the carousel's own scroll to the card ends |
| System animations off (animator duration scale 0) | every move above is a jump (R37) |

Animation length (`CameraMath.flyDurationMs`, tested in `CameraMathTest`):
`350 ms + 1.5 ms/km + 160 ms per zoom level`, clamped to 400–1800 ms (R37; was 450–1600).
A step move eases in and out over exactly that time (MapLibre `easeCamera`, no zoom-out bump);
`adb logcat -s CameraPlan` prints its zoom, length and padding.

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
* No online geocoding: the app does not call Nominatim/LocationIQ in this phase. Place search and
  automatic names use the bundled gazetteer below (R23, R24). The picker's chips are "Vị trí của
  tôi" (one device fix, never stored) and the trip's own steps.
* A search result moves the camera like a chip (zoom 8.5 for a province, else the picker zoom).

*Changed from the reference:* polarsteps docs/22 §1.3/§2.6 search places through LocationIQ and
tap-to-move the pin; neighbour steps are drawn there as a dotted line, here as small context pins.

## Place search — the offline gazetteer (R23, R24)

* **Data:** `app/src/main/assets/places/vn_places.tsv` (UTF-8, ~0.9 MB, ~0.3 MB compressed in the
  APK): the 34 provinces of the 2025 merger (with the merged former provinces as aliases), ~3 200
  communes/wards, cities, towns, villages, hamlets and ~5 000 travel places (attractions,
  viewpoints, peaks, passes, waterfalls, caves, bays, beaches, islands, lakes, museums/historic
  sites, camp sites, notable places of worship), each with its province. Format and kinds: the
  header of `tools/gazetteer/build_gazetteer.py`.
* **Source and licence:** OpenStreetMap data © OpenStreetMap contributors, **ODbL 1.0** — the
  asset's first line, `app/src/main/assets/licenses/OpenStreetMap-ODbL.txt`, and the attribution
  line under every result list ("Địa danh © OpenStreetMap (ODbL)"). The bundled file is a derived
  database: it is shipped under the ODbL, and the generator is in the repository.
* **Generator:** `tools/gazetteer/build_gazetteer.py` (pyosmium + shapely) reads the Geofabrik
  Vietnam extract (`download.geofabrik.de/asia/vietnam-latest.osm.pbf`) at development time; the
  recipe is `tools/gazetteer/README.md` (~1 min). The same extract gives the same bytes. It stops
  unless the extract has exactly 34 `admin_level=4` provinces; province polygons are rebuilt from
  the relations' member ways (the area assembler drops some broken ones).
* **Loading:** `data/PlaceIndex.kt` parses the asset once per process on a background thread
  (logcat `PlaceIndex: 21748 places in ~4000 ms` on the debug emulator build; release builds not
  measured); screens read `PlaceIndex.gazetteer` (null until ready, the picker then shows "Đang tìm
  địa danh…") and the step form awaits it. Searching is in memory (`Gazetteer.search`, a linear
  scan of ~22 000 places on a background dispatcher, debounced 120 ms); nothing is sent anywhere.
* **Updating** after an administrative change: edit `FORMER_PROVINCES` if provinces merge again,
  re-run the generator, check the counts it prints and `PlacesTest`'s bundled-asset tests.

## Offline maps (R15)

Screen in 03-screens.md §11, rules in 04-business-rules.md R15, numbers in `map/OfflineConfig.kt`.

* **Engine.** MapLibre's own offline store: `OfflineManager.createOfflineRegion` with an
  `OfflineTilePyramidRegionDefinition(<offline basemap style URL>, bounds, minZoom 0, maxZoom 14, pixelRatio =
  screen density, includeIdeographs = false)` and JSON metadata
  `{"key","name","description","paused"}`. MapLibre downloads style, sprite, glyphs and tiles of
  every source in the style (vector `openmaptiles` z0–14, Natural Earth relief raster z0–6) into
  `files/mbgl-offline.db` and serves them to every `MapView` when the network is gone — no change
  to the map code. The basemap repaint (`applyBasemap`), route layers, pins and badges are built
  at runtime after the style loads, so they work offline unchanged.
* **App side.** `OfflineMapStore` (`map/MapEngine.kt`), on Android `map/maplibre/MapLibreOfflineStore.kt` (one per process, in `AppContainer`, started in
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
  cache (MapLibre keeps up to 50 MB) and the file does not shrink. `OfflineMapStore.delete` therefore
  calls `OfflineManager.clearAmbientCache` + `packDatabase` afterwards (measured: 33,7 → 28,2 →
  0,07 MB deleting Ninh Bình then Hà Giang; the fonts stay while any area uses them).
* **Provider note.** OpenFreeMap is a donated public instance: downloads are kept modest on
  purpose (two ready-made regions; every area within OpenFreeMap's offline rule "≤3,000 tiles or
  50 MB per offline download" — R31: estimate ≤ 50 MB and ≤ 3 000 tiles, vector z0–14 + relief
  raster z0–6, else refused without splitting; no country/province packs; one revalidation per
  process). A production release with many users should self-host the
  OpenMapTiles data (or use a paid provider) before offering offline areas widely; only the
  offline provider's style URL changes, `OfflineConfig` re-measures `BYTES_PER_TILE`/`FIXED_BYTES`.
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
(today downloads stop when the process dies and resume at the next start), dark map, clustering for trips with many steps, satellite imagery offline (Esri's terms forbid it), 3D terrain, on-device routing (road legs need the
FOSSGIS router, R36; offline new legs stay straight until online), turn-by-turn directions, photo thumbnails inside pins, current-location
button and following the user on the map (location is used only while recording a track, R16),
snapping recorded tracks to roads (GPX export/import is done, R25: an imported track is drawn
like a recorded one), a self-hosted or paid tile source
for production (also for offline areas, see "Provider note").
