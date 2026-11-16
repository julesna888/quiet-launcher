"""Elenca le chiamate al framework Android nelle classi compilate che richiedono un'API più alta di <max-api>.
Su un Fire TV più vecchio una chiamata non protetta da Build.VERSION.SDK_INT manda in crash l'app
(es. Paint.setFontVariationSettings è API 26, il Fire TV Stick 4K con Fire OS 6 è API 25).
Le chiamate già protette compaiono comunque: controllale a mano.
Uso (dopo app/build.sh): python3 tools/apicheck.py $ANDROID_HOME/platforms/android-28/data/api-versions.xml app/out/classes 21
"""
import re, subprocess, sys, pathlib
import xml.etree.ElementTree as ET

xml_path, cdir, MAX = sys.argv[1], sys.argv[2], int(sys.argv[3])
root = ET.parse(xml_path).getroot()
classes = {}
for c in root.iter("class"):
    name = c.get("name")
    since = int(c.get("since", "1"))
    members = {}
    for m in c:
        if m.tag in ("method", "field"):
            members[m.get("name")] = int(m.get("since", str(since)))
    supers = [e.get("name") for e in c if e.tag in ("extends", "implements") and e.get("removed") is None]
    classes[name] = (since, members, supers)

def lookup(cls, member, seen=None):
    seen = seen or set()
    if cls in seen or cls not in classes:
        return None
    seen.add(cls)
    since, members, supers = classes[cls]
    if member in members:
        return max(members[member], since)
    for s in supers:
        r = lookup(s, member, seen)
        if r is not None:
            return r
    return None

files = [str(p) for p in pathlib.Path(cdir).rglob("*.class")]
out = subprocess.run(["javap", "-v", "-p"] + files, capture_output=True, text=True).stdout
own_super = {}
cur = None
for line in out.splitlines():
    m = re.match(r"\s*(?:public |final |abstract |private |protected |static )*class (\S+) extends (\S+)", line)
    if m:
        own_super[m.group(1).replace(".", "/")] = m.group(2).replace(".", "/")

def resolve(cls):
    while cls in own_super and not cls.startswith("android/"):
        cls = own_super[cls]
    return cls

hits = set()
for line in out.splitlines():
    m = re.search(r"= (Methodref|InterfaceMethodref|Fieldref|Class)\s+\S+\s+// (.+)$", line)
    if not m:
        continue
    kind, ref = m.groups()
    ref = ref.strip().strip('"')
    if kind == "Class":
        cls = ref
        if cls.startswith("android/") and cls in classes and classes[cls][0] > MAX:
            hits.add((classes[cls][0], f"class {cls}"))
        continue
    cls, rest = ref.split(".", 1)
    cls = cls.strip('"')
    name, desc = rest.split(":", 1)
    name = name.strip('"')
    base = resolve(cls)
    if not base.startswith("android/"):
        continue
    key = name if kind == "Fieldref" else name + desc
    lvl = lookup(base, key)
    if lvl is not None and lvl > MAX:
        hits.add((lvl, f"{cls}.{name}{'' if kind == 'Fieldref' else desc}"))

for lvl, ref in sorted(hits):
    print(f"API {lvl}: {ref}")
print(f"-- {len(hits)} chiamate sopra API {MAX}")
