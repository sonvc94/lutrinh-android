# 08 — Design: "Sổ bản đồ" (field atlas)

Platform-neutral design spec for Lữ Trình (Android now, iOS later). Every screen follows it; a
change of look updates this file in the same branch (AGENTS.md standing rule). Android tokens:
`ui/theme/Theme.kt` (`Palette`, `Radii`, `hairline`) and `ui/theme/Type.kt`; map repaint:
`map/basemap/OpenFreeMap.kt`; iOS: docs/07 "Theme".

The audit that led here is `docs/design-audit-huashu.md` (findings A1–A12, directions D1–D4).
Captain's choice (news #67): *"So ban do (atlas): tuyen duong + km la ban sac, 1 mau nhan cham
indigo, net manh thay the do bong"*.

## 1. Direction

A travel journal that looks like a field atlas, not a generic app template:

* **The route and the kilometres are the identity.** The most saturated thing on any screen is a
  route line or a number about the road. Decoration never competes with them.
* **One accent: chàm** (the indigo of Hmong and Dao hemp cloth from the northern mountains). Every
  other colour is a neutral sampled from limestone, mist and paper.
* **Lines instead of shadows.** Containers are separated by 1 dp hairlines; a selection is a 2 dp
  chàm edge. No drop shadows on cards, sheets or bars, no glows, no gradients.
* **Real or nothing.** A picture is a photo the user took (or, in debug sample data, a licensed
  real photo). If there is no photo, show the trip's own data (its route), never a stand-in drawing.

## 2. Palette (tokens)

