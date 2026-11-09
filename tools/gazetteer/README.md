# Offline gazetteer of Vietnam (R23 place search, R24 auto place name)

`app/src/main/assets/places/vn_places.tsv` is generated from OpenStreetMap by
`build_gazetteer.py`. The app never calls the network for search; refresh the file here when the
data should be newer (e.g. after an administrative change).

## Rebuild

```bash
# 1. tools (once): pyosmium + shapely in a throw-away venv
python3 -m venv /tmp/gaz-venv && /tmp/gaz-venv/bin/pip install osmium shapely
# 2. data: the Geofabrik extract of Vietnam (~330 MB, OSM data © OpenStreetMap contributors, ODbL)
mkdir -p /tmp/gaz-osm && curl -L -o /tmp/gaz-osm/vietnam-latest.osm.pbf \
  https://download.geofabrik.de/asia/vietnam-latest.osm.pbf
# 3. build (~1 min on an M-series Mac); prints the counts per kind
/tmp/gaz-venv/bin/python -I tools/gazetteer/build_gazetteer.py \
  /tmp/gaz-osm/vietnam-latest.osm.pbf app/src/main/assets/places/vn_places.tsv
git diff --stat app/src/main/assets/places/
```

The same `.pbf` gives the same bytes (sorted output; the header names the extract timestamp).
The script stops when the extract does not hold exactly the 34 provinces of the 2025 merger.

## What goes in

* The 34 provinces / centrally-run cities (`admin_level=4` relations, polygons built from the member
  ways), each with its merged former provinces as aliases (`FORMER_PROVINCES`, Nghị quyết
  202/2025/QH15): "Hà Giang" finds Tuyên Quang.
* Communes and wards (`admin_level=6`, representative point of the polygon).
* `place=city|town|village|hamlet|island|islet`.
* Travel places: `tourism=attraction|viewpoint|camp_site|museum|theme_park|zoo`, `natural=peak|
  saddle|waterfall|cave_entrance|bay|beach`, lakes (`natural=water` + `water=lake|reservoir|lagoon`),
  `mountain_pass=yes`, selected `historic=*`, places of worship with a Wikidata/Wikipedia link.
* Every place gets the province whose polygon holds it; places outside Vietnam's 34 are dropped.
  Same name within ~2 km is kept once (higher kind wins). Names without a letter are dropped.

Size (extract of 2026-11-08): ~22 000 places, 0.9 MB TSV, ~0.3 MB compressed in the APK.

## Licence

OpenStreetMap data is © OpenStreetMap contributors under the ODbL 1.0; so is the derived TSV. The
credit is in the file header, in `app/src/main/assets/licenses/OpenStreetMap-ODbL.txt`, under the
search results in the app, and in `docs/05-map.md`.
