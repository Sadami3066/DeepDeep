# graph · 装配图

AppGraph 拆出的装配子图：把一组相关的全局依赖的构造与接线从 `AppGraph.kt` 收进独立文件，`AppGraph` 只保留一个懒加载属性对外暴露。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginGraph.kt` | 插件装配子图：创建 `PluginInstaller` / `ExternalPluginRuntime` / `ExternalUpdateSupport` / `PluginHostManager`，按插件构造宿主服务依赖（`buildPluginServices`），并把经理暴露为 `AppGraph.pluginManager`；依赖直接读取 `AppGraph` 的 internal 属性，行为与拆分前一致 |

## 要点

- `PluginGraph` 只做装配，不做业务决策；插件生命周期、状态、服务实现仍在 `app/.../plugin/**`。
- 插件数据目录固定在 `files/plugins`，插件运行数据在 `<pluginId>/data`，版本目录由 `PluginPaths` 管理。
- 新增装配子图时沿用同样形式：`internal class XxxGraph(private val graph: AppGraph)` + `val` 产出，AppGraph 只保留懒加载入口。
- 装配依赖需要 `AppGraph` 暴露 `internal` 属性时，只放宽可见性，不复制状态。
