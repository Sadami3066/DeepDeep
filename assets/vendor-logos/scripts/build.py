#!/usr/bin/env python3
"""Rebuild DeepDeep vendor logo assets from svg/ and upstream/ sources.

Usage:
  NODE_PATH=<dir containing sharp> python3 scripts/build.py

Requires: ImageMagick 7 (`magick`), Node.js with `sharp` (libvips/librsvg),
Python 3. sharp is only used to rasterize SVG and derive 256/192 px PNGs.
"""
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile

PACK = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(os.path.dirname(PACK))
APP_RES = os.path.join(REPO, "app/src/main/res/drawable-nodpi")
SCRIPTS = os.path.join(PACK, "scripts")
MAGICK = "magick"

NODE_PATH = os.environ.get("NODE_PATH", "")
ENV = dict(os.environ)
if NODE_PATH:
    ENV["NODE_PATH"] = NODE_PATH


def run(args, **kw):
    r = subprocess.run(args, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        print("FAIL", args, "\n", r.stderr[-1000:])
        sys.exit(1)
    return r.stdout


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def check_tools():
    run([MAGICK, "-version"])
    try:
        run(["node", "-e", "require('sharp')"], env=ENV)
    except SystemExit:
        print("sharp not resolvable; run with NODE_PATH=<node_modules dir containing sharp>", file=sys.stderr)
        sys.exit(1)


ICONS = [
    ("feishu",   "飞书 Feishu",   "appstore", None,      "App Store artwork / brand trademark"),
    ("wecom",    "企业微信 WeCom",  "appstore", None,      "App Store artwork / brand trademark"),
    ("wechat",   "微信 WeChat",    "appstore", "#07C160", "App Store artwork / brand trademark"),
    ("xiaomi",   "小米 Xiaomi",    "svg",      "#FF6900", "Public domain (Wikimedia Commons)"),
    ("dingtalk", "钉钉 DingTalk",  "svg",      "#0089FF", "MIT (Tabler Icons)"),
    ("notion",   "Notion",       "svg",      "#000000", "MIT (Tabler Icons)"),
    ("github",   "GitHub",       "svg",      "#181717", "MIT (Tabler Icons)"),
    ("slack",    "Slack",        "svg",      "#4A154B", "MIT (Tabler Icons)"),
    ("telegram", "Telegram",     "svg",      "#26A5E4", "MIT (Tabler Icons)"),
    ("discord",  "Discord",      "svg",      "#5865F2", "MIT (Tabler Icons)"),
]

APPSTORE_META = {
    "feishu": {
        "track_name": "飞书 - 字节跳动旗下 AI 工作平台",
        "artist": "Beijing Feishu Technology Co., Ltd.",
        "search_url": "https://itunes.apple.com/search?term=%E9%A3%9E%E4%B9%A6&country=cn&entity=software&limit=5",
        "artwork_url": "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/45/f4/d8/45f4d8ef-fc19-8e6e-19eb-4e1e4ee1aabd/AppIcon-0-0-1x_U007epad-0-0-0-1-0-0-85-220.png/512x512bb.jpg",
        "upstream": "upstream/feishu-appstore-512.jpg",
        "process": "white background flood-filled to transparent (8% fuzz), trimmed, 4% transparent margin, square 512",
        "mode": "white-bg",
    },
    "wecom": {
        "track_name": "企业微信",
        "artist": "Tencent Technology (Shenzhen) Company Limited",
        "search_url": "https://itunes.apple.com/search?term=%E4%BC%81%E4%B8%9A%E5%BE%AE%E4%BF%A1&country=cn&entity=software&limit=5",
        "artwork_url": "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/3e/ba/6b/3eba6be1-a7fd-00c5-7f0d-2415c8258ef5/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/512x512bb.jpg",
        "upstream": "upstream/wecom-appstore-512.jpg",
        "process": "white background flood-filled to transparent (8% fuzz), trimmed, 4% transparent margin, square 512",
        "mode": "white-bg",
    },
    "wechat": {
        "track_name": "微信",
        "artist": "Tencent Technology (Shenzhen) Company Limited",
        "search_url": "https://itunes.apple.com/search?term=%E5%BE%AE%E4%BF%A1&country=cn&entity=software&limit=5",
        "artwork_url": "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/84/6b/3a/846b3ab8-9dea-e3e5-861e-f6ecaa0b3387/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-0-0-85-220.png/512x512bb.jpg",
        "upstream": "upstream/wechat-appstore-512.jpg",
        "process": "green tile keyed out (12% fuzz), white mark recolored to #07C160, trimmed, 4% transparent margin, square 512",
        "mode": "wechat",
    },
}

TABLER_META = {
    "dingtalk": ("brand-dingtalk.svg", "#0089FF"),
    "notion":   ("brand-notion.svg",   "#000000"),
    "github":   ("brand-github.svg",   "#181717"),
    "slack":    ("brand-slack.svg",    "#4A154B"),
    "telegram": ("brand-telegram.svg", "#26A5E4"),
    "discord":  ("brand-discord.svg",  "#5865F2"),
}
TABLER_COMMIT = "ed177a085b652fe04ec4f37ced867c94621e7840"
TABLER_VERSION = "3.50.0"


def main():
    check_tools()
    os.makedirs(os.path.join(PACK, "png/512"), exist_ok=True)
    os.makedirs(os.path.join(PACK, "png/256"), exist_ok=True)
    os.makedirs(os.path.join(PACK, "png/192"), exist_ok=True)
    os.makedirs(os.path.join(PACK, "android/res/drawable-nodpi"), exist_ok=True)
    os.makedirs(APP_RES, exist_ok=True)
    work = tempfile.mkdtemp(prefix="vendor-logos-")

    for icon_id, _name, kind, _color, _lic in ICONS:
        master = os.path.join(work, f"{icon_id}-512.png")
        if kind == "appstore":
            meta = APPSTORE_META[icon_id]
            run([os.path.join(SCRIPTS, "build_appstore_master.sh"), meta["mode"],
                 os.path.join(PACK, meta["upstream"]), master])
        else:
            svg = os.path.join(PACK, "svg", f"{icon_id}.svg")
            run(["node", os.path.join(SCRIPTS, "render_svg.js"), svg, master, "512", "2400"], env=ENV)
        shutil.copy2(master, os.path.join(PACK, "png/512", f"{icon_id}.png"))
        run(["node", os.path.join(SCRIPTS, "derive_sizes.js"), master,
             os.path.join(PACK, "png/256"), os.path.join(PACK, "png/192")], env=ENV)
        # derive_sizes names output after the master ("<id>-512.png"), keep <id>.png inside png/ dirs
        for size in (256, 192):
            derived = os.path.join(PACK, f"png/{size}/{icon_id}-512.png")
            if os.path.exists(derived):
                os.replace(derived, os.path.join(PACK, f"png/{size}/{icon_id}.png"))
        shutil.copy2(os.path.join(PACK, "png/192", f"{icon_id}.png"),
                     os.path.join(PACK, "android/res/drawable-nodpi", f"vendor_{icon_id}.png"))
        shutil.copy2(os.path.join(PACK, "png/192", f"{icon_id}.png"),
                     os.path.join(APP_RES, f"vendor_{icon_id}.png"))
        print("built", icon_id)

    preview = [MAGICK, "montage"]
    for icon_id, *_ in ICONS:
        preview += ["-label", icon_id, os.path.join(PACK, "png/256", f"{icon_id}.png")]
    preview += ["-background", "#f5f5f5", "-fill", "#333333", "-font", "DejaVu-Sans",
                "-pointsize", "22", "-geometry", "200x200+18+18", "-tile", "5x2",
                os.path.join(PACK, "preview.png")]
    run(preview)

    entries = []
    for icon_id, name, kind, color, lic in ICONS:
        files = {
            "png_512": f"png/512/{icon_id}.png",
            "png_256": f"png/256/{icon_id}.png",
            "png_192": f"png/192/{icon_id}.png",
            "android_drawable": f"android/res/drawable-nodpi/vendor_{icon_id}.png",
        }
        entry = {
            "id": icon_id,
            "name": name,
            "source_kind": "apple-app-store-artwork" if kind == "appstore" else (
                "wikimedia-commons-svg" if icon_id == "xiaomi" else "tabler-icons-svg"),
            "license": lic,
            "brand_color": color,
            "files": files,
            "sha256": {k: sha256(os.path.join(PACK, v)) for k, v in files.items()},
            "bytes": {k: os.path.getsize(os.path.join(PACK, v)) for k, v in files.items()},
        }
        if kind == "appstore":
            meta = dict(APPSTORE_META[icon_id])
            meta.pop("mode", None)
            entry["source"] = meta
        elif icon_id == "xiaomi":
            svg = os.path.join(PACK, "svg/xiaomi.svg")
            entry["source"] = {
                "svg": "svg/xiaomi.svg",
                "page": "https://commons.wikimedia.org/wiki/File:Xiaomi_logo.svg",
                "original_credit": "http://www.mi.com/en/about/mediakit (media kit; PD conversion by Wikimedia contributors)",
                "note": "Official Xiaomi logo; Wikimedia Commons marks it public domain (below threshold of originality). Brand trademark remains with Xiaomi.",
            }
            entry["sha256"]["svg"] = sha256(svg)
        else:
            src_file, hexcolor = TABLER_META[icon_id]
            svg = os.path.join(PACK, "svg", f"{icon_id}.svg")
            entry["source"] = {
                "svg": f"svg/{icon_id}.svg",
                "repo": "https://github.com/tabler/tabler-icons",
                "version": TABLER_VERSION,
                "commit": TABLER_COMMIT,
                "path": f"icons/outline/{src_file}",
                "raw_url": f"https://raw.githubusercontent.com/tabler/tabler-icons/{TABLER_COMMIT}/icons/outline/{src_file}",
                "modification": f"stroke=\"currentColor\" replaced with brand color {hexcolor}; rasterized with sharp/libvips at 512px.",
            }
            entry["sha256"]["svg"] = sha256(svg)
        entries.append(entry)

    manifest = {
        "title": "DeepDeep vendor logo assets",
        "generated": datetime.date.today().isoformat(),
        "generator": "sharp (libvips) for SVG rasterization + ImageMagick for App Store artwork cleanup; see scripts/",
        "icon_count": len(entries),
        "android_drawable_prefix": "vendor_",
        "android_drawable_dir": "app/src/main/res/drawable-nodpi",
        "kotlin_map_file": "app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/VendorIcons.kt",
        "tabler": {"version": TABLER_VERSION, "commit": TABLER_COMMIT,
                   "license": "MIT", "license_file": "licenses/LICENSE-Tabler-MIT.txt"},
        "app_store_artwork_notice": "licenses/NOTICE-AppStore-Artwork.md",
        "xiaomi_license_notice": "licenses/NOTICE-Xiaomi-Logo.md",
        "icons": entries,
    }
    with open(os.path.join(PACK, "manifest.json"), "w") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")
    total = sum(os.path.getsize(os.path.join(PACK, v)) for e in entries for v in e["files"].values())
    print("manifest ok; png bytes:", total)


if __name__ == "__main__":
    main()
