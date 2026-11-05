# ui · 页面与组件

Compose 界面层：按功能分子包，每个子包是一个页面/组件域。Screen 只渲染状态与回调，数据由 ViewModel 或 StateHolder 提供，装配与导航统一在 `navigation/`。本目录只有 `DeepDeepDrawer.kt` 一个直接文件（常驻抽屉：账户行、功能导航、按工作区分组的会话列表），其余都是子包。

## 子包

| 子包 | 职责 |
|---|---|
| `assistant/` | 系统助手设置页：默认助手角色与电源设置入口 |
| [chat/](chat/AGENTS.md) | 聊天首页与消息流（核心界面，详见子包文档） |
| [common/](common/AGENTS.md) | 跨页面通用组件：语音、OAuth、厂商图标、头像与扫一扫 |
| `files/` | 工作区文件浏览器：目录逐级浏览、按类型预览或外部打开 |
| `goal/` | 聊天内目标条：阶段与进度展示、暂停/恢复 |
| `jobs/` | 后台任务 sheet：列表、增量输出、终止与清理 |
| `mcp/` | MCP 服务器管理：增删改、导入、连接测试与 OAuth |
| `navigation/` | 自研导航：路由表、返回栈、页面装配与推入转场 |
| `onboarding/` | 首次安装 6 屏引导：首启判定、数据落盘与动画 |
| `plugins/` | 插件列表/详情：启停、安装更新、专用设置与 setup sheet |
| `questions/` | ask_user_question 提问卡/已答题卡与工具轨迹解析 |
| `sessions/` | 会话搜索、归档、操作菜单与重命名/删除对话框 |
| [settings/](settings/AGENTS.md) | 设置各页：个性化、权限、Provider、运行环境等 |
| `shell/` | 外壳层：抽屉/浮层宿主、`/` 命令解析、分享与深链进入意图 |
| `skills/` | 已安装技能管理：搜索、启停、zip 安装与删除 |
| `timers/` | 定时任务页：列表、编辑器、日期/时间选择与 cron 草稿 |
| [workshop/](workshop/AGENTS.md) | 创意工坊：列表/查看器、WebView 与 bridge |
| `workspaces/` | 工作区设置：项目技能、AGENTS.md 编辑与工作区对话框 |

## 要点

- 包边界（根 `build.gradle.kts` 的 `verifyPackageBoundaries` 会查）：普通 `ui/<feature>` 之间禁止互相 import，只有 `common`、`navigation` 例外；`ui.navigation` 与 `ui.shell` 是组合层，可 import 各 feature；`ui` 下禁止 import `AppGraph`/`DeepDeepApplication`（装配只在 app 根层）。既有违规在 `gradle/package-boundaries.allow` 里，只许减少不许新增。
- 页面注册在 `navigation/`：`AppDestination` 加路由、`AppRoutes.kt` 的 `AppRouteContent` 装配 Screen（唯一拿 `AppDependencies` 的地方）；Screen 通过参数收 state 与回调，不自己取依赖，也不直接调 dev.pi SDK（数据与事件都来自 runtime）。
- 多页子包把加载与操作放 `XxxStateHolder`（mcp、plugins、skills、timers、workshop），Screen 保持薄；过滤/解析等纯逻辑抽成顶层函数以便单测。
- 用户可见文案全部在 `res/values*/strings_*.xml`（默认中文 + `values-en`），视觉只用 `:design` 的 `deepDeepColors`/`DdType`/`Dd*` 组件，不自造颜色与字号。
