# Routed legs: feasibility study (road distance and road-following lines)

Status: **research only, 2026-11-10**. Nothing here is implemented; the rules in 01–07 still say
"no routing" (R3, R10, R12). This doc collects the evidence and a proposed design for the
follow-up branch.

Request (captain, quoted as given):

> hãy tạo task nghiên cứu tính khả thi của việc lộ trình khi esimate khoảng cách phải theo đường di
> chuyển thực tế chứ không phải chim bay, route nối giữa các điểm cũng phải draw như là dẫn đường
> chứ không phải là đường chim bay như hiện tại. nếu được thì tách nhánh code luôn sau khi đã cập
> nhật giao diện theo skills mới

Our reading (not the captain's words): (1) a leg's km should be the road distance, (2) the line
between two steps should follow the road like a navigation app, (3) if feasible, open a code branch
once the new design has landed.

## 1. Today

* `domain/usecase/Legs.kt`: `Leg.distanceKm` = haversine (`Geo.distanceKm`); `LegGeometry.path`
  = straight segment for every mode except `PLANE` (lifted great-circle arc).
* `domain/usecase/Ordering.kt`: `statsOf` = sum of haversine legs ("đường chim bay", R10), or the
  ended GPS tracks' total when that is at least as long ("đã ghi GPS").
* Consumers of `LegGeometry.path`: `map/MapPresenters.kt:106,203,221` (trip map, all-trips map)
  and `map/StaticTripMap.kt:56` (share image). Leg km text: `ui/trips/LegUi.kt:31`; labels
  `stat_km_straight`, `leg_sheet_subtitle` ("… km đường chim bay").
* Modes (`TransportMode`): WALK, MOTORBIKE, CAR, BUS, TRAIN, PLANE, BOAT, or none. Stored on the
  arrival step (R12, not R14). Room schema version 7. OkHttp 4.12 is already a dependency.

## 2. Candidate sources

Terms were read on 2026-11-10 for our use: a non-commercial app on users' phones, storing each
leg's route in the local database, drawing it on a non-vendor renderer (MapLibre), and offline
use. "Unverified" means the page could not be read or does not say.

| Source | Account/key | Free quota | Profiles useful here | Storing results / map | Verdict |
| --- | --- | --- | --- | --- | --- |
| **Valhalla on FOSSGIS** `valhalla1.openstreetmap.de` | none | max **1 req/s**, "no heavy usage" | `auto`, `motorcycle`, **`motor_scooter`**, `bus`, `pedestrian`, `bicycle` | Terms do not forbid storing routes (they restrict tiles only). OSM data is ODbL, so the app must show attribution and a link to `openstreetmap.org/fixthemap`. Any map is allowed. | **Use (primary)** |
| **OSRM on FOSSGIS** `routing.openstreetmap.de/routed-{car,bike,foot}`, `router.project-osrm.org` | none | 1 req/s; "reasonable, non-commercial" (OSRM wiki) | car, bike, foot; **no motorbike** | Same FOSSGIS terms; credit OSRM and show ODbL attribution | Fallback for car only |
| OpenRouteService (HeiGIT) | key (account) | Standard plan 2 000 directions/day, 40/min (forum and plans pages, not read first-hand) | car, hgv, cycling, foot, wheelchair; **no motorbike** | Results under CC BY 4.0 (some sources say CC BY-SA). Attribution "© openrouteservice.org by HeiGIT, Map data © OpenStreetMap contributors". Storing appears allowed (unverified). | Possible later fallback |
| GraphHopper Directions API | key | 500 credits/day, **non-commercial only** | limited profile set on the free plan (list unverified) | ToS: results "may be temporarily cached on the client side". Permanent storage per leg conflicts with this. Allowed "with and without showing a map". | Excluded (storage) |
| LocationIQ | key | 5 000/day, 2/s | driving (walking/cycling per third-party docs) | Free plan: may "cache request-response pairs for upto 48 hours". Attribution link required. | Excluded (storage) |
| Mapbox Directions | token + account | free tier on pricing page (not read) | driving, driving-traffic, walking, cycling; **no motorbike** | Directions docs give limits but no storage or non-Mapbox-map rule; ToS silent. Unverified. | Excluded (unclear terms, token) |
| Esri / ArcGIS routing | ArcGIS Location Platform account + key | about 20 000 simple routes/month (search result) | car, truck, walking | Only geocoding has a documented stored/not-stored split; routing storage terms not found | Excluded (account, unclear terms) |
| VIETMAP Maps API | account + key | "60 000 transactions/month", described as free for the first two months (ambiguous) | car, bike, foot, **motorcycle** | Terms not read | Candidate if OSM quality fails; needs a captain account |
| Goong (VN) | account + key | not read | not read | not read | Not assessed |
| On-device: Valhalla (C++, MIT), GraphHopper core (Apache-2.0), BRouter (MIT) | none; needs an OSM extract | unlimited | per engine | ODbL: the shipped routing graph is a derivative database; show attribution | Not now (size, native builds; see §6) |

Polarsteps uses LocationIQ (their key, reference only); its free plan's 48-hour cache limit is
why we do not follow them.

## 3. Tests on real legs (2026-11-10, keyless endpoints)

Script: per leg, haversine vs routed; `snap` = metres from the step to the road the engine used.
Stops are the sample-data coordinates (`data/SampleData.kt:45-68`).

| Leg | Straight | OSRM car | Valhalla auto | Valhalla motor_scooter | snap |
| --- | --- | --- | --- | --- | --- |
| TP Hà Giang → Cổng Trời Quản Bạ | 27.3 km | 48.4 km / 35 min | 48.5 km / 68 min | 48.5 km / **93 min** | 21 m |
| Quản Bạ → Yên Minh | 16.1 | 49.1 / 37 | 49.2 / 70 | 49.3 / 96 | 23 m |
| Yên Minh → Đồng Văn | 28.7 | 46.1 / 35 | 46.2 / 67 | 46.2 / 110 | 23 m |
| Đồng Văn → Mã Pí Lèng | 6.5 | 11.4 / 8 | 11.4 / 17 | 11.4 / 28 | 21 m |
| Mã Pí Lèng → Mèo Vạc | 8.9 | 11.0 / 8 | 11.0 / 15 | 11.0 / 23 | **306 m** |
| Tràng An → Hoa Lư | 3.7 | 13.3 / 19 | 10.7 / 27 | 11.5 / 39 | **714 m** |
| Hoa Lư → Tam Cốc | 8.4 | 15.3 / 15 | 15.3 / 25 | 14.0 / 26 | 52 m |
| Tam Cốc → Tràng An | 6.1 | **29.3** / 31 | 29.4 / 49 | 25.2 / 55 | **714 m** |

Findings:

* **Road km is 1.2–3× the straight line** in Hà Giang. The whole loop Hà Giang → Mèo Vạc is
  87.5 km straight and **166 km by road** (both engines). Today's "chim bay" figure understates
  the trip by half.
* **Geometry is plausible.** The routed loop passes 8 m from Tam Sơn (Quản Bạ), 19 m from the Bắc
  Sum viewpoint, 49 m from Sủng Là and 15 m from Mèo Vạc town (Photon/OSM positions). OSRM and
  Valhalla lines differ by at most 268 m.
* **Durations differ widely.** OSRM car gives 125 min for the 166 km loop, an average of 80 km/h on
  mountain passes, which is not credible. Valhalla `motor_scooter` gives 349 min (29 km/h), in line
  with how the loop is ridden (our judgement, not measured).
* **Motorbikes must avoid expressways.** Hà Nội (Hoàn Kiếm) → Ninh Bình city:
  * Valhalla `auto`: 95.5 km, 78 km of it flagged as expressway (CT.01).
  * Valhalla `motorcycle`: 116.5 km, still 17 km on expressway.
  * Valhalla `motor_scooter`: 92.5 km on QL.1, **0 km** of expressway.
  * OSRM driving: 95.0 km via CT.01.

  Motorbikes are not allowed on Vietnamese expressways, so **Xe máy → `motor_scooter`**. OSRM has
  no motorbike profile at all.
* **Off-road stops snap to the wrong road.** The sample "Tràng An" stop is 714 m inside the
  protected karst area. It snaps to a road on the far (west) side, so Tam Cốc → Tràng An becomes
  29 km for a 6 km chord. With the stop on the Tràng An road by the wharf (20.2544, 105.9173), the
  same leg is **10.2 km / 11 min** against 4.7 km straight. The design needs a guard (§4).
* **Walking:** the FOSSGIS OSRM foot server returned `{"code":"NoRoute"}` for Hà Giang → Quản
  Bạ. Valhalla `pedestrian` routed every leg.
* **Size and speed:** each call took 0.6–1.3 s. The full geometry of the 166 km loop is 8 618
  points (32.9 KB as polyline6). Simplified to 10 m it is 1 434 points (**7.1 KB**); to 20 m,
  922 points (4.9 KB).

## 4. Which modes follow roads, failure and offline

| Mode | Line | Engine profile | Why |
| --- | --- | --- | --- |
| Xe máy | routed | Valhalla `motor_scooter` | avoids expressways (tested) |
| Ô tô | routed | `auto` | |
| Xe buýt/xe khách | routed | `bus` (or `auto`) | `bus` exists; untested |
| Đi bộ | routed | `pedestrian` | |
| Tàu hỏa | straight (as today) | none | no free engine routes on rails. A later option is matching to OSM railway lines. |
| Máy bay | arc (as today) | none | |
| Thuyền | dotted straight (as today) | none | river and sea trips (Tràng An, Nho Quế) are not on the road network |
| Chưa chọn | **captain decides** | car? or straight? | the app never guesses a mode (R12) |

Proposed failure and offline rules (to be settled in the ship):

* A routed line is **stored per leg** in Room, so the map, totals and share image work offline
  once a route was fetched while online.
* A leg that has no stored route yet (offline, error, rate limit, no route) is drawn straight as
  today. Its km is labelled "đường chim bay" and is queued. Requests are retried one at a time, at
  most 1 per second, when `NetworkMonitor` reports online.
* Guard for off-road stops: the line runs from the stop, along a short straight connector, to the
  road and back. The engine's own snap distance is used. A leg falls back to straight when the
  snap is larger than a threshold, or when road/straight > a cap; the proposal is 1 km and 5×,
  the Tràng An case shows why. A wrong-looking route can always be reset to straight from the leg
  sheet (proposal).
* Totals: trip km = sum of routed legs + straight km of the others. The source label says which
  ("theo đường đi" / mixed); exact wording is a design question. The GPS rule (R10) stays: ended
  tracks win when their total is ≥ the figure.

## 5. Data model, recompute, export, tracks

* **New table `leg_routes`** (migration 7 → 8, `MigrationTest` extended, no destructive
  fallback). Key: `to_step_id` (FK `steps` ON DELETE CASCADE), because the leg's mode already
  lives on the arrival step.
  * Inputs: `from_lat`, `from_lng`, `to_lat`, `to_lng`, `profile`.
  * Outputs: `provider`, `distance_m`, `duration_s`, `geometry` (polyline6, simplified to about
    10 m), `fetched_at`, `status` (`ok` / `no_route` / `too_far`).
* **Recompute is by key comparison.** A stored route is valid only while its inputs equal the
  current leg: rounded from/to coordinates plus the profile. That covers every trigger without
  hooks: a stop moved, the mode changed, a step inserted or deleted or its date changed (the
  `from` changes), a draft published. Stale rows are replaced on the next fetch.
* **Backup (R26)** copies `lutrinh.db`, so routes are included. An older backup is migrated on
  restore like an app update. No format change.
* **GPX export (R25):** unchanged in the first ship, because the importer turns every `rte` into a
  track, which would duplicate routes on re-import. Exporting routes is a later decision.
* **Share (R18):** `StaticTripMap` uses the stored geometry. The attribution gains the routing
  credit.
* **GPS tracks (R16):** stay as they are, recorded truth drawn on top. A leg fully covered by an
  ended track could hide its routed line later; that is not in the first ship.
* **Privacy:** routing sends stop coordinates (not photos or notes) to the FOSSGIS servers in
  Germany. This is the first time user positions leave the device; tiles do not carry positions.
  Docs 01/05 must say so, and the captain decides whether routing is on by default or opt-in.

## 6. Architecture (R35 pattern, platform-neutral)

* `domain` (pure):
  * `RouteProfile` (the mode → profile table above);
  * `RoutingProvider` interface: `suspend fun route(from, to, profile): RouteResult`;
  * `RouteResult` = `Ok(distanceM, durationS, geometry, snapFromM, snapToM)` | `NoRoute` |
    `Unavailable(reason)`;
  * `LegRoutePlan` (pure): which legs need a fetch, the guard, the fallback, totals;
  * a polyline6 codec and a simplifier (`Polyline.simplify` exists).
* `routing/valhalla/` is the only place that knows the URL, JSON shape and User-Agent, the same
  way `map/maplibre/` owns MapLibre. `routing/osrm/` is a second adapter. A
  `Routers.registry`/`active` mirrors `Basemaps`. A `RoutingBoundaryTest`, like
  `MapBoundaryTest`, keeps the endpoints out of every other package. FOSSGIS asks apps not to
  hard-code their URLs: with no server of ours, the URL lives in one adapter constant that can be
  swapped by an update.
* Repository `LegRouteRepository` (Room) and a fetch queue (1 req/s, online only).
  `LegGeometry.path` takes an optional stored route, so `MapPresenters` and `StaticTripMap` only
  pass it through. The map interface is unchanged: `MapLeg` already carries a point list.
* iOS: the same table and rules. The adapter is `URLSession` + `Codable` on the same Valhalla
  JSON. docs/07 gets one row.
* Not now: on-device routing. A Vietnam extract is 332 MB (Geofabrik `vietnam-latest.osm.pbf`,
  2026-11-08). Valhalla mobile needs native builds for two platforms, and GraphHopper Android
  storage support was dropped according to a third-party forum. Stored routes already cover the
  offline case that matters: legs planned or recorded while online.

## 7. Verdict

**Feasible.** Free, keyless, OSM-based routing covers our real legs with plausible lines, and
Valhalla's `motor_scooter` profile fits Vietnamese motorbike rules. The terms allow storing a few
routes per leg in a non-commercial app, given attribution and at most 1 request per second.

Risks:

* FOSSGIS is volunteer-run and may withdraw service without notice. Mitigation: stored routes
  keep working, an OSRM car fallback exists, and the adapter can be swapped for a keyed provider
  (ORS, VIETMAP) by an update.
* Stops off the road network give odd routes (the guard in §4).
* Durations are estimates and are not shown in the first ship (proposal).
* Privacy disclosure (§5).

Rough effort: 4–5 crew-days, after the design branch lands.

* Domain, adapter and tests: 1.5 d.
* Room, migration and queue: 1 d.
* Map, share and stats wiring: 1 d.
* Docs 01–07, R-rule updates, screenshots: 1 d.

Decisions for the captain before the ship: mode "Chưa chọn" routed or straight; routing on by
default or opt-in (privacy); whether durations are shown.

Evidence: commands and raw outputs are in the scout report
(`.mate/projects/vietmap/crews/scout-lutrinh-routing/report.md`, outside the repo).

## 8. Proposed ship outline (follow-up branch, after the design branch)

Build:

* `RoutingProvider` interface, a Valhalla adapter (+ an OSRM car fallback), `RoutingBoundaryTest`.
* `leg_routes` table (migration 7 → 8).
* Fetch queue: 1 req/s, online only.
* Routed lines on the trip map, all-trips map and share image; road km on legs and totals.
* Fallback to straight with the "đường chim bay" label; guard for off-road stops.
* Attribution plus the fixthemap link.
* R3/R10/R12 rewritten, a new rule R36, docs 01/02/05/07 updated, screenshots.

Out of scope: on-device routing, GPX `rte` export, map-matching tracks, durations in the UI.

Acceptance, one observable result per line:

* Motorbike legs avoid expressways, and the adapter is the only package that names the endpoint.
  verify: `./gradlew testDebugUnitTest --tests '*RoutingBoundaryTest*' --tests '*LegRoutePlanTest*'`
* A routed leg shows road km. verify: on the AVD, sample trip Hà Giang, leg sheet of Quản Bạ shows
  "48,5 km" (not "27,3 km đường chim bay"), screenshot in `docs/img/`.
* The line follows QL4C. verify: trip map screenshot at the Hà Giang zoom, with the Valhalla
  response logged by `logcat -s Routing`.
* Offline: stored routes still draw, and unrouted legs are straight with the chim bay label.
  verify: `cmd connectivity airplane-mode enable`, reopen the trip, screenshot; a new step added
  offline shows straight.
* Moving a stop or changing the mode refetches exactly that leg. verify: `LegRoutePlanTest`, and
  `sqlite3 databases/lutrinh.db "select to_step_id,profile,distance_m from leg_routes"` before
  and after.
* The migration keeps data. verify:
  `./gradlew testDebugUnitTest --tests '*MigrationTest*'` with `app/schemas/.../8.json` committed.
* Requests stay at 1 per second at most. verify: a unit test of the queue with a fake clock, and
  `logcat -s Routing` timestamps while opening a 9-leg trip.
* The full suites pass. verify:
  `./gradlew clean assembleDebug testDebugUnitTest` and
  `ANDROID_SERIAL=<own AVD> ./gradlew connectedDebugAndroidTest`; never emulator-5580 (the
  captain's data).
