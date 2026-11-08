# 05 — Map (OpenStreetMap)

## Choice

* **Data / tiles:** OpenStreetMap raster tiles (256 px PNG, zoom 0–19), free, no API key.
* **Renderer:** MapLibre Native (Android `org.maplibre.gl:android-sdk` 11.x). Reasons: open
  source (BSD), same engine and style format on iOS (MapLibre Native iOS), GPU rendering, GeoJSON
  sources + line/symbol layers for route and pins, and a path to vector tiles or offline packs
  later without changing renderer. osmdroid was the alternative: Android-only, so the iOS port
  would need a second mental model.
* **Style:** built in code (`MapConfig.styleJson()`), no style URL to fetch:
  one `background` layer (`#EDE7DA`, shown while tiles load or when they fail) + one `raster`
  layer from source `osm`.

## Tile server — the one place to change it

`map/MapConfig.kt`:

| Constant | Value |
| --- | --- |
| `TILE_URL` | `https://tile.openstreetmap.de/{z}/{x}/{y}.png` |
| `TILE_URL_OSM_ORG` | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` (alternative) |
| `ATTRIBUTION` | `© OpenStreetMap contributors` |
| `USER_AGENT` | `LuTrinh/<versionName> (Android; vn.lutrinh.app)` |
| `MAX_ZOOM` | 19 |

Why `.de`: on 2026-11-09 every `*.openstreetmap.org` host (tile server and website) reset the
TLS connection from the development network in Vietnam, while `tile.openstreetmap.de` (the German
OSM community server, same OSM data, OSM-Carto-like style) returned tiles:

```
curl https://tile.openstreetmap.org/1/1/0.png  → curl: (35) Recv failure: Connection reset by peer
curl https://tile.openstreetmap.de/1/1/0.png   → 200
```

Check this again from users' networks before release; switching back is a one-line change.

**Usage policy.** Community tile servers are donated capacity. Their policy
(https://operations.osmfoundation.org/policies/tiles/, followed by the .de server) requires a
valid identifying User-Agent, visible attribution, honouring cache headers and no bulk/offline
download. That is fine for development and a small beta, **not for a production app**. Before
release: a paid OSM-based provider (e.g. MapTiler, Stadia, Thunderforest) or a self-hosted tile
server / vector tiles (OpenMapTiles + MapLibre style). Only `TILE_URL` (or `styleJson`) changes.

**User-Agent.** MapLibre downloads through OkHttp; at app start `MapSetup.init` installs an
OkHttp client whose network interceptor sets `User-Agent: MapConfig.USER_AGENT`. No personal
data is sent. On iOS set the header via `MLNNetworkConfiguration` (session configuration
`httpAdditionalHeaders`).

**Attribution.** Every map shows the text "© OpenStreetMap contributors" in its bottom-right
corner (a small label on a translucent white background, drawn by the app). The MapLibre logo
and the (i) attribution button are hidden because the label already gives the required credit
and MapLibre needs no logo.

**Caching.** MapLibre keeps its default ambient HTTP cache; no tiles are pre-downloaded.

## Map surfaces

| Where | Size | Interactive | Content |
| --- | --- | --- | --- |
| Trip detail header | 220 dp tall | no (static preview; expand button opens Trip map) | pins + legs |
| Trip map | full screen | pan + pinch zoom; rotate/tilt off | pins + legs + mode badges; tap pin → card, tap badge → leg mode sheet |
| Step form location card | small | no | the step's point |
| Step detail | card | no | whole trip, this step highlighted |
| Location picker | full screen | pan + zoom | fixed centre pin, other steps as context pins |
| Thêm từ ảnh review | 180 dp tall | no | numbered pins of the kept suggestions, no route (R13) |
| Bản đồ của tôi | full screen | pan + zoom | every trip: one coloured line + stop dots per trip; tap → select, tap the card → open (R14) |

## Layers (bottom → top)

1. `background` — solid `#EDE7DA`.
2. `osm` — raster tiles.
3. `route-casing-layer` — white line, width 7 dp, round joins/caps, under every leg.
4. `route-layer` — solid legs, width 4 dp.
5. `route-dashed-layer` — dashed legs, width 4 dp, `line-dasharray [2.2, 1.6]` (in line widths).
6. `route-dotted-layer` — dotted legs, width 4.5 dp, `line-dasharray [0, 1.8]` + round caps (dots).
7. `leg-badges-layer` — one round mode badge (26 dp) at the middle of each leg that has a mode.
8. `steps-layer` — symbol layer, one icon per step, `icon-allow-overlap` on so no pin hides.

The route source holds **one LineString feature per leg** (R12), with properties `mode` (code or
`none`) and `color`. Line colour is data-driven (`get("color")`), but MapLibre's `line-dasharray`
is **not** data-driven, so each dash kind is a separate layer filtered with
`["in", ["get", "mode"], [...modes of that kind]]`.

