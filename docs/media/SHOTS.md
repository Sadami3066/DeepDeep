# README 素材与录制规格

README 里有一个 16:9 首屏、一个 Android 分区的竖向演示位，以及一张 Linux 终端示意图。

## 文件约定

| 文件 | 位置 | 规格 |
|---|---|---|
| `hero.gif` | 首屏横幅 | 1200×675（16:9），8–12 秒，循环，无声 |
| `phone.gif` | 「它住得够深」右侧 | 720×1280（9:16），6–10 秒，循环，无声 |

`terminal.svg` 是静态示意图，不需要录；想换成实机终端截图也可以，保持 16:9。

单个 GIF 控制在 **8 MB 以内**，最多不超过 10 MB。首屏显示宽度约 880px，1200 宽足够。

## 录什么

### hero.gif — 首屏

最好用「盯场地」这条，因为结果最直观：

| 时间 | 画面 |
|---|---|
| 0–2s | 输入「盯南校园羽毛球，有空位就订，晚上优先」 |
| 2–4s | 过程卡出现：查空位 → 选场地 → 下单 |
| 4–7s | 悬浮窗里虚拟屏在操作场馆页（可选） |
| 7–9s | 「预订成功」卡片，露出时间、场地、积分变化 |
| 9–12s | 日历事件出现，或回到聊天停留 1 秒 |

隐私：真实姓名、学号、积分、订单号提前遮掉，或用演示账号。

### phone.gif — Android 分区

这一段展示「它真的在手机里」，建议录悬浮窗 + 虚拟屏：

| 时间 | 画面 |
|---|---|
| 0–2s | 聊天里交代任务，悬浮小鲸鱼出现 |
| 2–5s | 长按小鲸鱼，虚拟屏预览展开，App 在后台被操作 |
| 5–7s | 回到聊天，进度更新 |
| 7–10s | 任务完成，交付卡片出现 |

如果不好录虚拟屏，也可以录「系统助手里交代任务 → 悬浮窗显示进度」的完整过程。

## 录制

### 手机直接录

```bash
adb shell screenrecord --size 720x1280 --bit-rate 8M --time-limit 20 /sdcard/Download/demo.mp4
adb pull /sdcard/Download/demo.mp4 .
```

### 转 GIF

首屏（16:9）：

```bash
ffmpeg -i hero.mp4 \
  -vf "fps=24,scale=1200:675:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=160[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 hero.gif
```

竖向（9:16）：

```bash
ffmpeg -i phone.mp4 \
  -vf "fps=24,scale=720:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 phone.gif
```

压完检查大小：

```bash
ls -lh *.gif
```

超过 8 MB 就降 fps（24 → 15）、减颜色（128 → 96）或缩短时长。

## 替换 README

1. 首屏：把 `hero-banner.svg` 换成 `hero.gif`，宽度保持 880。

```html
<img src="docs/media/hero.gif" width="880" alt="DeepDeep 实机演示" />
```

2. Android 分区：把 `phone-hero.svg` 换成 `phone.gif`，宽度保持 300。

3. 首屏下面的「实机演示位」说明可以删掉。

## 截图（可选）

如果不想全用 GIF，可以补静态图：

- `shot-chat.png`：完整对话与工具卡
- `shot-screen.png`：悬浮窗 + 虚拟屏预览
- `shot-miniapp.png`：一个做得比较完整的小程序

宽度统一 720，不要加手机边框，避免和现有矢量手机壳风格冲突。
