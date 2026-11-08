# Lữ Trình — product & engineering docs

Platform-neutral description of the app, written while building the Android version so that the
iOS (Swift/SwiftUI) version can be built from the same rules. Everything here is the **contract**;
the Kotlin code is one implementation of it. When the two disagree, fix one of them in the same
change.

Language: English for prose, Vietnamese for the on-screen copy (quoted as it appears in the app).

| Doc | Covers |
| --- | --- |
| [01-architecture.md](01-architecture.md) | Layers (UI → use cases → repositories → storage), modules/packages, dependency flow, what is local-only today and where a server plugs in later |
| [02-data-model.md](02-data-model.md) | Entities Trip / Step / Photo: fields, types, nullability, relations, storage format, validation limits |
| [03-screens.md](03-screens.md) | Every screen: purpose, states (empty / loading / error / content), actions, navigation graph, which Polarsteps flow inspired it and what was changed on purpose |
| [04-business-rules.md](04-business-rules.md) | Numbered rules R1–R10: ordering, date validation, new-step defaults, delete cascade, photo list rules, stats |
| [05-map.md](05-map.md) | OpenStreetMap tiles, tile server choice, User-Agent, attribution, markers, route line, camera rules, location picker |
| [06-photos.md](06-photos.md) | Picking (system picker, no storage permission), copying into app storage, cover, viewer, cleanup |
| [07-ios-port.md](07-ios-port.md) | Android choice → Swift/SwiftUI equivalent, per layer, plus a porting checklist |
| [img/](img/) | Emulator screenshots of each flow (listed in 03-screens.md) |

Source analysis (read-only, not part of this repo): `polarsteps/docs/` — mainly
`22-focus-trip-map-media.md` (trip/step screens, map, photos) and `11-trip-step-model.md`
(trip/step lifecycle). Only flows were used as inspiration; no assets, names, colours or copy.
