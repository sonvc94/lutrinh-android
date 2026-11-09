# 01 — Architecture

## Goals

1. **Local first.** All data lives on the device. There is no account and no API call; the only
   network traffic is the map style and its tiles, fonts and icons (see 05-map.md). GPS
   tracks (R16) are recorded and stored on the device only.
2. **Portable domain.** Business rules live in a layer without platform types (no Android, no UI,
   no database), so the iOS app can port them line by line and run the same unit tests.
3. **Server later without touching the UI.** Screens talk to use cases; use cases talk to
   repository *interfaces*. A remote or syncing repository can replace the local one.

## Layers

```
┌──────────────────────────────────────────────────────────────┐
│ UI (screens + view models)                                    │  Compose / SwiftUI
│   observes streams, calls use cases, owns transient form state│
├──────────────────────────────────────────────────────────────┤
│ Domain                                                        │  pure language code
│   models: Trip, Step, Photo, GeoPoint, TripSummary,           │
│          Track, TrackPoint, LocationFix                       │
│   rules: TripValidator, StepValidator, TripOrder, StepOrder,  │
│          routeOf, Geo, statsOf, NewStepDefaults               │
│          TrackFilter, TrackMath (R16)                         │
│   use cases: SaveTrip, DeleteTrip, SaveStep, DeleteStep,      │
│          TrackRecorder (recording state machine), DeleteTrack │
│   ports (interfaces): TripRepository, StepRepository,         │
│          TrackRepository,                                     │
│          PhotoFileStore, Clock, IdGenerator                   │
├──────────────────────────────────────────────────────────────┤
│ Data                                                          │  Room / SwiftData
│   local database (5 tables), mappers, repository impls        │
│   photo file store (app-private folder)                       │
│   sample data seeder (debug only)                             │
├──────────────────────────────────────────────────────────────┤
│ Map adapter                                                   │  MapLibre / MapLibre iOS
│   basemap config (one place), map views, route layers, pins   │
├──────────────────────────────────────────────────────────────┤
│ Tracking adapter                                              │  Service / CLLocationManager
│   location foreground service: GPS fixes → TrackRecorder,     │
│   recording notification                                      │
└──────────────────────────────────────────────────────────────┘
```

Dependencies point downwards only. Domain depends on nothing. Data implements the domain
interfaces. The UI gets everything from one composition root (`AppContainer`), which wires the
concrete classes by hand (no DI framework), so the iOS app can use a plain `struct`/`class` too.

## Packages (Android)

