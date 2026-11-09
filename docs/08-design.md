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
| ChamWash | 0.930 0.018 258 | `#E1E9F4` | primaryContainer (downloaded area, FAB) | |
| ChamBright | 0.520 0.160 262 | `#3263C3` | maps only: selected leg, selected pin ring/halo | |
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
| GPS track, stop suggestions | Ink (thin solid line on a white casing; hollow dot on an ink halo) |
| Pins | start: white disc + chàm ring; step: chàm disc; end: ink disc; context (picker): ink; draft: dashed chàm ring; selected: ×1.3, ChamBright ring + halo; faint ink drop shadow |

Satellite (Esri) keeps its own label/boundary colours for legibility on imagery (docs/05).

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
* **Stats**: the number in Barlow Condensed (tabular, ink), the label under it in InkMuted. Cells
  are hairline boxes for now (a scale-bar readout is follow-up B3).
* **Empty and error states**: type only, left-aligned: a 32 × 2 dp chàm rule, a title saying what
  is missing, a body saying what to do, one button doing it. No icon disc.
* **Transport modes**: one tint (chàm) for icon, chip and sheet; the stroke and the icon tell
  the mode.
* **Buttons**: Material defaults on the palette (filled = chàm). Recording uses the error red.
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
`Brush.*Gradient` in `ui/`, on code-drawn sample pictures, and on a sample photo without a README
row.

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
| PaperRaised / Cham | #FBFDFE / #2A4D80 | onPrimary on primary (buttons, numbered pins) | 8.33 | yes |
| ChamDeep / ChamWash | #112D55 / #E1E9F4 | onPrimaryContainer on primaryContainer (FAB) | 11.22 | yes |
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
| ChamBright / MapPaper | #3263C3 / #EDF1F2 | selected leg / pin ring | 4.98 | yes |
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

## 10. Not done (follow-up)

* B3, signature elements: stats as a readout / scale bar, a new trip header, own pin and badge
  shapes and own icons instead of stock Material icons, a brand mark for the share image.
* Shadowed pill FABs and the small round map buttons still use Material elevation; pill buttons.
* The share segmented button label wraps to two lines in Be Vietnam Pro.
* B4: refresh the older screenshots in `docs/img/` (many show the green/clay theme).
* Satellite boundaries keep warm colours; the my-location dot keeps the system blue `#1A73E8`.
* Dark theme (the app is light-only).

Rules adapted from huashu-design (MIT, © alchaincyf, https://github.com/alchaincyf/huashu-design).
