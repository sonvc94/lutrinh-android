"""
Vietnamese names for places OSM tags with an English "… Pass" (gazetteer rule, docs/05 "Place search").

`name:vi` of "Mã Pí Lèng" is literally "Mã Pí Lèng Pass" in OSM, so the generator rewrites it:
a viewpoint, sight or mountain pass named "<X> Pass" is stored as "Đèo <X>" and the original
name is kept as a search alias (typing "Pass" or the old name still finds it).

Pure functions, no osmium: `python3 -I -m unittest tools/gazetteer/test_place_names.py`.
`python3 -I tools/gazetteer/place_names.py <vn_places.tsv>` applies the rule to an existing TSV
in place (same rows, same sort order as a fresh build; running it twice changes nothing).
"""
import sys
import unicodedata

SUFFIX = " Pass"
PREFIX = "Đèo "
# Kinds that can be a pass: p = mountain pass, w = viewpoint, s = sight. Heritage ("m") rows named
# "Raving Pass" / "Mu Gua Pass" are not passes and keep their name.
KINDS = "pws"
# "Snake Pass" is an English word, not a Vietnamese pass name: "Đèo Snake" would read wrong.
KEEP = {"Snake Pass"}


def nfc(s):
    return unicodedata.normalize("NFC", s)


def pass_name(kind, name):
    """The Vietnamese name for [name], or None when the rule does not apply."""
    name = nfc(name)
    if kind not in KINDS or name in KEEP or not name.endswith(SUFFIX):
        return None
    rest = name[: -len(SUFFIX)].strip()
    if not rest or rest.lower().startswith(("đèo ", "deo ")):
        return None
    return PREFIX + rest


def apply(kind, name, aliases):
    """(name, aliases) after the rule: the old name goes first among the aliases."""
    new = pass_name(kind, name)
    if new is None:
        return name, aliases
    return new, [name] + [a for a in aliases if a != name]


def rewrite_tsv(path):
    """Applies the rule to a gazetteer TSV; returns the (old, new) pairs."""
    with open(path, encoding="utf-8") as f:
        lines = f.read().split("\n")
    head = [l for l in lines if l.startswith("#")]
    regions = [l for l in lines if l.startswith("R\t")]
    rows, changed = [], []
    for l in lines:
        if not l or l.startswith("#") or l.startswith("R\t"):
            continue
        c = l.split("\t")
        al = [a for a in (c[6] if len(c) > 6 else "").split(";") if a]
        name, al = apply(c[0], c[5], al)
        if name != c[5]:
            changed.append((c[5], name))
        rows.append((c[0], int(c[1]), int(c[2]), int(c[3]), c[4], name, ";".join(al)))
    rows.sort(key=lambda x: (x[1], x[5], x[0], x[2], x[3]))
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for l in head + regions:
            f.write(l + "\n")
        for r in rows:
            f.write("\t".join(str(x) for x in r) + "\n")
    return changed


if __name__ == "__main__":
    for old, new in rewrite_tsv(sys.argv[1]):
        print(f"{old} -> {new}")
