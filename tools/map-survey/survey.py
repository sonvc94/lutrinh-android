"""Decode OpenFreeMap z5-14 tiles over the R30 boxes; dump named features to a pickle. See README."""
import sys, json, pickle
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import mvt
from concurrent.futures import ThreadPoolExecutor
BOXES = {"HS": (15.4, 110.9, 17.4, 113.2), "TS": (6.8, 111.3, 12.2, 116.3)}  # S W N E
LAYERS = {"place", "water_name", "aerodrome_label", "transportation_name", "waterway", "mountain_peak", "poi", "boundary"}
def inbox(lon, lat):
    for k, (s, w, n, e) in BOXES.items():
        if s <= lat <= n and w <= lon <= e: return k
def tiles_for(z):
    out = set()
    for s, w, n, e in BOXES.values():
        x0, y1 = mvt.tile_of(w, s, z); x1, y0 = mvt.tile_of(e, n, z)
        out |= {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}
    return out
def run(z, tiles):
    def one(t):
        d = mvt.fetch(z, *t)
        return t, (mvt.decode(d, z, *t) if d else None)
    with ThreadPoolExecutor(24) as ex: return list(ex.map(one, tiles))
feats = []; nonempty = {}
for z in range(5, 15):
    if z <= 12: tiles = tiles_for(z)
    else:
        tiles = {(2*x+i, 2*y+j) for (x, y) in nonempty[z-1] for i in (0,1) for j in (0,1)} & tiles_for(z)
    res = run(z, tiles); ne = set()
    for (x, y), f in res:
        if not f: continue
        # descend if the tile holds any land/feature layer besides water/ocean
        if any(l not in ("water",) for l, *_ in f): ne.add((x, y))
        for l, t, p, pts in f:
            if l in LAYERS:
                for lon, lat in pts[:1] if t == 1 else pts:
                    k = inbox(lon, lat)
                    if k and (p.get("name") or p.get("name:vi") or p.get("name:latin")):
                        feats.append((z, k, l, p, (lon, lat))); break
    nonempty[z] = ne
    print(z, len(tiles), len(ne), len(feats), flush=True)
pickle.dump(feats, open(sys.argv[1] if len(sys.argv) > 1 else "feats.pkl", "wb"))
