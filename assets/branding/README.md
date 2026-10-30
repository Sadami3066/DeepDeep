# 品牌与第三方资产

## 启动图标

- 文件：`app/src/main/res/drawable/ic_launcher_foreground.xml`
- 内容：白色小写 `d` 线性标记 + 蓝色自适应背景 `#1D4ED8`
- 来源：本项目自绘，不使用第三方角色或图片素材
- 旧版鲸鱼娘占位图标、`DeepDeepMascot` 与其 CC BY-NC-SA 许可文件已从应用和仓库移除

## 字体

| 字体 | 文件 | 许可 |
|---|---|---|
| Instrument Sans | `core/ui/src/main/res/font/instrument_sans_*.ttf` | SIL Open Font License 1.1 |
| Newsreader | `core/ui/src/main/res/font/newsreader_*.ttf` | SIL Open Font License 1.1 |

- 上游：<https://github.com/Instrument/instrument-sans>、<https://github.com/productiontype/Newsreader>
- 静态实例由官方可变字体生成，未改字形。
- 许可全文：`core/ui/src/main/assets/licenses/instrument-sans-OFL.txt`、`newsreader-OFL.txt`，随 APK 打包。

## 图标

- 素材库新增[Lucide 通用 App 微调版](../icons/lucide-app-v1/README.md)：64 个候选图标、完整来源与许可、SVG/PNG 和导出脚本。

- Lucide，ISC License。
- 固定 revision `c29c276f16590cf017126671535ad8c792be64d8`；只转换实际使用的 24×24 线性图标。
- 上游：<https://github.com/lucide-icons/lucide>
- 许可全文：`core/ui/src/main/assets/licenses/lucide-ISC.txt`，随 APK 打包。

## 未进入仓库的参考素材

`artifacts/claude-apk/` 仅保存在本地，被 Git 忽略；未复制 Claude 的字体、图标、代码、文案或图片。Claude 拆包只用于理解信息架构与输入入口。
