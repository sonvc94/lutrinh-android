# Shared route spec (copy)

Copied unchanged from the Lữ Trình route server repo `lutrinh-server`, branch `main` at commit
**d7d152c**, folder `spec/` (`git show d7d152c:spec/<file>`). The server repo owns these files;
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

**Ahead of the server (leg notes, R41, 2026-11-11):** `route.schema.json` has the optional stop
key `arrivalNote` (trimmed, 1–1000 characters, absent = no note; formatVersion stays 1) and
`package-vectors.json` has seven `arrival note …` cases, one with the integrity code
`arrival_note_on_first_stop`. They follow the shared leg-note contract that `lutrinh-server` is
implementing in parallel (crew `leg-note-server`) and are not yet in a server commit: when the
server's change lands, copy its `spec/` again, name the commit above, and keep the app's problem
code in step with the server's README. The sample `route.json`/`expected.json` are unchanged
(tests add notes to the served copy, `ServedSample(arrivalNotes = …)`).
