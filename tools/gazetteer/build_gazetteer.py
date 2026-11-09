#!/usr/bin/env python3
"""
Builds the offline Vietnam gazetteer of Lữ Trình (docs/05 "Place search", rules R23/R24) from an
OpenStreetMap extract.

    python3 -I tools/gazetteer/build_gazetteer.py <vietnam.osm.pbf> app/src/main/assets/places/vn_places.tsv

See tools/gazetteer/README.md for the full recipe (download, venv, licence). The output is plain
UTF-8 TSV, sorted, so the same input gives the same bytes. Data © OpenStreetMap contributors,
ODbL 1.0: the header line, app/src/main/assets/licenses/OpenStreetMap-ODbL.txt and the
picker's result-list footer say so.

File format (one record per line, tab-separated):
    #  <comment>                                  header lines
    R  <id> <name> <aliases> <lat> <lng>          a province / city of the 34 (after 1 July 2025)
    <kind> <region id> <lat> <lng> <flags> <name> <aliases>
kinds: c city, t town, a commune/ward, v village, h hamlet, s sight (tourism=attraction),
w viewpoint, k peak, p mountain pass, f waterfall, g cave, b bay, e beach, i island, l lake,
m museum / historic, r notable place of worship, n camp site.
flags: "n" = notable (has wikidata/wikipedia) or "-". lat/lng: degrees x 1e5, integers.
aliases: ";"-separated (alt_name, old_name, short_name, name:en, former province names).
"""
import sys
import unicodedata
from collections import defaultdict

import osmium
import shapely
from shapely import wkb
from shapely.ops import polygonize
from shapely.strtree import STRtree

# Provinces merged on 1 July 2025 (Nghị quyết 202/2025/QH15): new name -> former provinces.
# Names are the display names below (without "Tỉnh"/"Thành phố").
FORMER_PROVINCES = {
    "Tuyên Quang": ["Hà Giang"],
    "Lào Cai": ["Yên Bái"],
    "Thái Nguyên": ["Bắc Kạn"],
    "Phú Thọ": ["Vĩnh Phúc", "Hòa Bình"],
    "Bắc Ninh": ["Bắc Giang"],
    "Hưng Yên": ["Thái Bình"],
    "Hải Phòng": ["Hải Dương"],
    "Ninh Bình": ["Hà Nam", "Nam Định"],
    "Quảng Trị": ["Quảng Bình"],
    "Đà Nẵng": ["Quảng Nam"],
    "Quảng Ngãi": ["Kon Tum"],
    "Gia Lai": ["Bình Định"],
    "Khánh Hòa": ["Ninh Thuận"],
    "Lâm Đồng": ["Đắk Nông", "Bình Thuận"],
    "Đắk Lắk": ["Phú Yên"],
    "Hồ Chí Minh": ["Bình Dương", "Bà Rịa - Vũng Tàu", "Bà Rịa Vũng Tàu", "Sài Gòn"],
    "Đồng Nai": ["Bình Phước"],
    "Tây Ninh": ["Long An"],
    "Cần Thơ": ["Sóc Trăng", "Hậu Giang"],
    "Vĩnh Long": ["Bến Tre", "Trà Vinh"],
    "Đồng Tháp": ["Tiền Giang"],
    "Cà Mau": ["Bạc Liêu"],
    "An Giang": ["Kiên Giang"],
}
PROVINCE_PREFIXES = ("Thành phố ", "Tỉnh ")

HISTORIC = {"monument", "archaeological_site", "castle", "fort", "ruins", "citadel", "city_gate",
            "tomb", "temple", "pagoda", "battlefield", "heritage", "building", "manor"}
PLACE_KIND = {"city": "c", "town": "t", "village": "v", "hamlet": "h", "island": "i", "islet": "i"}


