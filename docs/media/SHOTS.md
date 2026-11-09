# README 素材与录制规格

这个目录给 README 里的演示位提供素材。现在放的是占位图，录好实机后按下面的命名替换即可。

## 文件约定

| 文件 | 用途 | 规格 |
|---|---|---|
| `mascot.webp` | 标题上方的小鲸鱼 | 512×512，透明背景 |
| `hero.gif` | 首屏主演示 | 1200×675（16:9），8–12 秒，循环，无声 |
| `demo-1.gif` | 「一句话订场地」 | 720×960（3:4），6–10 秒，循环，无声 |
| `demo-2.gif` | 「翻完飞书消息」 | 720×960（3:4），6–10 秒，循环，无声 |
| `demo-3.gif` | 「现场写一个小程序」 | 720×960（3:4），6–10 秒，循环，无声 |

单个 GIF 控制在 **8 MB 以内**，最多不要超过 10 MB；GitHub 对单文件有 100 MB 上限，但大 GIF 在手机上加载很慢。

## 录什么

### hero.gif — 一句话订场地

建议横向拼两路画面：左边聊天，右边虚拟屏操作。没有条件就只录聊天 + 结果卡，也能用。

| 时间 | 画面 | 旁白/字幕 |
|---|---|---|
| 0–2s | 聊天输入「帮我盯着南校园羽毛球，有空位就订，晚上最好」 | 一句话 |
| 2–4s | 过程卡出现：`tool_search` → `bash` → 查空位 | 它在自己查 |
| 4–7s | 虚拟屏预览：打开场馆页、选日期、选时段 | 后台操作，不抢主屏 |
| 7–9s | 回到聊天，交付「预订成功 #RB-…」卡片 | 订好了 |
| 9–12s | 日历里出现事件，或账户积分变化 | 顺手写进日历 |

隐私：提前把真实姓名、学号、积分、订单号遮掉；用演示账号或后期打码。

### demo-1.gif — 订场地

只录竖屏聊天：输入一句 → 工具卡滚动 → 成功卡停留 2 秒。开头加 0.5 秒空镜，结尾加 1 秒停留。

### demo-2.gif — 飞书

输入「把这周群里的决定整理成待办」→ 飞书插件工具卡 → 输出待办列表。不要露出真实群名和同事姓名。

### demo-3.gif — 小程序

输入「给我做个课程表，能导入教务」→ 过程卡 → 小程序卡片 → 打开后的课程表页面。结尾停在完整页面上。

## 录制

### 手机直接录

```bash
# USB 连接后，按屏幕实际分辨率调整
adb shell screenrecord --size 720x1280 --bit-rate 8M --time-limit 20 /sdcard/Download/demo.mp4
adb pull /sdcard/Download/demo.mp4 .
```

虚拟屏画面可以在悬浮窗里长按预览后录屏，或让 Agent 截图后拼进视频。

### 转 GIF

```bash
ffmpeg -i demo.mp4 \
  -vf "fps=24,scale=720:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 demo-1.gif
```

16:9 主演示把 `scale=720:-1` 换成 `scale=1200:-1`，并确认输出是 1200×675：

```bash
ffmpeg -i hero.mp4 -vf "fps=24,scale=1200:675:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=160[p];[s1][p]paletteuse=dither=bayer" -loop 0 hero.gif
```

压完检查大小：

```bash
ls -lh *.gif
```

超过 8 MB 就降 fps（24 → 15）、减颜色（128 → 96）或缩短时长。

## 替换 README

录好后把文件放进本目录，然后把 README 里的：

```html
<img src="docs/media/hero-placeholder.svg" width="860" alt="DeepDeep 实机演示" />
```

换成：

```html
<img src="docs/media/hero.gif" width="860" alt="DeepDeep 实机演示" />
```

三个演示位同理，把 `demo-1.svg` 换成 `demo-1.gif`，并删掉这一行：

```html
<sub>以上为演示位，实机 GIF 录制中。录制规格见 docs/media/SHOTS.md。</sub>
```

## 截图（可选）

如果不想全用 GIF，可以补三张静态图放在 `docs/media/`：

- `shot-chat.png`：一段完整对话 + 工具卡
- `shot-screen.png`：悬浮窗 + 虚拟屏预览
- `shot-miniapp.png`：一个做得比较完整的小程序

宽度统一 720，圆角由页面自己裁，不要加手机边框，避免和 GIF 风格不一致。
