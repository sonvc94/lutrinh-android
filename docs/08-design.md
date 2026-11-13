# 08 — Design: "Topo outdoor" (bản đồ địa hình)

Platform-neutral design spec for Lữ Trình (Android now, iOS later).

**Standing rule: this topo-outdoor style is the style of every screen, dialog, popup, sheet,
menu, map and share output of the app, present and future.** A new or changed piece of UI follows
this file; a change of look updates this file in the same branch (AGENTS.md standing rule).
Screens not yet redone are listed in `docs/09-redesign-inventory.md`.

Android tokens: `ui/theme/Theme.kt` (`Palette`, `Radii`, `Lift`, `hairline`, `Modifier.ledge()`),
`ui/theme/Type.kt` (scale, `numerals()`), `ui/theme/TopoIcons.kt`; shared components in
`ui/common/` (`Topo.kt`, `Buttons.kt`, `Readout.kt`, `RouteCover.kt`, `Common.kt`); map repaint:
`map/basemap/OpenFreeMap.kt`; iOS: §13 and docs/07 "Theme".

## 1. Direction

Captain (2026-11-10), word for word: *"https://github.com/alchaincyf/huashu-design/blob/master/assets/style-specimens/app/06-topo-outdoor.html
t muon ap dung thiet ke nay cho toan bo app cua toi. hay lam Lai tat ca cac man hinh, popup da lam
VA tu sau nay tro di hay su dung Phong CACH nay"*. This replaces the earlier "Sổ bản đồ" (limestone
+ chàm) direction.

Source: huashu-design `assets/style-specimens/app/06-topo-outdoor.html` (MIT, © 2026 alchaincyf).
Nothing is copied from it but values: the tokens below are re-derived from its CSS/SVG, and the
render is kept as a reference (`img/topo/specimen.png`; side by side with the app:
`img/topo/specimen-vs-app.png`).

The style in one line: **an outdoor topographic map: warm map paper, raised off-white cards with a
solid ledge, one signal-orange route with a white casing, green/blue/forest pins, contour lines as
the only ornament, big condensed numbers over small bold labels, forest-dark chips for "here".**

Decisions (Mate, 2026-11-10): light only in this batch (the specimen has no dark variant; a
derived dark theme is follow-up, §14). Fonts: Barlow Condensed (OFL) bundled; the specimen's body
font Noto Sans SC (CJK) is replaced by its Latin + Vietnamese member **Noto Sans** (OFL), no CJK.

## 2. Palette (tokens)

Each token with the specimen value it comes from. "Derived" = not in the specimen, made from its
values for a role the specimen does not show.

| Token | Hex | Specimen source | Role |
| --- | --- | --- | --- |
| Parchment | `#EFEBDA` | phone/map `background:#EFEBDA` | screen background, map paper |
| Card | `#FBFAF2` | `.pan`, `.card`, `.chip` | cards, panels, sheets, dialogs, menus, map chips |
| CardDim | `#F6F3E6` | derived (Card → Parchment) | filled card a step down (surfaceContainerHighest) |
| Sand | `#E8E3CE` | `.mini` | thumbnail wells, image placeholders, bar tracks, selected list row |
| Grid | `#E3DEC8` | profile grid lines | dividers (outlineVariant), buildings on the map |
| Ledge | `#D9D4BE` | `.card` `box-shadow:0 2px 0 #D9D4BE` | the 2 dp ledge under a card; province borders |
| Ink | `#2A2A22` | `body color` | all body text and titles |
| InkMuted | `#6B6A58` | `.lbl`, `.m` | labels, meta, secondary text (on Parchment/Card/CardDim only) |
| Outline | `#86826B` | derived (InkMuted, lighter) | field borders, hollow discs: 3:1 lines |
| Forest | `#1F2A24` | dark chip, end pin, callout | primary: filled buttons, FAB, switch, selection, end pin, snackbar |
| Signal | `#E4572E` | route, elevation line | the route, the km bar, accents as lines/fills (never text) |
| SignalDeep | `#B83A16` | derived (Signal darkened) | Signal as text/icons: links, mode icons, selected leg |
| SignalWash | `#FBE3D9` | Signal at 22–25 % on Card | accent wells (e.g. selected pin halo) |
| Trail | `#2D7A46` | start circle | start pin, tertiary (cover tag) |
| Waypoint | `#2B6CB0` | waypoint circle | step pins, my-location dot |
| Contour | `#C09A68` | contour strokes | contour motif, country borders, empty-state mark |
| Vegetation | `#D8E4C4` | woods | parks and woods on the map |
| Water | `#BFDDE6` | water | water on the map |
| Easy wash / ink | `#CFE8CF` / `#1F5A2A` | `.dif` easy | badge |
| Moderate wash / ink | `#FDE6B8` / `#7A4A00` | `.dif` moderate | badge (draft, "Nháp") |
| Hard wash / ink | `#F6C9C0` / `#8A2410` | `.dif` hard | badge; errorContainer / onErrorContainer |
| Error | `#B3261E` | system red (not in specimen) | errors, destructive text |
| MapLabel / MapHalo | `#5A5948` / Card | derived (InkMuted, darker) | map labels |

