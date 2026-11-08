# design · 主题与通用组件

DeepDeep 的视觉底座：明暗配色、字体与字号、动效 token、通用 Compose 组件和图标资源。只被界面层使用，不含业务逻辑；同时作为插件 UI 的 SDK 与 `plugin-api` 同版本发布（`io.github.whywood.deepdeep:design`），让插件页面与 App 共用同一套组件与主题。

## 文件

| 文件 | 职责 |
|---|---|
| `DeepDeepTheme.kt` | 主题入口：配色、字体档、字号与圆角 |
| `DdCompositeFont.kt` | 拉丁字体拼系统中文字体兜底，缓存字体文件 |
| `DdComponents.kt` | 按压态、按钮、列表行、Chip、底部 Sheet 等基础件 |
| `DdControls.kt` | 开关与泛型分段选择器，带滑动指示块 |
| `DdDialog.kt` | 居中弹窗卡片，含标题与底部操作行 |
| `DdDropdown.kt` | 跟随锚点宽度的浮层，页面转后台即收起 |
| `DdElevation.kt` | 阴影档 token（Indicator/Soft/Composer/Overlay/Menu 等） |
| `DdEmptyState.kt` | 空状态提示：旧单行灰字 + 新 icon/action 版 |
| `DdGroupCard.kt` | 圆角分组卡片；旧用法 + 新 header/footer slot 版 |
| `DdIconBox.kt` | 40dp 圆底图标容器（列表行/市场行通用） |
| `DdIcon.kt` | 图标类型与 `DdIcon()` composable，统一 tint 与尺寸 |
| `DdIcons.kt` | 图标映射表（由 `design/icons/gen.mjs` 从 `catalog.json` 生成，勿手改） |
| `DdLoadingBlock.kt` | 转圈加说明文字的加载占位 |
| `DdMenu.kt` | 下拉菜单与菜单项（图标、选中、危险色） |
| `DdMotion.kt` | 动效 token：时长、缓动曲线与弹簧 |
| `DdAvatar.kt` | 头像：图片/首字母/占位图标，可点击 |
| `DdPageScaffold.kt` | 整页脚手架：纸色底、状态栏与键盘留白 |
| `DdSearchField.kt` | 搜索框：放大镜、占位、清空与 Esc；新 onSearch/trailing 版 |
| `DdSegmented.kt` | 字符串分段选择器 facade（选中块滑动） |
| `DdShapes.kt` | 圆角 token：`DdRadius` 档位与 `DdShapes` 常用形状 |
| `DdSpacing.kt` | 间距 token：0/2/4/8/12/16/18/20/24/32/40 与语义间距 |
| `DdSpinner.kt` | 旋转弧线加载圈 |
| `DdText.kt` | 文本薄封装：DdType + 颜色/maxLines/overflow/textAlign |
| `DdTextField.kt` | 单行输入框与多行文本域，聚焦变色 |
| `DdTheme.kt` | 主题变体：`DdTheme.lightSantorini()`（浅色）与 `DdTheme.darkOceanic()`（深色） |
| `DdTypingDots.kt` | 三点错峰呼吸的正在输入指示 |
| `DdAlpha.kt` | 透明度 token（disabled/hairline/muted/press/ripple 等） |
| `DdTone.kt` | 语义色调枚举与颜色映射（Neutral/Accent/Success/Warning/Danger） |
| `DdSectionHeader.kt` | 分组标题 + 可选副标题与右侧动作 |
| `DdNotice.kt` | 醒目提示块：标题/图标/色调/动作 |
| `DdInlineMessage.kt` | 行内提示：色调/图标/动作 |
| `DdBadge.kt` | 胶囊标签：色调 + 12dp 图标 |
| `DdStatRow.kt` | 标签-值行：可选说明与行尾内容 |
| `DdSettingRow.kt` | 设置行：标题/副标题/控件/点击/chevron，≥56dp |
| `DdSkeleton.kt` | 骨架占位：行/多行/卡片 |
| `DdProgressBar.kt` | 进度条：确定/不确定态，带无障碍语义 |
| `DdSheetHeaderExt.kt` | 新重载 Sheet 头：leadingIcon/subtitle/actions，旧 2 参签名保留 |
| `icons/` | 图标单源目录：`catalog.json`、生成脚本与分类预览页（运行时资源由它生成） |

