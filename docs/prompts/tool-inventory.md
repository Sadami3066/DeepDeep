> 快照文档：2026-11-07 由只读勘察生成，行号以当日工作区代码为准；代码改动后请以源码为最终依据。

# DeepDeep 工具 / Skill 描述清单（只读勘察）

勘察对象：`/home/whywood/project/DeepDeep/app`（Kotlin + Compose，模块 `app/ runtime/ runtime-api/ design/ shell/ plugin-api/`）、pi coding SDK 0.5.0（`/tmp/pi-src/src`）、插件仓库 `/home/whywood/project/DeepDeep/plugins`。
方法：只读 grep/read（未跑 Gradle，未改源码）。所有 `文件:行号` 以勘察时的磁盘内容为准。

---

## 0. 工具是怎样进入模型上下文的

装配链（自上而下）：

1. `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:177-198` `buildTools()`：
   - `defaultCodingTools(...)` 取 pi SDK 默认工具（`/tmp/pi-src/src/commonMain/dev/pi/coding/tools/CodingTools.kt:6-16`：read、bash、edit、write、grep、find、ls）。
   - `"bash" -> activeShell?.let { add(deepDeepBashTool(it)) }`（`PiSessionEngine.kt:188`）——用 DeepDeep 的 bash 替换 SDK bash；shell 关闭时不注册。
   - `"ls", "grep", "find" -> Unit`（`PiSessionEngine.kt:189`）——三个工具被**直接丢弃**，不进入上下文。
   - `else -> add(tool.withAbsolutePaths())`（`PiSessionEngine.kt:190`）——read/write/edit 被套上绝对路径约束（`WorkspacePaths.kt:26-73`）。
   - `toolContext?.let { addAll(appTools(cwd, context, binding, session)) }`（`PiSessionEngine.kt:193-195`）。
2. `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AppTools.kt:14-24` `appTools()` 顺序：
   `askUserQuestionTool` → `context.pluginTools(binding)`（插件工具）→ `deferredTools(...)`（tool_search、tool_execute）→ `extraToolFactories`（即 `builtInTools`）。
3. `app/src/main/kotlin/io/github/whywood/deepdeep/graph/RuntimeGraph.kt:83-90` 注入 `AppToolContext`：`pluginTools`/`pluginDeferredTools` 来自 `PluginHostManager`，`extraToolFactories = listOf({ _, binding -> builtInTools(binding) })`。
4. `app/src/main/kotlin/io/github/whywood/deepdeep/graph/RuntimeGraph.kt:92-140` `builtInTools()` 按固定顺序 `add(...)`（见下表「注册位置」）。
5. 系统提示词的 `tools` 段落由 `buildToolRuleSection(tools)` 生成（见 §4），工具清单本身由 SDK 序列化成请求里的 `tools` 数组。

因此进入模型上下文的**列表顺序**为：read → bash → edit → write → ask_user_question → 插件工具 → tool_search → tool_execute → present → root_shell/adb_shell → todo_write → web_search → web_fetch → arrange_calendar → get_goal → create_goal → update_goal → job_start → job_output → job_list → job_kill → timer_create → timer_list → timer_update → timer_delete → miniapp_create → miniapp_load → miniapp_list → miniapp_delete → miniapp_edit → memory_write → memory_read → memory_search → memory_status →（开关+权限满足时）screen_* 11 个 → virtual_screen。

---

## 1. 工具清单

图例：`label` 是 SDK `CodingTool.label`，用于 UI/时间线显示；`—` 表示该字段为 null。description 均按源码原样（含 `$CONST` 已展开为字面值）。

### 1.1 文件与 shell（pi 默认 + DeepDeep 覆盖）

| # | 工具 | 定义 | 注册 | label |
|---|---|---|---|---|
| 1 | `read` | `/tmp/pi-src/src/commonMain/dev/pi/coding/tools/ReadTool.kt:46-88`，name:47，desc:48 | `PiSessionEngine.kt:190` → `withAbsolutePaths()` | `read` |
| 2 | `bash` | `runtime/.../DeepDeepBashTool.kt:45-76` | `PiSessionEngine.kt:188`（替换 SDK bash） | `bash` |
| 3 | `edit` | `/tmp/pi-src/src/commonMain/dev/pi/coding/tools/EditTool.kt:49-56` | `PiSessionEngine.kt:190` → `withAbsolutePaths()` | `edit` |
| 4 | `write` | `/tmp/pi-src/src/commonMain/dev/pi/coding/tools/WriteTool.kt:27-34` | `PiSessionEngine.kt:190` → `withAbsolutePaths()` | `write` |

**read** — description（`ReadTool.kt:48`）：

```
Read the contents of a file. Supports text files and images (jpg, png, gif, webp, bmp). Images are sent as attachments. For text files, output is truncated to 2000 lines or 50KB (whichever is hit first). Use offset/limit for large files. When you need the full file, continue with offset until complete.
```

- promptSnippet（`ReadTool.kt:51`）：`Read file contents`
- promptGuidelines（`ReadTool.kt:52`）：`Use read to examine files instead of cat or sed.`
- 参数（`ReadTool.kt:34-44`）：
  - `path`：`Path to the file to read (relative or absolute)` → **被 DeepDeep 覆写**为 `Absolute path in the Android filesystem.`（`WorkspacePaths.kt:32`）
  - `offset`：`Line number to start reading from (1-indexed)`
  - `limit`：`Maximum number of lines to read`
- 追加 guideline（`WorkspacePaths.kt:18-19`，通过 `:49` 拼接在原有 guidelines 之后）：`File tool arguments need explicit absolute paths; the workspace is only a suggested location, not an access boundary.`

**bash** — description（`DeepDeepBashTool.kt:47`）：

```
Execute a command using the Linux /bin/sh shell in the current working directory. Returns merged stdout and stderr, ending with the exit code as [exit: N]. Output is truncated to the last 2000 lines or 50KB (whichever is hit first). Oversized output is saved to a complete log when available. Cancellation stops execution and is not retried. Optionally provide a timeout in seconds.
```

- label：`bash`（`:49`）
- promptSnippet：—（无）
- promptGuidelines（`:50-53`）：
  - `Check the [exit: N] marker on every bash result; investigate failures before moving on.`
  - `bash runs Linux /bin/sh; shared work files keep the same absolute paths as the file tools.`
- 参数（`:28-43`）：
  - `command`：`Shell command to execute`（required）
  - `timeout`：`Timeout in seconds (1-600, optional, default 60)`（minimum 1 / maximum 600）

**edit** — description（`EditTool.kt:51`）：

```
Edit a single file using exact text replacement. Every edits[].oldText must match a unique, non-overlapping region of the original file. If two changes affect the same block or nearby lines, merge them into one edit instead of emitting overlapping edits. Do not include large unchanged regions just to connect distant changes.
```

- promptSnippet（`:54`）：`Make precise file edits with exact text replacement, including multiple disjoint edits in one call`
- promptGuidelines（`:55-60`）：
  - `Use edit for precise changes (edits[].oldText must match exactly)`
  - `When changing multiple separate locations in one file, use one edit call with multiple entries in edits[] instead of multiple edit calls`
  - `Each edits[].oldText is matched against the original file, not after earlier edits are applied. Do not emit overlapping or nested edits. Merge nearby changes into one edit.`
  - `Keep edits[].oldText as small as possible while still being unique in the file. Do not pad with large unchanged regions.`
- 参数（`:18-48`）：
  - `path`：`Path to the file to edit (relative or absolute)`（被覆写为绝对路径，见上）
  - `edits`：`One or more targeted replacements. Each edit is matched against the original file, not incrementally. Do not include overlapping or nested edits. If two changes touch the same block or nearby lines, merge them into one edit instead.`
  - `edits[].oldText`：`Exact text for one targeted replacement. It must be unique in the original file and must not overlap with any other edits[].oldText in the same call.`
  - `edits[].newText`：`Replacement text for this targeted edit.`

**write** — description（`WriteTool.kt:29`）：

```
Write content to a file. Creates the file if it doesn't exist, overwrites if it does. Automatically creates parent directories.
```

- promptSnippet（`:32`）：`Create or overwrite files`
- promptGuidelines（`:33`）：`Use write only for new files or complete rewrites.`
- 参数（`:12-24`）：
  - `path`：`Path to the file to write (relative or absolute)`（被覆写为绝对路径）
  - `content`：`Content to write to the file`

### 1.2 runtime 内置（`RuntimeGraph.builtInTools`）

