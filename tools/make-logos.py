# Rasterizza i tuoi loghi (SVG/PNG in logos-src/, nome = netflix, primevideo, disneyplus, plutotv, youtube, appletv,
# dazn, discoveryplus, nowtv, raiplay, spotify, twitch) in PNG trasparenti per app/assets/logos/ (poi tinti in-app).
# Servono Chrome (CHROME=<percorso> se non in posizione standard macOS) e Pillow.
import re, struct, subprocess, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
(root / ".tmp-logos").mkdir(exist_ok=True)
import os
CH = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
main = {"netflix": ("h", .50), "primevideo": ("w", .74), "disneyplus": ("w", .62), "plutotv": ("w", .62), "youtube": ("h", .44)}
grid = {"nowtv": ("w", 120), "discoveryplus": ("w", 190), "raiplay": ("w", 140), "spotify": ("h", 56), "twitch": ("h", 52), "appletv": ("w", 104), "dazn": ("h", 56)}
def aspect(f):
    if f.suffix == ".png":
        w, h = struct.unpack(">II", f.read_bytes()[16:24]); return w / h
    t = f.read_text()
    m = re.search(r'viewBox="([\d.\-\s,eE]+)"', t)
    if m:
        v = [float(x) for x in re.split(r"[\s,]+", m.group(1).strip())]; return v[2] / v[3]
    w = float(re.search(r'width="([\d.]+)', t).group(1)); h = float(re.search(r'height="([\d.]+)', t).group(1)); return w / h
for f in sorted((root / "logos-src").iterdir()):
    n = f.stem
    if n not in main and n not in grid: continue
    a = aspect(f)
    if n in main:
        k, v = main[n]; w = 560 * v if k == "w" else 350 * v * a
    else:
        k, v = grid[n]; w = v * 1.3 if k == "w" else v * 1.3 * a
    W = int(round(w)); H = int(round(w / a))
    html = root / ".tmp-logos" / (n + ".html")
    html.write_text('<body style="margin:0;background:transparent"><img src="%s" style="width:%dpx;height:%dpx;display:block">' % (f.resolve().as_uri(), W, H))
    out = root / "app/assets/logos" / (n + ".png")
    subprocess.run([CH, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000", "--force-device-scale-factor=1", "--window-size=900,700", "--screenshot=" + str(out), html.as_uri()], capture_output=True, timeout=90)
    from PIL import Image
    Image.open(out).convert("RGBA").crop((0, 0, W, H)).save(out, optimize=True)
    print(n, W, H, out.stat().st_size)
