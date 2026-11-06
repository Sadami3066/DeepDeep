# conversation · 会话状态投影

runtime 事件经纯函数 `reduce` 变成 `ConversationState`，供聊天页、系统助手浮层、通知与悬浮窗共用。本包不持有协程或 Android 依赖，只有数据与纯函数。

## 文件

| 文件 | 职责 |
|---|---|
| `ConversationReducer.kt` | `ConversationState` 模型、`reduce()` 事件分派入口与消息/步骤共享工具 |
| `ConversationRunReducer.kt` | 运行生命周期：RunStarted/Finished/Failed、外部 run 接管、后台轮次、失败/重试收敛 |
| `ConversationMessageReducer.kt` | 消息内容：文本/思考增量、工具步骤与结果、插件消息卡 |
| `ConversationInputReducer.kt` | 用户输入：admission、插话/提醒注入、排队输入、通知 |
| `ConversationEdits.kt` | 纯编辑：开回合、步骤折叠、重试调度/恢复/取消 |
| `ConversationStrings.kt` | 会话文案载体 `ConversationStrings` 与运行错误分类 `RunErrorKind` |

## 要点

- 纯函数：不 import Android/Compose/DataStore，不启动协程；时间由调用方传入。
- 单一投影：所有 surface 用同一份 `reduce`，不在 UI 层再实现消息或运行态。
- 文案：默认英文 `fallback()`；App 侧从 `res/values*/strings_*.xml` 构造后传入。
- 测试镜像本包：`app/src/test/kotlin/.../conversation/`。
