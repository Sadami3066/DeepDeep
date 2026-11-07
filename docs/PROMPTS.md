# 提示词与工具描述装配（开发参考）

一次会话的系统提示词由 SDK 与 App 两层拼成：SDK 负责 preamble / 项目指令 / skills / cwd 等静态段，App 通过 `RuntimeHooks.extraSystemPromptSections` 每轮提供动态段。工具定义（name/description/参数 schema）不走系统提示词，走模型的 function calling；工具的 `promptGuidelines` 汇总成 `<rules>` 段进提示词。

本目录配套快照：

- `docs/prompts/assembly-chain.md`：装配链全文（逐段字面文本、资源加载、diff 机制、插件/记忆链路）。
- `docs/prompts/tool-inventory.md`：全部工具/Skill 的描述文案清单、HostToolNames 冲突表、rules 输入、18 条问题清单与优先修改项。

## 1. 调用链

```
ChatViewModel / RuntimeRuns.send
 └─ AgentSession.prompt()                       SDK，每次发送
     └─ runPrompt()
         ├─ mcpManager.expandPromptCommand
         ├─ runInputHooks → 插件 inputHandler
         ├─ expandPromptTemplate（cwd/.pi/prompts/*.md）
         ├─ hooks.transformPrompt → 插件 promptTransform
         ├─ syncSystemPromptIfNeeded()           ← 系统提示词在这里重建 + diff
         │   ├─ buildSystemPrompt(...)           静态段（preamble/addendum/project_context/skills/cwd）
         │   ├─ + staticSystemPromptSections     PiSessionEngine 建会话时传入
         │   ├─ + hooks.extraSystemPromptSections()  PiSessionEngine.currentSections()，每轮重算
         │   └─ + mcp_servers
         ├─ maybeCompactBeforePrompt
         └─ agent.prompt(...)
```

首次建会话：`initialSystemPromptSections = currentSections()`；此后每轮只把**变化的段**以 `SystemMessage` patch 追加进 transcript（内容不变则不产生新消息）。恢复历史会话时直接用 JSONL 里回放的 sections，不重新生成——旧版段落会残留到它被删除或改值为止。

## 2. 最终顺序（主会话）

| # | 段 | 来源 | 生成位置 | 会话内 |
|---|---|---|---|---|
| 1 | `preamble`（裸文本） | `agentDir/SYSTEM.md` | `runtime/SystemPrompt.kt` `DEEP_DEEP_SYSTEM_PROMPT` / `ensureSystemPromptFile` | 冻结 |
| 2 | `<addendum>` | `cwd/.pi/APPEND_SYSTEM.md` 或 `agentDir/APPEND_SYSTEM.md` | SDK `ResourceLoader` | 冻结 |
| 3 | `<project_context>` | `AGENTS.override.md` / `AGENTS.md` / `CLAUDE.md`（agentDir + cwd 逐级向上） | SDK `ResourceLoader.loadContextFiles` | 冻结 |
| 4 | `<skills>` | `cwd/.pi/skills` + `agentDir/skills` | SDK `ResourceLoader.loadSkills` + `renderAvailableSkills` | 冻结 |
| 5 | `<cwd>` | 会话 cwd | SDK `buildSystemPrompt` | 冻结 |
| 6 | `<rules>` | 4 条全局规则 + 所有工具的 `promptGuidelines` | `runtime/SystemPrompt.kt` `buildToolRuleSection`，经 `PiSessionEngine` extra 注入 | 工具集变化时 |
| 7 | `<environment>` | 日期、provider/model、默认工作位置、称呼、交互风格 | `runtime/Personalization.kt` | 跨日/切模型 |
| 8 | `<instruction>`（可空） | 用户长期偏好 | `runtime/Personalization.kt` | 设置变化 |
| 9 | `<shared_storage>`（条件） | 共享存储开关 | `runtime/Personalization.kt` | 设置变化 |
| 10 | `<official_plugins>` | 已启用插件的 `promptSections`，按 priority 升序 `\n\n` 拼接（无 per-plugin 标签） | `plugin/manager/PluginHostManager.kt` `promptBody()` | 插件状态变化 |
| 11 | `<memory>` | `MemoryStore.section()` 快照（16K 预算） | `runtime/PiSessionEngine.kt` + `MemoryMarkdown.kt` | 跨天 / 压缩 / 写 curated / 会话开始 |
| 12 | `<paths>` | 技能目录与 App 私有数据目录常量 | `runtime/PiSessionEngine.kt` | 冻结 |
| 13 | `<plugin_context>` | `hooks.systemContext()`（每轮收集） | `PiSessionEngine` + `PluginRuntimeHooksAdapter` | 每轮 |
| 14 | `<mcp_servers>`（条件） | MCP server 状态 | SDK `McpSection`，每轮追加 | 每轮 |

小程序一次性会话在 `<cwd>` 之后多 `<run_lifetime>`，且 `<rules>` 落在最后（`MiniappAgentService`）。

