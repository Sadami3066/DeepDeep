# service · 前台服务与通知

App 常驻后台的系统接线：前台服务是唯一常驻入口，通知中心把运行、提问、安装、无障碍事件变成系统通知；另有 shell 执行器、保活入口与进程诊断。业务状态在 runtime，这一层不持有真相。

## 文件

| 文件 | 职责 |
|---|---|
| `AgentService.kt` | 前台服务：常驻通知、定时器、悬浮窗、配对码动作；启动时确保进程已初始化 |
| `ProcessRuntime.kt` | 进程初始化唯一入口：随包技能同步 → 插件启动 → prepare → host reconfigure/ensureStarted，幂等、失败可重试；前台刷新插件与技能 |
| `Diagnostics.kt` | 静默失败诊断日志：`files/diagnostics/errors.log`，按行数/字节截断；启动、前台刷新、技能同步等后台路径失败时调用 |
| `NotificationCenter.kt` | 通知中心：订阅 ConversationStore 的运行状态与完成事件，发出前台、提问、结果、安装、无障碍通知 |
| `AppVisibility.kt` | App 前后台计数与把任务拉到前台 |
| `ProcessExitLog.kt` | 记录历史进程退出原因到诊断日志 |
| `GuiAccessibilityService.kt` | 无障碍服务，为屏幕工具提供节点树 |
| `GuiAccessibilityState.kt` | 无障碍服务连接与启用状态 |
| `AppShellExecutor.kt` | 把 Linux 执行器（proot 或 root chroot 的路由结果）适配成 runtime 的 shell 执行器 |
| `RootBash.kt` | Root bash 模式：开关、权限档与 rootfs 准备状态合成执行模式，按次路由到 proot/chroot，提供重试与重建 |
| `LinuxJobRunner.kt` | 后台任务在 proot 里的流式命令执行器 |

## 要点

- `AgentService` 是唯一常驻入口（START_STICKY）：通知中心、定时器与悬浮窗都由它启动；进程被杀后靠打开 App 或系统重启服务恢复。
- 进程初始化只经 `ProcessRuntime`：Application/Activity/浮层/服务都调它的 `start()`，不要直接调 `pluginManager.start()`、`host.reconfigure()`、`host.ensureStarted()`；前台刷新也走 `onForeground()`。
- 通知与前台服务用的是 application context，必须经 `LocalizedContext`（Graph 的 `uiContext`）解析文案，否则英文模式仍是中文。
- 运行中会话与完成事件只从 `ConversationStore` 取（`activeRuns`/`runCompletions`），不再自行跟踪 runtime 事件。
- 无障碍连接是全局单例状态：节点读取、中文输入与虚拟屏节点都依赖它；服务被系统断开时工具层应降级，不要假设它常在。
