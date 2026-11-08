# ui · 页面与组件

Compose 界面层：按功能分子包，每个子包是一个页面/组件域。Screen 只渲染状态与回调，数据由 ViewModel 或 StateHolder 提供，装配与导航统一在 `navigation/`。本目录只有 `DeepDeepDrawer.kt` 一个直接文件（常驻抽屉：账户行、功能导航、按工作区分组的会话列表），其余都是子包。

## 子包

| 子包 | 职责 |
|---|---|
| `assistant/` | 系统助手设置页：默认助手角色与电源设置入口 |
| [chat/](chat/AGENTS.md) | 聊天首页、输入卡与页面状态（核心界面，详见子包文档） |
| [common/](common/AGENTS.md) | 跨页面通用组件：语音、OAuth、厂商图标、头像、扫一扫；`chat/` 放聊天、浮层与悬浮窗共用的渲染与输入组件，`market/` 放市场页共用选项卡、真实条目列表/详情与图标加载 |
| `files/` | 工作区文件浏览器：目录逐级浏览、按类型预览或外部打开 |
| `goal/` | 聊天内目标条：阶段与进度展示、暂停/恢复 |
| `jobs/` | 后台任务 sheet：列表、增量输出、终止与清理 |
| `mcp/` | MCP 服务器管理：市场/我的选项卡（市场读真实数据）、增删改、导入、连接测试与 OAuth |
| `memory/` | 长期记忆工作台：四类记忆与时间线浏览/预览、沉淀当前会话、重建检索索引 |
| [navigation/](navigation/AGENTS.md) | 自研导航：路由表、返回栈、页面装配与推入转场；页面装配在 `routes/` |
| `onboarding/` | 首次安装 6 屏引导：欢迎、称呼、模型、权限、选择工作环境、权限等级；首启判定、数据落盘与动画 |
| [plugins/](plugins/AGENTS.md) | 插件页：市场/我的选项卡（市场读真实数据）、导入/启停/更新，行尾菜单与插件自绘页面 |
| `sessions/` | 会话搜索、归档、操作菜单与重命名/删除对话框 |
| [settings/](settings/AGENTS.md) | 设置各页：个性化、权限、Provider、运行环境等 |
| `shell/` | 外壳层：`AppShell`（导航/聊天 base）、`AppOverlays`（全局叠层与抽屉）、`AppOnboardingGate` 引导门控、`/` 命令解析、分享与深链进入意图 |
| `skills/` | 技能页：市场/我的选项卡、搜索、启停、zip 安装与删除；市场安装走市场仓库 |
| [remote/](remote/AGENTS.md) | 远程连接页：扫码/粘贴配对、电脑列表与状态、连接页 WebView 与手机端样式注入 |
| `timers/` | 定时任务页：列表、编辑器、日期/时间选择与 cron 草稿 |
| [miniapp/](miniapp/AGENTS.md) | 小程序：列表/查看器、WebView 与 bridge |
| `workspaces/` | 工作区设置：项目技能、继承的全局技能/插件（`WorkspaceInheritedSheet`，关闭后新会话不注入）、AGENTS.md 编辑与工作区对话框 |

## 要点

- 包边界（根 `build.gradle.kts` 的 `verifyPackageBoundaries` 会查）：普通 `ui/<feature>` 之间禁止互相 import，只有 `common`、`navigation` 例外；`ui.navigation` 与 `ui.shell` 是组合层，可 import 各 feature；`ui.common.chat` 是共享聊天渲染层，不 import 任何 `ui.<feature>`；`ui` 下禁止 import `AppGraph`/`DeepDeepApplication`（装配只在 app 根层）或 `assistant`，`assistant`/`floating` 也禁止 import `ui.chat`（共用组件走 `ui.common.chat`）。既有违规在 `gradle/package-boundaries.allow` 里，只许减少不许新增。
- 页面注册在 `navigation/`：`AppDestination` 加路由、`routes/` 下写页面装配、`AppRoutes.kt` 的 `AppRouteContent(destination, AppRouteHost)` 分发。`AppRouteHost` 是唯一拿 `AppDependencies` 的装配上下文；Screen 通过参数收 state 与回调，不自己取依赖，也不直接调 dev.pi SDK（数据与事件都来自 runtime）。
- 多页子包把加载与操作放 `XxxStateHolder`（mcp、plugins、skills、timers、miniapp），Screen 保持薄；过滤/解析等纯逻辑抽成顶层函数以便单测。
- 用户可见文案全部在 `res/values*/strings_*.xml`（默认中文 + `values-en`），视觉只用 `:design` 的 `deepDeepColors`/`DdType`/`Dd*` 组件，不自造颜色与字号。

## 界面文案与布局原则

- 简洁优先：一屏只说一件事，标题 + 一句话副标题即可；不要堆叠解释性长句、来源说明或技术细节。
- 少文字、多交互：能用选项、开关、表单、步骤表达清楚的，不要用段落文字解释；说明性文字只保留必要的一句。
- 次要信息（路径、体积、安装计数等）放行尾且尽量短；安装组合的进度用 `2/8` 这类计数，不写「已安装 / 剩余」长文案。
- 不要在同一张表单里混合两种无关的操作（例如「附加应用」「叠加组合」与「包含应用」并列）；同类信息分组，不同类拆分或删掉。
- 新增文案先问：删掉它用户是否还能完成任务？能删就删，能改成控件就改成控件。
