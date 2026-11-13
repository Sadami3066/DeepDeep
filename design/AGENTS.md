# design · 主题与通用组件

DeepDeep 的视觉底座：明暗配色、字体与字号、动效 token、通用 Compose 组件和图标资源。只被界面层使用，不含业务逻辑；同时作为插件 UI 的 SDK 发布（`io.github.sadami3066.deepdeep:design`，版本独立于 `plugin-api`，当前 0.6.1），让插件页面与 App 共用同一套组件与主题。

## 文件

| 文件 | 职责 |
|---|---|
| `DeepDeepTheme.kt` | 主题入口：配色、字体档、字号与圆角 |
| `DdCompositeFont.kt` | 拉丁字体拼系统中文字体兜底，缓存字体文件 |
| `DdCodeHighlighter.kt` | 代码高亮：`highlightedCode(code, language, colors)` 生成带语言着色的 AnnotatedString |
| `DdComponents.kt` | 按压态、按钮、列表行、Chip、底部 Sheet 等基础件 |
| `DdControls.kt` | 开关与泛型分段选择器，带滑动指示块 |
| `DdDatePicker.kt` | 日期选择 sheet：月历网格、今天描边、选中实心；文案由调用方传入 |
| `DdDialog.kt` | 居中弹窗卡片，含标题与底部操作行 |
| `DdDropdown.kt` | 跟随锚点宽度的浮层，页面转后台即收起 |
| `DdElevation.kt` | 阴影档 token（Indicator/Soft/Composer/Overlay/Menu 等） |
| `DdEmptyState.kt` | 空状态提示：旧单行灰字 + 新 icon/action 版 |
| `DdFileRow.kt` | 文件行：图标、名称、副标题、可点击与尾部操作槽 |
| `DdGroupCard.kt` | 圆角分组卡片；旧用法 + 新 header/footer slot 版 |
| `DdHtmlText.kt` | 安全子集 HTML → AnnotatedString（粗体/斜体/下划线/删除线/标题/列表/引用/代码/链接/换行/实体），链接可回调 |
| `DdIconBox.kt` | 40dp 圆底图标容器（仅为插件兼容保留，新界面不再使用） |
| `DdIcon.kt` | 图标类型与 `DdIcon()` composable，统一 tint 与尺寸 |
| `DdIcons.kt` | 图标映射表（由 `design/icons/gen.mjs` 从 `catalog.json` 生成，勿手改） |
| `DdImage.kt` | 图片显示：本地路径/ByteArray/ImageBitmap，按长边采样解码、圆角裁剪、失败占位 |
| `DdImageScaling.kt` | 解码采样与长边缩放纯函数（`DdImageScaling`/`DdScaledSize`） |
| `DdImageViewer.kt` | 全屏图片预览：scrim 底、点按关闭、淡入 |
| `DdLoadingBlock.kt` | 转圈加说明文字的加载占位 |
| `DdMarkdown.kt` | Markdown 渲染入口：`DdMarkdown`（流式 live、文件/链接/复制/图片回调）与内部块渲染件 |
| `DdMarkdownBlocks.kt` | Markdown 块模型、解析入口与纯文本化（`markdownToPlainText`） |
| `DdMarkdownBlockScanner.kt` | 块级扫描：围栏/表格/引用/列表/定义行提取 |
| `DdMarkdownInlineScanner.kt` | 行内扫描：强调/删除线/链接/代码/kbd/脚注/文件引用/HTML 内联 |
| `DdMarkdownStreamPlanner.kt` | 流式增量分块规划，稳定前缀不重复解析 |
| `DdMenu.kt` | 下拉菜单与菜单项（图标、选中、危险色） |
| `DdMotion.kt` | 动效 token：时长、缓动曲线与弹簧 |
| `DdAvatar.kt` | 头像：图片/首字母/占位图标，可点击 |
| `DdPageScaffold.kt` | 整页脚手架：纸色底、状态栏与键盘留白 |
| `DdPullToRefresh.kt` | Material3 `PullToRefreshBox` 薄封装（刷新状态 + onRefresh，不阻塞滚动） |
| `DdSearchField.kt` | 搜索框：放大镜、占位、清空与 Esc；新 onSearch/trailing 版 |
| `DdSegmented.kt` | 字符串分段选择器 facade（选中块滑动） |
| `DdShapes.kt` | 圆角 token：`DdRadius` 档位与 `DdShapes` 常用形状 |
| `DdSnackbarHost.kt` | SnackbarHost 的 Dd 风格封装与 `DdSnackbarHostState` |
| `DdSpacing.kt` | 间距 token：0/2/4/8/12/16/18/20/24/32/40 与语义间距 |
| `DdSpinner.kt` | 旋转弧线加载圈 |
| `DdText.kt` | 文本薄封装：DdType + 颜色/maxLines/overflow/textAlign；String/AnnotatedString 双重重载，支持 softWrap/onTextLayout/inlineContent |
| `DdTextField.kt` | 单行输入框与多行文本域，聚焦变色 |
| `DdTheme.kt` | 主题变体：`DdTheme.lightSantorini()`（浅色）与 `DdTheme.darkOceanic()`（深色） |
| `DdTimePicker.kt` | 时间选择 sheet：时/分双列滚动，文案由调用方传入 |
| `DdTypingDots.kt` | 三点错峰呼吸的正在输入指示 |
| `DdWebView.kt` | 通用 WebView 容器：url/html、JS/domStorage/文件访问开关、深色适配、页面与错误回调、可释放 state |
| `DdAlpha.kt` | 透明度 token（disabled/hairline/muted/press/ripple 等） |
| `icons/` | 图标单源目录：`catalog.json`、生成脚本与分类预览页（运行时资源由它生成） |

