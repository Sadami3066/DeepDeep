# 品牌与第三方资产

## 启动图标

- 文件：`app/src/main/res/drawable/ic_launcher_foreground.xml`
- 内容：白色小写 `d` 线性标记 + 蓝色自适应背景 `#1D4ED8`
- 来源：本项目自绘，不使用第三方角色或图片素材
- 旧版鲸鱼娘占位图标、`DeepDeepMascot` 与其 CC BY-NC-SA 许可文件已从应用和仓库移除

## 字体

| 字体 | 文件 | 许可 |
|---|---|---|
| Lora | `design/src/main/res/font/lora_variable.ttf` | SIL Open Font License 1.1 |
| Manrope | `design/src/main/res/font/manrope_variable.ttf` | SIL Open Font License 1.1 |

- 上游：<https://github.com/google/fonts/tree/main/ofl/lora>、<https://github.com/google/fonts/tree/main/ofl/manrope>
- Lora 与 Manrope 与上游文件字节一致，未修改；中文使用系统字体，不随包分发 CJK 字体。
- 许可全文：`design/src/main/assets/licenses/lora-OFL.txt`、`design/src/main/assets/licenses/manrope-OFL.txt`，随 APK 打包。

## 图标

- 应用内通用 UI 图标上游为 [Milo Modern 图标素材包](../milo-modern-icons-200-revised/README.md)（App 取 regular 档），分类源、命名与生成流程见 `design/icons/`，生成到 `design/src/main/res/drawable-nodpi/`。
- 无 Milo 对应语义的少数线稿为手绘 vector（源自 [Tabler Icons](https://github.com/tabler/tabler-icons)，MIT，快照 v3.50.0）；许可全文 `design/src/main/assets/licenses/tabler-MIT.txt`，随 APK 打包。
- 厂商/服务图标素材见 `assets/pi-provider-logos/` 与 `assets/vendor-logos/`，来源、许可与替换说明见各自 README 与仓库根目录 `THIRD_PARTY_NOTICES.md`。

## 未进入仓库的参考素材

`artifacts/` 仅保存在本地，被 Git 忽略；未复制 Claude 的字体、图标、代码、文案或图片。Claude 拆包只用于理解信息架构与输入入口。