| # | 工具 | 定义 | 注册位置 | label | 何时可用 |
|---|---|---|---|---|---|
| 5 | `ask_user_question` | `runtime/.../AskUserQuestionTool.kt:79-109` | `AppTools.kt:20`（不在 builtInTools） | `ask_user_question` | 总是 |
| 6 | `tool_search` | `runtime/.../DeferredTools.kt:70-147` | `AppTools.kt:22` → `DeferredTools.kt:62-65` | `搜索按需工具` | 总是 |
| 7 | `tool_execute` | `runtime/.../DeferredTools.kt:149-204` | 同上 | `执行按需工具` | 总是 |
| 8 | `present` | `runtime/.../PresentTool.kt:100-126` | `RuntimeGraph.kt:93` | `present` | 总是 |
| 9 | `root_shell` | `runtime/.../RootShellTool.kt:76-90` | `RuntimeGraph.kt:96-111` | `root_shell` | 仅 `availableShellBackends()` 含 ROOT，且 `ROOT in availableBackends` |
| 10 | `adb_shell` | `runtime/.../PrivilegedShellTool.kt:70-84` | `RuntimeGraph.kt:113-119` | `adb_shell` | 仅含 ADB 档，且 `ADB in availableBackends` |
| 11 | `todo_write` | `runtime/.../TodoTools.kt:55-91` | `RuntimeGraph.kt:124-127` | `todo_write` | 总是 |
| 12 | `web_search` | `app/.../web/WebTools.kt:67-85` | `RuntimeGraph.kt:128` | `web_search` | 总是 |
| 13 | `web_fetch` | `app/.../web/WebTools.kt:87-104` | 同上 | `web_fetch` | 总是 |
| 14 | `arrange_calendar` | `app/.../arrange/ArrangeTools.kt:130-265` | `RuntimeGraph.kt:129` | `arrange_calendar` | 总是（无日历权限时执行期报错） |
| 15 | `get_goal` | `runtime/.../GoalTools.kt:66`（变体） | `RuntimeGraph.kt:130` | `get_goal` | 总是 |
| 16 | `create_goal` | `runtime/.../GoalTools.kt:67` | 同上 | `create_goal` | 总是（执行期要求用户直接请求） |
| 17 | `update_goal` | `runtime/.../GoalTools.kt:68` | 同上 | `update_goal` | 总是 |
| 18 | `job_start` | `runtime/.../JobTools.kt:62` | `RuntimeGraph.kt:131` | `job_start` | 总是 |
| 19 | `job_output` | `runtime/.../JobTools.kt:63` | 同上 | `job_output` | 总是 |
| 20 | `job_list` | `runtime/.../JobTools.kt:64` | 同上 | `job_list` | 总是 |
| 21 | `job_kill` | `runtime/.../JobTools.kt:65` | 同上 | `job_kill` | 总是 |
| 22 | `timer_create` | `runtime/.../TimerTools.kt:85` | `RuntimeGraph.kt:132` | `timer_create` | 总是 |
| 23 | `timer_list` | `runtime/.../TimerTools.kt:86` | 同上 | `timer_list` | 总是 |
| 24 | `timer_update` | `runtime/.../TimerTools.kt:87` | 同上 | `timer_update` | 总是 |
| 25 | `timer_delete` | `runtime/.../TimerTools.kt:88` | 同上 | `timer_delete` | 总是 |
| 26 | `miniapp_create` | `runtime/.../MiniappTools.kt:72-98` | `RuntimeGraph.kt:133` | `miniapp_create` | 总是 |
| 27 | `miniapp_load` | `runtime/.../MiniappTools.kt:100-128` | 同上 | `miniapp_load` | 总是（`supportsChatDelivery` 影响返回的 next 文案） |
| 28 | `miniapp_list` | `runtime/.../MiniappTools.kt:130-172` | 同上 | `miniapp_list` | 总是 |
| 29 | `miniapp_delete` | `runtime/.../MiniappTools.kt:174-194` | 同上 | `miniapp_delete` | 总是 |
| 30 | `miniapp_edit` | `runtime/.../MiniappTools.kt:234-253` | `RuntimeGraph.kt:134` | `miniapp_edit` | 总是 |
| 31 | `memory_write` | `runtime/.../memory/MemoryTools.kt:89-120` | `RuntimeGraph.kt:135` | `Save memory` | 总是 |
| 32 | `memory_read` | `runtime/.../memory/MemoryTools.kt:122-145` | 同上 | `Read memory` | 总是 |
| 33 | `memory_search` | `runtime/.../memory/MemoryTools.kt:147-173` | 同上 | `Search memory` | 总是 |
| 34 | `memory_status` | `runtime/.../memory/MemoryTools.kt:175-194` | 同上 | `Memory status` | 总是 |
| 35-45 | `screen_*` 11 个 | `app/.../screen/ScreenTools.kt:133-145` | `RuntimeGraph.kt:136-139` | 同名 | `isScreenOpsActive(...)`：设置开关开 **且** 生效档为 ADB/ROOT（`RuntimeGraph.kt:259-260`） |
| 46 | `virtual_screen` | `app/.../vscreen/VirtualScreenFeature.kt:113-135`（常量 `:22-28`） | `RuntimeGraph.kt:138` | `virtual_screen` | 同上 |

#### ask_user_question

- description（`AskUserQuestionTool.kt:81`）：

```
Ask the user one to five structured questions and wait for answers. Use options for choices; omit options for free-form input. Append (Recommended) to a recommended label. Ask only when a real choice or missing fact blocks progress.
```

- promptSnippet：—；promptGuidelines：—（`CodingTool` 未传）
- 参数（`:22-77`）：
  - `questions`：`Questions to ask the user, one to five entries`
  - `questions[].id`：`Unique short id for this question, echoed back in the answers`
  - `questions[].header`：`Short header shown above the question, at most a few words`
  - `questions[].question`：`The question text`
  - `questions[].multiSelect`：`Whether the user may pick more than one option (default false)`
  - `questions[].options`：`Selectable options, at most eight. Omit to let the user type a free-form answer.`
  - `questions[].options[].label`：`Option label; append (Recommended) to mark the recommended option`
  - `questions[].options[].description`：`Optional one-line explanation`

#### tool_search

- description（`DeferredTools.kt:76`）：

```
Search deferred tools from MCP servers and plugins. Returns tool names and parameters; call tool_execute to run one.
```

- promptSnippet（`:101`）：`Use tool_search to discover deferred MCP or plugin tools, then tool_execute to call one.`
- promptGuidelines（`:102-105`）：
  - `Deferred tools are not callable directly; search first, then execute by name.`
  - `Do not guess deferred tool names without calling tool_search first.`
- 参数（`:77-99`）：
  - `query`：`What the tool should do.`
  - `limit`：`Maximum number of tools to return (1-8).`

#### tool_execute

- description（`DeferredTools.kt:155`）：`Execute one deferred MCP or plugin tool found with tool_search.`
- promptSnippet（`:180`）：`Run a deferred tool by name after finding it with tool_search.`
- promptGuidelines（`:181`）：`Unknown names fail; call tool_search before tool_execute.`
- 参数（`:156-178`）：
  - `name`：`Deferred tool name returned by tool_search.`
  - `arguments`：`Arguments for the deferred tool.`

#### present

- description（`PresentTool.kt:105`）：

```
Deliver one to four finished works to the user: files, images, videos, web pages, or installed miniapps. Chat shows a delivery card for every item; images and web pages render inline, videos show a cover that opens a player.
```

- promptSnippet：—；promptGuidelines：—
- 参数（`:52-98`）：
  - `items`：`One to four finished works to deliver to the user`
  - `items[].type`：`file: any generated file; image: picture shown inline; video: clip shown with a cover and player; html: web page shown inline; miniapp: installed mini app`
  - `items[].path`：`For file, image, video and html: explicit absolute path in the Android filesystem`
  - `items[].id`：`For miniapp: miniapp id, from miniapp_load`
  - `items[].title`：`Optional display title`

#### root_shell

- description（`RootShellTool.kt:82`）：

```
Run a shell command with root privileges and return its exit code, stdout and stderr. Use only when the user explicitly asks for a system-level operation that needs root; prefer the regular bash tool otherwise.
```

- promptSnippet：—；promptGuidelines（`PrivilegedShellTool.kt:50`）：`System shell output may already be truncated upstream; never claim a complete log when none is available.`
- 参数（`RootShellTool.kt:39-52`）：
  - `command`：`Shell command to run with root privileges`
  - `timeoutMs`：`Maximum run time in milliseconds (default 15000, clamped to 1..120000)`

#### adb_shell

- description（`PrivilegedShellTool.kt:76`）：

```
Run a shell command with Android shell privileges and return its exit code and combined output. On rooted devices the command can elevate with su.
```

- promptGuidelines（`:50`）：同 `root_shell` 的 `SYSTEM_SHELL_GUIDELINE`
- 参数（`:18-31`）：
  - `command`：`Shell command to run with Android shell privileges`
  - `timeoutMs`：`Maximum run time in milliseconds (default 15000, clamped to 1..120000)`