| Package | Content |
| --- | --- |
| `domain/model` | Immutable value types |
| `domain/repository` | Interfaces the domain needs (`TripRepository`, `StepRepository`, `TrackRepository`, `StopDecisionRepository`, `PhotoFileStore`, `Clock`, `IdGenerator`) |
| `domain/usecase` | `Validation.kt` (limits, inputs, error enums, validators, `TripExtension` for R17), `Ordering.kt` (comparators, route, distance, stats, new-step defaults), `UseCases.kt`; tracks (R16): `TrackLogic.kt` (`TrackRules`, `TrackFilter`, `TrackMath`, `TrackFormat`), `DisplayFormat.kt` (km, durations, in-trip days: the only formatters), `TrackRecorder.kt` (start/pause/resume/stop, fixes, process-death recovery); stop suggestions (R17): `StopSuggestions.kt` (`StopRules`, `StopDetection`, `StopSuggestions`); sharing (R18): `TripShare.kt` (stats, image photos, file names), `TripPage.kt` (offline HTML + size cap); gallery photos of a stop (R19): `StopPhotos.kt` (`StopPhotos` window/de-dup/offer, `GallerySelection`); draft steps and trip cover (R20, R21): `Drafts.kt` (`published`, `stepNumbers`, `TripCover`); offline places (R23, R24): `Places.kt` (`PlaceText` normaliser, `Gazetteer.search / nearest / parse`, `AutoName`, `AutoTitle`), `PlaceLabel.kt` (step-or-place labels) |
| `data/local` | Database entities, DAOs, type converters, mappers |
| `data` | `RoomTripRepository`, `RoomStepRepository`, `RoomTrackRepository`, `RoomStopDecisionRepository`, `PhotoStorage`, `GalleryReader` (R19: MediaStore read by time window, gallery permission state, source id/size of picked URIs), `PlaceIndex` (R23: parses the bundled gazetteer `assets/places/vn_places.tsv` once, in the background), `SampleData` |
| `tracking` | `TrackRecordingService` (foreground service type location: platform GPS → `TrackRecorder.onFix`, GPS on/off with the state) and `TrackNotifications` (channel + notification) |
| `map` | `MapConfig` (style URL, basemap tuning, attribution, User-Agent, zooms), `MapSetup`, `MapViews`, `RouteLayers`, `Pins`, `CameraMath`; offline areas: `OfflineConfig` (pure: areas, zooms, size estimate, limits), `OfflineState`, `OfflineMaps` (MapLibre offline store), `NetworkMonitor`, `OfflineIndicator`; share map (R18): `MapFit` (pure fit + Web Mercator projection), `StaticTripMap` (snapshot + drawn overlay) |
| `share` | `TripExporter` (renders, encodes, writes `cacheDir/share/`, FileProvider `content://` URIs, `ACTION_SEND`, MediaStore save) and `SummaryImage` (the 1080 × 1920 PNG on a `Canvas`) |
| `ui/*` | One file per screen + view model; `ui/common` holds shared widgets and dialogs; `ui/tracks` the "Ghi hành trình" control and the track list |
| `AppContainer`, `LuTrinhApp`, `MainActivity` | Composition root, app start, single activity |

## Data flow

* **Read:** the database exposes observable queries (Kotlin `Flow`). The repository maps rows to
  domain models. A view model turns the stream into a screen state `Loading | Ready | Missing |
  Failed` (see 03-screens.md). Any write anywhere updates every open screen automatically.
* **Write:** a screen collects user input into an input object (`TripInput`, `StepInput`) and calls
  a use case. The use case validates, builds the domain model (ids, timestamps), writes through the
  repository in one transaction, and returns `Saved(id)` or `Invalid(errors)`. Errors are enum
  values; each screen maps them to localized messages.
* **Recording (R16):** `TrackRecorder` is a single app-wide object (in `AppContainer`) holding the
  open track as an observable value. The UI starts the service, which calls `start`; pause /
  resume / stop come from the screen or the notification and go straight to the recorder; the
  service observes the open track to switch GPS on/off, update the notification and stop itself.
  Each accepted fix is written at once (point + track totals in one transaction). At app start
  `recoverInterrupted` ends tracks left open by a dead process.
* **Files:** photo files are written before the step is saved (import) and deleted after the
  database transaction that dropped their rows (see R7, R8 and 06-photos.md).

## Identity and time

* Ids are random UUID strings generated on the device, so rows created offline never collide
  with server ids later.
* `createdAt` / `updatedAt` are epoch milliseconds (UTC instants), set by the use case from an
  injectable `Clock`.
* Trip dates are calendar dates without time zone; a step's arrival is a local date-time without
  time zone ("what the traveller's watch showed"). See 02-data-model.md.

## Adding a server later (not built)

The analysis of the reference app (polarsteps docs/11 §2, docs/21) shows an offline-first sync:
local rows are the truth, a background job pushes changes and pulls remote ones. To add that:

1. Add `syncState`/`deletedAt` (tombstone) columns, and keep a deleted row until the server has
   acknowledged it instead of removing it at once.
2. Add a `SyncingTripRepository` that wraps the local one and enqueues changes; the UI and use
   cases do not change.
3. Upload photo files from `photos/` and store the remote URL next to `fileName`.
