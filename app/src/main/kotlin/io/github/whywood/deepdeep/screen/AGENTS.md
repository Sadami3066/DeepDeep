# screen · 屏幕操作后端

`screen_*` 工具的统一实现层：默认操作虚拟屏，显式 `target=main` 才走主屏。节点模型、选择器、无障碍动作与控制器都在本包，负责路由、能力判定与工具协议；App 自带工具在 `graph/RuntimeGraph` 里按「屏幕操作」开关装配。

## 文件

| 文件 | 职责 |
|---|---|
| `ScreenToolset.kt` | 屏幕工具装配：构造虚拟屏/主屏控制器并产出 `screen_*` 工具 |
| `ScreenTools.kt` | `screen_*` 工具定义：参数解析、JSON 返回与提示 |
| `PhongentVisibleText.kt` | 同屏读回的局部 OCR 适配，原 Mika 稳帧规则、本地中英识别及 OCR 去重，在树质量不足时补文字证据 |
| `PhongentScreenEffect.kt` | 适配 Mika `Interfaces.finish` / `ScreenSession.withScreen` 与 `withEffect` 的成功门槛、稳定等待、效果和同一次节点读回；树不足时附虚拟屏可见文字 |
| `PhongentScreenReferences.kt` | 对接原稳定 ref 和增量界面：模型上下文缺少基准时回退全量，最多五次连续差分，切换屏实例、窗口或后端时刷新引用 |
| `PhongentBatchTool.kt` | `do_steps` 的宿主适配，嵌套执行同屏工具，返回逐步动作效果与批内等待 |
| `ScreenExecutionLock.kt` | 每个屏幕后端共享的执行锁，宿主和插件的单次动作与证据采集在同一锁内 |
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
- 宿主 tap/swipe/scroll/type/key/launch 使用 Phongent 的动作效果输出。动作已发出、可观察变化、输入已回读和任务完成是独立事实。工具兼容主屏和虚拟屏的既有定位器。
- 同屏宿主工具顺序执行，保证动作前后采样之间不会插入另一个宿主工具的动作。
- 虚拟屏路由每次执行实时读取 `instanceId + displayId` 绑定；任一值变化都会清空旧快照，跨越重建的长操作拒绝返回旧结果。
- `screen_*` 与 `virtual_screen` 只在设置「屏幕操作」开关打开时出现在工具清单里；开关状态在每次装配工具时读取，切换后由 `host.reconfigure()` 重建会话生效。