#### todo_write

- description（`TodoTools.kt:60`）：`Update the task list for this session. Send the complete list every time; omitted items are removed.`
- promptSnippet（`:63`）：`todo_write: maintain the session task list with pending, in-progress and completed items.`
- promptGuidelines（`:64-67`）：
  - `Use todo_write for multi-step work so the user can follow progress.`
  - `Send the complete list on every call; keep at most one item in_progress.`
- 参数（`:23-53`）：
  - `todos`：`The complete task list, replacing the previous list.`
  - `todos[].content`：`Short task description.`
  - `todos[].status`：无 description（enum pending / in_progress / completed）

#### web_search

- description（`WebTools.kt:69`）：`Search the web for current information and return ranked results with title, URL and snippet.`
- promptSnippet：—；promptGuidelines（`:72-75`）：
  - `Cite the source URLs you used when answering with web results.`
  - `Search before answering questions about current events or facts you do not know.`
- 参数（`:26-45`）：
  - `query`：`Search query. Supports operators such as site:, -site:, quoted phrases, lang:, after: and before:.`
  - `maxResults`：`Maximum number of results to return.`

#### web_fetch

- description（`WebTools.kt:89`）：`Fetch one web page and return its main content as Markdown.`
- promptGuidelines（`:92`）：`Cite the source URLs you used when answering with web results.`
- 参数（`:47-63`）：
  - `url`：`HTTP or HTTPS URL of the page to fetch.`
  - `maxChars`：`Maximum number of content characters to return.`

#### arrange_calendar

- description（`ArrangeTools.kt:135`）：`Read, create, update or delete calendar events in the phone calendar.`
- promptSnippet：—；promptGuidelines（`:138`）：`Calendar writes change the user's schedule; confirm with the user before creating, updating or deleting an event.`
- 参数（`:40-120`）：
  - `action`：`calendars lists writable calendars; list queries events in a time range; get reads one event; create, update and delete change calendar events.`
  - `id`：`Calendar event id for get, update or delete.`
  - `calendarId`：`Target calendar id from action=calendars. Defaults to the primary writable calendar.`
  - `title`：`Event title for create, or a new title for update.`
  - `start`：`Event start (create/update) or range start (list). Accepts yyyy-MM-dd HH:mm, yyyy-MM-dd, ISO 8601 or epoch milliseconds.`
  - `end`：`Event end (create/update) or range end (list), same formats as start. Defaults to one hour after start.`
  - `allDay`：`Whether the event lasts the whole day; defaults to false.`
  - `location`：`Optional event location; empty string clears it on update.`
  - `description`：`Optional event notes; empty string clears them on update.`
  - `reminders`：`Alerts in minutes before the event. Pass an empty array on update to remove existing reminders.`
  - `query`：`Optional title filter for action=list.`
  - `limit`：`Maximum events for action=list, 1-200. Defaults to 50.`

#### goal（get_goal / create_goal / update_goal）

三个具名变体由 `toolVariant(...)`（`runtime/.../ToolVariants.kt:17-63`）从同一个 `goalTool`（`GoalTools.kt:72-98`）派生：**name 用变体名，label=变体名，description 用变体 description，参数只保留 fields 子集**；`goalTool` 自己的 description 与 name 不会进入上下文。三个变体都**没有 promptSnippet / promptGuidelines**。

- `get_goal`（`GoalTools.kt:66`）：`Read this conversation's goal, its id, revision, state and round progress.`；参数：空 properties、无 required。
- `create_goal`（`:67`）：`Create and arm a concrete goal when the user requests it. Omit maxRounds for the configured default.`；参数：`objective`、`maxRounds`（required: objective）。
- `update_goal`（`:68`）：`Update the current goal using its exact id and revision from get_goal. edit/pause/resume require a user request; complete requires achieved work; blocked requires the same blocker across three goal rounds.`；参数：`goalId`、`revision`、`action`（enum edit/pause/resume/complete/blocked）、`objective`、`maxRounds`、`blockedReason`（required: goalId、revision、action）。
- 参数 description（`GoalTools.kt:27-57`）：
  - `action`：`get, create, edit, pause, resume, complete or blocked`
  - `goalId`：`Id of the current goal from get_goal; required for change actions`
  - `revision`：`Current goal revision from get_goal; required for change actions`
  - `objective`：`Concrete objective, required for create and optional for edit`
  - `maxRounds`：`Optional maximum goal rounds, 1-50; omit for unlimited`
  - `blockedReason`：`Concrete blocker description, required for action=blocked`

#### job（4 个变体，`JobTools.kt:59-67`）

| 工具 | description |
|---|---|
| `job_start` | `Start a long-running shell command in this conversation and return its job id immediately.` |
| `job_output` | `Read output since the previous read and the current status. wait=true waits for new output or completion; timeout leaves the job running.` |
| `job_list` | `List this conversation's running and finished background jobs.` |
| `job_kill` | `Request cancellation of a background job. Read job_output to confirm its final status.` |

- 参数保留：job_start: command,label；job_output: jobId,wait,timeoutMs；job_list: 无；job_kill: jobId,reason。
- 全部**无 promptSnippet / promptGuidelines**。
- 参数 description（`:23-57`）：
  - `action`：`start, output, list or kill`
  - `command`：`Shell command to run in the background, required for start`
  - `label`：`Short label shown in the jobs list (defaults to the command)`
  - `jobId`：`Background job id from start or list; required for output and kill`
  - `wait`：`For output: wait for new output or job completion instead of returning immediately`
  - `timeoutMs`：`For output with wait: maximum wait in milliseconds (default 30000, clamped to 1..600000)`
  - `reason`：`Optional reason recorded on the job for kill`

#### timer（4 个变体，`TimerTools.kt:81-90`）

| 工具 | description |
|---|---|
| `timer_create` | `Create a timer in this conversation. once needs date/time, cron needs a five-field expression. enabled defaults to true; false creates a disabled timer.` |
| `timer_list` | `List timers in this conversation with their ids, trigger details, enabled state and delivery results.` |
| `timer_update` | `Update only supplied timer fields using an id from timer_list. The conversation cannot change.` |
| `timer_delete` | `Delete a timer by its id from timer_list.` |

- 全部**无 promptSnippet / promptGuidelines**。
- 参数 description（`:36-79`）：
  - `action`：`create, list, update or delete`
  - `id`：`Timer id, required for update and delete`
  - `prompt`：`Message sent to the current conversation when the timer fires`
  - `frequency`：`Trigger kind: once for a single date and time, cron for a five-field cron expression`
  - `name`：`Display name for the timer (defaults to the prompt preview)`
  - `date`：`Date in YYYY-MM-DD format, required for once`
  - `time`：`Local time in HH:mm 24-hour format, required for once`
  - `cron`：`Five-field cron expression in local time: minute hour day-of-month month day-of-week, e.g. 0 9 * * 1-5`
  - `enabled`：`Whether the timer is enabled`

#### miniapp（5 个）

| 工具 | description | label |
|---|---|---|
| `miniapp_create` | `Create a miniapp draft: a self-contained HTML/CSS/JS folder the user can open on the phone. Returns the draft folder where you write the app files; call miniapp_load afterwards to install it.` | `miniapp_create` |
| `miniapp_load` | `Install a miniapp draft so the user can open it. Call it with the absolute draft path returned by miniapp_create after writing all app files; relative paths are rejected.` | `miniapp_load` |
| `miniapp_list` | `List installed miniapps and the current drafts with their absolute folder paths.` | `miniapp_list` |
| `miniapp_delete` | `Delete an installed miniapp together with all of its saved data. Use it when the user asks to remove a mini app; this cannot be undone.` | `miniapp_delete` |
| `miniapp_edit` | `Read or edit one JSON value in a miniapp's persisted data layer (not its code): action get reads a key, set writes or overwrites it, delete removes it, keys lists all keys.` | `miniapp_edit` |

- 5 个全部**无 promptSnippet / promptGuidelines**。
- miniapp_create 参数（`:19-36`）：`name`：`Display name of the mini app, 1-40 characters`；`description`：`Short description shown in the app list, at most 300 characters`；`icon`：`Optional emoji icon, at most 8 characters`
- miniapp_load 参数（`:38-47`）：`path`：`Absolute path of the draft folder returned by miniapp_create; relative paths are rejected`
- miniapp_delete 参数（`:54-63`）：`app`：`miniapp id, from miniapp_list`
- miniapp_edit 参数（`:205-229`）：`action`：`Operation: get reads one key, set writes it, delete removes it, keys lists all keys`；`app`：`miniapp id, from miniapp_list`；`key`：`Data key for get, set and delete: 1-64 lowercase letters, digits, '-' or '_', starting with a letter or digit`；`value`：`JSON value to store for action set, at most 256KB`（无 type）

#### memory（4 个）