Material roles (Android `lightColorScheme`): primary Forest / onPrimary Card; primaryContainer Sand
/ on Forest; secondary SignalDeep; secondaryContainer Sand / on Ink; tertiary Trail; tertiaryContainer
EasyWash / EasyInk; background = surface = Parchment; on* Ink; surfaceVariant Sand / onSurfaceVariant
InkMuted; surfaceContainer* = Card (Highest = CardDim); inverseSurface Forest / inverseOnSurface Card;
outline Outline; outlineVariant Grid; error Error / errorContainer HardWash / onErrorContainer HardInk.

Hues on a screen: paper, ink, forest, plus signal orange for the route; trail green and waypoint
blue appear only as pins; badge washes only in badges. No other hue.

### Trip colours (R14, "Bản đồ của tôi" only)

Eight data colours, each ≥ 3:1 on the map paper: Signal, Waypoint, Trail, Forest, ModerateInk,
`#8C6A3C`, `#2F7480`, `#6E4E9A` (`Palette.TripColors`). Shown as lines and legend keys, never as text.

### Map

| Element | Look |
| --- | --- |
| Background / residential / buildings | Parchment / Sand / Grid with a Ledge outline |
| Woods, parks / water | Vegetation / Water |
| Country / province borders | Contour / Ledge |
| Labels / halo | MapLabel / Card (Vietnamese first, docs/05) |
| Route (every leg, every mode) | Signal core on a white casing (specimen 5 px on 9 px) with a soft forest shadow; selected leg SignalDeep, wider |
| Leg mode | told by stroke (solid / dots / dashed arc) and the badge icon (SignalDeep), never by hue |
| My location | Waypoint dot, white ring, translucent Waypoint accuracy circle (no system blue) |
| GPS track (R16) | Forest thin line on a white casing |
| Pins | start Trail disc · step Waypoint disc · end Forest disc, each in a 3 dp white ring (specimen r11/r8, 3 px white stroke), white number; context Forest; draft hollow, dashed Waypoint ring; selected ×1.3 with a Signal ring on a translucent Signal halo; a 1 dp 31 % forest edge, no drop shadow |
| Satellite (Esri) | own white labels on a Forest halo; borders Sand / Contour |

## 3. Type

* **Noto Sans** (SIL OFL, `assets/licenses/NotoSans-OFL.txt`) for all words, subset to Latin +
  Vietnamese; weights 400, 500, 700 only. Substitution for the specimen's Noto Sans SC: same design
  family, no CJK glyphs (the app has no Chinese text; the CJK file would cost megabytes).
* **Barlow Condensed** (SIL OFL, `assets/licenses/BarlowCondensed-OFL.txt`) 700, tabular figures
  (`tnum`), +0.01 em, for numbers that stand alone (specimen `.n`): km, counts, times in readouts,
  pin numbers, coordinates. Never for words. Android: `TextStyle.numerals()`.
* Both bundled, never downloaded (local-only app).

The specimen's scale (px at its 360 px phone = dp/sp):

| Size / line | Weight | Styles | Specimen |
| --- | --- | --- | --- |
| 40 / 40 | 700 | displayLarge (readout numbers, with numerals()) | `.stats .n` 40 px |
| 34 / 40, 30 / 36 | 700 | displayMedium, displaySmall (compact readout) | — |
| 28 / 36 | 700 | headlineMedium: screen and trip titles | list title 28 px |
| 22 / 30 | 700 | headlineSmall, titleLarge: sheet, dialog and empty-state titles (the 26 step is gone, R38) | — |
| 17 / 24 | 700 | titleMedium: card titles | `.card b` 17 px |
| 17 / 26 | 400 | bodyLarge: callouts, notes | callout 17 px / 1.5 |
| 15 / 22 | 700 | titleSmall | status bar 15 px |
| 14 / 21 | 400 | bodyMedium: meta lines | `.m` 14 px |
| 14 / 20 | 700 | labelLarge: buttons, action chips | `.chip` 14 px 700 |
| 13 / 18 | 700, +0.06 em | labelMedium: eyebrows and stat labels; also data chips (`LegChip`: km · time of a leg, 13, captain 2026-11-10) | `.lbl` 13 px 700 .06 em |
| 13 / 19 | 400 | bodySmall | axis labels 13 px |
| 12 / 16 | 700, +0.04 em | labelSmall (floor; only the 9 sp map credit is smaller) | — |

## 4. Space, radii, lines, elevation, motif

* **Space scale** (`Space`, R39): 4 · 8 · 12 · 16 · 24 · 32 (and 0). Every padding, gap and spacer in
  the app is one of these: screen edge 16 (`Space.L`); title / eyebrow inset 24; card padding 12
  (`TopoCard`), panel padding 16 (`TopoPanel`); list gap 12; gap between related controls 8; chip
  and badge insets 4 / 8; form-field gap 16. Sizes (thumbnails, 44 dp buttons, FAB clearance
  `Space.XXL * 3`) are not spacing. A new off-scale value is a design change: change the scale
  here first. Guard `spacingOnTheSpaceScale` (§12), its 2 exceptions carry a reason.
* **Four radii** (`Radii`): 8 badges · 12 chips, buttons, fields, thumbnails, menus, snackbars
  (Material `extraSmall`/`small`) · 18 cards (`medium`) · 22 panels, sheets, dialogs (`large`,
  `extraLarge`). Nothing is a full pill; `CircleShape` only for dots, discs and pins.
