# Design audit: Lữ Trình against huashu-design

Scout report, 2026-11-09, branch `mate/scout-lutrinh-huashu` from `main@fc45ff8`. No app code changed.
Captain's words: "hãy nghiên cứu skill https://github.com/alchaincyf/huashu-design, t muốn áp dụng vào Lũ Hành. hiện tại app build ra trông rất AI"
(Mate's reading, not the captain's: "Lũ Hành" = Lữ Trình.)

Evidence base: the skill cloned at commit `b2460b0` (2026-11-03, `metadata.version: 3.2.0`) into an empty
`/tmp` dir and read, never run; the committed screenshots under `docs/img/`; the Compose code. The
emulator was not used (another crew owns it), so every screen claim below comes from a committed
screenshot or from code, and is marked with its source.

## 1. What huashu-design is

- **Purpose.** An agent skill ("花叔Design", by alchaincyf) that makes an AI agent produce visual work in
  **HTML**: clickable prototypes, 1920×1080 decks (+PDF/PPTX export), timeline animations exported to
  MP4/GIF, infographics, Xiaohongshu carousels, and design critiques. Its stated bar: "the output must
  not be recognisable as AI-made". It says itself it is **not** for production web apps or backends.
- **Structure.** `SKILL.md` (223 lines, Chinese) is a router plus a "narrow bridge" of rules every
  task follows; 40 `references/*.md` (≈11 800 lines: style library, typography, content/anti-slop
  guidelines, app-prototype handbook, critique rubric, motion/video/audio, deck export);
  `references/modules/` per medium; `scripts/` (Python/Node: `design_lint.py`, `verify.py`,
  `fetch_images.py`, `ref_board.py`, `learn.py`, video/PPTX tools, a gate hook); `assets/` (React
  device frames incl. `android_frame.jsx`, deck stage, background music); `demos/` (HTML).