| 工具 | description | label |
|---|---|---|
| `memory_write` | `Save something to long-term memory. Curated files hold current understanding; daily holds raw events. Use append by default, read before revising, and never drop unrelated content when replacing.` | `Save memory` |
| `memory_read` | `Read a memory file, one section of it, or one day of the daily log. Use it before revising curated memory.` | `Read memory` |
| `memory_search` | `Search long-term memory (profile, context, playbook and daily logs) by keyword. Use it before answering questions about past sessions, prior decisions or user preferences, and cite what you find instead of guessing.` | `Search memory` |
| `memory_status` | `Show long-term memory statistics: file sizes, active projects, daily log count, search index state and snapshot state.` | `Memory status` |

- promptSnippet：
  - `memory_write`：`Save durable understanding or session events to long-term memory.`
  - `memory_read`：`Read long-term memory files or sections.`
  - `memory_search`：`Search long-term memory by keyword.`
  - `memory_status`：`Show long-term memory statistics.`
- promptGuidelines（`MemoryTools.kt:96-102 / 128-131 / 154-157 / 181`）见 §4.2 汇总。
- 参数：
  - `memory_write`：`target`：`Where to write. profile: durable facts about the user, you, how you collaborate and the environment. context: goals, active projects and commitments. playbook: methods that work and lessons learned. daily: raw record of what happened on a day.`；`content`：`Markdown to save. Keep entries short. Understanding you infer must cite its evidence and any expiry condition.`；`mode`：`How to write. append (default) adds new content. replace_section replaces one section body and requires section. overwrite rewrites the whole curated file and should be used only after memory_read.`；`section`：`Section title for replace_section or for appending inside a section, such as 用户 or 项目名, with or without leading ##.`；`date`：`Daily log date as yyyy-MM-dd. Only for target=daily; defaults to today.`
  - `memory_read`：`target`：`Which memory file to read: profile, context, playbook or daily.`；`section`：`When set, return only this section body.`；`date`：`Daily log date as yyyy-MM-dd; defaults to today.`
  - `memory_search`：`query`：`Keyword query, Chinese or English.`；`limit`：`Maximum results from 1 to 25; default 5.`
  - `memory_status`：无参数。

### 1.3 屏幕工具（11 + 1）

定义文件 `app/src/main/kotlin/io/github/whywood/deepdeep/screen/ScreenTools.kt:133-145`（`toolsForScreen`），外层 `screenTools`（`:65-115`）为每个工具**额外注入 `target` 参数**（enum `virtual`/`main`，default `virtual`，无 description，`:78-82`），并追加 guidelines（`:126-131`）。

| 工具 | description（源码行） |
|---|---|
| `screen_status` | `Check screen state, recovery status and available operations. Main screen nodes, Unicode field input and screenshots require accessibility; virtual coordinate gestures and screenshots use its own connection.`（`:511`） |
| `screen_observe` | `Read the selected screen as nodes. Filters combine with AND. Refs are re-resolved by node identity across ordinary operations; a restarted screen invalidates them. scanned counts visited nodes; complete=false means unsearched nodes remain, even when there are no matches.`（`:522`） |
| `screen_screenshot` | `Save the selected screen as a PNG. Main screen capture requires accessibility and Android 11+. Protected or locked screens may refuse capture. Returns one absolute path usable by file tools, shell and present.`（`:543`） |
| `screen_tap` | `Tap or long-press a node or a point on a screen. Use it to activate, open or long-press a visible control; provide exactly one of ref, text, desc, resourceId, class, or both x and y.`（`:571`） |
| `screen_swipe` | `Swipe between two points on a screen. Use it for gestures such as dismissing a card; coordinates are screen pixels and both points must lie inside the current screen.`（`:586`） |
| `screen_scroll` | `Scroll a screen or a scrollable node in one direction. Use it to reveal content outside the current view; down or up reveals later or earlier content, right or left reveals horizontal content, with an optional number of steps.`（`:605`） |
| `screen_type` | `Type into the selected screen. Unicode text needs accessibility and a ref or fieldText. ASCII without a field uses the virtual helper or authorized main system input. verified reports field readback.`（`:621`） |
| `screen_key` | `Press a screen key: back, enter, delete, tab, escape, space or arrows. Home, recents, notifications and volume keys are main-only. Availability depends on accessibility and authorized ADB/Root; inspect screen_status.`（`:646`） |
| `screen_wait` | `Wait until a package or text appears on a screen, or a text disappears, and report whether it happened before the timeout with the current window. Use it when waiting for a screen transition or loading state to finish.`（`:660`） |
| `screen_list_apps` | `List installed apps that can be opened. Does not require a running virtual screen.`（`:697`） |
| `screen_launch` | `Open an app on the selected screen by package name. Virtual needs an online virtual screen. Refuses to move an app from another screen; reports whether launch was confirmed on the requested screen.`（`:721`） |
| `virtual_screen` | `Manage a virtual screen that runs apps in the background without taking over the visible phone screen: start, stop or check it. Returns the screen state, its size, a rebuild notice or failure details.`（`VirtualScreenFeature.kt:24-25`） |

- promptSnippet：全部为 null（`ScreenTools.kt:74-83` 只传 `promptGuidelines`；测试 `ScreenToolsTest.kt:99` 明确断言不带 snippet）。
- promptGuidelines：每个 `screen_*` 都含
  - `Screen tools default to the virtual screen; pass target=main explicitly for the phone display, and never switch screens after a failure.`
  - `After a cancellation or timeout, observe the selected screen before retrying an action.`
  - 仅 `screen_observe` 追加：`Observe the screen before node operations and verify the result afterward; refs keep resolving by node identity across ordinary operations, and only a restarted screen invalidates them.`
  - 仅 `screen_launch` 追加：`After screen_launch, observe the target screen before interacting.`
  - `virtual_screen`：`virtual_screen only manages the virtual screen lifecycle; use the screen_* tools to operate on it.`（`VirtualScreenFeature.kt:27-28`）
- 参数 description 摘要（完整原文）：
  - `target`（所有工具）：**无 description**
  - `screen_observe`：`maxDepth`：`Maximum node depth to include, 1-20. Defaults to 20, which covers the full visible hierarchy; lower it only to trim very large trees.`；`maxNodes`：`Maximum number of nodes to include, 1-400. Defaults to 400.`（源码用 `$GUI_MAX_DEPTH=20`、`$GUI_MAX_NODES=400` 插值，`ScreenTools.kt:298,304`）；`packageName`：`Optional package whose window to read; other windows on that screen are then ignored for node reads and an error lists the visible windows when it is absent.`；`text`：`Keep only nodes whose visible text or content description contains this value, ignoring case, spaces and separators.`；`desc`：`Keep only nodes whose content description contains this value, ignoring case, spaces and separators.`；`resourceId`：`Keep only nodes with this view id, either full form (com.app:id/ok) or short form (ok).`；`class`：`Keep only nodes with this view class, either full form (android.widget.Button) or simple form (Button).`；`limit`：`Maximum nodes to return, 1-50. Omit to return all matches.`
  - `screen_tap`：`ref`：`Node ref from screen_observe on the same target screen; it is re-resolved by node identity when the node moves.`；`text`：`Visible text or content description to match case-insensitively as a substring, ignoring spaces and separators.`；`desc`：`Content description to match case-insensitively as a substring, ignoring spaces and separators.`；`resourceId`：`View resource id, either full form (com.app:id/ok) or short form (ok).`；`class`：`View class name, either full form (android.widget.Button) or simple form (Button).`；`x`：`Screen x coordinate in pixels; requires y and must lie inside the current screen.`；`y`：`Screen y coordinate in pixels; requires x and must lie inside the current screen.`；`durationMs`：`Press duration in milliseconds, 0-3000. Values of 400 or more are treated as a long press.`
  - `screen_swipe`：`x1`：`Start x coordinate in pixels.`；`y1`：`Start y coordinate in pixels.`；`x2`：`End x coordinate in pixels.`；`y2`：`End y coordinate in pixels.`；`durationMs`：`Swipe duration in milliseconds, 50-5000. Defaults to 300.`
  - `screen_scroll`：`direction`：`Scroll direction: down reveals later or lower content, up reveals earlier or upper content, right reveals content to the right, left reveals content to the left.`；`ref`：`Optional ref of a scrollable node from the latest screen_observe on the same screen; defaults to the whole screen.`；`amount`：`Number of swipe steps, 1-10. Defaults to 1.`
  - `screen_type`：`text`：`Text to type. Text with non-ASCII characters needs accessibility plus a ref or fieldText.`；`ref`：`Ref of the editable node from screen_observe on the same target screen; supports any Unicode text.`；`fieldText`：`Visible text or content description of the editable node when no ref is known; requires accessibility.`
  - `screen_key`：`key`：`Key name: back, enter, delete, tab, escape, space, arrow_up, arrow_down, arrow_left, arrow_right`（enum 里却包含 home/recents/notifications/volume_up/volume_down，见 §5）
  - `screen_wait`：`forPackage`：`Wait until this package owns the current window.`；`forText`：`Wait until this text appears in a node, a content description or the window title.`；`forTextGone`：`Wait until this text is gone from nodes, content descriptions and the window title.`；`timeoutMs`：`Maximum wait in milliseconds, 100-60000. Defaults to 5000.`
  - `screen_launch`：`packageName`：`Android package name from screen_list_apps; opens on the selected screen.`
  - `virtual_screen`：`action`：`start opens the background screen, stop closes it and its apps, status reports whether it is running.`