* **Ledge, not shadow, on paper**: a card sits on a solid 2 dp Ledge band (`Modifier.ledge()`),
  no blur. Dividers inside a card: 1 dp Grid (`hairline`).
* **Lift** (`Lift`), only for things floating over the map: chips and map buttons `Lift.Chip`
  (4 dp, specimen `0 4px 10px`), panels, the FAB and the offline note `Lift.Panel` (8 dp, specimen
  `0 8px 24px`). Nothing else casts a shadow.
* **Selection**: 2 dp Forest border (map carousel) or a Sand row (sheets); never a colour bar.
* **Contour motif**: thin Contour lines (1 dp, index line 2 dp) are the only ornament: concentric
  ovals in a route cover, the target mark of the empty state. Never behind text, never a fill.
* No gradients. The specimen's profile fill (Signal at 22–25 %) is a flat translucent fill.
* Touch targets ≥ 44 dp (Material 48 dp kept).

## 5. Icons

**Sizes** (`IconSize`, R39): 18 in a button, chip, row or badge · 22 in a bar, map button or card
arrow · 24 on its own (empty-state mark, battery guide) · 40 the Atlas mark. Disc / number markers
are 28 dp (`DiscRole` on the trip timeline, battery-guide steps, from-photos stops); the photo
buttons are 24 dp squares. Guard `spacingOnTheSpaceScale` checks every `Icon(…size(…))`.

`TopoIcons` (own drawings, no third-party licence), in the specimen's line grammar: a 24 grid, one
2 unit stroke, **round caps and round joins** (like its route and contour lines), boxes with 2 unit
corners, filled dots where a point is meant. Every icon of bars, FABs, menus, map buttons, rows,
chips and leg modes is a TopoIcon; Material icons remain only in files listed as not yet redone in
docs/09 (guard: `topoIconsAndTypeWeights`). Path data (SVG, 24 × 24):

