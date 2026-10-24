# 工具设计

状态：App 层工具已接入；Android 能力与 Linux 专用工具只保留分类。

## 工具分层

| 类别 | 运行位置 | 当前 |
|---|---|---|
| SDK 原生 | app 进程 / PRoot | `read`、`write`、`edit`、`ls`、`grep`、`find`、`bash`（前台阻塞） |
| App 层 | app 进程 | `ask_user_question`、`schedule_create/list/update/delete`、`get_goal`/`create_goal`/`update_goal`、`job_start`/`job_output`/`job_list`/`job_kill`、`present`、`plugin_setup` |
| MCP | app 进程 | 外部 MCP server 的工具/资源；deferred 工具先经 `tool_search` 加载，名为 `mcp__<server>__<tool>` |
| Android 能力 | app 进程 | 设计；通知、Intent、联系人等需要单独评审权限 |
| Linux 能力 | PRoot | 优先用 `bash` 或脚本；只有结构化输出/宿主能力才新增工具 |

新增工具的顺序：先用 `bash` 能否表达；不能再用 App/Android 工具。不要为同一能力同时提供 `bash` 与专用工具。

## 注册

`PiSessionEngine` 创建会话时：

```text
defaultCodingTools(...)
  + appTools(cwd, AppToolContext, SessionBinding)
```

`AppToolContext` 提供 `ScheduleService`、`QuestionCenter` 与额外的工具工厂列表（goal、job）；`SessionBinding` 提供当前会话的 id、会话文件与 cwd，供工具把数据绑定到调用它的会话。job 工具使用宿主的 `JobRegistry`，SDK 的 `bash` 由 `defaultCodingTools(...)` 直接提供；SDK 固定 pi2kotlin 0.2.1，不修改。工具实现 `CodingTool`，参数用 `JsonObject` 描述，执行结果用 `textResult`；错误结果 `isError = true`，不把用户可见文案放进 runtime。

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
| `plugin` | 官方插件 id，当前只有 `feishu` |

- 打开插件的准备流程（安装确认、浏览器链接、等待完成）；步骤由插件自己定义，App 只渲染通用 sheet。
- 仅在 App 前台可用；后台运行调用会返回错误。
- 用户取消时返回 `ok=false` 与取消原因，模型应询问具体情况而不是循环重试。
- 详见 `docs/PLUGINS.md`。

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
| `present` | `files`（1–4 项，每项 `{path}`） | 把已生成的文件作为交付物展示给用户：聊天里显示与附件同尺寸的文件卡，点击打开、右上角分享 |

- 只做展示，不读写文件、不校验文件存在性；路径由 App 侧按当前工作区解析，找不到时点打开会 Toast 提示。
- 工具结果是紧凑 JSON `{"files":[{"path":"…"}]}`；UI 从会话历史里的工具结果解析卡片，重开会话后仍能渲染。
- 适合报告、幻灯片、视频、网页、图片等已经落盘的文件；纯文本结论仍走普通回复。
