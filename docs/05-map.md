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
| Trip detail header | 220 dp tall | no (static preview; expand button opens Trip map) | pins + route |
| Trip map | full screen | pan + pinch zoom; rotate/tilt off | pins + route, tap pin |
| Step form location card | small | no | the step's point |
| Step detail | card | no | whole trip, this step highlighted |
| Location picker | full screen | pan + zoom | fixed centre pin, other steps as context pins |

## Layers (bottom → top)

1. `background` — solid `#EDE7DA`.
2. `osm` — raster tiles.
3. `route-casing-layer` — white line, width 7 dp, round joins/caps.
4. `route-layer` — primary green (`#1F5C45`) line, width 4 dp; GeoJSON LineString of R3 points.
5. `steps-layer` — symbol layer, one icon per step, `icon-allow-overlap` on so no pin hides.

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
Screenshot: `img/20-trip-map-pins-above-cards.png` (Hà Giang sample, 8 pins, all above the cards).

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
routes (snap-to-roads / OSRM), current-location button (needs location permission; tracking is
out of scope).
