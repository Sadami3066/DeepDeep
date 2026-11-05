# runtime-api · 运行时契约

纯 JVM 契约模块（无 Android）：定义 `:app` 与 `:runtime` 共用的数据模型、事件流和能力接口，本身不含实现。

## 文件

| 文件 | 职责 |
|---|---|
| `RuntimeTypes.kt` | 核心模型、事件流与 AgentRuntime 五个接口 |
| `ToolPayloads.kt` | 结构化工具结果载荷：Text 或 Blocks（Text/Image base64+mime） |
| `GoalContracts.kt` | 目标阶段、轮次模型与两个驱动接口 |
| `JobContracts.kt` | 后台任务快照、状态与 JobController |
| `Notices.kt` | 回合来源、回合提示与三类带外事件 |
| `PluginContracts.kt` | 插件连接结果与 PluginController |
| `QuestionContracts.kt` | ask_user_question 的题目与选项模型 |
| `UsageModels.kt` | 会话用量与压缩结果 |
| `ModelConfig.kt` | 模型配置：Provider、模型与思考档（全局默认与会话共用） |
| `ProviderDefaults.kt` | 默认 Provider 与模型常量 |

## 要点

- 与 `:runtime` 同包名 `io.github.whywood.deepdeep.runtime`，import 上看不出模块边界；改契约要同时改 `:runtime` 实现和 `:app` 消费方。
- 只依赖 kotlinx-coroutines 与 kotlinx-serialization-json，不引 Android；不要在这里放 Context、IO 或平台实现。
- `RuntimeEvent` 接口在 `RuntimeTypes.kt`，`Notices.kt` 的三个事件也扩展它，新增事件别另起体系。