## 要点

- 颜色从 `deepDeepColors()` 取，字号走 `DdType`，动效时长走 `DdMotion`，间距/圆角/阴影/透明度走 `DdSpacing`/`DdShapes`/`DdElevation`/`DdAlpha`；不硬编码 dp/sp、色值与时长。
- 组件只用 Compose 与本模块资源，不依赖 `:app`/`:runtime`；跨模块复用的控件先落到这里。
- 图标：单源在 `design/icons/`（7 类分类目录 + `catalog.json` 登记），`node design/icons/gen.mjs` 生成 `res/drawable-nodpi/ic_<category>_<id>.png|xml` 与 `DdIcons.kt`，分类预览 `design/icons/preview.html`；位图取自 `assets/milo-modern-icons-200-revised/` 的 **semibold** 档（256 画布、线宽约 2.15dp、`#273140` 线稿、图形约占画布 85%），手写矢量 24 viewport、2.1dp 圆头描边，两种都由 `DdIcon` 统一 tint；运行时资源是生成物，只改 `design/icons/` 再重跑生成。
- 圆角是 8/12/14/16/20/24/28dp 体系，可点区域按 44dp；改视觉先改 token 再改调用点。
- 边距与按压反馈：可点组件的内容与按压/波纹边缘之间至少留 `DdSpacing.Md`（12dp）横向内边距，文字与图标不许贴边（`DdTextAction` 已按此修正）；按压反馈只覆盖组件可视形状，不许溢出到相邻内容；所有可点区域 ≥ 44dp。
- 主题预设：`ThemePreset { DEEP_DEEP, SANTORINI, OCEANIC }`，入口是 `DeepDeepTheme(mode, preset, fontStyle)`；Santorini 固定浅色、Oceanic 固定深色、DeepDeep 跟随 Color mode；默认跟随系统（浅色落 Santorini、深色落 Oceanic）。新增主题只需在 `DdTheme` 加颜色函数并在 `ThemePreset` 登记，设置页会自动出现选项。
- 不用「图标容器」：列表行、空状态、展示页等位置图标直接以线性稿呈现，不加圆角方框或圆圈底色；旧 `DdIconBox` 仅为插件兼容保留，新界面不再使用。
- 设计系统 Phase 0 新增 token 与 6 个组件（list row / group card slot / 泛型 segmented / search field / empty state / avatar），均以「新增重载 + 旧签名保留」方式扩展，避免破坏插件 ABI；圣托里尼日光主题（近白底 #F4F6F8、纯白组件 #FFFFFF、发丝线 #E4ECF5、#0078D7 主蓝、#E3F2FD 浅天蓝、#1E3A47 深青灰）由 `DdTheme.lightSantorini()` 提供，深色镜像 `DdTheme.darkOceanic()`（#1B2B34 深海青、#6699CC 极光青、#FAC863 星群黄、#EC5F67 落霞红）提供夜间方案；默认主题仍走 `deepDeepColors()`。
- debug 展示页在 App 侧 `ui/settings/DesignSystemScreen.kt`：Settings → 关于 → 设计系统（仅 `BuildConfig.DEBUG`），用圣托里尼主题展示 tokens 与组件状态矩阵，供视觉验收与后续逐页迁移参考。
- 本模块无单测，改组件以编译通过 + 真机截图验收。
- 插件 UI 直接消费本模块组件（`DdGroupCard`/`DdListRow`/`DdPrimaryButton`/`DdTextField`/`DdSwitch`/`DdType`/`deepDeepColors()` 等），所以公共组件的签名与视觉是跨仓库 API：改签名要同步 `plugin-api` ABI 与 `docs/PLUGIN_DEV.md`，并让 `plugins/` 仓库重编译。
- 发布：`./scripts/gradlew-locked :design:publishToMavenLocal`（`maven-publish`，artifact `design`，版本取 `pluginApiVersion`，与 `plugin-api` 同版本发布）。
