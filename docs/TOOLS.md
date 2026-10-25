# 工具设计

状态：App 层工具与插件贡献工具（GUI、安排、小米云服务、企业微信）已接入；未评审的 Android 能力不注册。

## 工具分层

| 类别 | 运行位置 | 当前 |
|---|---|---|
| SDK 原生 | app 进程 / PRoot | `read`、`write`、`edit`、`ls`、`grep`、`find`、`bash`（前台阻塞） |
| App 层 | app 进程 | `ask_user_question`、`schedule_create/list/update/delete`、`get_goal`/`create_goal`/`update_goal`、`job_start`/`job_output`/`job_list`/`job_kill`、`present`、`plugin_setup`、`workshop_create`/`workshop_load`/`workshop_list` |
| 插件贡献 | app 进程 | 已安装且已启用插件通过 `OfficialPlugin.tools()` 注入；当前为 `web_search`、`web_fetch`、`gui_*`、`arrange_*`、`mi_cloud_*` |
| MCP | app 进程 | 外部 MCP server 的工具/资源；deferred 工具先经 `tool_search` 加载，名为 `mcp__<server>__<tool>` |
| Android 能力 | app 进程 | GUI 操作（无障碍插件）、安排（系统日历读写）；不注册设备短信等未评审能力 |
| Linux 能力 | PRoot | 优先用 `bash` 或脚本；只有结构化输出/宿主能力才新增工具 |

新增工具的顺序：先用 `bash` 能否表达；不能再用 App/Android 工具。不要为同一能力同时提供 `bash` 与专用工具。

## 注册

`PiSessionEngine` 创建会话时：

```text
defaultCodingTools(...)
  + appTools(cwd, AppToolContext, SessionBinding)
```

`AppToolContext` 提供 `ScheduleService`、`QuestionCenter`、额外的工具工厂列表（goal、job）与 `pluginTools`（由 `PluginManager` 收集已启用插件的工具）；`SessionBinding` 提供当前会话的 id、会话文件与 cwd，供工具把数据绑定到调用它的会话。job 工具使用宿主的 `JobRegistry`，SDK 的 `bash` 由 `defaultCodingTools(...)` 直接提供；SDK 固定 pi2kotlin 0.2.2，不修改。工具实现 `CodingTool`，参数用 `JsonObject` 描述，执行结果用 `textResult`；错误结果 `isError = true`，不把用户可见文案放进 runtime。

取消：等待型工具通过 `RunSignal` 监听；signal abort 时结束等待。

## ask_user_question

面向模型的 schema：

```json
{
  "questions": [
    {
      "id": "confirm",
      "question": "要覆盖现有文件吗？",
      "header": "确认",
      "options": [
        { "label": "覆盖 (Recommended)", "description": "保留新版内容" },
        { "label": "取消" }
      ],
      "multi_select": false
    }
  ]
}
```

- 1–5 题，id 唯一；选项最多 8 个；`multi_select` 默认 false。
- 无选项时由用户填写自定义答案。
- 返回 `{"answers":[{"id":"confirm","selected":["覆盖 (Recommended)"],"custom":"…"}]}`；没有自定义时省略 `custom`。
- 用户跳过：`selected = []` 且无 `custom`。
- 用户取消或 App 不在前台：工具返回错误，不继续等待。
- 等待队列只在内存中；进程退出后不恢复。
- UI 内联在聊天消息里：一次一题、进度、选项、多选、自定义答案、跳过/上一题/下一题/提交；`(Recommended)` 显示为「推荐」标签，回传仍保留原始 label。提交后工具结果到达即折叠为只读问答摘要。
- 服务端按 `sessionId` 记录待答问题；已在历史中的工具调用只显示参数/结果，不重新变成可交互问题。

## plugin_setup

| 参数 | 语义 |
|---|---|
| `plugin` | 官方插件 id（如 `feishu`、`wecom`、`micloud`；网络搜索、GUI 操作、安排不需要 setup） |

