# 鲸鱼娘 UI 套件 · 整板 v1

2026-10-22。按用户要求，**只调用一次内置 image_gen 绘制整板，再用 Python 脚本切出 16 张透明 PNG**。沿用系列2的鲸鱼娘设定，蓝白、粗描边、小表情；本批是候选视觉素材，尚未接入应用。

![切分后的套件](preview.png)

## 文件

- [atlas.png](atlas.png)：生成器原始整板，1254 × 1254 RGBA，未覆盖或修改。
- [pieces/](pieces/)：16 张清理后的透明 PNG，文件名可直接用作 Android drawable 名称。
- [pieces/manifest.json](pieces/manifest.json)：原图摘要、切图配置、每张的来源矩形、尺寸和 SHA-256。
- [layout.json](layout.json)：4×4 分区、实际行边界与透明度/留白设置。
- [切图脚本](../../../scripts/split_ui_atlas.py)：Pillow + Python 标准库，无网络请求或模型调用。
- [PROMPT.md](PROMPT.md)：整板的准确提示词、输入图顺序和生成方式。

## 内容

| 行 | 文件 | 用途 |
| --- | --- | --- |
| 1 | `mascot_idle`、`mascot_wave`、`mascot_thinking`、`mascot_sleepy` | 待命、欢迎、思考、休息表情 |
| 2 | `icon_whale`、`icon_chat`、`icon_microphone`、`icon_settings` | 鲸鱼、聊天、语音、设置图标候选 |
| 3 | `button_primary`、`button_secondary`、`chip_selected`、`chip_idle` | 无字按钮与标签底板 |
| 4 | `card_panel`、`bubble_assistant`、`divider_wave`、`accent_bow` | 卡片、聊天气泡、波浪分隔线、蝴蝶结装饰 |

## 重复切分

需要 Python 3 和 Pillow（当前机器已具备）。在项目根目录执行，输出目录可自行指定：

```bash
python3 scripts/split_ui_atlas.py \
  assets/ui-kit/whale-flat-v1/atlas.png \
  --layout assets/ui-kit/whale-flat-v1/layout.json \
  --output /tmp/deepdeep-ui-kit
```

默认拒绝覆盖同名文件；确需重新导出到已有目录时加 `--overwrite`。保留原始整板，以后更换命名、留白或导出目录均不需要再次生成图片。

生成图的行距没有严格等分，本批实际行边界为 `0 / 0.39 / 0.63 / 0.77 / 1`。脚本按透明通道找连通形状，再根据重心分配到网格，整块提取；即使发饰跨过分区线也不会被生硬截断。透明度较低的游离杂点会被排除，边缘扩展 2 px 保留原始抗锯齿，成品周围加 12 px 透明留白。

第一排设置 `largest_only`，只留人物主体，去掉生成器额外绘制的问号、Z 和招手短线；其他行保留同格的多个形状，如鲸鱼水花和分隔线。需要新板子时修改行列、名称和分区边界；形状应分离留白，若两格图形实质相连需先调整来源。

## 前端使用

- 图片负责视觉；按钮文字、点击区域、选中状态、读屏描述由 Compose 实现。底板保持无字，方便本地化。
- 本批原图共用一张画布，切图宽约 216–349 px；适合图标、表情和小组件皮肤。大幅插画需按目标显示尺寸另出更高分辨率素材。
- 按钮和卡片可作为风格稿或固定比例图片。需要自由拉伸时，按实际布局制作 Nine-patch 或用 Compose 重建轮廓；本批未声明为可任意拉伸的组件。
- 16 张图已检查独立轮廓和透明背景；动态状态对齐、暗色主题对比及真机观感在页面采用时验收。

## 来源与验证

参考图位于 [references/](references/)，从已恢复 Git 的 `origin/codex/mascot-assets` 读取。风格原图由用户提供，另一张为此前生成的系列2基准头像；原作者/许可尚未提供，不另行授予许可证。

验证记录：单次生成得到 16 个目标元素；脚本成功导出 16 张 PNG 和来源清单；核对尺寸、SHA-256、透明边缘与原图色值；覆盖保护、跨格完整提取和不透明输入拒绝已检查。原始整板保持逐字节不变。本轮不涉及 Android 构建或手机操作。
