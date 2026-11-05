# plugin/host/services · 宿主服务适配器

`plugin-api` 的 `PluginHost` 服务（Linux/特权/屏幕/通知/密钥/网络/文件/会话/任务/定时/目标/工坊/MCP/UI/App/总线/日志）在 `:app` 侧的真实实现。宿主只在本目录构造 `PluginServices`，`PluginHostImpl` 负责在服务缺失时抛 `PluginServiceUnavailable`。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginServiceDependencies.kt` | `PluginServiceDependencies`（注入的 app 对象与回调）+ `buildPluginServices(deps)`；只有 deps 提供了后端才构造对应服务，缺省为 null |
| `LinuxPluginService.kt` | `shell.LinuxEnvironment`：`exec`/`stream`（合并输出按行流式）、workspace 目录用 `cd` 前置 |
| `PrivilegePluginService.kt` | `privilege.PrivilegeController` 状态/观察；`shell` 按 effective 档位走 `RootShell` 或 `AdbBootstrap`，NORMAL 抛错 |
| `ScreenPluginService.kt` | `screen.ScreenController`（虚拟）+ 可选主屏 controller + `VirtualScreenFeature` 生命周期的薄适配；截图落 `screenshotDir` |
| `NotificationPluginService.kt` | 插件专属通知频道与稳定通知 id；`enabled` 同时看通知开关与 POST_NOTIFICATIONS 权限 |
| `SecretPluginService.kt` | `runtime.EncryptedSecretStore`，命名空间 `plugin.<pluginId>.` |
| `NetworkPluginService.kt` | `dev.pi.ai.http.HttpTransport`：`request`（2 MiB 截断）与 `download`（512 MiB 上限、sha256 校验、进度回调） |
| `FilePluginService.kt` | 以插件 data 目录为根的读写/列举/删除；`workspaceDir`/`sharedStorageDir` 只作属性暴露，绝对路径原样放行 |
| `SessionPluginService.kt` | `ConversationQueries`/`SessionCommands`：会话列表、当前会话观察、向会话发消息 |
| `JobPluginService.kt` | `runtime.JobRegistry`：启动/读取/列表/终止后台任务，`sessionId` 缺省取当前会话 |
| `TimerPluginService.kt` | `runtime.TimerService`：创建/列表/取消；`sessionId` 缺省取当前会话并从会话目录解析 session file/cwd |
| `GoalPluginService.kt` | `runtime.GoalService`：目标观察与创建/编辑/暂停/恢复/完成/清除 |
| `WorkshopPluginService.kt` | `WorkshopStore` 的扫描/加载/创建草稿/删除；`WorkshopAgentService` 的 create/edit/run 以 fire-and-forget 方式执行 |
| `McpPluginService.kt` | `McpConfigStore` 的服务器列表、`McpServerProbe` 探测；工具列表/调用需 deps 回调 |
| `UiAppInfoBus.kt` | `UiService`（openUrl/share/toast/openPage 回调）、`AppInfoService`（版本/语言/SDK/机型/安装 id）、`PluginBusImpl`/`ScopedPluginBus`（进程内 topic 总线，发布时盖 pluginId） |
| `LogPluginService.kt` | `android.util.Log`，tag 带 pluginId；测试可注入 sink |

## 要点

- 每个插件一份 `PluginServices`（在 `BuiltinPluginWiring.modern` / 新宿主装配处按 manifest 构造）：`deps.pluginId` 决定密钥命名空间、文件根目录、通知 id/频道、日志 tag、总线发布者；`deps.pluginDataDir` 默认 `<files>/plugins/<id>/data`，与 `PluginPaths` 一致。
- 跨插件共享的服务（`PluginBus` 实例、`HttpTransport`、`EncryptedSecretStore` 等）由 AppGraph 构造一次放进 deps；deps 是 data class，可 `copy(pluginId=..., pluginDataDir=...)` 派生每个插件的依赖。
- 映射损失（有意为之的薄适配）：Linux `stderr` 恒空（proot 合并输出）；`TimerTrigger.Daily/Weekly` 以 cron 表达式往返（plugin-api 只有 Once/Cron）；`ScreenService.status` 非 suspend 所以缓存快照、在初始化与每次操作后刷新，节点树扁平无 children，`scroll(deltaY)` 只按符号取方向并按 300px/步折算步数；Workshop 三个 agent 操作只返回 accepted，不暴露事件流（用 scan 观察结果）；MCP 工具列表/调用缺省为空/错误，需 AppGraph 注入回调；`SessionService` 没有 open-session 方法，故未接打开会话回调。
- 通知频道名复用 `R.string.plugins_title`：本目录不新增 res；需要独立文案时在 `res/values*/strings_*.xml` 补字符串后替换。
- 测试只覆盖纯 JVM 可替身的部分（File/Bus/AppInfo/Log/Network）；Android 依赖重的适配器靠编译与后续接线验证。
