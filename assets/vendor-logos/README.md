# PI vendor logos

Brand icons for the first ten vendors/services, reused in the plugin list, vendor identification and settings pages. All are transparent PNGs named `vendor_<id>`. The asset directory tracks provenance notes, manifests, license texts, vector sources and the regeneration scripts; raster images (`png/`, `upstream/`, `preview.png`, `android/res/`) stay local under `.gitignore`. What actually ships in the app is `app/src/main/res/drawable-nodpi/vendor_<id>.png`.

## Layout

```
assets/vendor-logos/
├── manifest.json                 # icon manifest: source URL, license, size, sha256 (tracked)
├── preview.png                   # overview of the 10 icons on a light background (local)
├── svg/                          # editable vector sources (7: Tabler 6 + Xiaomi, tracked)
├── upstream/                     # original App Store JPEGs (3, for review/replacement; local)
├── png/512/<id>.png              # 512×512 transparent PNG (local)
├── png/256/<id>.png              # 256×256 transparent PNG (local)
├── png/192/<id>.png              # 192×192 transparent PNG (local)
├── android/res/drawable-nodpi/   # vendor_<id>.png to copy into an Android project (192, local)
├── scripts/                      # reproducible build scripts (tracked, see below)
└── licenses/                     # MIT / public domain / App Store artwork notes (tracked)
```

## Icon list

| id | Name | Artwork source | License |
|---|---|---|---|
| `feishu` | Feishu | Apple App Store icon (`upstream/feishu-appstore-512.jpg`) | Public App Store icon, trademark of ByteDance |
| `wecom` | WeCom | Apple App Store icon | Public App Store icon, trademark of Tencent |
| `wechat` | WeChat | Apple App Store icon (green background removed, mark recolored `#07C160`) | Public App Store icon, trademark of Tencent |
| `xiaomi` | Xiaomi | Wikimedia Commons `Xiaomi_logo.svg` (from Xiaomi's official media kit) | Public domain (below the threshold of originality), trademark of Xiaomi |
| `dingtalk` | DingTalk | Tabler Icons `brand-dingtalk` | MIT, color `#0089FF` |
| `notion` | Notion | Tabler Icons `brand-notion` | MIT, color `#000000` |
| `github` | GitHub | Tabler Icons `brand-github` | MIT, color `#181717` |
| `slack` | Slack | Tabler Icons `brand-slack` | MIT, color `#4A154B` |
| `telegram` | Telegram | Tabler Icons `brand-telegram` | MIT, color `#26A5E4` |
| `discord` | Discord | Tabler Icons `brand-discord` | MIT, color `#5865F2` |

Tabler snapshot: `tabler-icons` v3.50.0, commit `ed177a085b652fe04ec4f37ced867c94621e7840`; sources live in `icons/outline/` of the Tabler repository, with only `stroke="currentColor"` replaced by a brand color. The exact source URL, sha256 and byte size for each icon are in `manifest.json`.

## Specifications

- Every PNG has a transparent background, is square and centered; App Store icons keep 4% transparent padding and Tabler line icons keep the original `24×24` viewBox ratio.
- `png/192/<id>.png` is the 192px derivative copied into `app/src/main/res/drawable-nodpi/` (PNGs in the asset directory are not tracked); the largest measures 9.6 KB (cap 30 KB).
- Android resource names allow only lowercase letters, digits and underscores: `vendor_<id>.png`, one-to-one with plugin ids (the `micloud` plugin reuses `vendor_xiaomi`).
- Tracked: the 10 files in `app/src/main/res/drawable-nodpi/`; the Kotlin mapping is in
  `app/src/main/kotlin/io/github/whywood/superpi/ui/plugins/VendorIcons.kt` (only `feishu`, `wecom` and `micloud` are wired to existing plugins; the rest are spare).
- Tabler line icons use brand colors: GitHub / Notion are dark marks, so showing them in brand colors on a dark theme would need a white variant in `drawable-night-nodpi/` (not done; `VendorPluginIcons` currently wires only Feishu/WeCom/Xiaomi, all colored marks, so it isn't affected).

## Regeneration

Dependencies: ImageMagick 7 (`magick`), Node.js + `sharp` (libvips/librsvg, for precise SVG rasterization) and Python 3. `NODE_PATH` points at the `node_modules` that holds `sharp`:

```bash
cd assets/vendor-logos
NODE_PATH="$(npm root -g)" python3 scripts/build.py
```

The script regenerates the 512/256/192 PNGs and `android/res/drawable-nodpi/` from `upstream/` and `svg/`, copies them into `app/src/main/res/drawable-nodpi/`, rebuilds `preview.png` and refreshes the sha256 and byte sizes in `manifest.json`. Per-script responsibilities:

- `scripts/build_appstore_master.sh <white-bg|wechat> <in.jpg> <out.png>`: remove the background from an App Store image, then crop and center it into a 512 transparent master.
- `scripts/render_svg.js <in.svg> <out.png> <size> [density]`: rasterize an SVG with sharp (render at `density 2400` and downscale to avoid jaggies).
- `scripts/derive_sizes.js <master-512.png> <dir256> <dir192>`: derive 256/192 and apply palette compression.
- `scripts/build.py`: whole pipeline + manifest + preview.

Adding an icon: put the source in `svg/` or `upstream/`, add a row to `ICONS`/the source table in `build.py`, then rerun the command above.

## License and trademark notices

- Tabler Icons: MIT, full text in `licenses/LICENSE-Tabler-MIT.txt`.
- Xiaomi mark: Wikimedia Commons marks it public domain; see `licenses/NOTICE-Xiaomi-Logo.md`.
- Feishu/WeCom/WeChat: public App Store icons, not open-source artwork; trademarks belong to their respective owners. The original App Store assets are not tracked, and the derived `vendor_<id>.png` files ship with the app only to identify the corresponding services. See `licenses/NOTICE-AppStore-Artwork.md` for details.
- All other brand names and marks are trademarks of their respective owners; this asset package implies no license, partnership or affiliation.
