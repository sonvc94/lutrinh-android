# 07 — Port to iOS (Swift / SwiftUI)

Goal: a native iOS app with the same features, rules and copy, sharing these docs as the spec.
Suggested floor: iOS 17 (SwiftData, `@Observable`, `PhotosPicker` with `Transferable`).

## Layer by layer

| Concern | Android (this repo) | iOS equivalent | Notes |
| --- | --- | --- | --- |
| Language | Kotlin 2.0 | Swift 5.10+/6 | |
| UI toolkit | Jetpack Compose + Material 3 | SwiftUI | Keep the palette (Theme row below); use native iOS controls rather than imitating Material |
| Screen state | `ViewModel` + `StateFlow`, `Loadable` (Loading/Ready/Missing/Failed) | `@Observable` class per screen + `enum Loadable<T>` | same four states |
| Streams from DB | Room `Flow` | SwiftData `@Query` in views, or `ModelContext` fetch + `NotificationCenter`/observation; GRDB `ValueObservation` if using GRDB | every write must refresh open screens |
| Navigation | Navigation-Compose string routes, one activity | `NavigationStack` with a `Route` enum (`.trip(id)`, `.tripMap(id)`, `.stepEdit(tripId, stepId?)`, …), `.sheet` for forms | picker result: binding or closure instead of `savedStateHandle` |
| Dependency wiring | `AppContainer` (manual) | `AppContainer` struct injected via `.environment` | no DI framework needed |
| Domain models | `data class` | `struct` (Equatable, Hashable, Identifiable) | ids `String` (UUID string), keep the same field names |
| Dates | `LocalDate`, `LocalDateTime` (java.time) | `DateComponents` or own `struct LocalDate {y,m,d}` / `LocalDateTime`; persist as ISO text `yyyy-MM-dd` / `yyyy-MM-dd'T'HH:mm` | do **not** store as `Date` in UTC — a step at 07:00 in Hà Giang must stay 07:00 |
| Instants | epoch ms `Long` | `Date` / `Int64` ms | |
| Validation, ordering, defaults | `Validation.kt`, `Ordering.kt` | `Validation.swift`, `Ordering.swift` (pure functions, `Comparable` sort descriptors) | port 1:1, port the unit tests with the same names |
| Use cases | `SaveTrip`, `DeleteTrip`, `SaveStep`, `DeleteStep` (suspend) | same classes with `async throws` `callAsFunction` | return `SaveResult` enum with associated values |
| Repositories | interfaces + Room impl | `protocol TripRepository`, `StepRepository` + SwiftData impl | keep them protocols for the later server/sync |
| Database | Room (SQLite), 3 tables, FK `ON DELETE CASCADE` | **SwiftData** `@Model Trip/Step/Photo` with `@Relationship(deleteRule: .cascade)`; or Core Data (cascade rule) / GRDB (same SQL as Android) | GRDB gives an identical schema if a shared SQLite file format ever matters |
| Cascade returns file names | DAO collects file names before delete, in a transaction | fetch photos of the trip/step first, delete, `save()`, then delete files | R7 order: DB first, files after |
| Photo picker | Android Photo Picker (`PickMultipleVisualMedia`, images, max 30), no permission | `PhotosPicker(selection:maxSelectionCount: 30, matching: .images)` | no Photo Library permission needed for PhotosPicker |
| Photo import (R11) | temp copy → `BitmapFactory` bounds → `ExifInterface` orientation → keep (+ remove GPS tags in place) or decode with `inSampleSize`, `Matrix` scale/rotate/mirror, `Bitmap.compress(JPEG, 85)` → `filesDir/photos/<uuid>.<ext>` | `PhotosPicker` item → `loadTransferable(type: Data.self)` (or a `FileRepresentation` transferable for big files) → `CGImageSourceCreateWithData`; size + orientation from `CGImageSourceCopyPropertiesAtIndex` (`kCGImagePropertyPixelWidth/Height`, `kCGImagePropertyOrientation`); **re-encode** with `CGImageSourceCreateThumbnailAtIndex` options `kCGImageSourceCreateThumbnailFromImageAlways: true`, `kCGImageSourceCreateThumbnailWithTransform: true` (applies orientation), `kCGImageSourceThumbnailMaxPixelSize: 2560`, then `CGImageDestinationCreateWithURL(…, UTType.jpeg)` + `kCGImageDestinationLossyCompressionQuality: 0.85` with no metadata; **keep** path: `CGImageDestinationCopyImageSource` with `kCGImageMetadataShouldExcludeGPS: true` (no re-encode) → `Application Support/photos/<uuid>.<ext>` | port `PhotoImportPlan` + `PhotoImportPlanTest` 1:1 (pure maths); ImageIO's thumbnail never upscales, matching "keep small images"; check HEIC → JPEG output; store only file names; exclude from iCloud backup? decide at release |
| Image loading | Coil 3 (local files only) | `UIImage(contentsOfFile:)` + downsampling with `CGImageSourceCreateThumbnailAtIndex`, or Nuke with local URLs | decode at view size |
| Photo viewer | `HorizontalPager` + custom pinch/pan | `TabView(.page)` + `MagnifyGesture`/`DragGesture`, or a `UIScrollView` zoom wrapper | same rule: swipe pages only when not zoomed |
| Map renderer | MapLibre Native Android 11.x | **MapLibre Native iOS** (`MLNMapView` wrapped in `UIViewRepresentable`) with the same `styleJson()`; alternative: MapKit `MKTileOverlay` with the OSM URL template + `MKPolyline` + annotations | MapLibre keeps layers/camera code nearly identical; MapKit is simpler but needs Apple attribution + OSM attribution |
| Tile config | `MapConfig.TILE_URL` etc. | `MapConfig.swift` with the same constants | one place |
| User-Agent | OkHttp interceptor via `HttpRequestUtil.setOkHttpClient` | `MLNNetworkConfiguration.sharedManager.sessionConfiguration.httpAdditionalHeaders["User-Agent"]` (MapKit overlay: subclass `MKTileOverlay.loadTile` with a `URLSession`) | `LuTrinh/<ver> (iOS; <bundle id>)` |
| Attribution | Compose text label, logo/(i) hidden | SwiftUI overlay text "© OpenStreetMap contributors"; MapLibre `logoView.isHidden`, `attributionButton.isHidden` | always visible |
| Pins | bitmaps drawn on `Canvas` | `UIGraphicsImageRenderer` images added with `style.setImage(_:forName:)`, symbol layer | numbered circles, same colours |
| Route line | GeoJSON source + 2 line layers | `MLNShapeSource` + 2 `MLNLineStyleLayer` | casing 7, line 4 |
| Camera fit | `getCameraForLatLngBounds(bounds, [l, t, r, b])` + cap zoom 14; full trip map adds the carousel height (measured with `onSizeChanged`) to the bottom padding | `mapView.cameraThatFitsCoordinateBounds(_:edgePadding:)` with `UIEdgeInsets(bottom: 48 + carouselHeight)` (measure with `GeometryReader`/`onGeometryChange`) then clamp `zoomLevel`; re-fit when the height changes | rules in 05-map.md |
| Location picker | fixed centre pin, camera-idle listener | same with `mapView(_:regionDidChangeAnimated:)` | |
| Strings | `res/values/strings.xml` (Vietnamese default) | `Localizable.xcstrings`, source language `vi` | copy the keys and texts; plurals not used yet |
| Theme | `ui/theme/Theme.kt` | `Color` assets: TerraceGreen `#1F5C45`, Clay `#B85C2E`, RiceGold `#E0A526`, Cream `#FBF8F2`, map background `#EDE7DA` | |
| App icon | adaptive vector (sun, karst peaks, terraces) | re-draw the same motif as a 1024 px asset | |
| Sample data | debug-only seeder | `#if DEBUG` seeder | same trips and coordinates |
| Unit tests | JUnit + Robolectric (in-memory Room) | XCTest / Swift Testing; in-memory `ModelConfiguration(isStoredInMemoryOnly: true)` | port test names 1:1 |
| UI tests | Compose `createAndroidComposeRule<MainActivity>` (`TripFlowsTest`: create/edit/delete trip, add step via quick-place chip), finds nodes by Vietnamese text / content description | XCUITest with the same four flows; find by accessibility label (`app.buttons["Chuyến đi mới"]`) | give the FAB-equivalent buttons the same labels |
| Picker language | `VietnameseLocale { … }` provides a `vi-VN` `LocalConfiguration` + resources **inside** the dialog content (a dialog window re-provides the device config) | `.environment(\.locale, Locale(identifier: "vi_VN"))` on the `DatePicker` (and the time picker); set `.environment(\.calendar, …)` with `firstWeekday = 2` for Monday-first weeks | device language must not change the picker |

## Porting checklist

1. Models + `Validation` + `Ordering` + use cases with the fakes from `app/src/test/.../Fakes.kt`;
   port `ValidationTest`, `OrderingTest`, `UseCasesTest` first — they define the behaviour.
2. SwiftData models + repositories; port `RoomRepositoryTest` (cascade, ordering, photo replace).
3. Photo store (import processing R11 with `PhotoImportPlanTest`, delete with path guard, R9
   cleanup on cancel).
4. Map wrapper (style JSON, layers, pins, camera rules, attribution, User-Agent).
5. Screens in the order of 03-screens.md; check each against its screenshot in `img/`.
6. Strings: all user-visible text from `strings.xml`, Vietnamese.

## Things that differ by platform (decide during the port)

* iOS has no system "back" button: the discard-changes dialog fires on the Cancel/back toolbar
  button and on interactive dismiss (`interactiveDismissDisabled` while dirty).
* Delete confirmation on iOS is usually a `confirmationDialog` with a destructive button.
* Date pickers: `DatePicker(.graphical)` in a sheet, forced to Vietnamese (row "Picker language");
  dates are validated on save (R5), as on Android.
* Large titles exist natively (`navigationBarTitleDisplayMode(.large)`).
