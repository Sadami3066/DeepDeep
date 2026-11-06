# screen · 屏幕操作后端

`screen_*` 工具的统一实现层：默认操作虚拟屏，显式 `target=main` 才走主屏。节点模型、选择器、无障碍动作与控制器都在本包，负责路由、能力判定与工具协议；App 自带工具在 `graph/RuntimeGraph` 里按「屏幕操作」开关装配。

## 文件

| 文件 | 职责 |
|---|---|
| `ScreenToolset.kt` | 屏幕工具装配：构造虚拟屏/主屏控制器并产出 `screen_*` 工具 |
| `ScreenTools.kt` | `screen_*` 工具定义：参数解析、JSON 返回与提示 |
| `GuiActions.kt` | 无障碍动作接口、截图/等待结果与异常 |
| `GuiAccessibilityActions.kt` | `GuiActions` 的无障碍服务实现：节点树、手势、输入、截图 |
| `GuiNodeTree.kt` | 节点/快照/窗口/选择器模型与节点树构建、查找、序列化 |
| `GuiSnapshotCache.kt` | 按 displayId 缓存快照与已知节点 ref，供点击与输入定位 |
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
- 工具层只做参数校验与 JSON 化，屏幕差异由 `ScreenController` 后端承担；节点树、选择器定义在本包 `GuiNodeTree.kt`。
- `screen_*` 与 `virtual_screen` 只在设置「屏幕操作」开关打开时出现在工具清单里；开关状态在每次装配工具时读取，切换后由 `host.reconfigure()` 重建会话生效。