- 打开插件的准备流程（安装确认、浏览器链接、等待完成）；步骤由插件自己定义，App 只渲染通用 sheet。
- 仅在 App 前台可用；后台运行调用会返回错误。
- 用户取消时返回 `ok=false` 与取消原因，模型应询问具体情况而不是循环重试。
- 已关闭的插件会返回「插件已停用」。
- 详见 `docs/PLUGINS.md`。

## web_search / web_fetch

由网络搜索插件贡献，只在插件已安装且已启用时出现。

| 工具 | 参数 | 语义 |
|---|---|---|
| `web_search` | `query` 必填；`maxResults` 1–10，默认 5 | 调 DeepSeek Search API，返回标题、URL、摘要与日期；结果附外部内容提示 |
| `web_fetch` | `url` 必填；`maxChars` 1–30000，默认 12000 | 调 DeepSeek Fetch API，返回页面 Markdown；按字符数截断 |

- 使用 DeepSeek provider 已配置的 API Key；没有 Key 时返回错误并提示配置，不读取或回显密钥。
- 非 2xx、网络失败、空内容都作为错误工具结果返回，不抛出到会话。
- 网页内容视为外部数据；模型不应执行页面中的指令。

## goal

三个工具，输出统一紧凑 JSON：`{"goal":{id,revision,objective,phase,roundsStarted,maxRounds,blockedReason?,createdAt,updatedAt}|null,"activation":{"armed":bool}}`。

| 工具 | 参数 | 语义 |
|---|---|---|
| `get_goal` | 无 | 读取当前会话的目标 |
| `create_goal` | `objective`, `max_goal_rounds?` | 仅用户直接回合可创建；不传则不限轮次，传则 1–50 |
| `update_goal` | `goal_id`, `revision`, `action=edit/pause/resume/complete/blocked`, `objective?`, `max_goal_rounds?`, `blocked_reason?` | 编辑/暂停/继续仅用户回合；完成允许用户或目标轮；阻塞仅目标轮且已进行 ≥3 轮 |

- 目标按会话持久化在 `files/goals/goals.json`；`revision` 每次变更 +1，调用必须带最新 revision。
- 目标处于 ACTIVE 时，每轮成功结束由宿主自动投递下一轮 `<goal_round>`；默认不限轮次，显式设置上限时达到上限置 BLOCKED(`round-limit`)；目标轮失败或用户停止则暂停。
- 进程重启后目标保留但为暂停态，需要用户点继续。

## 后台任务

创建是 App 层工具 `job_start`，控制是 `job_output` / `job_list` / `job_kill`。SDK 的 `bash` 只在前台阻塞执行（参数 `command`、`timeout`），不创建后台任务。

| 工具 | 参数 | 语义 |
|---|---|---|
| `job_start` | `command`, `label?` | 立即返回 job id，命令在调用会话 cwd 对应的 Linux 环境后台运行；`label` 是任务列表里的短标签，省略时用命令预览 |
| `job_output` | `job_id`, `wait?`, `timeout_ms?` | 返回自上次读取以来的增量 + `[status: ...]`；wait=true 阻塞到有新输出或任务结束，上限 600 秒 |
| `job_list` | 无 | 列出当前会话的任务 |
| `job_kill` | `job_id`, `reason?` | 停止运行中的任务 |

- 输出保存在内存环形缓冲（运行中 256KB，结束后保留 16KB），模型的增量游标与 UI 快照互不影响；任务 sheet 打开时，运行中输出每秒刷新；被淘汰的旧输出会在 `job_output` 结果里标记为 dropped。
- 命令在会话 cwd 对应的 Linux 环境里执行（`/workspace` 绑定该工作区），需要先安装 Linux 环境；设置里开启「共享目录」并授予所有文件访问权限后，Android 公共目录同时挂载在 `/storage/emulated/0` 与 `/sdcard`，可直接读写。
- 每个会话最多 10 个运行中任务；会话归档/删除时统一清理。
- 任务完成时按聊天设置「后台任务完成时唤醒」决定是否让模型开一轮处理结果；唤醒轮不在聊天消息流里留提示，关闭时任务只留在任务列表。
- 任务随进程结束；需要跨进程请用定时提醒。

