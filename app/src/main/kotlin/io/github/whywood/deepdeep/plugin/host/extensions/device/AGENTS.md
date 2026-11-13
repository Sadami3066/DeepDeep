# plugin/host/extensions/device · 插件设备能力适配器

plugin-api 1.1.0 设备类服务的宿主实现：`DeviceService`、`ClipboardService`、`PermissionService`、`PackageService`、`PrivilegeService`、`IntentsService`、`ComponentService`、`BackgroundService` 八个服务，加后台任务调度基础设施。宿主在 `PluginGraph` 的 `servicesFactory` 里用 `DeviceServiceDeps` 构造 `DevicePluginServices`，经 `PluginServiceDependencies.deviceServices` 注入 `PluginServices`，缺 capability 时由 `declaredOnly` 过滤。

## 文件

| 文件 | 职责 |
|---|---|
| `DeviceServiceDeps.kt` | `DevicePluginServices` 服务容器、`DeviceServiceDeps` 注入依赖（Context/scope/插件目录/interaction/RootShell/AdbBootstrap/HostAccess 状态/launchPluginActivity 等）、`buildDevicePluginServices(deps)` |
| `DevicePluginService.kt` | `DeviceService`：Build/屏幕/内存/存储/语言/时区/安装 id；常驻 `BroadcastReceiver` 聚合电池、充电、省电、网络、亮屏、锁屏进 `DeviceLiveSnapshot`，`observe()` 合并 15s tick 与 HostAccess 变化输出 `DeviceState`；`vibrate` 走 VibratorManager/Vibrator；`openSettings` 打开对应系统设置页 |
| `DeviceLiveState.kt` | 设备状态纯聚合 reducer（`DeviceLiveSnapshot` + `DeviceLiveEvent`）与电量百分比计算，纯 JVM 单测覆盖 |
| `DeviceSettingsMapping.kt` | `DeviceSettings` / `PluginSpecialAccess` → `DeviceSettingsScreen` 的纯映射，Android Intent 构造在 `DeviceSystemSupport.kt` |
| `DeviceSystemSupport.kt` | 设置页 Intent 构造与降级（失败回落到应用详情）、安全 `startActivity`、shell 单引号转义、插件 id 校验、`HostAccess` → `HostAccessLevel` 映射 |
| `PluginPermissionMapping.kt` | manifest `permissions` 的 wire 值 → Android 运行时权限 / `PluginSpecialAccess` 映射，未知值一律不授予 |
| `ClipboardPluginService.kt` | `ClipboardManager` 读写 text/uri、`has`、`clear`（Android 10+ 后台读取由系统限制，返回 null） |
| `PermissionPluginService.kt` | `granted` 用 `ContextCompat.checkSelfPermission`（POST_NOTIFICATIONS 在 33 以下用通知开关）；`request` 经 `interaction.requestPermissions` 按原 wire 键回填；`special` 检查通知监听/无障碍/悬浮窗/用量/电池优化/未知来源安装/全文件访问；`openSettings` 打开对应系统页 |
| `PackagePluginService.kt` | PackageManager 列表/版本/启用/图标（Drawable → PNG bytes）；`launch` 走启动 Intent 或应用详情；`install` silent 走 RootShell/ADB `pm install -r -g`，否则限安装权限后经 `UpdateInstaller` 拉起系统安装；`uninstall` 有特权走 `pm uninstall`，否则 `ACTION_DELETE`；APK 路径只接受插件 data/cache 目录内文件 |
| `PrivilegeExecutor.kt` | Root/ADB 执行与静默安装/卸载的共用实现：AUTO 按 `hostAccess.effective` 选 ROOT/ADB，LOCAL 返回 error 结果；全部 `withTimeoutOrNull` + `runCatching`，`pm` 命令参数做包名/路径校验 |
| `PrivilegePluginService.kt` | `PrivilegeService`：access/root/adb 状态、`probeRoot`、`exec`、install/uninstall、`openAccessSettings`（深链 `deepdeep://settings/access` 可用时拉起，否则应用详情）、`openWirelessDebugging`、`startAdbPairing`/`submitAdbPairingCode`/`connectAdb` 直连 `AdbBootstrap` |
| `IntentsPluginService.kt` | startActivity（补 NEW_TASK）、startActivityForResult（interaction）、sendBroadcast、start/stopService（后台限制异常返回 false）、canResolve/resolve/queryActivities（PackageManager） |
| `ComponentsPluginService.kt` | `startActivity(PluginActivityOptions)` 转 `launchPluginActivity` 回调；`registerReceiver` 用 `RECEIVER_EXPORTED` 注册并把 Intent 投进 SharedFlow，`close()` 注销、插件 scope 结束时自动关闭 |
| `BackgroundPluginService.kt` | `BackgroundService`：schedule/cancel/tasks、前台服务三操作委托 `PluginHostService`、电池优化三操作；每次宿主重建时按元数据恢复非 BOOT 任务，插件 scope 结束时取消实际排程但保留元数据 |
| `BackgroundTasks.kt` | `BackgroundTaskRecord`、`background.json` 编解码（schemaVersion + 任务数组，未知/损坏条目跳过）、`PluginBackgroundStore`（跨实例共享 Mutex 的原子读写）、下次运行时间/到点判定/重排计算 |
| `PluginBackgroundDispatcher.kt` | 进程内派发点 `dispatch` 交给 AppGraph 注入，未注入时按 100ms 轮询等待；派发前校验插件 id/taskId |
| `PluginBackgroundScheduler.kt` | WorkManager 一次性/周期任务（inputData 带 pluginId/taskId/kind，tag 按插件）+ AlarmManager 精确任务（`canScheduleExactAlarms` 不满足降级 `setAndAllowWhileIdle`），支持按任务/按插件取消 |
| `PluginBackgroundWorker.kt` | WorkManager `CoroutineWorker`：回调 `PluginBackgroundDispatcher`，派发失败重试至 3 次；成功后一次性任务清元数据、周期任务记 lastRunAt |

