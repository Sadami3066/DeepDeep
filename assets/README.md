# 资产目录

- [鲸鱼娘 UI 整板套件](ui-kit/whale-flat-v1/README.md)：保留整板与四张吉祥物（wave/idle/thinking/sleepy），其余切图已移出。

- [Milo Modern 图标素材包](milo-modern-icons-200-revised/README.md)：200 个图形 × regular/semibold 两档线重；App 使用 semibold 档，实际用到的图标整理在 [图标分类源](../design/icons/README.md)，由生成脚本导出到 `design/src/main/res/drawable-nodpi/`。
- [图标分类源](../design/icons/README.md)：应用内通用 UI 图标的单源目录（分类 + `catalog.json`）、生成脚本与分类预览页；运行时资源由它生成。

- [品牌、字体与第三方许可](branding/README.md)：当前随 APK 发布的图标与字体。
- [Pi provider 图标素材包](pi-provider-logos/README.md)：42 个 provider 图标（31 份不同品牌图形），含来源、许可、SHA256 清单与可复现脚本。
- [厂商图标素材包](vendor-logos/README.md)：10 个厂商/服务品牌图标，含来源、许可与再生成脚本。

原图与候选素材保留在这里；前端选定后再把实际使用的导出文件加入所属模块，避免整库打包。保留来源与许可记录，候选素材不自动取得其他资产的许可。

光栅图（`*.png`、`*.jpg`、`*.webp`）按 `.gitignore` 只保留在本地，不入库；入库的是各目录的来源说明、清单、许可文本与再生成脚本。
