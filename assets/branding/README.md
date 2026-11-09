# 品牌与第三方资产

## 应用图标

自适应图标资源在 `app/src/main/res/mipmap-*/` 与 `drawable*/`，背景色为 `#1D4ED8`。

## 字体

| 字体 | 文件 | 许可 |
|---|---|---|
| Lora | `design/src/main/res/font/lora_variable.ttf` | SIL Open Font License 1.1 |
| Manrope | `design/src/main/res/font/manrope_variable.ttf` | SIL Open Font License 1.1 |

- 上游：<https://github.com/google/fonts/tree/main/ofl/lora>、<https://github.com/google/fonts/tree/main/ofl/manrope>
- Lora 与 Manrope 与上游文件字节一致，未修改；中文使用系统字体，不随包分发 CJK 字体。
- 许可全文：`design/src/main/assets/licenses/lora-OFL.txt`、`design/src/main/assets/licenses/manrope-OFL.txt`，随 APK 打包。

## 图标

- 应用内通用 UI 图标来自 [Milo Modern 图标素材包](../milo-modern-icons-200-revised/README.md)（App 取 regular 档），分类源、命名与生成流程见 `design/icons/`。
- 无 Milo 对应语义的少数线稿为手绘 vector（源自 [Tabler Icons](https://github.com/tabler/tabler-icons)，MIT，快照 v3.50.0）；许可全文 `design/src/main/assets/licenses/tabler-MIT.txt`，随 APK 打包。
- 厂商/服务图标素材见 `assets/pi-provider-logos/` 与 `assets/vendor-logos/`，来源与许可见各自 README。

## 许可

第三方组件的完整清单与许可见仓库根目录 [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)。
