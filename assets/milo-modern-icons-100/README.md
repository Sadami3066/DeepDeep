# Milo Modern Icons — 100

100 个不同功能的现代圆润单色图标，按功能英文命名。

- `icons/01_core/`：基础功能 25 个。
- `icons/02_navigation_chat/`：导航与聊天 25 个。
- `icons/03_memory_files/`：记忆与文件 25 个。
- `icons/04_automation_device/`：自动化与设备 25 个。
- `sheets/`：四张原始生成总览图。
- `preview_100.png`：全部图标预览与编号。
- `icon_names.csv`、`icon_names.json`：中英文名称、文件名、原图位置对照。

## 素材规格

独立图标为 256×256 RGBA 透明背景 PNG。轮廓统一为蓝灰色 #303F50，图形置中，最长边最多 176px。素材从生成总图按 5×5 网格裁切，清理背景并统一颜色；属于位图素材。

## Android / Compose

文件名使用 Android 资源兼容的英文小写与下划线，例如 `ic_milo_new_chat.png`。可放到 `app/src/main/res/drawable-nodpi/`，用 `painterResource()` 加载。PNG 含透明留白，若期望可见轮廓约 24dp，外部图片容器可设为约 35dp。单色图标可用 `ColorFilter.tint()` 调整选中和深色模式颜色。

03 组最后一枚按实际生成图形命名为 `bar_chart`（数据统计）。