### 1.4 Skill 相关

无独立「skill 工具」：skill 由 `SkillCatalog` 渲染进系统提示词（见 §3），并在 `ShellIntake.kt:234` 作为 `skillExpander` 供输入框 `/skill` 使用。

### 1.5 插件工具（`plugins/`）

进入方式：`PluginHostManager.tools(binding)`（`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/manager/PluginHostManager.kt` / `PluginSessionHooks.kt:39-46`）把每个**已安装且已启用**插件的 `Contributions.tools` 中 `exposure == DIRECT` 的工具转成 `CodingTool`，经 `AppTools.kt:21` 加在 `ask_user_question` 之后；`DEFERRED` 的只进 `tool_search` 目录（`PluginSessionHooks.kt:48-57`）。工具按名字 `distinctBy`，先到先得。

| 插件 | 工具 | 定义 | label | 进入方式 |
|---|---|---|---|---|
| deepdeep-memory | `memory_write` | `MemoryTools.kt:29-61`（name:30） | `Memory Write` | DIRECT |
| | `scratchpad` | `MemoryTools.kt:96-126`（name:97） | `Scratchpad` | DIRECT |
| | `memory_read` | `MemoryTools.kt:171-199`（name:172） | `Memory Read` | DIRECT |
| | `memory_forget` | `MemoryTools.kt:228-262`（name:229） | `Memory Forget` | DIRECT |
| | `memory_restore` | `MemoryTools.kt:316-331`（name:317） | `Memory Restore` | DIRECT |
| | `memory_search` | `MemoryTools.kt:371-391`（name:372） | `Memory Search` | DIRECT |
| | `memory_status` | `MemoryTools.kt:407-416`（name:408） | `Memory Status` | DIRECT |
| interop | `interop_echo` | `InteropTools.kt:27-43` | `Interop Echo` | DIRECT（`:32`） |
| | `interop_status` | `InteropTools.kt:46-60` | `Interop Status` | DIRECT |
| | `interop_post_card` | `InteropTools.kt:62-92` | `Interop Post Card` | DIRECT |
| | `interop_send_message` | `InteropTools.kt:94-119` | `Interop Send Message` | **DEFERRED**（`:117`） |
| micloud | `mi_cloud_account` | `MiCloudTools.kt:72-76` | `mi_cloud_account` | DIRECT |
| | `mi_cloud_notes` | `MiCloudNotesTools.kt:21-24` | `mi_cloud_notes` | DIRECT |
| | `mi_cloud_todo` | `MiCloudTodoTools.kt:22-25` | `mi_cloud_todo` | DIRECT |
| | `mi_cloud_sms` | `MiCloudMessagesTools.kt:10-13` | `mi_cloud_sms` | DIRECT |
| | `mi_cloud_contacts` | `MiCloudContactsTools.kt:11-14` | `mi_cloud_contacts` | DIRECT |
| | `mi_cloud_gallery` | `MiCloudMediaTools.kt:11-14` | `mi_cloud_gallery` | DIRECT |
| | `mi_cloud_recordings` | `MiCloudMediaTools.kt:53-56` | `mi_cloud_recordings` | DIRECT |
| sample | `sample_echo` | `SamplePlugin.kt:47-63` | `Sample Echo` | DIRECT |
| | `sample_card` | `SamplePlugin.kt:65-...` | `Sample Card` | DIRECT |
| template | `template_echo` | `TemplatePlugin.kt:148-178` | `Template Echo` | DIRECT |
| | `template_session` | `TemplatePlugin.kt:180-...` | `Template Session Info` | DIRECT |
| feishu / wecom / jwxt | **无工具** | `provides.tools = []`（各 `plugin.json`） | — | 只提供 skill + prompt section |

description 原文（工具自身 `description` 字段）：

```
memory_write: Persist information to long-term memory files. target=long_term writes to MEMORY.md (durable decisions, preferences, facts). target=daily appends to today's daily log (day-to-day notes and running context). mode=append adds to the end; mode=overwrite replaces MEMORY.md entirely.
scratchpad: Manage the working-memory scratchpad: add items to keep in mind, mark them done or undo, clear completed items, or list current items.
memory_read: Read memory files: long_term (MEMORY.md), scratchpad, daily (a specific date, default today), or list (memory inventory).
memory_forget: Delete matching entries from memory while keeping a recovery copy. target=long_term removes matching blocks from MEMORY.md; target=daily removes matching blocks from a daily log; target=scratchpad removes matching scratchpad lines. Default target is long_term.
memory_restore: Restore a previously forgotten memory block using the recovery ID returned by memory_forget. Already-present entries are skipped.
memory_search: Search all memory files (MEMORY.md, SCRATCHPAD.md, daily logs) with keyword BM25 ranking and return matching snippets.
memory_status: Report memory inventory, search index state, snapshot state, and session-summary configuration.
interop_echo: Echo the given text back and return call details.
interop_status: Return a JSON snapshot of the plugin host and every plugin service.
interop_post_card: Post an interop.card custom message into the current session chat.
interop_send_message: Send a chat message through ChatService: display an interop.card without a turn, or start a plugin-triggered turn.
mi_cloud_account: Read the signed-in Xiaomi Cloud account profile or storage quota. Read-only.
mi_cloud_notes: List, read, create, update or delete notes in Xiaomi Cloud. Reads and writes the user's own notes; writes are only allowed when the notes write switch is on.
mi_cloud_todo: List, create, update or delete to-do items in Xiaomi Cloud. Reads and writes the user's own to-do list; writes are only allowed when the to-do write switch is on.
mi_cloud_sms: List recent SMS threads (optionally with call records) from Xiaomi Cloud. Read-only.
mi_cloud_contacts: List or search contacts in Xiaomi Cloud. Read-only.
mi_cloud_gallery: List Xiaomi Cloud gallery photos/videos or albums. Read-only.
mi_cloud_recordings: List or download call recordings stored in Xiaomi Cloud. Downloads are saved into the plugin data directory.
sample_echo: Echo the given text back.
sample_card: Post a sample plugin card into the current chat.
template_echo: Echo text back in two steps, streaming one partial update before the final result.
template_session: Return the active session id and working directory; reports when no session is currently bound.
```

promptSnippet（插件侧全量；`null` 表示未设置）：

| 工具 | promptSnippet |
|---|---|
| memory_write | `memory_write persists information to MEMORY.md or the daily log` |
| scratchpad | `scratchpad tracks short-term items that must not be forgotten` |
| memory_read | `memory_read reads MEMORY.md, the scratchpad, a daily log, or the memory inventory` |
| memory_forget | `memory_forget deletes matching entries while keeping a recovery copy` |
| memory_restore | `memory_restore restores a forgotten entry by recovery ID` |
| memory_search | `memory_search finds past context across all memory files` |
| memory_status | `memory_status reports what is stored in memory` |
| interop_echo | `interop_echo echoes text back and reports the call id and session id` |
| interop_status | `interop_status returns a JSON snapshot of the DeepDeep plugin host` |
| interop_post_card | `interop_post_card posts an interop.card chip into the chat` |
| interop_send_message | `interop_send_message uses ChatService.sendMessage to post an interop.card or start a plugin turn` |
| sample_echo | `sample_echo echoes text` |
| sample_card | `sample_card posts a card into the chat` |
| template_echo | `template_echo echoes text and reports one partial update` |
| 其余（micloud 7 个、template_session） | — |

promptGuidelines（插件侧全量）：

- `memory_write`（`MemoryTools.kt:57-61`）：
  - `Use memory_write when the user shares durable decisions, preferences, or facts worth keeping across sessions.`
  - `Use target=long_term for durable memory; use target=daily for day-to-day notes.`
  - `Write concise markdown; do not dump raw tool output or secrets.`