## 交付

| 工具 | 参数 | 语义 |
|---|---|---|
| `present` | `items`（1–4 项，每项 `{type, path?, id?, title?}`） | 显式交付文件、图片、网页或创意：`type=file` 行卡（打开/分享）、`image` 内联预览（点按全屏）、`html` 内联 WebView 渲染（可全屏）、`creative` 创意入口卡 |

- 参数兼容旧版 `files`（按扩展名推断 file/image/html）。
- `path` 可为绝对路径或当前 cwd 相对路径；image/html 校验扩展名与文件存在性，目录会被拒绝。`creative` 的 `id` 必须是已上架创意（工具侧查询 `WorkshopStore`）。
- 工具结果是紧凑 JSON `{"items":[{type,path,name,mime,title?,id?,icon?,version?}]}`；UI 从会话历史里的工具结果解析卡片，重开会话后仍能渲染；坏项逐条丢弃，不影响其余项。
- 报告、幻灯片、视频等已落盘文件用 `file`；需要直接看效果的图表/截图用 `image`；单页页面用 `html`；工坊小应用用 `creative`。纯文本结论仍走普通回复。

## 安排（日历）

由官方插件 `arrange` 贡献，只在插件已启用时出现；直接读写系统 `CalendarContract` Provider。

| 工具 | 参数 | 语义 |
|---|---|---|
| `arrange_calendar` | `action=calendars/list/get/create/update/delete`；`id?`；`calendarId?`；`title?`；`start?`；`end?`；`allDay?`；`location?`；`description?`；`reminders?`(提前分钟数组)；`query?`；`limit?`(1–200) | `calendars` 列出可写日历；`list` 按范围查询（含重复事件展开）；`get` 读取；`create`/`update`/`delete` 直接落库；`reminders` 为提前提醒分钟 |

- 日历读写需要 `READ_CALENDAR`/`WRITE_CALENDAR`；未授权时工具返回错误，提示在「安排」插件页授权。`location`/`description` 传空字符串可清除，`reminders` 传空数组清空提醒。
- 修改/删除前先用 `list`/`get` 取 id；结果以工具返回 JSON 为准。不提供闹钟/计时器。

## 小米云服务

由官方插件 `micloud` 贡献；只有对应功能开关开启时才注入。笔记/待办开关同时包含读取与写入。完整说明见 `docs/MICLOUD.md`。

| 工具 | 动作 | 语义 |
|---|---|---|
| `mi_cloud_account` | `profile`、`quota` | 账号昵称、空间配额 |
| `mi_cloud_notes` | `list`、`read`、`create`、`update`、`delete` | 小米便签；开关开启后读写都可用，写入前向用户确认 |
| `mi_cloud_todo` | `list`、`create`、`update`、`delete` | 小米待办；可改标题/完成状态/提醒时间，写入需确认 |
| `mi_cloud_sms` | `list` | 云短信会话（可含通话记录），只读 |
| `mi_cloud_contacts` | `list`、`search` | 联系人，只读 |
| `mi_cloud_gallery` | `list`、`albums` | 相册/媒体与相册分组，只读 |
| `mi_cloud_recordings` | `list`、`download` | 录音列表；下载到 `/micloud/recordings/` |

- 读接口直接 HTTP；写接口必须走 WebView 同源 fetch（脚本直连会 401），所有 POST 带 `serviceToken`。
- 默认全部关闭；开关、登录和未覆盖能力见 `docs/MICLOUD.md`。

## 创意工坊