- **How it is used.** A Claude-Code-style skill (`npx skills add alchaincyf/huashu-design`), but
  agent-agnostic. Flow: run `learn.py` / `design_lint.py ledger` / `capabilities.py` first → pick a
  module → answer the "form five questions" (narrative role, viewing distance, visual temperature,
  capacity, a visual motif unique to the content) → **three-direction gate**: six candidate
  directions as thumbnails, three built as real drafts, then **stop and wait for the user to pick**
  (100%, no exemption for given styles) → build → `design_lint.py check` (red = must fix) → record the
  look in a local "look ledger" (`~/.huashu-design/`) so the next project avoids repeating it.
  Gate files (`brand-spec.md`, `direction-approved.md` with the user's words) make each step auditable.
- **Core rules** (paraphrased):
  1. Verify facts before designing; start from existing context (codebase, product, brand), never from nothing.
  2. Know the model's own default taste and leave it: warm cream background (~#F4F1EA) + high-contrast
     serif + terracotta accent (~#D97757), all-caps micro-labels, 01/02/03 numbering. Other 2026 "AI
     look" families: near-black + one neon accent; thin-rule newspaper; **"SaaS card kit"** (everything in
     identical rounded cards, same radius, same light shadow, gradient as decoration).
  3. Template decoration to delete: label above every heading, "A · B · C" chains, trailing "→", one
     recoloured word in a title, fake numbering. Older slop still banned: purple gradients, emoji icons,
     **rounded card with a coloured left border**, SVG-drawn people/scenes, icon on every item, invented
     stats ("metric cards" of icon+number+word), invented quotes.
  4. Colour: never invent; **sample → converge → justify** (brand assets, real photos of the subject,
     cultural context), 2–3 chromatic colours + one neutral lightness ramp, low chroma (oklch C
     0.01–0.04 for large surfaces, 0.08–0.15 for brand colour, ≤0.22 only for small accents), and one
     sentence saying why each colour.
  5. Typography: decide content type → language → temperature → *then* the font; derive sizes from a
     modular scale (1.2–1.25 for UI), ≤5–6 sizes, use more than two weights, tabular numbers for data,
     never "system font everywhere".
  6. Materials first: each direction names its lead material (real photo, data-as-image, procedural
     texture, big type). **Real-image honesty test**: if removing the image loses no information it is
     decoration — remove it; if the image *is* the content (a place, a product) it is mandatory. Data
     as image: "a route on a map" instead of "three big-number cards".
  7. Taste = one detail at 120 %, the rest at 80 %; fewer containers, fewer borders, fewer decorative icons.
  8. Readability floor: body ≥ 14 px (mobile 16), labels ≥ 12 px, touch ≥ 44 px, contrast ≥ 4.5:1.
  9. Critique rubric puts **concept first**: "swap the product name — does it still work? Then it is a
     template, score ≤ 5", and a concept ≤ 5 caps the total at 6/10.
- **Licence.** `LICENSE` is **MIT** (© 2026 alchaincyf); README: relicensed from a personal-use licence
  on 2026-05-14, commercial use allowed. One exception: stickers in
  `assets/style-specimens/xhs/_kit/stickers/` are PolyForm Shield 1.0.0 (irrelevant to us).
  So we may copy or adapt its text into a private app's docs and crew instructions; copying
  substantial portions requires keeping the MIT copyright + permission notice. Recommendation: write
  our own rules in our own words (they must be in English/Vietnamese anyway; the source is Chinese)
  with one attribution line, and copy no assets.
- **Side effects if installed** (why we should not install it for crews): its start/end steps write to
  `~/.huashu-design/`, its version check runs `git ls-remote` against GitHub, its lint/verify scripts
  need Playwright + Chromium, and its gate *requires* stopping the turn for a human choice.

## 2. What applies to a native Android Compose app

| Applies directly (design judgement, medium-independent) | Web/HTML-only (skip) |
|---|---|
| Anti-slop lists: AI look families, template decoration, left-border cards, fake imagery, metric cards, icon overuse | React/Babel prototype setup, `ios_frame.jsx`/`android_frame.jsx`, Tweaks system |
| Colour derivation protocol (sample → converge → justify), oklch chroma ranges, ≤3 chromatic colours | `design_lint.py` / `verify.py` (they measure rendered HTML via Playwright; they cannot read Compose) |
| Typography order, modular scale, weights, tabular numbers, "not system font everywhere" | Look ledger and `learn.py` (local per-machine files) |
| Real-image honesty test, data-as-image, "materials first" | Decks/PPTX/PDF export, motion/video/voiceover/SFX, Xiaohongshu carousels |
| One-detail-at-120 % rule, fewer containers | CSS tips (`text-wrap`, Grid, `clamp()`), Web scale rules for slides/print |
| Form five questions per screen; concept-first critique rubric (usable as a review checklist on screenshots) | `fetch_images.py` (fetches stock/museum images for HTML mocks; we need our own licensed sample photos instead) |
| App-prototype taste anchors (restraint by default; density where it is the product) | Device frames, "show board" layout of phones |
| Three-direction gate *as a process* (show 2–3 real directions before building) | Its exact deliverable form (HTML drafts + `direction-approved.md`) |

Closest entry in its App style library: "Topo Outdoor (Gaia GPS / AllTrails)" — map as hero, contour
texture, route line with white casing, big readouts. Useful as a reference, but taking it as-is would
make Lữ Trình look like a hiking app; the motif has to come from Vietnamese content (section 4).
Android translation of the floor: 14 sp body minimum, 12 sp labels, 48 dp touch targets (M3), 4.5:1.

## 3. Why Lữ Trình looks AI-generated

The concept test from the critique rubric fails first: cover the place names on the trip list, trip
detail or step form and the screens could be any Material 3 sample app (to-do, recipe, fitness). The
palette comment in `Theme.kt:11` shows intent (terrace green, limestone, rice gold, clay), but almost
nothing *on screen* comes from Vietnam except the words. Findings, most visible first:

| # | Finding (skill rule) | Screen + evidence |
|---|---|---|
| A1 | **Every photo is a generated cartoon landscape**: sky gradient, a sun disc, three layers of soft hills, in 3 colour variants. It fills trip covers, timeline thumbnails, the step hero and the share image. Skill: no SVG/procedurally drawn scenes; for a place the photo *is* content, so it must be real or an honest placeholder. Debug sample data only, but it is what every screenshot and every demo shows. | `data/SampleData.kt:164-197` (`landscape()`); `docs/img/draft-cover/13-trip-list-auto-cover.png`, `route-redraw/after-trip-detail.png`, `ux-flows/04-step-detail-prev-next.png`, `share-trip/04-summary-hagiang.png` |
| A2 | **Gradient + icon placeholder** for a trip without cover: `primary → tertiary` linear gradient with a 56 dp `Landscape` icon (gradient as decoration). | `ui/trips/TripListScreen.kt:238-248` |
| A3 | **Stock empty state**: 96 dp tinted circle, outlined luggage icon, centred title, centred body, pill button, FAB. The single most common generated empty state. Copy uses an em dash. | `ui/common/Common.kt:171-200`, `TripListScreen.kt:191-198`, `res/values/strings.xml:24`; `docs/img/01-trip-list-empty.png` |
| A4 | **Metric-card row**: four identical mint tiles (10 điểm dừng / 12 ảnh / 139 km / 4 ngày), repeated as four tiles on the share image and as a stat strip on My Map. Skill: "icon/number/word metric cards"; prefer data as image or plain type. | `ui/trips/TripDetailScreen.kt:505-529`; `share/SummaryImage.kt:~85-95`; `route-redraw/after-trip-detail.png`, `share-trip/04-summary-hagiang.png`, `route-redraw/after-mymap.png` |
| A5 | **Rounded card with a coloured left bar** — named verbatim in the skill's banned list — on My Map's trip cards, coloured from an 8-hue stock Material palette (`#1565C0 #8E24AA #C79100 #00897B #C2185B #5D4037` + green + clay). | `ui/trips/MyMapScreen.kt:91-92, 230-239`; `route-redraw/after-mymap.png` |
| A6 | **The 2026 "Claude default" palette**: cream background `#FBF8F2` + clay/terracotta `#B85C2E` accent (skill's #1 AI look: ~#F4F1EA + ~#D97757), filled with M3 tonal pastels (mint `#CDE8D9` for stats, FAB, empty-state circle; peach `#FFDBCB` for "Thêm ảnh", "Tải về", photo FAB). | `ui/theme/Theme.kt:12-46`; all screenshots above, `draft-cover/01-step-form-save-draft.png` |
| A7 | **Too many un-derived colours**: transport modes use stock Material 2 hues (`#E65100 #3949AB #8E24AA #455A64 #039BE5 #00897B #6D4C41`), the GPS track a red `#D7263D` (red over the green route on the share image), My Map 8 more. Skill: 2–3 chromatic colours, each justified. | `map/TransportStyle.kt:54-60`, `ui/tracks/TrackUi.kt:73`, `MyMapScreen.kt:91-92`; `share-trip/04-summary-hagiang.png` |
| A8 | **Typography is the M3 default with a font swap**: Be Vietnam Pro on display/headline/title only, body and labels on Roboto (system); sizes are `Typography()` defaults; weight set ad hoc (`fontWeight = FontWeight.SemiBold` in 9 places); no tabular figures for km/counts. | `ui/theme/Type.kt:15-30`; `grep -rn "fontWeight = FontWeight" ui` = 9 |
| A9 | **SaaS card kit**: content boxed in rounded cards with a 1–3 dp shadow, 7 different corner radii in code (6/8/10/12/16/24/28 dp), pill buttons, pill chips, extended pastel FAB plus a second small FAB. | `TripListScreen.kt:229-232`, `MyMapScreen.kt:235`, theme `Shapes` `Theme.kt:49-53`; `grep -oE "RoundedCornerShape\([0-9]+\.dp\)"` |
| A10 | **Stock icons everywhere**: 62 distinct Material icons (filled and outlined mixed), decorative icons in date/time fields, menu items and buttons; the only own drawables are the launcher and the tracking notification icon; the share image's brand mark is a generic route glyph. | `res/drawable/` (2 files); `grep -rhoE 'Icons\.(Outlined|Filled|AutoMirrored\.[A-Za-z]+)\.[A-Za-z]+' | sort -u | wc -l` = 62 |
| A11 | Copy is mostly good and specific (Hà Giang, Tràng An, "đường chim bay"). Minor tells: 4 strings with an em dash, decorative " · " chains in day headers ("Ngày 1 · Thứ Hai, 20/10") — fine where they separate data, not as ornament. | `strings.xml:24,62,240,298`; 13 strings with "·" |
| A12 | **Docs screenshots are partly stale**, so they over-state some problems: `docs/img/offline/01-offline-list.png` (commit e116f3d, 10:19) shows the stock M3 lavender card `#E6E0E9` (pixel sampled), fixed later by f914b3c (14:18 "offline card colours"); `01-trip-list-empty.png` predates Be Vietnam Pro (f11edf9). | `git log -1 -- <png>` vs `git log -- ui/theme/Theme.kt` |

What already works and should be kept: the map is the strongest original element (numbered pins, leg
lines with dash kinds per mode, Vietnamese place labels, R30 sovereignty labels); the copy voice; the
content-first trip detail (timeline first, after the declutter work).

## 4. Visual direction

Constraint check: distinct from Polarsteps (no copying of its screens; flows were inspired by it, so
the *look* must carry the difference), light theme only, local-only (fonts and textures bundled),
Vietnamese diacritics must render well (stacked marks: ặ, ữ, ở). Fonts named below are candidates whose
Google Fonts listing includes a Vietnamese subset; **glyph quality was not checked in this task** —
check ư/ơ/ặ/ữ at 14 and 32 sp before adopting.

**D1 — "Sổ bản đồ" (field atlas).** Lead material: the route itself and terrain. Neutral limestone
paper (cool grey-white, not cream: oklch ≈ L 0.97 C 0.005), ink near-black-green for text, **one** route
colour, contour-line texture generated from real elevation or drawn as thin hairlines at ≤ 8 % opacity
behind headers. Trip covers without a photo = the trip's own route on a mini map (data as image).
Numbers set big in a condensed face (Barlow Condensed / Be Vietnam Pro tabular) like map readouts;
hairline dividers instead of cards. Signature detail: a km scale bar under the trip title, the length of
the trip. Risk: drifts toward a hiking app (Gaia/AllTrails) if the motif is generic contours.

**D2 — "Chàm & đá" (indigo and limestone journal).** Lead material: real photos, framed like a printed
travel journal. Colour sampled from Hà Giang's Hmong indigo (chàm) cloth and Ninh Bình's karst limestone:
indigo primary (oklch ≈ L 0.38 C 0.08 H 265), limestone neutrals, ripe-rice gold only for small
accents. Serif display with Vietnamese support (Newsreader / Source Serif 4 / Literata candidates) +
Be Vietnam Pro body. Day headers as large serif numerals; photos full width with captions; very few
containers. Signature detail: a woven-pattern rule (from the chàm cloth) as the day separator. Risk:
serif-on-paper is the skill's "Claude default" family — acceptable only if the paper is cool and the
accent is indigo, never terracotta.

**D3 — "Tem & vé" (stamps and tickets).** Lead material: physical artefacts. Each step is a postmark
(round date stamp with place name), each leg a ticket stub with mode and km, the share image a
postcard. Bold, playful, very recognisable. Risk: the skill's "over-acted prop metaphor" — after a
week the props get in the way of reading a timeline; costly to keep consistent on map overlays.

**Recommendation: D1 as the system, with one D2 element as the 120 % detail** (indigo as the single
route/accent colour, sampled from chàm, replacing clay and the 7 transport hues; modes keep their dash
kinds and icons, so colour is not needed to tell them apart). Reasons: the map is already the app's
strongest, most original asset; D1 makes every screen read as "this trip" (its route, its km) rather
than "a card"; it removes the cream+terracotta default; it is the cheapest to build in Compose (paths,
hairlines, type) and to port to SwiftUI. Before building, the palette must go through the skill's
sample step: take 6–10 real, licensed photos of Hà Giang and Ninh Bình, sample the dominant hues, and
write one sentence of justification per colour into the design doc. The values above are hypotheses.
The final choice of direction is the Mate's (captain 2026-11-08: "giao diện để mate quyết").

## 5. Prioritised change list

Ordered by "less AI" effect per effort. Each batch is shippable alone and follows the repo rule:
screenshots in `docs/img/<batch>/before-*.png` and `after-*.png` taken from one install in one scripted
run, docs/03 index updated, docs/07 updated for token changes.

| Batch | Changes | Files | Effort | Verify |
|---|---|---|---|---|
| **B0 Decide** (no code) | Mate picks direction; sample palette from real photos; write `docs/08-design.md` (tokens, type scale, do/don't list, Swift equivalents) + AGENTS.md pointer | docs only | S | doc reviewed; every colour has a one-line reason |
| **B1 Stop faking** | Replace generated landscapes with real licensed photos of Hà Giang/Ninh Bình in the *debug* source set (licence + author per file in a README), or neutral honest placeholders; trip cover fallback = route drawn on paper instead of gradient+icon; remove My Map left bar (select state by outline/weight instead); remove em dashes | `data/SampleData.kt:164-197`, `TripListScreen.kt:238-248`, `MyMapScreen.kt:236-239`, `strings.xml` | S–M | before/after: trip list, trip detail, step detail, My Map, share image; release APK size unchanged (photos debug-only) |
| **B2 Tokens** | Palette from B0 (≤3 chromatic, neutral ramp, no cream+terracotta); transport lines in one colour family with dash kinds; type scale from one ratio, Be Vietnam Pro (or chosen pair) for body too, tabular figures; 2 corner radii; elevation 0 + hairlines instead of shadows; tonal pastel containers → neutral surfaces | `ui/theme/Theme.kt`, `Type.kt`, `map/TransportStyle.kt:54-60`, `ui/tracks/TrackUi.kt:73`, `MyMapScreen.kt:91-92`, `share/SummaryImage.kt` | M | full screenshot set before/after; contrast ≥ 4.5:1 on text tokens (compute from tokens); `grep -rn "Color(0x" ui map share` only in theme/style files |
| **B3 Signature** | Stats as type/scale bar not tiles; trip header = route + title on paper; empty state = blank map of the north with a first-step hint, left-aligned, no icon circle; day headers as the chosen 120 % detail; own pin/leg glyphs and app icon | `TripDetailScreen.kt:505-529`, `Common.kt:171-200`, `SummaryImage.kt`, `map/Pins.kt`, launcher | M–L | before/after of those screens; a reviewer-not-builder pass with the critique rubric (concept ≥ 7) |
| **B4 Refresh docs** | Retake stale screenshots (A12) with the new look; drop images that show the generated landscapes | `docs/img/**`, `docs/03-screens.md` | S | no `docs/img` file older than the theme commit for screens it shows |

The unmerged `mate/lutrinh-map-provider` branch (no commits beyond `main` when checked) will add a map
layers button and bottom sheet: it should use `MaterialTheme` tokens only, no new hard-coded colours,
so B2 restyles it for free. Land B2 after it merges, or rebase it onto B2.

## 6. Making future crews follow it

Recommendation: **a repo design doc + an AGENTS.md pointer + a screenshot review step; do not install
the skill for crews.**
- `docs/08-design.md` (platform-neutral, next to 01–07 so the iOS port gets it): direction in one
  sentence, tokens with reasons, type scale, spacing/radius, imagery rules (real photo or honest
  placeholder, never drawn scenes), banned patterns (sections 1–3 above, in our words, one attribution
  line to huashu-design, MIT), and a 6-question review checklist (concept test, ≤3 chromatic colours,
  no decorative icon/gradient/left bar/metric tiles, one 120 % detail, readability floor, matches
  tokens).
- AGENTS.md: one "Standing rule" line — "UI changes follow docs/08-design.md; new colours, fonts,
  radii only as tokens in `ui/theme/`; attach before/after screenshots".
- A cheap mechanical guard as a JVM test or script: no `Color(0x` outside `ui/theme/` and the map style
  file, no `Brush.linearGradient` in `ui/`.
- The skill itself is a poor fit for crews: HTML-only tooling, Chinese prompts, writes `~/.huashu-design`,
  network version check, and a mandatory stop-and-wait gate. Its three-direction process is still
  worth using once, by whoever makes the B0 choice, to see 2–3 directions as real mock-ups first.
