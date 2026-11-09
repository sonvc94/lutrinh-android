# Map survey (R30)
Proves which labels the R30 filter keeps over Hoàng Sa / Trường Sa (docs/05 "Hoàng Sa / Trường Sa").
Needs only Python 3 (stdlib; no protobuf lib, the tile decoder is `mvt.py`) and network access.

    python3 -I tools/map-survey/survey.py /tmp/feats.pkl   # z5–12 every tile of both boxes, z13–14 below non-empty parents (~25 000 tiles, ~3 min)
    python3 -I tools/map-survey/report.py /tmp/feats.pkl   # the rule applied; lists every label kept

`BASE` in `mvt.py` is the OpenFreeMap tile-set path (`curl https://tiles.openfreemap.org/planet` →
`tiles[0]`); update it with a new tile build. `report.py` mirrors `SovereigntyLabels.hides`: change
both together. Only layers the style draws as labels (`place`, `water_name`, `waterway`,
`transportation_name`, `aerodrome_label`) are judged; Positron has no POI symbol layer.
