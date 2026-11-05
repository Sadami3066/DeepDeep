# screen · 屏幕操作后端

`screen_*` 工具的统一抽象层：默认操作虚拟屏，显式 `target=main` 才走主屏。节点模型与选择器复用 `plugin/gui`，本包负责路由、能力判定与工具协议。

## 文件

| 文件 | 职责 |
|---|---|
| `ScreenTools.kt` | `screen_*` 工具定义：参数解析、JSON 返回与提示 |
| `ScreenContracts.kt` | 屏幕数据模型与后端、控制器接口契约 |
| `ScreenRouter.kt` | 虚拟屏默认控制器：组合引擎与无障碍后端 |
| `MainScreenController.kt` | 主屏控制器：无障碍节点操作加特权系统注入 |
| `MainScreenShell.kt` | 主屏特权命令通道（ADB/Root 择一执行） |
| `AccessibilityScreenBackend.kt` | 无障碍后端适配：观察、点击、输入、等待 |
| `VirtualEngineBackend.kt` | 虚拟屏引擎后端适配与失败分类映射 |
| `ScreenAppCatalog.kt` | 可启动应用列表与应用启动（含主屏） |
| `ScreenBounds.kt` | 节点/坐标可用性裁剪与滑动坐标计算 |

## 要点

- 两屏不互相回退：虚拟屏失败不会自动切主屏，主屏必须显式 `target=main`；ref 不能跨屏或跨虚拟屏实例使用。
- 节点读取、ref 点击与中文输入依赖无障碍；坐标手势、ASCII 与按键在主屏可由 ADB/Root 补齐，真实能力只信 `screen_status`。
- 工具层只做参数校验与 JSON 化，屏幕差异由 `ScreenController` 后端承担；节点树、选择器定义在 `plugin/gui`，不要在本包重建。
