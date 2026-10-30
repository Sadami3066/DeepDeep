# DeepDeep 厂商图标素材包

首批 10 个厂商/服务的品牌图标，供插件列表、厂商识别和设置页复用。全部为透明 PNG，按 `vendor_<id>` 命名；本目录与 `assets/` 其它素材一样**不入库**（未跟踪目录），真正随 App 打包的只有 `app/src/main/res/drawable-nodpi/vendor_<id>.png`。

## 目录结构

```
assets/vendor-logos/
├── manifest.json                 # 图标清单：来源 URL、许可、尺寸、sha256
├── preview.png                   # 10 个图标的浅色背景总览
├── svg/                          # 可编辑矢量源（7 个：Tabler 6 + 小米）
├── upstream/                     # App Store 原始 JPEG（3 个，便于复核/替换）
├── png/512/<id>.png              # 512×512 透明 PNG
├── png/256/<id>.png              # 256×256 透明 PNG
├── png/192/<id>.png              # 192×192 透明 PNG
├── android/res/drawable-nodpi/   # 可整体复制进 Android 工程的 vendor_<id>.png（192）
├── scripts/                      # 可复现的构建脚本（见下）
└── licenses/                     # MIT / 公有领域 / App Store 素材说明
```

## 图标清单

| id | 名称 | 图形来源 | 许可 |
|---|---|---|---|
| `feishu` | 飞书 Feishu | Apple App Store 图标（`upstream/feishu-appstore-512.jpg`） | App Store 公开图标，商标归字节跳动 |
| `wecom` | 企业微信 WeCom | Apple App Store 图标 | App Store 公开图标，商标归腾讯 |
| `wechat` | 微信 WeChat | Apple App Store 图标（绿色底抠除，标志重着色 `#07C160`） | App Store 公开图标，商标归腾讯 |
| `xiaomi` | 小米 Xiaomi | Wikimedia Commons `Xiaomi_logo.svg`（源自小米官方媒体素材包） | Public domain（低于独创性门槛），商标归小米 |
| `dingtalk` | 钉钉 DingTalk | Tabler Icons `brand-dingtalk` | MIT，颜色 `#0089FF` |
| `notion` | Notion | Tabler Icons `brand-notion` | MIT，颜色 `#000000` |
| `github` | GitHub | Tabler Icons `brand-github` | MIT，颜色 `#181717` |
| `slack` | Slack | Tabler Icons `brand-slack` | MIT，颜色 `#4A154B` |
| `telegram` | Telegram | Tabler Icons `brand-telegram` | MIT，颜色 `#26A5E4` |
| `discord` | Discord | Tabler Icons `brand-discord` | MIT，颜色 `#5865F2` |

Tabler 快照：`tabler-icons` v3.50.0，commit `ed177a085b652fe04ec4f37ced867c94621e7840`；源文件在 `icons/outline/`，只把 `stroke="currentColor"` 替换为品牌色。每个图标的精确来源 URL、sha256、字节数见 `manifest.json`。

## 规格

- 所有 PNG 背景透明、正方形、内容居中；App Store 图标带 4% 透明留白，Tabler 线稿保持原始 `24×24` viewBox 比例。
- `png/192/<id>.png` 即入库资源，单张实测最大 9.6 KB（上限 30 KB）。
- Android 资源名只允许小写字母/数字/下划线：`vendor_<id>.png`，与插件 id 一一对应（`micloud` 插件复用 `vendor_xiaomi`）。
- 已入库：`app/src/main/res/drawable-nodpi/` 下 10 张；Kotlin 映射见
  `app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/VendorIcons.kt`（当前只接 `feishu`、`wecom`、`micloud` 三个已有插件，其余图标留作备用）。
- Tabler 线稿按品牌色着色：GitHub / Notion 为深色图形，若将来在深色主题下以原色直接展示，需要另补 `drawable-night-nodpi/` 白色变体（本次未做；当前 `VendorPluginIcons` 只接飞书/企微/小米，均是彩色图形，不受影响）。

## 再生成

依赖：ImageMagick 7（`magick`）、Node.js + `sharp`（libvips/librsvg，用于 SVG 精确栅格化）、Python 3。本机 `sharp` 可复用 DSH 自带副本：

```bash
cd assets/vendor-logos
NODE_PATH=/home/whywood/.nvm/versions/node/v24.14.0/lib/node_modules/@deepseek-ai/dsh/node_modules \
  python3 scripts/build.py
```

脚本会：从 `upstream/` 与 `svg/` 重新生成 512/256/192 PNG 与 `android/res/drawable-nodpi/`，复制到 `app/src/main/res/drawable-nodpi/`，重建 `preview.png`，并刷新 `manifest.json` 里的 sha256/字节数。单个脚本职责：

- `scripts/build_appstore_master.sh <white-bg|wechat> <in.jpg> <out.png>`：App Store 原图去底/裁剪/居中成 512 透明母版。
- `scripts/render_svg.js <in.svg> <out.png> <size> [density]`：用 sharp 栅格化 SVG（`density 2400` 超采样后缩放，避免锯齿）。
- `scripts/derive_sizes.js <master-512.png> <dir256> <dir192>`：派生 256/192 并做调色板压缩。
- `scripts/build.py`：全流程编排 + manifest + preview。

新增图标：把源文件放进 `svg/` 或 `upstream/`，在 `build.py` 的 `ICONS`/来源表里加一行，然后重跑上面的命令。

## 许可与品牌声明

- Tabler Icons：MIT，完整文本见 `licenses/LICENSE-Tabler-MIT.txt`。
- 小米标志：Wikimedia Commons 标注 Public domain，说明见 `licenses/NOTICE-Xiaomi-Logo.md`。
- 飞书/企业微信/微信：App Store 公开图标，不是开源素材，商标归各自权利人；只用于 App 内识别对应服务，不随仓库分发。详见 `licenses/NOTICE-AppStore-Artwork.md`。
- 其余品牌名称与图形均为其各自权利人的商标；本素材包不表示任何授权、合作或隶属关系。
