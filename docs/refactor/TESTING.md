# 测试约定

只看「测试替身（fake/stub）放在哪、叫什么、怎么共享」。生产代码规则与门禁见 `AGENTS.md`，任务流程见 `PLAYBOOK.md`。

## 放置与命名

| 替身用途 | 位置 | 命名 |
|---|---|---|
| 模块内多个测试共用 | `<module>/src/test/kotlin/…/<domain>/testing/` | `Fake<被替身类型>` |
| 仅单个测试文件使用 | 该测试文件内 `private class` | 同上 |
| runtime 端通用 | `runtime/src/test/kotlin/io/github/whywood/deepdeep/runtime/testing/` | `FakeAgentRuntime`、`DeliveryRecord` |
| app 端聊天相关 | `app/src/test/kotlin/io/github/whywood/deepdeep/ui/chat/testing/` | `FakeAgentRuntime`、`HOME_ID`/`HOME_CWD` |

- 同一模块内同一个被替身接口只保留一份通用 fake；测试差异用可变属性或构造参数表达，不允许复制出新类。
- 新增 fake 前先搜索 `…/testing/` 与同包测试文件，确认没有可扩展的现成实现。

## 行为表达

- 各测试的差异走可变属性（如 `sessions`、`histories`、`usageResult`、`openFailure`），调用记录走 `deliveries`/`openedFiles`/`sent` 这类只读集合。
- 「本测试不应调用」的方法一律 `error("unused")`；失败注入用字段，不写子类。
- fake 不做断言；断言只属于测试用例。

## 硬规则

- 禁止为了让测试好写而在 `src/main` 增加测试钩子、开关或 `@VisibleForTesting` 成员；确有需要先开卡或写 ADR。
- fake 只实现接口，不复制被测实现逻辑；行为差异必须在测试里显式配置。

## 跨模块共享（暂不做）

app 测试复用 runtime 的 fake 需要 Gradle `testFixtures`，本阶段不做。触发条件：同一个 fake 在 ≥2 个模块出现，或第三处复制即将出现时，另开卡把 fake 迁到 `testFixtures` 源集。
