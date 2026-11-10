# graph · 装配图

AppGraph 拆出的装配子图：把一组相关的全局依赖的构造与接线从 `AppGraph.kt` 收进独立文件，`AppGraph` 只保留组合、入口与对 `AppDependencies` 的委托；子图通过显式依赖类注入，不反向依赖 `AppGraph`。

## 文件

| 文件 | 职责 |
|---|---|
| `DataGraph.kt` / `DataGraphDeps.kt` | 数据与偏好子图：目录、DataStore 偏好、附件/会话/工作区/技能存储、凭据与 Provider 目录、MCP 存储、问题中心、目标服务；持有 `jobWake` / `sharedStorage` 两个派生状态 |
| `PlatformGraph.kt` / `PlatformGraphDeps.kt` | 平台子图：RootShell、ADB 桥、权限控制器、Linux 环境（按工作区缓存）与 root bash（rootfs 准备、按权限档路由）、虚拟屏、屏幕工具装配（`ScreenToolset`，同时供插件宿主服务复用控制器）、MCP 插件桥 |
| `RuntimeGraph.kt` / `RuntimeGraphDeps.kt` | 运行时子图：App 自带工具装配（present/shell/web/安排/目标/任务/定时/小程序/记忆/开关且权限生效时的屏幕）、记忆存储与纪要（`MemoryStore`/`MemoryRecap`）、engine 工厂、Miniapp agent、`AgentRuntimeHost`、`ConversationStore` |
| `ServiceGraph.kt` / `ServiceGraphDeps.kt` | 服务子图：通知中心、定时器、后台任务、开发包管理器、`ProcessRuntime` |
| `PluginGraph.kt` / `PluginGraphDeps.kt` | 插件装配子图：创建 `PluginInstaller` / `ExternalPluginRuntime` / `PluginHostManager`，按插件构造宿主服务依赖（`buildPluginServices`；Linux/File 服务经 `HostEnvironmentProvider` 取 bash/files），并把经理暴露为 `AppGraph.pluginManager`；只读 `PluginGraphDeps` |

## 要点

- 子图之间只通过构造依赖传值；会形成循环或需要保持懒加载时机的对象用 `() -> T` 传入，子图内部再用 `by lazy` 展开，绝不调用 `AppGraph`。
- 构造顺序：`DataGraph` → `PlatformGraph` → `RuntimeGraph` → `ServiceGraph`；`host` 需要 `timerService` / `jobRegistry`，`ServiceGraph` 需要 `host` / `conversationStore`，两边都用 lambda 打破循环，首次访问时才落定。
- `AppGraph` 只保留 `AppDependencies` 委托、`pluginGraph`、`prepare`、中断提示装饰与运行时事件转发；新增子图时沿用 `internal class XxxGraph(private val deps: XxxGraphDeps)` + `val` 产出。
- 新增依赖时只加 `XxxGraphDeps` 字段并在 `AppGraph` 侧赋值；`AppGraph` 的属性保持 `private`，不为子图放宽可见性。
- 屏幕操作门控：`RuntimeGraph.isScreenOpsActive(screenOps, effective)` 同时要求开关开启与生效档为 ADB/ROOT；`PlatformGraph` 权限变化回调与 `AppGraph` 的开关回调先通过 `syncBundledSkills` 同步（含删除）`deepdeep-gui` 技能，再 `host.reconfigure()` 重建会话。
- `graph/` 包不得 import `AppGraph`；插件生命周期、状态、服务实现仍在 `app/.../plugin/**`。
