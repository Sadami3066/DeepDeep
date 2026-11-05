# plugin/manager · 插件宿主管理器

统一管理内置与外部插件的运行期状态：装载、启停、贡献聚合、状态/设置/技能同步、外部包安装与更新、`plugin_setup` 流程。UI 与 runtime 只读管理器状态，不直接持有插件对象。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginHostManager.kt` | 管理器主体：内置/外部描述符、`PluginRegistry` 快照、状态刷新、贡献物化、启停/设置/安装/卸载/动作、setup 流程、会话 hooks 绑定、UI 槽位查询 |
| `PluginManagerFacade.kt` | 对 UI/装配层暴露的接口（状态流、操作、槽位、更新、`PluginController`） |
| `PluginUiModels.kt` | UI 状态模型：快照/相位/可用性/状态/操作/交互/外部导入与更新模型 |
| `BuiltinPluginEntry.kt` | 内置插件（manifest + 实例）注册项 |
| `BuiltinPluginStateStore.kt` | 内置插件的安装/启用状态 `builtin_state.json` |
| `ExternalPluginRuntime.kt` | 外部 `.ddp` 的装载/激活/待更新提交/卸载包装 |

## 要点

- 状态相位：`IMPORTED/INSTALLED/ACTIVE/DEGRADED/FAILED/DISABLED/PENDING_RESTART/UNLOADED`；只有 ACTIVE/DEGRADED/PENDING_RESTART 贡献能力。
- 每次刷新重新物化 `PluginContributions`（状态相关的 actions/detailRows 随之变化），失败只记录到插件 `lastError`，不拖垮其他插件。
- 外部记录 v4 在 `host/external/ExternalPluginRecordStore`；内置启用/安装状态在本目录 store；两者都原子写。
- `bindSessionHooks` 把会话级 `PluginRuntimeHooksAdapter` 绑定到 engine 的 hooks delegate，`onRuntimeEvent` 负责运行期事件派发；会话关闭解绑。
- 外部安装/更新只做「校验 + 暂存」，不静默生效；`PENDING_RESTART` 在下一次进程启动提交。
- setup 与插件动作在独立 `Job` 上执行：`cancelInteraction` 取消 sheet 并立即中断正在跑的 CLI/登录流程；`cancelSetup(pluginId)` 供 `plugin_setup` 的运行取消信号同步取消，取消结果以 `PluginSetupResult.cancelled` 返回而不是抛异常。
