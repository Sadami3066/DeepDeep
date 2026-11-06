# conversation · 会话状态投影

runtime 事件经纯函数 `reduce` 变成 `ConversationState`，由 `ConversationStore` 统一持有并投影给聊天页、系统助手浮层、通知与悬浮窗。本包不依赖 Android/Compose，只有数据、纯函数与一个事件订阅者。

## 文件

| 文件 | 职责 |
|---|---|
| `ConversationStore.kt` | 唯一会话状态持有者：单订阅 runtime 事件、按会话投影、LRU、乐观开回合、delta 批处理、忙闲对账、运行完成通知 |
| `ConversationReducer.kt` | `ConversationState` 模型、`reduce()` 事件分派入口与消息/步骤共享工具 |
| `ConversationRunReducer.kt` | 运行生命周期：RunStarted/Finished/Failed、外部 run 接管、后台轮次、失败/重试收敛 |
| `ConversationMessageReducer.kt` | 消息内容：文本/思考增量、工具步骤与结果、插件消息卡 |
| `ConversationInputReducer.kt` | 用户输入：admission、插话/提醒注入、排队输入、通知 |
| `ConversationEdits.kt` | 纯编辑：开回合、步骤折叠、重试调度/恢复/取消 |
| `ConversationStrings.kt` | 会话文案载体 `ConversationStrings` 与运行错误分类 `RunErrorKind` |

## 要点

- 纯函数：除 `ConversationStore` 外不 import Android/Compose/DataStore，不启动协程；时间由调用方传入。
- 单一投影：所有 surface 只观察 `ConversationStore.observe(sessionId)`，输入草稿/附件/滚动留在各 surface，不在 UI 层再归约事件或另存消息/运行态。
- 事件只由 store 订阅一次；新增消费方按用途使用 `activeRuns`、`runCompletions`、`droppedInputs`、`runFailures`、`lifecycle`、`sessionChanges`，不要再自建 `runtime.events.collect`。
- Store 负责 LRU（默认 8，pin 的会话不淘汰）、delta 16ms 合并、忙→闲 500ms 对账和运行完成通知；`onEvict` 用于清理表面层按会话持有的资源。
- 标题：首次发送只在标题为空或仍是默认标题时写入；默认标题按当前语言和英文 fallback 识别。
- 文案：默认英文 `fallback()`；App 侧从 `res/values*/strings_*.xml` 构造后传入。
- 测试镜像本包：`app/src/test/kotlin/.../conversation/`。