def kind_of(t):
    """Kind letter of a travel place or settlement, or None (order = precedence)."""
    tourism, natural, historic = t.get("tourism"), t.get("natural"), t.get("historic")
    if natural == "bay":
        return "b"
    if natural == "peak" or natural == "volcano":
        return "k"
    if natural == "saddle" or t.get("mountain_pass") == "yes":
        return "p"
    if natural == "waterfall" or t.get("waterway") == "waterfall":
        return "f"
    if natural == "cave_entrance":
        return "g"
    if natural == "beach":
        return "e"
    if tourism == "viewpoint":
        return "w"
    if tourism in ("attraction", "theme_park", "zoo"):
        return "s"
    if tourism == "museum" or historic in HISTORIC:
        return "m"
    if tourism == "camp_site":
        return "n"
    if natural == "water" and t.get("water") in ("lake", "reservoir", "lagoon"):
        return "l"
    if t.get("amenity") == "place_of_worship" and ("wikidata" in t or "wikipedia" in t):
        return "r"
    return PLACE_KIND.get(t.get("place"))


def nfc(s):
    """OSM names come in both composed and decomposed Unicode; the app compares NFC text."""
    return unicodedata.normalize("NFC", s).strip()


def name_of(t):
    return nfc(t.get("name:vi") or t.get("name") or "")


def aliases_of(t, name):
    out = []
    for k in ("alt_name", "old_name", "short_name", "official_name", "name:en", "name"):
        for v in (t.get(k) or "").split(";"):
            v = nfc(v)
            if v and v != name and v not in out and "\t" not in v:
                out.append(v)
    return out


def strip_province(n):
    for p in PROVINCE_PREFIXES:
        if n.startswith(p):
            return n[len(p):]
    return n


def main(pbf, out_path):
    wkbf = osmium.geom.WKBFactory()
    provinces = []   # (name, geometry)
    communes = []    # (name, aliases, rep point)
    places = []      # (kind, flags, name, aliases, lat, lng)

    area_filter = osmium.filter.KeyFilter("boundary", "tourism", "natural", "historic", "place",
                                          "amenity", "mountain_pass")
    fp = (osmium.FileProcessor(pbf)
          .with_locations()
          .with_areas(area_filter)
          .with_filter(osmium.filter.KeyFilter("name", "name:vi")))
    for o in fp:
        t = o.tags
        name = name_of(t)
        if not any(ch.isalpha() for ch in name) or "\t" in name:
            continue  # unnamed, or a bare number ("1", "10")
        if o.is_area():
            if t.get("boundary") == "administrative":
                level = t.get("admin_level")
                if level not in ("4", "6"):
                    continue
                try:
                    g = wkb.loads(wkbf.create_multipolygon(o), hex=True)
                except Exception:
                    continue
                if level == "6":
                    p = g.representative_point()
                    communes.append((name, aliases_of(t, name), p.y, p.x))
                continue
            k = kind_of(t)
            if k is None:
                continue
            try:
                g = wkb.loads(wkbf.create_multipolygon(o), hex=True)
            except Exception:
                continue
            p = g.representative_point()
            places.append((k, notable(t), name, aliases_of(t, name), p.y, p.x))
        elif o.is_node():
            k = kind_of(t)
            if k is None or not o.location.valid():
                continue
            places.append((k, notable(t), name, aliases_of(t, name), o.location.lat, o.location.lon))

    provinces = province_polygons(pbf)
    names = [p[0] for p in provinces]
    if len(provinces) != 34 or set(FORMER_PROVINCES) - set(names):
        sys.exit(f"expected the 34 provinces after 2025, got {len(provinces)}: {names}")
    tree = STRtree([p[1] for p in provinces])

    def region(lat, lng):
        hits = tree.query(shapely.Point(lng, lat), predicate="intersects")
        return int(min(hits)) if len(hits) else None

    rows = set()
    for name, al, lat, lng in communes:
        r = region(lat, lng)
        if r is not None:
            rows.add(("a", r, round(lat * 1e5), round(lng * 1e5), "-", name, ";".join(al)))
    for k, fl, name, al, lat, lng in places:
        r = region(lat, lng)
        if r is not None:
            rows.add((k, r, round(lat * 1e5), round(lng * 1e5), fl, name, ";".join(al)))

    rows = dedupe(rows)
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Lữ Trình gazetteer v1 · data © OpenStreetMap contributors, ODbL 1.0 "
                "(openstreetmap.org/copyright) · built by tools/gazetteer/build_gazetteer.py\n")
        f.write(f"# source: {source_stamp(pbf)} · {len(rows)} places\n")
        for i, (name, g) in enumerate(provinces):
            c = g.representative_point()
            f.write("\t".join(["R", str(i), name, ";".join(FORMER_PROVINCES.get(name, [])),
                               str(round(c.y * 1e5)), str(round(c.x * 1e5))]) + "\n")
        for r in sorted(rows, key=lambda x: (x[1], x[5], x[0], x[2], x[3])):
            f.write("\t".join(str(x) for x in r) + "\n")
    counts = defaultdict(int)
    for r in rows:
        counts[r[0]] += 1
    print(f"{len(provinces)} provinces, {len(rows)} places", dict(sorted(counts.items())))