Sampled from real photos of the sample places (debug `sample-photos/`: Tam Sơn mist over Quản
Bạ, Mã Pí Lèng, Du Già terraces, Tràng An, Hang Múa) and from a Hmong indigo hemp skirt (Auckland
Museum, [Skirt (AM 2007.68.1-1)](https://commons.wikimedia.org/wiki/File:Skirt_(AM_2007.68.1-1).jpg),
CC BY 4.0; reference only, not shipped). Photo colours were clustered (k-means in sRGB) and
read in oklch; the token keeps the photo's hue and lowers or raises lightness for its role.
Chroma stays low (≤ 0.02) except chàm.

| Token | oklch | Hex | Role (Material 3 scheme role) | From |
| --- | --- | --- | --- | --- |
| Paper | 0.975 0.003 220 | `#F5F7F8` | background, surface | limestone mist, Tam Sơn |
| PaperRaised | 0.993 0.002 220 | `#FBFDFE` | surfaceContainerLowest (cards, sheets), onPrimary | |
| LimestoneLow | 0.960 0.005 230 | `#EFF2F4` | surfaceContainerLow (route cover ground) | karst rock |
| Limestone | 0.945 0.006 230 | `#E9EEF0` | surfaceContainer | |
| LimestoneHigh | 0.925 0.007 230 | `#E2E7EA` | surfaceContainerHigh, surfaceVariant, secondary/tertiaryContainer | |
| LimestoneHighest | 0.905 0.008 230 | `#DBE1E4` | surfaceContainerHighest | |
| LimestoneDim | 0.880 0.008 230 | `#D2D9DC` | surfaceDim | |
| Ink | 0.230 0.014 240 | `#171E23` | onSurface, secondary, tertiary, inverseSurface, GPS track, end pin | darkest chàm of the cloth |
| InkMuted | 0.450 0.014 240 | `#4F565C` | onSurfaceVariant, map credit | |
| Outline | 0.600 0.012 240 | `#7A8187` | outline (field borders, 3:1) | |
| Hairline | 0.870 0.008 235 | `#CFD5D9` | outlineVariant (dividers, card edges) | |
| **Cham** | 0.420 0.095 258 | `#2A4D80` | **primary**: routes, primary buttons, links, selection edge | indigo hemp skirt |
| ChamDeep | 0.300 0.080 258 | `#112D55` | onPrimaryContainer | |
| ChamWash | 0.930 0.018 258 | `#E1E9F4` | primaryContainer (downloaded area) | |
| ChamBright | 0.520 0.160 262 | `#3263C3` | maps only: selected leg, selected pin ring/halo, my-location dot | |
| Error | 0.50 0.17 27 | `#B3261E` | error, recording indicator (a system meaning, not decoration) | M3 default |
| MapPaper | 0.955 0.005 220 | `#EDF1F2` | map background (`MapConfig.BACKGROUND`, basemap `background`) | |

Rules:

* No colour literal outside the theme (Android: `ui/theme/`); map code turns tokens into
  `#RRGGBB` with `Color.rgbHex()`. Guard: `DesignGuardTest.hardCodedColoursOnlyInTheTheme`.
* No second accent. Terracotta, rice gold, terrace green and crimson of the old theme are gone;
  states that need a hue (error, recording) use the error red.
* No Material pastel defaults: every container role is set from the limestone ramp.

### Trip colours (R14, "Bản đồ của tôi" only)

Data colours, one per trip, never used elsewhere. Same photos, mid lightness, chroma ≤ 0.11; each
≥ 3:1 on MapPaper (they are lines, see §8): chàm `#2A4D80`, terrace `#405F39` (Du Già), ripe rice
`#A47726`, Nho Quế jade `#307672`, karst slate `#637883`, light chàm `#5D7AB0`, earth `#79543F`
(Mã Pí Lèng), young rice `#608A57`.

### Map

| Element | Colour |
| --- | --- |
| Basemap background / residential / buildings | `#EDF1F2` / `#E6EAEC` / `#E2E7E9` (outline `#D5DBDE`) |
| Country / province borders | `#A8AFB4` / `#C5CBCF` |
| Labels / halo | `#555C61` / `#F5F7F8` (Vietnamese first, docs/05) |
| Route (every leg, every mode) | Cham; selected leg ChamBright; soft ink shadow + white casing |
| Leg mode | told by stroke (solid / dots / dashed arc) and the badge icon, never by hue |
| My location | ChamBright dot, white ring, 15 % ChamBright accuracy circle (no system blue) |
| GPS track, stop suggestions | Ink (thin solid line on a white casing; hollow dot on an ink halo) |
| Pins | start: white disc + chàm ring; step: chàm disc; end: ink disc; context (picker): ink; draft: dashed chàm ring; end: ink **square** (4 dp corners); selected: ×1.3, ChamBright ring + halo; a 1 dp 31 % ink edge, no drop shadow |

Satellite (Esri) keeps its own white labels on a dark halo for legibility on imagery; its country /
province borders are Limestone / LimestoneDim (cool, no warm sand) (docs/05).

## 3. Type

* **Be Vietnam Pro** (SIL OFL) for all text: designed for Vietnamese, stacked marks (ữ, ằ, ệ, ỗ)
  sit clear of the line above. Bundled, never downloaded.
* **Barlow Condensed** (SIL OFL) with tabular figures (`tnum`) for numbers that stand alone: stat
  values, km, counts on the share image. Never for words. Be Vietnam Pro has no tabular figures.
* Checked before choosing (`img/design-atlas/font-check.png`): Be Vietnam Pro, Barlow Condensed,
  Newsreader, Source Serif 4 and Literata all draw ữ ằ ệ ỗ correctly; none of the serifs was
  clearly better for a map-and-numbers app, so Be Vietnam Pro stays (no serif-on-cream look).

One modular scale, ratio 1.2 from a 16 sp body (sizes in sp on Android, pt on iOS):

| Step | Size / line | Weight | Styles |
| --- | --- | --- | --- |
| 40 | 40 / 48 | 600, −1 % tracking | displayLarge |
| 33 | 33 / 40 | 600, −1 % | displayMedium, headlineLarge |
| 28 | 28 / 34 | 600 | displaySmall, headlineMedium |
| 23 | 23 / 30 | 600 / 500 | headlineSmall (stat values, empty-state title) / titleLarge |
| 19 | 19 / 26 | 600 | titleMedium |
| 16 | 16 / 22–24 | 600 / 400 | titleSmall / bodyLarge |
| 14 | 14 / 20 | 400 / 500 | bodyMedium / labelLarge |
| 12 | 12 / 16 | 400 / 500 | bodySmall, labelMedium, labelSmall (floor: nothing smaller, except the 9 sp map credit) |

Three weights only: 400 body, 500 labels, 600 titles.

## 4. Space, radii, lines

* Spacing on a 4 dp grid: 4 · 8 · 12 · 16 · 24 · 32. Screen edge 16 dp; empty states 24 dp.
* **Two radii**: 4 dp (chips, thumbnails, badges, small buttons' content) and 12 dp (cards,
  sheets, dialogs, map surfaces). Material `extraSmall`/`small` = 4, `medium`/`large`/`extraLarge`
  = 12. No other `RoundedCornerShape` literal in screens.
* **Hairline**: 1 dp `outlineVariant` (Android `hairline`). Cards, sheets, bottom bars and map
  overlays have a hairline and elevation 0. **Selection**: 2 dp primary border.
* Touch targets ≥ 44 dp (Material default 48 dp kept).

## 5. Components

* **Cards**: raised paper, hairline, no shadow, 12 dp. No coloured side bar or stripe
  (a trip's colour on My map is a 16 × 3 dp legend line beside the title).
* **Trip cover**: the cover photo; with no photo, the trip's own route on paper with a
  graticule (`RouteCover`: start = open chàm ring, steps = chàm dots, end = ink dot). Never a
  gradient or an icon placeholder.
* **Trip header** (trip detail): an atlas plate, not a card: the title (headlineMedium), the dates
  and the description, a hairline, then the km readout. No box, no icon.
* **Stats = km readout** (`KmReadout` + `KmScaleBar` in `ui/common/Readout.kt`; maths in
  `domain/usecase/KmScale.kt`): the km total large in Barlow Condensed (displayLarge, ink) with its
  unit and note ("đường thẳng"/"theo GPS") on one baseline; under it a cartographic **scale bar**
  whose whole length is the trip's km: a 6 dp bar, 1 dp chàm outline, alternate blocks filled
  chàm, every `step` km a 4 dp tick labelled in labelSmall numerals. `step` is the smallest
  1 · 2 · 5 × 10ⁿ km giving at most 5 blocks (139 km → 0 · 50 · 100; 12 km → 0 · 5 · 10; 0.8 km →
  0 · 0,2 · … ); the end is not labelled (the total is the big number); 0 km = no bar. The counts
  (điểm dừng, ảnh, ngày) follow as one line of type: number in headlineSmall numerals, label in
  labelLarge InkMuted. "Bản đồ của tôi" uses the compact form (displaySmall, counts right-aligned on
  the km line) inside its paper overlay. The share image (R18) draws the same readout and bar.
  No tiles, no boxes around numbers.
* **Empty and error states**: type only, left-aligned: a 32 × 2 dp chàm rule, a title saying what
  is missing, a body saying what to do, one button doing it. No icon disc.
* **Transport modes**: one tint (chàm) for icon, chip and sheet; the stroke and the icon tell
  the mode.
* **Buttons**: flat blocks with 4 dp corners (`shape = MaterialTheme.shapes.small` on every
  `Button`/`OutlinedButton`/`FilledTonalButton`, also the segmented button), filled = chàm,
  never a pill. Recording uses the error red. The screen's main action is `FlatFab`
  (`ui/common/Buttons.kt`): a chàm block, onPrimary text and icon, 4 dp corners, elevation 0,
  collapsing to its icon on scroll as before. Map controls are `MapButton`: a 44 dp raised-paper
  square with a hairline and 4 dp corners, like a legend box (Kiểu bản đồ, Vị trí của tôi, Xem
  toàn bộ hành trình, Xem bản đồ). Guards: `DesignGuardTest.noShadowedFabs`, `noPillButtons`.
* **Icons** (`ui/theme/AtlasIcons.kt`, own drawings, no third-party licence): a 24 grid, one
  1.6 stroke, square caps, mitred joins (survey-sheet lines), filled dots only where a point is
  meant. They replace the stock Material icons on bars, FABs, menus and map buttons: back, more,
  add, share, edit, delete, route (trip map), atlas (My map), my location, layers, expand, fit,
  add step, add from photos, photo, download, upload (backup), file open, restore, page (HTML
  share), search, close, check, settings ("Cài đặt", R36), and the picker's centre needle. Path data (SVG, 24 × 24; "dots"
  are filled):

  | Icon | Lines | Dots |
  | --- | --- | --- |
  | Add | `M12 5v14M5 12h14` | |
  | Back (mirrors in RTL) | `M19 12H6M11 6.5L5.5 12 11 17.5` | |
  | More | | `M11 4.5h2v2h-2zM11 11h2v2h-2zM11 17.5h2v2h-2z` |
  | Share | `M12 15V4M8 7.5L12 3.5l4 4M8.5 10H5v10h14V10h-3.5` | |
  | Edit | `M4.5 19.5h4l10-10-4-4-10 10zM12.5 7.5l4 4` | |
  | Delete | `M4.5 6.5h15M9.5 6.5v-2.5h5v2.5M6.5 6.5l1 13h9l1-13M10.5 10.5v5.5M13.5 10.5v5.5` | |
  | Route (= brand mark) | `M8.5 18H15a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h6.5M6 15.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5` | `M18 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z` |
  | Atlas | `M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M3 12h18M4.5 7.5h15M4.5 16.5h15M12 3c-3.5 3.5-3.5 14.5 0 18M12 3c3.5 3.5 3.5 14.5 0 18` | |
  | My location | `M12 6a6 6 0 1 0 0 12a6 6 0 1 0 0-12M12 2.5V6M12 18v3.5M2.5 12H6M18 12h3.5` | `M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z` |
  | Layers | `M12 4l8 4-8 4-8-4zM4 12.5l8 4 8-4M4 16.5l8 4 8-4` | |
  | Expand | `M14 4h6v6M20 4l-7 7M10 20H4v-6M4 20l7-7` | |
  | Fit | `M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5` | |
  | Add step | `M12 21s-6.5-5.5-6.5-11a6.5 6.5 0 0 1 13 0c0 5.5-6.5 11-6.5 11zM12 7v6M9 10h6` | |
  | Add photos | `M3 7.5h12v12H3zM3 16.5l4-4 3 3 2-2 3 3M19 3v7M15.5 6.5h7` | |
  | Photo | `M4 5h16v14H4zM4 16l5-5 4 4 2.5-2.5L20 17` | `M15 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z` |
  | Download | `M12 4v11M7.5 10.5L12 15l4.5-4.5M4.5 19.5h15` | |
  | Upload | `M12 15V4M7.5 8.5L12 4l4.5 4.5M4.5 19.5h15` | |
  | File open | `M6 3.5h8l4 4v13H6zM13.5 3.5V8H18M9 14.5h6M12 11.5l3 3-3 3` | |
  | Restore | `M5 12a7 7 0 1 0 2-5M4.5 4v4.5H9M12 8.5V12l2.5 2.5` | |
  | Page | `M6 3.5h12v17H6zM9 8h6M9 11.5h6M9 15h4` | |
  | Settings (three sliders) | `M4 7h9M17 7h3M15 5v4M4 12h3M11 12h9M9 10v4M4 17h11M19 17h1M17 15v4` | |
  | Needle (picker centre, tip at 12,22) | `M12 12v10M12 2.5a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0-9` | `M12 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z` |
  | Search | `M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13M15.5 15.5L20 20` | |
  | Close | `M6 6l12 12M18 6L6 18` | |
  | Check | `M5 12.5l4.5 4.5L19 7.5` | |

  Smaller stock icons remain inside rows and forms (schedule, place, info, edit note, video, play,
  check circle, leg-mode glyphs); replace them with the same grammar when a screen is touched.
* **Brand mark and app icon**: the Route glyph (open start ring, a switchback, the end dot) is the
  mark: on the share image (R18) in chàm with an ink end dot before "Lữ Trình", and as the launcher
  icon in PaperRaised on a chàm (`#2A4D80`) square (`res/drawable/ic_launcher_foreground.xml`,
  `colors.xml ic_launcher_background`). The window background (splash) is Paper `#F5F7F8`.
* **Copy**: plain Vietnamese, no em dash (—) in user text; use a colon or a comma. The en dash
  stays in ranges ("6 thg 11 – 9 thg 11").

## 6. Imagery

* Real photos only. No generated, AI-made or code-drawn landscapes anywhere, sample data included.
* Debug sample photos: `app/src/debug/assets/sample-photos/` with a README row per file (source,
  author, licence; CC0 / public domain / CC BY / CC BY-SA from Wikimedia Commons). Guard:
  `DesignGuardTest.samplePhotosHaveASourceAndLicence`, `noGeneratedSamplePictures`.
* Placeholders show data (route, initials of a place) or nothing; never a fake picture.

## 7. Rules against the "AI-made" look

Adopted from the huashu-design review (rules restated in our words):

1. No default look: no cream + serif + terracotta, no purple/blue gradient, no glassy blur.
2. At most three hues on a screen besides photos and data colours, low chroma, sampled from real
   images of the places; one accent.
3. One modular type scale; no ad-hoc sizes.
4. Honest imagery (§6): never a stand-in picture that pretends to be content.
5. No left-border "accent" cards, no decorative icon-in-a-circle, no fake metric cards with
   invented trends; numbers shown are the user's real numbers.
6. Lines over shadows; few radii.
7. Readability: body text ≥ 4.5:1, large text and meaningful lines/icons ≥ 3:1; targets ≥ 44 dp.
8. Every decorative element must carry meaning (the route, the km, the place) or go.

Guard (JVM, part of `testDebugUnitTest`): `app/src/test/java/vn/lutrinh/app/ui/DesignGuardTest.kt`
fails on a colour literal (`Color(0x…)` or an opaque `0xFF……` int) outside `ui/theme/`, on any
`Brush.*Gradient` in `ui/`, on code-drawn sample pictures, on a sample photo without a README
row, on a Material FAB composable outside `ui/common/Buttons.kt` (`noShadowedFabs`), on a
button call without a `shape =` (`noPillButtons`) and on the system blue `1A73E8`
(`noSystemBlueOnMaps`).

Review checklist for a screen change: tokens only; hairline not shadow; ≤ 2 radii; type from the
scale; numbers in the numeral style; no icon-only empty state; no em dash; contrast pair listed
below; screenshot in `docs/img/` + docs/03 index.

## 8. Contrast (WCAG 2.x, computed from the hex values)

| Text / background | Hex | Role | Ratio | ≥ 4.5 |
| --- | --- | --- | --- | --- |
| Ink / Paper | #171E23 / #F5F7F8 | onSurface on surface/background | 15.68 | yes |
| Ink / PaperRaised | #171E23 / #FBFDFE | onSurface on surfaceContainerLowest (cards, sheets) | 16.51 | yes |
| Ink / LimestoneLow | #171E23 / #EFF2F4 | onSurface on surfaceContainerLow | 14.98 | yes |
| Ink / Limestone | #171E23 / #E9EEF0 | onSurface on surfaceContainer | 14.40 | yes |
| Ink / LimestoneHigh | #171E23 / #E2E7EA | onSurface / onSecondaryContainer on surfaceContainerHigh | 13.52 | yes |
| Ink / LimestoneHighest | #171E23 / #DBE1E4 | onSurface on surfaceContainerHighest (filled cards) | 12.76 | yes |
| Ink / ChamWash | #171E23 / #E1E9F4 | onSurface on primaryContainer | 13.77 | yes |
| InkMuted / Paper | #4F565C / #F5F7F8 | onSurfaceVariant on surface | 6.93 | yes |
| InkMuted / PaperRaised | #4F565C / #FBFDFE | onSurfaceVariant on surfaceContainerLowest | 7.30 | yes |
| InkMuted / LimestoneLow | #4F565C / #EFF2F4 | onSurfaceVariant on surfaceContainerLow | 6.63 | yes |
| InkMuted / LimestoneHigh | #4F565C / #E2E7EA | onSurfaceVariant on surfaceContainerHigh | 5.98 | yes |
| InkMuted / LimestoneHighest | #4F565C / #DBE1E4 | onSurfaceVariant on surfaceContainerHighest | 5.64 | yes |
| Cham / Paper | #2A4D80 / #F5F7F8 | primary (links, text buttons, km lines) on surface | 7.91 | yes |
| Cham / PaperRaised | #2A4D80 / #FBFDFE | primary on surfaceContainerLowest | 8.33 | yes |
| Cham / LimestoneHigh | #2A4D80 / #E2E7EA | primary on surfaceContainerHigh | 6.82 | yes |
| Cham / ChamWash | #2A4D80 / #E1E9F4 | primary on primaryContainer | 6.94 | yes |
| PaperRaised / Cham | #FBFDFE / #2A4D80 | onPrimary on primary (buttons, FAB, numbered pins, app icon) | 8.33 | yes |
| ChamDeep / ChamWash | #112D55 / #E1E9F4 | onPrimaryContainer on primaryContainer | 11.22 | yes |
| Paper / Ink | #F5F7F8 / #171E23 | inverseOnSurface on inverseSurface (snackbar) | 15.68 | yes |
| Error / Paper | #B3261E / #F5F7F8 | error on surface (recording, errors) | 6.08 | yes |
| PaperRaised / Error | #FBFDFE / #B3261E | onError on error | 6.41 | yes |
| OnErrorContainer / ErrorContainer | #410E0B / #F9DEDC | onErrorContainer on errorContainer (M3 default, offline warning) | 12.77 | yes |
| MapLabel / MapPaper | #555C61 / #EDF1F2 | map labels on map paper (plus a paper halo) | 5.98 | yes |
| InkMuted / White | #4F565C / #FFFFFF | map credit on its 75 % white label | 7.45 | yes |

| Non-text / background | Hex | Role | Ratio | ≥ 3 |
| --- | --- | --- | --- | --- |
| Outline / Paper | #7A8187 / #F5F7F8 | outline (field borders, 3:1) | 3.68 | yes |
| Cham / MapPaper | #2A4D80 / #EDF1F2 | route line on map paper | 7.47 | yes |
| ChamBright / MapPaper | #3263C3 / #EDF1F2 | selected leg / pin ring, my-location dot | 4.98 | yes |
| Cham / PaperRaised | #2A4D80 / #FBFDFE | scale bar blocks and ticks, map-button icons (Ink) on raised paper | 8.33 | yes |
| Ink / MapPaper | #171E23 / #EDF1F2 | GPS track, end pin | 14.81 | yes |
| Hairline / Paper | #CFD5D9 / #F5F7F8 | hairline (decorative divider, not a boundary that must be seen) | 1.38 | no (decorative) |

| Trip colour (R14) | Hex | on map paper |
| --- | --- | --- |
| chàm | #2A4D80 | 7.47 |
| terrace | #405F39 | 6.34 |
| ripe rice | #A47726 | 3.52 |
| Nho Quế jade | #307672 | 4.66 |
| karst slate | #637883 | 4.06 |
| light chàm | #5D7AB0 | 3.79 |
| earth | #79543F | 5.86 |
| young rice | #608A57 | 3.50 |

Every text pair passes 4.5:1. The hairline is decorative (never the only sign of a boundary
that matters: cards also differ from the paper behind them). Trip colours are lines with a white
casing, so 3:1 applies; ripe rice and young rice are the weakest (3.5:1).

## 9. iOS

SwiftUI: `Color` assets with the token names and hex values above; `.overlay(RoundedRectangle(
cornerRadius: 12).stroke(Color.hairline, lineWidth: 1))` for cards, no `.shadow`; `Font.custom(
"BeVietnamPro-…", size:, relativeTo:)` per scale step and `Font.custom("BarlowCondensed-SemiBold",
…).monospacedDigit()` for numbers; `RouteCover` as a `Canvas` with the `RouteFit` maths (docs/07).
The km readout: `KmScale` ported as is (pure), the bar as a `Canvas`; icons: each row of the §5
table as a `Path` (SVG path data) stroked 1.6 with `.square` caps and `.miter` joins on a 24 frame
(or an SF-Symbol-free asset catalogue of the same shapes); FAB and map buttons: `.buttonStyle`
with `RoundedRectangle(cornerRadius: 4)`, no `.shadow`.

## 10. Not done (follow-up)

* Leg-mode badge glyphs and the smaller in-row stock icons (§5 Icons) are still Material.
* Satellite labels keep their dark green halo `#26332C`.
* B4 remainder: the feature screenshot folders not listed as retaken in docs/03 "Screenshot
  index" still show the old green/clay look (they need GPS replays, camera, airplane mode, pushed
  EXIF photos or Hoàng Sa zooms to reproduce).
* Dark theme (the app is light-only).

Rules adapted from huashu-design (MIT, © alchaincyf, https://github.com/alchaincyf/huashu-design).
