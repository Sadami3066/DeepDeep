# plugin · 插件

插件框架：两套插件契约并存——旧 `OfficialPlugin` 与新 `:plugin-api` 的 `DeepDeepPlugin`（经 `host/ModernPluginAdapter` 适配成 `OfficialPlugin`）；安装/启用/状态/技能/设置/promptBody 的编排，以及内置与外部官方插件；插件是 agent 扩展能力的入口，业务实现都在各插件子包，UI 只在 `ui/plugins/` 渲染。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginApi.kt` | 插件契约：能力、PluginUi/PluginProgress、状态/动作/详情/技能数据结构 |
| `PluginManager.kt` | 插件总控（实现 runtime 的 PluginController）：恢复、状态、工具/promptBody 汇总与操作分发 |
| `PluginInstaller.kt` | 安装/卸载编排：可用性校验、写安装记录与技能、失败回滚与通知 |
| `PluginSetupSession.kt` | plugin_setup 连接会话：安装确认、交互 sheet 状态、取消与前后台提前完成 |
| `PluginStatusCache.kt` | 状态快照缓存：可用性 + 记录 + `status()` → PluginsUiState，2 分钟 stale 刷新 |
| `PluginState.kt` | 插件 UI 状态模型（快照/操作/交互）与版本比较 |
| `PluginRecordStore.kt` | 安装记录 plugins.json 读写，损坏备份并暂停写入 |
| `PluginSettingsStore.kt` | 插件设置 settings.json 读写与缓存，原子替换 |
| `PluginSetting.kt` | 设置项声明（Toggle/Choice/Number/Text 含 secret）与 normalize |
| `PluginSkillStore.kt` | 插件技能的仓库/启用目录落盘与清理，用 `.deepdeep-plugin` 标记归属 |
| `PluginDownloader.kt` | 安装包下载：多 URL 回退、SHA-256 校验、体积上限与进度 |
| `PluginSetupContent.kt` | plugin_setup sheet 里插件自带 Composable 内容的槽位 |
| `AndroidPluginStrings.kt` | 按 App 语言读取插件字符串资源（PluginStrings 实现） |

## 子包

| 包 | 职责 |
|---|---|
| `arrange/` | 内置：系统日历读写工具与权限申请；已迁移到 `DeepDeepPlugin`（manifest 在 `BuiltinAndroidPlugins.androidArrangeManifest`） |
| `feishu/` | 外部：飞书 CLI，下载安装 lark-cli 并接入技能与 promptBody；已迁移到 `DeepDeepPlugin` |
| `gui/` | 内置（需特权）：屏幕操作，无障碍/ADB 节点树、点击输入与截图；仍在旧契约，P7 迁移 |
| `internal/` | 内置：目标、后台任务、定时、工坊插件（均实现 `:plugin-api` 契约） |
| `jwxt/` | 外部：中山大学教务 CLI，assets 装进 proot 并接入技能与 promptBody；已迁移到 `DeepDeepPlugin` |
| [micloud/](micloud/AGENTS.md) | 外部：小米云 WebView 登录与 `mi_cloud_*` 读写工具；已迁移到 `DeepDeepPlugin`（7 个能力开关是 `SettingSpec.Toggle`） |
| `virtualscreen/` | 特权虚拟屏：工具注册与 engine 接线，供 gui 插件使用 |
| `websearch/` | 内置：web_search/web_fetch 与 DeepSeek 搜索 provider |
| `wecom/` | 外部：企业微信 CLI，下载安装 wecom-cli 并接入技能与 promptBody；已迁移到 `DeepDeepPlugin` |

## 要点

- 两种 kind：EXTERNAL 走安装流程并进 `plugin_setup`（飞书/企微/教务是真装 CLI，小米云安装为空操作），INTERNAL 无安装、启动时自动补记录（屏幕操作、系统日历、网络搜索 + internal 下四个）；`plugin_setup` 要求 App 在前台。
- 三类贡献统一由 PluginManager 汇总：`tools()` 进 runtime 工具表、`promptBody()` 进系统提示、`skills()` 落 agent 技能目录；安装/启用/设置变化都触发 `host.reconfigure()` 重新装配。
- 状态只在已安装且启用时查，`status()` 归为 Ready/NotReady/Failed 三种并缓存 2 分钟；详情行、动作、设置项都从同一快照派生，不要另建状态。
- 迁移进度：内置 goal/jobs/timer/workshop/web-search 与 arrange、外部 micloud 已改用 `DeepDeepPlugin`（manifest 在 `BuiltinInternalPlugins.kt` / `BuiltinAndroidPlugins.kt`，经 `wiring.modern` 装配）；feishu/wecom/jwxt/gui 与 virtualscreen 仍在旧契约。
- 用户可见文案用 `@StringRes` 指向 `res/values*/strings_plugin_*.xml`（默认中文 + values-en），经 AndroidPluginStrings 按 App 语言取；promptBody 与技能 markdown 是给模型的输入，保持中文、不本地化，也不写开发说明。
