# README 媒体资产

README 直接使用 GitHub user-attachments 的视频链接。单独一行裸链接会被 GitHub 渲染成播放器；不要再写 `<video>` 标签，README 的 HTML sanitizer 会把它整个过滤掉。

## 目录

```text
docs/media/
├── shots/                   # 实机截图
│   ├── drawer.jpg
│   ├── home.jpg
│   ├── personalization.jpg
│   ├── providers.jpg
│   └── settings.jpg
├── hero-banner.svg          # 当前首屏；新 hero 到位后替换
├── mascot.webp
└── terminal.svg
```

截图统一为 720×1280，JPEG q86，单张 27–58KB。

## 视频

视频放在 GitHub user-attachments，README 里使用裸链接：

| 名称 | 内容 | 链接 |
|---|---|---|
| `deepdeep-monitor.mp4` | Agent 轮询场馆 | https://github.com/user-attachments/assets/148208b2-11de-4ea5-a3a0-0b0378e6df34 |
| `deepdeep-phone-control.mp4` | 悬浮窗操作其他应用 | https://github.com/user-attachments/assets/f20ce2ce-26e9-4f7c-8696-dea81d0a74e1 |
| `deepdeep-miniapp.mp4` | 生成打卡待办小程序 | https://github.com/user-attachments/assets/fd8eba76-f19e-477a-95c7-8c1d1609198d |
| `deepdeep-scan.mp4` | 扫码与跳转 | https://github.com/user-attachments/assets/0fcfc455-4a06-4f74-ba69-26f27d341b57 |
| `deepdeep-remote.mp4` | 连接电脑 DSH | https://github.com/user-attachments/assets/b712257f-c700-43c3-ac4e-6fd829c6e24a |
| `deepdeep-overlay-voice.mp4` | 悬浮语音输入 | https://github.com/user-attachments/assets/32ce705b-dd43-4677-8509-ea7ad7fcf49b |

这些是 720×1280、H.264、无音轨的压缩版，单个 0.3–2.3MB。原始录屏保留在 Release：

```text
https://github.com/Sadami3066/DeepDeep/releases/tag/demo-20261109
```

## 截图

| 文件 | 用途 |
|---|---|
| `shots/home.jpg` | UI 展示：主界面 |
| `shots/drawer.jpg` | UI 展示：抽屉 |
| `shots/providers.jpg` | UI 展示：模型服务商 |
| `shots/settings.jpg` | UI 展示：权限与运行环境 |
| `shots/personalization.jpg` | UI 展示：个性化 |
| `shots/ui-showcase.png` | UI 展示：五张截图合并为一张 |

## 重新生成

截图（Pillow）：

```bash
python3 - <<'PYCODE'
from PIL import Image
for src, dst in [
    ('主界面.jpg', 'home.jpg'),
    ('抽屉.jpg', 'drawer.jpg'),
    ('42+服务商.jpg', 'providers.jpg'),
    ('设置页.jpg', 'settings.jpg'),
    ('个性化.jpg', 'personalization.jpg'),
]:
    im = Image.open(src).convert('RGB')
    im = im.crop((0, 240, 1080, 2160)).resize((720, 1280), Image.LANCZOS)
    im.save('docs/media/shots/' + dst, quality=86, optimize=True)
PYCODE
```

视频（ffmpeg，输出 720×1280、无音轨、faststart）：

```bash
ffmpeg -i 帮我盯着.mp4 -vf scale=720:1280:flags=lanczos \
  -c:v libx264 -crf 26 -preset medium -pix_fmt yuv420p -an -movflags +faststart \
  deepdeep-monitor.mp4
```

`DeepDeep小程序.mp4` 先裁成 9:16 再缩放：

```bash
ffmpeg -i DeepDeep小程序.mp4 -vf "crop=1080:1920:0:240,scale=720:1280:flags=lanczos" \
  -c:v libx264 -crf 26 -preset medium -pix_fmt yuv420p -an -movflags +faststart \
  deepdeep-miniapp.mp4
```

上传（需要仓库写权限）：

```bash
curl -X POST \
  "https://uploads.github.com/user-attachments/assets?name=deepdeep-monitor.mp4&content_type=video/mp4&repository_id=1429875049" \
  -H "Authorization: Bearer <token>" \
  --data-binary @deepdeep-monitor.mp4
```

返回的 `url` 直接放进 README 的单元格或段落，单独一行。

## 首屏

`hero-banner.svg` 是当前占位图。新的首屏按 16:9 输出（建议 1200×675 或 1600×900），放到 `docs/media/hero.gif` 或 `docs/media/hero.png`，替换 README 引用即可。

首屏不承担功能说明；标题、定位和快速开始已经在上方，图片只需要呈现一个真实使用场景。
