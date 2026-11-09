# README 媒体资产

README 使用两类媒体：仓库内的压缩预览，以及 GitHub Release 中的完整视频。仓库只提交预览与截图，不提交原始录屏。

## 目录

```text
docs/media/
├── demo/                    # README 内联 GIF
│   ├── agent-monitor.gif
│   ├── miniapp.gif
│   ├── phone-operation.gif
│   ├── remote.gif
│   └── scan.gif
├── shots/                   # 实机截图与静态封面
│   ├── drawer.jpg
│   ├── home.jpg
│   ├── monitor.jpg
│   ├── personalization.jpg
│   ├── providers.jpg
│   └── settings.jpg
├── hero-banner.svg          # 当前首屏；新 hero 到位后替换
├── mascot.webp
└── terminal.svg
```

完整视频：

```text
https://github.com/Sadami3066/DeepDeep/releases/tag/demo-20261109
```

## 预览文件

| 文件 | 来源 | 片段 | 规格 | 用途 |
|---|---|---|---|---|
| `demo/agent-monitor.gif` | `帮我盯着.mp4` | 0–12s | 360 宽，8fps，96 色，623KB | Agent 运行时 |
| `demo/phone-operation.gif` | `手机操作.mp4` | 0–38s | 360 宽，6fps，80 色，1.5MB | Android 系统集成 |
| `demo/miniapp.gif` | `DeepDeep小程序.mp4` | 0–12s | 360 宽，8fps，96 色，901KB | 小程序 |
| `demo/scan.gif` | `统一扫一扫.mp4` | 0–12s | 360 宽，6fps，64 色，1.9MB | 万能扫一扫 |
| `demo/remote.gif` | `远程连接.mp4` | 0–14s | 360 宽，8fps，96 色，522KB | 远程连接 |
| `shots/home.jpg` | `主界面.jpg` | — | 720×1600，48KB | 预览 |
| `shots/drawer.jpg` | `抽屉.jpg` | — | 720×1600，48KB | 预览、市场 |
| `shots/providers.jpg` | `42+服务商.jpg` | — | 720×1600，74KB | 预览 |
| `shots/settings.jpg` | `设置页.jpg` | — | 720×1600，59KB | 权限与运行环境 |
| `shots/personalization.jpg` | `个性化.jpg` | — | 720×1600，59KB | 记忆 |
| `shots/monitor.jpg` | `帮我盯着.mp4` 22.5s | — | 720×1600，55KB | 帮我盯卡片 |

截图统一缩放到 720×1600，JPEG q86。GIF 保留手机原始比例，不裁状态栏。

## 完整视频

Release 资产使用英文文件名：

| 资产 | 原文件 |
|---|---|
| `deepdeep-monitor.mp4` | `帮我盯着.mp4` |
| `deepdeep-phone-control.mp4` | `手机操作.mp4` |
| `deepdeep-miniapp.mp4` | `DeepDeep小程序.mp4` |
| `deepdeep-overlay-voice.mp4` | `浮层浮窗主界面三态语音输入交互.mp4` |
| `deepdeep-scan.mp4` | `统一扫一扫.mp4` |
| `deepdeep-remote.mp4` | `远程连接.mp4` |

README 中的 GIF 点击后指向对应 Release 资产。

## 重新生成

截图（需要 Pillow）：

```bash
python3 - <<'EOF'
from PIL import Image
import glob, os
for p in glob.glob('UI_screenshot/*.jpg'):
    im = Image.open(p).convert('RGB')
    im.thumbnail((720, 1600), Image.LANCZOS)
    im.save('docs/media/shots/' + os.path.splitext(os.path.basename(p))[0] + '.jpg', quality=86, optimize=True)
EOF
```

GIF（需要 ffmpeg）：

```bash
ffmpeg -ss 0 -t 12 -i 帮我盯着.mp4 \
  -vf "fps=8,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=96[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 docs/media/demo/agent-monitor.gif

ffmpeg -ss 0 -t 38 -i 手机操作.mp4 \
  -vf "fps=6,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=80[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 docs/media/demo/phone-operation.gif

ffmpeg -ss 0 -t 12 -i DeepDeep小程序.mp4 \
  -vf "fps=8,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=96[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 docs/media/demo/miniapp.gif

ffmpeg -ss 0 -t 12 -i 统一扫一扫.mp4 \
  -vf "fps=6,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=64[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 docs/media/demo/scan.gif

ffmpeg -ss 0 -t 14 -i 远程连接.mp4 \
  -vf "fps=8,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=96[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 docs/media/demo/remote.gif
```

## 首屏

`hero-banner.svg` 是当前占位图。新的首屏准备好后，按 16:9 输出（建议 1200×675 或 1600×900），放到 `docs/media/hero.gif` 或 `docs/media/hero.png`，替换 README 中的引用即可。

首屏不承担功能说明；标题、定位和快速开始已经在上方，图片只需要呈现一个真实使用场景。
