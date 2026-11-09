# README 素材与录制规格

README 里现在是 **1 个竖向首屏 + 3 个时间线小窗**。录好实机后按下面的命名替换即可。

## 文件约定

| 文件 | 位置 | 规格 |
|---|---|---|
| `hero.gif` | 首屏左侧手机 | 720×1280（9:16），8–12 秒，循环，无声 |
| `slot-1.gif` | 07:50 课表 | 720×960（3:4），6–8 秒，循环，无声 |
| `slot-2.gif` | 12:10 飞书 | 720×960（3:4），8–10 秒，循环，无声 |
| `slot-3.gif` | 21:00 场地 | 720×960（3:4），8–12 秒，循环，无声 |

单个 GIF 控制在 **8 MB 以内**，最多不超过 10 MB。首屏手机显示宽度为 360px，不需要录 4K；720 宽足够。

## 录什么

### hero.gif — 一次完整的「交代」

建议录「盯场地」这条，因为结果最直观：

| 时间 | 画面 |
|---|---|
| 0–2s | 输入「盯南校园羽毛球，有空位就订，晚上优先」 |
| 2–4s | 过程卡出现：查空位 → 选场地 → 下单 |
| 4–7s | 悬浮窗里虚拟屏在操作场馆页（可选，没有就只录聊天） |
| 7–9s | 「预订成功」卡片，露出时间、场地、积分变化 |
| 9–12s | 日历事件出现，或回到聊天停留 1 秒 |

隐私：真实姓名、学号、积分、订单号提前遮掉，或用演示账号。

### slot-1.gif — 07:50 课表

输入「今天几节课？」→ 教务 CLI 工具卡 → 输出「3 门课，8:00 高等数学…」。结尾停在结果上 1 秒。

### slot-2.gif — 12:10 飞书

输入「群里这周定了什么？」→ 飞书插件工具卡 → 输出 3–4 条决定。不要露出真实群名、同事姓名和头像。

### slot-3.gif — 21:00 场地

输入「盯南校园羽毛球，有空位就订」→ 轮询过程 → 成功卡片。和 hero 用同一条也可以，hero 取完整版，slot 取 6 秒精华。

## 录制

### 手机直接录

```bash
adb shell screenrecord --size 720x1280 --bit-rate 8M --time-limit 20 /sdcard/Download/demo.mp4
adb pull /sdcard/Download/demo.mp4 .
```

### 转 GIF

竖屏首屏：

```bash
ffmpeg -i hero.mp4 \
  -vf "fps=24,scale=720:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 hero.gif
```

3:4 小窗（录的是竖屏时，先裁成 3:4）：

```bash
ffmpeg -i slot.mp4 \
  -vf "crop=ih*3/4:ih,fps=24,scale=720:960:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 slot-1.gif
```

压完检查大小：

```bash
ls -lh *.gif
```

超过 8 MB 就降 fps（24 → 15）、减颜色（128 → 96）或缩短时长。

## 替换 README

录好后把文件放进本目录，然后：

1. 首屏：把 `phone-hero.svg` 换成 `hero.gif`，宽度保持 360。

```html
<img src="docs/media/hero.gif" width="360" alt="DeepDeep 在手机上完成一次订场" />
```

2. 时间线：把 `slot-1.svg` / `slot-2.svg` / `slot-3.svg` 换成同名 `.gif`，宽度保持 120。

3. 删掉首屏下面的「实机演示录制中」和时间线下面的「画面为 GIF 占位」。

## 截图（可选）

如果不想全用 GIF，可以补静态图：

- `shot-chat.png`：完整对话与工具卡
- `shot-screen.png`：悬浮窗 + 虚拟屏预览
- `shot-miniapp.png`：一个做得比较完整的小程序

宽度统一 720，不要加手机边框，避免和首屏的矢量手机壳风格冲突。
