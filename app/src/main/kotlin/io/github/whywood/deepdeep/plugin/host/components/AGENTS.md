# plugin/host/components · 插件宿主 Android 组件

manifest 里声明的插件宿主组件（`AndroidManifest.xml` 已注册）：插件前台服务、系统事件接收器、独立窗口 Activity、通知监听与通知动作。组件本身不持有插件逻辑，只做系统入口与路由；插件能力实现在 `plugin/host/extensions/` 与 `plugin/host/services/`。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginHostService.kt` | 插件前台服务（`foregroundServiceType="specialUse"`）：静态 `start/update/stop` 供 `BackgroundService` 调用，按 pluginId 维护每个插件一条 ongoing 通知，channel 为 `deepdeep_plugin_<pluginId>_service`，内容意图打开插件首页/宿主；`START_NOT_STICKY`，前台启动限制异常捕获后返回 false |
| `PluginSystemEventReceiver.kt` | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / 精确闹钟 `ACTION_EXACT_ALARM`：`goAsync()` + 限时协程，开机与升级后重排所有插件精确闹钟并派发 BOOT 与到期/恢复任务；派发器未注入时先拉起 `ProcessRuntime` |
| `PluginHostActivity.kt` | 插件独立窗口/新任务页宿主：接收 pluginId/pageId/arguments，承载插件 Compose 页面 |
| `PluginNotificationListenerService.kt` | 通知监听系统服务（`BIND_NOTIFICATION_LISTENER_SERVICE`）：符合插件声明时把系统通知投递给插件通知服务 |
| `PluginNotificationActionReceiver.kt` | 插件通知动作回调（通知按钮/回复），把动作交回插件 |

## 要点

- 插件前台通知 ID 从固定基数按 pluginId 散列，避免与宿主运行状态通知（`NotificationCenter` 10000 段）冲突；通知小图标统一 `R.drawable.ic_launcher_foreground`。
- `PluginHostService` 在多插件共用时只有一个通知处于前台服务位，其余用普通 ongoing 通知展示；最后一个插件停止后 `stopForeground + stopSelf`，不做系统杀后重启。
- `PluginSystemEventReceiver` 的所有耗时操作都在 `goAsync` 的协程内完成并限时 9s，不做静态广播长任务；插件任务元数据在 `files/plugins/<id>/host/background.json`，收不到派发的任务由下次启动恢复。
- 组件声明的权限、`foregroundServiceType` 与属性由 `AndroidManifest.xml` 统一维护，新增组件需同步 manifest 与本索引。