| Icon | Lines (stroke 2, round caps/joins) | Dots (filled) |
| --- | --- | --- |
| Add | `M12 5v14M5 12h14` |  |
| Back (mirrors in RTL) | `M19 12H6M11 6.5L5.5 12 11 17.5` |  |
| More |  | `M12 3.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2zM12 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2zM12 17.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z` |
| Share | `M12 14.5V4M8 7.5l4-4 4 4M8.5 10H7a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-1.5` |  |
| Navigate (R42) | `M20 4L4 10.5l6.5 2.5L13 19.5z` |  |
| Edit | `M4.5 19.5h4l10-10-4-4-10 10zM12.5 7.5l4 4` |  |
| Delete | `M4.5 6.5h15M9.5 6.5V4h5v2.5M6.5 6.5l1 13h9l1-13M10.5 10.5v5.5M13.5 10.5v5.5` |  |
| Route | `M8.5 18H15a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h6.5M6 15.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5` | `M18 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z` |
| Atlas | `M12 3.5c5 0 8.5 3.3 8.5 8s-3.8 9-8.8 9-8.2-3.7-8.2-8.3S7 3.5 12 3.5zM12 7.5c2.9 0 4.8 2 4.8 4.4s-2.1 4.6-4.9 4.6-4.6-2-4.6-4.4 1.8-4.6 4.7-4.6z` | `M12 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z` |
| MyLocation | `M12 6a6 6 0 1 0 0 12a6 6 0 1 0 0-12M12 2.5V6M12 18v3.5M2.5 12H6M18 12h3.5` | `M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0 -4z` |
| Layers | `M12 4l8 4-8 4-8-4zM4 12.5l8 4 8-4M4 16.5l8 4 8-4` |  |
| Expand | `M14 4h6v6M20 4l-7 7M10 20H4v-6M4 20l7-7` |  |
| Fit | `M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5` |  |
| AddStep | `M12 21s-6.5-5.5-6.5-11a6.5 6.5 0 0 1 13 0c0 5.5-6.5 11-6.5 11zM12 7v6M9 10h6` |  |
| AddPhotos | `M5 7.5h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2zM3 16.5l4-4 3 3 2-2 3 3M19 3v7M15.5 6.5h7` |  |
| Photo | `M6 5h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM4 16l5-5 4 4 2.5-2.5L20 17` | `M15 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0 -3z` |
| PhotoOff | `M6 5h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM4 4l16 16` |  |
| Download | `M12 4v11M7.5 10.5L12 15l4.5-4.5M4.5 19.5h15` |  |
| Upload | `M12 15V4M7.5 8.5L12 4l4.5 4.5M4.5 19.5h15` |  |
| FileOpen | `M6 3.5h8l4 4v13H6zM13.5 3.5V8H18M9 14.5h6M12 11.5l3 3-3 3` |  |
| Restore | `M5 12a7 7 0 1 0 2-5M4.5 4v4.5H9M12 8.5V12l2.5 2.5` |  |
| Settings | `M4 7h9M17 7h3M15 5v4M4 12h3M11 12h9M9 10v4M4 17h11M19 17h1M17 15v4` |  |
| Page | `M8 3.5h8a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2zM9 8h6M9 11.5h6M9 15h4` |  |
| Needle | `M12 12v10M12 2.5a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0-9` | `M12 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z` |
| Search | `M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13M15.5 15.5L20 20` |  |
| Close | `M6 6l12 12M18 6L6 18` |  |
| Check | `M5 12.5l4.5 4.5L19 7.5` |  |
| Time | `M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17M12 7.5V12l3 2` |  |
| Place | `M12 21s-6.5-5.5-6.5-11a6.5 6.5 0 0 1 13 0c0 5.5-6.5 11-6.5 11z` | `M12 8a2 2 0 1 0 0 4a2 2 0 1 0 0 -4z` |
| Previous (mirrors in RTL) | `M14.5 6L8.5 12l6 6` |  |
| Next (mirrors in RTL) | `M9.5 6l6 6-6 6` |  |
| Note | `M4 6.5h12M4 11h12M4 15.5h6M13 20v-2.5l5.5-5.5 2.5 2.5-5.5 5.5z` |  |
| Camera | `M4 8h3.5l2-2.5h5l2 2.5H20v11H4zM12 10a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7` |  |
| Video | `M5 7h8a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 13 17H5a1.5 1.5 0 0 1-1.5-1.5v-7A1.5 1.5 0 0 1 5 7zM14.5 10.5l6-3.5v10l-6-3.5` |  |
| Play |  | `M8 5.5v13l10.5-6.5z` |
| Map | `M3.5 6.5l5.5-2 6 2 5.5-2v13l-5.5 2-6-2-5.5 2zM9 4.5v13M15 6.5v13` |  |
| Satellite | `M9 12l3-3 3 3-3 3zM10.5 10.5L7 7M13.5 13.5L17 17M4.5 9.5l5-5M14.5 19.5l5-5M15.5 4.5a4 4 0 0 1 4 4` |  |
| Walk (mirrors in RTL) | `M11 9.5l-3 3v3M11 9.5l2.5 1 1.5 3 2.5 1M11 9.5l-1 5 3 2.5 1 3.5M10 14.5l-2.5 6` | `M13 3.7a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0 -3.6z` |
| Motorbike | `M6 13a3 3 0 1 0 0 6a3 3 0 1 0 0-6M18 13a3 3 0 1 0 0 6a3 3 0 1 0 0-6M6 16l3.5-5h5L18 16M9 11L7.5 8H5M14.5 11l1-3h3` |  |
| Car | `M3.5 17v-4.5l2.5-5.5h12l2.5 5.5V17zM3.5 12.5h17M6 17v2.5M18 17v2.5` | `M7.5 13.7a1.1 1.1 0 1 0 0 2.2a1.1 1.1 0 1 0 0 -2.2zM16.5 13.7a1.1 1.1 0 1 0 0 2.2a1.1 1.1 0 1 0 0 -2.2z` |
| Bus | `M7 4h10a2 2 0 0 1 2 2v11H5V6a2 2 0 0 1 2-2zM5 11.5h14M8 17v3M16 17v3` | `M8 13.2a1.1 1.1 0 1 0 0 2.2a1.1 1.1 0 1 0 0 -2.2zM16 13.2a1.1 1.1 0 1 0 0 2.2a1.1 1.1 0 1 0 0 -2.2z` |
| Train | `M7 3.5h10a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2zM5 10h14M8.5 16.5L6 20.5M15.5 16.5l2.5 4` |  |
| Plane | `M12 3.5c1 0 1.5 1 1.5 2.5v4l7 4v2l-7-2v4l2 1.5v1.5l-3.5-1-3.5 1v-1.5l2-1.5v-4l-7 2v-2l7-4V6c0-1.5.5-2.5 1.5-2.5z` |  |
| Boat | `M3.5 15.5h17l-2.5 4H6zM6 15.5V11h12v4.5M12 11V4M12 5l5 4.5` |  |
| ChevronDown | `M6 9.5l6 6 6-6` |  |
| ChevronUp | `M6 14.5l6-6 6 6` |  |
| Pause | `M9 6v12M15 6v12` |  |
| Stop | `M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z` |  |
| Record |  | `M12 6a6 6 0 1 0 0 12a6 6 0 1 0 0 -12z` |
| Battery | `M5 8h12a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2zM21 10.5v3M11.5 9.5L9.5 12h3l-2 2.5` |  |
| CloudOff | `M7 18h10a3.5 3.5 0 0 0 .6-6.95A5.5 5.5 0 0 0 7.2 9.1 4.5 4.5 0 0 0 7 18zM4 4l16 16` |  |
| Calendar | `M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM4 10h16M8.5 3v4M15.5 3v4` |  |

## 6. Components

