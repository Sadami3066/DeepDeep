# 工具设计

状态：App 层工具已接入；Android 能力与 Linux 专用工具只保留分类。

## 工具分层

| 类别 | 运行位置 | 当前 |
|---|---|---|
| SDK 原生 | app 进程 + PRoot | `read`、`write`、`edit`、`ls`、`grep`、`find`、`bash` |
| App 层 | app 进程 | `ask_user_question`、`schedule_create/list/update/delete` |
| Android 能力 | app 进程 | 设计；通知、Intent、联系人等需要单独评审权限 |
| Linux 能力 | PRoot | 优先用 `bash` 或脚本；只有结构化输出/宿主能力才新增工具 |

新增工具的顺序：先用 `bash` 能否表达；不能再用 App/Android 工具。不要为同一能力同时提供 `bash` 与专用工具。

## 注册

`PiSessionEngine` 创建会话时：

```text
defaultCodingTools(...) + appTools(cwd, AppToolContext)
```

`AppToolContext` 提供 `ScheduleService`、`QuestionCenter`；`ScheduleBinding` 提供当前会话的 id、会话文件与 cwd，供工具把数据绑定到调用它的会话。工具实现 `CodingTool`，参数用 `JsonObject` 描述，执行结果用 `textResult`；错误结果 `isError = true`，不把用户可见文案放进 runtime。

取消：长任务通过 `RunSignal` 监听；UI 等待型工具在 signal abort 时结束等待。

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
- UI 是覆盖全 App 的 bottom sheet：一次一题、进度、选项、多选、自定义答案、跳过/上一题/下一题/提交；`(Recommended)` 显示为「推荐」标签，回传仍保留原始 label。
- 已在历史中的工具调用只显示参数/结果，不重新变成可交互问题。

## schedule

数据文件：`files/schedules/schedules.json`，`schemaVersion` 2。

```json
{
  "schemaVersion": 2,
  "schedules": [
    {
      "id": "a1b2c3d4e5f6",
      "name": "每日简报",
      "prompt": "整理今天的日程并给出建议",
      "trigger": { "type": "weekly", "time": "09:00", "days": [1,2,3,4,5] },
      "enabled": true,
      "sessionId": "01a167c8-2d77-7943-9854-5e670c5c1c23",
      "sessionFile": "agent/sessions/--...--/2026-10-23T08-02-14-519Z_01a167c8....jsonl",
      "cwd": "files/home",
      "nextRunAt": 1760058000000,
      "lastDeliveredAt": null,
      "lastError": null,
      "createdAt": 1760000000000,
      "updatedAt": 1760000000000
    }
  ]
}
```

触发类型：

| `frequency` | 参数 | 说明 |
|---|---|---|
| `once` | `date`, `time` | 必须是未来时间 |
| `daily` | `time` | 每天 |
| `weekdays` | `time` | 周一至周五 |
| `weekends` | `time` | 周六、周日 |
| `weekly` | `time`, `weekdays` | 自定义 ISO 星期 |

工具：

- `schedule_create`：创建提醒；绑定到调用它的会话，不指定工作区。
- `schedule_list`：列出当前会话的 id、名称、触发、下次投递、启用状态、上次投递与错误码。
- `schedule_update`：局部更新名称、内容、触发或启用状态并重算 `nextRunAt`；不能改绑会话。
- `schedule_delete`：按 id 删除当前会话的提醒。
- 四个工具都只作用于调用会话的提醒；结果取消 `execution` 字段，`lastError` 返回错误码。

投递：

- `ScheduleService` 在 `AgentService` 内运行；没有固定 tick，按最近的 `nextRunAt` 唤醒，存储变更与会话事件即时重算。
- 到点把 `[定时提醒] 名称` 消息投进绑定会话并开始一次运行；同一会话多条同时到点合并成一条批量提醒。
- 一次性提醒投递后停用；周期提醒按规则推进；失败记录 `SESSION_MISSING` / `OPEN_FAILED` / `DELIVERY_FAILED`。
- 服务被杀时不唤醒，下次打开 App 补投一次。投递与手动执行共用同一条路径。
- 存储使用 Mutex + 临时文件替换；JSON 损坏时保留原文件并报错。
- 触发时间使用设备本地时区；时间计算可注入 `Clock` 便于测试。

会话绑定：

- 工具通过 `ScheduleBinding.current()` 取调用会话；没有绑定时返回错误结果。
- 删除会话会删除其提醒；归档会话会停用其提醒。
- 任务页与工具写同一个存储，服务订阅存储变更，界面实时更新。

## 安全

- App 层工具拥有 app 自身权限；不要提供绕过系统权限的隐藏能力。
- 技能、AGENTS.md 与定时任务都是本地明文；不写入 API Key。
- `ask_user_question` 只在 App 前台可用；后台任务不得挂起等待用户。
- 新增 Android 能力工具前先确定权限、前台限制与失败时的用户可见反馈。
