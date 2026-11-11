# Shared route spec (copy)

Copied unchanged from the Lữ Trình route server repo `lutrinh-server`, branch `main` at commit
**3cfc5ff** ("Add stop arrivalNote", leg notes R41), folder `spec/` (`git show 3cfc5ff:spec/<file>`). The server repo owns these files;
when it changes them, copy them again and name the new commit here.

| file | used by |
| --- | --- |
| `codes.json` | `RouteSpecVectorsTest` (valid codes, invalid inputs, every single-character typo and transposition) |
| `package-vectors.json` | `RouteSpecVectorsTest` (base package + RFC 6902 patches → expected problem codes) |
| `samples/ha-giang-4n/route.json`, `expected.json` | `RoutePackageTest`, `RouteDownloadImportTest` (served by MockWebServer) |
| `route.schema.json` | `PackageJson` enforces it (required/extra keys, types, enums, patterns, lengths); the vectors check it |
| `canonical-json.json` | reference only (the app never writes route.json) |

The sample's media `sha256`/`bytes` are synthetic (`sha256("lutrinh-sample:<label>")`, no real
file has them). Tests that download it generate bytes per file and rewrite the media entries
(`ServedSample` in the tests). The server's own `lutrinh seed-sample` (lutrinh-server @df870f7)
publishes the same text with ffmpeg placeholder media for a check against the real local server.

**Leg notes (R41, lutrinh-server 3cfc5ff):** `route.schema.json` has the optional stop key
`arrivalNote` (trimmed, 1–1000 code points, absent = no note; formatVersion stays 1), the sample
carries one on stop `s5` "Cột cờ Lũng Cú" (`route.json` sha256 `a96783b0…12016f`, 6590 bytes) and
`package-vectors.json` ends with seven `arrivalNote …` cases. The server treats a note on the
first stop as a publish blocker (`arrival_note_first_stop`, spec/README), not a package rule, so
no vector covers it; the app's `PackageRules.check` refuses such a package itself with the same
code (`RoutePackageTest.arrivalNoteLimitsAndFirstStop`).