| Component | Rule | Android |
| --- | --- | --- |
| Screen heading | Eyebrow (labelMedium, InkMuted: a count, a date range) over a 28 bold title | `ScreenHeading`, `Eyebrow`; trip list `LargeTopAppBar` |
| Top bar | Parchment (Card while content scrolls under it), no elevation; TopoIcons; actions Ink | `TopAppBar` (theme); trip list keeps Parchment when collapsed |
| Card / list row | Card paper, 18 dp, 14 dp padding, Ledge under it; a 96 × 72 thumbnail (12 dp) on the left; title titleMedium, meta bodyMedium InkMuted, badge under | `TopoCard` |
| Panel | Card paper, 22 dp, 18 × 16 padding; floating over the map with `Lift.Panel` | `TopoPanel` |
| Stats readout | specimen stats row: optional eyebrow, numbers (Barlow 40 / 30 compact) over bold labels, spread across, then the km scale bar | `KmReadout`, `KmScaleBar` |
| Km scale bar | Sand track (rounded), alternating Signal blocks, InkMuted ticks and numbers | `KmScaleBar` |
| Badge | 13 bold, 8 dp radius, 2 × 8 padding, wash + ink pair (easy/moderate/hard) | `TopoBadge`, `DraftBadge` |
| Chip | Card paper, 12 dp, labelLarge (14; a data chip such as `LegChip` is 13); outlineVariant edge on paper, `Lift.Chip` on the map; dark chip = Forest + Card text | `LegChip`, `MapChip` |
| Buttons | Filled Forest + Card text; tonal Sand; outlined Outline edge; text Forest; all 12 dp (`shapes.small`) | guard `noPillButtons` |
| FAB | Forest chip with icon + label, 12 dp, `Lift.Chip`, content description = label | `ChipFab` |
| Map buttons | 44 dp Card squares, 12 dp, `Lift.Chip`, Ink TopoIcon | `MapButton` |
| Bottom sheet | Card paper, 22 dp top corners, drag handle InkMuted; eyebrow + titleLarge; rows transparent; selected = a Sand row for one-line rows (InkMuted never on Sand), else a radio | `ModalBottomSheet` (theme), `LegModeSheet` |
| Dialog / popup (R38) | Card paper, 22 dp, titleLarge 22, body bodyMedium; **three types** in `ui/common/Dialogs.kt`: `ConfirmDialog` (question: no icon, two text buttons, destructive confirm in Error), `NoticeDialog` (notice or permission rationale: a TopoIcon in Signal, filled Forest main button 12 dp, optional text dismiss), `MessageDialog` (error / result: one text button) + `BusyDialog` (spinner + text, no buttons); time and date pickers stay Material pickers | `Dialogs.kt`, `DiscardDialog`, `ConfirmDeleteDialog`, `RoutingNoticeDialog` |
| Menu | Card paper, 12 dp, labelLarge-size rows, TopoIcons Ink | `DropdownMenu` (theme) |
| Snackbar | the specimen's dark callout: Forest, Card text, 12 dp | `SnackbarHost` (theme) |
| Inputs | outlined, 12 dp, Card fill, Outline edge, Forest when focused, label InkMuted | `topoFieldColors()` |
| Switch / radio / checkbox | Forest when on; off = CardDim track with an Outline edge | theme |
| Segmented control | 12 dp outer corners, selected Sand + Forest check | theme (`ShareTripScreen`) |
| Empty state (R38) | one `MessageState`: Contour target mark (40 dp), titleLarge 22, bodyLarge text, optional filled button; inside a card or list `compact` = mark 24 dp, titleMedium, bodyMedium, no button. Used by: trip list, trip detail, "Bản đồ của tôi" (no trips), place search (no result), stop photos (none found) | `MessageState` |
| Mode badge on a leg | rounded tile 8 dp, Card paper, hairline edge + `Lift.Chip` shadow, mode TopoIcon Ink (selected: filled with the mode colour, Card icon) — never a circle, so it does not read as a pin | `rememberTransportBadges` |
| Buttons on a photo | 8 dp rounded squares (× remove, cover check, stop-photo tick), Forest 85 % + Card icon; media add row = three equal buttons on one row (icon over label) | `StepEditScreen`, `CoverPicker`, `StopPhotosUi` |
| Timeline (trip detail) | numbered discs (Trail start, Waypoint stops, Forest end, white ring, Barlow number; draft hollow Outline) on a Grid rail; each step a `TopoCard`; day labels as eyebrows; leg chips between | `StepRow`, `LegRow` |
| Trip cover | the cover photo; without one, `RouteCover`: Sand ground, three Contour ovals, the route in Signal on a Card casing, Trail/Waypoint/Forest pins | `RouteCover` |
| Map carousel card | Card, 18 dp, `Lift.Chip`, selected = 2 dp Forest edge; title row = 56 dp photo, date + title, then a `TopoIcons.Next` arrow (22 dp, Ink) on every card; the leg chip sits on its own line below and ellipsizes (card 260 dp), followed by a 32 dp Sand square with `TopoIcons.Navigate` (18 dp, Ink, R42); on "Bản đồ của tôi" the trip colour is a 16 × 4 dp line key by the title, the stats line in `numerals()` | `TripMapScreen`, `MyMapScreen` |
| Area card (offline maps) | `TopoCard`; titleMedium name, bodySmall description, status line (a done area = Easy `TopoBadge` + Trail check), 6 dp round Forest progress bar, Forest filled main button, outlined / text secondary; groups under an eyebrow | `OfflineMapsScreen` |
| Guide section (battery guide) | `TopoCard`, titleMedium, numbered steps on 26 dp discs: Forest for the device's own section, Sand for the general one (Barlow number) | `BatteryGuideScreen` |
| Photo / video viewer | the one dark screen: black stage (a photo is judged on a neutral ground), light status-bar icons; counter = `MapChip` (no shadow), close = `MapButton`; no other colour | `PhotoViewerScreen` |
| Offline note on maps | in an area: Card chip with Download icon; outside: Forest callout with CloudOff, `Lift.Panel` | `OfflineIndicator` |
| Video badge / photo tags | Forest at 85 % with Card text, 8 dp; cover tag Trail | `VideoBadge`, `PhotoTile` |
| Copy | plain Vietnamese, no em dash (—) in user text; en dash only in ranges | — |