- `scratchpad`（`:125-128`）：`Add open TODO items and warnings to the scratchpad instead of burying them in prose.`；`Mark items done as soon as they are resolved.`
- `memory_forget`（`:261-264`）：`Use memory_forget when the user asks to forget or remove something.`；`Prefer a specific match string; the deletion is reversible with memory_restore.`
- `memory_search`（`:390-392`）：`Use memory_search before asking the user to repeat something, and when a task may depend on earlier sessions.`
- `template_echo`（`TemplatePlugin.kt:166`）：`template_echo is a development demo; do not rely on it in user workflows.`
- 其余插件工具（`memory_read`/`memory_restore`/`memory_status`/interop 4 个/micloud 7 个/sample 2 个/template_session）**无 guidelines**。

另：`deepdeep-memory` 的 `plugin.json` `provides.tools` 声明了全部 **7** 个工具（含 `scratchpad`、`memory_forget`、`memory_restore`）；其中 4 个与 runtime memory 同名，安装校验是整包拒绝，因此另外 3 个独有工具实际也永远装不上。该插件还注册 `PromptSection("deepdeep-memory-rules", priority=120)`（`DeepDeepMemoryPlugin.kt:45-51`，正文 `PROMPT_RULES` 在 `:188-197`，中文 8 条），会作为 `<official_plugins>` 注入；interop/template/sample 也各自注入 debug/demo 提示段（`TemplatePlugin.kt:95-101`：`The template plugin exposes template_echo and template_session demo tools. They are examples for plugin development, not production tools.`）。

### 1.6 MCP 动态工具

- 描述来源：**MCP server 的 `tools/list` 返回的 `tool.description`**，不是本地文案。`McpToolAdapter.adapt()`（`/tmp/pi-src/src/commonMain/dev/pi/coding/mcp/McpToolAdapter.kt:58-67`）取 `tool.description.trim()`，空则回退 `tool.title`，再回退 `"MCP tool ${tool.name} from server $serverName"`，最后 `.take(MAX_DESCRIPTION_LENGTH)`，上限 **1024**（`:37`）。
- 工具名：`mcp__<server>__<tool>`，非 `[A-Za-z0-9_]` 一律替换为 `_`；超过 64 字符或重名时截断并追加稳定哈希（`McpToolAdapter.kt:125-133`，`MAX_TOOL_NAME_LENGTH = 64`）。
- label：`"$serverName/${tool.name}"`（`:65`）；参数：`normalizeToolParameters(tool.inputSchema)`（`:66`）；**无 promptSnippet / promptGuidelines**。
- 注册：`PiSessionEngine.createSession` 传 `McpOptions(enabled = mcpEnabled, loadConfig = true, samplingEnabled = false, rootsEnabled = true, autoEnableToolSearch = false)`（`PiSessionEngine.kt:140-146`），SDK 的 `McpManager` 按配置连接 server 后把工具挂进会话（`McpManager.kt:569-596`）。MCP 专用资源工具由 SDK 提供：`list_mcp_resources`、`list_mcp_resource_templates`、`read_mcp_resource`（`McpResourceTools.kt:28-30`）。
- 延迟加载：工具数超过阈值或 server 标注为 deferred 时，工具进入 deferred 目录，只能经 `tool_search`/`tool_execute`（DeepDeep 自己实现，`DeferredTools.kt`）或 MCP 内部搜索暴露；`autoEnableToolSearch = false` 表示不自动开 SDK 的搜索工具。

---

## 2. 工具名冲突表（`HostToolNames.kt`）

文件：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/host/HostToolNames.kt:3-...`。用途：插件安装时校验 manifest `provides.tools` 是否占用宿主名字，冲突直接拒绝安装（`PluginInstaller.kt:48-53`，`InstallRejection.TOOL_CONFLICT`："tool names are already in use: ..."）。

| 分类 | 名字 | 说明 |
|---|---|---|
| engine（7） | read, write, edit, bash, ls, grep, find | SDK 默认名；但 DeepDeep 实际只注册 read/bash/edit/write，**ls/grep/find 被丢弃**（`PiSessionEngine.kt:189`） |
| host（39） | ask_user_question, present, adb_shell, root_shell | 交互/权限 |
| | get_goal, create_goal, update_goal | 目标 |
| | job_start, job_output, job_list, job_kill | 后台任务 |
| | timer_create, timer_list, timer_update, timer_delete | 定时器 |
| | miniapp_create, miniapp_load, miniapp_list, miniapp_delete, miniapp_edit | 小程序 |
| | memory_write, memory_read, memory_search, memory_status | **与 deepdeep-memory 插件 4 个工具同名** |
| | web_search, web_fetch, arrange_calendar | 网络/日历 |
| | virtual_screen, screen_status, screen_observe, screen_screenshot, screen_tap, screen_swipe, screen_scroll, screen_type, screen_key, screen_wait, screen_list_apps, screen_launch | 屏幕（12） |
| sdk（7） | tool_search, tool_execute, mcp_loaded_tools, mcp_resource, mcp_resources, mcp_resource_templates, mcp_servers | 延迟工具 + MCP 资源；**后 4 个与 SDK 实际名不符**（实为 `list_mcp_resources` / `list_mcp_resource_templates` / `read_mcp_resource`） |
| 保留前缀 | `mcp__` | 任何以 `mcp__` 开头的插件工具名都视为冲突 |

`isReserved(name)` = 在 `names` 中或以 `mcp__` 开头；`conflicts(candidates)` 返回排序后的冲突名集合。

---

## 3. Skills 清单

### 3.1 扫描目录与文件结构

`SkillCatalog.discover()`（`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:39-45`）调用 SDK `loadSkills(cwd, agentDir, fs, projectTrusted = true)`；`loadSkills`（`/tmp/pi-src/src/commonMain/dev/pi/coding/prompt/ResourceLoader.kt:121-138`）按顺序扫：

1. `cwd/.pi/skills`（仅 `projectTrusted = true`；本仓库固定传 true）——包含该目录自身的 `SKILL.md`（`includeRootFiles = true`）。
2. `agentDir/skills`——设备上即 `/data/data/<pkg>/files/agent/skills`（`DataGraph.kt:53,106`；App 私有目录）。

目录内规则（`ResourceLoader.kt:140-168`）：
- 目录下若直接存在 `SKILL.md`，**只取它**，不再向下递归。
- 否则按名字排序遍历：跳过 `.` 开头项与 `node_modules`；子目录递归（`includeRootFiles = false`）；只有顶层允许把散落的 `.md` 当 skill。
- 同名去重：先扫到的优先（项目 over 全局）。

`SKILL.md` 结构（`ResourceLoader.kt:170-188`）：
- YAML frontmatter：`name`（可省，默认父目录名）、`description`（**必填**，空则整条被丢弃）、`disable-model-invocation`（值为字符串 `"true"` 时该 skill 不出现在提示词，不可被模型调用）。
- 正文为 markdown；可带引用文件（插件技能用 `PluginSkillFile` 复制到同目录）。

### 3.2 name/description 如何进入提示词

1. SDK `buildSystemPrompt`（`/tmp/pi-src/src/commonMain/dev/pi/coding/prompt/SystemPrompt.kt:50-99`）在有 `read` 或 `bash` 工具时，把 `renderAvailableSkills(skills)` 的输出放进 `skills` section。DeepDeep 有 `read`，故始终生成。
2. 文案（`Skill.kt:18-50`）：

```
The following skills provide specialized instructions for specific tasks.
Use the read tool to load a skill's file when the task matches its description.
When a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.

<available_skills>
  <skill>
    <name>…</name>
    <description>…</description>
    <location>/abs/path/SKILL.md</location>
  </skill>
