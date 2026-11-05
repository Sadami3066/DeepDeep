# plugin · 插件

统一插件系统：内置与外部 `.ddp` 插件都实现 `:plugin-api` 的 `DeepDeepPlugin`，由 `manager/PluginHostManager` 统一装载、启停、贡献与状态；`.ddp` 的签名/记录/装载在 `host/external/`，事件在 `host/events/`，UI 槽位在 `host/ui/`，宿主服务适配器在 `host/services/`，更新在 `host/update/`。可安装的外部 CLI 能力（飞书/企微/教务/小米云）仍是随包编译的内置代码（manifest `source=EXTERNAL` 以保留安装/`plugin_setup` 体验）。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginStrings.kt` | 插件文案取用接口 |
| `AndroidPluginStrings.kt` | 按 App 语言读取 `R.string` 的实现 |
| `PluginDownloader.kt` | 内置 CLI 插件安装包下载：多 URL 回退、SHA-256、体积上限与进度 |
| `PluginSettingsStore.kt` | 插件设置 `settings.json` 读写与缓存，原子替换 |
| `PluginSkillStore.kt` | 插件技能仓库/启用目录落盘与清理，`.deepdeep-plugin` 归属标记 |
| `BuiltinPluginWiring.kt` / `Builtin*Plugins.kt` | 内置插件依赖装配与 manifest 注册（顺序即 UI 顺序） |
| `manager/` | 插件宿主管理器（见目录内 AGENTS.md） |
| `host/` | 插件宿主：`:plugin-api` 契约实现、注册表、外部包管道、事件、UI 槽位、宿主服务（见目录内 AGENTS.md） |
| `internal/` | 内置：目标、后台任务、定时、工坊插件 |
| `arrange/` `micloud/` `gui/` `websearch/` | 内置插件（均实现 `DeepDeepPlugin`） |
| `feishu/` `wecom/` `jwxt/` | 可安装的内置能力：下载/复制 CLI 到 rootfs，接入技能与 promptBody |
| `virtualscreen/` | 特权虚拟屏引擎与工具工厂，供 gui 插件使用 |

## 要点

- 生命周期由 `PluginHostManager` 驱动：`start()` 装载内置与外部插件 → 刷新状态/贡献 → 技能同步；启停/设置/安装/动作变化触发 `host.reconfigure()`，忙碌会话在轮次结束的安全点重建。
- 外部插件只从 `.ddp` 安装（ddp-1 签名、TOFU 发布者、sha256、记录 v4）；安装/更新确认页展示发布者指纹、sha256 与能力清单；更新在下次进程启动生效，失败回滚上一版本。
- 内置插件可继续用构造注入拿 App 依赖（Linux/特权/Downloader 等）；外部插件只能通过 `PluginHost` 的 17 个宿主服务访问 App 能力，且无法使用 Android 资源/组件（字符串/图标走 manifest + assets）。
- 插件贡献 = tools / skills / promptSections / settings / actions / detailRows / ui 槽位 / hooks；由 `PluginRegistry` 聚合成不可变快照，会话装配按快照构建。
- UI 全部由 manifest 驱动（名称/图标/设置 schema/阶段/来源），插件自绘槽位失败时回退宿主默认渲染；外部插件不得注册 Activity/Service/Provider。