## 6a. Motion (R37, `ui/theme/Motion.kt`)

Motion is quiet and short: things glide, nothing bounces. One curve for everything: **cubic
ease in-out** `cubic-bezier(0.65, 0, 0.35, 1)` (`Motion.EaseInOut`).

| Token | Value | Use |
| --- | --- | --- |
| Camera step move | 400–1800 ms: `350 + 1.5 ms/km + 160 ms per zoom level`, clamped (`CameraMath`) | trip map camera to the focused step, ease in-out, never while the cards move |
| `EXPAND_MS` | 400 ms | card → Step detail shared bounds (and back) |
| `CONTENT_FADE_MS` / `CONTENT_FADE_DELAY_MS` | 200 ms after 150 ms | the detail's content fades in once the bounds are growing; out at once (200 ms) on Back |
| `SCREEN_MS` / `SCREEN_SLIDE_DP` | 300 ms, 24 dp | screen → screen (navigation, `NavHost` in `AppNavigation.kt`): the new screen fades in while sliding 24 dp along the way in (forward: from the right; Back: from the left), the old one fades out and slides out the same way, `EaseInOut`. Trip map ↔ Step detail only fades: the card's shared bounds do the moving |
| `DAY_FLIP_MS` | 260 ms | "Ngày N/M": the number slides by its own height and fades, up when N grows, down when it shrinks; the chip itself fades in and out with the same duration and `EaseInOut` |

The day counter is a Card chip over the map (Ink "Ngày" label, Barlow numerals, InkMuted "/M",
`Radii` small, `Lift.Chip`); no new colour pair. Reduced motion: with the system's animator
duration scale at 0 every camera move jumps and the Compose motions end at once (iOS: Reduce
Motion). A live map cannot be drawn inside a moving shared bounds: show a plain
surfaceContainer panel in its place until the move ends.

## 7. Map styling

The basemap is repainted to the specimen's map (§2 "Map"): paper, vegetation, water, contour-brown
borders; the route is the only strong colour. The leg the camera has just travelled (R37) keeps
that colour: SignalDeep line on top plus a soft, wide Signal halo (opacity 0.38, blurred), no
second colour. Map overlays (chips, buttons, panels) float with
`Lift`; the credit sits on Card at 80 %, one text (basemap line + router + "Sửa bản đồ", OSM named once). The map buttons (44 dp, 12 dp margin) keep a 72 dp strip at the right of the fit, so the route never sits under them. Under every live map there is a `Palette.MapPaper` panel, so a map being created shows paper, never a dark frame. Static maps (share image, `StaticTripMap`) use the same
tokens.

## 8. Share outputs

* **Share image (R18, `share/SummaryImage.kt`)**: Parchment page, Card panels with a Ledge, the
  static map with the Signal route and the pins of §2, Barlow numbers over bold InkMuted labels,
  the Route mark before "Lữ Trình" in Signal with a Forest end dot.
* **HTML trip page (R18, `domain/usecase/TripPage.kt`)**: the same values as hex in its inline CSS (the
  file is pure Kotlin, so no `Palette`): Parchment page; dates as an eyebrow (13 px bold, +0.06 em)
  over a 28 px title; map, stats and each step as Card blocks with a 2 px Ledge shadow (22 / 18 px
  corners); stats and step numbers in Barlow Condensed; step discs Trail (first) / Waypoint /
  Forest (last) with a white ring; no Signal text, no gradients; the only links are the per-step "Dẫn đường" chips (R42: Forest chip, 12 px corners, Card text 14 px bold, pair Card / Forest of §11, no underline). Text in `"Noto Sans"` then system sans (name only); numbers in `"Barlow Condensed"` 700,
  embedded as a ~2 kB WOFF2 subset of the digits and `.,:-–/+%` (`BarlowDigits.kt`, OFL, licence in
  `assets/licenses`) in a `data:` `@font-face`; the CSP allows `font-src data:` only (no network).
* App icon: the Route mark in Card on a Forest square (`ic_launcher_background`); window background Parchment.

## 9. Do / don't

Do: tokens only · one route colour (Signal) · numbers in Barlow over bold labels · ledge on paper,
lift only over the map · four radii · TopoIcons · contour lines as the only ornament · real photos
only (sample photos: `app/src/debug/assets/sample-photos/` with a README row each) · every new
text/background pair in §11.

Don't: gradients, glass blur, coloured side bars, pills, drop shadows on cards, icon-in-a-circle
decoration, a second accent hue, Signal as text, InkMuted on Sand, invented numbers, generated or
AI-made pictures, em dashes in copy.

## 10. Specimen → app

