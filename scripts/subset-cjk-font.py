#!/usr/bin/env python3
import sys
import tempfile
import urllib.request
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "design" / "src" / "main" / "res" / "font" / "noto_sans_sc_subset.ttf"
CACHE = Path(tempfile.gettempdir()) / "NotoSansSC-VF.ttf"
SOURCES = (
    "https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf",
    "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf",
)
MAX_BYTES = 4_500_000

EXTRA_RANGES = (
    (0x3000, 0x303F),
    (0xFF00, 0xFFEF),
    (0x2010, 0x2027),
    (0x3040, 0x30FF),
)
EXTRA_CHARS = ("\u00b7",)


def gb2312_hanzi():
    chars = set()
    for high in range(0xB0, 0xF8):
        for low in range(0xA1, 0xFF):
            try:
                chars.add(bytes((high, low)).decode("gb2312"))
            except UnicodeDecodeError:
                continue
    return chars


def charset():
    chars = gb2312_hanzi()
    for start, end in EXTRA_RANGES:
        chars.update(chr(code) for code in range(start, end + 1))
    chars.update(EXTRA_CHARS)
    return chars


def download():
    if CACHE.exists() and CACHE.stat().st_size > 0:
        return CACHE
    request_headers = {"User-Agent": "Mozilla/5.0"}
    for url in SOURCES:
        try:
            request = urllib.request.Request(url, headers=request_headers)
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
            if data:
                CACHE.write_bytes(data)
                return CACHE
        except Exception:
            continue
    raise RuntimeError("unable to download Noto Sans SC")


def build_options():
    options = subset.Options()
    options.layout_features = ["*"]
    options.hinting = False
    options.drop_tables += ["BASE", "JSTF", "DSIG"]
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    options.notdef_glyph = True
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.glyph_names = True
    options.symbol_cmap = True
    options.legacy_cmap = True
    return options


def main():
    chars = charset()
    source = download()
    options = build_options()
    font = subset.load_font(str(source), options)
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=[ord(char) for char in chars])
    subsetter.subset(font)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    subset.save_font(font, str(OUTPUT), options)
    size = OUTPUT.stat().st_size
    if size > MAX_BYTES:
        raise SystemExit(f"subset too large: {size} bytes > {MAX_BYTES}")
    check = TTFont(str(OUTPUT))
    cmap = check.getBestCmap()
    missing = [char for char in gb2312_hanzi() if ord(char) not in cmap]
    if missing:
        raise SystemExit(f"missing GB2312 glyphs: {len(missing)}")
    axes = [axis.axisTag for axis in check["fvar"].axes]
    if "wght" not in axes:
        raise SystemExit("wght axis missing")
    print(f"charset={len(chars)} glyphs={check['maxp'].numGlyphs} cmap={len(cmap)} bytes={size} axes={axes}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