</available_skills>
```

3. 位置：`<skills>` 在 `<preamble>` 之后、`<cwd>` 之前（`SystemPrompt.kt:72-79`），见 §4.3 的完整顺序。
4. `/skill` 手动展开：`ShellIntake.kt:234` 的 `skillExpander` → `SkillCatalog.expand()` → `renderSkillPromptBlock`：`<skill name="…" location="…">\nReferences are relative to <baseDir>.\n\n<正文（去 frontmatter）>\n</skill>`，参数非空时追加在块后（`Skill.kt:52-62`）。

### 3.3 仓库里实际存在的 skills

App 内置（`app/app/src/main/assets/skills/`，由 `BundledSkills`（`BundledSkills.kt:14-69`）同步到 `files/agent/skills`）：

| id / name | 文件 | description 原文 | 条件 |
|---|---|---|---|
| `deepdeep-arrange` | `assets/skills/deepdeep-arrange/SKILL.md` | `管理系统日历事件与提醒：创建、读取、修改、删除；需要把会议、日程或提醒落到系统日历时使用。` | 总是 |
| `deepdeep-gui` | `assets/skills/deepdeep-gui/SKILL.md` | `优先操作后台虚拟屏，也支持显式选择主屏；读取节点、输入中文、点击、滚动、等待和截图。` | 仅屏幕操作开关开且 effective ∈ {ADB, ROOT}（`AppGraph.kt:292-299`，`BundledSkills.SCREEN_OPS_SKILL`） |
| `miniapp` | `assets/skills/miniapp/SKILL.md` | `在 DeepDeep「小程序」里创建或修改 HTML/CSS/JS 轻量小应用（待办、课程表、纪念日、抽签、小游戏、动画、计算器、工具页等）。页面可以经 bridge 读写自己的数据，也可以用 app.agent 编排一次性 Agent（调查、写作、处理文件）搭建多步骤工作台。当用户想要一个手机上的小程序/小应用/小页面/小工具，或要修改已有小程序时使用。` | 总是 |

插件提供（写入 `agentDir/skills`，插件启用时才有；`PluginSkillStore.kt:21-56`）：

| id / name | 来源 | description 原文 |
|---|---|---|
| `deepdeep-feishu` | `plugins/feishu/.../FeishuContent.kt:9,91-95` | `飞书 CLI 用法速查：读取消息与群聊、查看日历、搜索云文档、云空间文件、邮箱与任务；当用户提到飞书、lark、消息、日程或云文档时使用。` |
| `deepdeep-wecom` | `plugins/wecom/.../WecomPlugin.kt:92-98,114` | `企业微信 CLI 用法速查：消息、邮件、文档、在线表格、智能表格、日程、会议、待办、微盘与通讯录；当用户提到企业微信、WeCom、企微、发消息、文档、日程或待办时使用。` |
| `deepdeep-jwxt` | `plugins/jwxt/.../JwxtContent.kt:44-48`、`JwxtCli.kt:10` | `中山大学教务系统 CLI——查课表、成绩、学分、考试、教学大纲、公告、空闲教室、请假记录与提交。用户问上课时间、某门课怎么样、成绩绩点、考试安排、空教室、教学大纲、请假审批时用；在 bash 中运行 jwxt。` |
| `deepdeep-micloud` | `plugins/micloud/.../MiCloudPlugin.kt:167,206-210` | `小米云数据读写：账号与空间、笔记、待办、相册、短信、联系人、录音；当用户提到小米云、小米便签或待办、i.mi.com，或要读取小米账号下的个人数据时使用。` |
| `template-skill` | `plugins/template/src/main/assets/skills/template-skill/SKILL.md:1-4` | `插件模板自带的示例技能，说明 external 插件如何把 markdown 与附加文件作为技能交给 agent；当用户问插件模板技能如何工作、或需要演示技能加载时使用。` |
| `interop-guide` | `plugins/interop/.../InteropTexts.kt:8,32-45` | **无 frontmatter**（正文以 `# Interop guide` 开头）→ 缺 `description`，被 `loadSkillFromFile` 丢弃，**实际不会出现在提示词里** |

仓库内其它 `SKILL.md` 均位于 `reference/`（第三方参考源码）与 `.agents/worktrees/`（历史工作树），不参与 App 运行时扫描。

---

## 4. 工具规则段（rules）生成逻辑

### 4.1 `buildToolRuleSection`

`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:68-78`：

```kotlin
internal fun buildToolRuleSection(tools: List<CodingTool>): Map<String, String> {
    val rules = LinkedHashSet<String>()
    rules.addAll(GLOBAL_TOOL_RULES)
    for (tool in tools) {
        for (guideline in tool.promptGuidelines.orEmpty()) {
            val normalized = guideline.trim()
            if (normalized.isNotEmpty()) rules.add(normalized)
        }
    }
    return mapOf("rules" to wrapPromptSection("rules", rules.joinToString("\n") { "- $it" }))
}
```

- 拼接方式：每个 guideline 一行 `- ` 前缀；`LinkedHashSet` **仅对完全相同的整行去重**（大小写/空白差异不算重复，`trim()` 后比较）。
- 顺序：4 条 `GLOBAL_TOOL_RULES`（`SystemPrompt.kt:49-54`）在前，然后按 `tools` 列表注册顺序（即 §0 的顺序）逐工具追加。
- 上限：**没有**。无条数上限、无字符上限、无截断、无按工具分组/标题。
- 无 guideline 的工具对 rules 段零贡献。
- `wrapPromptSection`（`Personalization.kt:101-102`）输出 `<rules>\n- …\n</rules>`。
- 当前工具集下的规模：约 **37 行**（全屏幕工具在场）/ **33 行**（屏幕关闭）；其中 `GLOBAL` 4 条 + memory 10 条 + edit 4 条 + screen 4 条（2 条通用 + observe/launch 各 1，重复项已被去重）+ 其余零散。

### 4.2 各工具 guidelines 汇总（进 rules 段的全部输入）

| 工具 | guideline 条数 | 内容 |
|---|---|---|
| 全局 | 4 | 见 §4.1 引用的 `GLOBAL_TOOL_RULES` |
| read | 1 + 1 | `Use read to examine files instead of cat or sed.`；`File tool arguments need explicit absolute paths; the workspace is only a suggested location, not an access boundary.`（`WorkspacePaths.kt:18-19`） |
| bash | 2 | `Check the [exit: N] marker on every bash result; investigate failures before moving on.`；`bash runs Linux /bin/sh; shared work files keep the same absolute paths as the file tools.` |
| edit | 4 | 见 §1.1 |
| write | 1 | `Use write only for new files or complete rewrites.` |
| tool_search | 2 | 见 §1.2 |
| tool_execute | 1 | `Unknown names fail; call tool_search before tool_execute.` |
| root_shell / adb_shell | 1（共用，去重后 1 条） | `System shell output may already be truncated upstream; never claim a complete log when none is available.` |
| todo_write | 2 | 见 §1.2 |
| web_search | 2 | `Cite the source URLs you used when answering with web results.`；`Search before answering questions about current events or facts you do not know.` |
| web_fetch | 1 | `Cite the source URLs you used when answering with web results.`（与 web_search 重复，去重后不新增） |
| arrange_calendar | 1 | `Calendar writes change the user's schedule; confirm with the user before creating, updating or deleting an event.` |
| memory_write | 5 | `Write decisions, preferences, active project state and methods that worked or failed to memory_write so they survive the session.`；`Curated files (profile, context, playbook) hold current understanding; daily holds events. Do not copy daily events into curated files.`；`Inferred understanding must cite its evidence and any expiry condition; keep each entry short.`；`Before replace_section or overwrite, call memory_read first and keep unrelated content.`；`When the user asks you to remember something, write it with memory_write before replying.` |
| memory_read | 2 | `Read a curated file before rewriting it so you do not drop existing content.`；`Use memory_search for recall across everything; use memory_read to inspect the exact current text.` |
| memory_search | 2 | `Search memory before assuming past context; if nothing matches, say so instead of inventing a memory.`；`Prefer short, distinctive Chinese or English keywords over long sentences.` |
| memory_status | 1 | `Use memory_status when the user asks what you remember or how much memory exists.` |
| screen_*（11 个） | 每条 2 条通用 + observe/launch 各 +1 | `Screen tools default to the virtual screen; pass target=main explicitly for the phone display, and never switch screens after a failure.`；`After a cancellation or timeout, observe the selected screen before retrying an action.`；observe：`Observe the screen before node operations and verify the result afterward; …`；launch：`After screen_launch, observe the target screen before interacting.` |
| virtual_screen | 1 | `virtual_screen only manages the virtual screen lifecycle; use the screen_* tools to operate on it.` |
| ask_user_question / present / goal 3 / job 4 / timer 4 / miniapp 5 | 0 | — |

### 4.3 在最终系统提示词中的位置

1. `PiSessionEngine.createSession` 先 `ensureSystemPromptFile(agentDirectory)`（`PiSessionEngine.kt:65`），保证 `files/agent/SYSTEM.md` 存在且内容为 `DEEP_DEEP_SYSTEM_PROMPT`（`SystemPrompt.kt:8-11,56-66`）。
2. SDK `buildFreshSections`（`AgentSdk.kt:239-256`）用 `resources.systemPromptOverride`（即 SYSTEM.md）当 `preamble`。**因为 preamble 非空，SDK 的 `tools` 段与 SDK 自己的 `rules` 段都不会生成**（`SystemPrompt.kt:61-71` 的 else 分支）。
3. 实际顺序（`SystemPrompt.kt:72-88` + `AgentSdk.kt:252-256` + `AgentSession.kt:725-741`）：
   `preamble`（SYSTEM.md）→ `addendum`（APPEND_SYSTEM.md，若有）→ `project_context`（AGENTS.md/CLAUDE.md，若有）→ `skills` → `cwd` → 动态 extras：**`rules`（静态注入，排在最前）** → `personalization` 各家 section → `shared_storage` → `official_plugins` → `memory` → `paths` → `plugin_context` → `mcp_servers`。
   即 **`<rules>` 位于系统提示词末尾附近**（MCP 状态段之前），不在工具定义旁。