| Specimen element | Specimen value | App component |
| --- | --- | --- |
| Map paper `.ph` / map rect | `#EFEBDA` | screen background, map paper (`Parchment`) |
| Bottom stats panel `.pan` | `#FBFAF2`, 22 px, `0 8px 24px`, label + 3 stats | trip header `TopoPanel` + `KmReadout`; empty-map notice `TopoPanel(floating)` |
| `.stats .n` / `.lbl` | Barlow 700 40 px / 13 px 700 .06 em `#6B6A58` | `KmReadout` numbers (`numerals()`) / `Eyebrow` |
| Elevation profile line + 25 % fill | `#E4572E` 3 px | route on maps and covers, `KmScaleBar` blocks (`Signal`) |
| Map chips `.chip` | `#FBFAF2`, 12 px, 14 px 700, `0 4px 10px` | `MapChip`, `MapButton`, offline chip, record control |
| Dark chip / callout | `#1F2A24`, white | `ChipFab`, filled buttons, snackbar, offline note, `MapChip(dark)` |
| List eyebrow + 28 px title | `.lbl` + 28 px 700 | `ScreenHeading` (trip list, trip detail) |
| Route card `.card` | `#FBFAF2`, 18 px, 14 px pad, `0 2px 0 #D9D4BE` | `TopoCard` (trip list, timeline steps, settings, step info, GPS card) |
| `.mini` thumbnail with contours | 96 × 72, 12 px, `#E8E3CE`, `#C09A68` ovals, orange route | trip thumbnail: photo or `RouteCover` |
| Difficulty `.dif` badge | 13 px 700, 8 px, wash + ink | `TopoBadge` (draft badge, trip drafts) |
| Route line + white casing | 5 px `#E4572E` on 9 px `#fff` | leg lines (`OverlayLayers`), `RouteCover` |
| Start / waypoint / end circles | `#2D7A46` r11 · `#2B6CB0` r8 · `#1F2A24` r11, 3 px white | pins (`Pins.kt`), timeline discs |
| Contours | `#C09A68` 1.3 / 2.4 px | `RouteCover` ovals, empty-state mark, borders |
| Woods / water | `#D8E4C4` / `#BFDDE6` | basemap repaint |
| Profile grid | `#E3DEC8` | dividers, rail (`Grid`) |
| Layers chip "图层" | Card chip | "Kiểu bản đồ" `MapButton` + sheet |
| Fonts | Noto Sans SC + Barlow Condensed | Noto Sans (Latin + Vietnamese) + Barlow Condensed |
| (not in specimen) | — | dialogs, sheets, menus, inputs: Card paper with the specimen radii |

## 11. Contrast (WCAG 2.x, computed from the hex values)

| Text / background | Hex | Role | Ratio | ≥ 4.5 |
| --- | --- | --- | --- | --- |
| Ink / Parchment | #2A2A22 / #EFEBDA | body on background | 12.09 | yes |
| Ink / Card | #2A2A22 / #FBFAF2 | body on cards, sheets, dialogs | 13.81 | yes |
| Ink / CardDim | #2A2A22 / #F6F3E6 | body on surfaceContainerHighest | 13.00 | yes |
| Ink / Sand | #2A2A22 / #E8E3CE | text in wells, selected rows, "+N" | 11.23 | yes |
| Ink / SignalWash | #2A2A22 / #FBE3D9 | text on accent wells | 11.77 | yes |
| InkMuted / Parchment | #6B6A58 / #EFEBDA | eyebrows, meta on background | 4.59 | yes |
| InkMuted / Card | #6B6A58 / #FBFAF2 | meta, labels on cards | 5.24 | yes |
| InkMuted / CardDim | #6B6A58 / #F6F3E6 | labels on dim cards | 4.93 | yes |
| InkMuted / Sand | #6B6A58 / #E8E3CE | **not allowed** | 4.26 | no |
| Forest / Parchment | #1F2A24 / #EFEBDA | text buttons, links | 12.41 | yes |
| Forest / Card | #1F2A24 / #FBFAF2 | text buttons in dialogs | 14.18 | yes |
| Forest / Sand | #1F2A24 / #E8E3CE | onPrimaryContainer | 11.53 | yes |
| Card / Forest | #FBFAF2 / #1F2A24 | filled buttons, FAB, snackbar, dark chip, launcher mark | 14.18 | yes |
| SignalDeep / Card | #B83A16 / #FBFAF2 | accent text, mode icons | 5.49 | yes |
| SignalDeep / Parchment | #B83A16 / #EFEBDA | accent text | 4.81 | yes |
| SignalDeep / Sand | #B83A16 / #E8E3CE | mode icon on a selected row (icon, ≥ 3) | 4.46 | icon only |
| Error / Parchment | #B3261E / #EFEBDA | error text | 5.47 | yes |
| Error / Card | #B3261E / #FBFAF2 | destructive text in dialogs | 6.24 | yes |
| Card / Error | #FBFAF2 / #B3261E | onError | 6.24 | yes |
| EasyInk / EasyWash | #1F5A2A / #CFE8CF | badge | 6.30 | yes |
| ModerateInk / ModerateWash | #7A4A00 / #FDE6B8 | badge (Nháp) | 6.12 | yes |
| HardInk / HardWash | #8A2410 / #F6C9C0 | badge, errorContainer | 6.00 | yes |
| White / Waypoint | #FFFFFF / #2B6CB0 | step pin number | 5.42 | yes |
| White / Trail | #FFFFFF / #2D7A46 | start pin number, cover tag | 5.27 | yes |
| White / Forest | #FFFFFF / #1F2A24 | end pin number | 14.84 | yes |
| MapLabel / Parchment | #5A5948 / #EFEBDA | map labels | 5.94 | yes |
| Signal / Parchment | #E4572E / #EFEBDA | route line (line, ≥ 3) | 3.08 | line only |
| Outline / Card | #86826B / #FBFAF2 | field border (≥ 3) | 3.70 | line only |
| Outline / Parchment | #86826B / #EFEBDA | hollow disc (≥ 3) | 3.24 | line only |
| Contour / Parchment | #C09A68 / #EFEBDA | ornament | 2.18 | decorative |
| Ledge / Parchment | #D9D4BE / #EFEBDA | card ledge | 1.24 | decorative |
| Trip colours / Parchment | #8C6A3C · #2F7480 · #6E4E9A · #7A4A00 | My map lines (≥ 3) | 4.14 · 4.47 · 5.46 · 6.26 | lines only |

