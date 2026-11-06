# graph · 装配图

AppGraph 拆出的装配子图：把一组相关的全局依赖的构造与接线从 `AppGraph.kt` 收进独立文件，`AppGraph` 只保留一个懒加载属性对外暴露；子图通过显式依赖类注入，不反向依赖 `AppGraph`。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginGraph.kt` | 插件装配子图：创建 `PluginInstaller` / `ExternalPluginRuntime` / `ExternalUpdateSupport` / `PluginHostManager`，按插件构造宿主服务依赖（`buildPluginServices`），并把经理暴露为 `AppGraph.pluginManager`；只读 `PluginGraphDeps` |
| `PluginGraphDeps.kt` | 插件子图的显式依赖：构造期就绪的对象直接传值，懒加载对象（`builtinWiring`/`notificationCenter`/`workshopAgentService`/`jobRegistry`/`timerService`/`goalService`/`virtualScreenFeature`/`ScreenController`/`mcpPluginBridge`）以 `() -> T` 传入，保持原懒加载时机 |

## 要点

- `PluginGraph` 只做装配，不做业务决策；插件生命周期、状态、服务实现仍在 `app/.../plugin/**`。
- 插件数据目录固定在 `files/plugins`，插件运行数据在 `<pluginId>/data`，版本目录由 `PluginPaths` 管理。
- 新增装配子图时沿用同样形式：`internal class XxxGraph(private val deps: XxxGraphDeps)` + `val` 产出，`AppGraph` 只保留懒加载入口与一个构造 deps 的私有函数；`graph/` 包不得 import `AppGraph`。
- 新增依赖时只加 `XxxGraphDeps` 字段并在 `AppGraph` 侧赋值；`AppGraph` 的属性保持 `private`，不为子图放宽可见性。