def province_polygons(pbf):
    """
    The 34 province polygons, built from the member ways of the admin_level=4 relations: the
    extract's area assembler drops relations with one broken ring (Hà Nội, Hà Tĩnh, Khánh Hòa in
    2026-11), polygonize keeps every closed part.
    """
    rels = {}  # name -> (outer way ids, inner way ids)
    for o in osmium.FileProcessor(pbf, osmium.osm.RELATION):
        t = o.tags
        name = nfc(t.get("name") or "")  # name:vi may lack the "Tỉnh"/"Thành phố" prefix
        if t.get("boundary") == "administrative" and t.get("admin_level") == "4" \
                and name.startswith(PROVINCE_PREFIXES):
            outer = {m.ref for m in o.members if m.type == "w" and m.role != "inner"}
            inner = {m.ref for m in o.members if m.type == "w" and m.role == "inner"}
            rels[strip_province(name)] = (outer, inner)
    wanted = set().union(*(a | b for a, b in rels.values()))
    lines = {}
    for w in osmium.FileProcessor(pbf, osmium.osm.NODE | osmium.osm.WAY).with_locations():
        if w.is_way() and w.id in wanted:
            pts = [(n.lon, n.lat) for n in w.nodes if n.location.valid()]
            if len(pts) >= 2:
                lines[w.id] = shapely.LineString(pts)
    out = []
    for name in sorted(rels):
        outer, inner = rels[name]
        shell = shapely.union_all(list(polygonize([lines[i] for i in outer if i in lines])))
        holes = list(polygonize([lines[i] for i in inner if i in lines]))
        g = shell.difference(shapely.union_all(holes)) if holes else shell
        if g.is_empty:
            sys.exit(f"no polygon for {name}")
        out.append((name, g))
    return out


def notable(t):
    return "n" if ("wikidata" in t or "wikipedia" in t) else "-"


PRECEDENCE = "cta" "sbkpfgewilmrn" "vh"


def dedupe(rows):
    """One record per (name, place): the same name within 2 km keeps the higher-precedence kind."""
    by_name = defaultdict(list)
    for r in rows:
        by_name[r[5]].append(r)
    out = []
    for group in by_name.values():
        group.sort(key=lambda r: (PRECEDENCE.index(r[0]), r[4] != "n", r[2], r[3]))
        kept = []
        for r in group:
            if all(abs(r[2] - k[2]) > 2000 or abs(r[3] - k[3]) > 2000 for k in kept):
                kept.append(r)
            else:
                # a duplicate may still carry the notable flag or aliases the kept one lacks
                k = next(k for k in kept if abs(r[2] - k[2]) <= 2000 and abs(r[3] - k[3]) <= 2000)
                if r[4] == "n" and k[4] != "n":
                    kept[kept.index(k)] = k[:4] + ("n",) + k[5:]
        out.extend(kept)
    return out


def source_stamp(pbf):
    h = osmium.io.Reader(pbf, osmium.osm.osm_entity_bits.NOTHING).header()
    ts = h.get("osmosis_replication_timestamp") or "unknown time"
    return f"{pbf.rsplit('/', 1)[-1]} ({ts})"


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
