# README 媒体资产

README 直接使用 GitHub user-attachments 的视频链接。单独一行裸链接会被 GitHub 渲染成播放器；不要写 `<video>` 标签，README 的 HTML sanitizer 会把它整个过滤掉。

中文版使用 `README.md` 与 `hero-zh.webp`；英文版使用 `README.en.md` 与 `hero-en.webp`。

## 目录

```text
docs/media/
├── shots/
│   ├── drawer.jpg
│   ├── home.jpg
│   ├── personalization.jpg
│   ├── providers.jpg
│   ├── settings.jpg
│   └── ui-showcase.png
├── hero-en.webp
├── hero-zh.webp
└── mascot.webp
```

单张截图为 720×1280，JPEG q86，27–58KB。`ui-showcase.png` 是五张截图合并后的展示图，1920×680，约 310KB。

## 视频

视频放在 GitHub user-attachments，README 里使用裸链接：

| 名称 | 内容 | 链接 |
|---|---|---|
| `monitor.mp4` | Agent 轮询场馆 | https://github.com/user-attachments/assets/bfeaac5e-da31-4790-a9e3-3f7e9a6b2018 |
| `phone-control.mp4` | 悬浮窗操作其他应用 | https://github.com/user-attachments/assets/32019883-ca2d-40f6-b18f-6c941131cc64 |
| `miniapp.mp4` | 生成打卡待办小程序 | https://github.com/user-attachments/assets/9deb4845-514d-4c42-a197-0fa80f2cd9f4 |
| `scan.mp4` | 扫码与跳转 | https://github.com/user-attachments/assets/d387b523-4283-4269-9302-63f135e3541c |
| `remote.mp4` | 连接电脑 DSH | https://github.com/user-attachments/assets/11c0d18c-192d-4e15-98bf-c6fb95498fae |
| `overlay-voice.mp4` | 悬浮语音输入 | https://github.com/user-attachments/assets/99a8fa59-0e0b-404d-a56f-f00015c5647d |

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
  monitor.mp4
```

`DeepDeep小程序.mp4` 先裁成 9:16 再缩放：

```bash
ffmpeg -i DeepDeep小程序.mp4 -vf "crop=1080:1920:0:240,scale=720:1280:flags=lanczos" \
  -c:v libx264 -crf 26 -preset medium -pix_fmt yuv420p -an -movflags +faststart \
  miniapp.mp4
```

上传（需要仓库写权限）：

```bash
curl -X POST \
  "https://uploads.github.com/user-attachments/assets?name=monitor.mp4&content_type=video/mp4&repository_id=1429875049" \
  -H "Authorization: Bearer <token>" \
  --data-binary @monitor.mp4
```

返回的 `url` 直接放进 README 的单元格或段落，单独一行。

## Hero

`hero-zh.webp` 与 `hero-en.webp` 都是 1881×836，WebP q90。它们分别服务中文和英文 README；替换时保持尺寸一致即可。

Hero 不承担功能说明；标题、定位和快速开始已经在上方，图片只需要呈现一个真实使用场景。