4. 同一段 rules 被传了两次（`PiSessionEngine.kt:122` 进 `currentSections()` 的 extra，`:139` 作 `extraSystemPromptSections`）；`extras = extraSystemPromptSections + initialSystemPromptSections`（`AgentSdk.kt:252`），后者的同名 `rules` 覆盖前者，内容相同且 key 位置不变，不会重复出现。
5. 每轮若 section 文本变化，SDK 以差分 `SystemMessage` 追加（`AgentSession.kt:702-720`），`rules` 段内容随工具清单变化（如屏幕开关切换）整体重发。

---

## 5. 描述文案问题清单

| # | 位置 | 现象 | 证据 |
|---|---|---|---|
| 1 | 所有带 `promptSnippet` 的工具 | **snippet 根本没进模型上下文**：DeepDeep 始终有自定义 `preamble`（SYSTEM.md），SDK 只在没有 preamble 时才生成 `tools` 段，因此 read/bash/edit/write/todo/tool_search/插件工具的 snippet 全是死配置 | `SystemPrompt.kt:61-71`（else 分支才 `sections["tools"] = …`）、`PiSessionEngine.kt:65` |
| 2 | deepdeep-memory 插件 vs runtime memory | `provides.tools` 声明 7 个工具，其中 4 个**同名**（memory_write/read/search/status）；安装校验整包拒绝，导致 `scratchpad`/`memory_forget`/`memory_restore` 三个独有工具也装不上，插件实际处于「不可安装」状态；同时它注入的 `deepdeep-memory-rules` 与 runtime `MemoryTools.kt` 的 rules + `<memory>` section 语义重复；若历史版本已安装，则会出现双份同名工具 | `plugins/deepdeep-memory/src/main/assets/plugin.json` 的 `provides.tools`；`HostToolNames.kt`；`PluginInstaller.kt:48-53` |
| 3 | `screen_key` | 参数 `key` 的 description 只列 `back, enter, delete, tab, escape, space, arrow_*` 共 10 个，但 enum 实际还含 `home/recents/notifications/volume_up/volume_down`，共 15 个；描述里的「Home, recents… main-only」只在工具 description，不在参数上 | `ScreenTools.kt:646` 与 `:41-47`（`SCREEN_KEYS` + `MAIN_SCREEN_KEYS`） |
| 4 | `HostToolNames.kt` | 已过时：仍保留 `ls/grep/find`（实际被丢弃），MCP 名字写成 `mcp_resource/mcp_resources/mcp_resource_templates/mcp_servers/mcp_loaded_tools`，与 SDK 实际 `list_mcp_resources/list_mcp_resource_templates/read_mcp_resource` 不符 → 冲突校验漏检 | `PiSessionEngine.kt:189`；`McpResourceTools.kt:28-30` |
| 5 | `edit` | description 与 guideline 大量重复（"overlapping or nested edits / merge nearby changes"、"do not pad with large unchanged regions" 两边各说一遍），单工具 1 段 description + 4 条 guideline 共 5 处同义训诫 | `EditTool.kt:51,55-60` |
| 6 | `web_search` / `web_fetch` | 同一条 `Cite the source URLs…` 各写一次（靠去重才不重复）；`web_fetch` 的其它引用规范缺失 | `WebTools.kt:72-75,92` |
| 7 | `root_shell` / `adb_shell` | 两个工具共用一条关于「输出可能被上游截断」的 guideline；`root_shell` description 又说 "prefer the regular bash tool"，权限语义与输出语义混在一起 | `PrivilegedShellTool.kt:50`、`RootShellTool.kt:82` |
| 8 | 中英混杂 | 系统提示词主体中文，工具 description/guideline 全英文；label 更乱：`Save memory`/`Read memory`/`Search memory`/`Memory status`（英文短语）、`搜索按需工具`/`执行按需工具`（中文）、`mi_cloud_*`/`interop_*`（下划线原名）、`miniapp_create`（小写原名） | `MemoryTools.kt:90,123,148,176`；`DeferredTools.kt:73,151`；各插件 |
| 9 | `memory_write` 参数 `section` | 英文描述里塞中文例子「such as 用户 or 项目名」，同一句内语码转换 | `MemoryTools.kt:48` |
| 10 | `present` / `ask_user_question` | 只有 description，无 snippet/guidelines；两者是最高频交互工具，行为约束（何时用、卡片怎么选）全靠模型读长描述 | `PresentTool.kt`、`AskUserQuestionTool.kt` |
| 11 | goal/job/timer/miniapp（共 16 个） | 全部 0 snippet、0 guideline，纯靠 description；其中 job/timer 的描述里还混着参数语义（"wait=true waits…"），模型要靠读 JSON 才知道怎么用 | `GoalTools.kt`、`JobTools.kt`、`TimerTools.kt`、`MiniappTools.kt` |
| 12 | `interop-guide` skill | 无 frontmatter → 没有 description → 整条 skill 被静默丢弃，插件却在 `provides.skills` 里声明了它 | `InteropTexts.kt:32-45`；`ResourceLoader.kt:177-178` |
| 13 | `template` 插件 | 面向开发者的提示直接进生产提示词：`template_echo is a development demo; do not rely on it in user workflows.`、`The template plugin exposes … demo tools … not production tools.`；`template-skill` 的描述也自称「示例技能」 | `TemplatePlugin.kt:95-101,166`；`template-skill/SKILL.md` |
| 14 | 过长的 description | `bash`(~330 字符)、`read`(~330)、`screen_observe`(~300)、`memory_write`(~300)、`arrange_calendar` 11 个参数、`memory_write` 5 个参数；`screen_*` 每个都带 2 条通用 guideline（11 个工具重复同一句，靠去重才没爆） | 各文件 |
| 15 | 空泛 snippet | `todo_write: maintain the session task list…`、`memory_write persists information…`、`sample_echo echoes text` 等只是把工具名/动作重说一遍；即使 snippet 生效也几乎没有增量信息 | `TodoTools.kt:63`、`MemoryTools.kt:56` |
| 16 | `memory_search` description | 把「cite what you find instead of guessing」这类 guideline 写进 description，与 guideline 分工混乱 | `MemoryTools.kt:149` |
| 17 | `read` 的参数 `path` | SDK 原文 "relative or absolute" 被运行期覆写成 "Absolute path in the Android filesystem."；description 里没有同步说明，模型可能仍按相对路径试 | `WorkspacePaths.kt:32`、`ReadTool.kt:37` |
| 18 | 插件 label 与 name 重复 | micloud 7 个工具 label 与 name 完全相同（`mi_cloud_notes` 等），UI 上没有可读性增益；sample/template/interop 则用 Title Case，风格不统一 | 各插件 |

---

## 6. 最值得先改的 5 个

1. **promptSnippet 死配置 / 缺 `tools` 段**（全局）：要么在 `PiShellEngine` 之外补一个 `<tools>` section（把 snippet 拼成 `- name: snippet`），要么删掉所有 snippet，避免后续维护者以为它在生效。影响 7 个内置（read、bash、edit、write、todo_write、tool_search、tool_execute）+ 14 个插件工具（memory 7、interop 4、sample 2、template 1）。
2. **deepdeep-memory 与 runtime memory 同名工具**：7 个工具里 4 个撞名，整包安装被拒，连累 `scratchpad`/`memory_forget`/`memory_restore` 三个独有能力；同时两份记忆规则段语义重叠。先决定「runtime 自带」还是「插件提供」，删掉另一套并同步 `provides.tools`。
3. **`screen_key` 参数 enum 与 description 不一致**：模型按描述不会去按 home/recents/volume，按 enum 又能传；直接把 15 个键写进 description，并标 main-only。
4. **`HostToolNames` 过时名单**：`ls/grep/find` 已不在上下文，MCP 资源工具名与 SDK 不符；冲突校验对真实 MCP 工具名无效，插件可抢占 `list_mcp_resources` 等名字。
5. **规则段冗余与缺失并存**：`edit` 5 处同义、`web_*` 重复引用规范、`root/adb` 共用一条；同时 goal/job/timer/miniapp 16 个工具 0 条 guideline。优先给 job/timer/miniapp 各补 1 条「何时用/何时别用」，再删 `edit` 的重复条目。

---

## 附：统计

- 运行时/App 内置：**46** 个（34 个常驻 + 12 个屏幕条件注册）。
- 插件：**22** 个（deepdeep-memory 7 + interop 4 + micloud 7 + sample 2 + template 2；feishu/wecom/jwxt 无工具）。
- 合计静态定义 **68** 个；MCP 工具数量由 server `tools/list` 动态决定（另加 SDK 3 个 MCP 资源工具）。
- 进入提示词的 skill：内置 3 个（其中 `deepdeep-gui` 有条件）+ 插件最多 5 个（interop-guide 缺 frontmatter，实际不出现）。