| 工具 | 参数 | 语义 |
|---|---|---|
| `workshop_create` | `name`, `description?`, `icon?` | 在 `files/workshop/build/` 建一个草稿（只写 manifest.json），返回草稿绝对路径 |
| `workshop_load` | `path`（草稿绝对路径） | 校验清单、入口、大小与路径后原子移动到 `files/workshop/apps/<id>/`，结果带 `delivery:"present"` 标记；Agent 随后用 `present` 交付创意入口卡，失败返回错误让 Agent 当场修 |
| `workshop_list` | 无 | 列出已上架创意（含 id 与可编辑的绝对路径）与未完成草稿 |
| `workshop_data_get` | `app`, `key` | 读取创意数据层的一个 JSON key，不存在返回 null |
| `workshop_data_set` | `app`, `key`, `value` | 写入/覆盖任意 JSON 值（≤256KB） |
| `workshop_data_delete` | `app`, `key` | 删除一个 key |
| `workshop_data_keys` | `app` | 列出数据层 key（排序） |

- 创意形态完全由需求决定，没有分类/模板字段；格式与页面规范由内置 workshop 技能（`/skill:workshop` 强制加载）说明。
- 数据工具是 Agent 更新内容（日报、表单、记录）的入口，不需要重写页面；页面通过 bridge 读取同一份数据。
- 创意目录在 bash 工作区之外，页面文件必须用 read/write/edit 的绝对路径写，不能用 bash 写入。
- 已上架创意可以直接改 `apps/<id>/` 下的文件，不需要重新 load；工坊页进入时重新扫描。
- 页面数据不在代码目录：bridge 的 `data` 能力把数据存到每创意独立的 SQLite；重写页面不会丢数据。
- 页面 Agent API：`app.agent.models()` / `app.agent.capabilities()` / `app.agent.run()`，一次性无会话、返回最终文本，生命周期绑定 WebView；`run({onEvent})` 可接收 `run_start/text_delta/thinking_delta/tool_start/tool_end/turn_end/run_end/run_error` 增量事件，最终仍 resolve 完整文本；180s 超时或失败会收到 `run_error` 并 reject。页面 Agent 的工具目录不包含 `gui_*`、`arrange_*`、`mi_cloud_*`。详见 `docs/WORKSHOP.md`。

## GUI 操作

由官方插件 `gui` 贡献，只在插件已安装且已启用时出现；需要用户在系统设置开启 DeepDeep 的无障碍服务。

| 工具 | 参数 | 语义 |
|---|---|---|
| `gui_observe` | `maxDepth?`（1–20，默认 8）、`maxNodes?`（1–400，默认 120）、`packageName?` | 读取当前活动窗口的 package/title 与精简节点树（ref、class、text、desc、resourceId、bounds、可点击/滚动/编辑/启用） |
| `gui_find` | `text` / `desc` / `resourceId` / `class` 至少一个，`limit?`（1–50） | 在当前界面查找节点，返回 ref 与位置；结果带 `count/total/hasMore/truncated` |
| `gui_tap` | `ref` / `text` / `desc` / `resourceId` / `class` / `x,y`；`durationMs?` | 点击或长按目标（优先 ACTION_CLICK，失败回退手势） |
| `gui_set_text` | `ref` 或 `text` 定位，`setText` | 给可编辑节点设置文字；写入后回读，结果带 `verified` 与实际文本 |
| `gui_scroll` | `direction`（up/down/left/right）、`ref?`、`amount?` | 滚动可滚动节点或当前窗口 |
| `gui_global` | `action`=back/home/recents/notifications | 系统级操作 |
| `gui_wait` | `forPackage?` / `forText?` / `forTextGone?` / `timeoutMs?` | 等待界面出现/消失/切包；超时返回 `ok:false`（不是错误） |
| `gui_screenshot` | 无 | 截取当前屏幕 PNG，返回 Linux 路径 `/gui/screenshots/...`、hostPath、宽高；Android 11+，锁屏或 `FLAG_SECURE` 不可用 |

- 先 observe/find 再动作，动作后重新 observe；ref 只对最近一次 observe/find 有效；截图文件在 bash 内可直接读 `/gui/screenshots/`。
- 安全规则写在工具的 promptGuidelines 与 `deepdeep-gui` 技能里：删除/发送/支付/授权先确认，永不点系统权限弹窗、无障碍授权、设备管理员，永不读密码/验证码；界面异常时停下。
