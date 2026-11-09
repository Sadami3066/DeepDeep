# plugin · 插件

外部 `.ddp` 插件系统：插件实现 `:plugin-api` 的 `DeepDeepPlugin`，由 `manager/PluginHostManager` 统一装载、启停、贡献与状态；`.ddp` 的签名/记录/装载在 `host/external/`，事件在 `host/events/`，UI 槽位在 `host/ui/`，宿主服务适配器在 `host/services/`。App 自带工具（web/目标/后台任务/定时/小程序/安排/屏幕）不走插件系统，装配在 `graph/RuntimeGraph` 的 `AppToolContext`；插件仓库里没有随包编译的插件实现。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginStrings.kt` | 插件文案取用接口 |
| `AndroidPluginStrings.kt` | 按 App 语言读取 `R.string` 的实现 |
| `PluginSkillStore.kt` | 插件技能仓库/启用目录落盘与清理，`.deepdeep-plugin` 归属标记 |
| `manager/` | 插件宿主管理器（见目录内 AGENTS.md） |
| `host/` | 插件宿主：`:plugin-api` 契约实现、注册表、外部包管道、事件、UI 槽位、宿主服务（见目录内 AGENTS.md） |

## 要点

- 插件页是插件唯一的自绘界面：行点击进入 `pages[0]`，没有 `pages` 不可点；行尾「⋯」菜单 = 卸载 / 版本与发布者 /（失败时）重试激活、回滚。
- 生命周期由 `PluginHostManager` 驱动：`start()` 装载外部记录 → 一次物化 → 启用 → 技能同步；每插件独立 `SupervisorJob` scope，停用先 `onDisable` 再 cancel scope 与 `close()` 收尾；启停变化触发 `host.reconfigure()`，忙碌会话在轮次结束的安全点重建。
- 外部插件只从 `.ddp` 安装（ddp-1 签名、TOFU 发布者、sha256、记录 v4）；安装/更新确认页展示发布者指纹、sha256 与能力清单；更新在下次进程启动激活，失败保留旧版本；激活中断/连续启动崩溃按恢复策略跳过、自动停用或回滚（`PluginManagerCommands.retryActivation`/`rollbackPlugin`）。
- 插件只能通过 `PluginHost` 的 18 个宿主服务访问 App 能力，且无法使用 Android 资源/组件（字符串/图标走 manifest + assets）。
- 插件贡献 = tools / skills / promptSections / ui 槽位（tool.card/chat.message）/ pages（`PluginPage.content` 内联）/ composerActions / hooks；每轮 refresh 每插件只物化一次，由 `PluginRegistry` 聚合成不可变快照，工具/提示词/技能/槽位/UI 都消费这份快照。
- 插件自己的 CLI 下载与登录由插件页面或工具自行完成，宿主不再提供安装/连接流程。
