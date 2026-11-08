# ui/settings · 设置页

设置功能的全部页面与分区：主设置页按分组进入各子页，开关与选择落到 `app/data` 的偏好/Store，凭据与权限状态来自 AppGraph 并最终喂给 runtime。页面只做展示与编辑，状态和回调大多由 `ui/navigation/AppRoutes.kt` 注入，个别页面（悬浮窗、扫一扫）自建偏好/Store。

## 文件

| 文件 | 职责 |
|---|---|
| `SettingsScreen.kt` | 设置首页：账户卡、功能入口（Provider/权限/Agent 设置/Linux 设置/Android 设置）、主题/字体/语言、通知、版本与许可 |
| `ChatSettingsScreen.kt` | 聊天设置：正文字号、过程折叠、运行中发送（插话/排队）、自动重试、后台唤醒、自动朗读 |
| `AgentSettingsScreen.kt` | Agent 设置：屏幕操作开关与未生效提示（点提示进权限页）、权限级别行（进权限页） |
| `AndroidSettingsScreen.kt` | Android 设置：悬浮窗开关与悬浮权限申请/状态、系统助手角色与电源设置 |
| `FontScreen.kt` | 英文字体选择与预览（DdFontStyle） |
| `PermissionScreen.kt` | 权限三档选择、ADB 配对、Root 探测、生效档提示 |
| `PersonalizationScreen.kt` | 个性化：头像、称呼、互动风格、语气、自定义指令 |
| `PrivacyScreen.kt` | 隐私说明：本地数据、Provider 通信、统计、小米云、卸载 |
| `ProviderListScreen.kt` | 全厂商列表：搜索排序、已配置标记、进详情 |
| `ProviderDetailScreen.kt` | 厂商详情：OAuth 登录/退出、API Key 保存/清除 |
| `RuntimeSettingsScreen.kt` | Linux 设置页：Linux 环境 + 开发包组合页 |
| `LinuxEnvironmentSection.kt` | Linux 分区：安装/重置/启用、共享存储、挂载目录（SAF 选公共存储子目录 + 名称/说明）与占用 |
| `PublicStoragePicker.kt` | 公共存储 SAF 选择：仅接受 externalstorage+primary，解析为 `/storage/emulated/0` 路径 |
| `DevPackagesSection.kt` | 开发包分区：DevBundle 安装状态、刷新与详情 |
| `ScanSettingsScreen.kt` | 扫一扫策略：官方/自定义分组、编辑、导入导出、试跑 |
| `ScanJsonScreen.kt` | 扫一扫策略 JSON 编辑与保存 |
| `UsageHabitsScreen.kt` | 使用习惯：系统助手浮层进入模式 |
| `SettingsComponents.kt` | 设置页共用小组件：分组卡、状态胶囊、占位行 |

## 要点

- Screen 不持有全局状态、数据落在 `data/` 的 `*Preferences`/`*Store`；除 `SettingsGroup` 被插件页、小米云、工作区设置页复用外，其余 Screen 只由 `AppRoutes.kt` 路由，新增入口要加 `AppDestination` 分支。
- Provider 凭据经 `graph.credentials` 写入并立即发布 authKinds，默认模型由 `core/ModelSelection` 纠正；设置页不直接改 `modelConfigStore`。
- 屏幕操作开关在 Agent 设置页：运行时注入 `screen_*` / `virtual_screen` 工具与 `deepdeep-gui` 随包技能的条件是开关开启且当前生效档为 ADB 或 Root，门槛判断见 `graph/RuntimeGraph.kt` 的 `isScreenOpsActive`；开关变化经 `AppGraph` 触发随包技能重同步与会话重建，开关开启但未生效时在开关下方显示橙色提示并可点入权限页。
- 扫一扫策略由 `ui/common/scan` 承载：官方内置 `assets/scan/strategies.json`（只读），自定义与版本迁移落 `files/scan/strategies.json`。