Signal (`#E4572E`) is never text: white on it is 3.68 and it is 3.08 on the paper.

## 12. Guard

`app/src/test/java/vn/lutrinh/app/ui/DesignGuardTest.kt` (JVM, part of `testDebugUnitTest`) fails on:

* `hardCodedColoursOnlyInTheTheme`: a colour literal (`Color(0x…)`, `0xFF……`) outside `ui/theme/`;
* `noGradientsInUi`: any `Brush.*Gradient` in `ui/`;
* `fabsOnlyInButtons`: a Material FAB composable outside `ui/common/Buttons.kt` (use `ChipFab`);
* `noPillButtons`: a button call without a `shape =` first;
* `cornerRadiiOnlyFromTokens`: a `RoundedCornerShape(` not taking a `Radii` token, or a `CutCornerShape`;
* `shadowsOnlyFromLiftTokens`: a `shadowElevation`/`.shadow(`/non-zero `defaultElevation` without `Lift.`;
* `bundledFontsAreTheSpecFontsWithALicence`: a font other than Noto Sans / Barlow Condensed, a
  missing OFL file, or a leftover of the old design (`be_vietnam`, `AtlasIcons`, `FlatFab`, `Cham`);
* `topoIconsAndTypeWeights`: a Material icon import or a font weight off the scale (Thin, Light,
  SemiBold, …) outside the shrinking `notYetTopo` list (docs/09; a clean listed file fails too);
* `spacingOnTheSpaceScale`: a dp literal other than 0/4/8/12/16/24/32 in `padding(` / `PaddingValues(` /
  `spacedBy(` / `Spacer(`, or an `Icon(…size(…))` other than 18/22/24/40, outside `ui/theme/`; the
  allow-list (`spaceAllowed`, 2 entries: the picker's 124 dp overlay offset, the 2 dp ring of a day
  disc) needs a reason per entry and fails when an entry is no longer in the source;
* `dialogsUseTheThreeTypesAndTheDialogTitleToken`: `AlertDialog(` only in `Dialogs.kt` (+ the time picker), no 26 sp title token in `ui/`;
* `noSystemBlueOnMaps`, `noGeneratedSamplePictures`, `samplePhotosHaveASourceAndLicence`.

Review checklist for a UI change: tokens only; ledge on paper, Lift over the map; radii 8/12/18/22;
type from the scale; numbers in `numerals()`; TopoIcons; contrast pair in §11; before/after shots
in `docs/img/` + docs/03 index.

## 13. iOS

SwiftUI: `Color` assets with the token names and hex values of §2; cards as
`RoundedRectangle(cornerRadius: 18)` filled Card over a second one offset 2 pt in Ledge (no
`.shadow`); map chips/buttons `.shadow(color: .black.opacity(0.25), radius: 5, y: 4)` and panels
`.shadow(color: Color(red: 0.12, green: 0.16, blue: 0.08).opacity(0.3), radius: 12, y: 8)` only
over the map; `Font.custom("NotoSans-…", size:, relativeTo:)` per scale step (400/500/700) and
`Font.custom("BarlowCondensed-Bold", …).monospacedDigit()` for numbers; icons: each §5 row as a
`Path` from the SVG data stroked 2 with `.round` caps and joins on a 24 frame; `RouteCover` and the
km bar as a `Canvas` (`RouteFit`, `KmScale` ported as is, docs/07).

## 14. Not done (follow-up)

* Dark theme: the specimen has none; **the app stays light-only** (captain 2026-11-10, Q-1:
  `MainActivity` forces light system bars, the system's dark mode is ignored on purpose). A derived
  dark palette (Forest ground, Card ink, Signal kept) is a later decision.
* The screens, dialogs and sheets marked "not yet" in `docs/09-redesign-inventory.md` (batches 2–4),
  including the HTML trip page.
* Elevation profile: the app has no altitude data, so the specimen's profile chart has no
  counterpart; **the km scale bar stays** (captain 2026-11-10, Q-7: no elevation chart).
* Older feature screenshots in `docs/img/` (docs/03 index) still show the previous look.
