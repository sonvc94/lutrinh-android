import gzip, struct, math, sys, json, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor
BASE = "https://tiles.openfreemap.org/planet/20261101_080013_pt/{z}/{x}/{y}.pbf"
def varint(b, i):
    r = 0; s = 0
    while True:
        c = b[i]; i += 1
        r |= (c & 0x7f) << s
        if not c & 0x80: return r, i
        s += 7
def fields(b):
    i = 0; n = len(b)
    while i < n:
        k, i = varint(b, i); f, w = k >> 3, k & 7
        if w == 0: v, i = varint(b, i)
        elif w == 2:
            l, i = varint(b, i); v = b[i:i+l]; i += l
        elif w == 1: v = b[i:i+8]; i += 8
        elif w == 5: v = b[i:i+4]; i += 4
        else: raise Exception("wt")
        yield f, w, v
def packed(b):
    i = 0; out = []
    while i < len(b):
        v, i = varint(b, i); out.append(v)
    return out
def zz(n): return (n >> 1) ^ -(n & 1)
def value(b):
    for f, w, v in fields(b):
        if f == 1: return v.decode()
        if f == 2: return struct.unpack('<f', v)[0]
        if f == 3: return struct.unpack('<d', v)[0]
        if f in (4, 5): return v
        if f == 6: return zz(v)
        if f == 7: return bool(v)
def decode(data, z, x, y):
    out = []
    for f, w, lb in fields(data):
        if f != 3: continue
        name = None; keys = []; vals = []; feats = []; extent = 4096
        for f2, w2, v in fields(lb):
            if f2 == 1: name = v.decode()
            elif f2 == 2: feats.append(v)
            elif f2 == 3: keys.append(v.decode())
            elif f2 == 4: vals.append(value(v))
            elif f2 == 5: extent = v
        for fb in feats:
            tags = []; typ = 0; geom = []
            for f3, w3, v in fields(fb):
                if f3 == 2: tags = packed(v)
                elif f3 == 3: typ = v
                elif f3 == 4: geom = packed(v)
            props = {keys[tags[i]]: vals[tags[i+1]] for i in range(0, len(tags), 2)}
            # first vertex
            px = py = 0; i = 0; first = None; allpts = []
            while i < len(geom):
                cmd = geom[i] & 7; cnt = geom[i] >> 3; i += 1
                if cmd == 7: continue
                for _ in range(cnt):
                    px += zz(geom[i]); py += zz(geom[i+1]); i += 2
                    allpts.append((px, py))
            if not allpts: continue
            def ll(p):
                n = 2 ** z
                lon = (x + p[0] / extent) / n * 360 - 180
                lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * (y + p[1] / extent) / n))))
                return lon, lat
            out.append((name, typ, props, [ll(p) for p in allpts]))
    return out
def fetch(z, x, y):
    for _ in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(BASE.format(z=z, x=x, y=y), headers={"User-Agent": "lutrinh-survey/1.0"}), timeout=30) as r:
                d = r.read(); return gzip.decompress(d) if d[:2] == b"\x1f\x8b" else d
        except urllib.error.HTTPError as e:
            if e.code == 404: return None
        except Exception as e:
            err = e
    return None
def tile_of(lon, lat, z):
    n = 2 ** z
    return int((lon + 180) / 360 * n), int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n)