## 要点

- 后台任务元数据固定在 `files/plugins/<id>/host/background.json`；插件禁用只取消 WorkManager/AlarmManager 排程，不清元数据，重新启用时 `BackgroundPluginService` 初始化恢复。
- 精确任务在 `tasks()` 里用 `kind = "exact-degraded"` 标注降级（真正精确时是 `exact`）；BOOT 任务只保留元数据，只在开机广播时派发。
- `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / 精确闹钟由 `plugin/host/components/PluginSystemEventReceiver` 处理，开机时若派发器未注入先触发 `ProcessRuntime.startAsync()` 再派发。
- `DeviceServiceDeps.pluginEnabled` 可空：注入时用管理器状态，未注入时回退读 `files/plugins/plugins.json` 的 enabled 记录；`BackgroundService.schedule` 在插件禁用时返回 false。
- 已知环境依赖：NetworkType 需要 `ACCESS_NETWORK_STATE`（当前 manifest 未声明，缺失时降级为 `none`，不抛异常）；包枚举依赖 `QUERY_ALL_PACKAGES`；精确闹钟权限 `SCHEDULE_EXACT_ALARM` 已声明但用户可关，关闭时降级。
- 前台服务每条插件通知的键是 pluginId（`ForegroundServiceOptions.id` 相应被合并），channel 为 `deepdeep_plugin_<pluginId>_service`；后台启动限制异常由 `PluginHostService.start` 捕获返回 false。
- 纯逻辑（状态 reducer、wire 映射、设置页映射、后台任务编解码/原子写/时间计算）在 `app/src/test/kotlin/io/github/whywood/deepdeep/plugin/host/extensions/device/` 有 JVM 单测；Android 依赖重的部分靠编译与后续设备验证。
