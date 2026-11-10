# plugin/host/services · 宿主服务适配器

`plugin-api` 的 `PluginHost` 17 个服务（Linux/屏幕/通知/密钥/网络/文件/会话/任务/定时/目标/小程序/MCP/UI/App/总线/日志/聊天）在 `:app` 侧的真实实现。宿主只在本目录构造 `PluginServices`，`PluginHostImpl` 负责在服务缺失时抛 `PluginServiceUnavailable`。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginServiceDependencies.kt` | `PluginServiceDependencies`（注入的 app 对象与回调）+ `buildPluginServices(deps)`；只有 deps 提供了后端才构造对应服务，缺省为 null |
| `LinuxPluginService.kt` | `runtime.host.HostEnvironment`：`exec` 走 `hostEnvironment.current().bash(cwd)` 并把 `CommandResult` 映射为插件结果（合并输出在 `stdout`，`stderr` 恒空）；`stream` 优先走 `bashStreaming`（LOCAL/ADB proot、ROOT chroot 实时回调逐行推送，24 小时超时），当前环境无流式实现时回退为命令完成后一次性推送；输出经无界 channel 背压排队，不丢弃行；`available` 由注入的 lambda 决定 |
| `ScreenPluginService.kt` | `screen.ScreenController`（虚拟）+ 可选主屏 controller + `VirtualScreenFeature` 生命周期的薄适配；`status` 首次读取后每秒轮询后端跟随状态，截图落 `screenshotDir` |
| `NotificationPluginService.kt` | 插件专属通知频道（频道 id 用完整 pluginId 区分）与稳定通知 id；`enabled` 同时看通知开关与 POST_NOTIFICATIONS 权限 |
| `SecretPluginService.kt` | `runtime.EncryptedSecretStore`，命名空间 `plugin.<pluginId>.` |
| `NetworkPluginService.kt` | `io.github.whywood.deepdeep.sdk.ai.http.HttpTransport`：`request`（2 MiB 截断）与 `download`（512 MiB 上限、sha256 校验、进度回调） |
| `FilePluginService.kt` | 以插件 data 目录为根，经 `HostEnvironment.files`（okio `FileSystem`）读写/列举/删除/resolve；`workspaceDir`/`sharedStorageDir` 只作属性暴露，绝对路径原样放行 |
| `SessionPluginService.kt` | `ConversationQueries`/`SessionCommands`：会话列表、当前会话观察（同时观察 active 与 busySessionIds）、向会话发消息 |
| `JobPluginService.kt` | `runtime.JobRegistry`：启动/读取/列表/终止后台任务，`sessionId` 缺省取当前会话 |
| `TimerPluginService.kt` | `runtime.TimerService`：创建/列表/取消；`sessionId` 缺省取当前会话并从会话目录解析 session file/cwd |
| `GoalPluginService.kt` | `runtime.GoalService`：目标观察与创建/编辑/暂停/恢复/完成/清除 |
| `MiniappPluginService.kt` | `MiniappStore` 的扫描/加载/创建草稿/删除；`MiniappAgentService` 的 create/edit/run 以 fire-and-forget 方式执行 |
| `McpPluginService.kt` | `McpConfigStore` 的服务器列表、`McpServerProbe` 探测；工具列表/调用需 deps 回调 |
| `McpModelMapping.kt` | MCP 模型对象 ↔ plugin-api DTO 映射（工具、调用结果内容块） |
| `UiAppInfoBus.kt` | `UiService`（openUrl/share/toast/openPage 回调，某个回调未注入时调用即抛 `PluginServiceUnavailable`）、`AppInfoService`（版本/语言/SDK/机型/安装 id）、`PluginBusImpl`/`ScopedPluginBus`（进程内 topic 总线，发布时盖 pluginId） |
| `ChatPluginService.kt` | `ChatService.postCustomMessage` → 宿主的会话自定义消息投递（写 JSONL + 实时事件，返回是否成功） |
| `PluginUiBridge.kt` | UI 请求桥：`share` 起系统分享 chooser（FileProvider），`openPage` 写 pending 页请求并拉起 MainActivity（DeepDeepApp 消费后导航到插件页） |
| `LogPluginService.kt` | `android.util.Log`，tag 带 pluginId；测试可注入 sink |

## 要点

- 每个插件一份 `PluginServices`：`AppGraph` 的 `servicesFactory(pluginId, dataDir, pluginScope)` 用 `PluginServiceDependencies` + `buildPluginServices` 构造；`deps.pluginId` 决定密钥命名空间、文件根目录、通知 id/频道、日志 tag、总线发布者；`deps.pluginDataDir` 默认 `<files>/plugins/<id>/data`，与 `PluginPaths` 一致。
- 跨插件共享的服务（`PluginBus` 实例、`HttpTransport`、`EncryptedSecretStore` 等）由 AppGraph 构造一次放进 deps；deps 是 data class，可 `copy(pluginId=..., pluginDataDir=...)` 派生每个插件的依赖。
- 映射损失（有意为之的薄适配）：Linux `stderr` 恒空（run.sh 已 `2>&1` 合并输出，内容落在 `stdout`）；`TimerTrigger.Daily/Weekly` 以 cron 表达式往返（plugin-api 只有 Once/Cron）；`ScreenService.status` 非 suspend，所以首次访问起启动后台轮询（1s）跟随虚拟屏/无障碍后端变化，并在 start/stop 后立即刷新，节点树扁平无 children，`scroll(deltaY)` 只按符号取方向并按 300px/步折算步数；Miniapp 三个 agent 操作只返回 accepted，不暴露事件流（用 scan 观察结果）；MCP 工具列表/调用缺省为空/错误，需 AppGraph 注入回调；`SessionService` 没有 open-session 方法，故未接打开会话回调。
- 通知频道名复用 `R.string.plugins_title`：本目录不新增 res；需要独立文案时在 `res/values*/strings_*.xml` 补字符串后替换。分享 chooser 标题复用 `R.string.files_share`。
- `UiService` 的四个能力都要真实接线：AppGraph 注入 `openUrl`/`toast`/`share`/`openPage` 回调（后两者转 `PluginUiBridge`）；任一未注入时插件调用得到 `PluginServiceUnavailable`，不得静默 no-op。
- `PluginUiBridge` 的 FileProvider 分享依赖 `res/xml/file_paths.xml` 中的 `plugins/`、`gui/`、`home/` 等路径；新增可分享目录要同步该文件。
- 测试覆盖纯 JVM 可替身的部分（File/Bus/AppInfo/Log/Network/Linux stream/Screen 轮询/Session 观察/UI 接线/PluginUiBridge 请求状态）；Android 依赖重的部分靠编译与后续接线验证。
