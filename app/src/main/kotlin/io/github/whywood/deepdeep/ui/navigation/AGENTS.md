# navigation · 导航与页面装配

自研导航：`AppDestination` 是全部路由的可序列化定义；`AppNavigator` 维护返回栈；`AppRouteContent` 按当前目的地分发到 `routes/` 下的页面 composable。

## 文件

| 文件 | 职责 |
|---|---|
| `AppDestination.kt` | 路由定义：routeKey、depth、参数与 `fromRouteKey`；新增页面先加这里 |
| `AppNavigator.kt` | 返回栈：push/pop/reset/current，`rememberAppNavigator()` 支持保存恢复 |
| `AppRoutes.kt` | `RouteSpec`、父路由与深链栈推导；`AppRouteContent(destination, AppRouteHost)` 分发 |
| `AppRouteHost.kt` | 装配上下文：`AppRouteHost`（导航/依赖/聊天回调/壳层 UI）、`ChatRouteData`（会话切片）、`ShellRouteUi`（主题/字体/昵称/头像/模型） |
| `AppScreenTransition.kt` | 推入/返回转场与冻结 base 的 `AnimatedScreenHost` |
| `PluginComposerActions.kt` | 聊天输入区的插件动作行（组合层组件） |
| `routes/` | 各页面的装配 composable，按功能分文件：Files、Settings、Provider、Plugins、Session、Miniapp |

## 规则

- `AppRouteContent` 只收 `destination` 与 `AppRouteHost`；`routes/` 里的 composable 只取自己需要的字段，不传整个 `ChatUiState`。
- 新增页面：`AppDestination` 加路由 → `routes/<Feature>Route.kt` 写装配 → `AppRouteContent` 的 `when` 加一行。不需要改 `AppShell` 的参数列表；需要新的壳层值时只扩 `AppRouteHost`/`ChatRouteData`/`ShellRouteUi`。
- `routes/` 是唯一的依赖装配点，允许 import `AppDependencies` 与各 feature 的 Screen；不得 import `AppGraph`/`DeepDeepApplication`，不得直接调 pi SDK。
- Screen 只收 state 与回调；数据从依赖里取或由 route 传入，页面内部不自己抢依赖。