两个关键事实：

- SDK 自带的 `tools` / `rules` / `docs` 三段**永远不会出现**：`PiSessionEngine.createSession` 第一步保证 `agentDir/SYSTEM.md` 存在，SDK 判定「有自定义 preamble」后只填 preamble。
- 合并用 `Map.plus`（LinkedHashMap）语义：同名 key 只覆盖值、不换位置，因此**首次插入顺序决定最终顺序**。

## 3. 内容怎么进来（修改入口）

- **身份文案**：`runtime/SystemPrompt.kt` 的 `DEEP_DEEP_SYSTEM_PROMPT`；设备侧 `agentDir/SYSTEM.md`。`ensureSystemPromptFile` 只覆盖与 `LEGACY_SYSTEM_PROMPTS` 完全一致的旧内容，用户手改过的不动。
- **项目指令**：`agentDir/AGENTS.md`（全局）+ cwd 各级目录的 AGENTS/CLAUDE 文件；同名优先级 `AGENTS.override.md` > `AGENTS.md` > `CLAUDE.md`。
- **Skills**：`cwd/.pi/skills` + `agentDir/skills`（设备上即 `files/agent/skills`）。`SKILL.md` 的 frontmatter：`name`（默认目录名）、`description`（**必填**，空则整条丢弃）、`disable-model-invocation: "true"`（不出现在提示词）。提示词里只有 name/description/location，正文由模型 `read` 加载。插件技能由 `PluginSkillStore` 同步进 `agentDir/skills/<id>`（带 owner marker，卸载时移除）。
- **工具描述**：`CodingTool(name, description, parameters, label, promptSnippet, promptGuidelines, ...)`。`name/description/parameters` 走 function calling；`promptGuidelines` 汇总进 `<rules>`；`promptSnippet` 目前**不会**进上下文（SDK tools 段不生成），属待清理的死配置。
- **插件内容**：`DeepDeepPlugin.promptSections(host)` 返回 `PromptSection(id, body, priority)`，priority 升序拼接成一个 `<official_plugins>`（当前 deepdeep-memory=120、template=100，其余默认 0）；`hooks.systemContext()` 另走 `<plugin_context>`（2s 超时、单插件 8K 字符、总 32K）。
- **记忆**：`MemoryStore.section()` → `<memory>`，重见 `docs/MEMORY.md`。
- **MCP**：工具描述来自 server `tools/list`（≤1024 字符），名字 `mcp__<server>__<tool>`；deferred 工具只进 `tool_search`/`tool_execute` 目录。
- **Personalization**：`environment` 段含当前日期与 UTC 偏移、provider/model、默认工作位置、称呼、交互风格（QUIET/BALANCED/PROACTIVE 及对应指令）；`instruction` 为用户长期偏好，冲突以系统指令为准。

## 4. 现状数字

- 静态工具 **68**：内置 46（34 常驻 + 12 屏幕条件）+ 插件 22（deepdeep-memory 7、interop 4、micloud 7、sample 2、template 2）；MCP 工具动态；另有 SDK 的 3 个 MCP 资源工具。
- 进提示词的 skill：内置 3（`deepdeep-gui` 有条件）+ 插件最多 5（`interop-guide` 缺 frontmatter，实际不出现）。
- `<rules>` 当前约 33–37 行（取决于屏幕工具是否注册）。

## 5. 已定位的问题（先看这些）

完整 18 条见 `docs/prompts/tool-inventory.md` §5。优先处理：

1. `promptSnippet` 是死配置：7 个内置 + 14 个插件工具的 snippet 从未进入上下文。
2. `deepdeep-memory` 插件与 runtime 记忆的 4 个工具同名、两份规则段语义重叠；插件安装会被 `HostToolNames` 整包拒绝。
3. `screen_key` 参数 description 只列 10 个键，enum 实际 15 个（缺 home/recents/notifications/volume_up/volume_down）。
4. `HostToolNames` 过时：保留已丢弃的 `ls/grep/find`，MCP 资源工具名与 SDK 实际不符，冲突校验漏检。
5. rules 段冗余与缺失并存：`edit` 5 处同义、`web_*` 重复引用规范、`root/adb` 共用一条；goal/job/timer/miniapp 16 个工具 0 条 guideline。

## 6. 改描述时的注意事项

- 工具 `description` 写「何时用/何时别用/失败后怎么办」，参数语义放 `parameters.description`，不要两边重复。
- `<rules>` 是**全量拼接**：只有完全相同的整行才被 `LinkedHashSet` 去重，措辞相近的重复条目会实打实占 token；新增 guideline 前先对照 `tool-inventory.md` §4.2 的现状表。
- 冻结段（preamble/addendum/project_context/skills/cwd）在会话创建时读取：改设备文件只影响新会话，老会话沿用 JSONL 回放。
- 动态段变化会触发新的 system patch（前缀缓存失效）；`environment` 含日期，跨日必然变化属预期。