## 要点

- 颜色从 `deepDeepColors()` 取，字号走 `DdType`，动效时长走 `DdMotion`，间距/圆角/阴影/透明度走 `DdSpacing`/`DdShapes`/`DdElevation`/`DdAlpha`；不硬编码 dp/sp、色值与时长。
- 组件只用 Compose 与本模块资源，不依赖 `:app`/`:runtime`；跨模块复用的控件先落到这里。
- 图标：单源在 `design/icons/`（7 类分类目录 + `catalog.json` 登记），`node design/icons/gen.mjs` 生成 `res/drawable-nodpi/ic_<category>_<id>.png|xml` 与 `DdIcons.kt`，分类预览 `design/icons/preview.html`；位图取自 `assets/milo-modern-icons-200-revised/` 的 **regular** 档（256 画布、线宽约 1.75dp、`#273140` 线稿、图形约占画布 85%），手写矢量 24 viewport、2.1dp 圆头描边，两种都由 `DdIcon` 统一 tint；运行时资源是生成物，只改 `design/icons/` 再重跑生成。
- 圆角是 8/12/14/16/20/24/28dp 体系，可点区域按 44dp；改视觉先改 token 再改调用点。
- 边距与按压反馈：可点组件的内容与按压/波纹边缘之间至少留 `DdSpacing.Md`（12dp）横向内边距，文字与图标不许贴边（`DdTextAction` 已按此修正）；按压反馈只覆盖组件可视形状，不许溢出到相邻内容；所有可点区域 ≥ 44dp。
- 主题预设：`ThemePreset { SYSTEM, DEEP_DEEP, SANTORINI, OCEANIC }`，入口是 `DeepDeepTheme(preset, fontStyle)`；Santorini 固定浅色、Oceanic 固定深色、DeepDeep 固定浅色（已删除其深色配色）；默认 SYSTEM 自动在 Santorini/Oceanic 间选择。新增主题只需在 `DdTheme` 加颜色函数并在 `ThemePreset` 登记，设置页会自动出现选项。
- 不用「图标容器」：列表行、空状态、展示页等位置图标直接以线性稿呈现，不加圆角方框或圆圈底色；旧 `DdIconBox` 仅为插件兼容保留，新界面不再使用。
- 设计系统仅保留 token（间距/圆角/阴影/透明度/字号）与 Phase 0 基础组件（DdText/DdAvatar/DdEmptyState/DdSearchField/DdIconBox 等）；Phase 1/2 新增组件已移除；组件可按需扩展，但新增公共组件或新增全局约束前必须先向用户汇报并获同意；圣托里尼日光主题（近白底 #F7F8F8、纯白组件 #FFFFFF、发丝线 #E2E5E7、#0078D7 主蓝、#E3F2FD 浅天蓝、#1E3A47 深青灰）由 `DdTheme.lightSantorini()` 提供，深色镜像 `DdTheme.darkOceanic()`（#1B2B34 深海青、#6699CC 极光青、#FAC863 星群黄、#EC5F67 落霞红）提供夜间方案；默认主题走「跟随系统」（浅色 Santorini / 深色 Oceanic）。
- 组件文案由调用方传入：新组件不加 `res` 文案（`DdSheetHeader` 的 `dd_close` 是历史特例）；插件作者按自己的语言提供标题/标签/错误文案。
- 单测覆盖纯逻辑：Markdown 解析栈（`DdMarkdownBlocks`/`DdMarkdownInlineScanner`/`DdMarkdownStreamPlanner`）与 `DdImageScaling` 有 JVM 单测（`testImplementation(libs.junit)`，不进 SDK 产物）；组件视觉与交互仍以编译通过 + 真机截图验收。
- `design` 是向后兼容的发布 SDK：新增组件与服务内聚的 `Dd*` 符号是加法，不改已有公开签名；`DdMarkdown`/`highlightedCode`/`markdownToPlainText`/`DdImageScaling` 供 App 与插件共用，App 不得再保留第二套 Markdown 解析或图片缩放实现。
- 插件 UI 直接消费本模块组件（`DdGroupCard`/`DdListRow`/`DdPrimaryButton`/`DdTextField`/`DdSwitch`/`DdType`/`deepDeepColors()` 等），所以公共组件的签名与视觉是跨仓库 API：改签名要同步 `plugin-api` ABI 与 `docs/PLUGIN_DEV.md`，并让 `plugins/` 仓库重编译。
- 发布：`./scripts/gradlew-locked :design:publishAllPublicationsToPagesRepository`（`maven-publish`，artifact `design`，版本独立维护，当前 0.6.1）；产物在 `build/maven-repo`，发布后复制到 `deepdeep-maven` 仓库。`plugin-api` 单独发布。
