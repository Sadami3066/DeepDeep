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
| `DdEmptyState.kt` | 单行灰字空状态提示 |
| `DdGroupCard.kt` | 圆角分组卡片，统一内部列表行左右留白 |
| `DdIcons.kt` | 图标封装与全量图标常量表 |
| `DdLoadingBlock.kt` | 转圈加说明文字的加载占位 |
| `DdMenu.kt` | 下拉菜单与菜单项（图标、选中、危险色） |
| `DdMotion.kt` | 动效 token：时长、缓动曲线与弹簧 |
| `DdPageScaffold.kt` | 整页脚手架：纸色底、状态栏与键盘留白 |
| `DdSearchField.kt` | 搜索框：放大镜、占位、清空与 Esc |
| `DdSegmented.kt` | 字符串分段选择器，选中块滑动 |
| `DdSpinner.kt` | 旋转弧线加载圈 |
| `DdTextField.kt` | 单行输入框与多行文本域，聚焦变色 |
| `DdTypingDots.kt` | 三点错峰呼吸的正在输入指示 |

## 要点

- 颜色从 `deepDeepColors()` 取，字号走 `DdType`，动效时长走 `DdMotion`；不硬编码色值与时长。
- 组件只用 Compose 与本模块资源，不依赖 `:app`/`:runtime`；跨模块复用的控件先落到这里。
- 圆角是 8/12/14/16/20/24/28dp 体系，可点区域按 44dp；改视觉先改 token 再改调用点。
- 本模块无单测，改组件以编译通过 + 真机截图验收。
- 插件 UI 直接消费本模块组件（`DdGroupCard`/`DdListRow`/`DdPrimaryButton`/`DdTextField`/`DdSwitch`/`DdType`/`deepDeepColors()` 等），所以公共组件的签名与视觉是跨仓库 API：改签名要同步 `plugin-api` ABI 与 `docs/PLUGIN_DEV.md`，并让 `plugins/` 仓库重编译。
- 发布：`./scripts/gradlew-locked :design:publishToMavenLocal`（`maven-publish`，artifact `design`，版本取 `pluginApiVersion`，与 `plugin-api` 同版本发布）。
