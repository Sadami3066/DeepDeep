# assistant · 系统助手浮层

默认助手手势唤起的全屏透明浮层：输入卡、会话面板、工作区/会话切换与光效。会话状态与发送由 `ConversationStore` 与 runtime 提供，本包只做浮层交互。

## 文件

| 文件 | 职责 |
|---|---|
| `AssistantOverlayActivity.kt` | 浮层 Activity 入口：透明全屏、权限、附件与生命周期 |
| `AssistantOverlayController.kt` | 状态机：打开/新建会话、发送与排队、附件、模型切换、重试 |
| `AssistantOverlayScreen.kt` | 根布局：光效、顶部面板、底部输入卡与 insets |
| `AssistantOverlayPanel.kt` | 顶部会话面板：消息、过程、失败重试、交付与提问 |
| `AssistantOverlayComposer.kt` | 底部输入卡：折叠/展开、语音、附件与发送 |
| `AssistantOverlaySwitcher.kt` | 工作区/会话两级切换浮层 |
| `OverlayEffects.kt` | 光效绘制与输入卡玻璃、投影效果 |
| `OverlayGlow.kt` | 光效状态机：按轮次阶段驱动亮度与动画 |
| `AssistantEntry.kt` | 唤起浮层与跳转系统助手设置 |
| `AssistantRole.kt` | 默认助手角色查询与系统默认应用设置入口 |

## 要点

- 是独立 Activity（透明、edge-to-edge），语言在 `attachBaseContext` 包装；关闭浮层不取消正在进行的运行，右上角全屏才切回 `MainActivity`。
- `setContent` 根用 `ProvidePluginManager(graph.pluginManager)` 包住内容，浮层里的自定义消息卡与工具卡才能渲染插件 Compose 槽位（与 `MainActivity` 同源）；输入图片经 `normalizeImages` 带原始附件路径，供插件输入钩子使用。
- 会话状态投影自 `ConversationStore`，不复制真相；运行中发送是插话、长按发送是排队。
- 光效与玻璃输入卡是同一套视觉：`OverlayGlow` 出状态、`OverlayEffects` 负责绘制，别绕开它们另画。