### Legs per transport mode (R12)

| Mode | Colour | Stroke | Icon (Material) |
| --- | --- | --- | --- |
| Đi bộ | `#6D4C41` | dotted | DirectionsWalk |
| Xe máy | `#E65100` | solid | TwoWheeler |
| Ô tô | `#3949AB` | solid | DirectionsCar |
| Xe buýt/xe khách | `#8E24AA` | solid | DirectionsBus |
| Tàu hỏa | `#455A64` | solid | Train |
| Máy bay | `#039BE5` | dashed **arc** | Flight |
| Thuyền | `#00897B` | dashed | DirectionsBoat |
| none | primary green `#1F5C45` | solid | — (no badge) |

One table (`map/TransportStyle.kt`) serves the map, the timeline chips and the mode sheet. Badges:
white disc, mode-coloured inner disc, white icon, drawn into bitmaps in code. Tapping a badge on the
full trip map opens the leg's mode sheet.

**Flight arc** (`Legs.arc`, unit-tested): a quadratic Bézier in plain lat/lng space from A to B,
control point at the midpoint moved sideways (to the left of A→B) by `0.2 ×` the A–B length;
25 points. Other modes are straight lines between the two steps (no routing). The camera fit uses
the steps only, so a strongly bent arc may touch the padding edge — accepted.

Screenshots: `img/23-trip-map-leg-modes.png` (Hà Giang sample: xe máy, ô tô, đi bộ, thuyền, and the Mèo Vạc → Du Già leg set to Máy bay by hand to show the arc),
`img/24-trip-map-legs-zoomed.png`, `img/25-leg-mode-sheet.png`.

### All trips ("Bản đồ của tôi", R14)

`map/AllTripsMap.kt`: one GeoJSON LineString per trip with ≥ 2 steps (R3 order; flight legs
drawn as the same arc, every other leg straight; no dashes or badges — the colour identifies the
trip) and one point per step.
Layers: `trips-casing-layer` (white, 6 dp; 9 dp when selected), `trips-layer` (trip colour,
3.5 dp; 5.5 dp when selected), `trip-stops-layer` (circles, radius 4.5 dp / 6 dp selected, trip
colour, white 2 dp stroke). The selected trip is sorted on top. A tap within 18 dp of a line or dot selects
that trip; the camera fits all steps of all trips (same rules as above).
Screenshot: `img/26-my-map.png`.

Pins are bitmaps drawn in code (no font glyphs or sprite sheet needed): a filled circle (28 dp,
34 dp when highlighted) with a white ring and the step number (R2 position, 1-based) in white.
Normal colour = primary green, highlighted/selected = rice gold `#E0A526`; context pins in the
picker = clay `#B85C2E`.

## Camera rules

| Case | Rule |
| --- | --- |
| No steps | centre (16.0, 106.0) — Vietnam overview — zoom 5 |
| One step | centre on it, zoom 12 |
| Two or more | fit the bounds of all steps inside the padded area, then cap zoom at 14. Padding: 40 dp on embedded maps; full trip map 48 dp on each side **plus the measured height of the card carousel (incl. its 24 dp offset and the navigation bar) at the bottom**, so no pin is drawn under the cards |
| Picker opening | target point at zoom 13 (see R6 for which point); overview at zoom 5 when none |
| Quick-place chip | animate to the place at zoom 13 |

The camera is fitted once per change of the step list — or of the bottom padding, i.e. when the
carousel is first measured — after the view has its size; the user's own panning is not reset
while both stay the same. A single step is centred in the whole view (well above the cards).
Screenshot: `img/20-trip-map-pins-above-cards.png` (Hà Giang sample when it had 8 steps, all
pins above the cards).

*Inspired by* polarsteps docs/22 §2.4 (single point zoom 11, fit-to-points with padding and max
zoom 14). *Changed:* zoom 12 for a single point (village scale suits rural Vietnam better), no
pitch/bearing.

## Location picker behaviour

* The pin is a fixed overlay in the centre; the user drags the map under it. The coordinate
  label updates when the camera stops (camera-idle event), 5 decimals (~1 m).
* "Dùng vị trí này" returns the centre point. Back returns nothing (step keeps its old point).
* No geocoding: the user types the place name; the app does not call Nominatim/LocationIQ in this
  phase (would be a network call; Nominatim's policy also forbids autocomplete-style use).
  Quick-place chips cover the main northern destinations offline.

*Changed from the reference:* polarsteps docs/22 §1.3/§2.6 search places through LocationIQ and
tap-to-move the pin; neighbour steps are drawn there as a dotted line, here as small context pins.

## Not done (later)

Offline packs, vector style, dark map, clustering for trips with many steps, road-following
routes (snap-to-roads / OSRM — legs stay straight or arcs), great-circle flight paths (the arc is
decorative), current-location button (needs location permission; tracking is
out of scope).
