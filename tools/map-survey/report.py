"""Apply the R30 rule (mirror of SovereigntyLabels.hides) to feats.pkl and list what stays/hides.
Usage: python3 -I report.py feats.pkl   -- only layers the Positron style draws as labels are judged."""
import pickle, sys
from collections import defaultdict
NAME_FIELDS = ["name", "name:vi", "name:latin", "name_en", "name:en", "name_int", "name:zh"]
BLOCKED = {"三沙市", "三沙", "Tam Sa", "Thành phố Tam Sa", "Sansha", "Sansha City", "西沙区", "西沙", "Quận Tây Sa", "Tây Sa", "Xisha", "Xisha District",
           "南沙区", "南沙", "Quận Nam Sa", "Nam Sa", "Nansha", "Nansha District", "永兴镇", "Yongxing", "永乐群岛", "Yongle Qundao", "七连屿", "Qilianyu", "渚碧岛", "Zhubi",
           "Kalayaan", "Pag-asa", "Pag-asa Island Harbor", "Rancudo Airfield", "Layang-Layang"}
ADMIN = {"country", "state", "province", "region", "county", "district", "municipality", "city", "town", "village", "hamlet", "suburb", "quarter", "neighbourhood", "isolated_dwelling"}
VI_ADMIN = {"Trường Sa", "Hoàng Sa", "An Bang", "Sinh Tồn", "Song Tử Tây", "Thuyền Chài D", "Thuyền Chài E", "Đá Tây A"}
NATURAL = {"island", "ocean", "sea", "bay", "strait", "lake"}
SUBS = ["Tam Sa", "Tây Sa", "Nam Sa", "Sansha", "Xisha", "Nansha"]
DRAWN = {"place", "water_name", "waterway", "transportation_name", "aerodrome_label"}
def hides(p):
    if any(p.get(f) in BLOCKED for f in NAME_FIELDS): return True
    if any(isinstance(p.get(f), str) and sub in p[f] for f in ("name:vi", "name:latin", "name_en", "name:en") for sub in SUBS): return True
    if p.get("class") in ADMIN and p.get("name") not in VI_ADMIN: return True
    return p.get("name:nonlatin") is not None and not (p.get("class") in NATURAL and p.get("name:vi") is not None)
kept = defaultdict(lambda: [99, 0]); hid = 0
for z, box, layer, p, pt in pickle.load(open(sys.argv[1], "rb")):
    if layer not in DRAWN: continue
    if hides(p): hid += 1; continue
    shown = p.get("name:vi") or p.get("name:latin") or p.get("name")
    a = kept[(layer, p.get("class"), shown, p.get("name"))]; a[0] = min(a[0], z); a[1] = max(a[1], z)
print("hidden features:", hid)
for (layer, cls, shown, local), (z0, z1) in sorted(kept.items(), key=lambda kv: [str(x) for x in kv[0]]):
    print(f"{layer:18} {str(cls):10} z{z0}-{z1:<3} shown={shown!r}" + ("" if shown == local else f" (name={local!r})"))
